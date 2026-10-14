package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.taemoi.project.cifrado.CifradorDatos;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuditoriaAccion;
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
}
