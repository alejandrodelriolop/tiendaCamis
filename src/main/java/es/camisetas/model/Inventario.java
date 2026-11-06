package es.camisetas.model;

/** Existencias de una camiseta (relación 1:1: la clave primaria es la de la camiseta). */
public record Inventario(long camisetaId, int stock, int umbralReposicion) {
}
