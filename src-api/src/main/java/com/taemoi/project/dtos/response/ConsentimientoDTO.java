package com.taemoi.project.dtos.response;

import java.time.Instant;
import java.time.LocalDate;

import com.taemoi.project.entities.Consentimiento;
import com.taemoi.project.entities.OrigenDato;
import com.taemoi.project.entities.RelacionFirmante;
import com.taemoi.project.entities.TipoConsentimiento;

public record ConsentimientoDTO(Long id, TipoConsentimiento tipo, boolean otorgado, LocalDate fecha,
		boolean fechaAproximada, String firmanteNombre, RelacionFirmante firmanteRelacion, OrigenDato origen,
		Long documentoId, Long preinscripcionId, Instant registradoEn) {

	public static ConsentimientoDTO de(Consentimiento c) {
		return new ConsentimientoDTO(c.getId(), c.getTipo(), c.isOtorgado(), c.getFecha(), c.isFechaAproximada(),
				c.getFirmanteNombre(), c.getFirmanteRelacion(), c.getOrigen(), c.getDocumentoId(),
				c.getPreinscripcionId(), c.getRegistradoEn());
	}
}
