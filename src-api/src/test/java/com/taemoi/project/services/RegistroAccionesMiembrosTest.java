package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.RolMembresia;

/**
 * Pieza E8-3 (D8-16, fase3-e8-miembros.md §7): el registro del módulo {@code miembros} solo lleva ids (también listas
 * de ids), enumeraciones y sí/no; nunca el email, el nombre ni el cargo.
 */
class RegistroAccionesMiembrosTest {

	private final AuditoriaService auditoria = mock(AuditoriaService.class);
	private final RegistroAccionesMiembros registro = new RegistroAccionesMiembros(auditoria);

	@Test
	void resumenSoloConIds() {
		registro.registrar(RegistroAccionesMiembros.Accion.ROL_DADO, "/api/miembros",
				valores("usuario", 21L, "rol", RolMembresia.RESPONSABLE_SEDE));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.CREATE, "miembros", "/api/miembros", 21L,
				"MIEMBROS ROL_DADO usuario=#21 rol=RESPONSABLE_SEDE");

		registro.registrar(RegistroAccionesMiembros.Accion.ROL_REACTIVADO, "/api/miembros/21/roles/PROFESOR",
				valores("usuario", 21L, "rol", RolMembresia.PROFESOR));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "miembros", "/api/miembros/21/roles/PROFESOR",
				21L, "MIEMBROS ROL_REACTIVADO usuario=#21 rol=PROFESOR");

		registro.registrar(RegistroAccionesMiembros.Accion.ROL_QUITADO, "/api/miembros/21/roles/PROFESOR",
				valores("usuario", 21L, "rol", RolMembresia.PROFESOR));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "miembros", "/api/miembros/21/roles/PROFESOR",
				21L, "MIEMBROS ROL_QUITADO usuario=#21 rol=PROFESOR");

		registro.registrar(RegistroAccionesMiembros.Accion.CARGO_CAMBIADO, "/api/miembros/21/roles/PROFESOR",
				valores("usuario", 21L, "rol", RolMembresia.PROFESOR));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "miembros", "/api/miembros/21/roles/PROFESOR",
				21L, "MIEMBROS CARGO_CAMBIADO usuario=#21 rol=PROFESOR");
		assertEquals("miembros", RegistroAccionesMiembros.MODULO);
	}

	@Test
	void listaDeIds() {
		registro.registrar(RegistroAccionesMiembros.Accion.SEDES_CAMBIADAS, "/api/miembros/21/sedes",
				valores("usuario", 21L, "sedes", List.of(4L, 3L)));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "miembros", "/api/miembros/21/sedes", 21L,
				"MIEMBROS SEDES_CAMBIADAS usuario=#21 sedes=#3,#4");
		assertEquals("NINGUNO", RegistroAccionesMiembros.formatear("sedes", List.of()));
	}

	@Test
	void sinCambiosComoSiNo() {
		Map<String, Object> v = valores("usuario", 7L, "rol", RolMembresia.PROFESOR);
		v.put("sinCambios", true);
		registro.registrar(RegistroAccionesMiembros.Accion.ROL_DADO, "/api/miembros", v);
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.CREATE, "miembros", "/api/miembros", 7L,
				"MIEMBROS ROL_DADO usuario=#7 rol=PROFESOR sinCambios=SI");
		Map<String, Object> grupos = new LinkedHashMap<>();
		grupos.put("usuario", 9L);
		grupos.put("sinCambios", true);
		registro.registrar(RegistroAccionesMiembros.Accion.GRUPOS_CAMBIADOS, "/api/miembros/9/grupos", grupos);
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "miembros", "/api/miembros/9/grupos", 9L,
				"MIEMBROS GRUPOS_CAMBIADOS usuario=#9 sinCambios=SI");
		assertEquals("NO", RegistroAccionesMiembros.formatear("x", false));
		assertEquals("NINGUNO", RegistroAccionesMiembros.formatear("x", null));
	}

	/** Pieza E8-4: invitar y reenviar, solo con el id de la cuenta y el rol (nunca el email, el nombre ni el token). */
	@Test
	void invitacion_resumenSoloConIds() {
		registro.registrar(RegistroAccionesMiembros.Accion.MIEMBRO_INVITADO, "/api/miembros",
				valores("usuario", 21L, "rol", RolMembresia.PROFESOR));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.CREATE, "miembros", "/api/miembros", 21L,
				"MIEMBROS MIEMBRO_INVITADO usuario=#21 rol=PROFESOR");

		Map<String, Object> reenvio = new LinkedHashMap<>();
		reenvio.put("usuario", 21L);
		registro.registrar(RegistroAccionesMiembros.Accion.INVITACION_REENVIADA, "/api/miembros/21/invitacion",
				reenvio);
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.UPDATE, "miembros", "/api/miembros/21/invitacion",
				21L, "MIEMBROS INVITACION_REENVIADA usuario=#21");
	}

	@Test
	void invitacionCancelada_resumenConSiNoYSinDatosDeLaCuenta() {
		registro.registrar(RegistroAccionesMiembros.Accion.INVITACION_CANCELADA, "/api/miembros/21/invitacion",
				valores("usuario", 21L, "cuentaBorrada", Boolean.TRUE));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.DELETE, "miembros", "/api/miembros/21/invitacion",
				21L, "MIEMBROS INVITACION_CANCELADA usuario=#21 cuentaBorrada=SI");

		registro.registrar(RegistroAccionesMiembros.Accion.INVITACION_CANCELADA, "/api/miembros/22/invitacion",
				valores("usuario", 22L, "cuentaBorrada", Boolean.FALSE));
		verify(auditoria).registrarAccesoSensible(AuditoriaAccion.DELETE, "miembros", "/api/miembros/22/invitacion",
				22L, "MIEMBROS INVITACION_CANCELADA usuario=#22 cuentaBorrada=NO");
	}

	@Test
	void textoLibre_IllegalStateException() {
		Map<String, Object> conCargo = valores("usuario", 8L, "cargo", "Director técnico");
		assertThrows(IllegalStateException.class,
				() -> registro.registrar(RegistroAccionesMiembros.Accion.CARGO_CAMBIADO, "/api/miembros", conCargo));
		Map<String, Object> conEmail = valores("usuario", 8L, "email", "alguien@example.test");
		assertThrows(IllegalStateException.class,
				() -> registro.registrar(RegistroAccionesMiembros.Accion.ROL_DADO, "/api/miembros", conEmail));
		assertThrows(IllegalStateException.class, () -> RegistroAccionesMiembros.formatear("x", 30));
		assertThrows(IllegalStateException.class,
				() -> RegistroAccionesMiembros.formatear("sedes", List.of(3L, "Sede Inventada")));
		verify(auditoria, never()).registrarAccesoSensible(any(), anyString(), anyString(), any(), anyString());
	}

	private static Map<String, Object> valores(String k1, Object v1, String k2, Object v2) {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put(k1, v1);
		valores.put(k2, v2);
		return valores;
	}
}
