package es.camisetas.dao;

import es.camisetas.model.Jugador;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JugadorDAO {

    private static final Logger log = LogManager.getLogger(JugadorDAO.class);

    public List<Long> insertarLote(Connection con, List<Jugador> jugadores) {
        String sql = "INSERT INTO jugador (nombre, pais, posicion) VALUES (?, ?, ?)";
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, sql)) {
            for (Jugador j : jugadores) {
                ps.setString(1, j.nombre());
                ps.setString(2, j.pais());
                ps.setString(3, j.posicion());
                ps.addBatch();
            }
            ps.executeBatch();
            List<Long> ids = JdbcUtil.clavesDeLote(ps, jugadores.size());
            log.debug("Jugadores insertados en lote: {}", ids.size());
            return ids;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el lote de jugadores", e);
        }
    }

    public Optional<Jugador> buscarPorId(Connection con, long id) {
        String sql = "SELECT id, nombre, pais, posicion FROM jugador WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Jugador(rs.getLong("id"), rs.getString("nombre"),
                            rs.getString("pais"), rs.getString("posicion")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar el jugador " + id, e);
        }
    }
}
