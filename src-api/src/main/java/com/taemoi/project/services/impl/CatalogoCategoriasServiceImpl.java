package com.taemoi.project.services.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.services.CatalogoCategoriasService;
import com.taemoi.project.services.CatalogoDisciplinasService;

@Service
@Transactional(readOnly = true)
public class CatalogoCategoriasServiceImpl implements CatalogoCategoriasService {

	private final CategoriaRepository categorias;
	private final CatalogoDisciplinasService disciplinas;

	public CatalogoCategoriasServiceImpl(CategoriaRepository categorias, CatalogoDisciplinasService disciplinas) {
		this.categorias = categorias;
		this.disciplinas = disciplinas;
	}

	@Override
	public boolean asignaCategoriaPorEdad(String codigoDisciplina) {
		return !deLaDisciplina(codigoDisciplina).isEmpty();
	}

	@Override
	public Optional<Categoria> categoriaPorEdad(String codigoDisciplina, int edad) {
		return deLaDisciplina(codigoDisciplina).stream().filter(c -> c.incluyeEdad(edad)).findFirst();
	}

	private List<Categoria> deLaDisciplina(String codigoDisciplina) {
		Optional<Disciplina> disciplina = disciplinas.buscarPorCodigo(codigoDisciplina);
		return disciplina.isEmpty() ? List.of()
				: categorias.findByDisciplinaIdOrderByOrdenAscIdAsc(disciplina.get().getId());
	}
}
