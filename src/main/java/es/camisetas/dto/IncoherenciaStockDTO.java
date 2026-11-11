package es.camisetas.dto;

/** Camiseta cuyo stock no coincide con la suma de sus movimientos (no debería aparecer nunca). */
public record IncoherenciaStockDTO(String sku, int stockActual, int stockSegunMovimientos) {
}
