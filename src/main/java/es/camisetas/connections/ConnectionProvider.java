package es.camisetas.connections;

import java.sql.Connection;
import es.camisetas.excepciones.ConexionException;
/**
 * Único punto por el que la aplicación obtiene conexiones. Los servicios y los DAO no saben
 * si detrás hay DriverManager o un pool: siempre reciben una Connection que cierran ellos
 * (con un pool, close() solo la devuelve).
 */
public interface ConnectionProvider extends AutoCloseable {

    /** @throws ConexionException si no se puede obtener la conexión */
    Connection getConnection();

    /** Descripción breve para los logs (sin credenciales). */
    String descripcion();

    /** Libera los recursos compartidos (en modo pool, cierra el DataSource). */
    @Override
    void close();
}
