package com.taemoi.project.dtos.request;

import java.time.LocalDate;
import java.util.Set;

import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoTarifa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record FinalizarPreinscripcionRequest(
		@NotNull AccionAlumno accionAlumno,
		Long alumnoId,
		Set<CampoActualizable> camposActualizar,
		Boolean discapacidadHistorica,
		@Valid DatosAltaDeporte datosDeporte) {
	public enum AccionAlumno { CREAR_NUEVO, VINCULAR_EXISTENTE }

	public enum CampoActualizable {
		NOMBRE,
		APELLIDOS,
		FECHA_NACIMIENTO,
		DIRECCION,
		TELEFONO,
		TELEFONO2,
		NIF,
		TIENE_DISCAPACIDAD,
		RESPONSABLE_LEGAL_NOMBRE,
		RESPONSABLE_LEGAL_NIF,
		EMAIL
	}

	public record DatosAltaDeporte(
			TipoTarifa tipoTarifa,
			@PositiveOrZero Double cuantiaTarifa,
			RolFamiliar rolFamiliar,
			@Size(max = 50) String grupoFamiliar,
			TipoGrado grado,
			LocalDate fechaGrado) {
	}

	public Set<CampoActualizable> camposActualizarSeguros() {
		return camposActualizar == null ? Set.of() : Set.copyOf(camposActualizar);
	}

}
