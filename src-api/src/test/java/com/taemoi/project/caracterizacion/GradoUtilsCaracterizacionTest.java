package com.taemoi.project.caracterizacion;

import static com.taemoi.project.legado.TipoGrado.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.taemoi.project.legado.Deporte;
import com.taemoi.project.legado.TipoGrado;
import com.taemoi.project.legado.GradoUtils;

/**
 * Caracterizacion de GradoUtils.getNombreGradoParaDeporte: nombre visible por deporte.
 */
class GradoUtilsCaracterizacionTest {

	/** {grado, nombre en TKD/PILATES/DPF/null, nombre en KICKBOXING}. */
	private static final Object[][] TABLA = {
			{ BLANCO, "Blanco", "Blanco" },
			{ BLANCO_AMARILLO, "Blanco-Amarillo", "Blanco-Amarillo" },
			{ AMARILLO, "Amarillo", "Amarillo" },
			{ AMARILLO_NARANJA, "Amarillo-Naranja", "Amarillo-Naranja" },
			{ NARANJA, "Naranja", "Naranja" },
			{ NARANJA_VERDE, "Naranja-Verde", "Naranja-Verde" },
			{ VERDE, "Verde", "Verde" },
			{ VERDE_AZUL, "Verde-Azul", "Verde-Azul" },
			{ AZUL, "Azul", "Azul" },
			{ AZUL_ROJO, "Azul-Rojo", "Azul-Rojo" },
			{ ROJO, "Rojo", "MARRÓN" },
			{ ROJO_NEGRO_1_PUM, "Rojo-Negro-1-Pum", "Rojo-Negro-1-Pum" },
			{ ROJO_NEGRO_2_PUM, "Rojo-Negro-2-Pum", "Rojo-Negro-2-Pum" },
			{ ROJO_NEGRO_3_PUM, "Rojo-Negro-3-Pum", "Rojo-Negro-3-Pum" },
			{ NEGRO_1_DAN, "Negro-1-Dan", "Negro-1-Dan" },
			{ NEGRO_2_DAN, "Negro-2-Dan", "Negro-2-Dan" },
			{ NEGRO_3_DAN, "Negro-3-Dan", "Negro-3-Dan" },
			{ NEGRO_4_DAN, "Negro-4-Dan", "Negro-4-Dan" },
			{ NEGRO_5_DAN, "Negro-5-Dan", "Negro-5-Dan" },
	};

	@Test
	void sobrecargaEnum_todosLosGradosYDeportes() {
		assertEquals(TipoGrado.values().length, TABLA.length);
		List<Deporte> noKickboxing = java.util.Arrays.asList(Deporte.TAEKWONDO, Deporte.PILATES,
				Deporte.DEFENSA_PERSONAL_FEMENINA, null);
		for (Object[] fila : TABLA) {
			TipoGrado g = (TipoGrado) fila[0];
			for (Deporte d : noKickboxing) {
				assertEquals(fila[1], GradoUtils.getNombreGradoParaDeporte(g, d), g + "/" + d);
			}
			assertEquals(fila[2], GradoUtils.getNombreGradoParaDeporte(g, Deporte.KICKBOXING), g + "/KB");
		}
	}

	@Test
	void sobrecargaEnum_gradoNulo() {
		assertEquals("N/A", GradoUtils.getNombreGradoParaDeporte((TipoGrado) null, Deporte.TAEKWONDO));
		assertEquals("N/A", GradoUtils.getNombreGradoParaDeporte((TipoGrado) null, Deporte.KICKBOXING));
	}

	@Test
	void sobrecargaString() {
		assertEquals("MARRÓN", GradoUtils.getNombreGradoParaDeporte("Rojo", Deporte.KICKBOXING));
		assertEquals("MARRÓN", GradoUtils.getNombreGradoParaDeporte("ROJO", Deporte.KICKBOXING));
		assertEquals("MARRÓN", GradoUtils.getNombreGradoParaDeporte("rojo", Deporte.KICKBOXING));
		assertEquals("Rojo", GradoUtils.getNombreGradoParaDeporte("Rojo", Deporte.TAEKWONDO));
		assertEquals("ROJO", GradoUtils.getNombreGradoParaDeporte("ROJO", null));
		// Solo coincide el literal exacto "rojo": compuestos y nombres de enum no se traducen
		assertEquals("Azul-Rojo", GradoUtils.getNombreGradoParaDeporte("Azul-Rojo", Deporte.KICKBOXING));
		assertEquals("ROJO_NEGRO_1_PUM", GradoUtils.getNombreGradoParaDeporte("ROJO_NEGRO_1_PUM", Deporte.KICKBOXING));
		assertEquals("N/A", GradoUtils.getNombreGradoParaDeporte("", Deporte.KICKBOXING));
		assertEquals("N/A", GradoUtils.getNombreGradoParaDeporte((String) null, Deporte.KICKBOXING));
	}
}
