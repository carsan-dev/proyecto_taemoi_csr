package com.taemoi.project.services.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.response.CatalogoDTO;
import com.taemoi.project.dtos.response.CatalogoPublicoDTO;
import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.SistemaGrados;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.SistemaGradosRepository;
import com.taemoi.project.services.CatalogoService;
import com.taemoi.project.services.ComportamientoLegadoService;
import com.taemoi.project.services.TarifasService;

/**
 * Catálogo de la organización activa. Los repositorios filtran por {@code @TenantId}: solo se leen filas de la
 * organización de la petición. Las reglas de tarifa (rol y grupo familiar) son las de {@link TarifasService}.
 */
@Service
@Transactional(readOnly = true)
public class CatalogoServiceImpl implements CatalogoService {

	private final DisciplinaRepository disciplinas;
	private final SistemaGradosRepository sistemas;
	private final GradoRepository grados;
	private final CategoriaRepository categorias;
	private final TarifasService tarifas;
	private final ComportamientoLegadoService comportamientoLegado;

	public CatalogoServiceImpl(DisciplinaRepository disciplinas, SistemaGradosRepository sistemas,
			GradoRepository grados, CategoriaRepository categorias, TarifasService tarifas,
			ComportamientoLegadoService comportamientoLegado) {
		this.disciplinas = disciplinas;
		this.sistemas = sistemas;
		this.grados = grados;
		this.categorias = categorias;
		this.tarifas = tarifas;
		this.comportamientoLegado = comportamientoLegado;
	}

	@Override
	public CatalogoDTO obtener(boolean incluirTarifas) {
		List<CatalogoDTO.SistemaGrados> sistemasDto = new ArrayList<>();
		// Sistemas con material de examen: algún grado con bloque de temario.
		Set<Long> conMaterial = new HashSet<>();
		for (SistemaGrados sistema : sistemas.findAllByOrderByIdAsc()) {
			List<CatalogoDTO.Grado> gradosDto = new ArrayList<>();
			for (Grado grado : grados.findBySistemaGradosIdOrderByOrdenAscIdAsc(sistema.getId())) {
				if (grado.getBloqueTemario() != null && !grado.getBloqueTemario().isBlank()) {
					conMaterial.add(sistema.getId());
				}
				if (grado.getOrden() == null) {
					continue; // como ProgresionGradosService: sin orden no es seleccionable
				}
				gradosDto.add(new CatalogoDTO.Grado(grado.getCodigo(), grado.getNombre(), grado.getOrden(),
						grado.getAplicaA() == null ? null : grado.getAplicaA().name(), grado.getColor1(),
						grado.getColor2(), Boolean.TRUE.equals(grado.getRequiereExamen())));
			}
			sistemasDto.add(new CatalogoDTO.SistemaGrados(sistema.getId(), sistema.getNombre(), gradosDto));
		}

		List<CatalogoDTO.Disciplina> disciplinasDto = disciplinas.findAllByOrderByOrdenAscIdAsc().stream()
				.map(d -> {
					Long sistemaId = d.getSistemaGrados() == null ? null : d.getSistemaGrados().getId();
					return new CatalogoDTO.Disciplina(d.getCodigo(), d.getNombre(), d.getOrden(),
							Boolean.TRUE.equals(d.getActiva()), d.getEdadAdulto(),
							Boolean.TRUE.equals(d.getPermiteCompeticion()), Boolean.TRUE.equals(d.getRequiereLicencia()),
							sistemaId, sistemaId != null && conMaterial.contains(sistemaId));
				}).toList();

		List<CatalogoDTO.Categoria> categoriasDto = categorias.findAllByOrderByOrdenAscIdAsc().stream()
				.map(CatalogoServiceImpl::categoria).toList();

		List<CatalogoDTO.Tarifa> tarifasDto = incluirTarifas
				? tarifas.listarTodas().stream().map(this::tarifa).toList()
				: List.of();

		return new CatalogoDTO(new CatalogoDTO.Organizacion(TenantContext.get(), comportamientoLegado.activo()),
				disciplinasDto, sistemasDto, categoriasDto, tarifasDto);
	}

	@Override
	public CatalogoPublicoDTO obtenerPublico() {
		return new CatalogoPublicoDTO(disciplinas.findByActivaTrueOrderByOrdenAscIdAsc().stream()
				.map(d -> new CatalogoPublicoDTO.Disciplina(d.getCodigo(), d.getNombre(), d.getOrden())).toList());
	}

	private static CatalogoDTO.Categoria categoria(Categoria c) {
		Disciplina d = c.getDisciplina();
		return new CatalogoDTO.Categoria(c.getCodigo(), c.getNombre(), d == null ? null : d.getCodigo(),
				c.getEdadMin(), c.getEdadMax(), c.getOrden());
	}

	private CatalogoDTO.Tarifa tarifa(Tarifa t) {
		Disciplina d = t.getDisciplina();
		return new CatalogoDTO.Tarifa(t.getCodigo(), t.getNombre(), d == null ? null : d.getCodigo(), t.getImporte(),
				t.getImporteAdulto(), t.getImporteMenor(), t.getOrden(), Objects.equals(Boolean.TRUE, t.getActiva()),
				tarifas.requiereRolFamiliar(t), tarifas.requiereGrupoFamiliar(t));
	}
}
