package es.camisetas.dto;

import es.camisetas.model.Pedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Pedido completo: cliente, dirección de envío, fecha, estado, líneas y total. */
public record PedidoDetalleDTO(
        long pedidoId,
        String cliente,
        String email,
        String direccionEnvio,
        LocalDateTime fecha,
        Pedido.Estado estado,
        BigDecimal total,
        List<LineaDetalleDTO> lineas) {
}
