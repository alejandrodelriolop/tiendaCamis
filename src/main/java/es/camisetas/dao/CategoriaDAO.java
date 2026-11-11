package es.camisetas.dao;


import es.camisetas.model.Categoria;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Categorías y su relación N:M con las camisetas (tabla intermedia camiseta_categoria). */
public class CategoriaDAO {

    private static final Logger log = LogManager.getLogger(CategoriaDAO.class);

    public List<Long> insertarLote(Connection con, List<Categoria> categorias) {
        String sql = "INSERT INTO categoria (nombre, descripcion) VALUES (?, ?)";
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, sql)) {
            for (Categoria c : categorias) {
                ps.setString(1, c.nombre());
                ps.setString(2, c.descripcion());
                ps.addBatch();
            }
            ps.executeBatch();
            List<Long> ids = JdbcUtil.clavesDeLote(ps, categorias.size());
            log.debug("Categorías insertadas en lote: {}", ids.size());
            return ids;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el lote de categorías", e);
        }
    }

    public Optional<Categoria> buscarPorNombre(Connection con, String nombre) {
        String sql = "SELECT id, nombre, descripcion FROM categoria WHERE nombre = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar la categoría " + nombre, e);
        }
    }

    public List<Categoria> listar(Connection con) {
        String sql = "SELECT id, nombre, descripcion FROM categoria ORDER BY nombre";
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Categoria> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw JdbcUtil.error("listar las categorías", e);
        }
    }

    /** Categorías de una camiseta: JOIN a través de la tabla intermedia. */
    public List<Categoria> listarPorCamiseta(Connection con, long camisetaId) {
        String sql = """
                SELECT c.id, c.nombre, c.descripcion
                FROM categoria c
                JOIN camiseta_categoria cc ON cc.categoria_id = c.id
                WHERE cc.camiseta_id = ?
                ORDER BY c.nombre
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, camisetaId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Categoria> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("listar las categorías de la camiseta " + camisetaId, e);
        }
    }

    public void asignar(Connection con, long camisetaId, long categoriaId) {
        String sql = "INSERT INTO camiseta_categoria (camiseta_id, categoria_id) VALUES (?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, camisetaId);
            ps.setLong(2, categoriaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw JdbcUtil.error("asignar la categoría " + categoriaId + " a la camiseta " + camisetaId, e);
        }
    }

    /** Cada elemento es el par {camisetaId, categoriaId}. */
    public void asignarLote(Connection con, List<long[]> pares) {
        String sql = "INSERT INTO camiseta_categoria (camiseta_id, categoria_id) VALUES (?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (long[] par : pares) {
                ps.setLong(1, par[0]);
                ps.setLong(2, par[1]);
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("Clasificaciones camiseta-categoría insertadas en lote: {}", pares.size());
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el lote de clasificaciones", e);
        }
    }

    /** @return filas eliminadas (0 si la camiseta no tenía esa categoría) */
    public int desasignar(Connection con, long camisetaId, long categoriaId) {
        String sql = "DELETE FROM camiseta_categoria WHERE camiseta_id = ? AND categoria_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, camisetaId);
            ps.setLong(2, categoriaId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw JdbcUtil.error("quitar la categoría " + categoriaId + " de la camiseta " + camisetaId, e);
        }
    }

    private Categoria mapear(ResultSet rs) throws SQLException {
        return new Categoria(rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"));
    }
}
