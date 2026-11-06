package es.camisetas.model;


import java.math.BigDecimal;

/** Detalle de un pedido. Guarda el precio aplicado en la compra, independiente del catálogo. */
public record LineaPedido(Long id, long pedidoId, long camisetaId, int cantidad, BigDecimal precioUnitario, BigDecimal importe) {
}