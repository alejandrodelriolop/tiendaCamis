package es.camisetas;

import es.camisetas.config.GestorDB;
import es.camisetas.dto.CamisetaDetalleDTO;
import es.camisetas.dto.CatalogoFiltro;
import es.camisetas.dto.Pagina;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.model.Camiseta;
import es.camisetas.model.Categoria;
import es.camisetas.model.MovimientoStock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogoPersistenciaTest extends BaseDatosTest {

    /** Inserción y consulta: lo guardado se recupera con los mismos valores. */
    @Test
    void registrarCamisetaGuardaCamisetaInventarioMovimientoYCategorias() {
        long equipo = servicios.catalogo().idDeEquipo("Liverpool");
        long era = servicios.catalogo().idDeEra("Años 2000");
        long retro = categoria("Retro");
        long clubes = categoria("Clubes");

        Camiseta nueva = new Camiseta(null, "CAM-TEST-01", "Liverpool 2005 Visitante", equipo, era, null, null,
                "2004/05", Camiseta.Equipacion.VISITANTE, Camiseta.Edicion.RETRO, new BigDecimal("69.90"), true, true);
        long id = servicios.catalogo().registrarCamiseta(nueva, 7, 3, List.of(retro, clubes));

        CamisetaDetalleDTO detalle = servicios.catalogo().detalle(id).orElseThrow();
        assertEquals("CAM-TEST-01", detalle.sku());
        assertEquals("Liverpool", detalle.equipo());
        assertEquals("Años 2000", detalle.era());
        assertEquals(0, new BigDecimal("69.90").compareTo(detalle.precio()));
        assertEquals(7, detalle.stock());
        assertEquals(List.of("Clubes", "Retro"), servicios.catalogo().categoriasDe(id).stream().map(Categoria::nombre).toList());

        List<MovimientoStock> movimientos = servicios.inventario().movimientos(id);
        assertEquals(1, movimientos.size());
        assertEquals(MovimientoStock.Tipo.ENTRADA_REPOSICION, movimientos.get(0).tipo());
        assertEquals(7, movimientos.get(0).cantidad());
    }

    /** Una camiseta mal formada se rechaza sin dejar nada a medias (ni camiseta ni inventario). */
    @Test
    void altaRechazadaNoDejaRegistrosParciales() {
        long equipo = servicios.catalogo().idDeEquipo("Liverpool");
        long era = servicios.catalogo().idDeEra("Años 2000");
        long totalAntes = servicios.catalogo().consultar(CatalogoFiltro.todo(), 1, 100).totalElementos();

        Camiseta duplicada = new Camiseta(null, "CAM-LIV-05-LOC", "SKU repetido", equipo, era, null, null,
                "2004/05", Camiseta.Equipacion.LOCAL, Camiseta.Edicion.REPLICA, new BigDecimal("50.00"), true, true);
        assertThrows(NegocioException.class, () -> servicios.catalogo().registrarCamiseta(duplicada, 5, 1, List.of()));

        Camiseta categoriaInexistente = new Camiseta(null, "CAM-TEST-02", "Con categoría inexistente", equipo, era, null, null,
                "2004/05", Camiseta.Equipacion.LOCAL, Camiseta.Edicion.REPLICA, new BigDecimal("50.00"), true, true);
        assertThrows(es.camisetas.exceptions.PersistenciaException.class,
                () -> servicios.catalogo().registrarCamiseta(categoriaInexistente, 5, 1, List.of(999_999L)));

        assertEquals(totalAntes, servicios.catalogo().consultar(CatalogoFiltro.todo(), 1, 100).totalElementos());
        assertThrows(NegocioException.class, () -> servicios.catalogo().buscarPorSku("CAM-TEST-02"));
    }

    /** Modificación: el UPDATE cambia el estado persistido y se puede volver a leer. */
    @Test
    void actualizarPrecioSePersiste() {
        Camiseta pele = camiseta("CAM-BRA-70-PEL");
        servicios.catalogo().actualizarPrecio(pele.id(), new BigDecimal("101.50"));

        assertEquals(0, new BigDecimal("101.50").compareTo(servicios.catalogo().detalle(pele.id()).orElseThrow().precio()));
        assertThrows(NegocioException.class, () -> servicios.catalogo().actualizarPrecio(pele.id(), BigDecimal.ZERO));
        assertThrows(NegocioException.class, () -> servicios.catalogo().actualizarPrecio(987_654L, new BigDecimal("10.00")));
    }

    /** Eliminación en la tabla N:M: quitar una categoría solo borra esa fila de camiseta_categoria. */
    @Test
    void desclasificarEliminaSoloLaRelacionIndicada() {
        Camiseta maradona = camiseta("CAM-ARG-86-MAR");
        List<String> antes = servicios.catalogo().categoriasDe(maradona.id()).stream().map(Categoria::nombre).toList();
        assertTrue(antes.contains("Leyendas"));

        servicios.catalogo().desclasificar(maradona.id(), categoria("Leyendas"));

        List<String> despues = servicios.catalogo().categoriasDe(maradona.id()).stream().map(Categoria::nombre).toList();
        assertFalse(despues.contains("Leyendas"));
        assertEquals(antes.size() - 1, despues.size());
        assertThrows(NegocioException.class, () -> servicios.catalogo().desclasificar(maradona.id(), categoria("Leyendas")));
    }

    /** Paginación en SQL: página, tamaño, total y orden estable sin repetir elementos entre páginas. */
    @Test
    void catalogoPaginadoDevuelveSoloLaPaginaSolicitada() {
        Pagina<CamisetaDetalleDTO> p1 = servicios.catalogo().consultar(CatalogoFiltro.todo(), 1, 5);
        Pagina<CamisetaDetalleDTO> p4 = servicios.catalogo().consultar(CatalogoFiltro.todo(), 4, 5);

        assertEquals(18, p1.totalElementos());
        assertEquals(4, p1.totalPaginas());
        assertEquals(5, p1.elementos().size());
        assertEquals(3, p4.elementos().size());

        List<String> todos = new java.util.ArrayList<>();
        for (int pagina = 1; pagina <= 4; pagina++) {
            servicios.catalogo().consultar(CatalogoFiltro.todo(), pagina, 5).elementos().forEach(c -> todos.add(c.sku()));
        }
        assertEquals(18, todos.stream().distinct().count());
        List<String> ordenados = servicios.catalogo().consultar(CatalogoFiltro.todo(), 1, 100).elementos().stream().map(CamisetaDetalleDTO::sku).toList();
        assertEquals(ordenados, todos);
    }

    /** Filtros combinados: categoría + precio + disponibilidad, y la consulta propia de la temática (por jugador). */
    @Test
    void filtrosDelCatalogoYBusquedaPorJugador() {
        CatalogoFiltro filtro = CatalogoFiltro.todo().conCategoria(categoria("Selecciones")).conPrecio(null, new BigDecimal("95.00")).disponibles();
        List<String> skus = servicios.catalogo().consultar(filtro, 1, 50).elementos().stream().map(CamisetaDetalleDTO::sku).toList();
        assertEquals(List.of("CAM-ARG-86-MAR", "CAM-BRA-02-RON", "CAM-BRA-70-PEL"), skus.stream().sorted().toList());

        List<CamisetaDetalleDTO> zidane = servicios.catalogo().camisetasDeJugador("zidane");
        assertEquals(List.of("Juventus", "Real Madrid"), zidane.stream().map(CamisetaDetalleDTO::equipo).sorted().toList());
        assertEquals("Años 90", zidane.get(0).era());

        // Una camiseta descatalogada no aparece entre las disponibles
        CatalogoFiltro disponibles = CatalogoFiltro.todo().disponibles();
        assertFalse(servicios.catalogo().consultar(disponibles, 1, 100).elementos().stream().anyMatch(c -> c.sku().equals("CAM-ATM-25-POR")));
        assertNotNull(servicios.catalogo().buscarPorSku("CAM-ATM-25-POR"));
    }

    private long categoria(String nombre) {
        return servicios.catalogo().listarCategorias().stream().filter(c -> c.nombre().equals(nombre)).findFirst().orElseThrow().id();
    }

}
