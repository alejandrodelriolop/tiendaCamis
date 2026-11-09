package es.camisetas.connections;


import es.camisetas.config.Configuracion;
import es.camisetas.exceptions.ConexionException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;

/** Obtiene conexiones de un DataSource HikariCP; close() de cada conexión la devuelve al pool. */
public class HikariConnectionProvider implements ConnectionProvider {

    private static final Logger log = LogManager.getLogger(HikariConnectionProvider.class);

    private final HikariDataSource dataSource;

    public HikariConnectionProvider(Configuracion config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("TiendaCamisetasPool");
        hikari.setJdbcUrl(config.url());
        hikari.setUsername(config.usuario());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolMaximo());
        hikari.setMinimumIdle(config.poolMinimoInactivo());
        hikari.setConnectionTimeout(config.poolTimeoutMs());
        try {
            this.dataSource = new HikariDataSource(hikari);
        } catch (RuntimeException e) {
            throw new ConexionException("No se pudo inicializar el pool HikariCP",
                    e.getCause() instanceof SQLException sql ? sql : new SQLException(e.getMessage(), e));
        }
        log.info("Pool HikariCP creado: maximo={} minimoInactivo={}", config.poolMaximo(), config.poolMinimoInactivo());
    }

    @Override
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new ConexionException("No se pudo obtener una conexión del pool", e);
        }
    }

    @Override
    public String descripcion() {
        return "HikariCP";
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
            log.info("Pool HikariCP cerrado");
        }
    }
}
