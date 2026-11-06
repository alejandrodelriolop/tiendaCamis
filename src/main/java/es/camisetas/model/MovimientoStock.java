package es.camisetas.model;

import java.time.LocalDateTime;

/** Entrada o salida de existencias. La cantidad siempre es positiva; el signo lo da el tipo. */
public record MovimientoStock(
        Long id,
        long camisetaId,
        Tipo tipo,
        int cantidad,
        LocalDateTime fecha,
        Long pedidoId,
        String motivo) {

    public enum Tipo {
        ENTRADA_REPOSICION,
        SALIDA_PEDIDO,
        ENTRADA_CANCELACION
    }
}