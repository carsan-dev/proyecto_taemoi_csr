package com.taemoi.project.servicios.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Producto;
import com.taemoi.project.errores.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.errores.producto.ProductoNoEncontradoException;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.ProductoRepository;
import com.taemoi.project.servicios.ProductoService;

@Service
public class ProductoServiceImpl implements ProductoService{

	@Autowired
	private ProductoRepository productoRepository;
	
	@Autowired
	private AlumnoRepository alumnoRepository;
	
    @Override
    public Producto agregarProductoAAlumno(Long alumnoId, Producto producto) {
        Alumno alumno = alumnoRepository.findById(alumnoId).orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));
        producto.setAlumno(alumno);
        return productoRepository.save(producto);
    }

    @Override
    public List<Producto> obtenerProductosDeAlumno(Long alumnoId) {
        Alumno alumno = alumnoRepository.findById(alumnoId).orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));
        return alumno.getProductos();
    }

    @Override
    public Producto actualizarEstadoPago(Long productoId, boolean pagado) {
        Producto producto = productoRepository.findById(productoId).orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado"));
        producto.setPagado(pagado);
        if (pagado) {
            producto.setFechaPago(new Date());
        }
        return productoRepository.save(producto);
    }
    
    @Override
    public Producto crearReservaPlaza(Long alumnoId) {
        Alumno alumno = alumnoRepository.findById(alumnoId)
            .orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado"));

        // Configurar el concepto como "Reserva de Plaza" y la cuantía fija de 20 euros
        Producto reservaPlaza = new Producto();
        reservaPlaza.setAlumno(alumno);
        reservaPlaza.setConcepto("Reserva de Plaza");
        reservaPlaza.setCantidad(1);
        reservaPlaza.setPrecio(20.0);
        reservaPlaza.setPagado(false);
        reservaPlaza.setFecha(new Date());

        // Calcular el curso correspondiente, por ejemplo, "2024 - 2025"
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String curso = year + " - " + (year + 1);
        reservaPlaza.setNotas(curso);

        return productoRepository.save(reservaPlaza);
    }
    
    @Override
    public List<Producto> obtenerTodosLosProductos() {
        return productoRepository.findAll();
    }
    
    @Override
    public Optional<Producto> obtenerProductoPorId(Long id) {
        return productoRepository.findById(id);
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
            // Actualiza los campos necesarios
            producto.setConcepto(productoDetalles.getConcepto());
            producto.setFecha(productoDetalles.getFecha());
            producto.setCantidad(productoDetalles.getCantidad());
            producto.setPrecio(productoDetalles.getPrecio());
            producto.setPagado(productoDetalles.getPagado());
            producto.setFechaPago(productoDetalles.getFechaPago());
            producto.setNotas(productoDetalles.getNotas());
            producto.setAlumno(productoDetalles.getAlumno());

            return productoRepository.save(producto);
        } else {
            // Maneja el caso cuando el producto no existe
            throw new RuntimeException("Producto no encontrado con id " + id);
        }
    }
    
    @Override
    public void eliminarProducto(Long id) {
    	productoRepository.deleteById(id);
    }
}
