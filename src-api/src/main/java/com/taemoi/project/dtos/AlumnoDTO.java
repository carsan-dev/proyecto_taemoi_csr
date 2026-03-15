package com.taemoi.project.dtos;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.taemoi.project.dtos.request.AlumnoDeporteCreacionDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.TipoTarifa;
import com.taemoi.project.utils.AlumnoDeporteUtils;
import com.taemoi.project.utils.FechaUtils;
import com.taemoi.project.utils.NifUtils;

public class AlumnoDTO {
	private final Long id;
	private String nombre;
	private String apellidos;
	private Date fechaNacimiento;
	private Integer numeroExpediente;
	private String nif;
	private String direccion;
	private String email;
	private Integer telefono;
	private Integer telefono2;
	private String observaciones;
	private Double cuantiaTarifa;
	private TipoTarifa tipoTarifa;
	private RolFamiliar rolFamiliar;
	private String grupoFamiliar;
	private Date fechaAlta;
	private Date fechaAltaInicial;
	private String antiguedad;
	private Date fechaBaja;
	private Boolean activo;
	private Boolean autorizacionWeb;
	private Boolean competidor;
	private Double peso;
	private Date fechaPeso;
	private Deporte deporte;
	private String categoria;
	private String grado;
	private Date fechaGrado;
	private Imagen fotoAlumno;
	private Boolean tieneLicencia;
	private Integer numeroLicencia;
	private Date fechaLicencia;
	private Boolean tieneDiscapacidad;
	private Boolean aptoParaExamen;

	/**
	 * Lista de deportes para asignar durante la creación del alumno
	 */
	private List<AlumnoDeporteCreacionDTO> deportesInicial = new ArrayList<>();

	/**
	 * Lista de deportes del alumno (para visualización en dashboard/listados)
	 */
	private List<AlumnoDeporteDTO> deportes = new ArrayList<>();

	public AlumnoDTO(final Long id, String nombre, String apellidos, Date fechaNacimiento, Integer numeroExpediente,
			String nif, String direccion, String email, Integer telefono, Integer telefono2, String observaciones, Double cuantiaTarifa, TipoTarifa tipoTarifa,
			RolFamiliar rolFamiliar, String grupoFamiliar, Date fechaAlta, Date fechaAltaInicial, String antiguedad,
			Date fechaBaja, Boolean activo, Boolean autorizacionWeb, Boolean competidor, Double peso, Date fechaPeso,
			Deporte deporte, String categoria, String grado, Date fechaGrado, Imagen fotoAlumno, Boolean tieneLicencia,
			Integer numeroLicencia, Date fechaLicencia, Boolean tieneDiscapacidad, Boolean aptoParaExamen) {
		this.id = id;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.fechaNacimiento = fechaNacimiento;
		this.numeroExpediente = numeroExpediente;
		this.nif = nif;
		this.direccion = direccion;
		this.email = email;
		this.telefono = telefono;
		this.telefono2 = telefono2;
		this.observaciones = observaciones;
		this.cuantiaTarifa = cuantiaTarifa;
		this.tipoTarifa = tipoTarifa;
		this.rolFamiliar = rolFamiliar;
		this.grupoFamiliar = grupoFamiliar;
		this.fechaAlta = fechaAlta;
		this.fechaAltaInicial = fechaAltaInicial;
		this.antiguedad = antiguedad;
		this.fechaBaja = fechaBaja;
		this.activo = activo;
		this.autorizacionWeb = autorizacionWeb;
		this.competidor = competidor;
		this.peso = peso;
		this.fechaPeso = fechaPeso;
		this.deporte = deporte;
		this.categoria = categoria;
		this.grado = grado;
		this.fechaGrado = fechaGrado;
		this.fotoAlumno = fotoAlumno;
		this.tieneLicencia = tieneLicencia;
		this.numeroLicencia = numeroLicencia;
		this.fechaLicencia = fechaLicencia;
		this.tieneDiscapacidad = tieneDiscapacidad;
		this.aptoParaExamen = aptoParaExamen;
	}

	public AlumnoDTO() {
		this.id = null;
	}

	public Long getId() {
		return id;
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

	public Date getFechaNacimiento() {
		return fechaNacimiento;
	}

	public void setFechaNacimiento(Date fechaNacimiento) {
		this.fechaNacimiento = fechaNacimiento;
	}

	public String getDireccion() {
		return direccion;
	}

	public void setDireccion(String direccion) {
		this.direccion = direccion;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Integer getTelefono() {
		return telefono;
	}

	public void setTelefono(Integer telefono) {
		this.telefono = telefono;
	}

	public Integer getTelefono2() {
		return telefono2;
	}

	public void setTelefono2(Integer telefono2) {
		this.telefono2 = telefono2;
	}

	public String getObservaciones() {
		return observaciones;
	}

	public void setObservaciones(String observaciones) {
		this.observaciones = observaciones;
	}

	public TipoTarifa getTipoTarifa() {
		return tipoTarifa;
	}

	public void setTipoTarifa(TipoTarifa tipoTarifa) {
		this.tipoTarifa = tipoTarifa;
	}

	public Date getFechaAlta() {
		return fechaAlta;
	}

	public void setFechaAlta(Date fechaAlta) {
		this.fechaAlta = fechaAlta;
	}

	public Date getFechaAltaInicial() {
		return fechaAltaInicial;
	}

	public void setFechaAltaInicial(Date fechaAltaInicial) {
		this.fechaAltaInicial = fechaAltaInicial;
	}

	public String getAntiguedad() {
		return antiguedad;
	}

	public void setAntiguedad(String antiguedad) {
		this.antiguedad = antiguedad;
	}

	public Date getFechaBaja() {
		return fechaBaja;
	}

	public void setFechaBaja(Date fechaBaja) {
		this.fechaBaja = fechaBaja;
	}

	public Boolean getActivo() {
		return activo;
	}

	public void setActivo(Boolean activo) {
		this.activo = activo;
	}

	public Boolean getAutorizacionWeb() {
		return autorizacionWeb;
	}

	public void setAutorizacionWeb(Boolean autorizacionWeb) {
		this.autorizacionWeb = autorizacionWeb;
	}

	public Boolean getCompetidor() {
		return competidor;
	}

	public void setCompetidor(Boolean competidor) {
		this.competidor = competidor;
	}

	public Double getPeso() {
		return peso;
	}

	public void setPeso(Double peso) {
		this.peso = peso;
	}

	public Date getFechaPeso() {
		return fechaPeso;
	}

	public Deporte getDeporte() {
		return deporte;
	}

	public void setDeporte(Deporte deporte) {
		this.deporte = deporte;
	}

	public void setFechaPeso(Date fechaPeso) {
		this.fechaPeso = fechaPeso;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public String getGrado() {
		return grado;
	}

	public void setGrado(String grado) {
		this.grado = grado;
	}

	public Date getFechaGrado() {
		return fechaGrado;
	}

	public void setFechaGrado(Date fechaGrado) {
		this.fechaGrado = fechaGrado;
	}

	public Integer getNumeroExpediente() {
		return numeroExpediente;
	}

	public void setNumeroExpediente(Integer numeroExpediente) {
		this.numeroExpediente = numeroExpediente;
	}

	public String getNif() {
		return nif;
	}

	public void setNif(String nif) {
		this.nif = nif;
	}

	public Double getCuantiaTarifa() {
		return cuantiaTarifa;
	}

	public void setCuantiaTarifa(Double cuantiaTarifa) {
		this.cuantiaTarifa = cuantiaTarifa;
	}

	public RolFamiliar getRolFamiliar() {
		return rolFamiliar;
	}

	public void setRolFamiliar(RolFamiliar rolFamiliar) {
		this.rolFamiliar = rolFamiliar;
	}

	public String getGrupoFamiliar() {
		return grupoFamiliar;
	}

	public void setGrupoFamiliar(String grupoFamiliar) {
		this.grupoFamiliar = grupoFamiliar;
	}

	public Imagen getFotoAlumno() {
		return fotoAlumno;
	}

	public void setFotoAlumno(Imagen fotoAlumno) {
		this.fotoAlumno = fotoAlumno;
	}

	public Boolean getTieneLicencia() {
		return tieneLicencia;
	}

	public void setTieneLicencia(Boolean tieneLicencia) {
		this.tieneLicencia = tieneLicencia;
	}

	public Integer getNumeroLicencia() {
		return numeroLicencia;
	}

	public void setNumeroLicencia(Integer numeroLicencia) {
		this.numeroLicencia = numeroLicencia;
	}

	public Date getFechaLicencia() {
		return fechaLicencia;
	}

	public void setFechaLicencia(Date fechaLicencia) {
		this.fechaLicencia = fechaLicencia;
	}

	public Boolean getTieneDiscapacidad() {
		return tieneDiscapacidad;
	}

	public void setTieneDiscapacidad(Boolean tieneDiscapacidad) {
		this.tieneDiscapacidad = tieneDiscapacidad;
	}

	public Boolean getAptoParaExamen() {
		return aptoParaExamen;
	}

	public void setAptoParaExamen(Boolean aptoParaExamen) {
		this.aptoParaExamen = aptoParaExamen;
	}

	public List<AlumnoDeporteCreacionDTO> getDeportesInicial() {
		return deportesInicial;
	}

	public void setDeportesInicial(List<AlumnoDeporteCreacionDTO> deportesInicial) {
		this.deportesInicial = deportesInicial;
	}

	public List<AlumnoDeporteDTO> getDeportes() {
		return deportes;
	}

	public void setDeportes(List<AlumnoDeporteDTO> deportes) {
		this.deportes = deportes;
	}

	/**
	 * Convierte un objeto Alumno en un objeto AlumnoDTO.
	 *
	 * @param alumno El objeto Alumno a convertir.
	 * @return El objeto AlumnoDTO resultante, o null si el parámetro alumno es
	 *         null.
	 */
	public static AlumnoDTO deAlumno(Alumno alumno) {
		if (alumno == null) {
			return null;
		}

		AlumnoDeporte deportePrincipal = AlumnoDeporteUtils.seleccionarDeportePrincipal(alumno.getDeportes());

		TipoTarifa tipoTarifa = deportePrincipal != null ? deportePrincipal.getTipoTarifa() : alumno.getTipoTarifa();
		Double cuantiaTarifa = deportePrincipal != null ? deportePrincipal.getCuantiaTarifa() : alumno.getCuantiaTarifa();
		RolFamiliar rolFamiliar = deportePrincipal != null ? deportePrincipal.getRolFamiliar() : alumno.getRolFamiliar();
		String grupoFamiliar = deportePrincipal != null ? deportePrincipal.getGrupoFamiliar() : alumno.getGrupoFamiliar();

		// Categoria is now per-sport (in AlumnoDeporte), not global
		String categoriaNombre = deportePrincipal != null && deportePrincipal.getCategoria() != null
				? deportePrincipal.getCategoria().getNombre()
				: null;
		String gradoTipo = null;
		if (deportePrincipal != null && deportePrincipal.getGrado() != null) {
			gradoTipo = deportePrincipal.getGrado().getTipoGrado().name();
		} else if (alumno.getGrado() != null) {
			gradoTipo = alumno.getGrado().getTipoGrado().name();
		}

		Integer telefono = null;
		if (alumno.getTelefono() != null) {
			telefono = Integer.valueOf(alumno.getTelefono());
		}

		Integer telefono2 = null;
		if (alumno.getTelefono2() != null) {
			telefono2 = Integer.valueOf(alumno.getTelefono2());
		}

		// Calcular antigüedad desde fechaAltaInicial
		String antiguedad = FechaUtils.calcularAntiguedad(alumno.getFechaAltaInicial());

		Boolean competidor = deportePrincipal != null ? deportePrincipal.getCompetidor() : alumno.getCompetidor();
		Double peso = deportePrincipal != null ? deportePrincipal.getPeso() : alumno.getPeso();
		Date fechaPeso = deportePrincipal != null ? deportePrincipal.getFechaPeso() : alumno.getFechaPeso();
		Deporte deporte = deportePrincipal != null ? deportePrincipal.getDeporte() : alumno.getDeporte();
		Date fechaGrado = deportePrincipal != null ? deportePrincipal.getFechaGrado() : alumno.getFechaGrado();
		Boolean tieneLicencia = deportePrincipal != null ? deportePrincipal.getTieneLicencia() : alumno.getTieneLicencia();
		Integer numeroLicencia = deportePrincipal != null ? deportePrincipal.getNumeroLicencia() : alumno.getNumeroLicencia();
		Date fechaLicencia = deportePrincipal != null ? deportePrincipal.getFechaLicencia() : alumno.getFechaLicencia();
		Boolean aptoParaExamen = deportePrincipal != null ? deportePrincipal.getAptoParaExamen() : alumno.getAptoParaExamen();

		AlumnoDTO dto = new AlumnoDTO(alumno.getId(), alumno.getNombre(), alumno.getApellidos(), alumno.getFechaNacimiento(),
				alumno.getNumeroExpediente(), NifUtils.normalizeForStorage(alumno.getNif()), alumno.getDireccion(), alumno.getEmail(), telefono, telefono2,
				alumno.getObservaciones(), cuantiaTarifa, tipoTarifa, rolFamiliar, grupoFamiliar,
				alumno.getFechaAlta(), alumno.getFechaAltaInicial(), antiguedad, alumno.getFechaBaja(), alumno.getActivo(),
				alumno.getAutorizacionWeb(),
				competidor,
				peso,
				fechaPeso,
				deporte,
				categoriaNombre, gradoTipo,
				fechaGrado,
				alumno.getFotoAlumno(),
				tieneLicencia,
				numeroLicencia,
				fechaLicencia,
				alumno.getTieneDiscapacidad(),
				aptoParaExamen);

		// Populate deportes list from AlumnoDeporte entities
		if (alumno.getDeportes() != null && !alumno.getDeportes().isEmpty()) {
			List<AlumnoDeporteDTO> deportesDTOs = alumno.getDeportes().stream()
					.map(AlumnoDeporteDTO::deAlumnoDeporte)
					.collect(java.util.stream.Collectors.toList());
			dto.setDeportes(deportesDTOs);
		}

		return dto;
	}

	/**
	 * Convierte un objeto Alumno en un objeto AlumnoDTO usando el deporte indicado
	 * como referencia (si existe en el alumno).
	 */
	public static AlumnoDTO deAlumno(Alumno alumno, Deporte deporte) {
		if (alumno == null) {
			return null;
		}

		if (deporte == null) {
			return deAlumno(alumno);
		}

		AlumnoDeporte deporteSeleccionado = null;
		if (alumno.getDeportes() != null && !alumno.getDeportes().isEmpty()) {
			deporteSeleccionado = alumno.getDeportes().stream()
					.filter(ad -> deporte.equals(ad.getDeporte()) && Boolean.TRUE.equals(ad.getActivo()))
					.findFirst()
					.orElse(null);

			if (deporteSeleccionado == null) {
				deporteSeleccionado = alumno.getDeportes().stream()
						.filter(ad -> deporte.equals(ad.getDeporte()))
						.findFirst()
						.orElse(null);
			}
		}

		if (deporteSeleccionado == null) {
			return deAlumno(alumno);
		}

		TipoTarifa tipoTarifa = deporteSeleccionado.getTipoTarifa();
		Double cuantiaTarifa = deporteSeleccionado.getCuantiaTarifa();
		RolFamiliar rolFamiliar = deporteSeleccionado.getRolFamiliar();
		String grupoFamiliar = deporteSeleccionado.getGrupoFamiliar();

		// Categoria is now per-sport (in AlumnoDeporte), not global
		String categoriaNombre = deporteSeleccionado.getCategoria() != null
				? deporteSeleccionado.getCategoria().getNombre()
				: null;
		String gradoTipo = null;
		if (deporteSeleccionado.getGrado() != null) {
			gradoTipo = deporteSeleccionado.getGrado().getTipoGrado().name();
		} else if (alumno.getGrado() != null) {
			gradoTipo = alumno.getGrado().getTipoGrado().name();
		}

		Integer telefono = null;
		if (alumno.getTelefono() != null) {
			telefono = Integer.valueOf(alumno.getTelefono());
		}

		Integer telefono2 = null;
		if (alumno.getTelefono2() != null) {
			telefono2 = Integer.valueOf(alumno.getTelefono2());
		}

		// Calcular antiguedad desde fechaAltaInicial
		String antiguedad = FechaUtils.calcularAntiguedad(alumno.getFechaAltaInicial());

		Boolean competidor = deporteSeleccionado.getCompetidor();
		Double peso = deporteSeleccionado.getPeso();
		Date fechaPeso = deporteSeleccionado.getFechaPeso();
		Deporte deporteAlumno = deporteSeleccionado.getDeporte();
		Date fechaGrado = deporteSeleccionado.getFechaGrado();
		Boolean tieneLicencia = deporteSeleccionado.getTieneLicencia();
		Integer numeroLicencia = deporteSeleccionado.getNumeroLicencia();
		Date fechaLicencia = deporteSeleccionado.getFechaLicencia();
		Boolean aptoParaExamen = deporteSeleccionado.getAptoParaExamen();

		AlumnoDTO dto = new AlumnoDTO(alumno.getId(), alumno.getNombre(), alumno.getApellidos(), alumno.getFechaNacimiento(),
				alumno.getNumeroExpediente(), NifUtils.normalizeForStorage(alumno.getNif()), alumno.getDireccion(), alumno.getEmail(), telefono, telefono2,
				alumno.getObservaciones(), cuantiaTarifa, tipoTarifa, rolFamiliar, grupoFamiliar,
				alumno.getFechaAlta(), alumno.getFechaAltaInicial(), antiguedad, alumno.getFechaBaja(), alumno.getActivo(),
				alumno.getAutorizacionWeb(),
				competidor,
				peso,
				fechaPeso,
				deporteAlumno,
				categoriaNombre, gradoTipo,
				fechaGrado,
				alumno.getFotoAlumno(),
				tieneLicencia,
				numeroLicencia,
				fechaLicencia,
				alumno.getTieneDiscapacidad(),
				aptoParaExamen);

		// Populate deportes list from AlumnoDeporte entities
		if (alumno.getDeportes() != null && !alumno.getDeportes().isEmpty()) {
			List<AlumnoDeporteDTO> deportesDTOs = alumno.getDeportes().stream()
					.map(AlumnoDeporteDTO::deAlumnoDeporte)
					.collect(java.util.stream.Collectors.toList());
			dto.setDeportes(deportesDTOs);
		}

		return dto;
	}
}
