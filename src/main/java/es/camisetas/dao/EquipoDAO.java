package es.camisetas.dao;


import es.camisetas.model.Equipo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class EquipoDAO {


    private static final Logger log = LogManager.getLogger(EquipoDAO.class);

    /** Inserta todos los equipos en un batch y devuelve sus ids generados, en el mismo orden. */
    public List<Long> insertarLote(Connection con, List<Equipo> equipos) {
        String sql = "INSERT INTO equipo (nombre, pais, liga) VALUES (?, ?, ?)";
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, sql)) {
            for (Equipo e : equipos) {
                ps.setString(1, e.nombre());
                ps.setString(2, e.pais());
                ps.setString(3, e.liga());
                ps.addBatch();
            }
            ps.executeBatch();
            List<Long> ids = JdbcUtil.clavesDeLote(ps, equipos.size());
            log.debug("Equipos insertados en lote: {}", ids.size());
            return ids;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el lote de equipos", e);
        }
    }

    public Optional<Equipo> buscarPorId(Connection con, long id) {
        String sql = "SELECT id, nombre, pais, liga FROM equipo WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Equipo(rs.getLong("id"), rs.getString("nombre"),
                            rs.getString("pais"), rs.getString("liga")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar el equipo " + id, e);
        }
    }

    public Optional<Equipo> buscarPorNombre(Connection con, String nombre) {
        String sql = "SELECT id, nombre, pais, liga FROM equipo WHERE nombre = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Equipo(rs.getLong("id"), rs.getString("nombre"),
                            rs.getString("pais"), rs.getString("liga")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar el equipo " + nombre, e);
        }
    }

    public long contar(Connection con) {
        return JdbcUtil.contar(con, "equipo");
    }
}
