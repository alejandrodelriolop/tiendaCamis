package es.camisetas.dto;

import java.math.BigDecimal;

/** Resultado del procedimiento almacenado sp_resumen_equipo (solo MySQL). */
public record ResumenEquipoDTO(long equipoId, String equipo, int unidades, BigDecimal ingresos, int pedidos) {
}