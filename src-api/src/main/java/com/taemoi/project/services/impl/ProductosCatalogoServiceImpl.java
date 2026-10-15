package com.taemoi.project.services.impl;


import com.taemoi.project.services.CatalogoLegadoTaemoi;
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
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.ComportamientoLegadoService;
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
	private final ComportamientoLegadoService comportamientoLegado;

	public ProductosCatalogoServiceImpl(ProductoRepository productos, GradoRepository grados,
			CatalogoDisciplinasService catalogo, ProgresionGradosService progresion,
			ComportamientoLegadoService comportamientoLegado) {
		this.productos = productos;
		this.grados = grados;
		this.catalogo = catalogo;
		this.progresion = progresion;
		this.comportamientoLegado = comportamientoLegado;
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
	 * Nombre de producto (en mayúsculas) -> código de grado que concede: con comportamiento heredado (solo
	 * Taemoi), primero los nombres que usaba el código anterior ({@link CatalogoLegadoTaemoi}, los del antiguo
	 * {@code TipoGrado} y las variantes «rojo bordado»); después, los productos clasificados de la
	 * organización activa.
	 */
	private Map<String, String> nombresQueConcedenGrado(TipoProducto tipo) {
		boolean recompensa = tipo == TipoProducto.RECOMPENSA;
		Map<String, String> nombres = new LinkedHashMap<>();
		if (comportamientoLegado.activo()) {
			for (String grado : CatalogoLegadoTaemoi.codigosGrado()) {
				String producto = CatalogoLegadoTaemoi.productoGradoLegado(grado, recompensa);
				if (producto != null) {
					nombres.put(producto.toUpperCase(Locale.ROOT), grado);
				}
			}
			nombres.put((recompensa ? RECOMPENSA_ROJO_BORDADO : EXAMEN_ROJO_BORDADO).toUpperCase(Locale.ROOT),
					CatalogoLegadoTaemoi.GRADO_ROJO);
		}
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

	// ---------------------------------------------------------------------------------------------
	// Resolución de productos cobrados (V27)
	// ---------------------------------------------------------------------------------------------

	@Override
	public Optional<Producto> resolverProductoMensualidad(String codigoDisciplina) {
		return sinClasificar(CONCEPTO_LEGADO_MENSUALIDAD, "mensualidad", codigoDisciplina)
				.or(() -> porTipo(TipoProducto.MENSUALIDAD, codigoDisciplina))
				.or(() -> porConceptoLegado(CONCEPTO_LEGADO_MENSUALIDAD, "mensualidad", codigoDisciplina));
	}

	@Override
	public Optional<Producto> resolverProductoReservaPlaza(String codigoDisciplina) {
		return sinClasificar(CONCEPTO_LEGADO_RESERVA_PLAZA, "reserva de plaza", codigoDisciplina)
				.or(() -> porTipo(TipoProducto.RESERVA_PLAZA, codigoDisciplina))
				.or(() -> porConceptoLegado(CONCEPTO_LEGADO_RESERVA_PLAZA, "reserva de plaza", codigoDisciplina));
	}

	@Override
	public Optional<Producto> resolverProductoCompetidor(String codigoDisciplina) {
		if (codigoDisciplina == null) {
			return Optional.empty();
		}
		Optional<Producto> heredado = sinClasificar(CONCEPTO_LEGADO_COMPETIDOR + codigoDisciplina, "competidor",
				codigoDisciplina);
		if (heredado.isPresent()) {
			return heredado;
		}
		Optional<Producto> enlazado = catalogo.buscarPorCodigo(codigoDisciplina)
				.flatMap(d -> real(d.getProductoCompetidor()));
		if (enlazado.isPresent()) {
			return enlazado;
		}
		return buscarPorCodigo(PREFIJO_COMPETIDOR + codigoDisciplina)
				.or(() -> porConceptoLegado(CONCEPTO_LEGADO_COMPETIDOR + codigoDisciplina, "competidor",
						codigoDisciplina));
	}

	@Override
	@Transactional
	public Optional<Producto> asegurarProductoCompetidor(String codigoDisciplina) {
		Optional<Disciplina> disciplina = catalogo.buscarPorCodigo(codigoDisciplina);
		if (disciplina.isEmpty() || !Boolean.TRUE.equals(disciplina.get().getPermiteCompeticion())) {
			return Optional.empty();
		}
		Optional<Producto> existente = resolverProductoCompetidor(codigoDisciplina);
		if (existente.isPresent()) {
			return existente;
		}
		// Como el código anterior a V27, pero clasificado y enlazado: la próxima vez se resuelve por enlace.
		Producto nuevo = new Producto();
		nuevo.setConcepto(CONCEPTO_LEGADO_COMPETIDOR + codigoDisciplina);
		nuevo.setPrecio(PRECIO_LEGADO_COMPETIDOR);
		nuevo.setCodigo(PREFIJO_COMPETIDOR + codigoDisciplina);
		nuevo.setTipo(TipoProducto.COMPETIDOR);
		nuevo.setDisciplina(disciplina.get());
		Producto guardado = productos.save(nuevo);
		disciplina.get().setProductoCompetidor(guardado);
		log.warn("No había producto de competidor para {}: se crea «{}» ({} €)", codigoDisciplina,
				guardado.getConcepto(), guardado.getPrecio());
		return Optional.of(guardado);
	}

	@Override
	public Optional<Producto> resolverProductoLicencia(String codigoDisciplina, boolean discapacidad, boolean menor,
			boolean proporcional) {
		boolean enCatalogo = codigoDisciplina != null && catalogo.buscarPorCodigo(codigoDisciplina).isPresent();
		String prefijo = "LICENCIA_" + codigoDisciplina + "_";
		String variante = discapacidad ? "DISCAPACIDAD" : (menor ? "INFANTIL" : "ADULTO");
		String edad = menor ? "INFANTIL" : "ADULTO";
		// 1. Solo con comportamiento heredado (Taemoi): un producto SIN clasificar con el concepto que buscaba
		// el código hasta V27 (p. ej. una variante con otras mayúsculas o de id menor que V24 no clasificó): es
		// el que se cobraba, y sigue siéndolo.
		String conceptoLegado = conceptoLegadoLicencia(codigoDisciplina, discapacidad, menor, proporcional);
		Optional<Producto> legado = productos.findFirstByConcepto(conceptoLegado);
		if (legado.isPresent() && legado.get().getCodigo() == null && comportamientoLegado.activo()) {
			log.warn("Producto de licencia ({}) sin catálogo: se usa el de concepto «{}» (id {})", codigoDisciplina,
					legado.get().getConcepto(), legado.get().getId());
			return legado;
		}
		// 2. El codigo con todo lo pedido.
		if (enCatalogo) {
			Optional<Producto> exacto = buscarPorCodigo(prefijo + variante + (proporcional ? SUFIJO_PROPORCIONAL : ""));
			if (exacto.isPresent()) {
				return exacto;
			}
		}
		// 3. La disciplina no tiene esa variante (ningún producto de licencia suyo con discapacidad o parte
		// proporcional, como kickboxing en Taemoi): la de su edad, a precio completo.
		if (enCatalogo) {
			List<String> codigos = productos.findByCodigoIsNotNullOrderByIdAsc().stream()
					.map(Producto::getCodigo)
					.filter(c -> c.startsWith(prefijo))
					.toList();
			boolean conDiscapacidad = discapacidad
					&& codigos.stream().anyMatch(c -> c.startsWith(prefijo + "DISCAPACIDAD"));
			boolean conProporcional = proporcional && codigos.stream().anyMatch(c -> c.endsWith(SUFIJO_PROPORCIONAL));
			Optional<Producto> degradado = buscarPorCodigo(
					prefijo + (conDiscapacidad ? "DISCAPACIDAD" : edad) + (conProporcional ? SUFIJO_PROPORCIONAL : ""));
			if (degradado.isPresent()) {
				return degradado;
			}
		}
		// 4. El producto (clasificado) con el concepto heredado.
		legado.ifPresent(p -> log.warn("Producto de licencia ({}) resuelto por concepto «{}» (id {}, codigo {})",
				codigoDisciplina, p.getConcepto(), p.getId(), p.getCodigo()));
		return legado;
	}

	@Override
	public String conceptoLegadoLicencia(String codigoDisciplina, boolean discapacidad, boolean menor,
			boolean proporcional) {
		if ("KICKBOXING".equals(codigoDisciplina)) {
			return menor ? "LICENCIA FEDERATIVA KICKBOXING INFANTIL" : "LICENCIA FEDERATIVA KICKBOXING ADULTO";
		}
		String variante = discapacidad ? "DISCAPACIDAD" : (menor ? "INFANTIL" : "ADULTO");
		return (proporcional ? "PARTE PROPORCIONAL " : "") + "LICENCIA FEDERATIVA TAEKWONDO " + variante;
	}

	@Override
	public Optional<Producto> resolverProductoGrado(String codigoDisciplina, String codigoGrado, boolean recompensa,
			boolean bordado, String conceptoLegado) {
		String prefijo = recompensa ? "RECOMPENSA_" : "EXAMEN_";
		// Primero lo que elegía el código anterior si NO está clasificado (variante de id menor o con otras
		// mayúsculas): se sigue cobrando el mismo producto.
		if (conceptoLegado != null) {
			Optional<Producto> heredado = sinClasificar(conceptoLegado, recompensa ? "recompensa" : "examen",
					codigoDisciplina);
			if (heredado.isPresent()) {
				return heredado;
			}
		}
		Optional<Producto> catalogado = Optional.empty();
		if (codigoGrado != null) {
			if (bordado) {
				catalogado = buscarPorCodigo(prefijo + codigoGrado + "_BORDADO");
			} else {
				if (codigoDisciplina != null && catalogo.buscarPorCodigo(codigoDisciplina).isPresent()) {
					catalogado = recompensa ? buscarProductoRecompensa(codigoDisciplina, codigoGrado)
							: buscarProductoExamen(codigoDisciplina, codigoGrado);
				}
				if (catalogado.isEmpty()) {
					catalogado = buscarPorCodigo(prefijo + codigoGrado);
				}
			}
		}
		if (catalogado.isPresent() || conceptoLegado == null) {
			return catalogado;
		}
		Optional<Producto> exacto = productos.findFirstByConcepto(conceptoLegado);
		Optional<Producto> legado = exacto.isPresent() ? exacto
				: productos.findAll().stream()
						.filter(p -> normalizar(p.getConcepto()).equals(normalizar(conceptoLegado)))
						.findFirst();
		legado.ifPresent(p -> log.warn(
				"Producto de {} para el grado {} ({}) sin catálogo: se usa el de concepto «{}» (id {})",
				recompensa ? "recompensa" : "examen", codigoGrado, codigoDisciplina, p.getConcepto(), p.getId()));
		return legado;
	}

	@Override
	public Optional<TipoProducto> tipoDeProducto(Long productoId) {
		if (productoId == null) {
			return Optional.empty();
		}
		return productos.findById(productoId).map(Producto::getTipo);
	}

	private static final String PREFIJO_COMPETIDOR = "COMPETIDOR_";
	private static final String CONCEPTO_LEGADO_COMPETIDOR = "TARIFA COMPETIDOR ";
	private static final String SUFIJO_PROPORCIONAL = "_PROPORCIONAL";

	/** Producto de ese tipo de la disciplina; si no hay, el general (sin disciplina). */
	private Optional<Producto> porTipo(TipoProducto tipo, String codigoDisciplina) {
		if (codigoDisciplina != null) {
			Optional<Disciplina> disciplina = catalogo.buscarPorCodigo(codigoDisciplina);
			if (disciplina.isPresent()) {
				List<Producto> deLaDisciplina = productos.findByTipoAndDisciplinaIdOrderByIdAsc(tipo,
						disciplina.get().getId());
				if (!deLaDisciplina.isEmpty()) {
					return Optional.of(deLaDisciplina.get(0));
				}
			}
		}
		return productos.findByTipoAndDisciplinaIsNullOrderByIdAsc(tipo).stream().findFirst();
	}

	/**
	 * Primera búsqueda, la del código anterior a V27: el producto con ese concepto (findFirstByConcepto,
	 * con la intercalación de la columna) si NO está clasificado. Así se sigue cobrando la variante sin
	 * clasificar (otras mayúsculas, id menor) que elegía el código anterior; si es el clasificado, se
	 * continúa por el catálogo.
	 */
	private Optional<Producto> sinClasificar(String concepto, String uso, String codigoDisciplina) {
		if (!comportamientoLegado.activo()) {
			// Solo Taemoi (comportamiento heredado): las demás escuelas usan siempre el catálogo.
			return Optional.empty();
		}
		Optional<Producto> producto = productos.findFirstByConcepto(concepto).filter(p -> p.getCodigo() == null);
		producto.ifPresent(p -> log.warn("Producto de {} ({}) sin clasificar: se usa, como antes de V27, el de concepto «{}» (id {})",
				uso, codigoDisciplina, p.getConcepto(), p.getId()));
		return producto;
	}

	/** Respaldo: el producto con el concepto que buscaba el código hasta V27 (WARN si lo encuentra). */
	private Optional<Producto> porConceptoLegado(String concepto, String uso, String codigoDisciplina) {
		Optional<Producto> producto = productos.findFirstByConcepto(concepto);
		producto.ifPresent(p -> log.warn(
				"Producto de {} ({}) sin catálogo: se usa el de concepto «{}» (id {}, codigo {})", uso,
				codigoDisciplina, p.getConcepto(), p.getId(), p.getCodigo()));
		return producto;
	}

	/** Normalización de AlumnoServiceImpl.buscarProductoPorConcepto: sin acentos, espacios ni mayúsculas. */
	private static String normalizar(String concepto) {
		if (concepto == null) {
			return "";
		}
		String sinAcentos = java.text.Normalizer.normalize(concepto, java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "");
		return sinAcentos.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
	}

	/** Los enlaces son perezosos: se devuelve la entidad cargada, usable fuera de la transacción. */
	private static Optional<Producto> real(Producto producto) {
		return Optional.ofNullable(producto == null ? null : (Producto) Hibernate.unproxy(producto));
	}
}
