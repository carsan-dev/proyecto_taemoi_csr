package com.taemoi.project.catalogo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.AplicaGrado;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.SistemaGrados;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.ProgresionGradosService;
import com.taemoi.project.services.TarifasService;
import com.taemoi.project.services.impl.CatalogoDisciplinasServiceImpl;
import com.taemoi.project.services.impl.ProgresionGradosServiceImpl;
import com.taemoi.project.services.impl.TarifasServiceImpl;

/**
 * Catálogo de Taemoi (los datos de V23) en memoria para tests unitarios: los servicios reales
 * ({@link CatalogoDisciplinasServiceImpl}, {@link ProgresionGradosServiceImpl}) sobre repositorios
 * simulados. Los tests de integración usan la base real; este catálogo solo evita levantar MySQL en
 * los tests de servicios que antes usaban las clases de configuración.
 */
public final class CatalogoTaemoiEnMemoria {

	private static final long SISTEMA_TKD = 1L;
	private static final long SISTEMA_KB = 2L;

	/** codigo, nombre, orden, aplica_a, meses menor, meses adulto, bloque (V23). */
	private static final Object[][] TAEKWONDO = {
			{ "BLANCO", "Blanco", 1, "TODOS", 2, 3, "b01_inicio_a_amarillo" },
			{ "BLANCO_AMARILLO", "Blanco-Amarillo", 2, "MENORES", 2, null, "b01_inicio_a_amarillo" },
			{ "AMARILLO", "Amarillo", 3, "TODOS", 3, 5, "b02_amarillo_a_naranja" },
			{ "AMARILLO_NARANJA", "Amarillo-Naranja", 4, "MENORES", 3, null, "b02_amarillo_a_naranja" },
			{ "NARANJA", "Naranja", 5, "TODOS", 4, 6, "b03_naranja_a_verde" },
			{ "NARANJA_VERDE", "Naranja-Verde", 6, "MENORES", 4, null, "b03_naranja_a_verde" },
			{ "VERDE", "Verde", 7, "TODOS", 6, 8, "b04_verde_a_azul" },
			{ "VERDE_AZUL", "Verde-Azul", 8, "MENORES", 6, null, "b04_verde_a_azul" },
			{ "AZUL", "Azul", 9, "TODOS", 8, 10, "b05_azul_a_rojo" },
			{ "AZUL_ROJO", "Azul-Rojo", 10, "MENORES", 10, null, "b05_azul_a_rojo" },
			{ "ROJO", "Rojo", 11, "TODOS", 12, 12, "b06_rojo_a_negro_1" },
			{ "ROJO_NEGRO_1_PUM", "Rojo-Negro-1-Pum", 12, "MENORES", 24, null, "b07_negro_1_a_negro_2" },
			{ "ROJO_NEGRO_2_PUM", "Rojo-Negro-2-Pum", 13, "MENORES", 36, null, "b08_negro_2_a_negro_3" },
			{ "ROJO_NEGRO_3_PUM", "Rojo-Negro-3-Pum", 14, "MENORES", null, null, "b09_negro_3_a_negro_4" },
			{ "NEGRO_1_DAN", "Negro-1-Dan", 15, "ADULTOS", null, 24, "b07_negro_1_a_negro_2" },
			{ "NEGRO_2_DAN", "Negro-2-Dan", 16, "ADULTOS", null, 36, "b08_negro_2_a_negro_3" },
			{ "NEGRO_3_DAN", "Negro-3-Dan", 17, "ADULTOS", null, 48, "b09_negro_3_a_negro_4" },
			{ "NEGRO_4_DAN", "Negro-4-Dan", 18, "ADULTOS", null, 60, "b10_negro_4_a_negro_5" },
			{ "NEGRO_5_DAN", "Negro-5-Dan", 19, "ADULTOS", null, null, "b11_negro_5_a_maximo" } };

	private final DisciplinaRepository disciplinas = mock(DisciplinaRepository.class);
	private final GradoRepository grados = mock(GradoRepository.class);
	private final CatalogoDisciplinasService catalogo = new CatalogoDisciplinasServiceImpl(disciplinas);
	private final ProgresionGradosService progresion = new ProgresionGradosServiceImpl(catalogo, grados);
	private final List<Grado> todos = new ArrayList<>();
	private final TarifaRepository tarifaRepository = mock(TarifaRepository.class);
	private final TarifasService tarifas = new TarifasServiceImpl(tarifaRepository);
	private final List<Tarifa> listaTarifas = new ArrayList<>();

	public CatalogoTaemoiEnMemoria() {
		SistemaGrados tkd = sistema(SISTEMA_TKD, "TAEKWONDO");
		SistemaGrados kb = sistema(SISTEMA_KB, "KICKBOXING");
		List<Disciplina> lista = List.of(disciplina(1L, "TAEKWONDO", tkd, true, 14, 1),
				disciplina(2L, "KICKBOXING", kb, true, 15, 2), disciplina(3L, "PILATES", null, false, 14, 3),
				disciplina(4L, "DEFENSA_PERSONAL_FEMENINA", null, false, 14, 4));
		long id = 100;
		for (Object[] fila : TAEKWONDO) {
			todos.add(grado(++id, tkd, fila, (String) fila[1], (Integer) fila[2], AplicaGrado.valueOf((String) fila[3])));
		}
		// Copia de kickboxing (V23): ROJO = «MARRÓN», DAN tras ROJO, PUM al final, intermedios y PUM nunca destino.
		Map<String, Integer> desplazamiento = Map.of("NEGRO", -3, "ROJO_NEGRO", 5);
		for (Object[] fila : TAEKWONDO) {
			String codigo = (String) fila[0];
			int orden = (Integer) fila[2];
			if (codigo.startsWith("ROJO_NEGRO")) {
				orden += desplazamiento.get("ROJO_NEGRO");
			} else if (codigo.startsWith("NEGRO")) {
				orden += desplazamiento.get("NEGRO");
			}
			AplicaGrado aplica = "MENORES".equals(fila[3]) ? AplicaGrado.NINGUNO : AplicaGrado.TODOS;
			todos.add(grado(++id, kb, fila, codigo.equals("ROJO") ? "MARRÓN" : (String) fila[1], orden, aplica));
		}

		// Tarifas de V24: codigo, importe, importe adulto, importe menor, disciplina.
		Object[][] filasTarifa = { { "FAMILIAR", "0.00", null, null, 0 }, { "PADRES_HIJOS", "0.00", "28.00", "26.00", 0 },
				{ "ADULTO_GRUPO", "20.00", null, null, 0 }, { "INFANTIL_GRUPO", "20.00", null, null, 0 },
				{ "HERMANOS", "26.00", null, null, 0 }, { "INFANTIL", "28.00", null, null, 0 },
				{ "ADULTO", "30.00", null, null, 0 }, { "KICKBOXING", "30.00", null, null, 1 },
				{ "PILATES", "30.00", null, null, 2 }, { "DEFENSA_PERSONAL_FEMENINA", "25.00", null, null, 3 } };
		long idTarifa = 0;
		for (Object[] fila : filasTarifa) {
			Tarifa tarifa = new Tarifa();
			ReflectionTestUtils.setField(tarifa, "id", ++idTarifa);
			tarifa.setCodigo((String) fila[0]);
			tarifa.setNombre((String) fila[0]);
			tarifa.setImporte(new java.math.BigDecimal((String) fila[1]));
			tarifa.setImporteAdulto(fila[2] == null ? null : new java.math.BigDecimal((String) fila[2]));
			tarifa.setImporteMenor(fila[3] == null ? null : new java.math.BigDecimal((String) fila[3]));
			tarifa.setDisciplina(lista.get((Integer) fila[4]));
			listaTarifas.add(tarifa);
		}
		lenient().when(tarifaRepository.findByCodigo(any())).thenAnswer(inv -> listaTarifas.stream()
				.filter(t -> t.getCodigo().equals(inv.getArgument(0))).findFirst());
		lenient().when(tarifaRepository.findByActivaTrueOrderByIdAsc()).thenReturn(listaTarifas);

		lenient().when(disciplinas.findByCodigo(any())).thenAnswer(inv -> lista.stream()
				.filter(d -> d.getCodigo().equals(inv.getArgument(0))).findFirst());
		lenient().when(disciplinas.findAllByOrderByOrdenAscIdAsc()).thenReturn(lista);
		lenient().when(disciplinas.findByActivaTrueOrderByOrdenAscIdAsc()).thenReturn(lista);
		lenient().when(grados.findBySistemaGradosIdOrderByOrdenAscIdAsc(anyLong())).thenAnswer(inv -> del(inv.getArgument(0))
				.sorted(Comparator.comparing(Grado::getOrden).thenComparing(Grado::getId)).toList());
		lenient().when(grados.findBySistemaGradosIdOrderByIdAsc(anyLong())).thenAnswer(inv -> del(inv.getArgument(0))
				.sorted(Comparator.comparing(Grado::getId)).toList());
		lenient().when(grados.findBySistemaGradosIdAndCodigo(anyLong(), any())).thenAnswer(inv -> del(inv.getArgument(0))
				.filter(g -> g.getCodigo().equals(inv.getArgument(1))).findFirst());
		lenient().when(grados.findById(anyLong())).thenAnswer(inv -> todos.stream()
				.filter(g -> g.getId().equals(inv.getArgument(0))).findFirst());
	}

	public CatalogoDisciplinasService catalogo() {
		return catalogo;
	}

	public ProgresionGradosService progresion() {
		return progresion;
	}

	/** {@link TarifasServiceImpl} real sobre las 10 tarifas de Taemoi (V24). */
	public TarifasService tarifas() {
		return tarifas;
	}

	/** Grado del catálogo con ese código en el sistema de la disciplina. */
	public Grado grado(String codigoDisciplina, String codigoGrado) {
		return progresion.buscarGrado(codigoDisciplina, codigoGrado).orElseThrow();
	}

	public Optional<Grado> grado(String codigoDisciplina, TipoGrado tipo) {
		return progresion.buscarGrado(codigoDisciplina, tipo.name());
	}

	private java.util.stream.Stream<Grado> del(Long sistema) {
		return todos.stream().filter(g -> g.getSistemaGrados().getId().equals(sistema));
	}

	private static SistemaGrados sistema(long id, String plantilla) {
		SistemaGrados sistema = new SistemaGrados();
		ReflectionTestUtils.setField(sistema, "id", id);
		sistema.setNombre(plantilla);
		sistema.setPlantillaOrigen(plantilla);
		return sistema;
	}

	private static Disciplina disciplina(long id, String codigo, SistemaGrados sistema, boolean conGrados,
			int edadAdulto, int orden) {
		Disciplina disciplina = new Disciplina();
		ReflectionTestUtils.setField(disciplina, "id", id);
		disciplina.setCodigo(codigo);
		disciplina.setNombre(codigo);
		disciplina.setSistemaGrados(sistema);
		disciplina.setPermiteCompeticion(conGrados);
		disciplina.setRequiereLicencia(conGrados);
		disciplina.setEdadAdulto(edadAdulto);
		disciplina.setOrden(orden);
		return disciplina;
	}

	private static Grado grado(long id, SistemaGrados sistema, Object[] fila, String nombre, int orden,
			AplicaGrado aplica) {
		Grado grado = new Grado();
		grado.setId(id);
		grado.setSistemaGrados(sistema);
		grado.setCodigo((String) fila[0]);
		grado.setTipoGrado(TipoGrado.valueOf((String) fila[0]));
		grado.setNombre(nombre);
		grado.setOrden(orden);
		grado.setAplicaA(aplica);
		grado.setMesesMinimosMenor((Integer) fila[4]);
		grado.setMesesMinimosAdulto((Integer) fila[5]);
		grado.setBloqueTemario((String) fila[6]);
		return grado;
	}
}
