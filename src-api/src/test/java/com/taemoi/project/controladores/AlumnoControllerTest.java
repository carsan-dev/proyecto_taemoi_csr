package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.servicios.AlumnoService;

@SpringBootTest
class AlumnoControllerTest {

	@Mock
	private AlumnoService alumnoService;

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private GradoRepository gradoRepository;

	@InjectMocks
	private AlumnoController alumnoController;

	@Test
	void obtenerAlumnosDTO_Test() {
	    List<Alumno> alumnosList = new ArrayList<>();
	    Page<Alumno> alumnosPage = new PageImpl<>(alumnosList);

	    when(alumnoService.obtenerTodosLosAlumnos(any(Pageable.class))).thenReturn(alumnosPage);

	    ResponseEntity<?> responseEntity = alumnoController.obtenerAlumnosDTO(null, null, null, null, null);

	    assertTrue(responseEntity.getBody() instanceof Page);
	    assertTrue(((Page<?>) responseEntity.getBody()).isEmpty());
	}
	
	@Test
	void obtenerAlumnoPorIdDTO_Test() {
	    Long id = 1L;
	    AlumnoDTO alumnoDTO = new AlumnoDTO();
	    when(alumnoService.obtenerAlumnoDTOPorId(id)).thenReturn(Optional.of(alumnoDTO));

	    ResponseEntity<AlumnoDTO> result = alumnoController.obtenerAlumnoPorIdDTO(id);

	    assertEquals(HttpStatus.OK, result.getStatusCode());
	    assertEquals(alumnoDTO, result.getBody());
	}

	@Test
	void crearAlumno_Test() {
	    AlumnoDTO nuevoAlumnoDTO = new AlumnoDTO();
	    nuevoAlumnoDTO.setTelefono(123456789);
	    when(alumnoService.fechaNacimientoValida(any())).thenReturn(true);
	    when(alumnoService.datosAlumnoValidos(any())).thenReturn(true);
	    when(alumnoService.asignarCuantiaTarifa(any())).thenReturn(100.0);
	    when(alumnoService.calcularEdad(any())).thenReturn(20);
	    when(alumnoService.asignarCategoriaSegunEdad(anyInt())).thenReturn(new Categoria());
	    when(alumnoService.asignarGradoSegunEdad(any())).thenReturn(new Grado());
	    when(alumnoRepository.findByNif(null)).thenReturn(Optional.empty());
	    when(alumnoService.crearAlumno(any())).thenReturn(new Alumno());

	    ResponseEntity<?> result = alumnoController.crearAlumno(nuevoAlumnoDTO, null);

	    assertEquals(HttpStatus.CREATED, result.getStatusCode());
	}

	@Test
	void actualizarAlumno_Test() {
	    Long id = 1L;
	    AlumnoDTO alumnoActualizado = new AlumnoDTO();
	    when(alumnoService.fechaNacimientoValida(any())).thenReturn(true);
	    when(alumnoService.datosAlumnoValidos(any())).thenReturn(true);
	    when(alumnoService.actualizarAlumno(anyLong(), any(), any())).thenReturn(new Alumno());

	    ResponseEntity<AlumnoDTO> result = alumnoController.actualizarAlumno(id, alumnoActualizado);

	    assertEquals(HttpStatus.OK, result.getStatusCode());
	}


	@Test
	void eliminarAlumno_Test() {
		Long id = 1L;
		when(alumnoService.eliminarAlumno(id)).thenReturn(true);

		ResponseEntity<Void> result = alumnoController.eliminarAlumno(id);

		assertEquals(HttpStatus.OK, result.getStatusCode());
	}
}