package es.camisetas.dto;

import java.util.List;

/** Una página de resultados, empieza en 1. */
public record Pagina<T>(List<T> elementos, int numero, int tamano, long totalElementos) {

    public long totalPaginas() {
        return (totalElementos + tamano - 1) / tamano;
    }
}
