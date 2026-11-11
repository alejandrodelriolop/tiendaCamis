package es.camisetas.dao;


import es.camisetas.model.Direccion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DireccionDAO {

    private static final Logger log = LogManager.getLogger(DireccionDAO.class);

    private static final String INSERT =
            "INSERT INTO direccion (cliente_id, calle, ciudad, codigo_postal, pais, principal) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SELECT =
            "SELECT id, cliente_id, calle, ciudad, codigo_postal, pais, principal FROM direccion";

    public long insertar(Connection con, Direccion d) {
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, INSERT)) {
            rellenar(ps, d);
            ps.executeUpdate();
            long id = JdbcUtil.claveGenerada(ps);
            log.debug("Dirección insertada id={} cliente={}", id, d.clienteId());
            return id;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar una dirección del cliente " + d.clienteId(), e);
        }
    }

    public List<Long> insertarLote(Connection con, List<Direccion> direcciones) {
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, INSERT)) {
            for (Direccion d : direcciones) {
                rellenar(ps, d);
                ps.addBatch();
            }
            ps.executeBatch();
            List<Long> ids = JdbcUtil.clavesDeLote(ps, direcciones.size());
            log.debug("Direcciones insertadas en lote: {}", ids.size());
            return ids;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el lote de direcciones", e);
        }
    }

    public Optional<Direccion> buscarPorId(Connection con, long id) {
        try (PreparedStatement ps = con.prepareStatement(SELECT + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar la dirección " + id, e);
        }
    }

    public List<Direccion> listarPorCliente(Connection con, long clienteId) {
        try (PreparedStatement ps = con.prepareStatement(SELECT + " WHERE cliente_id = ? ORDER BY principal DESC, id")) {
            ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Direccion> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("listar las direcciones del cliente " + clienteId, e);
        }
    }

    private void rellenar(PreparedStatement ps, Direccion d) throws SQLException {
        ps.setLong(1, d.clienteId());
        ps.setString(2, d.calle());
        ps.setString(3, d.ciudad());
        ps.setString(4, d.codigoPostal());
        ps.setString(5, d.pais());
        ps.setBoolean(6, d.principal());
    }

    private Direccion mapear(ResultSet rs) throws SQLException {
        return new Direccion(rs.getLong("id"), rs.getLong("cliente_id"), rs.getString("calle"), rs.getString("ciudad"),
                rs.getString("codigo_postal"), rs.getString("pais"), rs.getBoolean("principal"));
    }
}
