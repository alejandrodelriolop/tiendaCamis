package es.camisetas.dao;



import es.camisetas.dto.IncoherenciaStockDTO;
import es.camisetas.dto.StockBajoDTO;
import es.camisetas.model.Inventario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InventarioDAO {

    private static final Logger log = LogManager.getLogger(InventarioDAO.class);

    public void insertar(Connection con, Inventario inv) {
        insertarLote(con, List.of(inv));
    }

    public void insertarLote(Connection con, List<Inventario> inventarios) {
        String sql = "INSERT INTO inventario (camiseta_id, stock, umbral_reposicion) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (Inventario i : inventarios) {
                ps.setLong(1, i.camisetaId());
                ps.setInt(2, i.stock());
                ps.setInt(3, i.umbralReposicion());
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("Inventarios insertados: {}", inventarios.size());
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar inventarios", e);
        }
    }

    public Optional<Inventario> buscarPorCamiseta(Connection con, long camisetaId) {
        String sql = "SELECT camiseta_id, stock, umbral_reposicion FROM inventario WHERE camiseta_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, camisetaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Inventario(rs.getLong("camiseta_id"), rs.getInt("stock"),
                            rs.getInt("umbral_reposicion")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("consultar el stock de la camiseta " + camisetaId, e);
        }
    }

    /**
     * Resta unidades solo si hay suficientes (la condición viaja en el propio UPDATE).
     * @return 1 si se descontó, 0 si no había stock suficiente
     */
    public int descontarStock(Connection con, long camisetaId, int cantidad) {
        String sql = "UPDATE inventario SET stock = stock - ? WHERE camiseta_id = ? AND stock >= ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, cantidad);
            ps.setLong(2, camisetaId);
            ps.setInt(3, cantidad);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw JdbcUtil.error("descontar stock de la camiseta " + camisetaId, e);
        }
    }

    public int incrementarStock(Connection con, long camisetaId, int cantidad) {
        String sql = "UPDATE inventario SET stock = stock + ? WHERE camiseta_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, cantidad);
            ps.setLong(2, camisetaId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw JdbcUtil.error("incrementar stock de la camiseta " + camisetaId, e);
        }
    }

    /** Camisetas activas con control de stock cuyo stock ha llegado al umbral de reposición. */
    public List<StockBajoDTO> listarStockBajo(Connection con) {
        String sql = """
                SELECT c.id, c.sku, c.nombre, i.stock, i.umbral_reposicion,
                       (i.umbral_reposicion - i.stock) AS deficit
                FROM inventario i
                JOIN camiseta c ON c.id = i.camiseta_id
                WHERE c.control_stock = TRUE AND c.activa = TRUE AND i.stock <= i.umbral_reposicion
                ORDER BY deficit DESC, c.id
                """;
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<StockBajoDTO> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new StockBajoDTO(rs.getLong("id"), rs.getString("sku"), rs.getString("nombre"),
                        rs.getInt("stock"), rs.getInt("umbral_reposicion"), rs.getInt("deficit")));
            }
            return lista;
        } catch (SQLException e) {
            throw JdbcUtil.error("listar las camisetas con stock bajo", e);
        }
    }

    /** Camisetas con control de stock cuyo stock no coincide con la suma de sus movimientos. */
    public List<IncoherenciaStockDTO> listarIncoherencias(Connection con) {
        String sql = """
                SELECT c.sku, i.stock,
                       COALESCE(SUM(CASE WHEN m.tipo = 'SALIDA_PEDIDO' THEN -m.cantidad ELSE m.cantidad END), 0) AS neto
                FROM camiseta c
                JOIN inventario i ON i.camiseta_id = c.id
                LEFT JOIN movimiento_stock m ON m.camiseta_id = c.id
                WHERE c.control_stock = TRUE
                GROUP BY c.id, c.sku, i.stock
                HAVING i.stock <> COALESCE(SUM(CASE WHEN m.tipo = 'SALIDA_PEDIDO' THEN -m.cantidad ELSE m.cantidad END), 0)
                ORDER BY c.sku
                """;
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<IncoherenciaStockDTO> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new IncoherenciaStockDTO(rs.getString("sku"), rs.getInt("stock"), rs.getInt("neto")));
            }
            return lista;
        } catch (SQLException e) {
            throw JdbcUtil.error("comprobar la coherencia del stock", e);
        }
    }

    public long sumarStock(Connection con) {
        try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(stock), 0) FROM inventario");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw JdbcUtil.error("sumar el stock total", e);
        }
    }
}
