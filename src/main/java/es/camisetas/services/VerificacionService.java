package es.camisetas.services;

import es.camisetas.connections.Transacciones;
import es.camisetas.dao.*;
import es.camisetas.dto.InstantaneaBD;

/** Lee el estado persistido con consultas SQL para comprobar el
 * efecto real de una operación. */
public class VerificacionService {

    private final Transacciones tx;
    private final PedidoDAO pedidoDao = new PedidoDAO();
    private final LineaPedidoDAO lineaDao = new LineaPedidoDAO();
    private final MovimientoStockDAO movimientoDao = new MovimientoStockDAO();
    private final PagoDAO pagoDao = new PagoDAO();
    private final InventarioDAO inventarioDao = new InventarioDAO();

    public VerificacionService(Transacciones tx) {
        this.tx = tx;
    }

    public InstantaneaBD instantanea() {
        return tx.consultar(con -> new InstantaneaBD(
                pedidoDao.contar(con),
                lineaDao.contar(con),
                movimientoDao.contar(con),
                pagoDao.contar(con),
                pedidoDao.contarSinLineas(con),
                inventarioDao.sumarStock(con)));
    }
}
