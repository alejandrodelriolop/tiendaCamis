package es.camisetas.services;

import es.camisetas.connections.Transacciones;
import es.camisetas.dao.CamisetaDAO;
import es.camisetas.dao.InventarioDAO;
import es.camisetas.dao.MovimientoStockDAO;
import es.camisetas.dto.IncoherenciaStockDTO;
import es.camisetas.dto.StockBajoDTO;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.model.Camiseta;
import es.camisetas.model.Inventario;
import es.camisetas.model.MovimientoStock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class InventarioService {

    private static final Logger log = LogManager.getLogger(InventarioService.class);

    private final Transacciones tx;
    private final CamisetaDAO camisetaDao = new CamisetaDAO();
    private final InventarioDAO inventarioDao = new InventarioDAO();
    private final MovimientoStockDAO movimientoDao = new MovimientoStockDAO();

    public InventarioService(Transacciones tx) {
        this.tx = tx;
    }

    public Inventario consultarStock(long camisetaId) {
        return tx.consultar(con -> inventarioDao.buscarPorCamiseta(con, camisetaId)
                .orElseThrow(() -> NegocioException.noEncontrado("Inventario de la camiseta", camisetaId)));
    }

    public List<MovimientoStock> movimientos(long camisetaId) {
        return tx.consultar(con -> movimientoDao.listarPorCamiseta(con, camisetaId));
    }

    /**
     * Entrada de mercancía: incrementa el stock y registra el movimiento en la misma transacción.
     * @return el stock resultante, leído de la base de datos
     */
    public int reponer(long camisetaId, int cantidad, String motivo) {
        if (cantidad <= 0) {
            throw NegocioException.validacion("La cantidad a reponer debe ser positiva");
        }
        return tx.ejecutar("reposición de la camiseta " + camisetaId, con -> {
            Camiseta camiseta = camisetaDao.buscarPorId(con, camisetaId)
                    .orElseThrow(() -> NegocioException.noEncontrado("Camiseta", camisetaId));
            if (!camiseta.controlStock()) {
                throw NegocioException.estadoInvalido("La camiseta " + camiseta.sku() + " se fabrica bajo pedido y no lleva control de stock");
            }
            inventarioDao.incrementarStock(con, camisetaId, cantidad);
            movimientoDao.insertar(con, new MovimientoStock(null, camisetaId, MovimientoStock.Tipo.ENTRADA_REPOSICION,
                    cantidad, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), null, motivo));
            int stock = inventarioDao.buscarPorCamiseta(con, camisetaId).orElseThrow().stock();
            log.info("Reposición registrada camiseta={} cantidad={} stockResultante={}", camisetaId, cantidad, stock);
            return stock;
        });
    }

    public List<StockBajoDTO> stockBajo() {
        return tx.consultar(inventarioDao::listarStockBajo);
    }

    /** Debe devolver siempre una lista vacía: el stock de cada camiseta equivale a la suma de sus movimientos. */
    public List<IncoherenciaStockDTO> incoherencias() {
        return tx.consultar(inventarioDao::listarIncoherencias);
    }
}
