package com.taemoi.project.config;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.TipoGrado;

@Component
public class ExamMaterialBlockConfig {

	private final Map<Deporte, Map<TipoGrado, String>> bloquesPorDeporte;

	public ExamMaterialBlockConfig() {
		this.bloquesPorDeporte = crearBloques();
	}

	public String obtenerBloque(Deporte deporte, TipoGrado grado) {
		if (deporte == null || grado == null) {
			return null;
		}
		Map<TipoGrado, String> bloques = bloquesPorDeporte.get(deporte);
		if (bloques == null) {
			return null;
		}
		return bloques.get(grado);
	}

	private Map<Deporte, Map<TipoGrado, String>> crearBloques() {
		Map<Deporte, Map<TipoGrado, String>> porDeporte = new EnumMap<>(Deporte.class);

		Map<TipoGrado, String> bloquesGenerales = new EnumMap<>(TipoGrado.class);
		bloquesGenerales.put(TipoGrado.BLANCO, "b01_inicio_a_amarillo");
		bloquesGenerales.put(TipoGrado.BLANCO_AMARILLO, "b01_inicio_a_amarillo");
		bloquesGenerales.put(TipoGrado.AMARILLO, "b02_amarillo_a_naranja");
		bloquesGenerales.put(TipoGrado.AMARILLO_NARANJA, "b02_amarillo_a_naranja");
		bloquesGenerales.put(TipoGrado.NARANJA, "b03_naranja_a_verde");
		bloquesGenerales.put(TipoGrado.NARANJA_VERDE, "b03_naranja_a_verde");
		bloquesGenerales.put(TipoGrado.VERDE, "b04_verde_a_azul");
		bloquesGenerales.put(TipoGrado.VERDE_AZUL, "b04_verde_a_azul");
		bloquesGenerales.put(TipoGrado.AZUL, "b05_azul_a_rojo");
		bloquesGenerales.put(TipoGrado.AZUL_ROJO, "b05_azul_a_rojo");
		bloquesGenerales.put(TipoGrado.ROJO, "b06_rojo_a_negro_1");
		// PUM comparte materiales con DAN equivalente.
		bloquesGenerales.put(TipoGrado.ROJO_NEGRO_1_PUM, "b07_negro_1_a_negro_2");
		bloquesGenerales.put(TipoGrado.ROJO_NEGRO_2_PUM, "b08_negro_2_a_negro_3");
		bloquesGenerales.put(TipoGrado.ROJO_NEGRO_3_PUM, "b09_negro_3_a_negro_4");
		bloquesGenerales.put(TipoGrado.NEGRO_1_DAN, "b07_negro_1_a_negro_2");
		bloquesGenerales.put(TipoGrado.NEGRO_2_DAN, "b08_negro_2_a_negro_3");
		bloquesGenerales.put(TipoGrado.NEGRO_3_DAN, "b09_negro_3_a_negro_4");
		bloquesGenerales.put(TipoGrado.NEGRO_4_DAN, "b10_negro_4_a_negro_5");
		bloquesGenerales.put(TipoGrado.NEGRO_5_DAN, "b11_negro_5_a_maximo");

		porDeporte.put(Deporte.TAEKWONDO, new EnumMap<>(bloquesGenerales));
		porDeporte.put(Deporte.KICKBOXING, new EnumMap<>(bloquesGenerales));
		porDeporte.put(Deporte.PILATES, new EnumMap<>(TipoGrado.class));
		porDeporte.put(Deporte.DEFENSA_PERSONAL_FEMENINA, new EnumMap<>(TipoGrado.class));

		return porDeporte;
	}
}
