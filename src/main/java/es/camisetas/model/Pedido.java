package es.camisetas.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Pedido(Long id, long clienteId, long direccionId, LocalDateTime fecha, Estado estado, BigDecimal total) {

    /** PENDIENTE -> PAGADO -> CANCELADO (también se puede cancelar desde PENDIENTE). */
    public enum Estado { PENDIENTE, PAGADO, CANCELADO }
}
