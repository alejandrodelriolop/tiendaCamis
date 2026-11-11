package es.camisetas.services;

import es.camisetas.config.GestorDB;
import es.camisetas.connections.Transacciones;
import es.camisetas.dao.EquipoDAO;
import es.camisetas.dao.EstadisticasDAO;
import es.camisetas.dto.ResumenEquipoDTO;
import es.camisetas.dto.VentasCamisetaDTO;
import es.camisetas.dto.VentasEraDTO;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.model.Equipo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;


/** Consultas de gestión (ventas). Los pedidos cancelados no cuentan. */
public class EstadisticasService {

    private static final Logger log = LogManager.getLogger(EstadisticasService.class);

    private final Transacciones tx;
    private final GestorDB gestor;
    private final EstadisticasDAO estadisticasDao = new EstadisticasDAO();
    private final EquipoDAO equipoDao = new EquipoDAO();

    public EstadisticasService(Transacciones tx, GestorDB gestor) {
        this.tx = tx;
        this.gestor = gestor;
    }

    public List<VentasCamisetaDTO> camisetasMasVendidas(int limite) {
        return tx.consultar(con -> estadisticasDao.topCamisetasVendidas(con, limite));
    }

    public List<VentasEraDTO> ventasPorEra() {
        return tx.consultar(estadisticasDao::ventasPorEra);
    }

    /** El procedimiento almacenado solo existe en MySQL; con H2 devuelve vacío sin intentar ejecutarlo. */
    public Optional<ResumenEquipoDTO> resumenDeEquipo(long equipoId) {
        if (gestor != GestorDB.MYSQL) {
            log.info("sp_resumen_equipo es específico de MySQL: no se ejecuta con {}", gestor);
            return Optional.empty();
        }
        return tx.consultar(con -> {
            Equipo equipo = equipoDao.buscarPorId(con, equipoId)
                    .orElseThrow(() -> NegocioException.noEncontrado("Equipo", equipoId));
            ResumenEquipoDTO resumen = estadisticasDao.resumenEquipoConProcedimiento(con, equipoId, equipo.nombre());
            log.info("sp_resumen_equipo equipo={} unidades={} ingresos={} pedidos={}",
                    equipoId, resumen.unidades(), resumen.ingresos(), resumen.pedidos());
            return Optional.of(resumen);
        });
    }
}