package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.taemoi.project.cifrado.CifradorDatos;
import com.taemoi.project.cifrado.DatoCifradoIlegibleException;
import com.taemoi.project.dtos.request.AlumnoSaludRequest;
import com.taemoi.project.dtos.response.AlumnoSaludDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoSalud;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.TipoDiscapacidad;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.AlumnoSaludRepository;

@ExtendWith(MockitoExtension.class)
class AlumnoSaludServiceTest {

	@Mock AlumnoSaludRepository salud;
	@Mock AlumnoRepository alumnos;
	@Mock AuditoriaService auditoria;
	@Mock CifradorDatos cifrador;

	@Test
	void siNoSePuedeAuditarNoSeLee() {
		AlumnoSaludService service = new AlumnoSaludService(salud, alumnos, auditoria, cifrador);
		when(alumnos.buscarPorIdEnOrganizacionActiva(7L)).thenReturn(Optional.of(new Alumno()));
		doThrow(new IllegalStateException("auditoría caída")).when(auditoria)
				.registrarAccesoSensible(any(), anyString(), anyString(), anyLong(), anyString());

		assertThrows(IllegalStateException.class, () -> service.obtener(7L));
		verify(salud, never()).findById(any());
	}

	@Test
	void laLecturaSeAuditaAntesDeLeer() {
		AlumnoSaludService service = new AlumnoSaludService(salud, alumnos, auditoria, cifrador);
		when(alumnos.buscarPorIdEnOrganizacionActiva(7L)).thenReturn(Optional.of(new Alumno()));
		when(salud.findById(7L)).thenReturn(Optional.empty());

		service.obtener(7L);
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.READ), eq("salud"), eq("/api/alumnos/7/salud"),
				eq(7L), anyString());
	}

	@Test
	void alumnoDeOtraEscuelaNoSeAuditaNiSeLee() {
		AlumnoSaludService service = new AlumnoSaludService(salud, alumnos, auditoria, cifrador);
		when(alumnos.buscarPorIdEnOrganizacionActiva(9L)).thenReturn(Optional.empty());

		assertThrows(AlumnoNoEncontradoException.class, () -> service.obtener(9L));
		verifyNoInteractions(auditoria, salud, cifrador);
	}

	/** Pieza E7-2 (D7-14): la lectura del profesor también falla si no se puede registrar. */
	@Test
	void comoProfesor_siNoSePuedeAuditarNoSeLee() {
		AlumnoSaludService service = new AlumnoSaludService(salud, alumnos, auditoria, cifrador);
		when(alumnos.buscarPorIdEnOrganizacionActiva(7L)).thenReturn(Optional.of(new Alumno()));
		doThrow(new IllegalStateException("auditoría caída")).when(auditoria)
				.registrarAccesoSensible(any(), anyString(), anyString(), anyLong(), anyString());

		assertThrows(IllegalStateException.class, () -> service.obtenerComoProfesor(7L));
		verify(salud, never()).findById(any());
	}

	/** Mismo módulo; la ruta del profesor en el evento y un resumen solo con el id. */
	@Test
	void comoProfesor_registraLaRutaDelProfesor() {
		AlumnoSaludService service = new AlumnoSaludService(salud, alumnos, auditoria, cifrador);
		when(alumnos.buscarPorIdEnOrganizacionActiva(7L)).thenReturn(Optional.of(new Alumno()));
		when(salud.findById(7L)).thenReturn(Optional.empty());

		service.obtenerComoProfesor(7L);
		verify(auditoria).registrarAccesoSensible(eq(AuditoriaAccion.READ), eq("salud"),
				eq("/api/profesor/alumnos/7/salud"), eq(7L), eq("Lectura de datos de salud del alumno #7 (profesor)"));
	}

	// ------------------------------------------------------------------ discapacidad

	private static final String CONTEXTO_TIPOS = "alumno_salud.discapacidad_tipos#7";
	private static final String CONTEXTO_OTRA = "alumno_salud.discapacidad_otra#7";

	/** Cifrador de mentira: {@code c(<texto>)} para ver qué se cifra; {@code null} sigue siendo {@code null}. */
	private AlumnoSaludService conCifradorDePrueba(Alumno alumno) {
		lenient().when(alumnos.buscarPorIdEnOrganizacionActiva(7L)).thenReturn(Optional.of(alumno));
		lenient().when(salud.findById(7L)).thenReturn(Optional.empty());
		lenient().when(salud.saveAndFlush(any(AlumnoSalud.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(cifrador.cifrar(any(), anyString())).thenAnswer(inv -> {
			String texto = inv.getArgument(0);
			return texto == null ? null : "c(" + texto + ")";
		});
		lenient().when(cifrador.descifrar(any(), anyString())).thenAnswer(inv -> {
			String guardado = inv.getArgument(0);
			return guardado == null ? null : guardado.substring(2, guardado.length() - 1);
		});
		return new AlumnoSaludService(salud, alumnos, auditoria, cifrador);
	}

	private static AlumnoSaludRequest peticion(Boolean tiene, List<TipoDiscapacidad> tipos, String otra) {
		return new AlumnoSaludRequest(null, null, null, null, null, null, tiene, tipos, otra);
	}

	@Test
	void guardaLosTipos_sinDuplicados_enElOrdenDelEnum_ligadosASuColumna() {
		AlumnoSaludService service = conCifradorDePrueba(new Alumno());

		AlumnoSaludDTO dto = service.guardar(7L, peticion(true, List.of(TipoDiscapacidad.OTRA, TipoDiscapacidad.FISICA,
				TipoDiscapacidad.OTRA, TipoDiscapacidad.AUDITIVA), "  Texto ficticio  "));

		verify(cifrador).cifrar("FISICA,AUDITIVA,OTRA", CONTEXTO_TIPOS);
		verify(cifrador).cifrar("Texto ficticio", CONTEXTO_OTRA);
		assertEquals(List.of(TipoDiscapacidad.FISICA, TipoDiscapacidad.AUDITIVA, TipoDiscapacidad.OTRA),
				dto.discapacidadTipos());
		assertEquals("Texto ficticio", dto.discapacidadOtra());
	}

	@Test
	void sinTipos_nuncaCifraUnaCadenaVacia() {
		AlumnoSaludService service = conCifradorDePrueba(new Alumno());

		service.guardar(7L, peticion(true, List.of(), "Se descarta"));

		verify(cifrador).cifrar(isNull(), eq(CONTEXTO_TIPOS));
		verify(cifrador).cifrar(isNull(), eq(CONTEXTO_OTRA));
		verify(cifrador, never()).cifrar(eq(""), anyString());
	}

	@Test
	void elementoNuloEnUnaLista_400_sinTocarNada() {
		Alumno alumno = new Alumno();
		AlumnoSaludService service = conCifradorDePrueba(alumno);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.guardar(7L, peticion(true, Arrays.asList(TipoDiscapacidad.VISUAL, null), null)));

		assertFalse(error.getMessage().contains("VISUAL"));
		assertFalse(alumno.getTieneDiscapacidad());
		verify(salud, never()).saveAndFlush(any());
	}

	@Test
	void conBooleanoNulo_noTocaNiElAlumnoNiLasColumnas() {
		Alumno alumno = new Alumno();
		alumno.setTieneDiscapacidad(true);
		AlumnoSalud fila = new AlumnoSalud(7L);
		fila.setDiscapacidadTiposCifrados("c(VISUAL)");
		fila.setDiscapacidadOtraCifrada("c(Algo)");
		AlumnoSaludService service = conCifradorDePrueba(alumno);
		when(salud.findById(7L)).thenReturn(Optional.of(fila));

		AlumnoSaludDTO dto = service.guardar(7L, peticion(null, null, null));

		assertTrue(alumno.getTieneDiscapacidad());
		assertEquals("c(VISUAL)", fila.getDiscapacidadTiposCifrados());
		assertEquals("c(Algo)", fila.getDiscapacidadOtraCifrada());
		assertEquals(List.of(TipoDiscapacidad.VISUAL), dto.discapacidadTipos());
		assertEquals("Algo", dto.discapacidadOtra());
	}

	@Test
	void unaFichaAnonimizadaNoSeModifica_laMismaExcepcionQueElResto() {
		Alumno alumno = new Alumno();
		alumno.setAnonimizadoEn(LocalDateTime.now());
		AlumnoSaludService service = conCifradorDePrueba(alumno);

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> service.guardar(7L, peticion(true, List.of(TipoDiscapacidad.FISICA), null)));

		assertEquals(Alumno.ALUMNO_ANONIMIZADO_NO_SE_MODIFICA, error.getMessage());
		assertFalse(alumno.getTieneDiscapacidad());
		verify(salud, never()).saveAndFlush(any());
		verifyNoInteractions(cifrador);
	}

	@Test
	void laLecturaDeUnaFichaAnonimizadaNoCambia() {
		Alumno alumno = new Alumno();
		alumno.setAnonimizadoEn(LocalDateTime.now());
		AlumnoSaludService service = conCifradorDePrueba(alumno);

		assertFalse(service.obtener(7L).registrado());
	}

	/** Un nombre guardado que ya no está en el enum falla sin repetirlo (valueOf lo llevaría al mensaje). */
	@Test
	void unTipoGuardadoDesconocido_noRepiteElValor() {
		Alumno alumno = new Alumno();
		alumno.setTieneDiscapacidad(true);
		AlumnoSalud fila = new AlumnoSalud(7L);
		fila.setDiscapacidadTiposCifrados("c(VALOR_FICTICIO)");
		AlumnoSaludService service = conCifradorDePrueba(alumno);
		when(salud.findById(7L)).thenReturn(Optional.of(fila));

		DatoCifradoIlegibleException error = assertThrows(DatoCifradoIlegibleException.class, () -> service.obtener(7L));

		assertFalse(error.getMessage().contains("VALOR_FICTICIO"));
	}

	@Test
	void limpiarDiscapacidad_noLeeNiDescifraNiAudita() {
		AlumnoSaludService service = new AlumnoSaludService(salud, alumnos, auditoria, cifrador);

		service.limpiarDiscapacidad(7L);

		verify(salud).limpiarDiscapacidad(eq(7L), any(Instant.class));
		verify(salud, never()).findById(any());
		verifyNoInteractions(alumnos, auditoria, cifrador);
	}

	@Test
	void elToStringDeLaPeticionNoLlevaLosCamposDeDiscapacidad() {
		String texto = new AlumnoSaludRequest(null, "Descripcion ficticia", null, null, null, null, true,
				List.of(TipoDiscapacidad.INTELECTUAL), "Otra ficticia").toString();

		assertFalse(texto.contains("INTELECTUAL"));
		assertFalse(texto.contains("Otra ficticia"));
		assertFalse(texto.contains("true"));
		assertFalse(texto.contains("Descripcion ficticia"));
	}

	@Test
	void elDtoNuncaDevuelveUnaListaNula() {
		AlumnoSaludDTO vacio = AlumnoSaludDTO.vacio(7L, true);

		assertEquals(List.of(), vacio.discapacidadTipos());
		assertSame(Boolean.TRUE, vacio.tieneDiscapacidad());
		assertEquals(List.of(), new AlumnoSaludDTO(7L, true, null, null, null, null, null, null, null, false, null, null)
				.discapacidadTipos());
	}
}
