package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.AuditoriaAccion;

/**
 * Pieza E8-2 (D8-16, fase3-e8-miembros.md §7): el registro del módulo {@code sedes} solo lleva ids, enumeraciones y
 * sí/no; nunca el nombre, la dirección, el teléfono ni el email de la sede.
 */
class RegistroAccionesSedesTest {

	private final AuditoriaService auditoria = mock(AuditoriaService.class);
	private final RegistroAccionesSedes registro = new RegistroAccionesSedes(auditoria);

	@Test
	void resumenSoloConIds() {
		registro.registrar(RegistroAccionesSedes.Accion.SEDE_CREADA, "/api/sedes", Map.of("sede", 5L));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.CREATE, "sedes", "/api/sedes", 5L,
				"SEDES SEDE_CREADA sede=#5");

		registro.registrar(RegistroAccionesSedes.Accion.SEDE_EDITADA, "/api/sedes/5", Map.of("sede", 5L));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "sedes", "/api/sedes/5", 5L,
				"SEDES SEDE_EDITADA sede=#5");

		registro.registrar(RegistroAccionesSedes.Accion.SEDE_DESACTIVADA, "/api/sedes/5/activa", Map.of("sede", 5L));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "sedes", "/api/sedes/5/activa", 5L,
				"SEDES SEDE_DESACTIVADA sede=#5");

		registro.registrar(RegistroAccionesSedes.Accion.SEDE_ACTIVADA, "/api/sedes/6/activa", Map.of("sede", 6L));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "sedes", "/api/sedes/6/activa", 6L,
				"SEDES SEDE_ACTIVADA sede=#6");
		assertEquals("sedes", RegistroAccionesSedes.MODULO);
	}

	@Test
	void cambioDeSedeDeUnAlumno_soloIds_ySinSedeAnterior() {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("alumno", 12L);
		valores.put("sedeAnterior", null);
		valores.put("sedeNueva", 3L);
		valores.put("cargosMovidos", 2L);
		registro.registrar(RegistroAccionesSedes.Accion.ALUMNO_SEDE_CAMBIADA, "/api/alumnos/12/sede", valores);
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "sedes", "/api/alumnos/12/sede", 12L,
				"SEDES ALUMNO_SEDE_CAMBIADA alumno=#12 sedeAnterior=NINGUNO sedeNueva=#3 cargosMovidos=#2");
	}

	@Test
	void sinCambiosComoSiNo() {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("sede", 7L);
		valores.put("sinCambios", true);
		registro.registrar(RegistroAccionesSedes.Accion.SEDE_EDITADA, "/api/sedes/7", valores);
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "sedes", "/api/sedes/7", 7L,
				"SEDES SEDE_EDITADA sede=#7 sinCambios=SI");
		assertEquals("NO", RegistroAccionesSedes.formatear("x", false));
		assertEquals("NINGUNO", RegistroAccionesSedes.formatear("x", null));
	}

	@Test
	void textoLibre_IllegalStateException() {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("sede", 8L);
		valores.put("nombre", "Sede Inventada");
		assertThrows(IllegalStateException.class,
				() -> registro.registrar(RegistroAccionesSedes.Accion.SEDE_CREADA, "/api/sedes", valores));
		assertThrows(IllegalStateException.class, () -> RegistroAccionesSedes.formatear("aforo", 30));
		verify(auditoria, never()).registrarAccesoSensible(any(), anyString(), anyString(), any(), anyString());
	}
}
