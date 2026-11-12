package es.camisetas.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Lectura de datos del usuario con validación. Si se cierra la entrada, lanza FinDeEntrada}. */
final class Consola {

    /** La entrada estándar se ha cerrado (Ctrl+D / fin del fichero): no se puede seguir preguntando. */
    static final class FinDeEntrada extends RuntimeException {
        FinDeEntrada() {
            super("Fin de la entrada");
        }
    }

    private final BufferedReader in;
    private final PrintStream out;

    Consola(InputStream in, PrintStream out) {
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
    }

    /** Muestra la pregunta y devuelve la línea escrita, sin espacios sobrantes (puede estar vacía). */
    String leer(String pregunta) {
        out.print(pregunta + ": ");
        out.flush();
        try {
            String linea = in.readLine();
            if (linea == null) {
                throw new FinDeEntrada();
            }
            return linea.trim();
        } catch (IOException e) {
            throw new FinDeEntrada();
        }
    }

    /** Pide un texto no vacío. */
    String obligatorio(String pregunta) {
        while (true) {
            String texto = leer(pregunta);
            if (!texto.isEmpty()) {
                return texto;
            }
            out.println("  Este dato es obligatorio.");
        }
    }

    /** Pide un número entero. */
    int entero(String pregunta) {
        while (true) {
            String texto = leer(pregunta);
            try {
                return Integer.parseInt(texto);
            } catch (NumberFormatException e) {
                out.println("  Escribe un número entero.");
            }
        }
    }

    long enteroLargo(String pregunta) {
        while (true) {
            String texto = leer(pregunta);
            try {
                return Long.parseLong(texto);
            } catch (NumberFormatException e) {
                out.println("  Escribe un número entero.");
            }
        }
    }

    /** Opción de menú entre {@code min} y {@code max}, ambos incluidos. */
    int opcion(String pregunta, int min, int max) {
        while (true) {
            String texto = leer(pregunta);
            try {
                int n = Integer.parseInt(texto);
                if (n >= min && n <= max) {
                    return n;
                }
            } catch (NumberFormatException e) {
                // se vuelve a preguntar
            }
            out.println("  Opción no válida: elige un número entre " + min + " y " + max + ".");
        }
    }

    /** Importe opcional: Enter devuelve null. Acepta coma o punto decimal. */
    BigDecimal decimalOpcional(String pregunta) {
        while (true) {
            String texto = leer(pregunta);
            if (texto.isEmpty()) {
                return null;
            }
            try {
                BigDecimal valor = new BigDecimal(texto.replace(',', '.'));
                if (valor.signum() >= 0) {
                    return valor;
                }
            } catch (NumberFormatException e) {
                // se vuelve a preguntar
            }
            out.println("  Escribe un importe válido (por ejemplo 89,90) o pulsa Enter.");
        }
    }

    /** Pregunta sí/no; Enter equivale a no. */
    boolean confirmar(String pregunta) {
        String r = leer(pregunta + " (s/n)").toLowerCase(Locale.ROOT);
        return r.equals("s") || r.equals("si") || r.equals("sí") || r.equals("y");
    }
}
