package es.camisetas.init;

import es.camisetas.dao.*;
import es.camisetas.model.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import es.camisetas.model.Camiseta.Equipacion;
import es.camisetas.model.Camiseta.Edicion;

/**
 * Datos de partida reproducibles. Todo se inserta por lotes (addBatch/executeBatch) y las relaciones se
 * construyen con los ids que devuelve el SGBD (getGeneratedKeys), nunca suponiendo el siguiente id.
 * La transacción la gestiona quien llama.
 */
public final class DatosIniciales {

    public record ResumenCarga(int equipos, int eras, int jugadores, int categorias, int camisetas,
                               int clasificaciones, int clientes, int direcciones, int movimientos) {
    }

    private DatosIniciales() {
    }

    public static ResumenCarga cargar(Connection con) {
        // ---- Equipos, eras, jugadores y categorías (4 lotes)
        List<Long> equipo = new EquipoDAO().insertarLote(con, List.of(
                new Equipo(null, "Real Madrid", "España", "LaLiga"),                  // 0
                new Equipo(null, "FC Barcelona", "España", "LaLiga"),                 // 1
                new Equipo(null, "Manchester United", "Inglaterra", "Premier League"), // 2
                new Equipo(null, "AC Milan", "Italia", "Serie A"),                    // 3
                new Equipo(null, "Brasil", "Brasil", "Selecciones"),                  // 4
                new Equipo(null, "Argentina", "Argentina", "Selecciones"),            // 5
                new Equipo(null, "Juventus", "Italia", "Serie A"),                    // 6
                new Equipo(null, "Liverpool", "Inglaterra", "Premier League"),        // 7
                new Equipo(null, "Bayern Múnich", "Alemania", "Bundesliga"),          // 8
                new Equipo(null, "Ajax", "Países Bajos", "Eredivisie"),               // 9
                new Equipo(null, "España", "España", "Selecciones"),                  // 10
                new Equipo(null, "Atlético de Madrid", "España", "LaLiga")));         // 11

        List<Long> era = new EraDAO().insertarLote(con, List.of(
                new Era(null, "Clásica", 1950, 1979),     // 0
                new Era(null, "Años 80", 1980, 1989),     // 1
                new Era(null, "Años 90", 1990, 1999),     // 2
                new Era(null, "Años 2000", 2000, 2009),   // 3
                new Era(null, "Era moderna", 2010, null))); // 4

        List<Long> jugador = new JugadorDAO().insertarLote(con, List.of(
                new Jugador(null, "Pelé", "Brasil", "Delantero"),                 // 0
                new Jugador(null, "Diego Maradona", "Argentina", "Mediapunta"),   // 1
                new Jugador(null, "Johan Cruyff", "Países Bajos", "Delantero"),   // 2
                new Jugador(null, "Zinedine Zidane", "Francia", "Centrocampista"), // 3
                new Jugador(null, "Ronaldo Nazário", "Brasil", "Delantero"),      // 4
                new Jugador(null, "Ronaldinho", "Brasil", "Mediapunta"),          // 5
                new Jugador(null, "Lionel Messi", "Argentina", "Delantero"),      // 6
                new Jugador(null, "Cristiano Ronaldo", "Portugal", "Delantero"),  // 7
                new Jugador(null, "Andrés Iniesta", "España", "Centrocampista"),  // 8
                new Jugador(null, "Paolo Maldini", "Italia", "Defensa"),          // 9
                new Jugador(null, "Eric Cantona", "Francia", "Delantero"),        // 10
                new Jugador(null, "Thierry Henry", "Francia", "Delantero")));     // 11

        List<Long> categoria = new CategoriaDAO().insertarLote(con, List.of(
                new Categoria(null, "Retro", "Camisetas históricas de temporadas pasadas"),                    // 0
                new Categoria(null, "Selecciones", "Camisetas de selecciones nacionales"),                      // 1
                new Categoria(null, "Clubes", "Camisetas de clubes"),                                           // 2
                new Categoria(null, "Leyendas", "Camisetas con el nombre y el dorsal de un jugador histórico"), // 3
                new Categoria(null, "Edición limitada", "Series cortas y ediciones especiales")));              // 4

        // ---- Camisetas (1 lote). Datos: equipo, era, jugador (o -1), dorsal, temporada, equipación, edición, precio, activa, controlStock
        record Def(String sku, String nombre, int eq, int er, int ju, Integer dorsal, String temporada,
                   Camiseta.Equipacion equipacion, Camiseta.Edicion edicion, String precio, boolean activa, boolean controlStock,
                   int stock, int umbral, int... categorias) {
        }
        List<Def> defs = List.of(
                new Def("CAM-BRA-70-PEL", "Brasil 1970 Local · Pelé", 4, 0, 0, 10, "1970", Equipacion.LOCAL, Edicion.RETRO, "89.90", true, true, 6, 3, 0, 1, 3),
                new Def("CAM-ARG-86-MAR", "Argentina 1986 Local · Maradona", 5, 1, 1, 10, "1986", Equipacion.LOCAL, Edicion.RETRO, "94.90", true, true, 8, 3, 0, 1, 3),
                new Def("CAM-AJX-73-CRU", "Ajax 1973 Local · Cruyff", 9, 0, 2, 14, "1972/73", Equipacion.LOCAL, Edicion.RETRO, "84.90", true, true, 4, 2, 0, 2, 3),
                new Def("CAM-MIL-94-MAL", "AC Milan 1994 Local · Maldini", 3, 2, 9, 3, "1993/94", Equipacion.LOCAL, Edicion.RETRO, "79.90", true, true, 5, 2, 0, 2, 3),
                new Def("CAM-MUN-96-CAN", "Manchester United 1996 Visitante · Cantona", 2, 2, 10, 7, "1995/96", Equipacion.VISITANTE, Edicion.RETRO, "89.90", true, true, 3, 3, 0, 2, 3),
                new Def("CAM-BRA-02-RON", "Brasil 2002 Local · Ronaldo", 4, 3, 4, 9, "2002", Equipacion.LOCAL, Edicion.RETRO, "92.90", true, true, 10, 4, 0, 1, 3),
                new Def("CAM-RMA-02-ZID", "Real Madrid 2002 Local · Zidane", 0, 3, 3, 5, "2001/02", Equipacion.LOCAL, Edicion.RETRO, "99.90", true, true, 7, 3, 0, 2, 3),
                new Def("CAM-BAR-09-HEN", "FC Barcelona 2009 Local · Henry", 1, 3, 11, 14, "2008/09", Equipacion.LOCAL, Edicion.RETRO, "84.90", true, true, 5, 2, 0, 2),
                new Def("CAM-BAR-06-RDN", "FC Barcelona 2006 Local · Ronaldinho", 1, 3, 5, 10, "2005/06", Equipacion.LOCAL, Edicion.RETRO, "89.90", true, true, 9, 3, 0, 2, 3),
                new Def("CAM-BAR-15-MES", "FC Barcelona 2015 Local · Messi", 1, 4, 6, 10, "2014/15", Equipacion.LOCAL, Edicion.REPLICA, "79.90", true, true, 12, 5, 2, 3),
                new Def("CAM-RMA-17-CRI", "Real Madrid 2017 Local · Cristiano", 0, 4, 7, 7, "2016/17", Equipacion.LOCAL, Edicion.REPLICA, "79.90", true, true, 12, 5, 2, 3),
                new Def("CAM-ESP-10-INI", "España 2010 Local · Iniesta", 10, 4, 8, 6, "2010", Equipacion.LOCAL, Edicion.JUGADOR, "109.90", true, true, 6, 2, 1, 3, 4),
                new Def("CAM-ARG-22-MES", "Argentina 2022 Local · Messi (coleccionista)", 5, 4, 6, 10, "2022", Equipacion.LOCAL, Edicion.COLECCIONISTA, "149.90", true, true, 3, 2, 1, 3, 4),
                new Def("CAM-JUV-98-ZID", "Juventus 1998 Local · Zidane", 6, 2, 3, 21, "1997/98", Equipacion.LOCAL, Edicion.RETRO, "84.90", true, true, 4, 2, 0, 2, 3),
                new Def("CAM-LIV-05-LOC", "Liverpool 2005 Local", 7, 3, -1, null, "2004/05", Equipacion.LOCAL, Edicion.REPLICA, "74.90", true, true, 8, 3, 0, 2),
                new Def("CAM-ATM-25-POR", "Atlético de Madrid 2025 Portero (descatalogada)", 11, 4, -1, null, "2024/25", Equipacion.PORTERO, Edicion.REPLICA, "64.90", false, true, 2, 2, 2),
                new Def("CAM-BAY-01-LOC", "Bayern Múnich 2001 Local (bajo pedido)", 8, 3, -1, null, "2000/01", Equipacion.LOCAL, Edicion.RETRO, "79.90", true, false, 0, 0, 0, 2),
                new Def("CAM-MUN-99-LOC", "Manchester United 1999 Local", 2, 2, -1, null, "1998/99", Equipacion.LOCAL, Edicion.RETRO, "89.90", true, true, 6, 2, 0, 2));

        List<Camiseta> camisetas = new ArrayList<>();
        for (Def d : defs) {
            camisetas.add(new Camiseta(null, d.sku(), d.nombre(), equipo.get(d.eq()), era.get(d.er()),
                    d.ju() < 0 ? null : jugador.get(d.ju()), d.dorsal(), d.temporada(), d.equipacion(), d.edicion(),
                    new BigDecimal(d.precio()), d.activa(), d.controlStock()));
        }
        List<Long> camiseta = new CamisetaDAO().insertarLote(con, camisetas);

        // ---- Inventario (1 lote), clasificación N:M (1 lote) y movimientos de reposición inicial (1 lote)
        List<Inventario> inventarios = new ArrayList<>();
        List<long[]> clasificaciones = new ArrayList<>();
        List<MovimientoStock> movimientos = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < defs.size(); i++) {
            Def d = defs.get(i);
            long id = camiseta.get(i);
            inventarios.add(new Inventario(id, d.stock(), d.umbral()));
            for (int c : d.categorias()) {
                clasificaciones.add(new long[]{id, categoria.get(c)});
            }
            if (d.controlStock() && d.stock() > 0) {
                movimientos.add(new MovimientoStock(null, id, MovimientoStock.Tipo.ENTRADA_REPOSICION, d.stock(),
                        ahora, null, "Stock inicial"));
            }
        }
        new InventarioDAO().insertarLote(con, inventarios);
        new CategoriaDAO().asignarLote(con, clasificaciones);
        new MovimientoStockDAO().insertarLote(con, movimientos);

        // ---- Clientes (1 lote) y direcciones (1 lote, 1:N)
        List<Long> cliente = new ClienteDAO().insertarLote(con, List.of(
                new Cliente(null, "Laura Martín", "laura.martin@example.com", LocalDate.of(2025, 9, 14)),   // 0
                new Cliente(null, "Carlos Gómez", "carlos.gomez@example.com", LocalDate.of(2025, 10, 2)),   // 1
                new Cliente(null, "Marta Ruiz", "marta.ruiz@example.com", LocalDate.of(2025, 11, 20)),      // 2
                new Cliente(null, "Javier Torres", "javier.torres@example.com", LocalDate.of(2026, 1, 8)),  // 3
                new Cliente(null, "Elena Navarro", "elena.navarro@example.com", LocalDate.of(2026, 2, 17)), // 4
                new Cliente(null, "Pablo Sanz", "pablo.sanz@example.com", LocalDate.of(2026, 3, 5))));     // 5

        record Dir(int cliente, String calle, String ciudad, String cp, String pais, boolean principal) {
        }
        List<Dir> dirs = List.of(
                new Dir(0, "Calle Alcalá 120", "Madrid", "28009", "España", true),
                new Dir(0, "Av. de la Paz 8", "Toledo", "45002", "España", false),
                new Dir(1, "Gran Vía 45", "Bilbao", "48011", "España", true),
                new Dir(2, "Carrer de Aragó 230", "Barcelona", "08007", "España", true),
                new Dir(2, "Rúa do Franco 12", "Santiago de Compostela", "15705", "España", false),
                new Dir(3, "Calle Sierpes 31", "Sevilla", "41004", "España", true),
                new Dir(4, "Calle Colón 17", "Valencia", "46004", "España", true),
                new Dir(4, "Plaza Mayor 3", "Salamanca", "37002", "España", false),
                new Dir(5, "Calle Mayor 66", "Zaragoza", "50001", "España", true));
        List<Direccion> direcciones = new ArrayList<>();
        for (Dir d : dirs) {
            direcciones.add(new Direccion(null, cliente.get(d.cliente()), d.calle(), d.ciudad(), d.cp(), d.pais(), d.principal()));
        }
        new DireccionDAO().insertarLote(con, direcciones);

        return new ResumenCarga(equipo.size(), era.size(), jugador.size(), categoria.size(), camiseta.size(),
                clasificaciones.size(), cliente.size(), direcciones.size(), movimientos.size());
    }
}
