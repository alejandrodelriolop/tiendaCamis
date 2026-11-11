package es.camisetas.dao;



import es.camisetas.dto.ResumenEquipoDTO;
import es.camisetas.dto.VentasCamisetaDTO;
import es.camisetas.dto.VentasEraDTO;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Consultas de gestión con agregaciones. Los pedidos cancelados no cuentan como venta. */
public class EstadisticasDAO {

    /** Camisetas más vendidas: JOIN de tres tablas + GROUP BY. */
    public List<VentasCamisetaDTO> topCamisetasVendidas(Connection con, int limite) {
        String sql = """
                SELECT c.id, c.sku, c.nombre,
                       SUM(lp.cantidad) AS unidades,
                       SUM(lp.importe)  AS ingresos
                FROM linea_pedido lp
                JOIN pedido p   ON p.id = lp.pedido_id
                JOIN camiseta c ON c.id = lp.camiseta_id
                WHERE p.estado <> 'CANCELADO'
                GROUP BY c.id, c.sku, c.nombre
                ORDER BY unidades DESC, ingresos DESC, c.id
                LIMIT ?
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<VentasCamisetaDTO> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new VentasCamisetaDTO(rs.getLong("id"), rs.getString("sku"), rs.getString("nombre"),
                            rs.getLong("unidades"), rs.getBigDecimal("ingresos")));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw JdbcUtil.error("consultar las camisetas más vendidas", e);
        }
    }

    /** Consulta propia de la temática: ventas agrupadas por era (de la más antigua a la más reciente). */
    public List<VentasEraDTO> ventasPorEra(Connection con) {
        String sql = """
                SELECT r.nombre AS era,
                       SUM(lp.cantidad) AS unidades,
                       SUM(lp.importe)  AS ingresos
                FROM linea_pedido lp
                JOIN pedido p   ON p.id = lp.pedido_id
                JOIN camiseta c ON c.id = lp.camiseta_id
                JOIN era r      ON r.id = c.era_id
                WHERE p.estado <> 'CANCELADO'
                GROUP BY r.id, r.nombre, r.anio_inicio
                ORDER BY r.anio_inicio
                """;
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<VentasEraDTO> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new VentasEraDTO(rs.getString("era"), rs.getLong("unidades"), rs.getBigDecimal("ingresos")));
            }
            return lista;
        } catch (SQLException e) {
            throw JdbcUtil.error("consultar las ventas por era", e);
        }
    }

    /**
     * Llama al procedimiento almacenado de MySQL sp_resumen_equipo(IN equipo, OUT unidades, OUT ingresos, OUT pedidos)
     * mediante CallableStatement. El nombre del equipo lo completa el servicio.
     */
    public ResumenEquipoDTO resumenEquipoConProcedimiento(Connection con, long equipoId, String nombreEquipo) {
        try (CallableStatement cs = con.prepareCall("{call sp_resumen_equipo(?, ?, ?, ?)}")) {
            cs.setLong(1, equipoId);
            cs.registerOutParameter(2, Types.INTEGER);
            cs.registerOutParameter(3, Types.DECIMAL);
            cs.registerOutParameter(4, Types.INTEGER);
            cs.execute();
            int unidades = cs.getInt(2);
            BigDecimal ingresos = cs.getBigDecimal(3);
            int pedidos = cs.getInt(4);
            return new ResumenEquipoDTO(equipoId, nombreEquipo, unidades, ingresos, pedidos);
        } catch (SQLException e) {
            throw JdbcUtil.error("llamar al procedimiento sp_resumen_equipo", e);
        }
    }
}
