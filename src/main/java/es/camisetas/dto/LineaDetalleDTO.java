package es.camisetas.dto;

import java.math.BigDecimal;

public record LineaDetalleDTO(String sku, String camiseta, int cantidad, BigDecimal precioUnitario, BigDecimal importe) {
}
