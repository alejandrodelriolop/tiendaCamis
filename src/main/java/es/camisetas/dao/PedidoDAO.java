package es.camisetas.dao;


import es.camisetas.dto.PedidoDetalleDTO;
import es.camisetas.dto.PedidoResumenDTO;
import es.camisetas.model.Pedido;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PedidoDAO {

    private static final Logger log = LogManager.getLogger(PedidoDAO.class);

    public long insertar(Connection con, Pedido p) {
        String sql = "INSERT INTO pedido (cliente_id, direccion_id, fecha, estado, total) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, sql)) {
            ps.setLong(1, p.clienteId());
            ps.setLong(2, p.direccionId());
            ps.setTimestamp(3, JdbcUtil.aTimestamp(p.fecha()));
            ps.setString(4, p.estado().name());
            ps.setBigDecimal(5, p.total());
            ps.executeUpdate();
            long id = JdbcUtil.claveGenerada(ps);
            log.debug("Pedido insertado id={}", id);
            return id;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar un pedido del cliente " + p.clienteId(), e);
        }
    }

    public Optional<Pedido> buscarPorId(Connection con, long id) {
        String sql = "SELECT id, cliente_id, direccion_id, fecha, estado, total FROM pedido WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Pedido(rs.getLong("id"), rs.getLong("cliente_id"), rs.getLong("direccion_id"),
                            JdbcUtil.aFechaHora(rs, "fecha"), Pedido.Estado.valueOf(rs.getString("estado")),
                            rs.getBigDecimal("total")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar el pedido " + id, e);
        }
    }

    public int actualizarTotal(Connection con, long id, BigDecimal total) {
        try (PreparedStatement ps = con.prepareStatement("UPDATE pedido SET total = ? WHERE id = ?")) {
            ps.setBigDecimal(1, total);
            ps.setLong(2, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw JdbcUtil.error("actualizar el total del pedido " + id, e);
        }
    }

    /** Cambia el estado solo si el pedido sigue en {@code desde}. @return 1 si cambió, 0 si ya no estaba en ese estado */
    public int cambiarEstado(Connection con, long id, Pedido.Estado desde, Pedido.Estado hacia) {
        String sql = "UPDATE pedido SET estado = ? WHERE id = ? AND estado = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, hacia.name());
            ps.setLong(2, id);
            ps.setString(3, desde.name());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw JdbcUtil.error("cambiar el estado del pedido " + id, e);
        }
    }

    /**
     * Cancela el pedido salvo que ya estuviera cancelado. La condición va en el UPDATE, de modo que
     * una segunda cancelación afecta a 0 filas y no puede devolver el stock dos veces.
     * @return 1 si se canceló, 0 si ya estaba cancelado
     */
    public int cancelar(Connection con, long id) {
        String sql = "UPDATE pedido SET estado = 'CANCELADO' WHERE id = ? AND estado <> 'CANCELADO'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw JdbcUtil.error("cancelar el pedido " + id, e);
        }
    }

    /** Cabecera del pedido con los datos del cliente y la dirección (sin líneas). */
    public Optional<PedidoDetalleDTO> buscarCabecera(Connection con, long id) {
        String sql = """
                SELECT p.id, c.nombre, c.email, d.calle, d.ciudad, p.fecha, p.estado, p.total
                FROM pedido p
                JOIN cliente c   ON c.id = p.cliente_id
                JOIN direccion d ON d.id = p.direccion_id
                WHERE p.id = ?
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new PedidoDetalleDTO(rs.getLong("id"), rs.getString("nombre"), rs.getString("email"),
                            rs.getString("calle") + ", " + rs.getString("ciudad"), JdbcUtil.aFechaHora(rs, "fecha"),
                            Pedido.Estado.valueOf(rs.getString("estado")), rs.getBigDecimal("total"), List.of()));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("consultar la cabecera del pedido " + id, e);
        }
    }

    /** Historial de un cliente: número de líneas y unidades calculados en SQL. */
    public List<PedidoResumenDTO> historialDeCliente(Connection con, long clienteId) {
        String sql = """
                SELECT p.id, p.fecha, p.estado, p.total,
                       COUNT(lp.id) AS lineas,
                       COALESCE(SUM(lp.cantidad), 0) AS unidades
                FROM pedido p
                LEFT JOIN linea_pedido lp ON lp.pedido_id = p.id
                WHERE p.cliente_id = ?
                GROUP BY p.id, p.fecha, p.estado, p.total
                ORDER BY p.fecha DESC, p.id DESC
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PedidoResumenDTO> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new PedidoResumenDTO(rs.getLong("id"), JdbcUtil.aFechaHora(rs, "fecha"),
                            Pedido.Estado.valueOf(rs.getString("estado")), rs.getBigDecimal("total"),
                            rs.getInt("lineas"), rs.getInt("unidades")));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("consultar el historial del cliente " + clienteId, e);
        }
    }

    public long contar(Connection con) {
        return JdbcUtil.contar(con, "pedido");
    }

    /** Pedidos que no tienen ninguna línea: un pedido incompleto, que nunca debería quedar persistido. */
    public long contarSinLineas(Connection con) {
        String sql = "SELECT COUNT(*) FROM pedido p WHERE NOT EXISTS (SELECT 1 FROM linea_pedido lp WHERE lp.pedido_id = p.id)";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw JdbcUtil.error("contar los pedidos sin líneas", e);
        }
    }
}
