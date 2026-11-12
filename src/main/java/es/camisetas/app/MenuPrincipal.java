package es.camisetas.app;


import es.camisetas.config.Configuracion;
import es.camisetas.config.ModoSimulacion;
import es.camisetas.init.DatabaseInitializer;
import es.camisetas.services.Services;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.PrintStream;


/**
 * Menú de arranque: tienda en modo normal, simulación automática o demostración de rollback.
 * La base de datos ya está inicializada cuando se muestra; las simulaciones la reconstruyen para partir
 * siempre de un estado conocido.
 */
public class MenuPrincipal {

    private static final Logger log = LogManager.getLogger(MenuPrincipal.class);

    private final Services servicios;
    private final DatabaseInitializer inicializador;
    private final Configuracion config;
    private final Consola consola;
    private final PrintStream out;
    private final Presentacion presentacion;

    public MenuPrincipal(Services servicios, DatabaseInitializer inicializador, Configuracion config,
                         InputStream in, PrintStream out) {
        this.servicios = servicios;
        this.inicializador = inicializador;
        this.config = config;
        this.consola = new Consola(in, out);
        this.out = out;
        this.presentacion = new Presentacion(out);
    }

    public void ejecutar() {
        try {
            bucle();
        } catch (Consola.FinDeEntrada e) {
            log.info("Entrada cerrada: fin del menú");
            out.println("\nEntrada finalizada. Hasta pronto.");
        }
    }

    private void bucle() {
        while (true) {
            presentacion.titulo("TIENDA DE CAMISETAS DE FÚTBOL · gestor=" + config.gestor() + " · conexiones=" + config.modoConexion());
            out.println("  1. Entrar en la tienda (modo normal)");
            out.println("  2. Simulación automática (recorrido completo)");
            out.println("  3. Simulación de fallo de transacción (demostración de rollback)");
            out.println("  4. Reiniciar la base de datos con los datos de partida");
            out.println("  0. Salir");
            switch (consola.opcion("Elige una opción", 0, 4)) {
                case 1 -> new MenuTienda(servicios, consola, out).ejecutar();
                case 2 -> simular(ModoSimulacion.NORMAL);
                case 3 -> simular(ModoSimulacion.FALLO_TRANSACCION);
                case 4 -> reiniciar();
                default -> {
                    log.info("Salida de la aplicación desde el menú");
                    out.println("\nHasta pronto.");
                    return;
                }
            }
        }
    }

    private void simular(ModoSimulacion modo) {
        out.println("\nLas simulaciones parten de un estado conocido: se reconstruirá la base de datos y se perderán");
        out.println("los cambios hechos en la tienda.");
        if (!consola.confirmar("¿Continuar?")) {
            return;
        }
        try {
            inicializador.inicializar();
            new Simulacion(servicios, config, out).ejecutar(modo);
        } catch (RuntimeException e) {
            log.error("La simulación {} terminó con error: {}", modo, e.getMessage(), e);
            out.println("\n✗ La simulación terminó con error: " + e.getMessage());
        }
        consola.leer("\nPulsa Enter para volver al menú");
    }

    private void reiniciar() {
        if (consola.confirmar("Se borrarán los cambios hechos en la tienda. ¿Reiniciar la base de datos?")) {
            try {
                inicializador.inicializar();
                out.println("\n✓ Base de datos reiniciada con los datos de partida.");
            } catch (RuntimeException e) {
                log.error("No se pudo reiniciar la base de datos: {}", e.getMessage(), e);
                out.println("\n✗ No se pudo reiniciar: " + e.getMessage());
            }
        }
    }
}