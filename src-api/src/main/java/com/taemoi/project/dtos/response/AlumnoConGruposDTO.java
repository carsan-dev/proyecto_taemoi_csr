package com.taemoi.project.dtos.response;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.utils.AlumnoDeporteUtils;

public class AlumnoConGruposDTO {
	private Long id;
	private String nombre;
	private String apellidos;
	private String grado;
	private Integer numeroExpediente;
	private Date fechaNacimiento;
	private List<GrupoResponseDTO> grupos;

	public AlumnoConGruposDTO(Long id, String nombre, String apellidos, Date fechaNacimiento, Integer numeroExpediente,
			String grado, List<GrupoResponseDTO> grupos) {
		this.id = id;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.fechaNacimiento = fechaNacimiento;
		this.numeroExpediente = numeroExpediente;
		this.grado = grado;
		this.grupos = grupos;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}

	public String getGrado() {
		return grado;
	}

	public void setGrado(String grado) {
		this.grado = grado;
	}

	public Integer getNumeroExpediente() {
		return numeroExpediente;
	}

	public void setNumeroExpediente(Integer numeroExpediente) {
		this.numeroExpediente = numeroExpediente;
	}

	public Date getFechaNacimiento() {
		return fechaNacimiento;
	}

	public void setFechaNacimiento(Date fechaNacimiento) {
		this.fechaNacimiento = fechaNacimiento;
	}

	public List<GrupoResponseDTO> getGrupos() {
		return grupos;
	}

	public void setGrupos(List<GrupoResponseDTO> grupos) {
		this.grupos = grupos;
	}

	// Método para convertir un objeto Alumno a AlumnoConGruposDTO
	public static AlumnoConGruposDTO deAlumnoConGrupos(Alumno alumno) {
		if (alumno == null) {
			return null;
		}

		// Convertimos la lista de grupos de Alumno a GrupoDTO
		List<GrupoResponseDTO> gruposDTO = alumno.getGrupos().stream()
				.map(grupo -> new GrupoResponseDTO(grupo.getId(), grupo.getNombre())).collect(Collectors.toList());

		String grado = null;
		AlumnoDeporte principal = AlumnoDeporteUtils.seleccionarDeportePrincipal(alumno.getDeportes());
		if (principal != null && principal.getGrado() != null) {
			grado = principal.getGrado().getTipoGrado().name();
		} else if (alumno.getGrado() != null) {
			grado = alumno.getGrado().getTipoGrado().name();
		}

		return new AlumnoConGruposDTO(alumno.getId(), alumno.getNombre(), alumno.getApellidos(),
				alumno.getFechaNacimiento(), alumno.getNumeroExpediente(), grado, gruposDTO);
	}

	// MǸtodo para convertir usando datos por deporte
	public static AlumnoConGruposDTO deAlumnoConGrupos(Alumno alumno, AlumnoDeporte alumnoDeporte) {
		if (alumno == null) {
			return null;
		}

		List<GrupoResponseDTO> gruposDTO = alumno.getGrupos().stream()
				.map(grupo -> new GrupoResponseDTO(grupo.getId(), grupo.getNombre())).collect(Collectors.toList());

		String grado = alumnoDeporte != null && alumnoDeporte.getGrado() != null
				? alumnoDeporte.getGrado().getTipoGrado().name()
				: null;

		return new AlumnoConGruposDTO(alumno.getId(), alumno.getNombre(), alumno.getApellidos(),
				alumno.getFechaNacimiento(), alumno.getNumeroExpediente(), grado, gruposDTO);
	}
}
