package es.camisetas.dto;

import es.camisetas.model.Cliente;
import es.camisetas.model.Direccion;

import java.util.List;

/** Cliente junto con sus direcciones y su historial de pedidos (datos relacionados recuperados juntos). */
public record ClienteCompletoDTO(Cliente cliente, List<Direccion> direcciones, List<PedidoResumenDTO> pedidos) {
}
