package com.taemoi.project.services.impl;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoConvocatoria;
import com.taemoi.project.entities.Convocatoria;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.repositories.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ConvocatoriaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.ConvocatoriaService;

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
				alumno.setTieneDerechoExamen(false);
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

	    for (AlumnoConvocatoria ac : convocatoria.getAlumnosConvocatoria()) {
	        Alumno alumno = ac.getAlumno();
	        TipoGrado nuevoTipo = alumnoService.calcularSiguienteGrado(alumno);
	        if (nuevoTipo != null) {
	            Grado nuevoGrado = gradoRepository.findByTipoGrado(nuevoTipo);
	            alumno.setGrado(nuevoGrado);
	            alumno.setFechaGrado(new Date());
	            alumno.setAptoParaExamen(alumnoService.esAptoParaExamen(alumno));

	            alumno.setTieneDerechoExamen(false);

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
	    alumno.setTieneDerechoExamen(alumnoConvocatoriaDTO.getPagado());
	    alumnoRepository.save(alumno);
	}

	private ConvocatoriaDTO convertirAConvocatoriaDTO(Convocatoria convocatoria) {
		ConvocatoriaDTO dto = new ConvocatoriaDTO();
		dto.setId(convocatoria.getId());
		dto.setFechaConvocatoria(convocatoria.getFechaConvocatoria());
		dto.setDeporte(convocatoria.getDeporte());
		return dto;
	}
}
