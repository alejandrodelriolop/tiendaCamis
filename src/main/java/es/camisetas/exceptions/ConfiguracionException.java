package es.camisetas.exceptions;
/** Problema de infraestructura: la configuración externa falta o no es válida. */
public class ConfiguracionException extends RuntimeException {

    public ConfiguracionException(String mensaje) {
        super(mensaje);
    }

    public ConfiguracionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}