package com.taemoi.project.services.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Predicate;

import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.CatalogoLegadoTaemoi;
import com.taemoi.project.services.ProductoService;

@Service
public class ProductoServiceImpl implements ProductoService {

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;

	@Autowired
	private com.taemoi.project.services.ComportamientoLegadoService comportamientoLegado;

	@Override
	public List<Producto> obtenerTodosLosProductos() {
		return productoRepository.findAll();
	}

	@Override
	public Page<Producto> obtenerProductosPaginados(Pageable pageable) {
		return productoRepository.findAll(pageable);
	}

	@Override
	public Optional<Producto> obtenerProductoPorId(Long id) {
		return productoRepository.findById(id);
	}

	@Override
	public Page<Producto> buscarProductosPorConcepto(String concepto, Pageable pageable) {
		return productoRepository.findByConceptoContaining(concepto, pageable);
	}

	@Override
	public Page<Producto> buscarProductosFiltrados(String concepto, TipoProducto tipo, Boolean vendibleProfesor,
			Pageable pageable) {
		Specification<Producto> filtro = (root, query, cb) -> {
			List<Predicate> condiciones = new ArrayList<>();
			if (concepto != null && !concepto.isEmpty()) {
				// Mismo LIKE %x% con escape que la consulta derivada findByConceptoContaining.
				condiciones.add(cb.like(root.get("concepto"), "%" + EscapeCharacter.DEFAULT.escape(concepto) + "%",
						EscapeCharacter.DEFAULT.getEscapeCharacter()));
			}
			if (tipo != null) {
				condiciones.add(cb.equal(root.get("tipo"), tipo));
			}
			if (vendibleProfesor != null) {
				condiciones.add(cb.equal(root.get("vendibleProfesor"), vendibleProfesor));
			}
			return cb.and(condiciones.toArray(new Predicate[0]));
		};
		return productoRepository.findAll(filtro, pageable);
	}

	/**
	 * Alta (o, con el id de uno existente, sobrescritura por merge, como siempre). Pieza 6b-3b-1: si la petición no trae
	 * {@code vendibleProfesor} (cliente anterior), un producto existente conserva su marca y uno nuevo queda sin marcar
	 * ({@code Producto#valoresPorDefecto}).
	 */
	@Override
	public Producto guardarProducto(Producto producto) {
		if (producto.getVendibleProfesor() == null && producto.getId() != null) {
			productoRepository.findById(producto.getId())
					.ifPresent(actual -> producto.setVendibleProfesor(actual.getVendibleProfesor()));
		}
		return productoRepository.save(producto);
	}

	@Override
	public Producto actualizarProducto(Long id, Producto productoDetalles) {
		Optional<Producto> productoActual = productoRepository.findById(id);

		if (productoActual.isPresent()) {
			Producto producto = productoActual.get();
			validarCambioDeNombre(producto, productoDetalles.getConcepto());
			// Actualiza los campos necesarios
			producto.setConcepto(productoDetalles.getConcepto());
			producto.setPrecio(productoDetalles.getPrecio());
			// Pieza 6b-3b-1: sin el campo (cliente anterior) se conserva la marca; solo un valor explícito la cambia.
			if (productoDetalles.getVendibleProfesor() != null) {
				producto.setVendibleProfesor(productoDetalles.getVendibleProfesor());
			}

			return productoRepository.save(producto);
		} else {
			// Maneja el caso cuando el producto no existe
			throw new RuntimeException("Producto no encontrado con id " + id);
		}
	}

	/**
	 * Pieza 6b-3c (decisión 185): solo la marca; el resto de la ficha no se toca. El producto se carga por id con la
	 * sesión de Hibernate, así que rige el aislamiento por escuela de {@code @TenantId} (el de otra escuela no se
	 * encuentra: 404, como el inexistente).
	 */
	@Override
	@Transactional
	public Producto marcarVendibleProfesor(Long id, Boolean vendibleProfesor) {
		exigirMarca(vendibleProfesor);
		Producto producto = productoRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"Producto no encontrado con id " + id));
		producto.setVendibleProfesor(vendibleProfesor);
		return productoRepository.save(producto);
	}

	/**
	 * Pieza 6b-3c (decisión 185): todo o nada. Se cargan las entidades (consulta JPQL, filtrada por la escuela de la
	 * sesión por {@code @TenantId}) y se guardan una a una; NO un UPDATE en bloque, para no depender de que el filtro
	 * de organización alcance a las sentencias masivas. Si falta alguna (no existe o es de otra escuela), 404 antes de
	 * cambiar nada.
	 */
	@Override
	@Transactional
	public int marcarVendibleProfesor(List<Long> ids, Boolean vendibleProfesor) {
		exigirMarca(vendibleProfesor);
		if (ids == null || ids.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica al menos un producto.");
		}
		if (ids.contains(null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lista de productos tiene un valor vacío.");
		}
		Set<Long> distintos = new LinkedHashSet<>(ids);
		if (distintos.size() > MAX_IDS_VENDIBLE_PROFESOR) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Como máximo " + MAX_IDS_VENDIBLE_PROFESOR + " productos a la vez.");
		}
		List<Producto> productos = productoRepository.findAllById(distintos);
		if (productos.size() != distintos.size()) {
			Set<Long> faltan = new LinkedHashSet<>(distintos);
			productos.forEach(p -> faltan.remove(p.getId()));
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"Productos no encontrados: " + faltan.stream().map(String::valueOf).collect(Collectors.joining(", "))
							+ ". No se ha cambiado ninguno.");
		}
		productos.forEach(p -> p.setVendibleProfesor(vendibleProfesor));
		productoRepository.saveAll(productos);
		return distintos.size();
	}

	private static void exigirMarca(Boolean vendibleProfesor) {
		if (vendibleProfesor == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Indica si el profesor lo puede vender (vendibleProfesor).");
		}
	}

	/**
	 * Los cargos de mensualidad, competidor, licencia y reserva se reconocen por su concepto, que para un
	 * código PROPIO sale del nombre del producto: renombrarlo cuando ya tiene cargos duplicaría los del
	 * periodo al volver a generarlos. Los códigos heredados de Taemoi usan el concepto literal de siempre y
	 * se pueden renombrar.
	 *
	 * @throws ResponseStatusException 400 si se cambia el nombre de un producto así con cargos.
	 */
	private void validarCambioDeNombre(Producto producto, String conceptoNuevo) {
		if (Objects.equals(producto.getConcepto(), conceptoNuevo)) {
			return;
		}
		TipoProducto tipo = CatalogoLegadoTaemoi.tipoDeCatalogo(producto, comportamientoLegado.activo());
		boolean generaCargos = tipo == TipoProducto.MENSUALIDAD || tipo == TipoProducto.COMPETIDOR
				|| tipo == TipoProducto.LICENCIA || tipo == TipoProducto.RESERVA_PLAZA;
		if (generaCargos && productoAlumnoRepository.existsByProductoId(producto.getId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"No se puede cambiar el nombre de «" + producto.getConcepto() + "»: ya tiene cargos y su nombre "
							+ "identifica los cobros de cada periodo (se duplicarían). Cree un producto nuevo.");
		}
	}

	@Override
	@Transactional
	public void eliminarProducto(Long id) {
		// Desvincular todos los ProductoAlumno asociados antes de eliminar
		// Esto mantiene el historial de pagos pero sin referencia al producto eliminado
		List<ProductoAlumno> productosAlumno = productoAlumnoRepository.findByProductoId(id);
		for (ProductoAlumno pa : productosAlumno) {
			pa.setProducto(null);
			productoAlumnoRepository.save(pa);
		}

		productoRepository.deleteById(id);
	}
}
