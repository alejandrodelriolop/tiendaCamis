package es.camisetas.app;



import es.camisetas.config.Configuracion;
import es.camisetas.config.ModoSimulacion;
import es.camisetas.dto.CamisetaDetalleDTO;
import es.camisetas.dto.CatalogoFiltro;
import es.camisetas.dto.ClienteCompletoDTO;
import es.camisetas.dto.ErrorIntegridadDTO;
import es.camisetas.dto.InstantaneaBD;
import es.camisetas.dto.ItemPedido;
import es.camisetas.dto.Pagina;
import es.camisetas.dto.PedidoDetalleDTO;
import es.camisetas.dto.SolicitudPedido;
import es.camisetas.exceptions.FalloControladoException;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.model.Camiseta;
import es.camisetas.model.Categoria;
import es.camisetas.model.Cliente;
import es.camisetas.model.Direccion;
import es.camisetas.model.MovimientoStock;
import es.camisetas.model.Pago;
import es.camisetas.services.Services;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Recorrido de demostración. Solo orquesta servicios y presenta resultados: no contiene SQL.
 * Todo lo que se muestra se vuelve a leer de la base de datos.
 */
public class Simulacion {

    private static final Logger log = LogManager.getLogger(Simulacion.class);

    private final Services servicios;
    private final Configuracion config;
    private final PrintStream out;
    private final Presentacion presentacion;

    public Simulacion(Services servicios, Configuracion config, PrintStream out) {
        this.servicios = servicios;
        this.config = config;
        this.out = out;
        this.presentacion = new Presentacion(out);
    }

    /** Ejecuta la simulación indicada en {@code simulation.mode} (arranque sin menú). */
    public void ejecutar() {
        ejecutar(config.modoSimulacion());
    }

    public void ejecutar(ModoSimulacion modo) {
        log.info("Inicio de la simulación en modo {}", modo);
        titulo("TIENDA DE CAMISETAS DE FÚTBOL · gestor=" + config.gestor() + " · conexiones=" + config.modoConexion()
                + " · simulación=" + modo);
        if (modo == ModoSimulacion.FALLO_TRANSACCION) {
            demostrarFalloTransaccion();
        } else {
            recorridoNormal();
        }
        log.info("Fin de la simulación en modo {}", modo);
    }
    // ================================================================= modo NORMAL

    private void recorridoNormal() {
        consultarCatalogo();
        gestionarClientes();
        gestionarInventario();
        long pedidoA = operarPedidos();
        cancelaciones(pedidoA);
        consultasDeGestion();
        procedimientoAlmacenado();
        integridad();
        verificarCoherencia();
    }

    private void consultarCatalogo() {
        titulo("1. CATÁLOGO (paginado en SQL)");
        CatalogoFiltro todo = CatalogoFiltro.todo();
        for (int pagina = 1; pagina <= 2; pagina++) {
            Pagina<CamisetaDetalleDTO> p = servicios.catalogo().consultar(todo, pagina, 5);
            out.printf("Página %d de %d (%d camisetas en total, 5 por página)%n", p.numero(), p.totalPaginas(), p.totalElementos());
            p.elementos().forEach(this::imprimir);
        }

        long retro = categoriaId("Retro");
        out.println("\nFiltro: categoría Retro, hasta 85,00 €, solo disponibles");
        servicios.catalogo().consultar(CatalogoFiltro.todo().conCategoria(retro).conPrecio(null, new BigDecimal("85.00")).disponibles(), 1, 10)
                .elementos().forEach(this::imprimir);

        long era2000 = servicios.catalogo().idDeEra("Años 2000");
        out.println("\nFiltro: era \"Años 2000\"");
        servicios.catalogo().consultar(CatalogoFiltro.todo().conEra(era2000), 1, 10).elementos().forEach(this::imprimir);

        out.println("\nConsulta temática: la carrera de Zidane a través de eras y equipos");
        servicios.catalogo().camisetasDeJugador("Zidane").forEach(this::imprimir);

        Camiseta maradona = servicios.catalogo().buscarPorSku("CAM-ARG-86-MAR");
        List<Categoria> categorias = servicios.catalogo().categoriasDe(maradona.id());
        out.println("\nCategorías (N:M) de " + maradona.nombre() + ": " + categorias.stream().map(Categoria::nombre).toList());
    }

    private void gestionarClientes() {
        titulo("2. CLIENTES");
        long nuevoId = servicios.clientes().registrar(
                new Cliente(null, "Sofía Herrera", "sofia.herrera@example.com", LocalDate.of(2026, 11, 6)),
                List.of(new Direccion(null, 0, "Calle Goya 77", "Madrid", "28001", "España", true),
                        new Direccion(null, 0, "Paseo de Gracia 10", "Barcelona", "08007", "España", false)));
        out.println("Cliente registrado con id " + nuevoId);

        Cliente laura = servicios.clientes().buscarPorEmail("laura.martin@example.com");
        ClienteCompletoDTO completo = servicios.clientes().obtenerCompleto(laura.id());
        out.println("Cliente " + completo.cliente().nombre() + " <" + completo.cliente().email() + ">");
        completo.direcciones().forEach(d -> out.println("  Dirección: " + d.calle() + ", " + d.ciudad() + (d.principal() ? " (principal)" : "")));
        out.println("  Pedidos: " + completo.pedidos().size());

        try {
            servicios.clientes().registrar(new Cliente(null, "Otra Laura", "laura.martin@example.com", LocalDate.now()),
                    List.of(new Direccion(null, 0, "X", "Y", "00000", "España", true)));
        } catch (NegocioException e) {
            out.println("Registro rechazado (regla de negocio): " + e.getMessage());
        }
    }

    private void gestionarInventario() {
        titulo("3. INVENTARIO Y REPOSICIÓN");
        out.println("Camisetas con stock bajo (stock <= umbral):");
        servicios.inventario().stockBajo().forEach(b -> out.printf("  %-16s stock=%d umbral=%d déficit=%d%n", b.sku(), b.stock(), b.umbral(), b.deficit()));

        Camiseta cantona = servicios.catalogo().buscarPorSku("CAM-MUN-96-CAN");
        int antes = servicios.inventario().consultarStock(cantona.id()).stock();
        int despues = servicios.inventario().reponer(cantona.id(), 6, "Reposición del proveedor");
        out.printf("%nReposición de 6 unidades de %s: stock %d -> %d%n", cantona.sku(), antes, despues);
        out.println("Movimientos de " + cantona.sku() + ":");
        servicios.inventario().movimientos(cantona.id()).forEach(this::imprimir);

        try {
            servicios.inventario().reponer(cantona.id(), 0, "cantidad inválida");
        } catch (NegocioException e) {
            out.println("Reposición rechazada: " + e.getMessage());
        }

        Camiseta bayern = servicios.catalogo().buscarPorSku("CAM-BAY-01-LOC");
        try {
            servicios.inventario().reponer(bayern.id(), 3, "no aplica");
        } catch (NegocioException e) {
            out.println("Reposición rechazada: " + e.getMessage());
        }
    }

    /** @return id del pedido que se cancela después */
    private long operarPedidos() {
        titulo("4. PEDIDOS Y PAGOS");
        Cliente laura = servicios.clientes().buscarPorEmail("laura.martin@example.com");
        Cliente carlos = servicios.clientes().buscarPorEmail("carlos.gomez@example.com");
        Cliente marta = servicios.clientes().buscarPorEmail("marta.ruiz@example.com");
        Camiseta maradona = servicios.catalogo().buscarPorSku("CAM-ARG-86-MAR");
        Camiseta ronaldinho = servicios.catalogo().buscarPorSku("CAM-BAR-06-RDN");
        Camiseta pele = servicios.catalogo().buscarPorSku("CAM-BRA-70-PEL");
        Camiseta messi = servicios.catalogo().buscarPorSku("CAM-BAR-15-MES");
        Camiseta zidane = servicios.catalogo().buscarPorSku("CAM-RMA-02-ZID");

        out.println("Stock antes del pedido 1: Maradona=" + stock(maradona) + ", Ronaldinho=" + stock(ronaldinho));
        long pedido1 = servicios.pedidos().crearPedido(solicitud(laura, new ItemPedido(maradona.id(), 1), new ItemPedido(ronaldinho.id(), 2)), false);
        servicios.pedidos().pagar(pedido1, Pago.Metodo.TARJETA);
        imprimir(servicios.pedidos().detalle(pedido1));
        out.println("Stock después del pedido 1: Maradona=" + stock(maradona) + ", Ronaldinho=" + stock(ronaldinho));

        long pedido2 = servicios.pedidos().crearPedido(solicitud(carlos, new ItemPedido(pele.id(), 2), new ItemPedido(messi.id(), 3)), false);
        servicios.pedidos().pagar(pedido2, Pago.Metodo.BIZUM);

        long pedido3 = servicios.pedidos().crearPedido(solicitud(marta, new ItemPedido(zidane.id(), 1), new ItemPedido(messi.id(), 1)), false);
        servicios.pedidos().pagar(pedido3, Pago.Metodo.PAYPAL);

        // Pedido pendiente de pago: se cancelará sin reembolso
        long pedidoPendiente = servicios.pedidos().crearPedido(solicitud(laura, new ItemPedido(pele.id(), 1)), false);
        out.println("\nPedidos registrados: " + pedido1 + ", " + pedido2 + ", " + pedido3 + " (pagados) y " + pedidoPendiente + " (pendiente)");

        out.println("\nPedidos rechazados por reglas de negocio (la base de datos no cambia):");
        InstantaneaBD antes = servicios.verificacion().instantanea();
        Camiseta coleccionista = servicios.catalogo().buscarPorSku("CAM-ARG-22-MES");
        Camiseta portero = servicios.catalogo().buscarPorSku("CAM-ATM-25-POR");
        rechazar("Stock insuficiente", solicitud(laura, new ItemPedido(ronaldinho.id(), 999)));
        rechazar("Límite de edición coleccionista", solicitud(laura, new ItemPedido(coleccionista.id(), 3)));
        rechazar("Producto descatalogado", solicitud(laura, new ItemPedido(portero.id(), 1)));
        rechazar("Cantidad no positiva", solicitud(laura, new ItemPedido(pele.id(), 0)));
        rechazar("Dirección de otro cliente", new SolicitudPedido(laura.id(), servicios.clientes().obtenerCompleto(carlos.id()).direcciones().get(0).id(),
                List.of(new ItemPedido(pele.id(), 1))));
        InstantaneaBD despues = servicios.verificacion().instantanea();
        out.println("Estado de la base de datos igual antes y después de los rechazos: " + antes.equals(despues));

        try {
            servicios.pedidos().pagar(pedido1, Pago.Metodo.TARJETA);
        } catch (NegocioException e) {
            out.println("Segundo pago rechazado: " + e.getMessage());
        }
        // Se cancela el pedido pagado número 2 en la sección siguiente; el pendiente aquí
        this.pedidoPendiente = pedidoPendiente;
        return pedido2;
    }

    private long pedidoPendiente;

    private void cancelaciones(long pedidoPagado) {
        titulo("5. CANCELACIONES");
        Camiseta pele = servicios.catalogo().buscarPorSku("CAM-BRA-70-PEL");
        Camiseta messi = servicios.catalogo().buscarPorSku("CAM-BAR-15-MES");
        out.println("Stock antes de cancelar el pedido " + pedidoPagado + ": Pelé=" + stock(pele) + ", Messi=" + stock(messi));
        int repuestas = servicios.pedidos().cancelar(pedidoPagado);
        out.println("Pedido " + pedidoPagado + " cancelado. Unidades repuestas: " + repuestas);
        out.println("Stock después: Pelé=" + stock(pele) + ", Messi=" + stock(messi));
        imprimir(servicios.pedidos().detalle(pedidoPagado));
        out.println("Movimientos de " + pele.sku() + ":");
        servicios.inventario().movimientos(pele.id()).forEach(this::imprimir);

        try {
            servicios.pedidos().cancelar(pedidoPagado);
        } catch (NegocioException e) {
            out.println("Segunda cancelación rechazada: " + e.getMessage());
        }
        out.println("Stock tras el intento duplicado (no debe cambiar): Pelé=" + stock(pele) + ", Messi=" + stock(messi));

        int repuestasPendiente = servicios.pedidos().cancelar(pedidoPendiente);
        out.println("Pedido pendiente " + pedidoPendiente + " cancelado sin pago asociado. Unidades repuestas: " + repuestasPendiente);
        try {
            servicios.pedidos().pagar(pedidoPendiente, Pago.Metodo.TARJETA);
        } catch (NegocioException e) {
            out.println("Pago de un pedido cancelado rechazado: " + e.getMessage());
        }
    }

    private void consultasDeGestion() {
        titulo("6. CONSULTAS DE GESTIÓN");
        out.println("Camisetas más vendidas (sin pedidos cancelados):");
        servicios.estadisticas().camisetasMasVendidas(5).forEach(v ->
                out.printf("  %-16s %-45s unidades=%d ingresos=%s%n", v.sku(), v.nombre(), v.unidades(), eur(v.ingresos())));

        out.println("\nVentas por era (consulta propia de la temática):");
        servicios.estadisticas().ventasPorEra().forEach(v -> out.printf("  %-12s unidades=%d ingresos=%s%n", v.era(), v.unidades(), eur(v.ingresos())));

        out.println("\nClientes con número de pedidos y total gastado (calculados en SQL):");
        servicios.clientes().resumenDeClientes().forEach(c ->
                out.printf("  %-16s pedidos=%d gastado=%s%n", c.nombre(), c.numPedidos(), eur(c.totalGastado())));

        Cliente laura = servicios.clientes().buscarPorEmail("laura.martin@example.com");
        out.println("\nHistorial de " + laura.nombre() + ":");
        servicios.clientes().obtenerCompleto(laura.id()).pedidos().forEach(p ->
                out.printf("  Pedido %d · %s · %s · líneas=%d unidades=%d total=%s%n", p.pedidoId(), p.fecha(), p.estado(),
                        p.lineas(), p.unidades(), eur(p.total())));

        out.println("\nStock bajo tras las operaciones:");
        servicios.inventario().stockBajo().forEach(b -> out.printf("  %-16s stock=%d umbral=%d déficit=%d%n", b.sku(), b.stock(), b.umbral(), b.deficit()));
    }

    private void procedimientoAlmacenado() {
        titulo("7. PROCEDIMIENTO ALMACENADO (solo MySQL)");
        long barcelona = servicios.catalogo().idDeEquipo("FC Barcelona");
        Optional<es.camisetas.dto.ResumenEquipoDTO> resumen = servicios.estadisticas().resumenDeEquipo(barcelona);
        if (resumen.isPresent()) {
            var r = resumen.get();
            out.printf("sp_resumen_equipo(%s): unidades=%d ingresos=%s pedidos=%d%n", r.equipo(), r.unidades(), eur(r.ingresos()), r.pedidos());
        } else {
            out.println("No disponible con " + config.gestor() + ": sp_resumen_equipo es específico de MySQL. El resto de la tienda funciona igual.");
        }
    }

    private void integridad() {
        titulo("8. VIOLACIONES DE INTEGRIDAD REALES");
        Camiseta pele = servicios.catalogo().buscarPorSku("CAM-BRA-70-PEL");
        for (ErrorIntegridadDTO e : servicios.integridad().provocarViolaciones("laura.martin@example.com", pele.id())) {
            out.println(e.situacion());
            out.println("    SQLState=" + e.sqlState() + " · código=" + e.errorCode());
            out.println("    mensaje del driver: " + e.mensajeDriver());
        }
    }

    private void verificarCoherencia() {
        titulo("9. COHERENCIA FINAL (consultas SQL)");
        InstantaneaBD i = servicios.verificacion().instantanea();
        out.printf("pedidos=%d líneas=%d movimientos=%d pagos=%d pedidosSinLíneas=%d stockTotal=%d%n",
                i.pedidos(), i.lineas(), i.movimientos(), i.pagos(), i.pedidosSinLineas(), i.stockTotal());
        var incoherencias = servicios.inventario().incoherencias();
        out.println("Camisetas con stock distinto de la suma de sus movimientos: " + incoherencias.size());
        incoherencias.forEach(x -> out.println("  " + x));
    }

    // ================================================================= modo FALLO_TRANSACCION

    private void demostrarFalloTransaccion() {
        titulo("DEMOSTRACIÓN DE ROLLBACK (FALLO_TRANSACCION)");
        Cliente laura = servicios.clientes().buscarPorEmail("laura.martin@example.com");
        Camiseta maradona = servicios.catalogo().buscarPorSku("CAM-ARG-86-MAR");
        Camiseta ronaldinho = servicios.catalogo().buscarPorSku("CAM-BAR-06-RDN");

        InstantaneaBD antes = servicios.verificacion().instantanea();
        int stockMaradonaAntes = stock(maradona);
        int stockRonaldinhoAntes = stock(ronaldinho);
        out.println("ANTES   : " + describir(antes));
        out.println("          stock Maradona=" + stockMaradonaAntes + ", Ronaldinho=" + stockRonaldinhoAntes);

        out.println("\nSe registra un pedido de 2 camisetas y se provoca el fallo tras guardar pedido, líneas, stock y movimientos...");
        try {
            servicios.pedidos().crearPedido(solicitud(laura, new ItemPedido(maradona.id(), 2), new ItemPedido(ronaldinho.id(), 3)), true);
            throw new IllegalStateException("El fallo controlado debía impedir el commit");
        } catch (FalloControladoException e) {
            out.println("Excepción capturada: " + e.getMessage());
            out.println("Rollback realizado por el servicio.");
        }

        InstantaneaBD despues = servicios.verificacion().instantanea();
        int stockMaradonaDespues = stock(maradona);
        int stockRonaldinhoDespues = stock(ronaldinho);
        out.println("\nDESPUÉS : " + describir(despues));
        out.println("          stock Maradona=" + stockMaradonaDespues + ", Ronaldinho=" + stockRonaldinhoDespues);

        boolean igual = antes.equals(despues) && stockMaradonaAntes == stockMaradonaDespues && stockRonaldinhoAntes == stockRonaldinhoDespues;
        out.println("\nVERIFICACIÓN (consultas SQL):");
        out.println("  Pedidos, líneas, movimientos y pagos sin cambios ........ " + (antes.pedidos() == despues.pedidos()
                && antes.lineas() == despues.lineas() && antes.movimientos() == despues.movimientos() && antes.pagos() == despues.pagos()));
        out.println("  Pedidos incompletos (sin líneas) ........................ " + despues.pedidosSinLineas());
        out.println("  Existencias conservan sus valores anteriores ............ " + (antes.stockTotal() == despues.stockTotal()));
        out.println("  Stock de cada camiseta coherente con sus movimientos .... " + servicios.inventario().incoherencias().isEmpty());
        out.println("  Estado idéntico al anterior ............................. " + igual);
        if (!igual) {
            throw new IllegalStateException("El rollback no restauró el estado anterior");
        }
        log.info("Verificación del rollback correcta: la base de datos conserva el estado anterior");
    }

    // ================================================================= utilidades de presentación

    private void rechazar(String motivo, SolicitudPedido solicitud) {
        try {
            servicios.pedidos().crearPedido(solicitud, false);
            throw new IllegalStateException("El pedido debía rechazarse: " + motivo);
        } catch (NegocioException e) {
            out.printf("  %-34s -> %s [%s]%n", motivo, e.getMessage(), e.getCausa());
        }
    }

    private SolicitudPedido solicitud(Cliente cliente, ItemPedido... items) {
        Direccion principal = servicios.clientes().obtenerCompleto(cliente.id()).direcciones().get(0);
        return new SolicitudPedido(cliente.id(), principal.id(), List.of(items));
    }

    private int stock(Camiseta c) {
        return servicios.inventario().consultarStock(c.id()).stock();
    }

    private long categoriaId(String nombre) {
        return servicios.catalogo().listarCategorias().stream().filter(c -> c.nombre().equals(nombre)).findFirst().orElseThrow().id();
    }

    private void imprimir(CamisetaDetalleDTO c) {
        presentacion.camiseta(c);
    }

    private void imprimir(MovimientoStock m) {
        presentacion.movimiento(m);
    }

    private void imprimir(PedidoDetalleDTO p) {
        presentacion.pedido(p);
    }

    private String describir(InstantaneaBD i) {
        return "pedidos=" + i.pedidos() + " líneas=" + i.lineas() + " movimientos=" + i.movimientos() + " pagos=" + i.pagos()
                + " pedidosSinLíneas=" + i.pedidosSinLineas() + " stockTotal=" + i.stockTotal();
    }

    private void titulo(String texto) {
        presentacion.titulo(texto);
    }

    private static String eur(BigDecimal v) {
        return Presentacion.eur(v);
    }
}
