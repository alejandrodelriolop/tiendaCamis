package es.camisetas.model;

import java.math.BigDecimal;

/**
 * Producto del catálogo. Lo que la especializa: pertenece a un equipo y a una era, puede llevar
 * estampado un jugador (con su dorsal) y tiene una edición (réplica, retro, coleccionista...).
 */
public record Camiseta(
        Long id,
        String sku,
        String nombre,
        long equipoId,
        long eraId,
        Long jugadorId,
        Integer dorsal,
        String temporada,
        Equipacion equipacion,
        Edicion edicion,
        BigDecimal precio,
        boolean activa,
        boolean controlStock) {

    public enum Equipacion { LOCAL, VISITANTE, TERCERA, PORTERO }

    /** COLECCIONISTA es edición limitada: la tienda restringe las unidades por pedido. */
    public enum Edicion { REPLICA, JUGADOR, RETRO, COLECCIONISTA }
}