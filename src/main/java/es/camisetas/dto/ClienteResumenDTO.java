package es.camisetas.dto;

import java.math.BigDecimal;

/** Cliente con valores calculados en SQL (COUNT y SUM de sus pedidos no cancelados). */
public record ClienteResumenDTO(long clienteId, String nombre, String email, int numPedidos, BigDecimal totalGastado) {
}
