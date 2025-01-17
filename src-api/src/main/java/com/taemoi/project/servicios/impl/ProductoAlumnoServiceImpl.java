package com.taemoi.project.servicios.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Producto;
import com.taemoi.project.entidades.ProductoAlumno;
import com.taemoi.project.errores.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.errores.producto.ProductoNoEncontradoException;
import com.taemoi.project.repositorios.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.ProductoAlumnoRepository;
import com.taemoi.project.repositorios.ProductoRepository;
import com.taemoi.project.servicios.ProductoAlumnoService;
import com.taemoi.project.utils.FechaUtils;
import com.taemoi.project.utils.MensualidadUtils;

@Service
public class ProductoAlumnoServiceImpl implements ProductoAlumnoService {

	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private AlumnoRepository alumnoRepository;

	@Autowired
	private AlumnoConvocatoriaRepository alumnoConvocatoriaRepository;

	@Override
	public ProductoAlumnoDTO asignarProductoAAlumno(Long alumnoId, Long productoId, ProductoAlumnoDTO detallesDTO) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));

		Producto producto = productoRepository.findById(productoId)
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado"));

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setProducto(producto);
		productoAlumno.setConcepto(producto.getConcepto());
		productoAlumno.setPrecio(producto.getPrecio());
		productoAlumno.setCantidad(detallesDTO.getCantidad() != null ? detallesDTO.getCantidad() : 1);
		productoAlumno.setPagado(detallesDTO.getPagado() != null ? detallesDTO.getPagado() : false);
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setNotas(detallesDTO.getNotas());

		if (productoAlumno.getPagado()) {
			productoAlumno.setFechaPago(new Date());
		}

		ProductoAlumno savedProductoAlumno = productoAlumnoRepository.save(productoAlumno);

		// Convertir a DTO antes de retornar
		return convertirADTO(savedProductoAlumno);
	}

	@Override
	public List<ProductoAlumnoDTO> obtenerProductosDeAlumno(Long alumnoId) {
		List<ProductoAlumno> productosAlumno = productoAlumnoRepository.findByAlumnoId(alumnoId);
		return productosAlumno.stream().map(this::convertirADTO).collect(Collectors.toList());
	}

	@Override
	public ProductoAlumnoDTO actualizarProductoAlumno(Long id, ProductoAlumnoDTO detallesDTO) {
		ProductoAlumno productoAlumno = productoAlumnoRepository.findById(id)
				.orElseThrow(() -> new ProductoNoEncontradoException("ProductoAlumno no encontrado"));

		if (detallesDTO.getCantidad() != null) {
			productoAlumno.setCantidad(detallesDTO.getCantidad());
		}
		if (detallesDTO.getPrecio() != null) {
			productoAlumno.setPrecio(detallesDTO.getPrecio());
		}
		if (detallesDTO.getConcepto() != null) {
			productoAlumno.setConcepto(detallesDTO.getConcepto());
		}
		if (detallesDTO.getNotas() != null) {
			productoAlumno.setNotas(detallesDTO.getNotas());
		}

		Boolean pagadoAntes = productoAlumno.getPagado();
		Boolean pagadoAhora = detallesDTO.getPagado();

		if (pagadoAhora != null && !pagadoAhora.equals(pagadoAntes)) {
			productoAlumno.setPagado(pagadoAhora);
			if (pagadoAhora) {
				productoAlumno.setFechaPago(new Date());
			} else {
				productoAlumno.setFechaPago(null);
			}
		}

		ProductoAlumno updatedProductoAlumno = productoAlumnoRepository.save(productoAlumno);

		alumnoConvocatoriaRepository.findByProductoAlumnoId(updatedProductoAlumno.getId())
				.ifPresent(alumnoConvocatoria -> {
					alumnoConvocatoria.setCuantiaExamen(updatedProductoAlumno.getPrecio());
					alumnoConvocatoria.setPagado(updatedProductoAlumno.getPagado());
					alumnoConvocatoriaRepository.save(alumnoConvocatoria);
				});

		return convertirADTO(updatedProductoAlumno);
	}

	@Override
	public void eliminarProductoAlumno(Long id) {
		productoAlumnoRepository.deleteById(id);
	}

	@Override
	public ProductoAlumnoDTO reservarPlaza(Long alumnoId, String concepto, boolean pagado, boolean forzar) {
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));

		if (!forzar) {
			boolean existeReserva = alumno.getProductosAlumno().stream()
					.anyMatch(productoAlumno -> concepto.equals(productoAlumno.getConcepto()));

			if (existeReserva) {
				throw new IllegalStateException("Ya existe una reserva de plaza para esta temporada.");
			}
		}

		Producto productoReserva = productoRepository.findByConcepto("RESERVA DE PLAZA")
				.orElseThrow(() -> new ProductoNoEncontradoException("Producto 'RESERVA DE PLAZA' no encontrado"));

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setProducto(productoReserva);
		productoAlumno.setConcepto(concepto);
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setCantidad(1);
		productoAlumno.setPrecio(productoReserva.getPrecio());
		productoAlumno.setPagado(pagado);
		if (pagado) {
			productoAlumno.setFechaPago(new Date());
		}
		productoAlumnoRepository.save(productoAlumno);

		return convertirADTO(productoAlumno);
	}

	@Override
	public void cargarMensualidadesGenerales(String mesAno) {
		String nombreMensualidad = MensualidadUtils.formatearNombreMensualidad(mesAno);
		List<Alumno> alumnos = alumnoRepository.findAll();
		Producto productoMensualidad = productoRepository.findByConcepto("MENSUALIDAD")
				.orElseThrow(() -> new IllegalArgumentException("Producto 'MENSUALIDAD' no encontrado"));

		for (Alumno alumno : alumnos) {
			boolean yaExiste = alumno.getProductosAlumno().stream()
					.anyMatch(pa -> pa.getConcepto().equalsIgnoreCase(nombreMensualidad));

			if (!yaExiste) {
				ProductoAlumno productoAlumno = new ProductoAlumno();
				productoAlumno.setAlumno(alumno);
				productoAlumno.setProducto(productoMensualidad);
				productoAlumno.setConcepto(nombreMensualidad);
				productoAlumno.setPrecio(alumno.getCuantiaTarifa());
				productoAlumno.setFechaAsignacion(new Date());
				productoAlumno.setCantidad(1);
				productoAlumno.setPagado(false);

				productoAlumnoRepository.save(productoAlumno);
			}
		}
	}

	@Override
	public void cargarMensualidadIndividual(Long alumnoId, String mesAno, boolean forzar) {
		String nombreMensualidad = MensualidadUtils.formatearNombreMensualidad(mesAno);
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado"));
		Producto productoMensualidad = productoRepository.findByConcepto("MENSUALIDAD")
				.orElseThrow(() -> new IllegalArgumentException("Producto 'MENSUALIDAD' no encontrado"));

		boolean yaExiste = alumno.getProductosAlumno().stream()
				.anyMatch(pa -> pa.getConcepto().equalsIgnoreCase(nombreMensualidad));

		if (yaExiste && !forzar) {
			throw new IllegalStateException("El alumno ya tiene asignada la " + nombreMensualidad);
		}

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setAlumno(alumno);
		productoAlumno.setProducto(productoMensualidad);
		productoAlumno.setConcepto(nombreMensualidad);
		productoAlumno.setPrecio(alumno.getCuantiaTarifa());
		productoAlumno.setFechaAsignacion(new Date());
		productoAlumno.setCantidad(1);
		productoAlumno.setPagado(false);

		productoAlumnoRepository.save(productoAlumno);
	}
	
	@Override
	public void crearAltaLicenciaFederativa(Alumno alumno) {
	    Producto productoLicencia = productoRepository.findByConcepto("LICENCIA FEDERATIVA")
	            .orElseThrow(() -> new ProductoNoEncontradoException("El producto 'LICENCIA FEDERATIVA' no existe."));

	    Date fechaLicencia = alumno.getFechaLicencia();
	    if (fechaLicencia == null) {
	        throw new IllegalArgumentException("La fecha de licencia del alumno no puede ser nula.");
	    }
	    LocalDate fechaLicenciaLocal = fechaLicencia.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	    String mesEnEspanol = fechaLicenciaLocal.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES")).toUpperCase();
	    String mesAnio = mesEnEspanol + " " + fechaLicenciaLocal.getYear();

	    boolean esSegundaMitadDelAno = fechaLicenciaLocal.getMonthValue() >= 9;

	    double precio;
	    if (Boolean.TRUE.equals(alumno.getTieneDiscapacidad())) {
	        precio = esSegundaMitadDelAno ? 20.0 : 30.0;
	    } else {
	        int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
	        if (edad < 14) {
	            precio = esSegundaMitadDelAno ? 20.0 : 35.0;
	        } else {
	            precio = esSegundaMitadDelAno ? 35.0 : 46.0;
	        }
	    }

	    ProductoAlumno productoAlumno = new ProductoAlumno();
	    productoAlumno.setAlumno(alumno);
	    productoAlumno.setProducto(productoLicencia);
	    productoAlumno.setConcepto("ALTA LICENCIA FEDERATIVA " + mesAnio);
	    productoAlumno.setCantidad(1);
	    productoAlumno.setPrecio(precio);
	    productoAlumno.setFechaAsignacion(fechaLicencia);
	    productoAlumno.setPagado(true);

	    productoAlumnoRepository.save(productoAlumno);
	}

	@Override
	public ProductoAlumnoDTO renovarLicencia(Long alumnoId) {
	    Alumno alumno = alumnoRepository.findById(alumnoId)
	            .orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + alumnoId));

	    Producto productoLicencia = productoRepository.findByConcepto("LICENCIA FEDERATIVA")
	            .orElseThrow(() -> new ProductoNoEncontradoException("El producto 'LICENCIA FEDERATIVA' no existe."));

	    LocalDate fechaActual = LocalDate.now();
	    boolean esSegundaMitadDelAno = fechaActual.getMonthValue() >= 9;
	    
	    String mesEnEspanol = fechaActual.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES")).toUpperCase();

	    double precio;
	    if (Boolean.TRUE.equals(alumno.getTieneDiscapacidad())) {
	        precio = esSegundaMitadDelAno ? 20.0 : 30.0;
	    } else {
	        int edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
	        if (edad < 14) {
	            precio = esSegundaMitadDelAno ? 20.0 : 35.0;
	        } else {
	            precio = esSegundaMitadDelAno ? 35.0 : 46.0;
	        }
	    }

	    ProductoAlumno productoAlumno = new ProductoAlumno();
	    productoAlumno.setAlumno(alumno);
	    productoAlumno.setProducto(productoLicencia);
	    productoAlumno.setConcepto("RENOVACION " + productoLicencia.getConcepto() + " " + mesEnEspanol + " " + fechaActual.getYear());
	    productoAlumno.setCantidad(1);
	    productoAlumno.setPrecio(precio);
	    productoAlumno.setFechaAsignacion(Date.from(fechaActual.atStartOfDay(ZoneId.systemDefault()).toInstant()));
	    productoAlumno.setPagado(true);

	    ProductoAlumno savedProductoAlumno = productoAlumnoRepository.save(productoAlumno);

	    alumno.setFechaLicencia(Date.from(fechaActual.atStartOfDay(ZoneId.systemDefault()).toInstant()));
	    alumnoRepository.save(alumno);

	    return convertirADTO(savedProductoAlumno);
	}

	private ProductoAlumnoDTO convertirADTO(ProductoAlumno productoAlumno) {
		ProductoAlumnoDTO dto = new ProductoAlumnoDTO();
		dto.setId(productoAlumno.getId());
		dto.setAlumnoId(productoAlumno.getAlumno().getId());
		dto.setProductoId(productoAlumno.getProducto().getId());
		dto.setConcepto(productoAlumno.getConcepto());
		dto.setPrecio(productoAlumno.getPrecio());
		dto.setCantidad(productoAlumno.getCantidad());
		dto.setPagado(productoAlumno.getPagado());
		dto.setFechaAsignacion(productoAlumno.getFechaAsignacion());
		dto.setFechaPago(productoAlumno.getFechaPago());
		dto.setNotas(productoAlumno.getNotas());
		return dto;
	}
}
