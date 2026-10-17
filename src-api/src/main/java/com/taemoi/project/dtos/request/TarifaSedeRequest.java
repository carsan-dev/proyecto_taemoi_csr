package com.taemoi.project.dtos.request;

import java.math.BigDecimal;

/**
 * Alta de una tarifa por sede (entrega 6-4, D6-30): {@code POST /api/tarifas-sede}.
 *
 * @param codigoTarifa  código de la tarifa del catálogo de la escuela ({@code ADULTO}…), el mismo que expone
 *                      {@code /api/catalogo}
 * @param sedeId        sede de la escuela
 * @param importe       importe general (&gt;= 0, como el catálogo)
 * @param importeAdulto importe del progenitor; obligatorio si la tarifa base lo tiene
 * @param importeMenor  importe del hijo; obligatorio si la tarifa base lo tiene
 * @param vigenteDesde  mes desde el que aplica, {@code YYYY-MM} (D6-34); el en curso o posterior (D6-37)
 */
public record TarifaSedeRequest(String codigoTarifa, Long sedeId, BigDecimal importe, BigDecimal importeAdulto,
		BigDecimal importeMenor, String vigenteDesde) {
}
