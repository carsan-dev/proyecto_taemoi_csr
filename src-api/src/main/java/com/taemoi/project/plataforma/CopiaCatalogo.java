package com.taemoi.project.plataforma;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.SistemaGrados;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.SistemaGradosRepository;
import com.taemoi.project.repositories.TarifaRepository;

/**
 * Fase 4, pieza F4-2 (decisión 392): escribe la selección del catálogo de la plantilla en la escuela de la sesión ACTUAL
 * (la nueva: {@code organizacion_id} lo pone {@code @TenantId}), dentro de la transacción del alta. Filas nuevas con los
 * enlaces remapeados a ellas: disciplina → sistema, grado → sistema, producto → disciplina y grado, grado → productos de
 * examen y recompensa, disciplina → productos de licencia y competidor. Un enlace a algo que no se copia queda vacío.
 *
 * <p>Las tarifas se copian DESACTIVADAS ({@code activa = false}) con los importes de la plantilla como referencia: la
 * escuela pone los suyos y las activa. Los productos conservan su precio (no tienen interruptor).
 */
@Component
class CopiaCatalogo {

	private final SistemaGradosRepository sistemas;
	private final DisciplinaRepository disciplinas;
	private final GradoRepository grados;
	private final ProductoRepository productos;
	private final CategoriaRepository categorias;
	private final TarifaRepository tarifas;

	CopiaCatalogo(SistemaGradosRepository sistemas, DisciplinaRepository disciplinas, GradoRepository grados,
			ProductoRepository productos, CategoriaRepository categorias, TarifaRepository tarifas) {
		this.sistemas = sistemas;
		this.disciplinas = disciplinas;
		this.grados = grados;
		this.productos = productos;
		this.categorias = categorias;
		this.tarifas = tarifas;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void copiar(PlantillaCatalogo plantilla, PlantillaCatalogo.Seleccion seleccion) {
		Map<Long, SistemaGrados> sistemaNuevo = new HashMap<>();
		for (PlantillaCatalogo.Sistema s : seleccion.sistemas()) {
			SistemaGrados nuevo = new SistemaGrados();
			nuevo.setNombre(s.nombre());
			nuevo.setPlantillaOrigen(s.plantillaOrigen() != null ? s.plantillaOrigen() : primeraQueLoUsa(seleccion, s));
			sistemaNuevo.put(s.id(), sistemas.save(nuevo));
		}

		Map<Long, Disciplina> disciplinaNueva = new HashMap<>();
		for (PlantillaCatalogo.Disciplina d : seleccion.disciplinas()) {
			Disciplina nueva = new Disciplina();
			nueva.setCodigo(d.codigo());
			nueva.setNombre(d.nombre());
			nueva.setSistemaGrados(d.sistemaId() == null ? null : sistemaNuevo.get(d.sistemaId()));
			nueva.setPermiteCompeticion(d.permiteCompeticion());
			nueva.setRequiereLicencia(d.requiereLicencia());
			nueva.setEdadAdulto(d.edadAdulto());
			nueva.setOrden(d.orden());
			nueva.setActiva(d.activa());
			disciplinaNueva.put(d.id(), disciplinas.save(nueva));
		}

		Map<Long, Grado> gradoNuevo = new HashMap<>();
		// Por sistema de la plantilla, sus grados nuevos por código.
		Map<Long, Map<String, Grado>> gradosPorCodigo = new HashMap<>();
		for (PlantillaCatalogo.Grado g : seleccion.grados()) {
			Grado nuevo = new Grado();
			nuevo.setSistemaGrados(sistemaNuevo.get(g.sistemaId()));
			nuevo.setCodigo(g.codigo());
			nuevo.setNombre(g.nombre());
			nuevo.setOrden(g.orden());
			nuevo.setColor1(g.color1());
			nuevo.setColor2(g.color2());
			nuevo.setAplicaA(g.aplicaA());
			nuevo.setRequiereExamen(g.requiereExamen());
			nuevo.setMesesMinimosMenor(g.mesesMinimosMenor());
			nuevo.setMesesMinimosAdulto(g.mesesMinimosAdulto());
			nuevo.setBloqueTemario(g.bloqueTemario());
			Grado guardado = grados.save(nuevo);
			gradoNuevo.put(g.id(), guardado);
			gradosPorCodigo.computeIfAbsent(g.sistemaId(), k -> new HashMap<>()).put(g.codigo(), guardado);
		}
		for (PlantillaCatalogo.Grado g : seleccion.grados()) {
			if (g.equivalenteAdultoId() != null) {
				gradoNuevo.get(g.id()).setEquivalenteAdulto(gradoNuevo.get(g.equivalenteAdultoId()));
			}
		}

		Map<Long, Producto> productoNuevo = new HashMap<>();
		for (PlantillaCatalogo.Producto p : seleccion.productos()) {
			Producto nuevo = new Producto();
			nuevo.setConcepto(p.concepto());
			nuevo.setPrecio(p.precio());
			nuevo.setCodigo(p.codigo());
			nuevo.setTipo(p.tipo());
			nuevo.setDisciplina(p.disciplinaId() == null ? null : disciplinaNueva.get(p.disciplinaId()));
			nuevo.setGrado(gradoDelProducto(plantilla, seleccion, p, gradoNuevo, gradosPorCodigo));
			nuevo.setVendibleProfesor(p.vendibleProfesor());
			productoNuevo.put(p.id(), productos.save(nuevo));
		}
		for (PlantillaCatalogo.Disciplina d : seleccion.disciplinas()) {
			Disciplina nueva = disciplinaNueva.get(d.id());
			nueva.setProductoLicencia(enlace(productoNuevo, d.productoLicenciaId()));
			nueva.setProductoCompetidor(enlace(productoNuevo, d.productoCompetidorId()));
		}
		for (PlantillaCatalogo.Grado g : seleccion.grados()) {
			Grado nuevo = gradoNuevo.get(g.id());
			nuevo.setProductoExamen(enlace(productoNuevo, g.productoExamenId()));
			nuevo.setProductoRecompensa(enlace(productoNuevo, g.productoRecompensaId()));
		}

		List<Categoria> categoriasNuevas = new ArrayList<>();
		for (PlantillaCatalogo.Categoria c : seleccion.categorias()) {
			Categoria nueva = new Categoria();
			nueva.setCodigo(c.codigo());
			nueva.setNombre(c.nombre());
			nueva.setDisciplina(c.disciplinaId() == null ? null : disciplinaNueva.get(c.disciplinaId()));
			nueva.setEdadMin(c.edadMin());
			nueva.setEdadMax(c.edadMax());
			nueva.setOrden(c.orden());
			categoriasNuevas.add(nueva);
		}
		categorias.saveAll(categoriasNuevas);

		List<Tarifa> tarifasNuevas = new ArrayList<>();
		for (PlantillaCatalogo.Tarifa t : seleccion.tarifas()) {
			Tarifa nueva = new Tarifa();
			nueva.setDisciplina(t.disciplinaId() == null ? null : disciplinaNueva.get(t.disciplinaId()));
			nueva.setCodigo(t.codigo());
			nueva.setNombre(t.nombre());
			nueva.setImporte(t.importe());
			nueva.setImporteAdulto(t.importeAdulto());
			nueva.setImporteMenor(t.importeMenor());
			nueva.setActiva(Boolean.FALSE);
			nueva.setOrden(t.orden());
			tarifasNuevas.add(nueva);
		}
		tarifas.saveAll(tarifasNuevas);
		tarifas.flush();
	}

	/**
	 * El grado nuevo de un producto: la copia de su grado si su sistema se copió; si no (producto compartido de una
	 * disciplina cuyo sistema tiene ese código, p. ej. solo kickboxing), el grado con el mismo código del sistema de su
	 * disciplina o, sin disciplina, de la primera elegida (en el orden del catálogo) que lo tenga.
	 */
	private static Grado gradoDelProducto(PlantillaCatalogo plantilla, PlantillaCatalogo.Seleccion seleccion,
			PlantillaCatalogo.Producto p, Map<Long, Grado> gradoNuevo, Map<Long, Map<String, Grado>> gradosPorCodigo) {
		if (p.gradoId() == null) {
			return null;
		}
		Grado copia = gradoNuevo.get(p.gradoId());
		if (copia != null) {
			return copia;
		}
		String codigo = plantilla.codigoDeGrado(p.gradoId());
		for (PlantillaCatalogo.Disciplina d : seleccion.disciplinas()) {
			if (d.sistemaId() == null || (p.disciplinaId() != null && !p.disciplinaId().equals(d.id()))) {
				continue;
			}
			Grado mismoCodigo = gradosPorCodigo.getOrDefault(d.sistemaId(), Map.of()).get(codigo);
			if (mismoCodigo != null) {
				return mismoCodigo;
			}
		}
		return null;
	}

	private static String primeraQueLoUsa(PlantillaCatalogo.Seleccion seleccion, PlantillaCatalogo.Sistema sistema) {
		return seleccion.disciplinas().stream().filter(d -> sistema.id().equals(d.sistemaId()))
				.map(PlantillaCatalogo.Disciplina::codigo).findFirst().orElse(null);
	}

	private static Producto enlace(Map<Long, Producto> productoNuevo, Long productoId) {
		return productoId == null ? null : productoNuevo.get(productoId);
	}
}
