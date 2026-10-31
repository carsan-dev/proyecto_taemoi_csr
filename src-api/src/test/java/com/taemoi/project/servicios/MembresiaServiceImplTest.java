package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.dtos.response.OrganizacionUsuarioDTO;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;
import com.taemoi.project.services.impl.MembresiaServiceImpl;

/** Traducción de roles, puerta de entrada y regla de elección de escuela al iniciar sesión. */
class MembresiaServiceImplTest {

	private final MembresiaRepository repo = mock(MembresiaRepository.class);
	private final AccesoFamiliaService acceso = mock(AccesoFamiliaService.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final OrganizacionRepository organizaciones = mock(OrganizacionRepository.class);
	private final List<Membresia> activas = new ArrayList<>();
	private MembresiaServiceImpl servicio;
	private Usuario usuario;

	@BeforeEach
	void setUp() {
		servicio = new MembresiaServiceImpl(repo, organizaciones, usuarios, acceso,
				new ResolutorOrganizacion(1L));
		usuario = new Usuario();
		usuario.setId(10L);
		usuario.setEmail("persona@example.test");
		when(repo.findActivasDeUsuario(10L)).thenAnswer(i -> List.copyOf(activas));
		when(organizaciones.findAllById(any())).thenAnswer(i -> {
			List<Organizacion> resultado = new ArrayList<>();
			for (Object id : (Iterable<?>) i.getArgument(0)) {
				Organizacion org = new Organizacion();
				ReflectionTestUtils.setField(org, "id", id);
				org.setSlug("escuela-" + id);
				org.setNombre("Escuela " + id);
				resultado.add(org);
			}
			return resultado;
		});
	}

	@Test
	void traduccionDeRoles() {
		assertEquals(Roles.ROLE_ADMIN, RolMembresia.PROPIETARIO.autoridad());
		assertEquals(Roles.ROLE_MANAGER, RolMembresia.ADMINISTRADOR.autoridad());
		assertEquals(Roles.ROLE_RESPONSABLE_SEDE, RolMembresia.RESPONSABLE_SEDE.autoridad());
		assertEquals(Roles.ROLE_PROFESOR, RolMembresia.PROFESOR.autoridad());
		assertEquals(Roles.ROLE_USER, RolMembresia.FAMILIA.autoridad());
		for (Roles rol : Set.of(Roles.ROLE_ADMIN, Roles.ROLE_MANAGER, Roles.ROLE_USER)) {
			assertTrue(RolMembresia.asignableDesdeApiDeRoles(rol), rol.name());
			assertEquals(rol, RolMembresia.desdeAutoridad(rol).autoridad(), "Ida y vuelta de " + rol);
		}
		when(repo.findRolesActivos(10L, 1L)).thenReturn(List.of(RolMembresia.PROPIETARIO, RolMembresia.FAMILIA));
		assertEquals(Set.of(Roles.ROLE_ADMIN, Roles.ROLE_USER), servicio.autoridadesEn(10L, 1L));
	}

	@Test
	void losRolesNuevosNoSeAsignanDesdeLaApiDeRoles() {
		for (Roles rol : Set.of(Roles.ROLE_RESPONSABLE_SEDE, Roles.ROLE_PROFESOR)) {
			assertFalse(RolMembresia.asignableDesdeApiDeRoles(rol), rol.name());
			assertThrows(IllegalArgumentException.class, () -> RolMembresia.desdeAutoridad(rol), rol.name());
		}
	}

	/** Taemoi igual: con las tres autoridades de siempre, abre sesión exactamente quien tiene alguna. */
	@Test
	void conLasAutoridadesDeSiempreAbreSesionQuienTieneAlguna() {
		List<Roles> antiguas = List.of(Roles.ROLE_ADMIN, Roles.ROLE_MANAGER, Roles.ROLE_USER);
		for (int mascara = 0; mascara < 8; mascara++) {
			Set<Roles> autoridades = EnumSet.noneOf(Roles.class);
			for (int i = 0; i < 3; i++) {
				if ((mascara & (1 << i)) != 0) {
					autoridades.add(antiguas.get(i));
				}
			}
			assertEquals(!autoridades.isEmpty(), RolMembresia.abreSesion(autoridades), autoridades.toString());
			assertEquals(autoridades.equals(Set.of(Roles.ROLE_USER)), RolMembresia.requiereAlumnoActivo(autoridades),
					autoridades.toString());
		}
	}

	@Test
	void profesorYResponsableAbrenSesionSinAlumno_yProfesorConFamiliaLoExige() {
		// Decisión 180 (matiza la D5-1): la cuenta que solo es profesor entra (solo a su caja); sin grupos, también.
		assertTrue(RolMembresia.abreSesion(Set.of(Roles.ROLE_PROFESOR)));
		assertFalse(RolMembresia.requiereAlumnoActivo(Set.of(Roles.ROLE_PROFESOR)));
		assertTrue(RolMembresia.puedeEntrar(Set.of(Roles.ROLE_PROFESOR), () -> {
			throw new AssertionError("solo profesor no consulta alumnos accesibles");
		}));
		// Decisión 370: el responsable de sede solo entra, sin alumno; con profesor entra como responsable (371).
		assertTrue(RolMembresia.abreSesion(Set.of(Roles.ROLE_RESPONSABLE_SEDE)));
		assertFalse(RolMembresia.requiereAlumnoActivo(Set.of(Roles.ROLE_RESPONSABLE_SEDE)));
		assertTrue(RolMembresia.puedeEntrar(Set.of(Roles.ROLE_RESPONSABLE_SEDE), () -> {
			throw new AssertionError("el responsable no consulta alumnos accesibles");
		}));
		assertTrue(RolMembresia.abreSesion(Set.of(Roles.ROLE_RESPONSABLE_SEDE, Roles.ROLE_PROFESOR)));
		assertFalse(RolMembresia.requiereAlumnoActivo(Set.of(Roles.ROLE_RESPONSABLE_SEDE, Roles.ROLE_PROFESOR)));
		assertFalse(RolMembresia.abreSesion(Set.of()));
		assertTrue(RolMembresia.abreSesion(Set.of(Roles.ROLE_PROFESOR, Roles.ROLE_USER)));
		assertTrue(RolMembresia.requiereAlumnoActivo(Set.of(Roles.ROLE_PROFESOR, Roles.ROLE_USER)));
		// Pieza 9-5: el responsable entra por sí mismo; quedarse sin alumnos como familia no le cierra la puerta.
		assertFalse(RolMembresia.requiereAlumnoActivo(Set.of(Roles.ROLE_RESPONSABLE_SEDE, Roles.ROLE_USER)));
		assertFalse(RolMembresia.requiereAlumnoActivo(
				Set.of(Roles.ROLE_RESPONSABLE_SEDE, Roles.ROLE_PROFESOR, Roles.ROLE_USER)));
		assertFalse(RolMembresia.requiereAlumnoActivo(Set.of(Roles.ROLE_PROFESOR, Roles.ROLE_MANAGER)));
	}

	/**
	 * Pieza 9-5: los 32 subconjuntos de los cinco roles contra la fórmula anterior, congelada aquí a propósito (no llama a
	 * producción). {@code requiereAlumnoActivo} solo cambia en R + familia sin gestión, con o sin profesor.
	 */
	@Test
	void requiereAlumnoActivo_soloCambiaParaResponsableConFamiliaSinGestion() {
		List<Roles> roles = List.of(Roles.ROLE_ADMIN, Roles.ROLE_MANAGER, Roles.ROLE_RESPONSABLE_SEDE,
				Roles.ROLE_PROFESOR, Roles.ROLE_USER);
		assertEquals(5, Roles.values().length, "un rol nuevo debe entrar en esta tabla");
		int cambian = 0;
		for (int mascara = 0; mascara < 32; mascara++) {
			Set<Roles> autoridades = EnumSet.noneOf(Roles.class);
			for (int i = 0; i < roles.size(); i++) {
				if ((mascara & (1 << i)) != 0) {
					autoridades.add(roles.get(i));
				}
			}
			boolean gestion = autoridades.contains(Roles.ROLE_ADMIN) || autoridades.contains(Roles.ROLE_MANAGER);
			boolean anterior = autoridades.contains(Roles.ROLE_USER) && !gestion;
			boolean responsableConFamiliaSinGestion = anterior && autoridades.contains(Roles.ROLE_RESPONSABLE_SEDE);
			assertEquals(anterior && !responsableConFamiliaSinGestion, RolMembresia.requiereAlumnoActivo(autoridades),
					autoridades.toString());
			if (anterior != RolMembresia.requiereAlumnoActivo(autoridades)) {
				cambian++;
			}
		}
		assertEquals(2, cambian, "R + F y R + F + Pr");
	}

	@Test
	void sesionSoloProfesor_esLaQueTieneProfesorYNingunoDeLosTresRolesDeSiempre() {
		assertTrue(RolMembresia.sesionSoloProfesor(Set.of(Roles.ROLE_PROFESOR)));
		// Decisión 371 (sustituye a la 184): con RESPONSABLE_SEDE entra como responsable, sin la lista blanca.
		assertFalse(RolMembresia.sesionSoloProfesor(Set.of(Roles.ROLE_PROFESOR, Roles.ROLE_RESPONSABLE_SEDE)));
		for (Roles deSiempre : List.of(Roles.ROLE_ADMIN, Roles.ROLE_MANAGER, Roles.ROLE_USER)) {
			assertFalse(RolMembresia.sesionSoloProfesor(Set.of(Roles.ROLE_PROFESOR, deSiempre)), deSiempre.name());
			assertFalse(RolMembresia.sesionSoloProfesor(Set.of(deSiempre)), deSiempre.name());
		}
		assertFalse(RolMembresia.sesionSoloProfesor(Set.of()));
		assertFalse(RolMembresia.sesionSoloProfesor(Set.of(Roles.ROLE_RESPONSABLE_SEDE)));
	}

	@Test
	void sinMembresiasNoHayEscuela() {
		assertTrue(servicio.elegirOrganizacionParaLogin(usuario).isEmpty());
	}

	@Test
	void administradorDePlataformaSinEscuelaValidaEntraEnLaEscuelaPorDefecto() {
		ReflectionTestUtils.setField(usuario, "esAdminPlataforma", Boolean.TRUE);
		assertEquals(Optional.of(1L), servicio.elegirOrganizacionParaLogin(usuario), "sin membresías");

		// Solo familia sin alumno accesible en la escuela 4: tampoco es válida; por defecto.
		activas.add(new Membresia(10L, 4L, RolMembresia.FAMILIA));
		when(acceso.tieneAlgunoAccesible(usuario, 4L)).thenReturn(false);
		assertEquals(Optional.of(1L), servicio.elegirOrganizacionParaLogin(usuario), "sin escuela válida");

		// Con una escuela válida, la regla de siempre.
		activas.add(new Membresia(10L, 7L, RolMembresia.PROPIETARIO));
		assertEquals(Optional.of(7L), servicio.elegirOrganizacionParaLogin(usuario));
	}

	@Test
	void sinMarcaYSinEscuelaValidaNoHayEscuela() {
		activas.add(new Membresia(10L, 4L, RolMembresia.FAMILIA));
		when(acceso.tieneAlgunoAccesible(usuario, 4L)).thenReturn(false);
		assertFalse(usuario.isEsAdminPlataforma());
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
		activas.add(new Membresia(10L, 8L, RolMembresia.ADMINISTRADOR));

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
		when(acceso.tieneAlgunoAccesible(usuario, 2L)).thenReturn(false);
		when(acceso.tieneAlgunoAccesible(usuario, 4L)).thenReturn(true);
		usuario.setUltimaOrganizacionId(2L);

		assertEquals(Optional.of(4L), servicio.elegirOrganizacionParaLogin(usuario));
	}

	@Test
	void familiaQueTambienGestionaNoNecesitaAlumno() {
		activas.add(new Membresia(10L, 2L, RolMembresia.FAMILIA));
		activas.add(new Membresia(10L, 2L, RolMembresia.ADMINISTRADOR));

		assertEquals(Optional.of(2L), servicio.elegirOrganizacionParaLogin(usuario));
		verify(acceso, never()).tieneAlgunoAccesible(any(), anyLong());
	}

	/**
	 * La puerta de una cuenta solo familia la decide la regla única de AccesoFamiliaService (email normalizado,
	 * guard de email en blanco y vínculos: AccesoFamiliaServiceImplTest y AccesoFamiliaEquivalenciaIntegrationTest).
	 */
	@Test
	void familiaEntraSoloSiElServicioCentralLeDaAlgunAlumno() {
		ReflectionTestUtils.setField(usuario, "email", " Persona@Example.test ");
		activas.add(new Membresia(10L, 2L, RolMembresia.FAMILIA));
		when(repo.findRolesActivos(10L, 2L)).thenReturn(List.of(RolMembresia.FAMILIA));

		when(acceso.tieneAlgunoAccesible(usuario, 2L)).thenReturn(false);
		assertTrue(servicio.elegirOrganizacionParaLogin(usuario).isEmpty());
		assertFalse(servicio.puedeEntrarEn(usuario, 2L));

		when(acceso.tieneAlgunoAccesible(usuario, 2L)).thenReturn(true);
		assertEquals(Optional.of(2L), servicio.elegirOrganizacionParaLogin(usuario));
		assertTrue(servicio.puedeEntrarEn(usuario, 2L));
	}

	@Test
	void gestionConEmailEnBlancoSigueEntrando() {
		ReflectionTestUtils.setField(usuario, "email", "");
		activas.add(new Membresia(10L, 2L, RolMembresia.PROPIETARIO));
		activas.add(new Membresia(10L, 3L, RolMembresia.ADMINISTRADOR));
		when(repo.findRolesActivos(10L, 3L)).thenReturn(List.of(RolMembresia.ADMINISTRADOR));

		assertEquals(Optional.of(2L), servicio.elegirOrganizacionParaLogin(usuario), "ADMIN (PROPIETARIO)");
		assertTrue(servicio.puedeEntrarEn(usuario, 3L), "MANAGER (ADMINISTRADOR)");
		verify(acceso, never()).tieneAlgunoAccesible(any(), anyLong());
	}

	@Test
	void soloResponsableYSoloProfesorSonEscuelaValida() {
		// Decisión 370: solo responsable de sede (sin FAMILIA ni alumno) abre sesión en su escuela.
		activas.add(new Membresia(10L, 3L, RolMembresia.RESPONSABLE_SEDE));
		when(repo.findRolesActivos(10L, 3L)).thenReturn(List.of(RolMembresia.RESPONSABLE_SEDE));

		assertEquals(Optional.of(3L), servicio.elegirOrganizacionParaLogin(usuario));
		assertTrue(servicio.puedeEntrarEn(usuario, 3L));
		assertFalse(servicio.soloTieneRolesSinAcceso(10L));

		// Decisión 180: solo profesor (sin FAMILIA ni alumno) abre sesión en su escuela.
		activas.clear();
		activas.add(new Membresia(10L, 2L, RolMembresia.PROFESOR));
		when(repo.findRolesActivos(10L, 2L)).thenReturn(List.of(RolMembresia.PROFESOR));
		assertEquals(Optional.of(2L), servicio.elegirOrganizacionParaLogin(usuario));
		assertTrue(servicio.puedeEntrarEn(usuario, 2L));
		assertFalse(servicio.soloTieneRolesSinAcceso(10L));
		verify(acceso, never()).tieneAlgunoAccesible(any(), anyLong());
	}

	/** Decisión 371 (sustituye a la 184): responsable de sede + profesor entra como responsable, sin la lista blanca. */
	@Test
	void responsableYProfesorEnLaMismaEscuelaEntraComoResponsable() {
		activas.add(new Membresia(10L, 2L, RolMembresia.PROFESOR));
		activas.add(new Membresia(10L, 2L, RolMembresia.RESPONSABLE_SEDE));
		when(repo.findRolesActivos(10L, 2L)).thenReturn(List.of(RolMembresia.RESPONSABLE_SEDE, RolMembresia.PROFESOR));

		assertEquals(Optional.of(2L), servicio.elegirOrganizacionParaLogin(usuario));
		assertTrue(servicio.puedeEntrarEn(usuario, 2L));
		assertFalse(servicio.soloTieneRolesSinAcceso(10L));
		assertFalse(RolMembresia.sesionSoloProfesor(servicio.autoridadesEn(10L, 2L)));
		verify(acceso, never()).tieneAlgunoAccesible(any(), anyLong());
	}

	@Test
	void profesorYFamiliaEntraComoFamilia() {
		activas.add(new Membresia(10L, 2L, RolMembresia.PROFESOR));
		activas.add(new Membresia(10L, 2L, RolMembresia.FAMILIA));

		when(acceso.tieneAlgunoAccesible(usuario, 2L)).thenReturn(false);
		assertTrue(servicio.elegirOrganizacionParaLogin(usuario).isEmpty(), "sin alumno accesible no entra");
		assertFalse(servicio.soloTieneRolesSinAcceso(10L), "tiene FAMILIA: mensaje de siempre");

		when(acceso.tieneAlgunoAccesible(usuario, 2L)).thenReturn(true);
		assertEquals(Optional.of(2L), servicio.elegirOrganizacionParaLogin(usuario));
	}

	@Test
	void profesorYAdministradorEntraComoSiempre() {
		activas.add(new Membresia(10L, 2L, RolMembresia.PROFESOR));
		activas.add(new Membresia(10L, 2L, RolMembresia.ADMINISTRADOR));

		assertEquals(Optional.of(2L), servicio.elegirOrganizacionParaLogin(usuario));
		verify(acceso, never()).tieneAlgunoAccesible(any(), anyLong());
	}

	@Test
	void selectorDeEscuelaListaLasEscuelasDondeSusRolesAbrenSesion() {
		activas.add(new Membresia(10L, 1L, RolMembresia.ADMINISTRADOR));
		activas.add(new Membresia(10L, 2L, RolMembresia.PROFESOR));
		activas.add(new Membresia(10L, 3L, RolMembresia.RESPONSABLE_SEDE));
		activas.add(new Membresia(10L, 4L, RolMembresia.PROFESOR));
		activas.add(new Membresia(10L, 4L, RolMembresia.FAMILIA));

		List<OrganizacionUsuarioDTO> lista = servicio.organizacionesDe(10L, 1L);

		// Decisiones 180 y 370: aparecen la escuela donde solo es profesor y la de solo responsable.
		assertEquals(List.of(1L, 2L, 3L, 4L), lista.stream().map(OrganizacionUsuarioDTO::id).toList());
		assertEquals(List.of("ROLE_MANAGER"), lista.get(0).roles());
		assertEquals(List.of("ROLE_PROFESOR"), lista.get(1).roles());
		assertEquals(List.of("ROLE_RESPONSABLE_SEDE"), lista.get(2).roles());
		assertEquals(List.of("ROLE_PROFESOR", "ROLE_USER"), lista.get(3).roles(), "PROFESOR + FAMILIA: como familia");
		assertTrue(lista.get(0).activa());
	}

	/** Taemoi igual: con los roles de siempre aparecen todas, también la familia sin alumnos activos. */
	@Test
	void selectorDeEscuelaConLosRolesDeSiempreListaTodasSinMirarAlumnos() {
		activas.add(new Membresia(10L, 1L, RolMembresia.FAMILIA));
		activas.add(new Membresia(10L, 2L, RolMembresia.PROPIETARIO));
		activas.add(new Membresia(10L, 3L, RolMembresia.ADMINISTRADOR));
		activas.add(new Membresia(10L, 3L, RolMembresia.FAMILIA));
		when(acceso.tieneAlgunoAccesible(any(), anyLong())).thenReturn(false);

		List<OrganizacionUsuarioDTO> lista = servicio.organizacionesDe(10L, 2L);

		assertEquals(List.of(1L, 2L, 3L), lista.stream().map(OrganizacionUsuarioDTO::id).toList());
		assertEquals(List.of("ROLE_USER"), lista.get(0).roles());
		assertEquals(List.of("ROLE_ADMIN"), lista.get(1).roles());
		assertEquals(List.of("ROLE_MANAGER", "ROLE_USER"), lista.get(2).roles());
		verify(acceso, never()).tieneAlgunoAccesible(any(), anyLong());
	}

	@Test
	void soloTieneRolesSinAcceso_hoyNingunRolLoEs() {
		assertFalse(servicio.soloTieneRolesSinAcceso(10L), "sin membresías: es SIN_ESCUELA");
		activas.add(new Membresia(10L, 2L, RolMembresia.RESPONSABLE_SEDE));
		assertFalse(servicio.soloTieneRolesSinAcceso(10L), "decisión 370: el responsable abre sesión");
		activas.add(new Membresia(10L, 4L, RolMembresia.FAMILIA));
		assertFalse(servicio.soloTieneRolesSinAcceso(10L), "familia en otra escuela: mensaje de siempre");
	}

	@Test
	void reemplazarAutoridadesConservaProfesorYGestionaSoloLosRolesDeSiempre() {
		Membresia profesor = new Membresia(10L, 1L, RolMembresia.PROFESOR);
		Membresia familia = new Membresia(10L, 1L, RolMembresia.FAMILIA);
		when(repo.findByUsuarioIdAndOrganizacionId(10L, 1L)).thenReturn(List.of(profesor, familia));

		servicio.reemplazarAutoridades(10L, 1L, Set.of(Roles.ROLE_MANAGER, Roles.ROLE_ADMIN));

		assertTrue(profesor.isActiva(), "PROFESOR no lo gestiona la API de roles: se conserva");
		assertFalse(familia.isActiva(), "ROLE_USER ya no está: se desactiva");
		verify(repo).saveAll(org.mockito.ArgumentMatchers.argThat(lista -> {
			List<Membresia> guardadas = new ArrayList<>();
			lista.forEach(guardadas::add);
			return guardadas.size() == 4
					&& guardadas.stream().anyMatch(m -> m.getRol() == RolMembresia.PROPIETARIO && m.isActiva())
					&& guardadas.stream().anyMatch(m -> m.getRol() == RolMembresia.ADMINISTRADOR && m.isActiva());
		}));
	}

	@Test
	void reemplazarAutoridadesSinNingunaNoDesactivaProfesorNiResponsable() {
		Membresia profesor = new Membresia(10L, 1L, RolMembresia.PROFESOR);
		Membresia responsable = new Membresia(10L, 1L, RolMembresia.RESPONSABLE_SEDE);
		Membresia administrador = new Membresia(10L, 1L, RolMembresia.ADMINISTRADOR);
		when(repo.findByUsuarioIdAndOrganizacionId(10L, 1L)).thenReturn(List.of(profesor, responsable, administrador));

		servicio.reemplazarAutoridades(10L, 1L, Set.of());

		assertTrue(profesor.isActiva());
		assertTrue(responsable.isActiva());
		assertFalse(administrador.isActiva());
	}

	@Test
	void reemplazarAutoridadesConUnRolNuevoSeRechazaSinTocarNada() {
		Membresia familia = new Membresia(10L, 1L, RolMembresia.FAMILIA);
		when(repo.findByUsuarioIdAndOrganizacionId(10L, 1L)).thenReturn(List.of(familia));

		assertThrows(IllegalArgumentException.class,
				() -> servicio.reemplazarAutoridades(10L, 1L, Set.of(Roles.ROLE_PROFESOR)));

		assertTrue(familia.isActiva());
		verify(repo, never()).saveAll(any());
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
