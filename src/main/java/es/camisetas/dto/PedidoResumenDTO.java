package es.camisetas.dto;
import es.camisetas.model.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una fila del historial de pedidos de un cliente. */
public record PedidoResumenDTO(long pedidoId, LocalDateTime fecha, Pedido.Estado estado, BigDecimal total, int lineas, int unidades) {
}

