package es.camisetas.dto;

/** Camiseta cuyo stock ha llegado al umbral de reposición. deficit = umbral - stock (calculado en SQL). */
public record StockBajoDTO(long camisetaId, String sku, String nombre, int stock, int umbral, int deficit) {
}
