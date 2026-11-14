package es.camisetas;

import es.camisetas.app.MenuPrincipal;
import es.camisetas.config.Configuracion;
import es.camisetas.config.GestorDB;
import es.camisetas.config.ModoConexion;
import es.camisetas.dto.InstantaneaBD;
import es.camisetas.init.DatabaseInitializer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El menú se prueba con una sesión escrita de antemano y datos reales en H2: lo que importa es lo que queda en la BD. */
abstract class MenuTest extends BaseDatosTest {

    private static final String LAURA = "laura.martin@example.com";

    private String ejecutarMenu(String entrada) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        Configuracion config = Configuracion.paraPruebas(
                gestorBD(), "menu", ModoConexion.DRIVER_MANAGER);

        new MenuPrincipal(servicios,
                new DatabaseInitializer(provider, gestorBD()),
                config,
                new ByteArrayInputStream(entrada.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(salida, true, StandardCharsets.UTF_8)).ejecutar();

        return salida.toString(StandardCharsets.UTF_8);
    }

    /** Tienda en modo normal: hacer un pedido y pagarlo desde el menú modifica la base de datos. */
    @Test
    void hacerYPagarUnPedidoDesdeElMenuDeLaTienda() {
        InstantaneaBD antes = servicios.verificacion().instantanea();
        int stockPele = stock("CAM-BRA-70-PEL");

        String sesion = String.join("\n",
                "1",                    // menú principal: entrar en la tienda
                "5",                    // hacer un pedido
                LAURA,                  // cliente
                "1",                    // primera dirección
                "cam-bra-70-pel", "2",  // SKU (en minúsculas) y cantidad
                "",                     // fin de las líneas
                "s",                    // ¿pagar ahora?
                "1",                    // método: TARJETA
                "0",                    // volver al menú principal
                "0",                    // salir
                "");

        String salida = ejecutarMenu(sesion);

        InstantaneaBD despues = servicios.verificacion().instantanea();
        assertEquals(antes.pedidos() + 1, despues.pedidos());
        assertEquals(antes.lineas() + 1, despues.lineas());
        assertEquals(antes.pagos() + 1, despues.pagos());
        assertEquals(stockPele - 2, stock("CAM-BRA-70-PEL"));
        assertTrue(salida.contains("registrado"), salida);
        assertTrue(salida.contains("PAGADO"), salida);
    }

    /** Un error de negocio dentro del menú se explica y no tumba la aplicación ni cambia la base de datos. */
    @Test
    void unPedidoRechazadoSeExplicaYElMenuSigueFuncionando() {
        InstantaneaBD antes = servicios.verificacion().instantanea();

        String sesion = String.join("\n",
                "x", "99",              // opciones no válidas: se vuelve a preguntar
                "1",                    // entrar en la tienda
                "5", LAURA, "1",        // hacer un pedido
                "CAM-BRA-70-PEL", "999", // más unidades de las que hay
                "",                     // fin de las líneas
                "8", "12345",           // cancelar un pedido inexistente (error de negocio)
                "0", "0", "");

        String salida = ejecutarMenu(sesion);

        assertEquals(antes, servicios.verificacion().instantanea());
        assertTrue(salida.contains("Opción no válida"), salida);
        assertTrue(salida.contains("Stock insuficiente"), salida);
        assertTrue(salida.contains("Pedido no encontrado"), salida);
        assertTrue(salida.contains("Hasta pronto"), salida);
    }

    /** La simulación de rollback lanzada desde el menú reconstruye la BD y termina sin cambios parciales. */
    @Test
    void laSimulacionDeFalloSePuedeLanzarDesdeElMenu() {
        // Antes de simular se altera la base de datos desde la tienda; la simulación debe reconstruirla
        servicios.inventario().reponer(camiseta("CAM-BRA-70-PEL").id(), 50, "ajuste previo");
        assertEquals(56, stock("CAM-BRA-70-PEL"));

        String salida = ejecutarMenu(String.join("\n", "3", "s", "", "0", ""));

        assertTrue(salida.contains("DEMOSTRACIÓN DE ROLLBACK"), salida);
        assertTrue(salida.matches("(?s).*Estado idéntico al anterior \\.+ true.*"), salida);
        assertEquals(6, stock("CAM-BRA-70-PEL"));                        // datos de partida restaurados
        assertEquals(0, servicios.verificacion().instantanea().pedidos());
    }

    /** Si se cierra la entrada (Ctrl+D) el menú termina con elegancia en lugar de lanzar una excepción. */
    @Test
    void conLaEntradaCerradaElMenuTermina() {
        String salida = ejecutarMenu("");
        assertTrue(salida.contains("Entrada finalizada"), salida);
    }
}
