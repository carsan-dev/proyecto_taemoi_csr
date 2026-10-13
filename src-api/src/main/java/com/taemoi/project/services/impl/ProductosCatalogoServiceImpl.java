package com.taemoi.project.services.impl;

import java.util.List;
import java.util.Optional;

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
import com.taemoi.project.services.ProductosCatalogoService;
import com.taemoi.project.services.ProgresionGradosService;

@Service
@Transactional(readOnly = true)
public class ProductosCatalogoServiceImpl implements ProductosCatalogoService {

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
