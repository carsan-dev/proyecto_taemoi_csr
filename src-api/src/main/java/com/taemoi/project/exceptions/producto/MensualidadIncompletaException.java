package com.taemoi.project.exceptions.producto;

import java.util.Collections;
import java.util.List;

public class MensualidadIncompletaException extends RuntimeException {
	private final List<String> mensualidadesExistentes;
	private final List<String> mensualidadesFaltantes;
	private final List<String> tarifasCompetidorExistentes;
	private final List<String> tarifasCompetidorFaltantes;

	public MensualidadIncompletaException(
			String mensaje,
			List<String> mensualidadesExistentes,
			List<String> mensualidadesFaltantes,
			List<String> tarifasCompetidorExistentes,
			List<String> tarifasCompetidorFaltantes) {
		super(mensaje);
		this.mensualidadesExistentes = mensualidadesExistentes == null ? Collections.emptyList() : mensualidadesExistentes;
		this.mensualidadesFaltantes = mensualidadesFaltantes == null ? Collections.emptyList() : mensualidadesFaltantes;
		this.tarifasCompetidorExistentes = tarifasCompetidorExistentes == null ? Collections.emptyList() : tarifasCompetidorExistentes;
		this.tarifasCompetidorFaltantes = tarifasCompetidorFaltantes == null ? Collections.emptyList() : tarifasCompetidorFaltantes;
	}

	public List<String> getMensualidadesExistentes() {
		return mensualidadesExistentes;
	}

	public List<String> getMensualidadesFaltantes() {
		return mensualidadesFaltantes;
	}

	public List<String> getTarifasCompetidorExistentes() {
		return tarifasCompetidorExistentes;
	}

	public List<String> getTarifasCompetidorFaltantes() {
		return tarifasCompetidorFaltantes;
	}
}
