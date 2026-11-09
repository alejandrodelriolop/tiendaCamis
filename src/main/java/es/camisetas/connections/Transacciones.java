package es.camisetas.connections;



import es.camisetas.exceptions.FalloControladoException;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.exceptions.PersistenciaException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Delimita las transacciones JDBC. Cada operación obtiene UNA conexión, la comparte con todos los
 * DAO que participan, confirma si todo va bien y hace rollback si algo falla.
 * Los servicios deciden qué va dentro de la transacción; esta clase solo se ocupa del ciclo
 * setAutoCommit(false) / commit / rollback / cierre.
 */
public class Transacciones {

    private static final Logger log = LogManager.getLogger(Transacciones.class);

    private final ConnectionProvider provider;

    public Transacciones(ConnectionProvider provider) {
        this.provider = provider;
    }

    /** Consulta de solo lectura: una conexión en autocommit, cerrada al terminar. */
    public <T> T consultar(Function<Connection, T> trabajo) {
        try (Connection con = provider.getConnection()) {
            return trabajo.apply(con);
        } catch (SQLException e) {
            throw new PersistenciaException("Error al cerrar la conexión tras una consulta", e);
        }
    }

    /** Ejecuta el trabajo en una única transacción: commit si termina, rollback si lanza cualquier excepción. */
    public <T> T ejecutar(String operacion, Function<Connection, T> trabajo) {
        try (Connection con = provider.getConnection()) {
            con.setAutoCommit(false);
            log.info("Inicio de transacción: {}", operacion);
            try {
                T resultado = trabajo.apply(con);
                con.commit();
                log.info("Commit completado: {}", operacion);
                return resultado;
            } catch (RuntimeException e) {
                revertir(con, operacion, e);
                throw e;
            } catch (SQLException e) {
                revertir(con, operacion, e);
                throw new PersistenciaException("Error al confirmar la transacción '" + operacion + "'", e);
            } finally {
                restaurarAutoCommit(con);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Error al preparar o cerrar la transacción '" + operacion + "'", e);
        }
    }

    private void revertir(Connection con, String operacion, Exception causa) {
        if (causa instanceof NegocioException ne) {
            log.warn("Regla de negocio incumplida en '{}' ({}): {}", operacion, ne.getCausa(), ne.getMessage());
        } else if (causa instanceof FalloControladoException) {
            log.warn("Fallo controlado en '{}': {}", operacion, causa.getMessage());
        } else {
            log.error("Error técnico en '{}': {}", operacion, causa.toString(), causa);
        }
        try {
            con.rollback();
            log.info("Rollback completado: {}", operacion);
        } catch (SQLException e) {
            log.error("No se pudo hacer rollback de '{}': {}", operacion, e.getMessage(), e);
            causa.addSuppressed(e);
        }
    }

    private void restaurarAutoCommit(Connection con) {
        try {
            con.setAutoCommit(true);
        } catch (SQLException e) {
            log.warn("No se pudo restaurar el autocommit de la conexión: {}", e.getMessage());
        }
    }
}
