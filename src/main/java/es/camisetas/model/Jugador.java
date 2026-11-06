package es.camisetas.model;

/** Jugador cuyo nombre y dorsal lleva estampados una camiseta. */
public record Jugador(Long id, String nombre, String pais, String posicion) {
}