package com.taemoi.project.plataforma;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.SistemaGradosRepository;
import com.taemoi.project.repositories.TarifaRepository;

/**
 * Fase 4, pieza F4-2: lee la foto del catálogo de la escuela plantilla en una transacción de solo lectura CON ESA ESCUELA
 * fijada ({@link TransaccionEnEscuela#leer}): los repositorios de siempre, con su filtro {@code @TenantId}, sin SQL nativo.
 * Solo se lee: la plantilla no cambia.
 */
@Component
class PlantillaCatalogoLector {

	private static final Sort POR_ID = Sort.by("id");

	private final TransaccionEnEscuela enEscuela;
	private final DisciplinaRepository disciplinas;
	private final SistemaGradosRepository sistemas;
	private final GradoRepository grados;
	private final CategoriaRepository categorias;
	private final TarifaRepository tarifas;
	private final ProductoRepository productos;

	PlantillaCatalogoLector(TransaccionEnEscuela enEscuela, DisciplinaRepository disciplinas,
			SistemaGradosRepository sistemas, GradoRepository grados, CategoriaRepository categorias,
			TarifaRepository tarifas, ProductoRepository productos) {
		this.enEscuela = enEscuela;
		this.disciplinas = disciplinas;
		this.sistemas = sistemas;
		this.grados = grados;
		this.categorias = categorias;
		this.tarifas = tarifas;
		this.productos = productos;
	}

	PlantillaCatalogo leer(Long plantillaId) {
		return enEscuela.leer(plantillaId, () -> new PlantillaCatalogo(
				disciplinas.findAllByOrderByOrdenAscIdAsc().stream()
						.map(d -> new PlantillaCatalogo.Disciplina(d.getId(), d.getCodigo(), d.getNombre(),
								d.getSistemaGrados() == null ? null : d.getSistemaGrados().getId(),
								d.getPermiteCompeticion(), d.getRequiereLicencia(), d.getEdadAdulto(), d.getOrden(),
								d.getActiva(), id(d.getProductoLicencia()), id(d.getProductoCompetidor())))
						.toList(),
				sistemas.findAllByOrderByIdAsc().stream()
						.map(s -> new PlantillaCatalogo.Sistema(s.getId(), s.getNombre(), s.getPlantillaOrigen()))
						.toList(),
				grados.findAll(POR_ID).stream()
						.filter(g -> g.getSistemaGrados() != null)
						.map(g -> new PlantillaCatalogo.Grado(g.getId(), g.getSistemaGrados().getId(), g.getCodigo(),
								g.getNombre(), g.getOrden(), g.getColor1(), g.getColor2(), g.getAplicaA(),
								id(g.getEquivalenteAdulto()), g.getRequiereExamen(), g.getMesesMinimosMenor(),
								g.getMesesMinimosAdulto(), g.getBloqueTemario(), id(g.getProductoExamen()),
								id(g.getProductoRecompensa())))
						.toList(),
				categorias.findAll(POR_ID).stream()
						.map(c -> new PlantillaCatalogo.Categoria(c.getId(), c.getCodigo(), c.getNombre(),
								id(c.getDisciplina()), c.getEdadMin(), c.getEdadMax(), c.getOrden()))
						.toList(),
				tarifas.findAll(POR_ID).stream()
						.map(t -> new PlantillaCatalogo.Tarifa(t.getId(), id(t.getDisciplina()), t.getCodigo(),
								t.getNombre(), t.getImporte(), t.getImporteAdulto(), t.getImporteMenor(), t.getOrden()))
						.toList(),
				productos.findAll(POR_ID).stream()
						.map(p -> new PlantillaCatalogo.Producto(p.getId(), p.getConcepto(), p.getPrecio(), p.getCodigo(),
								p.getTipo(), id(p.getDisciplina()), id(p.getGrado()), p.getVendibleProfesor()))
						.toList()));
	}

	private static Long id(Disciplina disciplina) {
		return disciplina == null ? null : disciplina.getId();
	}

	private static Long id(Grado grado) {
		return grado == null ? null : grado.getId();
	}

	private static Long id(Producto producto) {
		return producto == null ? null : producto.getId();
	}
}
