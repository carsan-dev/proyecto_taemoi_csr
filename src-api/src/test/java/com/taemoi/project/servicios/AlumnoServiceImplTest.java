package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoTarifa;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRetoDiarioLogRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.services.impl.AlumnoServiceImpl;
import com.taemoi.project.utils.FechaUtils;

@ExtendWith(MockitoExtension.class)
public class AlumnoServiceImplTest {

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private CategoriaRepository categoriaRepository;

	@Mock
	private GradoRepository gradoRepository;

	@Mock
	private AlumnoDeporteRepository alumnoDeporteRepository;

	@Mock
	private AlumnoRetoDiarioLogRepository alumnoRetoDiarioLogRepository;

	@Mock
	private com.taemoi.project.config.TarifaConfig tarifaConfig;

	@InjectMocks
	private AlumnoServiceImpl alumnoService;

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

		Optional<AlumnoDTO> result = alumnoService.obtenerAlumnoPorIdDTO(1L);

		assertFalse(result.isPresent());
	}

	@Test
	public void testCrearAlumno() {
		Alumno alumno = new Alumno();
		when(alumnoRepository.save(alumno)).thenReturn(alumno);

		Alumno result = alumnoService.crearAlumno(alumno);

		assertNotNull(result);
	}

	/*
	 * @Test public void testActualizarAlumno() { Alumno alumnoExistente = new
	 * Alumno();
	 * when(alumnoRepository.findById(1L)).thenReturn(Optional.of(alumnoExistente));
	 * when(alumnoRepository.save(any(Alumno.class))).thenAnswer(invocation ->
	 * invocation.getArgument(0));
	 * 
	 * AlumnoDTO alumnoDTO = new AlumnoDTO(); Date fechaNacimiento = new Date();
	 * Imagen imagen = new Imagen("nombre", "tipo", new byte[0]);
	 * 
	 * Alumno result = alumnoService.actualizarAlumno(1L, alumnoDTO,
	 * fechaNacimiento, imagen);
	 * 
	 * verify(alumnoRepository).findById(1L);
	 * verify(alumnoRepository).save(any(Alumno.class));
	 * 
	 * assertNotNull(result); }
	 */

	/*
	 * @SuppressWarnings("null")
	 * 
	 * @Test public void testEliminarAlumno() {
	 * when(alumnoRepository.findById(1L)).thenReturn(Optional.of(new Alumno()));
	 * doNothing().when(alumnoRepository).delete(any(Alumno.class));
	 * 
	 * boolean result = alumnoService.eliminarAlumno(1L);
	 * 
	 * assertTrue(result); }
	 */
	@Test
	public void testAsignarCuantiaTarifa() {
		when(tarifaConfig.obtenerCuantia(TipoTarifa.ADULTO)).thenReturn(30.0);
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
		int result = FechaUtils.calcularEdad(new Date());

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

		boolean result = alumnoService.datosAlumnoValidos(alumnoDTO);

		assertTrue(result);
	}

	@Test
	public void testCompletarRetoDiario_CreaLogCuandoNoExiste() {
		Alumno alumno = new Alumno();
		alumno.setId(1L);
		alumno.setActivo(true);
		alumno.setRachaRetoDiario(2);
		alumno.setFechaRetoDiarioCompletado(java.sql.Date.valueOf(LocalDate.now().minusDays(1)));

		when(alumnoRepository.findById(1L)).thenReturn(Optional.of(alumno));
		when(alumnoRepository.save(any(Alumno.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(alumnoRetoDiarioLogRepository.existsByAlumnoIdAndFechaCompletado(any(Long.class), any(LocalDate.class)))
				.thenReturn(false);

		alumnoService.completarRetoDiario(1L);

		verify(alumnoRetoDiarioLogRepository, times(1)).save(any());
	}

	@Test
	public void testCompletarRetoDiario_NoDuplicaLogMismoDia() {
		Alumno alumno = new Alumno();
		alumno.setId(1L);
		alumno.setActivo(true);
		alumno.setRachaRetoDiario(3);
		alumno.setFechaRetoDiarioCompletado(java.sql.Date.valueOf(LocalDate.now()));

		when(alumnoRepository.findById(1L)).thenReturn(Optional.of(alumno));
		when(alumnoRepository.save(any(Alumno.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(alumnoRetoDiarioLogRepository.existsByAlumnoIdAndFechaCompletado(any(Long.class), any(LocalDate.class)))
				.thenReturn(true);

		alumnoService.completarRetoDiario(1L);

		verify(alumnoRetoDiarioLogRepository, never()).save(any());
	}

	@Test
	public void testObtenerRankingSemanalRetoDiario_RetornaRankingYMiPosicion() {
		Alumno alumnoActual = new Alumno();
		alumnoActual.setId(1L);
		alumnoActual.setNombre("aLUmNo");
		alumnoActual.setApellidos("aCTUAL gArcia");
		alumnoActual.setActivo(true);

		Alumno alumnoRival = new Alumno();
		alumnoRival.setId(2L);
		alumnoRival.setNombre("riVAL");
		alumnoRival.setApellidos("uNO perez");
		alumnoRival.setActivo(true);

		AlumnoDeporte deporteActual = new AlumnoDeporte();
		deporteActual.setAlumno(alumnoActual);
		deporteActual.setDeporte(Deporte.TAEKWONDO);
		deporteActual.setActivo(true);

		AlumnoDeporte deporteRival = new AlumnoDeporte();
		deporteRival.setAlumno(alumnoRival);
		deporteRival.setDeporte(Deporte.TAEKWONDO);
		deporteRival.setActivo(true);

		when(alumnoRepository.findById(1L)).thenReturn(Optional.of(alumnoActual));
		when(alumnoDeporteRepository.existsByAlumnoIdAndDeporteAndActivoTrue(1L, Deporte.TAEKWONDO)).thenReturn(true);
		when(alumnoRetoDiarioLogRepository.existsByAnioIsoAndSemanaIso(any(Integer.class), any(Integer.class)))
				.thenReturn(true);
		when(alumnoDeporteRepository.findActivosConAlumnoActivoByDeporte(Deporte.TAEKWONDO))
				.thenReturn(List.of(deporteActual, deporteRival));

		LocalDate hoy = LocalDate.now();
		WeekFields weekFields = WeekFields.ISO;
		int anioIso = hoy.get(weekFields.weekBasedYear());
		int semanaIso = hoy.get(weekFields.weekOfWeekBasedYear());

		AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection scoreActual =
				new AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection() {
					@Override
					public Long getAlumnoId() {
						return 1L;
					}

					@Override
					public Long getDiasCompletados() {
						return 3L;
					}

					@Override
					public LocalDate getUltimaFechaCompletado() {
						return hoy.minusDays(1);
					}
				};

		AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection scoreRival =
				new AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection() {
					@Override
					public Long getAlumnoId() {
						return 2L;
					}

					@Override
					public Long getDiasCompletados() {
						return 5L;
					}

					@Override
					public LocalDate getUltimaFechaCompletado() {
						return hoy;
					}
				};

		when(alumnoRetoDiarioLogRepository.obtenerPuntuacionesSemana(any(Integer.class), any(Integer.class), any(List.class)))
				.thenReturn(List.of(scoreActual, scoreRival));

		var ranking = alumnoService.obtenerRankingSemanalRetoDiario(1L, Deporte.TAEKWONDO, 10);

		assertNotNull(ranking);
		assertEquals("TAEKWONDO", ranking.getDeporte());
		assertEquals(Integer.valueOf(anioIso), ranking.getAnioIso());
		assertEquals(Integer.valueOf(semanaIso), ranking.getSemanaIso());
		assertEquals(Integer.valueOf(2), ranking.getTotalParticipantes());
		assertNotNull(ranking.getMiPosicion());
		assertEquals(Integer.valueOf(2), ranking.getMiPosicion().getPosicion());
		assertEquals(Integer.valueOf(3), ranking.getMiPosicion().getDiasCompletados());
		assertEquals(Integer.valueOf(3), ranking.getMiPosicion().getDiasParaSuperarSiguiente());
		assertEquals("Alumno Actual", ranking.getMiPosicion().getAlias());
		assertEquals("Rival Uno", ranking.getTop().get(0).getAlias());
	}

	@Test
	public void testObtenerRankingSemanalRetoDiario_DesambiguarAliasConInicialSegundoApellido() {
		Alumno alumnoActual = new Alumno();
		alumnoActual.setId(1L);
		alumnoActual.setNombre("CARLOS");
		alumnoActual.setApellidos("sAnchez rUIz");
		alumnoActual.setActivo(true);

		Alumno alumnoRival = new Alumno();
		alumnoRival.setId(2L);
		alumnoRival.setNombre("carlos");
		alumnoRival.setApellidos("sanchez moreno");
		alumnoRival.setActivo(true);

		AlumnoDeporte deporteActual = new AlumnoDeporte();
		deporteActual.setAlumno(alumnoActual);
		deporteActual.setDeporte(Deporte.KICKBOXING);
		deporteActual.setActivo(true);

		AlumnoDeporte deporteRival = new AlumnoDeporte();
		deporteRival.setAlumno(alumnoRival);
		deporteRival.setDeporte(Deporte.KICKBOXING);
		deporteRival.setActivo(true);

		when(alumnoRepository.findById(1L)).thenReturn(Optional.of(alumnoActual));
		when(alumnoDeporteRepository.existsByAlumnoIdAndDeporteAndActivoTrue(1L, Deporte.KICKBOXING)).thenReturn(true);
		when(alumnoRetoDiarioLogRepository.existsByAnioIsoAndSemanaIso(any(Integer.class), any(Integer.class)))
				.thenReturn(true);
		when(alumnoDeporteRepository.findActivosConAlumnoActivoByDeporte(Deporte.KICKBOXING))
				.thenReturn(List.of(deporteActual, deporteRival));

		LocalDate hoy = LocalDate.now();

		AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection scoreActual =
				new AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection() {
					@Override
					public Long getAlumnoId() {
						return 1L;
					}

					@Override
					public Long getDiasCompletados() {
						return 3L;
					}

					@Override
					public LocalDate getUltimaFechaCompletado() {
						return hoy.minusDays(1);
					}
				};

		AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection scoreRival =
				new AlumnoRetoDiarioLogRepository.AlumnoRetoDiarioScoreProjection() {
					@Override
					public Long getAlumnoId() {
						return 2L;
					}

					@Override
					public Long getDiasCompletados() {
						return 5L;
					}

					@Override
					public LocalDate getUltimaFechaCompletado() {
						return hoy;
					}
				};

		when(alumnoRetoDiarioLogRepository.obtenerPuntuacionesSemana(any(Integer.class), any(Integer.class), any(List.class)))
				.thenReturn(List.of(scoreActual, scoreRival));

		var ranking = alumnoService.obtenerRankingSemanalRetoDiario(1L, Deporte.KICKBOXING, 10);

		assertNotNull(ranking);
		assertNotNull(ranking.getMiPosicion());
		assertEquals("Carlos Sanchez R.", ranking.getMiPosicion().getAlias());
		assertEquals("Carlos Sanchez M.", ranking.getTop().get(0).getAlias());
		assertEquals("Carlos Sanchez R.", ranking.getTop().get(1).getAlias());
	}
}
