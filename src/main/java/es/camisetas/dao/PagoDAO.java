package es.camisetas.dao;



import es.camisetas.model.Pago;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class PagoDAO {

    private static final Logger log = LogManager.getLogger(PagoDAO.class);

    public long insertar(Connection con, Pago p) {
        String sql = "INSERT INTO pago (pedido_id, metodo, estado, importe, fecha) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, sql)) {
            ps.setLong(1, p.pedidoId());
            ps.setString(2, p.metodo().name());
            ps.setString(3, p.estado().name());
            ps.setBigDecimal(4, p.importe());
            ps.setTimestamp(5, JdbcUtil.aTimestamp(p.fecha()));
            ps.executeUpdate();
            long id = JdbcUtil.claveGenerada(ps);
            log.debug("Pago insertado id={} pedido={} estado={}", id, p.pedidoId(), p.estado());
            return id;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el pago del pedido " + p.pedidoId(), e);
        }
    }

    public Optional<Pago> buscarPorPedido(Connection con, long pedidoId) {
        String sql = "SELECT id, pedido_id, metodo, estado, importe, fecha FROM pago WHERE pedido_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, pedidoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Pago(rs.getLong("id"), rs.getLong("pedido_id"),
                            Pago.Metodo.valueOf(rs.getString("metodo")), Pago.Estado.valueOf(rs.getString("estado")),
                            rs.getBigDecimal("importe"), JdbcUtil.aFechaHora(rs, "fecha")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar el pago del pedido " + pedidoId, e);
        }
    }

    /** Cambia el estado del pago de un pedido solo si estaba en {@code desde}. @return filas modificadas */
    public int cambiarEstado(Connection con, long pedidoId, Pago.Estado desde, Pago.Estado hacia) {
        String sql = "UPDATE pago SET estado = ? WHERE pedido_id = ? AND estado = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, hacia.name());
            ps.setLong(2, pedidoId);
            ps.setString(3, desde.name());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw JdbcUtil.error("cambiar el estado del pago del pedido " + pedidoId, e);
        }
    }

    public long contar(Connection con) {
        return JdbcUtil.contar(con, "pago");
    }
}
