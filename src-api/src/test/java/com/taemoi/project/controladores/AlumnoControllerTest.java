package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.taemoi.project.controllers.AlumnoController;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.response.RetoDiarioRankingGeneralMiPosicionResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingGeneralResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingMiPosicionResponse;
import com.taemoi.project.dtos.response.RetoDiarioRankingSemanalResponse;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.services.AlumnoAccessControlService;
import com.taemoi.project.services.AlumnoService;
// import com.taemoi.project.utils.FechaUtils;

@ExtendWith(MockitoExtension.class)
class AlumnoControllerTest {

	@Mock
	private AlumnoService alumnoService;

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private GradoRepository gradoRepository;

	@Mock
	private AlumnoAccessControlService alumnoAccessControlService;

	@InjectMocks
	private AlumnoController alumnoController;

	/*
	 * @SuppressWarnings("null")
	 * 
	 * @Test void obtenerAlumnosDTO_Test() { List<Alumno> alumnosList = new
	 * ArrayList<>(); Page<Alumno> alumnosPage = new PageImpl<>(alumnosList);
	 * 
	 * when(alumnoService.obtenerTodosLosAlumnos(any(Pageable.class))).thenReturn(
	 * alumnosPage);
	 * 
	 * ResponseEntity<?> responseEntity = alumnoController.obtenerAlumnosDTO(null,
	 * null, null, null, null);
	 * 
	 * assertTrue(responseEntity.getBody() instanceof Page); assertTrue(((Page<?>)
	 * responseEntity.getBody()).isEmpty()); }
	 */
	@Test
	void obtenerAlumnoPorIdDTO_Test() {
		Long id = 1L;
		AlumnoDTO alumnoDTO = new AlumnoDTO();
		when(alumnoService.obtenerAlumnoPorIdDTO(id)).thenReturn(Optional.of(alumnoDTO));

		ResponseEntity<AlumnoDTO> result = alumnoController.obtenerAlumnoPorIdDTO(id);

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(alumnoDTO, result.getBody());
	}

	/*
	 * @SuppressWarnings("null")
	 * 
	 * @Test void crearAlumno_Test() { AlumnoDTO nuevoAlumnoDTO = new AlumnoDTO();
	 * nuevoAlumnoDTO.setTelefono(123456789); nuevoAlumnoDTO.setFechaNacimiento(new
	 * Date());
	 * 
	 * MultipartFile file = null;
	 * 
	 * ObjectMapper objectMapper = new ObjectMapper(); String alumnoJson; try {
	 * alumnoJson = objectMapper.writeValueAsString(nuevoAlumnoDTO); } catch
	 * (JsonProcessingException e) { alumnoJson = ""; }
	 * 
	 * when(alumnoService.fechaNacimientoValida(any())).thenReturn(true);
	 * when(alumnoService.datosAlumnoValidos(any())).thenReturn(true);
	 * when(alumnoService.asignarCuantiaTarifa(any())).thenReturn(100.0);
	 * when(FechaUtils.calcularEdad(any())).thenReturn(20);
	 * when(alumnoService.asignarCategoriaSegunEdad(anyInt())).thenReturn(new
	 * Categoria()); when(alumnoService.asignarGradoSegunEdad(any())).thenReturn(new
	 * Grado());
	 * when(alumnoRepository.findByNif(anyString())).thenReturn(Optional.empty());
	 * when(alumnoService.crearAlumno(any())).thenReturn(new Alumno());
	 * 
	 * ResponseEntity<?> result = alumnoController.crearAlumno(alumnoJson, file);
	 * 
	 * assertEquals(HttpStatus.CREATED, result.getStatusCode()); }
	 * 
	 */
	/*
	 * @Test void actualizarAlumno_Test() { Long id = 1L; AlumnoDTO
	 * alumnoActualizado = new AlumnoDTO();
	 * when(alumnoService.fechaNacimientoValida(any())).thenReturn(true);
	 * when(alumnoService.datosAlumnoValidos(any())).thenReturn(true);
	 * when(alumnoService.actualizarAlumno(anyLong(), any(), any(),
	 * any())).thenReturn(new Alumno());
	 * 
	 * ResponseEntity<?> result = alumnoController.actualizarAlumno(id, null,
	 * alumnoActualizado);
	 * 
	 * assertEquals(HttpStatus.OK, result.getStatusCode()); }
	 */

	@Test
	void eliminarAlumno_Test() {
		Long id = 1L;
		when(alumnoService.eliminarAlumno(id)).thenReturn(true);

		ResponseEntity<Void> result = alumnoController.eliminarAlumno(id);

		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	void obtenerDocumentosDelAlumno_DebeDevolverForbiddenCuandoNoTieneAcceso() {
		Long alumnoId = 1L;
		when(alumnoAccessControlService.canAccessAlumno(eq(alumnoId), any())).thenReturn(false);

		ResponseEntity<List<Documento>> result = alumnoController.obtenerDocumentosDelAlumno(alumnoId);

		assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
	}

	@Test
	void obtenerDocumentosDelAlumno_DebeDevolverOkCuandoTieneAcceso() {
		Long alumnoId = 1L;
		Documento documento = new Documento();
		documento.setId(99L);
		documento.setNombre("autorizacion.pdf");

		when(alumnoAccessControlService.canAccessAlumno(eq(alumnoId), any())).thenReturn(true);
		when(alumnoService.obtenerDocumentosAlumno(alumnoId)).thenReturn(List.of(documento));

		ResponseEntity<List<Documento>> result = alumnoController.obtenerDocumentosDelAlumno(alumnoId);

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(1, result.getBody().size());
		assertEquals("autorizacion.pdf", result.getBody().get(0).getNombre());
	}

	@Test
	void obtenerRankingSemanalRetoDiario_DebeDevolverForbiddenCuandoNoTieneAcceso() {
		Long alumnoId = 1L;
		when(alumnoAccessControlService.canAccessAlumno(eq(alumnoId), any())).thenReturn(false);

		ResponseEntity<?> result = alumnoController.obtenerRankingSemanalRetoDiario(alumnoId, "TAEKWONDO", 10);

		assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
	}

	@Test
	void obtenerRankingSemanalRetoDiario_DebeDevolverBadRequestSiDeporteEsInvalido() {
		Long alumnoId = 1L;
		when(alumnoAccessControlService.canAccessAlumno(eq(alumnoId), any())).thenReturn(true);

		ResponseEntity<?> result = alumnoController.obtenerRankingSemanalRetoDiario(alumnoId, "OTRO_DEPORTE", 10);

		assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
	}

	@Test
	void obtenerRankingSemanalRetoDiario_DebeDevolverOkCuandoDatosValidos() {
		Long alumnoId = 1L;
		RetoDiarioRankingSemanalResponse ranking = new RetoDiarioRankingSemanalResponse();
		ranking.setDeporte("TAEKWONDO");
		ranking.setAnioIso(2026);
		ranking.setSemanaIso(8);
		ranking.setTotalParticipantes(1);
		ranking.setMiPosicion(new RetoDiarioRankingMiPosicionResponse(1, "Alumno U.", 3, null));

		when(alumnoAccessControlService.canAccessAlumno(eq(alumnoId), any())).thenReturn(true);
		when(alumnoService.obtenerRankingSemanalRetoDiario(eq(alumnoId), eq(com.taemoi.project.entities.Deporte.TAEKWONDO),
				eq(10))).thenReturn(ranking);

		ResponseEntity<?> result = alumnoController.obtenerRankingSemanalRetoDiario(alumnoId, "TAEKWONDO", 10);

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(ranking, result.getBody());
	}

	@Test
	void obtenerRankingGeneralRetoDiario_DebeDevolverForbiddenCuandoNoTieneAcceso() {
		Long alumnoId = 1L;
		when(alumnoAccessControlService.canAccessAlumno(eq(alumnoId), any())).thenReturn(false);

		ResponseEntity<?> result = alumnoController.obtenerRankingGeneralRetoDiario(alumnoId, "TAEKWONDO", 10);

		assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
	}

	@Test
	void obtenerRankingGeneralRetoDiario_DebeDevolverBadRequestSiDeporteEsInvalido() {
		Long alumnoId = 1L;
		when(alumnoAccessControlService.canAccessAlumno(eq(alumnoId), any())).thenReturn(true);

		ResponseEntity<?> result = alumnoController.obtenerRankingGeneralRetoDiario(alumnoId, "OTRO_DEPORTE", 10);

		assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
	}

	@Test
	void obtenerRankingGeneralRetoDiario_DebeDevolverOkCuandoDatosValidos() {
		Long alumnoId = 1L;
		RetoDiarioRankingGeneralResponse ranking = new RetoDiarioRankingGeneralResponse();
		ranking.setDeporte("TAEKWONDO");
		ranking.setTotalParticipantes(2);
		ranking.setMiPosicion(new RetoDiarioRankingGeneralMiPosicionResponse(2, "Alumno U.", 4, 12, 2));

		when(alumnoAccessControlService.canAccessAlumno(eq(alumnoId), any())).thenReturn(true);
		when(alumnoService.obtenerRankingGeneralRetoDiario(eq(alumnoId), eq(com.taemoi.project.entities.Deporte.TAEKWONDO),
				eq(10))).thenReturn(ranking);

		ResponseEntity<?> result = alumnoController.obtenerRankingGeneralRetoDiario(alumnoId, "TAEKWONDO", 10);

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(ranking, result.getBody());
	}
}
