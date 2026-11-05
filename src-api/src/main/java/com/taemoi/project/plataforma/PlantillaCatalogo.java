package com.taemoi.project.plataforma;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.taemoi.project.entities.AplicaGrado;
import com.taemoi.project.entities.TipoProducto;

/**
 * Fase 4, pieza F4-2 (decisión 392): foto del catálogo de la escuela plantilla y regla de qué se copia a una escuela
 * nueva según las disciplinas elegidas. Sin acceso a datos: la lee {@link PlantillaCatalogoLector}; aquí solo la regla
 * (probada en {@code PlantillaCatalogoTest}).
 *
 * <p>Regla, POR DISCIPLINA elegida:
 * <ul>
 * <li>la disciplina y su sistema de grados con todos sus grados (un sistema compartido por dos disciplinas elegidas se
 * copia una vez);</li>
 * <li>sus categorías y sus tarifas ({@code disciplina_id} = ella);</li>
 * <li>sus productos: los de {@code disciplina_id} = ella y los COMPARTIDOS (sin disciplina y con grado: exámenes y
 * recompensas, que en la plantilla sirven a taekwondo y kickboxing) cuyo código de grado está en su sistema.</li>
 * </ul>
 * Además, siempre, lo COMÚN: categorías y tarifas sin disciplina y productos sin disciplina ni grado (mensualidad,
 * reserva de plaza, licencia federativa genérica, matrícula…). Nada más: ni plantillas de preinscripción, ni tarifas por
 * sede, ni grupos, turnos, alumnos, configuración o contadores.
 */
final class PlantillaCatalogo {

	record Disciplina(Long id, String codigo, String nombre, Long sistemaId, Boolean permiteCompeticion,
			Boolean requiereLicencia, Integer edadAdulto, Integer orden, Boolean activa, Long productoLicenciaId,
			Long productoCompetidorId) {
	}

	record Sistema(Long id, String nombre, String plantillaOrigen) {
	}

	record Grado(Long id, Long sistemaId, String codigo, String nombre, Integer orden, String color1, String color2,
			AplicaGrado aplicaA, Long equivalenteAdultoId, Boolean requiereExamen, Integer mesesMinimosMenor,
			Integer mesesMinimosAdulto, String bloqueTemario, Long productoExamenId, Long productoRecompensaId) {
	}

	record Categoria(Long id, String codigo, String nombre, Long disciplinaId, Integer edadMin, Integer edadMax,
			Integer orden) {
	}

	record Tarifa(Long id, Long disciplinaId, String codigo, String nombre, BigDecimal importe,
			BigDecimal importeAdulto, BigDecimal importeMenor, Integer orden) {
	}

	record Producto(Long id, String concepto, Double precio, String codigo, TipoProducto tipo, Long disciplinaId,
			Long gradoId, Boolean vendibleProfesor) {
	}

	/** Lo que se copia, en el orden de la plantilla; {@code disciplinas} en el orden del catálogo. */
	record Seleccion(List<Disciplina> disciplinas, List<Sistema> sistemas, List<Grado> grados,
			List<Categoria> categorias, List<Tarifa> tarifas, List<Producto> productos) {
	}

	static final String SIN_DISCIPLINAS = "Elige al menos una disciplina.";
	static final String DISCIPLINAS_REPETIDAS = "Hay disciplinas repetidas.";
	static final String DISCIPLINA_AJENA = "La disciplina no está en la plantilla.";

	private final List<Disciplina> disciplinas;
	private final List<Sistema> sistemas;
	private final List<Grado> grados;
	private final List<Categoria> categorias;
	private final List<Tarifa> tarifas;
	private final List<Producto> productos;
	private final Map<Long, Grado> gradoPorId = new HashMap<>();

	/** Cada lista en el orden de la plantilla ({@code disciplinas} por orden e id; el resto por id). */
	PlantillaCatalogo(List<Disciplina> disciplinas, List<Sistema> sistemas, List<Grado> grados,
			List<Categoria> categorias, List<Tarifa> tarifas, List<Producto> productos) {
		this.disciplinas = List.copyOf(disciplinas);
		this.sistemas = List.copyOf(sistemas);
		this.grados = List.copyOf(grados);
		this.categorias = List.copyOf(categorias);
		this.tarifas = List.copyOf(tarifas);
		this.productos = List.copyOf(productos);
		for (Grado grado : grados) {
			gradoPorId.put(grado.id(), grado);
		}
	}

	/** Las disciplinas que se ofrecen: las activas, en el orden del catálogo. */
	List<Disciplina> ofrecidas() {
		return disciplinas.stream().filter(d -> Boolean.TRUE.equals(d.activa())).toList();
	}

	/** {@code GET /api/plataforma/plantilla/disciplinas}: lo que se copiaría de cada una, sin contar lo común. */
	List<DisciplinaPlantillaDTO> resumen() {
		return ofrecidas().stream()
				.map(d -> new DisciplinaPlantillaDTO(d.codigo(), d.nombre(), d.sistemaId() != null,
						tarifas.stream().filter(t -> Objects.equals(t.disciplinaId(), d.id())).count(),
						productos.stream().filter(p -> esDe(p, d)).count()))
				.toList();
	}

	/**
	 * Lo que se copia para esas disciplinas (códigos exactos, de las ofrecidas).
	 *
	 * @throws IllegalArgumentException (400) ninguna, repetidas, o alguna que no se ofrece
	 */
	Seleccion seleccionar(Collection<String> codigos) {
		if (codigos == null || codigos.isEmpty()) {
			throw new IllegalArgumentException(SIN_DISCIPLINAS);
		}
		Set<String> pedidas = new HashSet<>();
		for (String codigo : codigos) {
			if (codigo == null || !pedidas.add(codigo)) {
				throw new IllegalArgumentException(codigo == null ? DISCIPLINA_AJENA : DISCIPLINAS_REPETIDAS);
			}
		}
		List<Disciplina> elegidas = ofrecidas().stream().filter(d -> pedidas.contains(d.codigo())).toList();
		if (elegidas.size() != pedidas.size()) {
			throw new IllegalArgumentException(DISCIPLINA_AJENA);
		}
		Set<Long> idsElegidas = new HashSet<>();
		Set<Long> idsSistemas = new LinkedHashSet<>();
		for (Disciplina d : elegidas) {
			idsElegidas.add(d.id());
			if (d.sistemaId() != null) {
				idsSistemas.add(d.sistemaId());
			}
		}
		List<Producto> productosCopiados = new ArrayList<>();
		for (Producto p : productos) {
			if (esComun(p) || elegidas.stream().anyMatch(d -> esDe(p, d))) {
				productosCopiados.add(p);
			}
		}
		return new Seleccion(elegidas,
				sistemas.stream().filter(s -> idsSistemas.contains(s.id())).toList(),
				grados.stream().filter(g -> idsSistemas.contains(g.sistemaId())).toList(),
				categorias.stream().filter(c -> c.disciplinaId() == null || idsElegidas.contains(c.disciplinaId()))
						.toList(),
				tarifas.stream().filter(t -> t.disciplinaId() == null || idsElegidas.contains(t.disciplinaId())).toList(),
				productosCopiados);
	}

	/** El código del grado de la plantilla ({@code null} si no existe). */
	String codigoDeGrado(Long gradoId) {
		Grado grado = gradoId == null ? null : gradoPorId.get(gradoId);
		return grado == null ? null : grado.codigo();
	}

	/** Producto común: sin disciplina ni grado. Se copia siempre y no cuenta en ninguna disciplina. */
	static boolean esComun(Producto p) {
		return p.disciplinaId() == null && p.gradoId() == null;
	}

	/** Si el producto se copia por la disciplina: suyo, o compartido con un código de grado de su sistema. */
	private boolean esDe(Producto p, Disciplina d) {
		if (p.disciplinaId() != null) {
			return p.disciplinaId().equals(d.id());
		}
		String codigo = codigoDeGrado(p.gradoId());
		return codigo != null && d.sistemaId() != null
				&& grados.stream().anyMatch(g -> d.sistemaId().equals(g.sistemaId()) && codigo.equals(g.codigo()));
	}
}
