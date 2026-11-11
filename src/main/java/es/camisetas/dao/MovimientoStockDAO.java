package es.camisetas.dao;


import es.camisetas.model.MovimientoStock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MovimientoStockDAO {

    private static final Logger log = LogManager.getLogger(MovimientoStockDAO.class);

    private static final String INSERT =
            "INSERT INTO movimiento_stock (camiseta_id, pedido_id, tipo, cantidad, fecha, motivo) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SELECT =
            "SELECT id, camiseta_id, pedido_id, tipo, cantidad, fecha, motivo FROM movimiento_stock";

    public long insertar(Connection con, MovimientoStock m) {
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, INSERT)) {
            rellenar(ps, m);
            ps.executeUpdate();
            long id = JdbcUtil.claveGenerada(ps);
            log.debug("Movimiento insertado id={} tipo={} camiseta={} cantidad={}", id, m.tipo(), m.camisetaId(), m.cantidad());
            return id;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar un movimiento de stock", e);
        }
    }

    public void insertarLote(Connection con, List<MovimientoStock> movimientos) {
        try (PreparedStatement ps = con.prepareStatement(INSERT)) {
            for (MovimientoStock m : movimientos) {
                rellenar(ps, m);
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("Movimientos insertados en lote: {}", movimientos.size());
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el lote de movimientos", e);
        }
    }

    public List<MovimientoStock> listarPorCamiseta(Connection con, long camisetaId) {
        return listar(con, SELECT + " WHERE camiseta_id = ? ORDER BY id", camisetaId,
                "listar los movimientos de la camiseta " + camisetaId);
    }

    /** Salidas de stock que provocó un pedido: lo que hay que devolver si se cancela. */
    public List<MovimientoStock> listarSalidasDePedido(Connection con, long pedidoId) {
        return listar(con, SELECT + " WHERE pedido_id = ? AND tipo = 'SALIDA_PEDIDO' ORDER BY id", pedidoId,
                "listar las salidas del pedido " + pedidoId);
    }

    public long contar(Connection con) {
        return JdbcUtil.contar(con, "movimiento_stock");
    }

    private List<MovimientoStock> listar(Connection con, String sql, long parametro, String contexto) {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                List<MovimientoStock> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new MovimientoStock(
                            rs.getLong("id"),
                            rs.getLong("camiseta_id"),
                            MovimientoStock.Tipo.valueOf(rs.getString("tipo")),
                            rs.getInt("cantidad"),
                            JdbcUtil.aFechaHora(rs, "fecha"),
                            JdbcUtil.getLongNullable(rs, "pedido_id"),
                            rs.getString("motivo")));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw JdbcUtil.error(contexto, e);
        }
    }

    private void rellenar(PreparedStatement ps, MovimientoStock m) throws SQLException {
        ps.setLong(1, m.camisetaId());
        JdbcUtil.setLongNullable(ps, 2, m.pedidoId());
        ps.setString(3, m.tipo().name());
        ps.setInt(4, m.cantidad());
        ps.setTimestamp(5, JdbcUtil.aTimestamp(m.fecha()));
        ps.setString(6, m.motivo());
    }
}
