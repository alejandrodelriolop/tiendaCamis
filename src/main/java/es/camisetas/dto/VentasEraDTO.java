package es.camisetas.dto;

import java.math.BigDecimal;

/** Consulta propia de la temática: ¿qué eras del fútbol venden más? */
public record VentasEraDTO(String era, long unidades, BigDecimal ingresos) {
}
