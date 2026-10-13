package com.taemoi.project.services.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.services.CatalogoDisciplinasService;

/**
 * Sin {@code @Transactional} de clase: cada consulta va en la transacción del repositorio, y la
 * regla por edad cumplida no toca la base (no exige organización activa).
 */
@Service
public class CatalogoDisciplinasServiceImpl implements CatalogoDisciplinasService {

	private final DisciplinaRepository disciplinas;

	public CatalogoDisciplinasServiceImpl(DisciplinaRepository disciplinas) {
		this.disciplinas = disciplinas;
	}

	@Override
	public Optional<Disciplina> buscarPorCodigo(String codigo) {
		if (codigo == null) {
			return Optional.empty();
		}
		return disciplinas.findByCodigo(codigo);
	}

	@Override
	public Disciplina obtenerPorCodigo(String codigo) {
		return buscarPorCodigo(codigo)
				.orElseThrow(() -> new IllegalArgumentException("Disciplina no encontrada: " + codigo));
	}

	@Override
	public List<Disciplina> listarActivas() {
		return disciplinas.findByActivaTrueOrderByOrdenAscIdAsc();
	}

	@Override
	public int obtenerEdadAdulto(String codigoDisciplina) {
		if (codigoDisciplina == null) {
			return EDAD_ADULTO_SIN_DISCIPLINA;
		}
		return obtenerPorCodigo(codigoDisciplina).getEdadAdulto();
	}

	@Override
	public boolean esMenorPorAnioNatural(LocalDate fechaNacimiento, String codigoDisciplina) {
		return esMenorPorAnioNatural(fechaNacimiento, codigoDisciplina, LocalDate.now().getYear());
	}

	@Override
	public boolean esMenorPorAnioNaturalFechaNulaComoAdulto(LocalDate fechaNacimiento, String codigoDisciplina) {
		return fechaNacimiento != null && esMenorPorAnioNatural(fechaNacimiento, codigoDisciplina);
	}

	@Override
	public boolean esMenorPorAnioNatural(LocalDate fechaNacimiento, String codigoDisciplina, int anioReferencia) {
		if (fechaNacimiento == null) {
			return true;
		}
		return fechaNacimiento.getYear() + obtenerEdadAdulto(codigoDisciplina) > anioReferencia;
	}

	@Override
	public boolean esMenorParaExamenPorEdadCumplida(int edadCumplida) {
		return edadCumplida < EDAD_CUMPLIDA_ADULTO_EXAMEN;
	}
}
