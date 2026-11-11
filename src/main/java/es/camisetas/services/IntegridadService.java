package es.camisetas.services;

import es.camisetas.connections.Transacciones;
import es.camisetas.dao.CamisetaDAO;
import es.camisetas.dao.ClienteDAO;
import es.camisetas.dao.DireccionDAO;
import es.camisetas.dto.ErrorIntegridadDTO;
import es.camisetas.exceptions.PersistenciaException;
import es.camisetas.model.Cliente;
import es.camisetas.model.Direccion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.sql.Connection;

/**
 * Provoca violaciones de integridad REALES (las rechaza el SGBD, no el código Java) y recoge los datos
 * técnicos de la SQLException: mensaje del driver, SQLState y código de error.
 */
public class IntegridadService {

    private static final Logger log = LogManager.getLogger(IntegridadService.class);

    private final Transacciones tx;
    private final ClienteDAO clienteDao = new ClienteDAO();
    private final DireccionDAO direccionDao = new DireccionDAO();
    private final CamisetaDAO camisetaDao = new CamisetaDAO();

    public IntegridadService(Transacciones tx) {
        this.tx = tx;
    }

    /**
     * @param emailExistente email ya registrado (para duplicarlo)
     * @param camisetaId     camiseta existente (para darle un precio inválido)
     */
    public List<ErrorIntegridadDTO> provocarViolaciones(String emailExistente, long camisetaId) {
        List<ErrorIntegridadDTO> errores = new ArrayList<>();
        errores.add(intentar("UNIQUE: segundo cliente con el email " + emailExistente,
                con -> clienteDao.insertar(con, new Cliente(null, "Cliente duplicado", emailExistente, LocalDate.of(2026, 1, 1)))));
        errores.add(intentar("FOREIGN KEY: dirección de un cliente inexistente (id 999999)",
                con -> direccionDao.insertar(con, new Direccion(null, 999_999L, "Calle Fantasma 1", "Madrid", "28001", "España", false))));
        errores.add(intentar("CHECK: precio negativo en la camiseta " + camisetaId,
                con -> camisetaDao.actualizarPrecio(con, camisetaId, new BigDecimal("-5.00"))));
        return errores;
    }

    private ErrorIntegridadDTO intentar(String situacion, Consumer<Connection> operacion) {
        return tx.consultar(con -> {
            try {
                operacion.accept(con);
            } catch (PersistenciaException e) {
                if (!e.esViolacionIntegridad()) {
                    throw e;
                }
                log.warn("Violación de integridad [{}] sqlState={} errorCode={} mensaje={}",
                        situacion, e.getSqlState(), e.getErrorCode(), e.getMensajeDriver());
                return new ErrorIntegridadDTO(situacion, e.getSqlState(), e.getErrorCode(), e.getMensajeDriver());
            }
            throw new IllegalStateException("La operación debía violar una restricción y no lo hizo: " + situacion);
        });
    }
}
