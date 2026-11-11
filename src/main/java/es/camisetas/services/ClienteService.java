package es.camisetas.services;



import es.camisetas.dao.*;
import es.camisetas.connections.Transacciones;
import es.camisetas.dto.*;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;

public class ClienteService {

    private static final Logger log = LogManager.getLogger(ClienteService.class);

    private final Transacciones tx;
    private final ClienteDAO clienteDao = new ClienteDAO();
    private final DireccionDAO direccionDao = new DireccionDAO();
    private final PedidoDAO pedidoDao = new PedidoDAO();

    public ClienteService(Transacciones tx) {
        this.tx = tx;
    }

    /**
     * Registra un cliente y sus direcciones en una sola transacción. El id del cliente generado por el
     * SGBD se usa para vincular las direcciones.
     */
    public long registrar(Cliente cliente, List<Direccion> direcciones) {
        if (cliente.nombre() == null || cliente.nombre().isBlank()) {
            throw NegocioException.validacion("El nombre del cliente es obligatorio");
        }
        if (cliente.email() == null || !cliente.email().contains("@")) {
            throw NegocioException.validacion("El email del cliente no es válido");
        }
        if (direcciones == null || direcciones.isEmpty()) {
            throw NegocioException.validacion("El cliente necesita al menos una dirección");
        }
        return tx.ejecutar("alta del cliente " + cliente.email(), con -> {
            if (clienteDao.buscarPorEmail(con, cliente.email()).isPresent()) {
                throw NegocioException.validacion("Ya existe un cliente con el email " + cliente.email());
            }
            long clienteId = clienteDao.insertar(con, cliente);
            List<Direccion> conCliente = new ArrayList<>();
            for (Direccion d : direcciones) {
                conCliente.add(new Direccion(null, clienteId, d.calle(), d.ciudad(), d.codigoPostal(), d.pais(), d.principal()));
            }
            direccionDao.insertarLote(con, conCliente);
            log.info("Cliente registrado id={} direcciones={}", clienteId, conCliente.size());
            return clienteId;
        });
    }

    /** Recupera conjuntamente el cliente, sus direcciones y su historial de pedidos con una misma conexión. */
    public ClienteCompletoDTO obtenerCompleto(long clienteId) {
        return tx.consultar(con -> {
            Cliente cliente = clienteDao.buscarPorId(con, clienteId)
                    .orElseThrow(() -> NegocioException.noEncontrado("Cliente", clienteId));
            return new ClienteCompletoDTO(cliente,
                    direccionDao.listarPorCliente(con, clienteId),
                    pedidoDao.historialDeCliente(con, clienteId));
        });
    }

    public Cliente buscarPorEmail(String email) {
        return tx.consultar(con -> clienteDao.buscarPorEmail(con, email)
                .orElseThrow(() -> NegocioException.noEncontrado("Cliente con email", email)));
    }

    public List<ClienteResumenDTO> resumenDeClientes() {
        return tx.consultar(clienteDao::listarResumen);
    }
}

