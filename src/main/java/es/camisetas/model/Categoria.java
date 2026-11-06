package es.camisetas.model;

/** Agrupación comercial del catálogo. Relación N:M con Camiseta mediante camiseta_categoria. */
public record Categoria(Long id, String nombre, String descripcion) {
}
