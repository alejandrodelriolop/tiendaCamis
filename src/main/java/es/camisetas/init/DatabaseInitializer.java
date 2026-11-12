package es.camisetas.init;

import es.camisetas.config.GestorDB;
import es.camisetas.connections.ConnectionProvider;
import es.camisetas.connections.SqlScriptRunner;
import es.camisetas.connections.Transacciones;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Reconstruye el esquema de la práctica (DROP + CREATE solo de nuestras tablas) y carga los datos de partida.
 * Los scripts DDL son específicos de cada gestor; el resto de la aplicación no lo sabe.
 */
public class DatabaseInitializer {

    private static final Logger log = LogManager.getLogger(DatabaseInitializer.class);

    private final ConnectionProvider provider;
    private final GestorDB gestor;
    private final Transacciones tx;

    public DatabaseInitializer(ConnectionProvider provider, GestorDB gestor) {
        this.provider = provider;
        this.gestor = gestor;
        this.tx = new Transacciones(provider);
    }

    public DatosIniciales.ResumenCarga inicializar() {
        log.info("Inicialización de la base de datos para {}", gestor);
        crearEsquema();
        DatosIniciales.ResumenCarga resumen = tx.ejecutar("carga inicial de datos", DatosIniciales::cargar);
        log.info("Carga inicial completada: equipos={} eras={} jugadores={} categorias={} camisetas={} clasificaciones={} clientes={} direcciones={} movimientos={}",
                resumen.equipos(), resumen.eras(), resumen.jugadores(), resumen.categorias(), resumen.camisetas(),
                resumen.clasificaciones(), resumen.clientes(), resumen.direcciones(), resumen.movimientos());
        return resumen;
    }

    private void crearEsquema() {
        String carpeta = gestor == GestorDB.MYSQL ? "/sql/mysql/" : "/sql/h2/";
        try (Connection con = provider.getConnection()) {
            int sentencias = SqlScriptRunner.ejecutarScript(con, carpeta + "01-schema.sql");
            log.info("Esquema reconstruido: {} sentencias DDL ({})", sentencias, carpeta + "01-schema.sql");
            if (gestor == GestorDB.MYSQL) {
                SqlScriptRunner.ejecutarSql(con, "DROP PROCEDURE IF EXISTS sp_resumen_equipo");
                SqlScriptRunner.ejecutarSentenciaUnica(con, carpeta + "sp_resumen_equipo.sql");
                log.info("Procedimiento almacenado sp_resumen_equipo creado");
            } else {
                log.info("H2: no se crea sp_resumen_equipo (procedimiento específico de MySQL)");
            }
        } catch (SQLException e) {
            throw new es.camisetas.exceptions.PersistenciaException("Error cerrando la conexión de inicialización", e);
        }
    }
}

