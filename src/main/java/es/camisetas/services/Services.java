package es.camisetas.services;

import es.camisetas.config.GestorDB;
import es.camisetas.connections.ConnectionProvider;
import es.camisetas.connections.Transacciones;

/** Agrupa los servicios de la tienda; se construyen igual
 *  con cualquier gestor y cualquier ConnectionProvider. */
public record Services(
                CatalogoService catalogo,
                ClienteService clientes,
                InventarioService inventario,
                PedidoService pedidos,
                EstadisticasService estadisticas,
                IntegridadService integridad,
                VerificacionService verificacion) {

    public static Services crear(ConnectionProvider provider, GestorDB gestor) {
        Transacciones tx = new Transacciones(provider);
        return new Services(
                new CatalogoService(tx),
                new ClienteService(tx),
                new InventarioService(tx),
                new PedidoService(tx),
                new EstadisticasService(tx, gestor),
                new IntegridadService(tx),
                new VerificacionService(tx));
    }
}
