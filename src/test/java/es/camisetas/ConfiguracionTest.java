package es.camisetas;

import es.camisetas.config.Configuracion;
import es.camisetas.config.GestorDB;
import es.camisetas.config.ModoConexion;
import es.camisetas.config.ModoSimulacion;
import es.camisetas.exceptions.ConfiguracionException;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguracionTest {

    private Properties base() {
        Properties p = new Properties();
        p.setProperty("database.engine", "mysql");
        p.setProperty("connection.mode", "pool");
        p.setProperty("mysql.url", "jdbc:mysql://localhost:3306/tiendaCamisTest");
        p.setProperty("mysql.user", "tienda");
        p.setProperty("mysql.password", "secreto");
        return p;
    }

    @Test
    void seleccionaLosDatosDelGestorElegido() {
        Configuracion c = Configuracion.desde(base());
        assertEquals(GestorDB.MYSQL, c.gestor());
        assertEquals(ModoConexion.POOL, c.modoConexion());
        assertEquals(ModoSimulacion.NORMAL, c.modoSimulacion());          // valor por defecto
        assertEquals("jdbc:mysql://localhost:3306/tiendaCamisTest", c.url());
        assertEquals("tienda", c.usuario());

        Properties h2 = base();
        h2.setProperty("database.engine", "H2");
        h2.setProperty("h2.url", "jdbc:h2:mem:configTest");
        h2.setProperty("connection.mode", "DRIVER_MANAGER");
        h2.setProperty("simulation.mode", "FALLO_TRANSACCION");

        Configuracion c2 = Configuracion.desde(h2);

        assertEquals(GestorDB.H2, c2.gestor());
        assertEquals("jdbc:h2:mem:configTest", c2.url());
        assertEquals(ModoConexion.DRIVER_MANAGER, c2.modoConexion());
        assertEquals(ModoSimulacion.FALLO_TRANSACCION, c2.modoSimulacion());
    }

    @Test
    void elMenuInteractivoEstaActivadoPorDefectoYSePuedeDesactivar() {
        assertTrue(Configuracion.desde(base()).interactivo());

        Properties sinMenu = base();
        sinMenu.setProperty("app.interactive", "false");
        assertFalse(Configuracion.desde(sinMenu).interactivo());

        Properties invalido = base();
        invalido.setProperty("app.interactive", "quizas");
        assertThrows(ConfiguracionException.class, () -> Configuracion.desde(invalido));
    }

    @Test
    void rechazaValoresInvalidosOAusentes() {
        Properties sinGestor = base();
        sinGestor.remove("database.engine");
        assertThrows(ConfiguracionException.class, () -> Configuracion.desde(sinGestor));

        Properties gestorRaro = base();
        gestorRaro.setProperty("database.engine", "ORACLE");
        assertThrows(ConfiguracionException.class, () -> Configuracion.desde(gestorRaro));

        Properties sinUrl = base();
        sinUrl.remove("mysql.url");
        assertThrows(ConfiguracionException.class, () -> Configuracion.desde(sinUrl));

        Properties poolIncoherente = base();
        poolIncoherente.setProperty("pool.maximumPoolSize", "2");
        poolIncoherente.setProperty("pool.minimumIdle", "5");
        assertThrows(ConfiguracionException.class, () -> Configuracion.desde(poolIncoherente));
    }

    @Test
    void laContrasenaNuncaApareceEnElTexto() {
        assertFalse(Configuracion.desde(base()).toString().contains("secreto"));
    }
}
