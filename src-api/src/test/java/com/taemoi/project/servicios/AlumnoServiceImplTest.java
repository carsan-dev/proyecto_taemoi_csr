package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.entidades.TipoTarifa;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.CategoriaRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.servicios.impl.AlumnoServiceImpl;

@SpringBootTest
public class AlumnoServiceImplTest {

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private CategoriaRepository categoriaRepository;

	@Mock
	private GradoRepository gradoRepository;

	@InjectMocks
	private AlumnoServiceImpl alumnoService;
	
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

	@Test
	public void testObtenerTodosLosAlumnos() {
		Pageable pageable = Pageable.unpaged();
		when(alumnoRepository.findAll(pageable)).thenReturn(Page.empty());

		Page<Alumno> result = alumnoService.obtenerTodosLosAlumnos(pageable);

		assertNotNull(result);
		assertEquals(0, result.getTotalElements());
	}

	@Test
    public void testObtenerAlumnoPorId() {
        when(alumnoRepository.findById(1L)).thenReturn(Optional.of(new Alumno()));

        Optional<Alumno> result = alumnoService.obtenerAlumnoPorId(1L);

        assertTrue(result.isPresent());
    }

	@Test
	public void testObtenerAlumnoDTOPorId() {
	    when(alumnoRepository.findById(1L)).thenReturn(Optional.empty());

	    Optional<AlumnoDTO> result = alumnoService.obtenerAlumnoDTOPorId(1L);

	    assertFalse(result.isPresent());
	}

	@Test
	public void testCrearAlumno() {
		Alumno alumno = new Alumno();
		when(alumnoRepository.save(alumno)).thenReturn(alumno);

		Alumno result = alumnoService.crearAlumno(alumno);

		assertNotNull(result);
	}

	@Test
    public void testActualizarAlumno() {
        when(alumnoRepository.findById(1L)).thenReturn(Optional.of(new Alumno()));
        when(alumnoRepository.save(any(Alumno.class))).thenReturn(new Alumno());

        AlumnoDTO alumnoDTO = new AlumnoDTO();
        Date fechaNacimiento = new Date();
        Alumno result = alumnoService.actualizarAlumno(1L, alumnoDTO, fechaNacimiento);

        assertNotNull(result);
    }

	@Test
    public void testEliminarAlumno() {
        when(alumnoRepository.findById(1L)).thenReturn(Optional.of(new Alumno()));
        doNothing().when(alumnoRepository).delete(any(Alumno.class));

        boolean result = alumnoService.eliminarAlumno(1L);

        assertTrue(result);
    }

	@Test
	public void testAsignarCuantiaTarifa() {
		double result = alumnoService.asignarCuantiaTarifa(TipoTarifa.ADULTO);

		assertEquals(30.0, result, 0.0);
	}

	@Test
    public void testAsignarCategoriaSegunEdad() {
        when(categoriaRepository.findByNombre(anyString())).thenReturn(new Categoria());

        Categoria result = alumnoService.asignarCategoriaSegunEdad(10);

        assertNotNull(result);
    }

	@Test
	public void testAsignarGradoSegunEdad() {
	    when(gradoRepository.findByTipoGrado(any(TipoGrado.class))).thenReturn(new Grado());

	    AlumnoDTO alumnoDTO = new AlumnoDTO();
	    alumnoDTO.setFechaNacimiento(new Date());

	    Grado result = alumnoService.asignarGradoSegunEdad(alumnoDTO);

	    assertNotNull(result);
	}

	@Test
	public void testCalcularEdad() {
		int result = alumnoService.calcularEdad(new Date());

		assertTrue(result >= 0);
	}

	@Test
	public void testFechaNacimientoValida() {
	    Calendar cal = Calendar.getInstance();
	    cal.add(Calendar.YEAR, -20);
	    Date fechaNacimientoValida = cal.getTime();

	    boolean result = alumnoService.fechaNacimientoValida(fechaNacimientoValida);

	    assertTrue(result);
	}

    @Test
    public void testDatosAlumnoValidos() {
        AlumnoDTO alumnoDTO = new AlumnoDTO();
        alumnoDTO.setNombre("Juan");
        alumnoDTO.setApellidos("Perez");
        alumnoDTO.setFechaNacimiento(new Date());
        alumnoDTO.setNumeroExpediente(12345);
        alumnoDTO.setNif("12345678A");
        alumnoDTO.setDireccion("Calle Principal");
        alumnoDTO.setEmail("juan@example.com");
        alumnoDTO.setTelefono(123456789);
        alumnoDTO.setTipoTarifa(TipoTarifa.ADULTO);
        alumnoDTO.setFechaAlta(new Date());

        when(alumnoRepository.save(any(Alumno.class))).thenReturn(new Alumno());

        boolean result = alumnoService.datosAlumnoValidos(alumnoDTO);

        assertTrue(result);
    }
}