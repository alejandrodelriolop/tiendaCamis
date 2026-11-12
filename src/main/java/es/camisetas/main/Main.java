package es.camisetas.main;
import es.camisetas.app.MenuPrincipal;
import es.camisetas.config.Configuracion;
import es.camisetas.connections.ConnectionProvider;
import es.camisetas.connections.ConnectionProviderFactory;
import es.camisetas.exceptions.ConexionException;
import es.camisetas.exceptions.ConfiguracionException;
import es.camisetas.init.DatabaseInitializer;
import es.camisetas.services.Services;
import es.camisetas.app.Simulacion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;


/** Punto de entrada: lee la configuración, prepara la base de datos y muestra el menú (o lanza la simulación). */
public class Main {

    private static final Logger log = LogManager.getLogger(Main.class);
    private static final Logger logConfig = LogManager.getLogger(Configuracion.class);

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int codigo = 0;
        try {
            Configuracion config = Configuracion.cargar();
            logConfig.info("Gestor={}, conexiones={}, simulacion={}, interactivo={}",
                    config.gestor(), config.modoConexion(), config.modoSimulacion(), config.interactivo());
            logConfig.debug("{}", config);

            try (ConnectionProvider provider = ConnectionProviderFactory.crear(config)) {
                log.info("Proveedor de conexiones: {}", provider.descripcion());
                DatabaseInitializer inicializador = new DatabaseInitializer(provider, config.gestor());
                inicializador.inicializar();
                Services servicios = Services.crear(provider, config.gestor());

                if (config.interactivo()) {
                    new MenuPrincipal(servicios, inicializador, config, System.in, out).ejecutar();
                } else {
                    new Simulacion(servicios, config, out).ejecutar();   // según simulation.mode
                }
            }
            log.info("Aplicación finalizada correctamente");
        } catch (ConfiguracionException e) {
            log.error("Configuración incorrecta: {}", e.getMessage());
            System.err.println("Configuración incorrecta: " + e.getMessage());
            codigo = 2;
        } catch (ConexionException e) {
            log.error("No se pudo conectar con la base de datos: {}", e.getMessage(), e);
            System.err.println("No se pudo conectar con la base de datos: " + e.getCause().getMessage());
            codigo = 3;
        } catch (RuntimeException e) {
            log.error("La aplicación terminó con error: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            codigo = 1;
        }
        LogManager.shutdown();
        if (codigo != 0) {
            System.exit(codigo);
        }
    }
}