package es.camisetas.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pago simulado (no hay pasarela real). Relación 1:1 con el pedido. */
public record Pago(Long id, long pedidoId, Metodo metodo, Estado estado, BigDecimal importe, LocalDateTime fecha) {

    public enum Metodo { TARJETA, PAYPAL, TRANSFERENCIA, BIZUM }

    /** COMPLETADO al pagar; REEMBOLSADO si se cancela un pedido ya pagado. */
    public enum Estado { COMPLETADO, REEMBOLSADO }
}
