package es.camisetas.excepciones;

import java.sql.SQLException;

import static es.camisetas.utilidades.DatabaseUtils.imprimirDetallesSQLException;

/** Problema de infraestructura: no se ha podido obtener una conexión (servidor caído, credenciales, pool agotado...). */
public class ConexionException extends RuntimeException {

    public ConexionException(String mensaje, SQLException causa) {
        super(mensaje, causa);
        imprimirDetallesSQLException(causa);
    }

}
