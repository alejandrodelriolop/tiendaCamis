package es.camisetas.dto;

/** Datos técnicos de una violación de integridad real, tal y como los proporciona el driver. */
public record ErrorIntegridadDTO(String situacion, String sqlState, int errorCode, String mensajeDriver) {
}
