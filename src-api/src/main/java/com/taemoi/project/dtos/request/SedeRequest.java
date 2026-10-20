package com.taemoi.project.dtos.request;

/**
 * {@code POST /api/sedes} y {@code PUT /api/sedes/{id}} (pieza E8-2, D8-2): los datos editables de una sede. Solo el
 * nombre es obligatorio; {@code aforoTurno} {@code null} = el límite de turno de la escuela. {@code PUT} es un
 * reemplazo: lo que no llega queda vacío. La activación va por {@code PUT /api/sedes/{id}/activa}.
 */
public record SedeRequest(String nombre, String direccion, String localidad, String codigoPostal, String telefono,
		String email, String codigoClubFederacion, Integer aforoTurno) {
}
