package es.camisetas.model;

import java.time.LocalDate;

public record Cliente(Long id, String nombre, String email, LocalDate fechaAlta) {
}
