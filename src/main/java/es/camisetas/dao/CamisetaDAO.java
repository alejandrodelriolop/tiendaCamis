package es.camisetas.dao;


import es.camisetas.dto.CamisetaDetalleDTO;
import es.camisetas.dto.CatalogoFiltro;
import es.camisetas.model.Camiseta;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CamisetaDAO {

    private static final Logger log = LogManager.getLogger(CamisetaDAO.class);

    private static final String INSERT = """
            INSERT INTO camiseta (sku, nombre, equipo_id, era_id, jugador_id, dorsal, temporada,
                                  equipacion, edicion, precio, activa, control_stock)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT = """
            SELECT id, sku, nombre, equipo_id, era_id, jugador_id, dorsal, temporada,
                   equipacion, edicion, precio, activa, control_stock
            FROM camiseta
            """;

    /** Consulta con JOIN: la camiseta con los nombres de equipo, era y jugador, y su stock. */
    private static final String SELECT_DETALLE = """
            SELECT c.id, c.sku, c.nombre, e.nombre AS equipo, r.nombre AS era, j.nombre AS jugador,
                   c.dorsal, c.temporada, c.equipacion, c.edicion, c.precio, c.activa, i.stock
            FROM camiseta c
            JOIN equipo e     ON e.id = c.equipo_id
            JOIN era r        ON r.id = c.era_id
            LEFT JOIN jugador j ON j.id = c.jugador_id
            JOIN inventario i ON i.camiseta_id = c.id
            """;

    private static final String FROM_CATALOGO = """
            FROM camiseta c
            JOIN equipo e     ON e.id = c.equipo_id
            JOIN era r        ON r.id = c.era_id
            LEFT JOIN jugador j ON j.id = c.jugador_id
            JOIN inventario i ON i.camiseta_id = c.id
            """;

    public long insertar(Connection con, Camiseta c) {
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, INSERT)) {
            rellenar(ps, c);
            ps.executeUpdate();
            long id = JdbcUtil.claveGenerada(ps);
            log.debug("Camiseta insertada id={} sku={}", id, c.sku());
            return id;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar la camiseta " + c.sku(), e);
        }
    }

    /** Inserta todas las camisetas en un batch y devuelve los ids generados, en el mismo orden. */
    public List<Long> insertarLote(Connection con, List<Camiseta> camisetas) {
        try (PreparedStatement ps = JdbcUtil.prepararConClaves(con, INSERT)) {
            for (Camiseta c : camisetas) {
                rellenar(ps, c);
                ps.addBatch();
            }
            ps.executeBatch();
            List<Long> ids = JdbcUtil.clavesDeLote(ps, camisetas.size());
            log.debug("Camisetas insertadas en lote: {}", ids.size());
            return ids;
        } catch (SQLException e) {
            throw JdbcUtil.error("insertar el lote de camisetas", e);
        }
    }

    public Optional<Camiseta> buscarPorId(Connection con, long id) {
        return buscarUna(con, SELECT + " WHERE id = ?", id, "buscar la camiseta " + id);
    }

    public Optional<Camiseta> buscarPorSku(Connection con, String sku) {
        return buscarUna(con, SELECT + " WHERE sku = ?", sku, "buscar la camiseta " + sku);
    }

    public Optional<CamisetaDetalleDTO> buscarDetalle(Connection con, long id) {
        try (PreparedStatement ps = con.prepareStatement(SELECT_DETALLE + " WHERE c.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearDetalle(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar el detalle de la camiseta " + id, e);
        }
    }

    /** UPDATE de un dato comercial. return filas modificadas */
    public int actualizarPrecio(Connection con, long id, BigDecimal precio) {
        try (PreparedStatement ps = con.prepareStatement("UPDATE camiseta SET precio = ? WHERE id = ?")) {
            ps.setBigDecimal(1, precio);
            ps.setLong(2, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw JdbcUtil.error("actualizar el precio de la camiseta " + id, e);
        }
    }

    public int actualizarActiva(Connection con, long id, boolean activa) {
        try (PreparedStatement ps = con.prepareStatement("UPDATE camiseta SET activa = ? WHERE id = ?")) {
            ps.setBoolean(1, activa);
            ps.setLong(2, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw JdbcUtil.error("actualizar la disponibilidad de la camiseta " + id, e);
        }
    }

    /**
     * Catálogo filtrado y paginado en SQL: solo se recuperan las filas de la página pedida,
     * con orden definido (nombre, id). pagina empieza en 1.
     */
    public List<CamisetaDetalleDTO> buscarCatalogo(Connection con, CatalogoFiltro filtro, int pagina, int tamano) {
        List<Object> parametros = new ArrayList<>();
        String where = construirWhere(filtro, parametros);
        String sql = "SELECT c.id, c.sku, c.nombre, e.nombre AS equipo, r.nombre AS era, j.nombre AS jugador, "
                + "c.dorsal, c.temporada, c.equipacion, c.edicion, c.precio, c.activa, i.stock "
                + FROM_CATALOGO + where + " ORDER BY c.nombre, c.id LIMIT ? OFFSET ?";
        parametros.add(tamano);
        parametros.add((pagina - 1) * tamano);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            asignarParametros(ps, parametros);
            try (ResultSet rs = ps.executeQuery()) {
                List<CamisetaDetalleDTO> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapearDetalle(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("consultar el catálogo", e);
        }
    }

    public long contarCatalogo(Connection con, CatalogoFiltro filtro) {
        List<Object> parametros = new ArrayList<>();
        String sql = "SELECT COUNT(*) " + FROM_CATALOGO + construirWhere(filtro, parametros);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            asignarParametros(ps, parametros);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("contar el catálogo", e);
        }
    }

    /** Consulta propia de la temática: todas las camisetas de un jugador a lo largo de las eras y los equipos. */
    public List<CamisetaDetalleDTO> buscarPorJugador(Connection con, String nombreJugador) {
        String sql = SELECT_DETALLE + " WHERE LOWER(j.nombre) LIKE ? ORDER BY r.anio_inicio, c.temporada, c.id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, "%" + nombreJugador.toLowerCase(Locale.ROOT) + "%");
            try (ResultSet rs = ps.executeQuery()) {
                List<CamisetaDetalleDTO> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapearDetalle(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("buscar las camisetas del jugador " + nombreJugador, e);
        }
    }

    public long contar(Connection con) {
        return JdbcUtil.contar(con, "camiseta");
    }

    // ---------------------------------------------------------------- privados

    private String construirWhere(CatalogoFiltro f, List<Object> parametros) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (f.equipoId() != null) {
            where.append(" AND c.equipo_id = ?");
            parametros.add(f.equipoId());
        }
        if (f.eraId() != null) {
            where.append(" AND c.era_id = ?");
            parametros.add(f.eraId());
        }
        if (f.categoriaId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM camiseta_categoria cc WHERE cc.camiseta_id = c.id AND cc.categoria_id = ?)");
            parametros.add(f.categoriaId());
        }
        if (f.jugador() != null && !f.jugador().isBlank()) {
            where.append(" AND LOWER(j.nombre) LIKE ?");
            parametros.add("%" + f.jugador().trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (f.precioMin() != null) {
            where.append(" AND c.precio >= ?");
            parametros.add(f.precioMin());
        }
        if (f.precioMax() != null) {
            where.append(" AND c.precio <= ?");
            parametros.add(f.precioMax());
        }
        if (f.soloDisponibles()) {
            where.append(" AND c.activa = TRUE AND (i.stock > 0 OR c.control_stock = FALSE)");
        }
        return where.toString();
    }

    private void asignarParametros(PreparedStatement ps, List<Object> parametros) throws SQLException {
        for (int i = 0; i < parametros.size(); i++) {
            ps.setObject(i + 1, parametros.get(i));
        }
    }

    private Optional<Camiseta> buscarUna(Connection con, String sql, Object parametro, String contexto) {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw JdbcUtil.error(contexto, e);
        }
    }

    private void rellenar(PreparedStatement ps, Camiseta c) throws SQLException {
        ps.setString(1, c.sku());
        ps.setString(2, c.nombre());
        ps.setLong(3, c.equipoId());
        ps.setLong(4, c.eraId());
        JdbcUtil.setLongNullable(ps, 5, c.jugadorId());
        JdbcUtil.setIntNullable(ps, 6, c.dorsal());
        ps.setString(7, c.temporada());
        ps.setString(8, c.equipacion().name());
        ps.setString(9, c.edicion().name());
        ps.setBigDecimal(10, c.precio());
        ps.setBoolean(11, c.activa());
        ps.setBoolean(12, c.controlStock());
    }

    private Camiseta mapear(ResultSet rs) throws SQLException {
        return new Camiseta(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("nombre"),
                rs.getLong("equipo_id"),
                rs.getLong("era_id"),
                JdbcUtil.getLongNullable(rs, "jugador_id"),
                JdbcUtil.getIntNullable(rs, "dorsal"),
                rs.getString("temporada"),
                Camiseta.Equipacion.valueOf(rs.getString("equipacion")),
                Camiseta.Edicion.valueOf(rs.getString("edicion")),
                rs.getBigDecimal("precio"),
                rs.getBoolean("activa"),
                rs.getBoolean("control_stock"));
    }

    private CamisetaDetalleDTO mapearDetalle(ResultSet rs) throws SQLException {
        return new CamisetaDetalleDTO(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("nombre"),
                rs.getString("equipo"),
                rs.getString("era"),
                rs.getString("jugador"),
                JdbcUtil.getIntNullable(rs, "dorsal"),
                rs.getString("temporada"),
                Camiseta.Equipacion.valueOf(rs.getString("equipacion")),
                Camiseta.Edicion.valueOf(rs.getString("edicion")),
                rs.getBigDecimal("precio"),
                rs.getBoolean("activa"),
                rs.getInt("stock"));
    }
}
