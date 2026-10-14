package com.taemoi.project.services.impl;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
	public Producto guardarProducto(Producto producto) {
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

			return productoRepository.save(producto);
		} else {
			// Maneja el caso cuando el producto no existe
			throw new RuntimeException("Producto no encontrado con id " + id);
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
