package com.taemoi.project.services.impl;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoConvocatoria;
import com.taemoi.project.entities.AlumnoDeporte;
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
import com.taemoi.project.services.ConvocatoriaService;
import com.taemoi.project.utils.FechaUtils;

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
	private GradoRepository gradoRepository;

	@Autowired
	private com.taemoi.project.services.AlumnoDeporteService alumnoDeporteService;

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
	public List<AlumnoConvocatoriaReporteDTO> obtenerReporteDeConvocatoria(Long convocatoriaId) {
		Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId).orElseThrow(
				() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + convocatoriaId));

		return convocatoria.getAlumnosConvocatoria().stream()
				.map(alumnoConvocatoria -> {
					Alumno alumno = alumnoConvocatoria.getAlumno();
					String nombreCompleto = alumno.getNombre() + " " + alumno.getApellidos();
					Integer edad = FechaUtils.calcularEdad(alumno.getFechaNacimiento());
					// Get per-sport data from AlumnoDeporte
					AlumnoDeporte alumnoDeporte = alumnoConvocatoria.getAlumnoDeporte();
					if (alumnoDeporte == null && alumno != null) {
						alumnoDeporte = alumno.getDeportes().stream()
								.filter(ad -> ad.getDeporte() == convocatoria.getDeporte())
								.findFirst()
								.orElse(null);
					}
					String categoriaNombre = alumnoDeporte != null && alumnoDeporte.getCategoria() != null
							? alumnoDeporte.getCategoria().getNombre()
							: null;
					Integer numeroLicencia = alumnoDeporte != null ? alumnoDeporte.getNumeroLicencia() : null;
					Double peso = alumnoDeporte != null ? alumnoDeporte.getPeso() : null;

					return new AlumnoConvocatoriaReporteDTO(
							alumno.getId(),
							nombreCompleto,
							alumno.getNumeroExpediente(),
							numeroLicencia,
							edad,
							categoriaNombre,
							peso,
							alumnoConvocatoria.getPagado(),
							alumnoConvocatoria.getGradoActual(),
							alumnoConvocatoria.getGradoSiguiente()
					);
				})
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
	        AlumnoDeporte alumnoDeporte = ac.getAlumnoDeporte();
	        if (alumnoDeporte == null && ac.getAlumno() != null) {
	        	alumnoDeporte = alumnoDeporteService.obtenerDeportesDelAlumno(ac.getAlumno().getId()).stream()
	        			.filter(ad -> ad.getDeporte() == convocatoria.getDeporte())
	        			.findFirst()
	        			.orElse(null);
	        }

	        if (alumnoDeporte == null) {
	        	continue;
	        }

	        TipoGrado nuevoTipo = alumnoDeporteService.calcularSiguienteGrado(alumnoDeporte);
	        if (nuevoTipo != null) {
	        	Grado nuevoGrado = gradoRepository.findByTipoGrado(nuevoTipo);
	        	if (nuevoGrado != null) {
	        		alumnoDeporteService.actualizarGradoPorDeporte(
	        				alumnoDeporte.getAlumno().getId(),
	        				alumnoDeporte.getDeporte(),
	        				nuevoTipo
	        		);
	        	}
	        }

	        Alumno alumno = alumnoDeporte.getAlumno();
	        if (alumno != null) {
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
		AlumnoDeporte alumnoDeporte = alumnoConvocatoria.getAlumnoDeporte();
		if (alumnoDeporte == null && alumno != null) {
			alumnoDeporte = alumnoDeporteService.obtenerDeportesDelAlumno(alumno.getId()).stream()
					.filter(ad -> ad.getDeporte() == alumnoConvocatoria.getConvocatoria().getDeporte())
					.findFirst()
					.orElse(null);
		}

		if (alumnoDeporte != null && alumnoDeporte.getFechaGrado() == null) {
			alumnoDeporteService.actualizarFechaGrado(
					alumnoDeporte.getAlumno().getId(),
					alumnoDeporte.getDeporte(),
					new Date()
			);
		}

		ProductoAlumno productoAlumno = alumnoConvocatoria.getProductoAlumno();
		if (productoAlumno != null) {
			productoAlumno.setPrecio(alumnoConvocatoriaDTO.getCuantiaExamen());
			productoAlumno.setPagado(alumnoConvocatoriaDTO.getPagado());
			productoAlumno.setFechaPago(alumnoConvocatoriaDTO.getPagado() ? new Date() : null);
			productoAlumnoRepository.save(productoAlumno);
		}

		if (alumno != null) {
			alumno.setTieneDerechoExamen(alumnoConvocatoriaDTO.getPagado());
			alumnoRepository.save(alumno);
		}
	}

	private ConvocatoriaDTO convertirAConvocatoriaDTO(Convocatoria convocatoria) {
		ConvocatoriaDTO dto = new ConvocatoriaDTO();
		dto.setId(convocatoria.getId());
		dto.setFechaConvocatoria(convocatoria.getFechaConvocatoria());
		dto.setDeporte(convocatoria.getDeporte());
		return dto;
	}
}
