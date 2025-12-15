package com.taemoi.project.dtos;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.taemoi.project.dtos.request.AlumnoDeporteCreacionDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.TipoTarifa;
import com.taemoi.project.utils.FechaUtils;

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

	public AlumnoDTO(final Long id, String nombre, String apellidos, Date fechaNacimiento, Integer numeroExpediente,
			String nif, String direccion, String email, Integer telefono, Double cuantiaTarifa, TipoTarifa tipoTarifa,
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

		String categoriaNombre = alumno.getCategoria() != null ? alumno.getCategoria().getNombre() : null;
		String gradoTipo = alumno.getGrado() != null && alumno.getGrado().getTipoGrado() != null
				? alumno.getGrado().getTipoGrado().name()
				: null;

		Integer telefono = null;
		if (alumno.getTelefono() != null) {
			telefono = Integer.valueOf(alumno.getTelefono());
		}

		// Calcular antigüedad desde fechaAltaInicial
		String antiguedad = FechaUtils.calcularAntiguedad(alumno.getFechaAltaInicial());

		return new AlumnoDTO(alumno.getId(), alumno.getNombre(), alumno.getApellidos(), alumno.getFechaNacimiento(),
				alumno.getNumeroExpediente(), alumno.getNif(), alumno.getDireccion(), alumno.getEmail(), telefono,
				alumno.getCuantiaTarifa(), alumno.getTipoTarifa(), alumno.getRolFamiliar(), alumno.getGrupoFamiliar(),
				alumno.getFechaAlta(), alumno.getFechaAltaInicial(), antiguedad, alumno.getFechaBaja(), alumno.getActivo(),
				alumno.getAutorizacionWeb(), alumno.getCompetidor(), alumno.getPeso(), alumno.getFechaPeso(), alumno.getDeporte(),
				categoriaNombre, gradoTipo, alumno.getFechaGrado(), alumno.getFotoAlumno(), alumno.getTieneLicencia(),
				alumno.getNumeroLicencia(), alumno.getFechaLicencia(), alumno.getTieneDiscapacidad(),
				alumno.getAptoParaExamen());
	}
}
