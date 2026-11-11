package es.camisetas.dto;

import java.util.List;

public record SolicitudPedido(long clienteId, long direccionId, List<ItemPedido> items) {
}
