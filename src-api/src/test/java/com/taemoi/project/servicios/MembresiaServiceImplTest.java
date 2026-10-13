package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.impl.MembresiaServiceImpl;

/** Traducción de roles y regla de elección de escuela al iniciar sesión. */
class MembresiaServiceImplTest {

	private final MembresiaRepository repo = mock(MembresiaRepository.class);
	private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final List<Membresia> activas = new ArrayList<>();
	private MembresiaServiceImpl servicio;
	private Usuario usuario;

	@BeforeEach
	void setUp() {
		servicio = new MembresiaServiceImpl(repo, mock(OrganizacionRepository.class), usuarios, alumnos,
				new ResolutorOrganizacion(1L));
		usuario = new Usuario();
		usuario.setId(10L);
		usuario.setEmail("persona@example.test");
		when(repo.findActivasDeUsuario(10L)).thenAnswer(i -> List.copyOf(activas));
	}

	@Test
	void traduccionProvisionalDeRoles() {
		assertEquals(Roles.ROLE_ADMIN, RolMembresia.PROPIETARIO.autoridad());
		assertEquals(Roles.ROLE_MANAGER, RolMembresia.ADMINISTRADOR.autoridad());
		assertEquals(Roles.ROLE_MANAGER, RolMembresia.RESPONSABLE_SEDE.autoridad());
		assertEquals(Roles.ROLE_MANAGER, RolMembresia.PROFESOR.autoridad());
		assertEquals(Roles.ROLE_USER, RolMembresia.FAMILIA.autoridad());
		for (Roles rol : Roles.values()) {
			assertEquals(rol, RolMembresia.desdeAutoridad(rol).autoridad(), "Ida y vuelta de " + rol);
		}
		when(repo.findRolesActivos(10L, 1L)).thenReturn(List.of(RolMembresia.PROPIETARIO, RolMembresia.FAMILIA));
		assertEquals(Set.of(Roles.ROLE_ADMIN, Roles.ROLE_USER), servicio.autoridadesEn(10L, 1L));
	}

	@Test
	void sinMembresiasNoHayEscuela() {
		assertTrue(servicio.elegirOrganizacionParaLogin(usuario).isEmpty());
	}

	@Test
	void unaSolaEscuela() {
		activas.add(new Membresia(10L, 5L, RolMembresia.ADMINISTRADOR));
		assertEquals(Optional.of(5L), servicio.elegirOrganizacionParaLogin(usuario));
	}

	@Test
	void variasEscuelasUsaLaUltimaSiSigueSiendoValidaYSiNoLaDeMenorId() {
		activas.add(new Membresia(10L, 3L, RolMembresia.PROPIETARIO));
		activas.add(new Membresia(10L, 8L, RolMembresia.PROFESOR));

		assertEquals(Optional.of(3L), servicio.elegirOrganizacionParaLogin(usuario));
		usuario.setUltimaOrganizacionId(8L);
		assertEquals(Optional.of(8L), servicio.elegirOrganizacionParaLogin(usuario));
		usuario.setUltimaOrganizacionId(99L); // ya no es miembro
		assertEquals(Optional.of(3L), servicio.elegirOrganizacionParaLogin(usuario));
	}

	@Test
	void familiaSinAlumnoActivoNoCuentaComoEscuelaValida() {
		activas.add(new Membresia(10L, 2L, RolMembresia.FAMILIA));
		activas.add(new Membresia(10L, 4L, RolMembresia.FAMILIA));
		when(alumnos.contarActivosPorEmailEnOrganizacion("persona@example.test", 2L)).thenReturn(0L);
		when(alumnos.contarActivosPorEmailEnOrganizacion("persona@example.test", 4L)).thenReturn(2L);
		usuario.setUltimaOrganizacionId(2L);

		assertEquals(Optional.of(4L), servicio.elegirOrganizacionParaLogin(usuario));
	}

	@Test
	void familiaQueTambienGestionaNoNecesitaAlumno() {
		activas.add(new Membresia(10L, 2L, RolMembresia.FAMILIA));
		activas.add(new Membresia(10L, 2L, RolMembresia.ADMINISTRADOR));

		assertEquals(Optional.of(2L), servicio.elegirOrganizacionParaLogin(usuario));
		verify(alumnos, never()).contarActivosPorEmailEnOrganizacion(any(), anyLong());
	}

	@Test
	void reemplazarAutoridadesConservaLasQueYaLasDanYDesactivaElResto() {
		Membresia profesor = new Membresia(10L, 1L, RolMembresia.PROFESOR);
		Membresia familia = new Membresia(10L, 1L, RolMembresia.FAMILIA);
		when(repo.findByUsuarioIdAndOrganizacionId(10L, 1L)).thenReturn(List.of(profesor, familia));

		servicio.reemplazarAutoridades(10L, 1L, Set.of(Roles.ROLE_MANAGER, Roles.ROLE_ADMIN));

		assertTrue(profesor.isActiva(), "PROFESOR ya da ROLE_MANAGER: se conserva");
		assertFalse(familia.isActiva(), "ROLE_USER ya no está: se desactiva");
		verify(repo).saveAll(org.mockito.ArgumentMatchers.argThat(lista -> {
			List<Membresia> guardadas = new ArrayList<>();
			lista.forEach(guardadas::add);
			return guardadas.size() == 3 && guardadas.stream()
					.anyMatch(m -> m.getRol() == RolMembresia.PROPIETARIO && m.isActiva());
		}));
	}

	@Test
	void cuentaGlobalSoloSeGestionaSiTodasLasMembresiasSonDeEsaEscuela() {
		Membresia inactivaEn1 = new Membresia(10L, 1L, RolMembresia.FAMILIA);
		inactivaEn1.setActiva(false);
		when(repo.findByUsuarioId(10L)).thenReturn(List.of(inactivaEn1, new Membresia(10L, 2L, RolMembresia.PROPIETARIO)));
		assertFalse(servicio.puedeGestionarUsuarioGlobal(10L, 1L));
		assertFalse(servicio.puedeGestionarUsuarioGlobal(10L, 2L), "La inactiva en la 1 también cuenta");

		when(repo.findByUsuarioId(10L)).thenReturn(List.of(inactivaEn1, new Membresia(10L, 1L, RolMembresia.PROPIETARIO)));
		assertTrue(servicio.puedeGestionarUsuarioGlobal(10L, 1L));

		when(repo.findByUsuarioId(10L)).thenReturn(List.of());
		assertTrue(servicio.puedeGestionarUsuarioGlobal(10L, 1L), "Legado sin membresías: escuela por defecto");
		assertFalse(servicio.puedeGestionarUsuarioGlobal(10L, 2L));
	}

	@Test
	void usuariosSinMembresiaSoloSonDeLaEscuelaPorDefecto() {
		when(repo.existsByUsuarioId(10L)).thenReturn(false);
		assertTrue(servicio.esUsuarioDeOrganizacion(10L, 1L));
		assertFalse(servicio.esUsuarioDeOrganizacion(10L, 2L));
	}
}
