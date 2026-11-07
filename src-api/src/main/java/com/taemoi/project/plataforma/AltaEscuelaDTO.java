package com.taemoi.project.plataforma;

/**
 * Fase 4, pieza F4-2: respuesta 201 de {@code POST /api/plataforma/escuelas}.
 *
 * @param invitacionEnviadaA email al que sale la invitación del propietario tras confirmar el alta (nueva, o reenviada
 *                           con esta escuela si su cuenta aún no la había aceptado); {@code null} si el email ya tenía
 *                           cuenta aceptada (solo se le da el rol, sin correo)
 */
public record AltaEscuelaDTO(Long id, String nombre, String invitacionEnviadaA) {
}
