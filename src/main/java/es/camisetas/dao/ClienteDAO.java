package es.camisetas.dao;

import es.camisetas.dto.ClienteResumenDTO;
import es.camisetas.model.Cliente;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClienteDAO {

    private static final Logger log = LogManager.getLogger(ClienteDAO.class);

    private static final String INSERT = "INSERT INTO cliente (nombre, email, fecha_alta) VALUES (?, ?, ?)";

    public long insertar(Connection con, Cliente c) {
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, INSERT)) {
            rellenar(ps, c);
            ps.executeUpdate();
            long id = JdbcUtil.claveGenerada(ps);
            log.debug("Cliente insertado id={}", id);
            return id;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el cliente " + c.email(), e);
        }
    }

    public List<Long> insertarLote(Connection con, List<Cliente> clientes) {
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, INSERT)) {
            for (Cliente c : clientes) {
                rellenar(ps, c);
                ps.addBatch();
            }
            ps.executeBatch();
            List<Long> ids = JdbcUtil.clavesDeLote(ps, clientes.size());
            log.debug("Clientes insertados en lote: {}", ids.size());
            return ids;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el lote de clientes", e);
        }
    }

    public Optional<Cliente> buscarPorId(Connection con, long id) {
        return buscarUno(con, "SELECT id, nombre, email, fecha_alta FROM cliente WHERE id = ?", id, "buscar el cliente " + id);
    }

    public Optional<Cliente> buscarPorEmail(Connection con, String email) {
        return buscarUno(con, "SELECT id, nombre, email, fecha_alta FROM cliente WHERE email = ?", email, "buscar el cliente " + email);
    }

    /** Clientes con su número de pedidos y total gastado, calculados con LEFT JOIN + GROUP BY (sin contar cancelados). */
    public List<ClienteResumenDTO> listarResumen(Connection con) {
        String sql = """
                SELECT c.id, c.nombre, c.email,
                       COUNT(p.id) AS num_pedidos,
                       COALESCE(SUM(p.total), 0) AS total_gastado
                FROM cliente c
                LEFT JOIN pedido p ON p.cliente_id = c.id AND p.estado <> 'CANCELADO'
                GROUP BY c.id, c.nombre, c.email
                ORDER BY total_gastado DESC, c.id
                """;
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<ClienteResumenDTO> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new ClienteResumenDTO(rs.getLong("id"), rs.getString("nombre"), rs.getString("email"),
                        rs.getInt("num_pedidos"), rs.getBigDecimal("total_gastado")));
            }
            return lista;
        } catch (SQLException e) {
            throw JdbcUtil.error("listar el resumen de clientes", e);
        }
    }

    public long contar(Connection con) {
        return JdbcUtil.contar(con, "cliente");
    }

    private Optional<Cliente> buscarUno(Connection con, String sql, Object parametro, String contexto) {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Cliente(rs.getLong("id"), rs.getString("nombre"), rs.getString("email"),
                            rs.getDate("fecha_alta").toLocalDate()));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error(contexto, e);
        }
    }

    private void rellenar(PreparedStatement ps, Cliente c) throws SQLException {
        ps.setString(1, c.nombre());
        ps.setString(2, c.email());
        ps.setDate(3, Date.valueOf(c.fechaAlta()));
    }
}
