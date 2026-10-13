package com.taemoi.project.services.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.ProductosCatalogoService;
import com.taemoi.project.services.ProgresionGradosService;

@Service
@Transactional(readOnly = true)
public class ProductosCatalogoServiceImpl implements ProductosCatalogoService {

	private static final Logger log = LoggerFactory.getLogger(ProductosCatalogoServiceImpl.class);

	/** Variantes «rojo bordado» que el código cobraba hasta V26 (AlumnoServiceImpl): conceden ROJO. */
	private static final String EXAMEN_ROJO_BORDADO = "DERECHO A EXAMEN ROJO BORDADO";
	private static final String RECOMPENSA_ROJO_BORDADO = "DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA";

	private final ProductoRepository productos;
	private final GradoRepository grados;
	private final CatalogoDisciplinasService catalogo;
	private final ProgresionGradosService progresion;

	public ProductosCatalogoServiceImpl(ProductoRepository productos, GradoRepository grados,
			CatalogoDisciplinasService catalogo, ProgresionGradosService progresion) {
		this.productos = productos;
		this.grados = grados;
		this.catalogo = catalogo;
		this.progresion = progresion;
	}

	@Override
	public Optional<Producto> buscarPorCodigo(String codigo) {
		if (codigo == null) {
			return Optional.empty();
		}
		return productos.findByCodigo(codigo);
	}

	@Override
	public List<Producto> listarPorTipo(TipoProducto tipo, String codigoDisciplina) {
		if (tipo == null) {
			return List.of();
		}
		if (codigoDisciplina == null) {
			return productos.findByTipoAndDisciplinaIsNullOrderByIdAsc(tipo);
		}
		return productos.findByTipoAndDisciplinaIdOrderByIdAsc(tipo, catalogo.obtenerPorCodigo(codigoDisciplina).getId());
	}

	@Override
	public Optional<Producto> buscarProductoCompetidor(String codigoDisciplina) {
		if (codigoDisciplina == null) {
			return Optional.empty();
		}
		return real(catalogo.obtenerPorCodigo(codigoDisciplina).getProductoCompetidor());
	}

	@Override
	public Optional<Producto> buscarProductoLicencia(String codigoDisciplina) {
		if (codigoDisciplina == null) {
			return Optional.empty();
		}
		return real(catalogo.obtenerPorCodigo(codigoDisciplina).getProductoLicencia());
	}

	@Override
	public Optional<Producto> buscarProductoExamen(String codigoDisciplina, String codigoGrado) {
		return progresion.buscarGrado(codigoDisciplina, codigoGrado).flatMap(g -> real(g.getProductoExamen()));
	}

	@Override
	public Optional<Producto> buscarProductoRecompensa(String codigoDisciplina, String codigoGrado) {
		return progresion.buscarGrado(codigoDisciplina, codigoGrado).flatMap(g -> real(g.getProductoRecompensa()));
	}

	@Override
	public Optional<Grado> buscarGradoPorProductoExamen(Long productoId, String codigoDisciplina) {
		return gradoConcedido(productoId, codigoDisciplina, TipoProducto.EXAMEN);
	}

	@Override
	public Optional<Grado> buscarGradoPorProductoRecompensa(Long productoId, String codigoDisciplina) {
		return gradoConcedido(productoId, codigoDisciplina, TipoProducto.RECOMPENSA);
	}

	@Override
	public Optional<Grado> buscarGradoPorProducto(Long productoId, String codigoDisciplina) {
		return gradoConcedido(productoId, codigoDisciplina, null);
	}

	@Override
	public Optional<String> buscarCodigoGradoConcedido(Long productoId, String conceptoCargo) {
		Optional<String> porProducto = productoId == null ? Optional.empty()
				: productos.findById(productoId).map(Producto::getGrado).map(Grado::getCodigo);
		if (porProducto.isPresent() || conceptoCargo == null) {
			return porProducto;
		}
		Optional<String> porConcepto = gradoDelConcepto(conceptoCargo);
		int sufijo = conceptoCargo.lastIndexOf(SEPARADOR_DISCIPLINA);
		if (porConcepto.isEmpty() && sufijo > 0) {
			porConcepto = gradoDelConcepto(conceptoCargo.substring(0, sufijo));
		}
		if (porConcepto.isPresent()) {
			return porConcepto;
		}
		Optional<String> porNombreContenido = gradoPorNombreContenido(conceptoCargo, TipoProducto.RECOMPENSA)
				.or(() -> gradoPorNombreContenido(conceptoCargo, TipoProducto.EXAMEN));
		porNombreContenido.ifPresent(codigo -> log.info(
				"Grado {} concedido por nombre contenido en el concepto del cargo «{}» (producto {} sin grado)",
				codigo, conceptoCargo, productoId));
		return porNombreContenido;
	}

	/**
	 * Último respaldo: el nombre de producto de ese tipo contenido en el concepto (sin distinguir
	 * mayúsculas) que sea más LARGO.
	 */
	private Optional<String> gradoPorNombreContenido(String concepto, TipoProducto tipo) {
		String conceptoMayusculas = concepto.toUpperCase(Locale.ROOT);
		String mejorNombre = null;
		String mejorCodigo = null;
		for (Map.Entry<String, String> candidato : nombresQueConcedenGrado(tipo).entrySet()) {
			String nombre = candidato.getKey();
			if (conceptoMayusculas.contains(nombre) && (mejorNombre == null || nombre.length() > mejorNombre.length())) {
				mejorNombre = nombre;
				mejorCodigo = candidato.getValue();
			}
		}
		return Optional.ofNullable(mejorCodigo);
	}

	/**
	 * Nombre de producto (en mayúsculas) -> código de grado que concede: los productos clasificados de
	 * la organización activa y, hasta la contracción, los nombres que usaba el código (Taemoi).
	 */
	private Map<String, String> nombresQueConcedenGrado(TipoProducto tipo) {
		boolean recompensa = tipo == TipoProducto.RECOMPENSA;
		Map<String, String> nombres = new LinkedHashMap<>();
		for (TipoGrado grado : TipoGrado.values()) {
			if (recompensa ? grado.tieneProductoRecompensa() : grado.tieneProductoExamen()) {
				nombres.put(grado.obtenerNombreProducto(recompensa).toUpperCase(Locale.ROOT), grado.name());
			}
		}
		nombres.put((recompensa ? RECOMPENSA_ROJO_BORDADO : EXAMEN_ROJO_BORDADO).toUpperCase(Locale.ROOT),
				TipoGrado.ROJO.name());
		for (Producto producto : productos.findByTipoAndGradoIsNotNullOrderByIdAsc(tipo)) {
			if (producto.getConcepto() != null && !producto.getConcepto().isBlank()) {
				nombres.putIfAbsent(producto.getConcepto().trim().toUpperCase(Locale.ROOT), producto.getGrado().getCodigo());
			}
		}
		return nombres;
	}

	/** Separador del sufijo de disciplina en los cargos («DERECHO A EXAMEN AZUL - Taekwondo»). */
	private static final String SEPARADOR_DISCIPLINA = " - ";

	private Optional<String> gradoDelConcepto(String concepto) {
		return productos.findFirstByConceptoAndGradoIsNotNullOrderByIdAsc(concepto)
				.map(Producto::getGrado)
				.map(Grado::getCodigo);
	}

	/**
	 * {@code producto.grado_id} traducido al sistema de la disciplina por codigo de grado (el
	 * enlace apunta al sistema de taekwondo y kickboxing comparte los productos).
	 */
	private Optional<Grado> gradoConcedido(Long productoId, String codigoDisciplina, TipoProducto tipo) {
		Long sistema = sistemaDe(codigoDisciplina);
		if (productoId == null || sistema == null) {
			return Optional.empty();
		}
		return productos.findById(productoId)
				.filter(p -> tipo == null || p.getTipo() == tipo)
				.map(Producto::getGrado)
				.flatMap(concedido -> grados.findBySistemaGradosIdAndCodigo(sistema, concedido.getCodigo()));
	}

	/** Sistema de grados de la disciplina; nulo si la disciplina es nula o no tiene grados. */
	private Long sistemaDe(String codigoDisciplina) {
		if (codigoDisciplina == null) {
			return null;
		}
		Disciplina disciplina = catalogo.obtenerPorCodigo(codigoDisciplina);
		return disciplina.tieneGrados() ? disciplina.getSistemaGrados().getId() : null;
	}

	/** Los enlaces son perezosos: se devuelve la entidad cargada, usable fuera de la transacción. */
	private static Optional<Producto> real(Producto producto) {
		return Optional.ofNullable(producto == null ? null : (Producto) Hibernate.unproxy(producto));
	}
}
