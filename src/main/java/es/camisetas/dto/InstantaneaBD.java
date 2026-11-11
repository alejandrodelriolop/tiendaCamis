package es.camisetas.dto;

/**
 * Recuento del estado persistido, obtenido con consultas SQL. Dos instantáneas iguales antes y
 * después de una operación demuestran que no cambió nada (p. ej. tras un rollback).
 */
public record InstantaneaBD(long pedidos, long lineas, long movimientos, long pagos, long pedidosSinLineas, long stockTotal) {
}
