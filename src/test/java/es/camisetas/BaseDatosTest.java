package es.camisetas;

import es.camisetas.config.Configuracion;
import es.camisetas.config.GestorDB;
import es.camisetas.config.ModoConexion;
import es.camisetas.connections.ConnectionProvider;
import es.camisetas.connections.ConnectionProviderFactory;
import es.camisetas.dto.ItemPedido;
import es.camisetas.dto.SolicitudPedido;
import es.camisetas.init.DatabaseInitializer;
import es.camisetas.model.Camiseta;
import es.camisetas.model.Cliente;
import es.camisetas.services.Services;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class BaseDatosTest {

    protected ConnectionProvider provider;
    protected Services servicios;

    protected GestorDB gestorBD() {
        return GestorDB.valueOf(System.getProperty("gestor", "H2"));
    }

    @BeforeEach
    void prepararBaseDeDatos() {
        prepararCon(gestorBD(), ModoConexion.DRIVER_MANAGER);
    }

    protected void prepararCon(GestorDB gestor, ModoConexion modo) {
        cerrarProveedor();

        String nombreBd = "test_" + UUID.randomUUID().toString().replace("-", "");

        Configuracion config = Configuracion.paraPruebas(gestor, nombreBd, modo);

        provider = ConnectionProviderFactory.crear(config);

        new DatabaseInitializer(provider, gestor).inicializar();

        servicios = Services.crear(provider, gestor);
    }

    @AfterEach
    void cerrarProveedor() {
        if (provider != null) {
            provider.close();
            provider = null;
        }
    }

    protected Camiseta camiseta(String sku) {
        return servicios.catalogo().buscarPorSku(sku);
    }

    protected Cliente cliente(String email) {
        return servicios.clientes().buscarPorEmail(email);
    }

    protected int stock(String sku) {
        return servicios.inventario().consultarStock(camiseta(sku).id()).stock();
    }

    /**
     * Pedido de la dirección principal del cliente con las camisetas indicadas
     * (sku, cantidad, sku, cantidad...).
     */
    protected SolicitudPedido solicitud(String email, Object... skuYCantidad) {
        Cliente cliente = cliente(email);

        long direccionId = servicios.clientes()
                .obtenerCompleto(cliente.id())
                .direcciones()
                .get(0)
                .id();

        List<ItemPedido> items = new ArrayList<>();

        for (int i = 0; i < skuYCantidad.length; i += 2) {
            items.add(new ItemPedido(
                    camiseta((String) skuYCantidad[i]).id(),
                    (Integer) skuYCantidad[i + 1]
            ));
        }

        return new SolicitudPedido(cliente.id(), direccionId, items);
    }
}