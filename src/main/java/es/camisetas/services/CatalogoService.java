package es.camisetas.services;

import es.camisetas.dao.CamisetaDAO;
import es.camisetas.dao.CategoriaDAO;
import es.camisetas.dao.EquipoDAO;
import es.camisetas.dao.EraDAO;
import es.camisetas.dao.InventarioDAO;
import es.camisetas.dao.JugadorDAO;
import es.camisetas.dao.MovimientoStockDAO;
import es.camisetas.connections.Transacciones;
import es.camisetas.dto.CamisetaDetalleDTO;
import es.camisetas.dto.CatalogoFiltro;
import es.camisetas.dto.Pagina;
import es.camisetas.exceptions.NegocioException;
import es.camisetas.model.Camiseta;
import es.camisetas.model.Categoria;
import es.camisetas.model.Inventario;
import es.camisetas.model.MovimientoStock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** Catálogo de camisetas: alta, cambio de precio, clasificación y búsquedas. */
public class CatalogoService {

    private static final Logger log = LogManager.getLogger(CatalogoService.class);
    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final Transacciones tx;
    private final CamisetaDAO camisetaDao = new CamisetaDAO();
    private final InventarioDAO inventarioDao = new InventarioDAO();
    private final MovimientoStockDAO movimientoDao = new MovimientoStockDAO();
    private final CategoriaDAO categoriaDao = new CategoriaDAO();
    private final EquipoDAO equipoDao = new EquipoDAO();
    private final EraDAO eraDao = new EraDAO();
    private final JugadorDAO jugadorDao = new JugadorDAO();

    public CatalogoService(Transacciones tx) {
        this.tx = tx;
    }

    /** Catálogo filtrado; la paginación se resuelve en SQL (LIMIT/OFFSET con orden definido). {@code pagina} empieza en 1. */
    public Pagina<CamisetaDetalleDTO> consultar(CatalogoFiltro filtro, int pagina, int tamano) {
        if (pagina < 1) {
            throw NegocioException.validacion("La página debe ser 1 o mayor");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO_PAGINA) {
            throw NegocioException.validacion("El tamaño de página debe estar entre 1 y " + TAMANO_MAXIMO_PAGINA);
        }
        return tx.consultar(con -> {
            long total = camisetaDao.contarCatalogo(con, filtro);
            List<CamisetaDetalleDTO> elementos = camisetaDao.buscarCatalogo(con, filtro, pagina, tamano);
            return new Pagina<>(elementos, pagina, tamano, total);
        });
    }

    public Camiseta buscarPorSku(String sku) {
        return tx.consultar(con -> camisetaDao.buscarPorSku(con, sku)
                .orElseThrow(() -> NegocioException.noEncontrado("Camiseta con SKU", sku)));
    }

    public long idDeEquipo(String nombre) {
        return tx.consultar(con -> equipoDao.buscarPorNombre(con, nombre)
                .orElseThrow(() -> NegocioException.noEncontrado("Equipo", nombre)).id());
    }

    public long idDeEra(String nombre) {
        return tx.consultar(con -> eraDao.buscarPorNombre(con, nombre)
                .orElseThrow(() -> NegocioException.noEncontrado("Era", nombre)).id());
    }

    public Optional<CamisetaDetalleDTO> detalle(long camisetaId) {
        return tx.consultar(con -> camisetaDao.buscarDetalle(con, camisetaId));
    }

    /** Consulta propia de la temática: la carrera de un jugador a través de eras y equipos. */
    public List<CamisetaDetalleDTO> camisetasDeJugador(String nombreJugador) {
        if (nombreJugador == null || nombreJugador.isBlank()) {
            throw NegocioException.validacion("Hay que indicar el nombre del jugador");
        }
        return tx.consultar(con -> camisetaDao.buscarPorJugador(con, nombreJugador.trim()));
    }

    public List<Categoria> categoriasDe(long camisetaId) {
        return tx.consultar(con -> categoriaDao.listarPorCamiseta(con, camisetaId));
    }

    public List<Categoria> listarCategorias() {
        return tx.consultar(categoriaDao::listar);
    }

    /**
     * Da de alta una camiseta con su inventario inicial, su movimiento de entrada y sus categorías.
     * Todo se confirma junto o no se guarda nada.
     */
    public long registrarCamiseta(Camiseta camiseta, int stockInicial, int umbralReposicion, List<Long> categoriaIds) {
        validar(camiseta, stockInicial, umbralReposicion);
        return tx.ejecutar("alta de camiseta " + camiseta.sku(), con -> {
            equipoDao.buscarPorId(con, camiseta.equipoId())
                    .orElseThrow(() -> NegocioException.noEncontrado("Equipo", camiseta.equipoId()));
            eraDao.buscarPorId(con, camiseta.eraId())
                    .orElseThrow(() -> NegocioException.noEncontrado("Era", camiseta.eraId()));
            if (camiseta.jugadorId() != null) {
                jugadorDao.buscarPorId(con, camiseta.jugadorId())
                        .orElseThrow(() -> NegocioException.noEncontrado("Jugador", camiseta.jugadorId()));
            }
            if (camisetaDao.buscarPorSku(con, camiseta.sku()).isPresent()) {
                throw NegocioException.validacion("Ya existe una camiseta con el SKU " + camiseta.sku());
            }

            long id = camisetaDao.insertar(con, camiseta);
            inventarioDao.insertar(con, new Inventario(id, stockInicial, umbralReposicion));
            if (camiseta.controlStock() && stockInicial > 0) {
                movimientoDao.insertar(con, new MovimientoStock(null, id, MovimientoStock.Tipo.ENTRADA_REPOSICION,
                        stockInicial, ahora(), null, "Alta de producto"));
            }
            for (Long categoriaId : categoriaIds) {
                categoriaDao.asignar(con, id, categoriaId);
            }
            log.info("Camiseta registrada id={} sku={} stockInicial={} categorias={}", id, camiseta.sku(), stockInicial, categoriaIds.size());
            return id;
        });
    }

    public void actualizarPrecio(long camisetaId, BigDecimal nuevoPrecio) {
        if (nuevoPrecio == null || nuevoPrecio.signum() <= 0) {
            throw NegocioException.validacion("El precio debe ser mayor que cero");
        }
        tx.ejecutar("cambio de precio de la camiseta " + camisetaId, con -> {
            if (camisetaDao.actualizarPrecio(con, camisetaId, nuevoPrecio) == 0) {
                throw NegocioException.noEncontrado("Camiseta", camisetaId);
            }
            log.info("Precio actualizado camiseta={} nuevoPrecio={}", camisetaId, nuevoPrecio);
            return null;
        });
    }

    public void cambiarDisponibilidad(long camisetaId, boolean activa) {
        tx.ejecutar("cambio de disponibilidad de la camiseta " + camisetaId, con -> {
            if (camisetaDao.actualizarActiva(con, camisetaId, activa) == 0) {
                throw NegocioException.noEncontrado("Camiseta", camisetaId);
            }
            log.info("Disponibilidad actualizada camiseta={} activa={}", camisetaId, activa);
            return null;
        });
    }

    public void clasificar(long camisetaId, long categoriaId) {
        tx.ejecutar("clasificar camiseta " + camisetaId, con -> {
            categoriaDao.asignar(con, camisetaId, categoriaId);
            log.info("Camiseta {} añadida a la categoría {}", camisetaId, categoriaId);
            return null;
        });
    }

    public void desclasificar(long camisetaId, long categoriaId) {
        tx.ejecutar("quitar categoría de la camiseta " + camisetaId, con -> {
            if (categoriaDao.desasignar(con, camisetaId, categoriaId) == 0) {
                throw NegocioException.noEncontrado("Clasificación de la camiseta " + camisetaId + " en la categoría", categoriaId);
            }
            log.info("Camiseta {} retirada de la categoría {}", camisetaId, categoriaId);
            return null;
        });
    }

    private void validar(Camiseta c, int stockInicial, int umbral) {
        if (c.sku() == null || c.sku().isBlank() || c.nombre() == null || c.nombre().isBlank()) {
            throw NegocioException.validacion("El SKU y el nombre de la camiseta son obligatorios");
        }
        if (c.precio() == null || c.precio().signum() <= 0) {
            throw NegocioException.validacion("El precio debe ser mayor que cero");
        }
        if (stockInicial < 0 || umbral < 0) {
            throw NegocioException.validacion("El stock inicial y el umbral no pueden ser negativos");
        }
        if (c.dorsal() != null && c.jugadorId() == null) {
            throw NegocioException.validacion("Una camiseta con dorsal debe indicar el jugador");
        }
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
