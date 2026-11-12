package es.camisetas.app;

import es.camisetas.dto.CamisetaDetalleDTO;
import es.camisetas.dto.CatalogoFiltro;
import es.camisetas.dto.ClienteCompletoDTO;
import es.camisetas.dto.ItemPedido;
import es.camisetas.dto.Pagina;
import es.camisetas.dto.SolicitudPedido;
import es.camisetas.exceptions.ConexionException;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.exceptions.PersistenciaException;
import es.camisetas.model.Camiseta;
import es.camisetas.model.Categoria;
import es.camisetas.model.Cliente;
import es.camisetas.model.Direccion;
import es.camisetas.model.Pago;
import es.camisetas.services.Services;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;



/**
 * Tienda en modo normal: el usuario decide qué hacer. Solo pregunta, llama a los servicios y presenta resultados;
 * las reglas de negocio y el acceso a datos siguen estando en los servicios y los DAO.
 */
final class MenuTienda {

    private static final Logger log = LogManager.getLogger(MenuTienda.class);
    private static final int TAMANO_PAGINA = 8;

    private final Services servicios;
    private final Consola consola;
    private final PrintStream out;
    private final Presentacion presentacion;

    MenuTienda(Services servicios, Consola consola, PrintStream out) {
        this.servicios = servicios;
        this.consola = consola;
        this.out = out;
        this.presentacion = new Presentacion(out);
    }

    void ejecutar() {
        log.info("Entrada en la tienda (modo normal, interactivo)");
        while (true) {
            presentacion.titulo("TIENDA DE CAMISETAS · MODO NORMAL");
            out.println("  1. Ver el catálogo (con filtros y páginas)");
            out.println("  2. Ver el detalle de una camiseta");
            out.println("  3. Registrar un cliente");
            out.println("  4. Consultar un cliente (direcciones y pedidos)");
            out.println("  5. Hacer un pedido");
            out.println("  6. Ver un pedido");
            out.println("  7. Pagar un pedido");
            out.println("  8. Cancelar un pedido");
            out.println("  9. Reponer stock");
            out.println(" 10. Consultas de gestión");
            out.println("  0. Volver al menú principal");
            int opcion = consola.opcion("Elige una opción", 0, 10);
            if (opcion == 0) {
                log.info("Salida de la tienda (modo normal)");
                return;
            }
            ejecutarAccion(opcion);
        }
    }

    /** Ejecuta una opción; los errores de negocio o de base de datos se explican y se vuelve al menú. */
    private void ejecutarAccion(int opcion) {
        try {
            switch (opcion) {
                case 1 -> catalogo();
                case 2 -> detalleCamiseta();
                case 3 -> registrarCliente();
                case 4 -> consultarCliente();
                case 5 -> hacerPedido();
                case 6 -> verPedido();
                case 7 -> pagarPedido();
                case 8 -> cancelarPedido();
                case 9 -> reponerStock();
                case 10 -> consultasDeGestion();
                default -> throw new IllegalStateException("Opción sin acción: " + opcion);
            }
        } catch (NegocioException e) {
            out.println("\n✗ No se ha podido completar: " + e.getMessage());
        } catch (PersistenciaException e) {
            log.error("Error de persistencia en la opción {} del menú: {}", opcion, e.getMessage());
            out.println("\n✗ Error de base de datos: " + e.getMensajeDriver());
        } catch (ConexionException e) {
            log.error("Error de conexión en la opción {} del menú: {}", opcion, e.getMessage());
            out.println("\n✗ No hay conexión con la base de datos: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ catálogo

    private void catalogo() {
        List<Categoria> categorias = servicios.catalogo().listarCategorias();
        out.println("\nCategorías:");
        for (int i = 0; i < categorias.size(); i++) {
            out.printf("  %d. %s%n", i + 1, categorias.get(i).nombre());
        }
        int cat = consola.opcion("Categoría (0 = todas)", 0, categorias.size());
        String jugador = consola.leer("Jugador (Enter = todos)");
        BigDecimal maximo = consola.decimalOpcional("Precio máximo en € (Enter = sin límite)");
        boolean disponibles = consola.confirmar("¿Solo camisetas disponibles?");

        CatalogoFiltro filtro = new CatalogoFiltro(null, null, cat == 0 ? null : categorias.get(cat - 1).id(),
                jugador.isBlank() ? null : jugador, null, maximo, disponibles);

        int pagina = 1;
        while (true) {
            Pagina<CamisetaDetalleDTO> resultado = servicios.catalogo().consultar(filtro, pagina, TAMANO_PAGINA);
            if (resultado.totalElementos() == 0) {
                out.println("\nNo hay camisetas con esos filtros.");
                return;
            }
            out.printf("%nPágina %d de %d (%d camisetas)%n", resultado.numero(), resultado.totalPaginas(), resultado.totalElementos());
            resultado.elementos().forEach(presentacion::camiseta);

            String nav = consola.leer("[S] siguiente · [A] anterior · [Enter] volver").toLowerCase(Locale.ROOT);
            if (nav.isEmpty()) {
                return;
            }
            if (nav.equals("s") && pagina < resultado.totalPaginas()) {
                pagina++;
            } else if (nav.equals("a") && pagina > 1) {
                pagina--;
            }
        }
    }

    private void detalleCamiseta() {
        Camiseta camiseta = servicios.catalogo().buscarPorSku(consola.obligatorio("SKU de la camiseta (p. ej. CAM-BRA-70-PEL)").toUpperCase(Locale.ROOT));
        Optional<CamisetaDetalleDTO> detalle = servicios.catalogo().detalle(camiseta.id());
        out.println();
        detalle.ifPresent(presentacion::camiseta);
        out.println("  Categorías: " + servicios.catalogo().categoriasDe(camiseta.id()).stream().map(Categoria::nombre).toList());
        if (camiseta.jugadorId() != null) {
            out.println("  Dorsal: " + camiseta.dorsal() + " · Temporada: " + camiseta.temporada() + " · Equipación: " + camiseta.equipacion());
        }
    }

    // ------------------------------------------------------------------ clientes

    private void registrarCliente() {
        String nombre = consola.obligatorio("Nombre");
        String email = consola.obligatorio("Email");
        List<Direccion> direcciones = new ArrayList<>();
        do {
            out.println("Dirección " + (direcciones.size() + 1) + ":");
            String calle = consola.obligatorio("  Calle y número");
            String ciudad = consola.obligatorio("  Ciudad");
            String cp = consola.obligatorio("  Código postal");
            String pais = consola.leer("  País (Enter = España)");
            direcciones.add(new Direccion(null, 0, calle, ciudad, cp, pais.isBlank() ? "España" : pais, direcciones.isEmpty()));
        } while (consola.confirmar("¿Añadir otra dirección?"));

        long id = servicios.clientes().registrar(new Cliente(null, nombre, email, LocalDate.now()), direcciones);
        out.println("\n✓ Cliente registrado con id " + id + " y " + direcciones.size() + " dirección(es).");
    }

    private void consultarCliente() {
        Cliente cliente = servicios.clientes().buscarPorEmail(consola.obligatorio("Email del cliente"));
        ClienteCompletoDTO completo = servicios.clientes().obtenerCompleto(cliente.id());
        out.printf("%n%s <%s> · cliente desde %s%n", cliente.nombre(), cliente.email(), cliente.fechaAlta());
        out.println("Direcciones:");
        completo.direcciones().forEach(d -> out.printf("  #%d %s, %s %s (%s)%s%n", d.id(), d.calle(), d.codigoPostal(), d.ciudad(), d.pais(),
                d.principal() ? " · principal" : ""));
        out.println("Pedidos:");
        if (completo.pedidos().isEmpty()) {
            out.println("  (ninguno)");
        }
        completo.pedidos().forEach(x -> out.printf("  Pedido %d · %s · %s · %d línea(s), %d unidad(es) · %s%n",
                x.pedidoId(), x.fecha(), x.estado(), x.lineas(), x.unidades(), Presentacion.eur(x.total())));
    }

    // ------------------------------------------------------------------ pedidos

    private void hacerPedido() {
        Cliente cliente = servicios.clientes().buscarPorEmail(consola.obligatorio("Email del cliente"));
        List<Direccion> direcciones = servicios.clientes().obtenerCompleto(cliente.id()).direcciones();
        out.println("Direcciones de envío de " + cliente.nombre() + ":");
        for (int i = 0; i < direcciones.size(); i++) {
            Direccion d = direcciones.get(i);
            out.printf("  %d. %s, %s%s%n", i + 1, d.calle(), d.ciudad(), d.principal() ? " (principal)" : "");
        }
        Direccion direccion = direcciones.get(consola.opcion("Dirección de envío", 1, direcciones.size()) - 1);

        List<ItemPedido> items = new ArrayList<>();
        out.println("\nAñade camisetas al pedido (Enter en el SKU para terminar):");
        while (true) {
            String sku = consola.leer("  SKU").toUpperCase(Locale.ROOT);
            if (sku.isEmpty()) {
                break;
            }
            try {
                Camiseta camiseta = servicios.catalogo().buscarPorSku(sku);
                int cantidad = consola.entero("  Cantidad de " + camiseta.nombre());
                items.add(new ItemPedido(camiseta.id(), cantidad));
                out.println("  + añadida");
            } catch (NegocioException e) {
                out.println("  ✗ " + e.getMessage());
            }
        }
        if (items.isEmpty()) {
            out.println("\nPedido cancelado: no se añadió ninguna camiseta.");
            return;
        }

        long pedidoId = servicios.pedidos().crearPedido(new SolicitudPedido(cliente.id(), direccion.id(), items), false);
        out.println("\n✓ Pedido " + pedidoId + " registrado.");
        presentacion.pedido(servicios.pedidos().detalle(pedidoId));

        if (consola.confirmar("\n¿Pagar ahora?")) {
            pagar(pedidoId);
        }
    }

    private void verPedido() {
        presentacion.pedido(servicios.pedidos().detalle(consola.enteroLargo("Número de pedido")));
    }

    private void pagarPedido() {
        pagar(consola.enteroLargo("Número de pedido"));
    }

    private void pagar(long pedidoId) {
        Pago.Metodo[] metodos = Pago.Metodo.values();
        for (int i = 0; i < metodos.length; i++) {
            out.printf("  %d. %s%n", i + 1, metodos[i]);
        }
        Pago.Metodo metodo = metodos[consola.opcion("Método de pago (simulado)", 1, metodos.length) - 1];
        long pagoId = servicios.pedidos().pagar(pedidoId, metodo);
        out.println("\n✓ Pago " + pagoId + " registrado. El pedido " + pedidoId + " queda PAGADO.");
    }

    private void cancelarPedido() {
        long pedidoId = consola.enteroLargo("Número de pedido");
        presentacion.pedido(servicios.pedidos().detalle(pedidoId));
        if (!consola.confirmar("\n¿Cancelar este pedido?")) {
            out.println("Cancelación abortada.");
            return;
        }
        int unidades = servicios.pedidos().cancelar(pedidoId);
        out.println("\n✓ Pedido " + pedidoId + " cancelado. Unidades devueltas al stock: " + unidades + ".");
    }

    // ------------------------------------------------------------------ inventario y gestión

    private void reponerStock() {
        Camiseta camiseta = servicios.catalogo().buscarPorSku(consola.obligatorio("SKU de la camiseta").toUpperCase(Locale.ROOT));
        out.println("Stock actual: " + servicios.inventario().consultarStock(camiseta.id()).stock());
        int cantidad = consola.entero("Unidades que entran");
        String motivo = consola.leer("Motivo (Enter = Reposición manual)");
        int stock = servicios.inventario().reponer(camiseta.id(), cantidad, motivo.isBlank() ? "Reposición manual" : motivo);
        out.println("\n✓ Reposición registrada. Stock de " + camiseta.sku() + ": " + stock);
        out.println("Últimos movimientos:");
        List<es.camisetas.model.MovimientoStock> movimientos = servicios.inventario().movimientos(camiseta.id());
        movimientos.subList(Math.max(0, movimientos.size() - 5), movimientos.size()).forEach(presentacion::movimiento);
    }

    private void consultasDeGestion() {
        out.println("\n  1. Camisetas más vendidas");
        out.println("  2. Ventas por era");
        out.println("  3. Clientes (pedidos y total gastado)");
        out.println("  4. Stock bajo");
        out.println("  5. Resumen de un equipo (procedimiento almacenado, solo MySQL)");
        out.println("  0. Volver");
        switch (consola.opcion("Elige una consulta", 0, 5)) {
            case 1 -> servicios.estadisticas().camisetasMasVendidas(5).forEach(v ->
                    out.printf("  %-16s %-45s unidades=%d ingresos=%s%n", v.sku(), v.nombre(), v.unidades(), Presentacion.eur(v.ingresos())));
            case 2 -> servicios.estadisticas().ventasPorEra().forEach(v ->
                    out.printf("  %-12s unidades=%d ingresos=%s%n", v.era(), v.unidades(), Presentacion.eur(v.ingresos())));
            case 3 -> servicios.clientes().resumenDeClientes().forEach(v ->
                    out.printf("  %-16s pedidos=%d gastado=%s%n", v.nombre(), v.numPedidos(), Presentacion.eur(v.totalGastado())));
            case 4 -> servicios.inventario().stockBajo().forEach(presentacion::stockBajo);
            case 5 -> {
                String equipo = consola.leer("Equipo (Enter = FC Barcelona)");
                long equipoId = servicios.catalogo().idDeEquipo(equipo.isBlank() ? "FC Barcelona" : equipo);
                servicios.estadisticas().resumenDeEquipo(equipoId).ifPresentOrElse(
                        r -> out.printf("  %s: unidades=%d ingresos=%s pedidos=%d%n", r.equipo(), r.unidades(), Presentacion.eur(r.ingresos()), r.pedidos()),
                        () -> out.println("  No disponible con este gestor: sp_resumen_equipo es específico de MySQL."));
            }
            default -> { /* volver */ }
        }
    }
}