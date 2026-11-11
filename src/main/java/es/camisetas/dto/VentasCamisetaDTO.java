package es.camisetas.dto;

import java.math.BigDecimal;

public record VentasCamisetaDTO(long camisetaId, String sku, String nombre, long unidades, BigDecimal ingresos) {
}
