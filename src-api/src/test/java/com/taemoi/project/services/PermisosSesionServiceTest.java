package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.taemoi.project.dtos.response.PermisosSesionDTO.Capacidad;
import com.taemoi.project.entities.Roles;

/** Reglas de capacidades de {@code GET /api/auth/permisos} (pieza E8-1, D8-14), sin Spring. */
class PermisosSesionServiceTest {

	@Test
	void capacidades_porRol() {
		assertEquals(List.of(Capacidad.MIEMBROS, Capacidad.DAR_GESTION, Capacidad.SEDES, Capacidad.ASIGNAR_PROFESORES),
				PermisosSesionService.capacidades(Set.of(Roles.ROLE_ADMIN), false));
		assertEquals(List.of(Capacidad.MIEMBROS, Capacidad.SEDES, Capacidad.ASIGNAR_PROFESORES),
				PermisosSesionService.capacidades(Set.of(Roles.ROLE_MANAGER), false), "el gestor no da gestión (D8)");
		assertEquals(List.of(), PermisosSesionService.capacidades(Set.of(Roles.ROLE_USER), false));
		assertEquals(List.of(), PermisosSesionService.capacidades(Set.of(Roles.ROLE_USER, Roles.ROLE_RESPONSABLE_SEDE),
				false));
		assertEquals(List.of(), PermisosSesionService.capacidades(Set.of(), false), "solo plataforma");
		// Con PROFESOR fuera de la sesión solo-profesor, «Mis grupos» solo con grupos (217); Mi caja siempre.
		assertEquals(List.of(Capacidad.MI_CAJA),
				PermisosSesionService.capacidades(Set.of(Roles.ROLE_USER, Roles.ROLE_PROFESOR), false));
		assertEquals(List.of(Capacidad.MIS_GRUPOS, Capacidad.MI_CAJA),
				PermisosSesionService.capacidades(Set.of(Roles.ROLE_USER, Roles.ROLE_PROFESOR), true));
		assertEquals(List.of(Capacidad.MIEMBROS, Capacidad.DAR_GESTION, Capacidad.SEDES, Capacidad.ASIGNAR_PROFESORES,
				Capacidad.MIS_GRUPOS, Capacidad.MI_CAJA),
				PermisosSesionService.capacidades(Set.of(Roles.ROLE_ADMIN, Roles.ROLE_PROFESOR), true));
		assertEquals(List.of(Capacidad.MIEMBROS, Capacidad.SEDES, Capacidad.ASIGNAR_PROFESORES, Capacidad.MI_CAJA),
				PermisosSesionService.capacidades(Set.of(Roles.ROLE_MANAGER, Roles.ROLE_PROFESOR), false));
	}

	@Test
	void capacidades_sesionSoloProfesorSiempreMisGrupos() {
		for (Set<Roles> autoridades : List.of(Set.of(Roles.ROLE_PROFESOR),
				Set.of(Roles.ROLE_PROFESOR, Roles.ROLE_RESPONSABLE_SEDE))) {
			for (boolean tieneGrupos : new boolean[] { false, true }) {
				assertEquals(List.of(Capacidad.MIS_GRUPOS, Capacidad.MI_CAJA),
						PermisosSesionService.capacidades(autoridades, tieneGrupos), autoridades + " " + tieneGrupos);
			}
		}
	}

	/** Pieza 8b-T (§4): «Nombre A.» con la inicial del primer apellido, para el botón de cuenta. */
	@Test
	void nombreVisible_nombreEInicialDelPrimerApellido() {
		assertEquals("Lucía O.", PermisosSesionService.nombreVisible("Lucía", "Ortega Ruiz"));
		assertEquals("María José Á.", PermisosSesionService.nombreVisible(" María José ", "  álvarez"));
		assertEquals("Ana María O.", PermisosSesionService.nombreVisible("Ana \t María", "ortega"));
		assertEquals("Lucía", PermisosSesionService.nombreVisible("Lucía", null));
		assertEquals("Lucía", PermisosSesionService.nombreVisible("Lucía", "  "));
		assertEquals(null, PermisosSesionService.nombreVisible(null, "Ortega"));
		assertEquals(null, PermisosSesionService.nombreVisible("  ", "Ortega"));
	}
}
