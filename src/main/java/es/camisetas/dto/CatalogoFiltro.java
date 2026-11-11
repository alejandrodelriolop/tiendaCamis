package es.camisetas.dto;

import java.math.BigDecimal;

/** Criterios opcionales de búsqueda en el catálogo; los campos null no filtran. */
public record CatalogoFiltro(
        Long equipoId,
        Long eraId,
        Long categoriaId,
        String jugador,
        BigDecimal precioMin,
        BigDecimal precioMax,
        boolean soloDisponibles) {

    public static CatalogoFiltro todo() {
        return new CatalogoFiltro(null, null, null, null, null, null, false);
    }

    public CatalogoFiltro conCategoria(Long categoriaId) {
        return new CatalogoFiltro(equipoId, eraId, categoriaId, jugador, precioMin, precioMax, soloDisponibles);
    }

    public CatalogoFiltro conPrecio(BigDecimal min, BigDecimal max) {
        return new CatalogoFiltro(equipoId, eraId, categoriaId, jugador, min, max, soloDisponibles);
    }

    public CatalogoFiltro conEra(Long eraId) {
        return new CatalogoFiltro(equipoId, eraId, categoriaId, jugador, precioMin, precioMax, soloDisponibles);
    }

    public CatalogoFiltro disponibles() {
        return new CatalogoFiltro(equipoId, eraId, categoriaId, jugador, precioMin, precioMax, true);
    }
}
