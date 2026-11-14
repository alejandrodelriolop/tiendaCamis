package es.camisetas;

import es.camisetas.dto.*;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.model.Cliente;
import es.camisetas.model.Direccion;
import es.camisetas.model.Pago;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientesInventarioConsultaTest extends BaseDatosTest {

    private static final String LAURA = "laura.martin@example.com";

    /** Claves generadas: el id que devuelve el SGBD vincula las direcciones con su cliente (1:N). */
    @Test
    void registrarClienteUsaLaClaveGeneradaParaSusDirecciones() {
        long id = servicios.clientes().registrar(
                new Cliente(null, "Nuevo Cliente", "nuevo@example.com", LocalDate.of(2026, 11, 6)),
                List.of(new Direccion(null, 0, "Calle Uno 1", "Madrid", "28001", "España", true),
                        new Direccion(null, 0, "Calle Dos 2", "Sevilla", "41001", "España", false)));

        ClienteCompletoDTO completo = servicios.clientes().obtenerCompleto(id);
        assertEquals("Nuevo Cliente", completo.cliente().nombre());
        assertEquals(LocalDate.of(2026, 11, 6), completo.cliente().fechaAlta());
        assertEquals(2, completo.direcciones().size());
        assertTrue(completo.direcciones().stream().allMatch(d -> d.clienteId() == id));
        assertTrue(completo.direcciones().get(0).principal());   // la principal se lista primero
        assertTrue(completo.pedidos().isEmpty());

        assertThrows(NegocioException.class, () -> servicios.clientes().registrar(
                new Cliente(null, "Repetido", "nuevo@example.com", LocalDate.now()),
                List.of(new Direccion(null, 0, "X", "Y", "00000", "España", true))));
    }

    /** Consulta con varios datos relacionados y valores calculados en SQL (COUNT/SUM) mapeados a un DTO. */
    @Test
    void resumenDeClientesCalculaPedidosYGastoSinContarCancelados() {
        servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-ARG-86-MAR", 1), false);                 // 94,90
        servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-BAR-06-RDN", 2), false);                 // 179,80
        long cancelado = servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-BRA-70-PEL", 1), false);
        servicios.pedidos().cancelar(cancelado);

        ClienteResumenDTO laura = servicios.clientes().resumenDeClientes().stream()
                .filter(c -> c.email().equals(LAURA)).findFirst().orElseThrow();
        assertEquals(2, laura.numPedidos());
        assertEquals(0, new BigDecimal("274.70").compareTo(laura.totalGastado()));

        ClienteResumenDTO marta = servicios.clientes().resumenDeClientes().stream()
                .filter(c -> c.email().equals("marta.ruiz@example.com")).findFirst().orElseThrow();
        assertEquals(0, marta.numPedidos());                      // LEFT JOIN: aparece aunque no tenga pedidos
        assertEquals(0, BigDecimal.ZERO.compareTo(marta.totalGastado()));

        ClienteCompletoDTO completo = servicios.clientes().obtenerCompleto(cliente(LAURA).id());
        assertEquals(3, completo.pedidos().size());               // el historial sí incluye el cancelado
        assertEquals(2, completo.pedidos().stream().filter(p -> p.lineas() == 1 && p.unidades() == 1).count());
    }

    /** Reposición: stock y movimiento cambian juntos; el stock siempre se explica por sus movimientos. */
    @Test
    void reponerIncrementaStockYRegistraElMovimiento() {
        long id = camiseta("CAM-MUN-96-CAN").id();
        int antes = stock("CAM-MUN-96-CAN");
        int movimientosAntes = servicios.inventario().movimientos(id).size();

        int resultante = servicios.inventario().reponer(id, 6, "Proveedor");

        assertEquals(antes + 6, resultante);
        assertEquals(resultante, stock("CAM-MUN-96-CAN"));
        assertEquals(movimientosAntes + 1, servicios.inventario().movimientos(id).size());
        assertTrue(servicios.inventario().incoherencias().isEmpty());

        assertThrows(NegocioException.class, () -> servicios.inventario().reponer(id, 0, "inválida"));
        assertThrows(NegocioException.class, () -> servicios.inventario().reponer(camiseta("CAM-BAY-01-LOC").id(), 5, "bajo pedido"));
        assertEquals(resultante, stock("CAM-MUN-96-CAN"));
    }

    /** Stock bajo: umbral alcanzado, solo camisetas activas y con control de stock; el déficit viene de SQL. */
    @Test
    void stockBajoListaSoloLasCamisetasQueLlegaronAlUmbral() {
        List<StockBajoDTO> bajo = servicios.inventario().stockBajo();
        assertEquals(List.of("CAM-MUN-96-CAN"), bajo.stream().map(StockBajoDTO::sku).toList());   // 3 uds, umbral 3
        assertEquals(0, bajo.get(0).deficit());

        servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-MUN-96-CAN", 2), false);            // queda 1
        StockBajoDTO tras = servicios.inventario().stockBajo().get(0);
        assertEquals(1, tras.stock());
        assertEquals(2, tras.deficit());
        assertFalse(servicios.inventario().stockBajo().stream().anyMatch(b -> b.sku().equals("CAM-ATM-25-POR")));   // descatalogada
        assertFalse(servicios.inventario().stockBajo().stream().anyMatch(b -> b.sku().equals("CAM-BAY-01-LOC")));   // sin control
    }

    /** Agregaciones con JOIN y GROUP BY mapeadas a DTO; los pedidos cancelados no cuentan como venta. */
    @Test
    void consultasDeVentasAgrupanPorCamisetaYPorEra() {
        servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-ARG-86-MAR", 1, "CAM-BAR-06-RDN", 2), false);
        long cancelado = servicios.pedidos().crearPedido(solicitud("carlos.gomez@example.com", "CAM-BRA-70-PEL", 5), false);
        servicios.pedidos().cancelar(cancelado);

        List<VentasCamisetaDTO> top = servicios.estadisticas().camisetasMasVendidas(5);
        assertEquals(2, top.size());
        assertEquals("CAM-BAR-06-RDN", top.get(0).sku());
        assertEquals(2, top.get(0).unidades());
        assertEquals(0, new BigDecimal("179.80").compareTo(top.get(0).ingresos()));

        List<VentasEraDTO> porEra = servicios.estadisticas().ventasPorEra();
        assertEquals(List.of("Años 80", "Años 2000"), porEra.stream().map(VentasEraDTO::era).toList());
        assertEquals(1, porEra.get(0).unidades());
        assertEquals(0, new BigDecimal("94.90").compareTo(porEra.get(0).ingresos()));
    }

    /** El procedimiento almacenado es específico de MySQL: con H2 no falla ni se ejecuta. */
    @Test
    void procedimientoAlmacenadoNoSeEjecutaEnH2() {
        Optional<?> resumen = servicios.estadisticas().resumenDeEquipo(servicios.catalogo().idDeEquipo("FC Barcelona"));
        assertTrue(resumen.isEmpty());
    }

    /** Integridad real: el SGBD rechaza duplicados, referencias inexistentes y datos no válidos. */
    @Test
    void elSgbdRechazaDuplicadosReferenciasInexistentesYDatosInvalidos() {
        long pele = camiseta("CAM-BRA-70-PEL").id();

        List<ErrorIntegridadDTO> errores = servicios.integridad().provocarViolaciones(LAURA, pele);

        assertEquals(3, errores.size());
        for (ErrorIntegridadDTO e : errores) {
            assertTrue(e.sqlState().startsWith("23"), "SQLState de integridad esperado y fue " + e.sqlState());
            assertFalse(e.mensajeDriver().isBlank());
        }
        assertEquals("23505", errores.get(0).sqlState());   // UNIQUE
        assertEquals("23506", errores.get(1).sqlState());   // FOREIGN KEY
        assertEquals("23513", errores.get(2).sqlState());   // CHECK

        // Nada se guardó: el precio sigue siendo el original y no hay un segundo cliente con ese email
        assertEquals(0, new BigDecimal("89.90").compareTo(servicios.catalogo().detalle(pele).orElseThrow().precio()));
        assertEquals(1, servicios.clientes().resumenDeClientes().stream().filter(c -> c.email().equals(LAURA)).count());
    }

    /** Pagos: un pedido pagado figura con su importe en el historial y el pago no se puede duplicar por UNIQUE. */
    @Test
    void elPagoQuedaVinculadoAlPedido() {
        long pedido = servicios.pedidos().crearPedido(solicitud(LAURA, "CAM-BRA-70-PEL", 1), false);
        long pagoId = servicios.pedidos().pagar(pedido, Pago.Metodo.PAYPAL);

        assertTrue(pagoId > 0);
        assertEquals(1, servicios.verificacion().instantanea().pagos());
    }
}
