package es.camisetas.connections;



import es.camisetas.exceptions.PersistenciaException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Ejecuta scripts SQL guardados como recursos del classpath. */
public final class SqlScriptRunner {

    private SqlScriptRunner() {
    }

    /** Ejecuta un script con varias sentencias separadas por ';' al final de línea. Devuelve cuántas ejecutó. */
    public static int ejecutarScript(Connection con, String recurso) {
        List<String> sentencias = dividir(leer(recurso));
        try (Statement st = con.createStatement()) {
            for (String sql : sentencias) {
                st.execute(sql);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Error ejecutando el script " + recurso, e);
        }
        return sentencias.size();
    }

    /** Ejecuta un fichero que contiene una única sentencia (p. ej. CREATE PROCEDURE, cuyo cuerpo lleva ';'). */
    public static void ejecutarSentenciaUnica(Connection con, String recurso) {
        ejecutarSql(con, leer(recurso).trim());
    }

    public static void ejecutarSql(Connection con, String sql) {
        try (Statement st = con.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new PersistenciaException("Error ejecutando SQL", e);
        }
    }

    private static String leer(String recurso) {
        try (InputStream in = SqlScriptRunner.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("No existe el recurso SQL " + recurso);
            }
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return lector.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el recurso SQL " + recurso, e);
        }
    }

    private static List<String> dividir(String script) {
        List<String> sentencias = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String linea : script.split("\n")) {
            String limpia = linea.strip();
            if (limpia.isEmpty() || limpia.startsWith("--")) {
                continue;
            }
            actual.append(linea).append('\n');
            if (limpia.endsWith(";")) {
                String sql = actual.toString().strip();
                sentencias.add(sql.substring(0, sql.length() - 1));
                actual.setLength(0);
            }
        }
        if (!actual.toString().isBlank()) {
            sentencias.add(actual.toString().strip());
        }
        return sentencias;
    }
}
