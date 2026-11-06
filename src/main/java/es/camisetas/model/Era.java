package es.camisetas.model;


/** Período histórico al que pertenece una camiseta. {@code anioFin} es null si la era sigue abierta. */
public record Era(Long id, String nombre, int anioInicio, Integer anioFin) {
}