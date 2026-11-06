package es.camisetas.utilidades;

import java.sql.SQLException;

public class DatabaseUtils {

    /**
     * Imprime en la salida de error estándar los detalles principales de una SQLException.
     *
     * @param sqle La excepción de SQL atrapada.
     */
    public static void imprimirDetallesSQLException(SQLException sqle) {
        if (sqle != null) {
            System.err.println("--- Detalle del Error SQL ---");
            System.err.println("Mensaje: " + sqle.getMessage());
            System.err.println("SQLState: " + sqle.getSQLState());
            System.err.println("Código del proveedor: " + sqle.getErrorCode());
        }
    }
}