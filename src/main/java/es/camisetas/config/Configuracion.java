package es.camisetas.config;

import es.camisetas.exceptions.ConfiguracionException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Configuración de la aplicación leída de application.properties.
 * La URL, el usuario y la contraseña corresponden ya al gestor seleccionado.
 */
public record Configuracion(
        GestorDB gestor,
        ModoConexion modoConexion,
        ModoSimulacion modoSimulacion,
        String url,
        String usuario,
        String password,
        int poolMaximo,
        int poolMinimoInactivo,
        long poolTimeoutMs,
        boolean interactivo) {

    public static final String PROPIEDAD_RUTA = "app.config";
    public static final String FICHERO_POR_DEFECTO = "application.properties";

    private static final String URL_H2_POR_DEFECTO = "jdbc:h2:mem:unidad2;DB_CLOSE_DELAY=-1";

    /** Carga el fichero indicado con -Dapp.config=... o, si no se indica, ./application.properties. */
    public static Configuracion cargar() {
        Path ruta = Path.of(System.getProperty(PROPIEDAD_RUTA, FICHERO_POR_DEFECTO));
        if (!Files.isRegularFile(ruta)) {
            throw new ConfiguracionException("No se encuentra el fichero de configuración '" + ruta
                    + "'. Copia application.properties.example a application.properties y ajústalo.");
        }
        Properties props = new Properties();
        try (Reader lector = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
            props.load(lector);
        } catch (IOException e) {
            throw new ConfiguracionException("No se pudo leer '" + ruta + "'", e);
        }
        return desde(props);
    }

    /** Valida las propiedades y selecciona los datos de conexión del gestor elegido. */
    public static Configuracion desde(Properties props) {
        GestorDB gestor = leerEnum(props, "database.engine", GestorDB.class, null);
        ModoConexion modo = leerEnum(props, "connection.mode", ModoConexion.class, null);
        ModoSimulacion simulacion = leerEnum(props, "simulation.mode", ModoSimulacion.class, ModoSimulacion.NORMAL);

        String url;
        String usuario;
        String password;
        if (gestor == GestorDB.MYSQL) {
            url = obligatoria(props, "mysql.url");
            usuario = obligatoria(props, "mysql.user");
            password = props.getProperty("mysql.password", "");
        } else {
            url = props.getProperty("h2.url", URL_H2_POR_DEFECTO).trim();
            usuario = props.getProperty("h2.user", "sa").trim();
            password = props.getProperty("h2.password", "");
        }

        int maximo = leerEntero(props, "pool.maximumPoolSize", 5);
        int minimo = leerEntero(props, "pool.minimumIdle", 1);
        long timeout = leerEntero(props, "pool.connectionTimeoutMs", 10_000);
        if (minimo > maximo) {
            throw new ConfiguracionException("pool.minimumIdle (" + minimo + ") no puede superar pool.maximumPoolSize (" + maximo + ")");
        }
        boolean interactivo = leerBooleano(props, "app.interactive", true);
        return new Configuracion(gestor, modo, simulacion, url, usuario, password, maximo, minimo, timeout, interactivo);
    }

    /** Configuración H2 en memoria para pruebas: cada nombre de base de datos es independiente. */
    public static Configuracion paraPruebas(String nombreBd, ModoConexion modo) {
        return new Configuracion(GestorDB.H2, modo, ModoSimulacion.NORMAL,
                "jdbc:h2:mem:" + nombreBd + ";DB_CLOSE_DELAY=-1", "sa", "", 3, 1, 10_000, false);
    }

    private static boolean leerBooleano(Properties props, String clave, boolean porDefecto) {
        String valor = props.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new ConfiguracionException("La propiedad '" + clave + "' debe ser true o false: " + valor);
        };
    }

    private static String obligatoria(Properties props, String clave) {
        String valor = props.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new ConfiguracionException("Falta la propiedad obligatoria '" + clave + "'");
        }
        return valor.trim();
    }

    private static int leerEntero(Properties props, String clave, int porDefecto) {
        String valor = props.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            int n = Integer.parseInt(valor.trim());
            if (n <= 0) {
                throw new ConfiguracionException("La propiedad '" + clave + "' debe ser positiva: " + valor);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new ConfiguracionException("La propiedad '" + clave + "' debe ser un número entero: " + valor, e);
        }
    }

    private static <E extends Enum<E>> E leerEnum(Properties props, String clave, Class<E> tipo, E porDefecto) {
        String valor = props.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            if (porDefecto != null) {
                return porDefecto;
            }
            throw new ConfiguracionException("Falta la propiedad obligatoria '" + clave + "'");
        }
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfiguracionException("Valor no válido para '" + clave + "': " + valor
                    + " (admitidos: " + java.util.Arrays.toString(tipo.getEnumConstants()) + ")", e);
        }
    }

    /** No incluye la contraseña para que nunca llegue a los logs. */
    @Override
    public String toString() {
        return "Configuracion[gestor=" + gestor + ", conexiones=" + modoConexion
                + ", simulacion=" + modoSimulacion + ", interactivo=" + interactivo
                + ", url=" + url + ", usuario=" + usuario + "]";
    }
}