package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Date;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.request.ActualizarTurnosPreinscripcionRequest;
import com.taemoi.project.dtos.request.FinalizarPreinscripcionRequest;
import com.taemoi.project.dtos.request.FinalizarPreinscripcionRequest.CampoActualizable;
import com.taemoi.project.dtos.request.FinalizarPreinscripcionRequest.DatosAltaDeporte;
import com.taemoi.project.dtos.request.FinalizarPreinscripcionRequest.DecisionEmail;
import com.taemoi.project.dtos.request.PreinscripcionRequest;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.EstadoEmailCambioTurnos;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.EstadoEmailFinalizacion;
import com.taemoi.project.events.PreinscripcionFinalizadaEvent;
import com.taemoi.project.events.PreinscripcionTurnosModificadosEvent;
import com.taemoi.project.exceptions.preinscripcion.PreinscripcionDuplicadaException;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.PlantillaPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoTarifa;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PlantillaPreinscripcionRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TurnoRepository;

@ExtendWith(MockitoExtension.class)
class PreinscripcionServiceTest {
	@Mock PreinscripcionRepository preinscripciones;
	@Mock PlantillaPreinscripcionRepository plantillas;
	@Mock GrupoRepository grupos;
	@Mock TurnoRepository turnos;
	@Mock AlumnoRepository alumnos;
	@Mock AlumnoDeporteRepository alumnoDeportes;
	@Mock GradoRepository grados;
	@Mock TemporadaService temporadas;
	@Mock PDFService pdf;
	@Mock EmailService email;
	@Mock ConfiguracionSistemaService configuracion;
	@Mock AforoPreinscripcionService aforo;
	@Mock ApplicationEventPublisher eventos;

	PreinscripcionService service;
	Preinscripcion preinscripcion;
	Grupo grupo;
	Turno lunes;
	Turno miercoles;

	@BeforeEach
	void setUp() {
		service = new PreinscripcionService(preinscripciones, plantillas, grupos, turnos, alumnos,
				alumnoDeportes, grados, temporadas, pdf, email, configuracion, new ObjectMapper(), aforo, eventos);
		lenient().when(temporadas.edadCohorte(any(LocalDate.class), anyString())).thenAnswer(inv -> {
			LocalDate nacimiento = inv.getArgument(0);
			String temporada = inv.getArgument(1);
			return Integer.parseInt(temporada.substring(0, 4)) - nacimiento.getYear();
		});
		grupo = new Grupo();
		grupo.setId(7L);
		grupo.setNombre("Taekwondo infantil");
		grupo.setDeporte(Deporte.TAEKWONDO);
		lunes = turno(11L, "Lunes");
		miercoles = turno(12L, "Miércoles");
		preinscripcion = solicitudPendiente();
		lenient().when(preinscripciones.findByReferenciaForUpdate("PRE-1")).thenReturn(Optional.of(preinscripcion));
		lenient().when(aforo.bloquearTurnos(anyCollection())).thenAnswer(inv -> {
			Collection<Long> ids = inv.getArgument(0);
			return List.of(lunes, miercoles).stream().filter(t -> ids.contains(t.getId())).toList();
		});
		lenient().when(turnos.findByGrupo(grupo)).thenReturn(List.of(lunes, miercoles));
		lenient().when(alumnos.saveAndFlush(any(Alumno.class))).thenAnswer(inv -> {
			Alumno alumno = inv.getArgument(0);
			if (alumno.getId() == null) alumno.setId(99L);
			return alumno;
		});
	}

	@Test
	void rechazaOtraPreinscripcionDeLaMismaPersonaDeporteYTemporada() {
		when(temporadas.actual()).thenReturn("2026-2027");
		when(preinscripciones.existsByIdentidadHashAndDeporteAndTemporadaAndEstadoIn(
				anyString(), eq(Deporte.TAEKWONDO), eq("2026-2027"), anyCollection())).thenReturn(true);

		PreinscripcionDuplicadaException error = assertThrows(PreinscripcionDuplicadaException.class,
				() -> service.crear(nuevaSolicitud(Deporte.TAEKWONDO), "envio-1"));

		assertTrue(error.getMessage().contains("persona y deporte"));
		verify(aforo, never()).bloquearTurnos(anyCollection());
	}

	@Test
	void rechazaElReenvioDeLaMismaPeticionIdempotente() {
		when(preinscripciones.existsByIdempotencyKeyHash(anyString())).thenReturn(true);

		assertThrows(PreinscripcionDuplicadaException.class,
				() -> service.crear(nuevaSolicitud(Deporte.TAEKWONDO), "envio-repetido"));

		verify(preinscripciones, never()).existsByIdentidadHashAndDeporteAndTemporadaAndEstadoIn(
				anyString(), any(), anyString(), anyCollection());
		verify(aforo, never()).bloquearTurnos(anyCollection());
	}

	@Test
	void compruebaDuplicadosDentroDelDeporteSolicitado() {
		grupo.setDeporte(Deporte.PILATES);
		when(temporadas.actual()).thenReturn("2026-2027");

		assertThrows(IllegalArgumentException.class,
				() -> service.crear(nuevaSolicitud(Deporte.PILATES), "envio-pilates"));

		verify(preinscripciones).existsByIdentidadHashAndDeporteAndTemporadaAndEstadoIn(
				anyString(), eq(Deporte.PILATES), eq("2026-2027"), anyCollection());
	}

	@Test
	void creaAlumnoDeporteGrupoYTodosLosTurnosEnUnaFinalizacion() {
		preinscripcion.setNombre("Ana María");
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.empty());
		when(alumnos.findMaxNumeroExpediente()).thenReturn(40);
		when(alumnoDeportes.findByAlumnoIdAndDeporte(99L, Deporte.TAEKWONDO)).thenReturn(Optional.empty());
		when(alumnoDeportes.countByAlumnoIdAndActivoTrue(99L)).thenReturn(0L);
		Grado blanco = new Grado();
		when(grados.findByTipoGrado(TipoGrado.BLANCO)).thenReturn(blanco);

		service.finalizar("PRE-1", request(null, Set.of(), datosTaekwondo()));

		Alumno creado = preinscripcion.getAlumno();
		assertNotNull(creado);
		assertEquals("ANA MARÍA", creado.getNombre());
		assertEquals("GARCÍA LÓPEZ", creado.getApellidos());
		assertEquals(41, creado.getNumeroExpediente());
		assertEquals(612345678, creado.getTelefono());
		assertEquals(611222333, creado.getTelefono2());
		assertFalse(creado.getTieneDiscapacidad());
		assertEquals("María García", creado.getResponsableLegalNombre());
		assertEquals("87654321X", creado.getResponsableLegalNif());
		assertTrue(creado.getActivo());
		assertFalse(creado.getAutorizacionWeb());
		assertEquals(List.of(grupo), creado.getGrupos());
		assertEquals(List.of(lunes, miercoles), creado.getTurnos());
		assertEquals(EstadoPreinscripcion.FINALIZADA, preinscripcion.getEstado());
		assertEquals(EstadoEmailFinalizacion.PENDIENTE, preinscripcion.getEstadoEmailFinalizacion());
		verify(eventos).publishEvent(new PreinscripcionFinalizadaEvent("PRE-1"));
		verify(alumnoDeportes).save(argThat(ad -> ad.getAlumno() == creado
				&& ad.getDeporte() == Deporte.TAEKWONDO
				&& Boolean.TRUE.equals(ad.getActivo())
				&& Boolean.TRUE.equals(ad.getPrincipal())
				&& ad.getFechaAlta() != null
				&& ad.getFechaAltaInicial() != null
				&& ad.getGrado() == blanco));
	}

	@Test
	void noDuplicaGrupoCuandoDosTurnosDevuelvenInstanciasEquivalentes() {
		Grupo mismoGrupo = new Grupo();
		mismoGrupo.setId(grupo.getId());
		mismoGrupo.setNombre(grupo.getNombre());
		mismoGrupo.setDeporte(grupo.getDeporte());
		miercoles.setGrupo(mismoGrupo);
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.empty());
		when(alumnos.findMaxNumeroExpediente()).thenReturn(40);
		when(alumnoDeportes.findByAlumnoIdAndDeporte(99L, Deporte.TAEKWONDO)).thenReturn(Optional.empty());
		when(alumnoDeportes.countByAlumnoIdAndActivoTrue(99L)).thenReturn(0L);
		when(grados.findByTipoGrado(TipoGrado.BLANCO)).thenReturn(new Grado());

		service.finalizar("PRE-1", request(null, Set.of(), datosTaekwondo()));

		Alumno creado = preinscripcion.getAlumno();
		assertEquals(List.of(lunes, miercoles), creado.getTurnos());
		assertEquals(List.of(grupo), creado.getGrupos());
	}

	@Test
	void asignaTurnosExactosDeDosGruposDiferentes() {
		Grupo segundoGrupo = new Grupo();
		segundoGrupo.setId(8L);
		segundoGrupo.setNombre("Taekwondo martes y jueves");
		segundoGrupo.setDeporte(Deporte.TAEKWONDO);
		Turno jueves = new Turno();
		jueves.setId(21L);
		jueves.setDiaSemana("Jueves");
		jueves.setHoraInicio("18:00");
		jueves.setHoraFin("19:00");
		jueves.setGrupo(segundoGrupo);
		preinscripcion.setTurnos(new java.util.LinkedHashSet<>(List.of(lunes, jueves)));
		when(aforo.bloquearTurnos(List.of(11L, 21L))).thenReturn(List.of(lunes, jueves));
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.empty());
		when(alumnos.findMaxNumeroExpediente()).thenReturn(40);
		when(alumnoDeportes.findByAlumnoIdAndDeporte(99L, Deporte.TAEKWONDO)).thenReturn(Optional.empty());
		when(alumnoDeportes.countByAlumnoIdAndActivoTrue(99L)).thenReturn(0L);
		when(grados.findByTipoGrado(TipoGrado.BLANCO)).thenReturn(new Grado());

		service.finalizar("PRE-1", request(null, Set.of(), datosTaekwondo()));

		Alumno creado = preinscripcion.getAlumno();
		assertEquals(List.of(lunes, jueves), creado.getTurnos());
		assertEquals(List.of(grupo, segundoGrupo), creado.getGrupos());
	}

	@Test
	void finalizaTurnosAsignadosAdministrativamenteAunqueEstanFueraDeEdad() {
		grupo.setRangoEdadMax(10);
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.empty());
		when(alumnos.findMaxNumeroExpediente()).thenReturn(40);
		when(alumnoDeportes.findByAlumnoIdAndDeporte(99L, Deporte.TAEKWONDO)).thenReturn(Optional.empty());
		when(alumnoDeportes.countByAlumnoIdAndActivoTrue(99L)).thenReturn(0L);
		when(grados.findByTipoGrado(TipoGrado.BLANCO)).thenReturn(new Grado());

		service.finalizar("PRE-1", request(null, Set.of(), datosTaekwondo()));

		assertEquals(EstadoPreinscripcion.FINALIZADA, preinscripcion.getEstado());
		verify(temporadas, never()).edadCohorte(any(), anyString());
	}

	@Test
	void rechazaTurnosFueraDeEdadEnLaSeleccionPublica() {
		grupo.setRangoEdadMax(10);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.validarSeleccion(Deporte.TAEKWONDO, LocalDate.of(2014, 4, 12),
						"2026-2027", List.of(lunes)));

		assertTrue(error.getMessage().contains("edad"));
	}

	@Test
	void cambiaTurnosSinAlterarSolicitudOriginalYPoneElAvisoEnCola() {
		preinscripcion.setEstado(EstadoPreinscripcion.EN_LISTA_ESPERA);
		preinscripcion.setGrupoSnapshot("Taekwondo · Lunes 18:00-19:00 (Taekwondo infantil)");
		byte[] pdfOriginal = {1, 2, 3, 4};
		preinscripcion.setPdfFirmado(pdfOriginal);
		Grupo mayores = new Grupo();
		mayores.setId(8L);
		mayores.setNombre("Taekwondo mayores");
		mayores.setDeporte(Deporte.TAEKWONDO);
		mayores.setRangoEdadMin(16);
		mayores.setRangoEdadMax(99);
		Turno viernes = new Turno();
		viernes.setId(21L);
		viernes.setDiaSemana("Viernes");
		viernes.setHoraInicio("20:00");
		viernes.setHoraFin("21:00");
		viernes.setGrupo(mayores);
		when(aforo.bloquearTurnos(Set.of(11L, 12L, 21L))).thenReturn(List.of(lunes, miercoles, viernes));

		service.actualizarTurnos("PRE-1", new ActualizarTurnosPreinscripcionRequest(List.of(21L)));

		assertEquals(List.of(viernes), preinscripcion.getTurnos().stream().toList());
		assertSame(mayores, preinscripcion.getGrupo());
		assertEquals(EstadoPreinscripcion.PENDIENTE, preinscripcion.getEstado());
		assertEquals("Taekwondo · Lunes 18:00-19:00 (Taekwondo infantil)", preinscripcion.getGrupoSnapshot());
		assertSame(pdfOriginal, preinscripcion.getPdfFirmado());
		assertTrue(service.grupoVigente(preinscripcion).contains("Taekwondo mayores"));
		assertEquals(EstadoEmailCambioTurnos.PENDIENTE, preinscripcion.getEstadoEmailCambioTurnos());
		assertNotNull(preinscripcion.getTurnosModificadosEn());
		assertTrue(preinscripcion.getEmailCambioTurnosAnteriorSnapshot().contains("Taekwondo infantil"));
		assertTrue(preinscripcion.getEmailCambioTurnosNuevoSnapshot().contains("Taekwondo mayores"));
		verify(preinscripciones).saveAndFlush(preinscripcion);
		verify(eventos).publishEvent(new PreinscripcionTurnosModificadosEvent("PRE-1"));
		verify(email, never()).sendEmailSync(anyString(), anyString(), anyString());
	}

	@Test
	void conservaComparacionesEnLaObservacionYRecortaLosExtremos() {
		String resultado = ReflectionTestUtils.invokeMethod(service, "limpiarObservacion", "  Peso > 45 kg y < 60 kg  ");

		assertEquals("Peso > 45 kg y < 60 kg", resultado);
	}

	@Test
	void reenviaSolicitudOriginalConHorarioYPdfOriginalesSinAfirmarEstado() {
		preinscripcion.setEstado(EstadoPreinscripcion.PENDIENTE);
		preinscripcion.setGrupoSnapshot("Horario original");
		preinscripcion.setEmailCambioTurnosNuevoSnapshot("Horario vigente");
		preinscripcion.setObservaciones("Peso > 45 kg <script>alert('x')</script>");
		byte[] pdfOriginal = {9, 8, 7};
		preinscripcion.setPdfFirmado(pdfOriginal);
		when(preinscripciones.findByReferencia("PRE-1")).thenReturn(Optional.of(preinscripcion));

		service.reenviar("PRE-1");

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(email).sendEmailConAdjunto(eq("ana@example.com"), startsWith("Copia de la solicitud original"),
				html.capture(), eq("solicitud-original-PRE-1.pdf"), same(pdfOriginal));
		assertTrue(html.getValue().contains("Horario original"));
		assertFalse(html.getValue().contains("Horario vigente"));
		assertFalse(html.getValue().contains("Plaza asignada"));
		assertTrue(html.getValue().contains("Peso &gt; 45 kg"));
		assertFalse(html.getValue().contains("<script>"));
	}

	@Test
	void solicitaReenvioDelUltimoCambioMientrasSiguePendiente() {
		preinscripcion.setEmailCambioTurnosAnteriorSnapshot("Horario anterior");
		preinscripcion.setEmailCambioTurnosNuevoSnapshot("Horario vigente");
		preinscripcion.setEstadoEmailCambioTurnos(EstadoEmailCambioTurnos.ERROR);
		preinscripcion.setEmailCambioTurnosUltimoError("SMTP");

		service.reenviarCambioTurnos("PRE-1");

		assertEquals(EstadoEmailCambioTurnos.PENDIENTE, preinscripcion.getEstadoEmailCambioTurnos());
		assertNull(preinscripcion.getEmailCambioTurnosUltimoError());
		verify(eventos).publishEvent(new PreinscripcionTurnosModificadosEvent("PRE-1"));
	}

	@Test
	void rechazaReenvioDeCambioTrasFinalizarLaSolicitud() {
		preinscripcion.setEstado(EstadoPreinscripcion.FINALIZADA);
		preinscripcion.setEmailCambioTurnosAnteriorSnapshot("Horario anterior");
		preinscripcion.setEmailCambioTurnosNuevoSnapshot("Horario vigente");

		assertThrows(IllegalStateException.class, () -> service.reenviarCambioTurnos("PRE-1"));
		verify(eventos, never()).publishEvent(new PreinscripcionTurnosModificadosEvent("PRE-1"));
	}

	@Test
	void cambioAdministrativoRechazaDosTurnosDelMismoDia() {
		miercoles.setDiaSemana("Lunes");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.actualizarTurnos("PRE-1", new ActualizarTurnosPreinscripcionRequest(List.of(11L, 12L))));

		assertTrue(error.getMessage().contains("por día"));
		verify(preinscripciones, never()).saveAndFlush(preinscripcion);
	}

	@Test
	void incluyeObservacionEscapadaEnCorreosDeClienteYAdmin() {
		PlantillaPreinscripcion plantilla = new PlantillaPreinscripcion();
		plantilla.setInstrucciones("Acude al club.");
		preinscripcion.setPlantilla(plantilla);
		preinscripcion.setGrupoSnapshot("Taekwondo · Lunes 18:00-19:00");
		preinscripcion.setObservaciones("<b>Es grande & fuerte</b>");
		ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:4200");

		String cliente = ReflectionTestUtils.invokeMethod(service, "htmlConfirmacion", preinscripcion);
		String admin = ReflectionTestUtils.invokeMethod(service, "htmlAdmin", preinscripcion);

		assertTrue(cliente.contains("Es grande &amp; fuerte"));
		assertTrue(admin.contains("Es grande &amp; fuerte"));
		assertFalse(cliente.contains("<b>Es grande"));
		assertFalse(admin.contains("<b>Es grande"));
	}

	@Test
	void rechazaDosTurnosDelMismoDia() {
		miercoles.setDiaSemana("Lunes");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.finalizar("PRE-1", request(null, Set.of(), datosTaekwondo())));

		assertTrue(error.getMessage().contains("por día"));
		assertEquals(EstadoPreinscripcion.PENDIENTE, preinscripcion.getEstado());
	}

	@Test
	void actualizaSoloDiferenciasConfirmadasYConservaAsignacionesPrevias() {
		Alumno existente = alumnoExistente(true);
		preinscripcion.setConsentimientoFotografico(true);
		existente.setAutorizacionWeb(false);
		existente.setNombre("Nombre anterior");
		existente.setEmail("conservar@example.com");
		Turno previo = turno(5L, "Viernes");
		existente.addTurno(previo);
		AlumnoDeporte deporte = deporte(existente, true);
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.of(existente));
		when(alumnos.findById(3L)).thenReturn(Optional.of(existente));
		when(alumnoDeportes.findByAlumnoIdAndDeporte(3L, Deporte.TAEKWONDO)).thenReturn(Optional.of(deporte));

		service.finalizar("PRE-1", request(3L,
				Set.of(CampoActualizable.NOMBRE, CampoActualizable.TELEFONO2), null));

		assertEquals("Ana", existente.getNombre());
		assertEquals("conservar@example.com", existente.getEmail());
		assertEquals(611222333, existente.getTelefono2());
		assertTrue(existente.getAutorizacionWeb());
		assertEquals(List.of(lunes, miercoles), existente.getTurnos());
		verify(alumnoDeportes).save(deporte);
	}

	@Test
	void bloqueaLaFinalizacionSiLosCorreosDifierenYNoHayDecisionExpresa() {
		Alumno existente = alumnoExistente(true);
		existente.setEmail("ficha@example.com");
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.of(existente));
		when(alumnos.findById(3L)).thenReturn(Optional.of(existente));
		FinalizarPreinscripcionRequest solicitud = new FinalizarPreinscripcionRequest(
				FinalizarPreinscripcionRequest.AccionAlumno.VINCULAR_EXISTENTE, 3L, Set.of(), null,
				null, datosTarifaExistente());

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.finalizar("PRE-1", solicitud));

		assertTrue(error.getMessage().contains("elegirse expresamente"));
		assertEquals("ficha@example.com", existente.getEmail());
		assertEquals(EstadoPreinscripcion.PENDIENTE, preinscripcion.getEstado());
		verify(eventos, never()).publishEvent(any());
	}

	@Test
	void usaElCorreoDePreinscripcionCuandoSeEligeComoDefinitivo() {
		Alumno existente = alumnoExistente(true);
		existente.setEmail("ficha@example.com");
		AlumnoDeporte deporte = deporte(existente, true);
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.of(existente));
		when(alumnos.findById(3L)).thenReturn(Optional.of(existente));
		when(alumnoDeportes.findByAlumnoIdAndDeporte(3L, Deporte.TAEKWONDO)).thenReturn(Optional.of(deporte));
		FinalizarPreinscripcionRequest solicitud = new FinalizarPreinscripcionRequest(
				FinalizarPreinscripcionRequest.AccionAlumno.VINCULAR_EXISTENTE, 3L, Set.of(), null,
				DecisionEmail.USAR_PREINSCRIPCION, datosTarifaExistente());

		service.finalizar("PRE-1", solicitud);

		assertEquals("ana@example.com", existente.getEmail());
		assertEquals(EstadoPreinscripcion.FINALIZADA, preinscripcion.getEstado());
	}

	@Test
	void reenvioFinalSoloSeSolicitaParaUnaInscripcionFinalizada() {
		preinscripcion.setEstado(EstadoPreinscripcion.FINALIZADA);

		service.reenviarFinalizacion("PRE-1");

		assertEquals(EstadoEmailFinalizacion.PENDIENTE, preinscripcion.getEstadoEmailFinalizacion());
		verify(eventos).publishEvent(new PreinscripcionFinalizadaEvent("PRE-1"));
	}

	@Test
	void rechazaReenvioFinalSiLaPreinscripcionNoEstaFinalizada() {
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> service.reenviarFinalizacion("PRE-1"));

		assertTrue(error.getMessage().contains("solo puede reenviarse"));
		assertEquals(EstadoEmailFinalizacion.NO_ENVIADO, preinscripcion.getEstadoEmailFinalizacion());
		verify(eventos, never()).publishEvent(any());
	}

	@Test
	void reactivaAlumnoYDeporteConservandoAntiguedad() {
		Alumno existente = alumnoExistente(false);
		Date altaInicialAlumno = new Date(1_000_000L);
		existente.setFechaAltaInicial(altaInicialAlumno);
		existente.setFechaBaja(new Date());
		AlumnoDeporte deporte = deporte(existente, false);
		Date altaInicialDeporte = new Date(2_000_000L);
		deporte.setFechaAltaInicial(altaInicialDeporte);
		deporte.setFechaBaja(new Date());
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.of(existente));
		when(alumnos.findById(3L)).thenReturn(Optional.of(existente));
		when(alumnoDeportes.findByAlumnoIdAndDeporte(3L, Deporte.TAEKWONDO)).thenReturn(Optional.of(deporte));
		when(alumnoDeportes.countByAlumnoIdAndActivoTrue(3L)).thenReturn(0L);

		service.finalizar("PRE-1", request(3L, Set.of(), null));

		assertTrue(existente.getActivo());
		assertNull(existente.getFechaBaja());
		assertSame(altaInicialAlumno, existente.getFechaAltaInicial());
		assertTrue(deporte.getActivo());
		assertNull(deporte.getFechaBaja());
		assertSame(altaInicialDeporte, deporte.getFechaAltaInicial());
		assertTrue(deporte.getPrincipal());
	}

	@Test
	void reactivaAlumnoSinRecrearVinculoConGrupoYaSeleccionado() {
		Alumno existente = alumnoExistente(false);
		Grupo grupoPersistido = spy(grupo);
		grupoPersistido.addAlumno(existente);
		existente.addTurno(lunes);
		AlumnoDeporte deporte = deporte(existente, false);
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.of(existente));
		when(alumnos.findById(3L)).thenReturn(Optional.of(existente));
		when(alumnoDeportes.findByAlumnoIdAndDeporte(3L, Deporte.TAEKWONDO)).thenReturn(Optional.of(deporte));
		when(alumnoDeportes.countByAlumnoIdAndActivoTrue(3L)).thenReturn(0L);
		clearInvocations(grupoPersistido);

		service.finalizar("PRE-1", request(3L, Set.of(), null));

		assertEquals(List.of(grupoPersistido), existente.getGrupos());
		assertEquals(List.of(lunes, miercoles), existente.getTurnos());
		verify(grupoPersistido, never()).removeAlumno(existente);
	}

	@Test
	void rechazaAlumnoCuyoDniNoCoincide() {
		Alumno equivocado = alumnoExistente(true);
		equivocado.setNif("87654321X");
		when(alumnos.findById(3L)).thenReturn(Optional.of(equivocado));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.finalizar("PRE-1", requestSinDatos(3L, Set.of())));

		assertTrue(error.getMessage().contains("identidad"));
		verify(alumnoDeportes, never()).save(any());
		assertEquals(EstadoPreinscripcion.PENDIENTE, preinscripcion.getEstado());
	}

	@Test
	void exigeDatosAdministrativosCuandoElDeporteEsNuevo() {
		Alumno existente = alumnoExistente(true);
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.of(existente));
		when(alumnos.findById(3L)).thenReturn(Optional.of(existente));
		when(alumnoDeportes.findByAlumnoIdAndDeporte(3L, Deporte.TAEKWONDO)).thenReturn(Optional.empty());

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.finalizar("PRE-1", requestSinDatos(3L, Set.of())));

		assertTrue(error.getMessage().contains("tarifa"));
		assertEquals(EstadoPreinscripcion.PENDIENTE, preinscripcion.getEstado());
	}

	@Test
	void creaMenorSinDniSinGenerarUnNifArtificial() {
		preinscripcion.setDni(null);
		preinscripcion.setTieneDiscapacidad(true);
		when(alumnos.findMaxNumeroExpediente()).thenReturn(40);
		when(alumnoDeportes.findByAlumnoIdAndDeporte(99L, Deporte.TAEKWONDO)).thenReturn(Optional.empty());
		when(alumnoDeportes.countByAlumnoIdAndActivoTrue(99L)).thenReturn(0L);
		when(grados.findByTipoGrado(TipoGrado.BLANCO)).thenReturn(new Grado());

		service.finalizar("PRE-1", request(null, Set.of(), datosTaekwondo()));

		assertNull(preinscripcion.getAlumno().getNif());
		assertTrue(preinscripcion.getAlumno().getTieneDiscapacidad());
	}

	@Test
	void vinculaFichaSinNifYPermiteIncorporarElDniConfirmado() {
		Alumno existente = alumnoExistente(true);
		existente.setNif(null);
		when(alumnos.findById(3L)).thenReturn(Optional.of(existente));
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.empty());
		when(alumnoDeportes.findByAlumnoIdAndDeporte(3L, Deporte.TAEKWONDO)).thenReturn(Optional.of(deporte(existente, true)));

		service.finalizar("PRE-1", request(3L, Set.of(CampoActualizable.NIF), null));

		assertEquals("12345678Z", existente.getNif());
		assertSame(existente, preinscripcion.getAlumno());
	}

	@Test
	void conservaTelefonoSecundarioExistenteCuandoNoFueFacilitado() {
		Alumno existente = alumnoExistente(true);
		existente.setTelefono2(699887766);
		preinscripcion.setTelefono2(null);
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.of(existente));
		when(alumnos.findById(3L)).thenReturn(Optional.of(existente));
		when(alumnoDeportes.findByAlumnoIdAndDeporte(3L, Deporte.TAEKWONDO))
				.thenReturn(Optional.of(deporte(existente, true)));

		service.finalizar("PRE-1", request(3L, Set.of(CampoActualizable.TELEFONO2), null));

		assertEquals(699887766, existente.getTelefono2());
	}

	@Test
	void exigeConfirmarDiscapacidadEnSolicitudHistorica() {
		preinscripcion.setTieneDiscapacidad(null);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.finalizar("PRE-1", request(null, Set.of(), datosTaekwondo())));

		assertTrue(error.getMessage().contains("discapacidad"));
		assertEquals(EstadoPreinscripcion.PENDIENTE, preinscripcion.getEstado());
	}

	@Test
	void finalizaSolicitudAntiguaConTurnoYDiscapacidadConfirmada() {
		preinscripcion.setGrupo(null);
		preinscripcion.setTurno(lunes);
		preinscripcion.setTieneDiscapacidad(null);
		when(alumnos.findByNif("12345678Z")).thenReturn(Optional.empty());
		when(alumnos.findMaxNumeroExpediente()).thenReturn(40);
		when(alumnoDeportes.findByAlumnoIdAndDeporte(99L, Deporte.TAEKWONDO)).thenReturn(Optional.empty());
		when(alumnoDeportes.countByAlumnoIdAndActivoTrue(99L)).thenReturn(0L);
		when(grados.findByTipoGrado(TipoGrado.BLANCO)).thenReturn(new Grado());
		FinalizarPreinscripcionRequest solicitud = new FinalizarPreinscripcionRequest(
				FinalizarPreinscripcionRequest.AccionAlumno.CREAR_NUEVO, null, Set.of(), false,
				null, datosTaekwondo());

		service.finalizar("PRE-1", solicitud);

		assertNull(preinscripcion.getGrupo());
		assertFalse(preinscripcion.getAlumno().getTieneDiscapacidad());
		assertEquals(List.of(lunes),preinscripcion.getAlumno().getTurnos());
		assertEquals(EstadoPreinscripcion.FINALIZADA, preinscripcion.getEstado());
	}

	@Test
	void promocionaColaCronologicaYReservaTodosLosTurnosAntesDeEvaluarLaSiguiente() {
		Alumno alumnoActivo = new Alumno();
		alumnoActivo.setId(50L);
		alumnoActivo.setActivo(true);
		lunes.setAlumnos(List.of(alumnoActivo));
		miercoles.setAlumnos(List.of());
		Preinscripcion primera = solicitudEnEspera("PRE-PRIMERA", "primera@example.com", List.of(lunes, miercoles));
		Preinscripcion segunda = solicitudEnEspera("PRE-SEGUNDA", "segunda@example.com", List.of(miercoles));
		List<Preinscripcion> cola = List.of(primera, segunda);
		when(temporadas.actual()).thenReturn("2026/2027");
		when(configuracion.obtenerLimiteTurno()).thenReturn(1);
		when(alumnoDeportes.existsByAlumnoIdAndDeporteAndActivoTrue(50L, Deporte.TAEKWONDO)).thenReturn(true);
		when(preinscripciones.findByTemporadaAndEstadoOrderByCreadaEnAscIdAsc(
				"2026/2027", EstadoPreinscripcion.EN_LISTA_ESPERA)).thenReturn(cola);
		when(turnos.findAllByIdForUpdate(anyList())).thenAnswer(inv -> {
			Collection<Long> ids = inv.getArgument(0);
			return List.of(lunes, miercoles).stream().filter(t -> ids.contains(t.getId())).toList();
		});
		when(preinscripciones.countDistinctByTurnosContainingAndTemporadaAndEstado(
				any(Turno.class), eq("2026/2027"), eq(EstadoPreinscripcion.PENDIENTE))).thenAnswer(inv -> {
			Turno turno = inv.getArgument(0);
			return cola.stream()
					.filter(p -> p.getEstado() == EstadoPreinscripcion.PENDIENTE)
					.filter(p -> p.getTurnos().contains(turno))
					.count();
		});
		AforoPreinscripcionService aforoReal = new AforoPreinscripcionService(
				turnos, alumnoDeportes, preinscripciones, configuracion);
		PreinscripcionService servicioPromocion = new PreinscripcionService(preinscripciones, plantillas, grupos,
				turnos, alumnos, alumnoDeportes, grados, temporadas, pdf, email, configuracion,
				new ObjectMapper(), aforoReal, eventos);

		servicioPromocion.promocionarTodas();

		assertEquals(EstadoPreinscripcion.PENDIENTE, primera.getEstado());
		assertEquals(EstadoPreinscripcion.EN_LISTA_ESPERA, segunda.getEstado());
		assertNotNull(primera.getPromocionadaEn());
		assertEquals(1L, preinscripciones.countDistinctByTurnosContainingAndTemporadaAndEstado(
				lunes, "2026/2027", EstadoPreinscripcion.PENDIENTE));
		assertEquals(1L, preinscripciones.countDistinctByTurnosContainingAndTemporadaAndEstado(
				miercoles, "2026/2027", EstadoPreinscripcion.PENDIENTE));
		verify(preinscripciones).saveAndFlush(primera);
		verify(preinscripciones, never()).saveAndFlush(segunda);
		verify(email, times(1)).sendEmail(eq("primera@example.com"), startsWith("Ya tienes plaza"), anyString());
	}

	@Test
	void muestraTurnoHistoricoSinGrupoSinLanzarNullPointerException() {
		Turno historico = new Turno("Viernes", "19:00", "20:00", null);
		historico.setId(30L);
		Preinscripcion solicitud = new Preinscripcion();
		solicitud.setTurnos(new java.util.LinkedHashSet<>(List.of(historico)));

		List<java.util.Map<String, Object>> resultado = service.turnosSolicitados(solicitud);

		assertEquals(1, resultado.size());
		assertNull(resultado.get(0).get("grupoId"));
		assertEquals("Grupo no disponible", resultado.get(0).get("grupo"));
	}

	private Preinscripcion solicitudEnEspera(String referencia, String correo, List<Turno> seleccion) {
		PlantillaPreinscripcion plantilla = new PlantillaPreinscripcion();
		plantilla.setInstrucciones("Formaliza la inscripción.");
		Preinscripcion solicitud = new Preinscripcion();
		solicitud.setReferencia(referencia);
		solicitud.setNombre("Ana");
		solicitud.setEmail(correo);
		solicitud.setDeporte(Deporte.TAEKWONDO);
		solicitud.setTemporada("2026/2027");
		solicitud.setEstado(EstadoPreinscripcion.EN_LISTA_ESPERA);
		solicitud.setPlantilla(plantilla);
		solicitud.setTurnos(new java.util.LinkedHashSet<>(seleccion));
		return solicitud;
	}

	private PreinscripcionRequest nuevaSolicitud(Deporte deporte) {
		return new PreinscripcionRequest(deporte, List.of(11L), "Ana", "García López", "12345678Z",
				LocalDate.of(1990, 1, 1), "Calle Mayor 1", "612 345 678", "611 222 333",
				"ana@example.com", null, null, null, false, false, true, "Ana García",
				"data:image/png;base64,AAAA");
	}

	private FinalizarPreinscripcionRequest request(Long alumnoId, Set<CampoActualizable> campos,
			DatosAltaDeporte datos) {
		return new FinalizarPreinscripcionRequest(alumnoId == null
				? FinalizarPreinscripcionRequest.AccionAlumno.CREAR_NUEVO
				: FinalizarPreinscripcionRequest.AccionAlumno.VINCULAR_EXISTENTE,
				alumnoId, campos, null, alumnoId == null ? null : DecisionEmail.CONSERVAR_FICHA,
				datos==null?datosTarifaExistente():datos);
	}

	private FinalizarPreinscripcionRequest requestSinDatos(Long alumnoId, Set<CampoActualizable> campos) {
		return new FinalizarPreinscripcionRequest(FinalizarPreinscripcionRequest.AccionAlumno.VINCULAR_EXISTENTE,
				alumnoId, campos, null, DecisionEmail.CONSERVAR_FICHA, null);
	}

	private DatosAltaDeporte datosTarifaExistente() {
		return new DatosAltaDeporte(TipoTarifa.INFANTIL, 32.0, null, null, null, null);
	}

	private DatosAltaDeporte datosTaekwondo() {
		return new DatosAltaDeporte(TipoTarifa.INFANTIL, 28.0, null, null,
				TipoGrado.BLANCO, LocalDate.of(2026, 8, 19));
	}

	private Preinscripcion solicitudPendiente() {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-1");
		p.setEstado(EstadoPreinscripcion.PENDIENTE);
		p.setDeporte(Deporte.TAEKWONDO);
		p.setTemporada("2026-2027");
		p.setGrupo(grupo);
		p.setNombre("Ana");
		p.setApellidos("García López");
		p.setDni("12345678Z");
		p.setFechaNacimiento(LocalDate.of(2014, 4, 12));
		p.setDireccion("Calle Mayor 1");
		p.setTelefono("+34 612 345 678");
		p.setTelefono2("611 222 333");
		p.setEmail("ana@example.com");
		p.setTieneDiscapacidad(false);
		p.setTutorNombre("María García");
		p.setTutorDni("87654321X");
		p.setConsentimientoFotografico(false);
		return p;
	}

	private Alumno alumnoExistente(boolean activo) {
		Alumno a = new Alumno();
		a.setId(3L);
		a.setNif("12345678Z");
		a.setNombre("Ana");
		a.setApellidos("García López");
		a.setFechaNacimiento(java.sql.Date.valueOf(LocalDate.of(2014, 4, 12)));
		a.setDireccion("Calle Mayor 1");
		a.setTelefono(612345678);
		a.setEmail("ana@example.com");
		a.setActivo(activo);
		return a;
	}

	private AlumnoDeporte deporte(Alumno alumno, boolean activo) {
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setAlumno(alumno);
		ad.setDeporte(Deporte.TAEKWONDO);
		ad.setActivo(activo);
		ad.setPrincipal(false);
		ad.setTipoTarifa(TipoTarifa.INFANTIL);
		ad.setCuantiaTarifa(28.0);
		return ad;
	}

	private Turno turno(Long id, String dia) {
		Turno t = new Turno();
		t.setId(id);
		t.setDiaSemana(dia);
		t.setHoraInicio("18:00");
		t.setHoraFin("19:00");
		t.setGrupo(grupo);
		return t;
	}
}
