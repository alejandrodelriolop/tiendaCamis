package es.camisetas.exceptions;

/**
 * Situación de negocio prevista (falta de stock, cancelación duplicada...). No es un fallo
 * técnico: la operación se rechaza y la base de datos queda como estaba.
 */
public class NegocioException extends RuntimeException {

    public enum Causa {
        NO_ENCONTRADO,
        VALIDACION,
        STOCK_INSUFICIENTE,
        PRODUCTO_NO_DISPONIBLE,
        LIMITE_EDICION,
        ESTADO_INVALIDO,
        PEDIDO_YA_CANCELADO
    }

    private final Causa causa;

    public NegocioException(Causa causa, String mensaje) {
        super(mensaje);
        this.causa = causa;
    }

    public Causa getCausa() {
        return causa;
    }

    public static NegocioException noEncontrado(String entidad, Object id) {
        return new NegocioException(Causa.NO_ENCONTRADO, entidad + " no encontrado: " + id);
    }

    public static NegocioException validacion(String mensaje) {
        return new NegocioException(Causa.VALIDACION, mensaje);
    }

    public static NegocioException stockInsuficiente(String sku, int solicitado, int disponible) {
        return new NegocioException(Causa.STOCK_INSUFICIENTE,
                "Stock insuficiente de " + sku + ": solicitadas " + solicitado + ", disponibles " + disponible);
    }

    public static NegocioException productoNoDisponible(String sku) {
        return new NegocioException(Causa.PRODUCTO_NO_DISPONIBLE, "La camiseta " + sku + " no está disponible para la venta");
    }

    public static NegocioException limiteEdicion(String sku, int maximo) {
        return new NegocioException(Causa.LIMITE_EDICION,
                "La camiseta " + sku + " es de edición coleccionista: máximo " + maximo + " unidades por pedido");
    }

    public static NegocioException estadoInvalido(String mensaje) {
        return new NegocioException(Causa.ESTADO_INVALIDO, mensaje);
    }

    public static NegocioException pedidoYaCancelado(long pedidoId) {
        return new NegocioException(Causa.PEDIDO_YA_CANCELADO, "El pedido " + pedidoId + " ya estaba cancelado");
    }
}

