package es.camisetas;

import es.camisetas.config.ModoConexion;
import es.camisetas.dao.PagoDAO;
import es.camisetas.dto.InstantaneaBD;
import es.camisetas.dto.PedidoDetalleDTO;
import es.camisetas.exceptions.FalloControladoException;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.model.MovimientoStock;
import es.camisetas.model.Pago;
import es.camisetas.model.Pedido;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PedidosTransaccionesTest extends BaseDatosTest {

    private static final String LAURA = "laura.martin@example.com";

    /** Commit: el pedido, sus líneas, el descuento de stock y los movimientos se confirman juntos. */
    @ParameterizedTest
    @EnumSource(ModoConexion.class)
    void crearPedidoConfirmaTodasSusModificaciones(ModoConexion modo) {
        prepararCon(gestorBD(),modo);
        InstantaneaBD antes = servicios.verificacion().instantanea();
        int stockMaradona = stock("CAM-ARG-86-MAR");
        int stockRonaldinho = stock("CAM-BAR-06-RDN");

        long pedidoId = servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-ARG-86-MAR", 1, "CAM-BAR-06-RDN", 2), false);

        PedidoDetalleDTO pedido = servicios.pedidos().detalle(pedidoId);
        assertEquals(Pedido.Estado.PENDIENTE, pedido.estado());
        assertEquals("Laura Martín", pedido.cliente());
        assertEquals(2, pedido.lineas().size());
        assertEquals(0, new BigDecimal("274.70").compareTo(pedido.total()));  // 94,90 + 2 x 89,90
        assertEquals(0, new BigDecimal("179.80").compareTo(pedido.lineas().get(1).importe()));

        assertEquals(stockMaradona - 1, stock("CAM-ARG-86-MAR"));
        assertEquals(stockRonaldinho - 2, stock("CAM-BAR-06-RDN"));

        InstantaneaBD despues = servicios.verificacion().instantanea();
        assertEquals(antes.pedidos() + 1, despues.pedidos());
        assertEquals(antes.lineas() + 2, despues.lineas());
        assertEquals(antes.movimientos() + 2, despues.movimientos());
        assertEquals(0, despues.pedidosSinLineas());
        assertTrue(servicios.inventario().incoherencias().isEmpty());
    }

    /** Rollback: un fallo tras modificar varias tablas no deja ningún cambio parcial. */
    @ParameterizedTest
    @EnumSource(ModoConexion.class)
    void falloControladoDeshaceTodasLasModificaciones(ModoConexion modo) {
        prepararCon(gestorBD(),modo);
        InstantaneaBD antes = servicios.verificacion().instantanea();
        int stockMaradona = stock("CAM-ARG-86-MAR");
        int stockRonaldinho = stock("CAM-BAR-06-RDN");

        assertThrows(FalloControladoException.class,
                () -> servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-ARG-86-MAR", 2, "CAM-BAR-06-RDN", 3), true));

        assertEquals(antes, servicios.verificacion().instantanea());          // ni pedido, ni líneas, ni movimientos
        assertEquals(0, servicios.verificacion().instantanea().pedidosSinLineas());
        assertEquals(stockMaradona, stock("CAM-ARG-86-MAR"));                  // existencias intactas
        assertEquals(stockRonaldinho, stock("CAM-BAR-06-RDN"));
        assertTrue(servicios.inventario().incoherencias().isEmpty());

        // El sistema sigue funcionando después del rollback
        long pedidoId = servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-ARG-86-MAR", 2, "CAM-BAR-06-RDN", 3), false);
        assertEquals(stockMaradona - 2, stock("CAM-ARG-86-MAR"));
        assertEquals(Pedido.Estado.PENDIENTE, servicios.pedidos().detalle(pedidoId).estado());
    }

    /** Si una línea no tiene stock, el pedido se rechaza completo y no se descuenta ninguna existencia. */
    @Test
    void stockInsuficienteEnUnaLineaRechazaTodoElPedido() {
        InstantaneaBD antes = servicios.verificacion().instantanea();
        int stockPele = stock("CAM-BRA-70-PEL");

        NegocioException e = assertThrows(NegocioException.class,
                () -> servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-BRA-70-PEL", 1, "CAM-BAR-06-RDN", 999), false));

        assertEquals(NegocioException.Causa.STOCK_INSUFICIENTE, e.getCausa());
        assertEquals(antes, servicios.verificacion().instantanea());
        assertEquals(stockPele, stock("CAM-BRA-70-PEL"));
    }

    @Test
    void reglasDeNegocioRechazanProductosYCantidadesInvalidos() {
        InstantaneaBD antes = servicios.verificacion().instantanea();

        assertEquals(NegocioException.Causa.PRODUCTO_NO_DISPONIBLE,
                assertThrows(NegocioException.class, () -> servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-ATM-25-POR", 1), false)).getCausa());
        assertEquals(NegocioException.Causa.LIMITE_EDICION,
                assertThrows(NegocioException.class, () -> servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-ARG-22-MES", 3), false)).getCausa());
        assertEquals(NegocioException.Causa.VALIDACION,
                assertThrows(NegocioException.class, () -> servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-BRA-70-PEL", 0), false)).getCausa());

        assertEquals(antes, servicios.verificacion().instantanea());
    }

    /** Un producto sin control de stock (bajo pedido) se vende sin tocar existencias ni generar movimientos. */
    @Test
    void productoBajoPedidoNoGeneraMovimientosDeStock() {
        InstantaneaBD antes = servicios.verificacion().instantanea();

        servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-BAY-01-LOC", 3), false);

        InstantaneaBD despues = servicios.verificacion().instantanea();
        assertEquals(antes.lineas() + 1, despues.lineas());
        assertEquals(antes.movimientos(), despues.movimientos());
        assertEquals(antes.stockTotal(), despues.stockTotal());
    }

    /** Cancelación: recupera las existencias, deja los movimientos y reembolsa el pago; la segunda no repite nada. */
    @Test
    void cancelarRepoeStockReembolsaYNoPuedeRepetirse() throws SQLException {
        int stockInicial = stock("CAM-BRA-70-PEL");
        long pedidoId = servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-BRA-70-PEL", 2), false);
        servicios.pedidos().pagar(pedidoId, Pago.Metodo.TARJETA);
        assertEquals(stockInicial - 2, stock("CAM-BRA-70-PEL"));
        assertEquals(Pago.Estado.COMPLETADO, estadoPago(pedidoId));

        int repuestas = servicios.pedidos().cancelar(pedidoId);

        assertEquals(2, repuestas);
        assertEquals(stockInicial, stock("CAM-BRA-70-PEL"));
        assertEquals(Pedido.Estado.CANCELADO, servicios.pedidos().detalle(pedidoId).estado());
        assertEquals(Pago.Estado.REEMBOLSADO, estadoPago(pedidoId));

        List<MovimientoStock> movimientos = servicios.inventario().movimientos(camiseta("CAM-BRA-70-PEL").id());
        assertEquals(MovimientoStock.Tipo.ENTRADA_CANCELACION, movimientos.get(movimientos.size() - 1).tipo());
        assertEquals(pedidoId, movimientos.get(movimientos.size() - 1).pedidoId());

        InstantaneaBD trasPrimera = servicios.verificacion().instantanea();
        NegocioException e = assertThrows(NegocioException.class, () -> servicios.pedidos().cancelar(pedidoId));
        assertEquals(NegocioException.Causa.PEDIDO_YA_CANCELADO, e.getCausa());
        assertEquals(trasPrimera, servicios.verificacion().instantanea());          // no se devolvió dos veces
        assertEquals(stockInicial, stock("CAM-BRA-70-PEL"));
        assertTrue(servicios.inventario().incoherencias().isEmpty());
    }

    /** Ciclo de vida del pago: PENDIENTE -> PAGADO; no se paga dos veces ni un pedido cancelado. */
    @Test
    void cicloDeVidaDelPago() {
        long pedidoId = servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-BRA-70-PEL", 1), false);
        servicios.pedidos().pagar(pedidoId, Pago.Metodo.BIZUM);

        assertEquals(Pedido.Estado.PAGADO, servicios.pedidos().detalle(pedidoId).estado());
        assertEquals(NegocioException.Causa.ESTADO_INVALIDO,
                assertThrows(NegocioException.class, () -> servicios.pedidos().pagar(pedidoId, Pago.Metodo.BIZUM)).getCausa());

        long otro = servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-BRA-70-PEL", 1), false);
        servicios.pedidos().cancelar(otro);
        assertEquals(NegocioException.Causa.ESTADO_INVALIDO,
                assertThrows(NegocioException.class, () -> servicios.pedidos().pagar(otro, Pago.Metodo.TARJETA)).getCausa());
    }

    /** El pedido conserva el precio de la compra aunque el catálogo cambie después. */
    @Test
    void elPedidoConservaElPrecioAplicadoAlComprar() {
        long pedidoId = servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-BRA-70-PEL", 1), false);
        servicios.catalogo().actualizarPrecio(camiseta("CAM-BRA-70-PEL").id(), new BigDecimal("200.00"));

        PedidoDetalleDTO pedido = servicios.pedidos().detalle(pedidoId);
        assertEquals(0, new BigDecimal("89.90").compareTo(pedido.lineas().get(0).precioUnitario()));
        assertEquals(0, new BigDecimal("89.90").compareTo(pedido.total()));
        assertNotEquals(0, new BigDecimal("200.00").compareTo(pedido.total()));
    }

    private Pago.Estado estadoPago(long pedidoId) throws SQLException {
        try (Connection con = provider.getConnection()) {
            return new PagoDAO().buscarPorPedido(con, pedidoId).orElseThrow().estado();
        }
    }
}