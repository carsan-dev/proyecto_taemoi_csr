package com.taemoi.project.servicios.impl;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.AlumnoConvocatoria;
import com.taemoi.project.entidades.Convocatoria;
import com.taemoi.project.entidades.Deporte;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.ProductoAlumno;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.repositorios.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.ConvocatoriaRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.repositorios.ProductoAlumnoRepository;
import com.taemoi.project.servicios.AlumnoService;
import com.taemoi.project.servicios.ConvocatoriaService;

@Service
public class ConvocatoriaServiceImpl implements ConvocatoriaService {

	@Autowired
	private ConvocatoriaRepository convocatoriaRepository;

	@Autowired
	private AlumnoConvocatoriaRepository alumnoConvocatoriaRepository;

	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;

	@Autowired
	private AlumnoRepository alumnoRepository;

	@Autowired
	private AlumnoService alumnoService;

	@Autowired
	private GradoRepository gradoRepository;

	@Override
	public ConvocatoriaDTO crearConvocatoria(ConvocatoriaDTO convocatoriaDTO) {
		Convocatoria convocatoria = new Convocatoria();
		convocatoria.setFechaConvocatoria(convocatoriaDTO.getFechaConvocatoria());
		convocatoria.setDeporte(convocatoriaDTO.getDeporte());
		Convocatoria convocatoriaGuardada = convocatoriaRepository.save(convocatoria);

		return convertirAConvocatoriaDTO(convocatoriaGuardada);
	}

	@Override
	public List<ConvocatoriaDTO> obtenerConvocatorias() {
		return convocatoriaRepository.findAll().stream().map(this::convertirAConvocatoriaDTO)
				.collect(Collectors.toList());
	}

	@Override
	public ConvocatoriaDTO obtenerConvocatoriaPorId(Long id) {
		Convocatoria convocatoria = convocatoriaRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + id));

		return convertirAConvocatoriaDTO(convocatoria);
	}

	@Override
	public List<ConvocatoriaDTO> obtenerConvocatoriasDeAlumno(Long alumnoId) {
		List<AlumnoConvocatoria> relaciones = alumnoConvocatoriaRepository.findByAlumnoId(alumnoId);
		return relaciones.stream().map(ac -> convertirAConvocatoriaDTO(ac.getConvocatoria()))
				.collect(Collectors.toList());
	}

	@Override
	public Optional<ConvocatoriaDTO> obtenerConvocatoriaActualPorDeporte(Deporte deporte) {
		return convocatoriaRepository.findConvocatoriaActualPorDeporte(deporte).map(this::convertirAConvocatoriaDTO);
	}

	@Override
	public List<ConvocatoriaDTO> obtenerConvocatoriasPorDeporte(Deporte deporte) {
		return convocatoriaRepository.findByDeporte(deporte).stream().map(this::convertirAConvocatoriaDTO)
				.collect(Collectors.toList());
	}

	@Override
	public List<AlumnoConvocatoriaDTO> obtenerAlumnosDeConvocatoria(Long convocatoriaId) {
		Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId).orElseThrow(
				() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + convocatoriaId));

		return convocatoria.getAlumnosConvocatoria().stream()
				.map(alumnoConvocatoria -> new AlumnoConvocatoriaDTO(alumnoConvocatoria.getId(),
						alumnoConvocatoria.getAlumno().getId(), alumnoConvocatoria.getAlumno().getNombre(),
						alumnoConvocatoria.getAlumno().getApellidos(), alumnoConvocatoria.getCuantiaExamen(),
						alumnoConvocatoria.getGradoActual(), alumnoConvocatoria.getGradoSiguiente(),
						alumnoConvocatoria.getPagado()))
				.collect(Collectors.toList());
	}

	@Override
	public void eliminarConvocatoria(Long convocatoriaId) {
		Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId).orElseThrow(
				() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + convocatoriaId));

		for (AlumnoConvocatoria alumnoConvocatoria : convocatoria.getAlumnosConvocatoria()) {
			Alumno alumno = alumnoConvocatoria.getAlumno();

			ProductoAlumno productoAlumno = alumnoConvocatoria.getProductoAlumno();
			if (productoAlumno != null) {
				alumnoConvocatoria.setProductoAlumno(null);
				alumno.getProductosAlumno().remove(productoAlumno);
				productoAlumnoRepository.delete(productoAlumno);
			}
			if (alumno != null) {
				alumno.getConvocatorias().remove(alumnoConvocatoria);
				alumnoRepository.save(alumno);
			}
			alumnoConvocatoriaRepository.delete(alumnoConvocatoria);
		}
		convocatoriaRepository.delete(convocatoria);
	}

	@Override
	public void actualizarGradosDeConvocatoria(Long convocatoriaId) {
		Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId)
				.orElseThrow(() -> new IllegalArgumentException("Convocatoria no encontrada"));

		List<AlumnoConvocatoria> alumnosConvocatoria = convocatoria.getAlumnosConvocatoria();

		for (AlumnoConvocatoria alumnoConvocatoria : alumnosConvocatoria) {
			Alumno alumno = alumnoConvocatoria.getAlumno();

			TipoGrado nuevoGrado = alumnoService.calcularSiguienteGrado(alumno);
			if (nuevoGrado != null) {
				Grado nuevoGradoEntidad = gradoRepository.findByTipoGrado(nuevoGrado);

				alumno.setGrado(nuevoGradoEntidad);
				alumno.setFechaGrado(new Date());
				alumno.setAptoParaExamen(alumnoService.esAptoParaExamen(alumno));
				alumnoRepository.save(alumno);
			}
		}
	}

	@Override
	public void actualizarAlumnoConvocatoria(Long alumnoConvocatoriaId, AlumnoConvocatoriaDTO alumnoConvocatoriaDTO) {
		AlumnoConvocatoria alumnoConvocatoria = alumnoConvocatoriaRepository.findById(alumnoConvocatoriaId)
				.orElseThrow(() -> new IllegalArgumentException("AlumnoConvocatoria no encontrado"));

		alumnoConvocatoria.setCuantiaExamen(alumnoConvocatoriaDTO.getCuantiaExamen());
		alumnoConvocatoria.setPagado(alumnoConvocatoriaDTO.getPagado());

		if (alumnoConvocatoriaDTO.getPagado()) {
			alumnoConvocatoria.setFechaPago(new Date());
		} else {
			alumnoConvocatoria.setFechaPago(null);
		}

		alumnoConvocatoriaRepository.save(alumnoConvocatoria);

		Alumno alumno = alumnoConvocatoria.getAlumno();

		if (alumno.getFechaGrado() == null) {
			alumno.setFechaGrado(new Date());
		}

		productoAlumnoRepository.findByAlumnoIdAndProductoId(alumno.getId(),
				alumnoConvocatoria.getProductoAlumno().getProducto().getId()).ifPresent(productoAlumno -> {
					productoAlumno.setPrecio(alumnoConvocatoriaDTO.getCuantiaExamen());
					productoAlumno.setPagado(alumnoConvocatoriaDTO.getPagado());
					productoAlumno.setFechaPago(alumnoConvocatoriaDTO.getPagado() ? new Date() : null);
					productoAlumnoRepository.save(productoAlumno);
				});
	}

	private ConvocatoriaDTO convertirAConvocatoriaDTO(Convocatoria convocatoria) {
		ConvocatoriaDTO dto = new ConvocatoriaDTO();
		dto.setId(convocatoria.getId());
		dto.setFechaConvocatoria(convocatoria.getFechaConvocatoria());
		dto.setDeporte(convocatoria.getDeporte());
		return dto;
	}
}
