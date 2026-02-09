package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.taemoi.project.controllers.GrupoController;
import com.taemoi.project.dtos.response.TurnoCortoDTO;
import com.taemoi.project.services.AlumnoAccessControlService;
import com.taemoi.project.services.GrupoService;

@ExtendWith(MockitoExtension.class)
class GrupoControllerTest {

	@Mock
	private GrupoService grupoService;

	@Mock
	private AlumnoAccessControlService alumnoAccessControlService;

	@InjectMocks
	private GrupoController grupoController;

	@Test
	void obtenerTurnosDelAlumnoEnGrupo_DebeDevolverForbiddenCuandoNoTieneAcceso() {
		Long grupoId = 1L;
		Long alumnoId = 10L;
		when(alumnoAccessControlService.canAccessAlumno(eq(alumnoId), any())).thenReturn(false);

		ResponseEntity<List<TurnoCortoDTO>> result = grupoController.obtenerTurnosDelAlumnoEnGrupo(grupoId, alumnoId);

		assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
	}

	@Test
	void obtenerTurnosDelAlumnoEnGrupo_DebeDevolverOkCuandoTieneAcceso() {
		Long grupoId = 1L;
		Long alumnoId = 10L;
		TurnoCortoDTO turno = new TurnoCortoDTO();
		turno.setId(15L);

		when(alumnoAccessControlService.canAccessAlumno(eq(alumnoId), any())).thenReturn(true);
		when(grupoService.obtenerTurnosDelAlumnoEnGrupo(grupoId, alumnoId)).thenReturn(List.of(turno));

		ResponseEntity<List<TurnoCortoDTO>> result = grupoController.obtenerTurnosDelAlumnoEnGrupo(grupoId, alumnoId);

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(1, result.getBody().size());
		assertEquals(15L, result.getBody().get(0).getId());
	}
}
