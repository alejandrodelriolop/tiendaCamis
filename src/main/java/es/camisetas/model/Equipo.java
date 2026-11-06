package es.camisetas.model;

/** Club o selección. Relación 1:N con {@link Camiseta}. */
public record Equipo(Long id, String nombre, String pais, String liga) {
}
