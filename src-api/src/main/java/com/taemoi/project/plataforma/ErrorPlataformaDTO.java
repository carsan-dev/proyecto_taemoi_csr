package com.taemoi.project.plataforma;

import java.util.Date;

/**
 * Error técnico de la API visto por el administrador de plataforma: SOLO estos campos. Sin datos personales
 * (email, nombre, IP, agente) ni contenido de la petición (cuerpo, parámetros).
 *
 * @param usuarioId {@code null} en peticiones sin sesión.
 */
public record ErrorPlataformaDTO(Long id, Date fechaEvento, Long organizacionId, String metodoHttp, String endpoint,
		String modulo, Integer estadoHttp, Long usuarioId, String resumen) {
}
