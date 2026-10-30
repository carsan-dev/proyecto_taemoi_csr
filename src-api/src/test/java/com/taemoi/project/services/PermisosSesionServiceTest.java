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

	/** Ronda 2 de 8b-T: las partículas del apellido no dan la inicial. */
	@Test
	void nombreVisible_saltaLasParticulas() {
		assertEquals("Lucía F.", PermisosSesionService.nombreVisible("Lucía", "de la Fuente"));
		assertEquals("Lucía R.", PermisosSesionService.nombreVisible("Lucía", "del Río Ortega"));
		assertEquals("Lucía G.", PermisosSesionService.nombreVisible("Lucía", "García-Pérez"));
		assertEquals("Lucía G.", PermisosSesionService.nombreVisible("Lucía", "Van Gogh"));
		assertEquals("Lucía S.", PermisosSesionService.nombreVisible("Lucía", "DOS Santos"));
		assertEquals("Lucía Y.", PermisosSesionService.nombreVisible("Lucía", "Yáñez"), "«y» solo es partícula sola");
		assertEquals("Lucía D.", PermisosSesionService.nombreVisible("Lucía", "Delgado"), "«del» solo como palabra");
		assertEquals("Lucía", PermisosSesionService.nombreVisible("Lucía", "de la"), "todo partículas");
		assertEquals("Lucía", PermisosSesionService.nombreVisible("Lucía", ""));
	}

	/** Decisión 322: el nombre preferido, tal cual, en el botón de cuenta. */
	@Test
	void nombreVisible_conPreferido_eseTalCual() {
		assertEquals("Luci", PermisosSesionService.nombreVisible("Luci", "Lucía", "Ortega Ruiz"));
		assertEquals("Maestra Lucía de la Sierra",
				PermisosSesionService.nombreVisible("Maestra Lucía de la Sierra", "Lucía", "Ortega"));
		assertEquals("Lucía O.", PermisosSesionService.nombreVisible(null, "Lucía", "Ortega Ruiz"));
		assertEquals(null, PermisosSesionService.nombreVisible(null, "  ", "Ortega"));
		assertEquals("Luci", PermisosSesionService.nombreVisible("Luci", null, null));
	}

	/** Decisión 322: el saludo de Inicio; el preferido entero, sin recortarlo a la primera palabra. */
	@Test
	void nombreSaludo_preferidoEnteroOPrimeraPalabra() {
		assertEquals("Maestra Lucía", PermisosSesionService.nombreSaludo("Maestra Lucía", "María José"));
		assertEquals("María", PermisosSesionService.nombreSaludo(null, "María José"));
		assertEquals("María", PermisosSesionService.nombreSaludo(null, "  María   José "));
		assertEquals("Lucía", PermisosSesionService.nombreSaludo(null, "Lucía"));
		assertEquals(null, PermisosSesionService.nombreSaludo(null, null));
		assertEquals(null, PermisosSesionService.nombreSaludo(null, "   "));
		assertEquals("Luci", PermisosSesionService.nombreSaludo("Luci", null));
	}
}
