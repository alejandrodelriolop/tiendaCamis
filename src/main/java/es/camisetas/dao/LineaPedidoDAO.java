package es.camisetas.dao;

import es.camisetas.dto.LineaDetalleDTO;
import es.camisetas.model.LineaPedido;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LineaPedidoDAO {

    private static final Logger log = LogManager.getLogger(LineaPedidoDAO.class);

    public long insertar(Connection con, LineaPedido l) {
        String sql = "INSERT INTO linea_pedido (pedido_id, camiseta_id, cantidad, precio_unitario, importe) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, sql)) {
            ps.setLong(1, l.pedidoId());
            ps.setLong(2, l.camisetaId());
            ps.setInt(3, l.cantidad());
            ps.setBigDecimal(4, l.precioUnitario());
            ps.setBigDecimal(5, l.importe());
            ps.executeUpdate();
            long id = JdbcUtil.claveGenerada(ps);
            log.debug("Línea insertada id={} pedido={} camiseta={} cantidad={}", id, l.pedidoId(), l.camisetaId(), l.cantidad());
            return id;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar una línea del pedido " + l.pedidoId(), e);
        }
    }

    /** Líneas de un pedido con los datos de la camiseta (JOIN). */
    public List<LineaDetalleDTO> listarDetalle(Connection con, long pedidoId) {
        String sql = """
                SELECT c.sku, c.nombre, lp.cantidad, lp.precio_unitario, lp.importe
                FROM linea_pedido lp
                JOIN camiseta c ON c.id = lp.camiseta_id
                WHERE lp.pedido_id = ?
                ORDER BY lp.id
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, pedidoId);
            try (ResultSet rs = ps.executeQuery()) {
                List<LineaDetalleDTO> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new LineaDetalleDTO(rs.getString("sku"), rs.getString("nombre"), rs.getInt("cantidad"),
                            rs.getBigDecimal("precio_unitario"), rs.getBigDecimal("importe")));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("listar las líneas del pedido " + pedidoId, e);
        }
    }

    public long contar(Connection con) {
        return JdbcUtil.contar(con, "linea_pedido");
    }
}
