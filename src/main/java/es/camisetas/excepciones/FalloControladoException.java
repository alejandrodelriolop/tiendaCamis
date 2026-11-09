package es.camisetas.excepciones;


/**
 * Error provocado a propósito (modo FALLO_TRANSACCION) después de modificar la base de datos
 * y antes del commit, para demostrar que el rollback deshace los cambios reales.
 */
public class FalloControladoException extends RuntimeException {

    public FalloControladoException(String mensaje) {
        super(mensaje);
    }
}