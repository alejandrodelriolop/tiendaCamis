package es.camisetas.services;


import es.camisetas.connections.Transacciones;
import es.camisetas.dao.*;
import es.camisetas.dto.ItemPedido;
import es.camisetas.dto.PedidoDetalleDTO;
import es.camisetas.dto.SolicitudPedido;
import es.camisetas.exceptions.FalloControladoException;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operaciones comerciales sobre pedidos. Cada operación abre una sola transacción y pasa la misma
 * Connection a todos los DAO implicados.
 */
public class PedidoService {

    private static final Logger log = LogManager.getLogger(PedidoService.class);

    /** Regla propia de la tienda: las ediciones de coleccionista son limitadas y se restringen por pedido. */
    public static final int MAX_UNIDADES_COLECCIONISTA = 2;

    private final Transacciones tx;
    private final ClienteDAO clienteDao = new ClienteDAO();
    private final DireccionDAO direccionDao = new DireccionDAO();
    private final CamisetaDAO camisetaDao = new CamisetaDAO();
    private final InventarioDAO inventarioDao = new InventarioDAO();
    private final MovimientoStockDAO movimientoDao = new MovimientoStockDAO();
    private final PedidoDAO pedidoDao = new PedidoDAO();
    private final LineaPedidoDAO lineaDao = new LineaPedidoDAO();
    private final PagoDAO pagoDao = new PagoDAO();

    public PedidoService(Transacciones tx) {
        this.tx = tx;
    }

    /**
     * Registra un pedido: valida cliente, dirección y productos; comprueba existencias; crea el pedido y sus
     * líneas con el precio vigente; descuenta stock y registra los movimientos. Es una única transacción.
     *
     * @param provocarFallo si es true (modo FALLO_TRANSACCION), lanza un {@link FalloControladoException} después de
     *                      todas las modificaciones y antes del commit, para demostrar el rollback
     * @return id del pedido (solo si se confirma)
     */
    public long crearPedido(SolicitudPedido solicitud, boolean provocarFallo) {
        Map<Long, Integer> items = normalizar(solicitud);
        return tx.ejecutar("pedido del cliente " + solicitud.clienteId(), con -> {
            log.info("Inicio pedido cliente={} lineas={}", solicitud.clienteId(), items.size());

            // 1) Validaciones (todavía no se ha escrito nada)
            clienteDao.buscarPorId(con, solicitud.clienteId())
                    .orElseThrow(() -> NegocioException.noEncontrado("Cliente", solicitud.clienteId()));
            Direccion direccion = direccionDao.buscarPorId(con, solicitud.direccionId())
                    .orElseThrow(() -> NegocioException.noEncontrado("Dirección", solicitud.direccionId()));
            if (direccion.clienteId() != solicitud.clienteId()) {
                throw NegocioException.validacion("La dirección " + direccion.id() + " no pertenece al cliente " + solicitud.clienteId());
            }
            List<Camiseta> camisetas = validarProductos(con, items);

            // 2) Escrituras
            LocalDateTime ahora = ahora();
            long pedidoId = pedidoDao.insertar(con, new Pedido(null, solicitud.clienteId(), solicitud.direccionId(),
                    ahora, Pedido.Estado.PENDIENTE, BigDecimal.ZERO));

            BigDecimal total = BigDecimal.ZERO;
            for (Camiseta c : camisetas) {
                int cantidad = items.get(c.id());
                if (c.controlStock() && inventarioDao.descontarStock(con, c.id(), cantidad) == 0) {
                    // Defensa extra: la condición "stock >= cantidad" viaja en el propio UPDATE.
                    int disponible = inventarioDao.buscarPorCamiseta(con, c.id()).map(i -> i.stock()).orElse(0);
                    throw NegocioException.stockInsuficiente(c.sku(), cantidad, disponible);
                }
                BigDecimal importe = c.precio().multiply(BigDecimal.valueOf(cantidad));
                lineaDao.insertar(con, new LineaPedido(null, pedidoId, c.id(), cantidad, c.precio(), importe));
                if (c.controlStock()) {
                    movimientoDao.insertar(con, new MovimientoStock(null, c.id(), MovimientoStock.Tipo.SALIDA_PEDIDO,
                            cantidad, ahora, pedidoId, "Pedido " + pedidoId));
                }
                total = total.add(importe);
            }
            pedidoDao.actualizarTotal(con, pedidoId, total);

            if (provocarFallo) {
                log.warn("Fallo controlado; se revierte pedido={}", pedidoId);
                throw new FalloControladoException("Fallo provocado tras guardar el pedido " + pedidoId
                        + ", sus líneas, el descuento de stock y los movimientos, antes del commit");
            }
            log.info("Pedido {} preparado para confirmar: total={}", pedidoId, total);
            return pedidoId;
        });
    }

    /** Pago simulado: registra el pago, lo deja COMPLETADO y pasa el pedido de PENDIENTE a PAGADO. */
    public long pagar(long pedidoId, Pago.Metodo metodo) {
        return tx.ejecutar("pago del pedido " + pedidoId, con -> {
            Pedido pedido = pedidoDao.buscarPorId(con, pedidoId)
                    .orElseThrow(() -> NegocioException.noEncontrado("Pedido", pedidoId));
            if (pedido.estado() == Pedido.Estado.CANCELADO) {
                throw NegocioException.estadoInvalido("No se puede pagar el pedido " + pedidoId + ": está cancelado");
            }
            if (pedido.estado() == Pedido.Estado.PAGADO) {
                throw NegocioException.estadoInvalido("El pedido " + pedidoId + " ya está pagado");
            }
            long pagoId = pagoDao.insertar(con, new Pago(null, pedidoId, metodo, Pago.Estado.COMPLETADO, pedido.total(), ahora()));
            if (pedidoDao.cambiarEstado(con, pedidoId, Pedido.Estado.PENDIENTE, Pedido.Estado.PAGADO) != 1) {
                throw NegocioException.estadoInvalido("El pedido " + pedidoId + " cambió de estado durante el pago");
            }
            log.info("Pago registrado id={} pedido={} metodo={} importe={}", pagoId, pedidoId, metodo, pedido.total());
            return pagoId;
        });
    }

    /**
     * Cancela un pedido: cambia su estado, devuelve el stock de lo que salió, registra los movimientos de entrada
     * y reembolsa el pago si lo había. La condición del UPDATE impide cancelar dos veces y, por tanto, devolver
     * el stock dos veces.
     *
     * @return unidades repuestas al inventario
     */
    public int cancelar(long pedidoId) {
        return tx.ejecutar("cancelación del pedido " + pedidoId, con -> {
            pedidoDao.buscarPorId(con, pedidoId)
                    .orElseThrow(() -> NegocioException.noEncontrado("Pedido", pedidoId));
            if (pedidoDao.cancelar(con, pedidoId) == 0) {
                throw NegocioException.pedidoYaCancelado(pedidoId);
            }

            int unidades = 0;
            LocalDateTime ahora = ahora();
            for (MovimientoStock salida : movimientoDao.listarSalidasDePedido(con, pedidoId)) {
                inventarioDao.incrementarStock(con, salida.camisetaId(), salida.cantidad());
                movimientoDao.insertar(con, new MovimientoStock(null, salida.camisetaId(),
                        MovimientoStock.Tipo.ENTRADA_CANCELACION, salida.cantidad(), ahora, pedidoId,
                        "Cancelación del pedido " + pedidoId));
                unidades += salida.cantidad();
            }
            int reembolsos = pagoDao.cambiarEstado(con, pedidoId, Pago.Estado.COMPLETADO, Pago.Estado.REEMBOLSADO);
            log.info("Pedido {} cancelado: unidadesRepuestas={} pagoReembolsado={}", pedidoId, unidades, reembolsos == 1);
            return unidades;
        });
    }

    /** Pedido completo (cliente, dirección, fecha, estado, líneas y total), leído con una sola conexión. */
    public PedidoDetalleDTO detalle(long pedidoId) {
        return tx.consultar(con -> {
            PedidoDetalleDTO cabecera = pedidoDao.buscarCabecera(con, pedidoId)
                    .orElseThrow(() -> NegocioException.noEncontrado("Pedido", pedidoId));
            return new PedidoDetalleDTO(cabecera.pedidoId(), cabecera.cliente(), cabecera.email(), cabecera.direccionEnvio(),
                    cabecera.fecha(), cabecera.estado(), cabecera.total(), lineaDao.listarDetalle(con, pedidoId));
        });
    }

    // ---------------------------------------------------------------- privados

    /** Comprueba que cada producto existe, está disponible, respeta la edición y tiene existencias. */
    private List<Camiseta> validarProductos(Connection con, Map<Long, Integer> items) {
        List<Camiseta> camisetas = new ArrayList<>();
        for (Map.Entry<Long, Integer> item : items.entrySet()) {
            Camiseta c = camisetaDao.buscarPorId(con, item.getKey())
                    .orElseThrow(() -> NegocioException.noEncontrado("Camiseta", item.getKey()));
            int cantidad = item.getValue();
            if (!c.activa()) {
                throw NegocioException.productoNoDisponible(c.sku());
            }
            if (c.edicion() == Camiseta.Edicion.COLECCIONISTA && cantidad > MAX_UNIDADES_COLECCIONISTA) {
                throw NegocioException.limiteEdicion(c.sku(), MAX_UNIDADES_COLECCIONISTA);
            }
            if (c.controlStock()) {
                int disponible = inventarioDao.buscarPorCamiseta(con, c.id()).map(i -> i.stock()).orElse(0);
                if (disponible < cantidad) {
                    throw NegocioException.stockInsuficiente(c.sku(), cantidad, disponible);
                }
            }
            camisetas.add(c);
        }
        return camisetas;
    }

    /** Valida las cantidades y agrupa las líneas repetidas de una misma camiseta. */
    private Map<Long, Integer> normalizar(SolicitudPedido solicitud) {
        if (solicitud.items() == null || solicitud.items().isEmpty()) {
            throw NegocioException.validacion("Un pedido necesita al menos un producto");
        }
        Map<Long, Integer> items = new LinkedHashMap<>();
        for (ItemPedido item : solicitud.items()) {
            if (item.cantidad() <= 0) {
                throw NegocioException.validacion("Las cantidades deben ser positivas (camiseta " + item.camisetaId() + ")");
            }
            items.merge(item.camisetaId(), item.cantidad(), Integer::sum);
        }
        return items;
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
