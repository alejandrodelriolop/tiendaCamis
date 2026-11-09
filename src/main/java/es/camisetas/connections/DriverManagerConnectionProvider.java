package es.camisetas.connections;

import es.camisetas.exceptions.ConexionException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Abre una conexión física nueva en cada llamada. */
public class DriverManagerConnectionProvider implements ConnectionProvider {

    private final String url;
    private final String usuario;
    private final String password;

    public DriverManagerConnectionProvider(String url, String usuario, String password) {
        this.url = url;
        this.usuario = usuario;
        this.password = password;
    }

    @Override
    public Connection getConnection() {
        try {
            return DriverManager.getConnection(url, usuario, password);
        } catch (SQLException e) {
            throw new ConexionException("No se pudo abrir la conexión con DriverManager", e);
        }
    }

    @Override
    public String descripcion() {
        return "DriverManager";
    }

    @Override
    public void close() {
        // No hay recursos compartidos que liberar.
    }
}