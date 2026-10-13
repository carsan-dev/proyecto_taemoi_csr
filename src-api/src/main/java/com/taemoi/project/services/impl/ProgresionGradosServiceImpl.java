package com.taemoi.project.services.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.AplicaGrado;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.ProgresionGradosService;

@Service
@Transactional(readOnly = true)
public class ProgresionGradosServiceImpl implements ProgresionGradosService {

	private static final String SIN_NOMBRE = "N/A";

	private final CatalogoDisciplinasService catalogo;
	private final GradoRepository grados;

	public ProgresionGradosServiceImpl(CatalogoDisciplinasService catalogo, GradoRepository grados) {
		this.catalogo = catalogo;
		this.grados = grados;
	}

	@Override
	public boolean tieneProgresion(String codigoDisciplina) {
		return codigoDisciplina != null && catalogo.obtenerPorCodigo(codigoDisciplina).tieneGrados();
	}

	@Override
	public Optional<Grado> buscarGrado(String codigoDisciplina, String codigoGrado) {
		if (codigoDisciplina == null || codigoGrado == null) {
			return Optional.empty();
		}
		Disciplina disciplina = catalogo.obtenerPorCodigo(codigoDisciplina);
		if (!disciplina.tieneGrados()) {
			return Optional.empty();
		}
		return grados.findBySistemaGradosIdAndCodigo(disciplina.getSistemaGrados().getId(), codigoGrado);
	}

	@Override
	public Optional<Grado> obtenerSiguienteGrado(String codigoDisciplina, boolean esMenor, String codigoGrado) {
		if (codigoDisciplina == null || codigoGrado == null) {
			return Optional.empty();
		}
		List<Grado> sistema = gradosDelSistema(codigoDisciplina);
		return siguiente(sistema, buscarEn(sistema, codigoDisciplina, codigoGrado), esMenor);
	}

	@Override
	public List<Grado> obtenerGradosAnteriores(String codigoDisciplina, boolean esMenor, String codigoGrado) {
		if (codigoDisciplina == null || codigoGrado == null) {
			return List.of();
		}
		List<Grado> sistema = gradosDelSistema(codigoDisciplina);
		Grado actual = buscarEn(sistema, codigoDisciplina, codigoGrado);
		// Réplica exacta de GradeProgressionConfig.obtenerGradosAnteriores: recorre las «claves del
		// mapa» (grados con siguiente para ese grupo de edad) hasta el actual.
		// POSIBLE ERROR (heredado, no se corrige aquí): si el actual no es clave (último PUM, un DAN
		// en menores, un PUM en adultos, NEGRO_5_DAN) devuelve TODAS las claves, incluidas algunas
		// posteriores al actual (p. ej. adulto en ROJO_NEGRO_1_PUM -> también NEGRO_1_DAN..NEGRO_4_DAN).
		List<Grado> anteriores = new ArrayList<>();
		for (Grado candidato : sistema) {
			if (siguiente(sistema, candidato, esMenor).isEmpty()) {
				continue;
			}
			if (Objects.equals(candidato.getId(), actual.getId())) {
				break;
			}
			anteriores.add(candidato);
		}
		return anteriores;
	}

	@Override
	public long obtenerMesesRequeridos(String codigoDisciplina, String codigoGrado, boolean esMenor) {
		Integer meses = buscarGrado(codigoDisciplina, codigoGrado)
				.map(g -> esMenor ? g.getMesesMinimosMenor() : g.getMesesMinimosAdulto())
				.orElse(null);
		return meses == null ? Long.MAX_VALUE : meses.longValue();
	}

	@Override
	public long obtenerMesesRequeridosPorEdadCumplida(String codigoDisciplina, int edadCumplida, String codigoGrado) {
		return obtenerMesesRequeridos(codigoDisciplina, codigoGrado,
				catalogo.esMenorParaExamenPorEdadCumplida(edadCumplida));
	}

	@Override
	public boolean tienePeriodoDefinido(String codigoDisciplina, String codigoGrado, boolean esMenor) {
		return obtenerMesesRequeridos(codigoDisciplina, codigoGrado, esMenor) != Long.MAX_VALUE;
	}

	@Override
	public String obtenerBloqueTemario(String codigoDisciplina, String codigoGrado) {
		return buscarGrado(codigoDisciplina, codigoGrado).map(Grado::getBloqueTemario).orElse(null);
	}

	@Override
	public String obtenerNombreVisible(Grado grado) {
		if (grado == null || grado.getNombre() == null) {
			return SIN_NOMBRE;
		}
		return grado.getNombre();
	}

	@Override
	public String obtenerNombreVisible(String codigoDisciplina, String codigoGrado) {
		if (codigoGrado == null) {
			return SIN_NOMBRE;
		}
		List<Grado> sistema = gradosDelSistema(codigoDisciplina);
		return obtenerNombreVisible(buscarEn(sistema, codigoDisciplina, codigoGrado));
	}

	// ---------------------------------------------------------------------------------

	/** Grados del sistema de la disciplina, por orden (sin los que no tienen orden). */
	private List<Grado> gradosDelSistema(String codigoDisciplina) {
		Disciplina disciplina = catalogo.obtenerPorCodigo(codigoDisciplina);
		if (!disciplina.tieneGrados()) {
			throw new IllegalArgumentException("Disciplina sin sistema de grados: " + codigoDisciplina);
		}
		return grados.findBySistemaGradosIdOrderByOrdenAscIdAsc(disciplina.getSistemaGrados().getId()).stream()
				.filter(g -> g.getOrden() != null)
				.toList();
	}

	private static Grado buscarEn(List<Grado> sistema, String codigoDisciplina, String codigoGrado) {
		return sistema.stream()
				.filter(g -> codigoGrado.equals(g.getCodigo()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Grado " + codigoGrado + " no existe en el sistema de " + codigoDisciplina));
	}

	private static Optional<Grado> siguiente(List<Grado> sistema, Grado actual, boolean esMenor) {
		AplicaGrado grupo = esMenor ? AplicaGrado.MENORES : AplicaGrado.ADULTOS;
		boolean actualEsDelGrupo = actual.getAplicaA() == AplicaGrado.TODOS || actual.getAplicaA() == grupo;
		for (Grado candidato : sistema) {
			if (candidato.getOrden() <= actual.getOrden()) {
				continue;
			}
			AplicaGrado aplica = candidato.getAplicaA();
			if (aplica == AplicaGrado.TODOS || (actualEsDelGrupo && aplica == grupo)) {
				return Optional.of(candidato);
			}
		}
		return Optional.empty();
	}
}
