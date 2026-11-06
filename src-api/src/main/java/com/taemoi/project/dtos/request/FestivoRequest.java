package com.taemoi.project.dtos.request;

import java.util.List;

/**
 * Cuerpo de {@code POST /api/festivos}, {@code PUT /api/festivos/{id}} y {@code POST /api/festivos/impacto} (decisiones
 * 455-463). {@code tipo}: {@code DIA}, {@code RANGO} o {@code ANUAL}. Fechas {@code "yyyy-MM-dd"} (texto: el servicio da
 * el 400 con el mensaje en español). DIA usa {@code fechaDesde} ({@code fechaHasta}, si llega, igual); RANGO, las dos;
 * ANUAL, {@code mes} y {@code dia}. Lo que no corresponde al tipo se ignora. {@code excluirFestivoId}, solo en el
 * impacto: el festivo que se está editando, que no cuenta como «ya festivo».
 */
public record FestivoRequest(String tipo, String fechaDesde, String fechaHasta, Integer mes, Integer dia, String nombre,
		List<Long> sedeIds, Long excluirFestivoId) {
}
