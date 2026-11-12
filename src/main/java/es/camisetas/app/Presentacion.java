package es.camisetas.app;

import es.camisetas.dto.CamisetaDetalleDTO;
import es.camisetas.dto.PedidoDetalleDTO;
import es.camisetas.dto.StockBajoDTO;
import es.camisetas.model.MovimientoStock;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.Locale;

/** Formato de salida por consola compartido por la simulación y el menú de la tienda. */
final class Presentacion {

    private static final Locale ES = Locale.forLanguageTag("es-ES");

    private final PrintStream out;

    Presentacion(PrintStream out) {
        this.out = out;
    }

    static String eur(BigDecimal v) {
        return String.format(ES, "%,.2f €", v);
    }

    void titulo(String texto) {
        out.println();
        out.println("=".repeat(100));
        out.println(texto);
        out.println("=".repeat(100));
    }

    void camiseta(CamisetaDetalleDTO c) {
        out.printf("  #%-3d %-16s %-48s %-11s %-9s %8s  stock=%d%s%n", c.id(), c.sku(), c.nombre(), c.era(), c.edicion(),
                eur(c.precio()), c.stock(), c.activa() ? "" : "  [descatalogada]");
    }

    void movimiento(MovimientoStock m) {
        out.printf("  #%-3d %-20s cantidad=%d %s%s%n", m.id(), m.tipo(), m.cantidad(), m.fecha(),
                m.pedidoId() == null ? "" : " pedido=" + m.pedidoId());
    }

    void pedido(PedidoDetalleDTO p) {
        out.printf("%nPedido %d · %s <%s> · envío a %s · %s · %s%n", p.pedidoId(), p.cliente(), p.email(), p.direccionEnvio(), p.fecha(), p.estado());
        p.lineas().forEach(l -> out.printf("    %-16s x%d a %s = %s%n", l.sku(), l.cantidad(), eur(l.precioUnitario()), eur(l.importe())));
        out.println("    TOTAL " + eur(p.total()));
    }

    void stockBajo(StockBajoDTO b) {
        out.printf("  %-16s stock=%d umbral=%d déficit=%d%n", b.sku(), b.stock(), b.umbral(), b.deficit());
    }
}