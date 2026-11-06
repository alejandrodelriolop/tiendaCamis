package es.camisetas.model;

/** Dirección de un cliente (relación 1:N). */
public record Direccion(Long id, long clienteId, String calle, String ciudad, String codigoPostal, String pais, boolean principal) {
}
