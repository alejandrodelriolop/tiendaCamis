package es.camisetas.dao;


import es.camisetas.model.Era;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class EraDAO {

    private static final Logger log = LogManager.getLogger(EraDAO.class);

    public List<Long> insertarLote(Connection con, List<Era> eras) {
        String sql = "INSERT INTO era (nombre, anio_inicio, anio_fin) VALUES (?, ?, ?)";
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, sql)) {
            for (Era e : eras) {
                ps.setString(1, e.nombre());
                ps.setInt(2, e.anioInicio());
                JdbcUtil.setIntNullable(ps, 3, e.anioFin());
                ps.addBatch();
            }
            ps.executeBatch();
            List<Long> ids = JdbcUtil.clavesDeLote(ps, eras.size());
            log.debug("Eras insertadas en lote: {}", ids.size());
            return ids;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el lote de eras", e);
        }
    }

    public Optional<Era> buscarPorNombre(Connection con, String nombre) {
        String sql = "SELECT id, nombre, anio_inicio, anio_fin FROM era WHERE nombre = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Era(rs.getLong("id"), rs.getString("nombre"),
                            rs.getInt("anio_inicio"), JdbcUtil.getIntNullable(rs, "anio_fin")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar la era " + nombre, e);
        }
    }

    public Optional<Era> buscarPorId(Connection con, long id) {
        String sql = "SELECT id, nombre, anio_inicio, anio_fin FROM era WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Era(rs.getLong("id"), rs.getString("nombre"),
                            rs.getInt("anio_inicio"), JdbcUtil.getIntNullable(rs, "anio_fin")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar la era " + id, e);
        }
    }
}
