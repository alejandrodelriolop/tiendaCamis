package es.camisetas.dto;

import es.camisetas.model.Camiseta;

import java.math.BigDecimal;
public record CamisetaDetalleDTO(
        long id,
        String sku,
        String nombre,
        String equipo,
        String era,
        String jugador,
        Integer dorsal,
        String temporada,
        Camiseta.Equipacion equipacion,
        Camiseta.Edicion edicion,
        BigDecimal precio,
        boolean activa,
        int stock) {
}