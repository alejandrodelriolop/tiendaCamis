package es.camisetas.dao;

import es.camisetas.exceptions.PersistenciaException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Utilidades JDBC compartidas por los DAO. */
final class JdbcUtil {

    private JdbcUtil() {
    }

    /** Clave generada por el SGBD tras un INSERT simple. */
    static long claveGenerada(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            throw new SQLException("El SGBD no devolvió la clave generada");
        }
    }

    /** Claves generadas, en orden, tras un executeBatch de INSERT. */
    static List<Long> clavesGeneradas(PreparedStatement ps) throws SQLException {
        List<Long> claves = new ArrayList<>();
        try (ResultSet rs = ps.getGeneratedKeys()) {
            while (rs.next()) {
                claves.add(rs.getLong(1));
            }
        }
        return claves;
    }

    /** Comprueba que el lote devolvió una clave por cada fila insertada. */
    static List<Long> clavesDeLote(PreparedStatement ps, int esperadas) throws SQLException {
        List<Long> claves = clavesGeneradas(ps);
        if (claves.size() != esperadas) {
            throw new SQLException("Se esperaban " + esperadas + " claves generadas y llegaron " + claves.size());
        }
        return claves;
    }

    static PreparedStatement prepararConClaves(Connection con, String sql) throws SQLException {
        return con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }

    static void setLongNullable(PreparedStatement ps, int indice, Long valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.BIGINT);
        } else {
            ps.setLong(indice, valor);
        }
    }

    static void setIntNullable(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.INTEGER);
        } else {
            ps.setInt(indice, valor);
        }
    }

    static Long getLongNullable(ResultSet rs, String columna) throws SQLException {
        long valor = rs.getLong(columna);
        return rs.wasNull() ? null : valor;
    }

    static Integer getIntNullable(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    static Timestamp aTimestamp(LocalDateTime fecha) {
        return Timestamp.valueOf(fecha);
    }

    static LocalDateTime aFechaHora(ResultSet rs, String columna) throws SQLException {
        return rs.getTimestamp(columna).toLocalDateTime();
    }

    /** Cuenta las filas de una tabla (el nombre es siempre una constante del código, nunca un dato externo). */
    static long contar(Connection con, String tabla) {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM " + tabla);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw error("contar filas de " + tabla, e);
        }
    }

    static PersistenciaException error(String contexto, SQLException causa) {
        return new PersistenciaException("Error de persistencia al " + contexto, causa);
    }
}
