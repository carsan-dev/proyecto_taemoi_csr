package com.taemoi.project.dtos.response;

import java.math.BigDecimal;

/**
 * Tarifa por sede de la escuela activa (entrega 6-4, D6-30): {@code GET/POST /api/tarifas-sede}.
 *
 * @param vigenteDesde mes desde el que aplica, {@code YYYY-MM}
 */
public record TarifaSedeDTO(Long id, String codigoTarifa, Long sedeId, BigDecimal importe, BigDecimal importeAdulto,
		BigDecimal importeMenor, String vigenteDesde) {
}
