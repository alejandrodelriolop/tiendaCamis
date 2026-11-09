package es.camisetas.connections;


import es.camisetas.config.Configuracion;
import es.camisetas.config.ModoConexion;



public final class ConnectionProviderFactory {

    private ConnectionProviderFactory() {
    }

    public static ConnectionProvider crear(Configuracion config) {
        if (config.modoConexion() == ModoConexion.POOL) {
            return new HikariConnectionProvider(config);
        }
        return new DriverManagerConnectionProvider(config.url(), config.usuario(), config.password());
    }
}
