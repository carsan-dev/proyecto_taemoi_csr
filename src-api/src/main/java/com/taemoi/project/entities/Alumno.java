package com.taemoi.project.entities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.hibernate.LazyInitializationException;
import org.hibernate.annotations.BatchSize;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.taemoi.project.utils.ResponsableLegal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
public class Alumno extends EntidadOrganizacion {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "El nombre no puede estar en blanco")
	private String nombre;

	@NotBlank(message = "Los apellidos no pueden estar en blanco")
	private String apellidos;

	// Único por organización: UNIQUE (organizacion_id, numero_expediente) en V19.
	@NotNull(message = "El número de expediente no puede ser nulo")
	private Integer numeroExpediente;

	@NotNull(message = "La fecha de nacimiento no puede ser nula")
	@Temporal(TemporalType.DATE)
	private Date fechaNacimiento;

	@Column(length = 9, nullable = true)
	@Size(min = 9, max = 9, message = "El NIF debe tener 9 caracteres")
	private String nif;

	@NotBlank(message = "La dirección no puede estar en blanco")
	private String direccion;

	// VARCHAR(20) NULL desde V25: hay fichas en papel sin teléfono. Su obligatoriedad y formato se
	// validan en el alta (TelefonoUtils), no aquí.
	@Column(length = 20)
	@Size(max = 20, message = "El teléfono no puede tener más de 20 caracteres")
	private String telefono;

	@Column(length = 20)
	@Size(max = 20, message = "El teléfono secundario no puede tener más de 20 caracteres")
	private String telefono2;

	@Enumerated(EnumType.STRING)
	@Column(columnDefinition = "ENUM('FEMENINO','MASCULINO')")
	private Genero genero;

	@Column(length = 100)
	@Size(max = 100, message = "La localidad no puede tener más de 100 caracteres")
	private String localidad;

	@Column(name = "codigo_postal", length = 10)
	@Size(max = 10, message = "El código postal no puede tener más de 10 caracteres")
	private String codigoPostal;

	@Email(message = "La dirección de correo electrónico debe ser válida")
	private String email;

	// Tutores (V25). El responsable legal que expone la API es el primero marcado como tal
	// (ResponsableLegal); las columnas heredadas responsable_legal_* las borró V29.
	@JsonIgnore
	@OneToMany(mappedBy = "alumno", fetch = FetchType.LAZY)
	@OrderBy("orden ASC, id ASC")
	@BatchSize(size = 50)
	private List<Tutor> tutores = new ArrayList<>();

	@Temporal(TemporalType.DATE)
	private Date fechaAlta;

	@Temporal(TemporalType.DATE)
	private Date fechaAltaInicial;

	@NotNull(message = "El estado de la baja no puede ser nulo")
	private Boolean activo = true;

	@Temporal(TemporalType.DATE)
	private Date fechaBaja;

	@NotNull(message = "La autorización web no puede ser nula")
	private Boolean autorizacionWeb = true;

	@Lob
	@Column(columnDefinition = "LONGTEXT")
	private String observaciones;

	private Boolean tieneDiscapacidad = false;

	@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "foto_alumno_id")
	private Imagen fotoAlumno;
	
	@OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonManagedReference("alumno-documentos")
	private List<Documento> documentos = new ArrayList<>();

	// NUEVO: Relación de deportes del alumno (multi-deporte)
	@OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@JsonManagedReference
	private List<AlumnoDeporte> deportes = new ArrayList<>();
	
	@Column(nullable = false)
	private Boolean tieneDerechoExamen = false;

	@ManyToMany(mappedBy = "alumnos", fetch = FetchType.EAGER)
	private Set<Grupo> grupos = new LinkedHashSet<>();

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "alumno_turno", joinColumns = @JoinColumn(name = "alumno_id"), inverseJoinColumns = @JoinColumn(name = "turno_id"))
	@JsonManagedReference
	private List<Turno> turnos = new ArrayList<>();

	@OneToOne(mappedBy = "alumno")
	@JsonManagedReference
	private Usuario usuario;

	@OneToMany(mappedBy = "alumno")
	private List<ProductoAlumno> productosAlumno = new ArrayList<>();

	@OneToMany(mappedBy = "alumno")
	private List<AlumnoConvocatoria> convocatorias = new ArrayList<>();

	@Temporal(TemporalType.DATE)
	@Column(nullable = true)
	private Date fechaRetoDiarioCompletado;

	@Column(nullable = true)
	private Integer rachaRetoDiario = 0;

	/**
	 * Sede principal (V31, regla D9 del alcance por sede). Se escribe en el ALTA (entrega 6-1b, D6-2) y nunca en un
	 * {@code UPDATE} de la entidad ({@code updatable = false}): ningún {@code save} de una entidad a medio construir
	 * la pone a NULL. Los cambios posteriores van por {@code AlumnoRepository.asignarSedePrincipal}. Fuera del JSON.
	 */
	@JsonIgnore
	@Column(name = "sede_principal_id", insertable = true, updatable = false)
	private Long sedePrincipalId;

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

	public String getNif() {
		return nif;
	}

	public void setNif(String nif) {
		this.nif = nif;
	}

	public String getDireccion() {
		return direccion;
	}

	public void setDireccion(String direccion) {
		this.direccion = direccion;
	}

	public String getTelefono() {
		return telefono;
	}

	public void setTelefono(String telefono) {
		this.telefono = telefono;
	}

	public String getTelefono2() {
		return telefono2;
	}

	public void setTelefono2(String telefono2) {
		this.telefono2 = telefono2;
	}

	public Genero getGenero() {
		return genero;
	}

	public void setGenero(Genero genero) {
		this.genero = genero;
	}

	public String getLocalidad() {
		return localidad;
	}

	public void setLocalidad(String localidad) {
		this.localidad = localidad;
	}

	public String getCodigoPostal() {
		return codigoPostal;
	}

	public void setCodigoPostal(String codigoPostal) {
		this.codigoPostal = codigoPostal;
	}

	public List<Tutor> getTutores() {
		return tutores;
	}

	/*
	 * El JSON de la entidad (p. ej. /api/admin) llevaba las columnas heredadas responsable_legal_*;
	 * se mantienen esas claves, ahora desde el tutor responsable legal (solo lectura).
	 */
	@JsonGetter("responsableLegalNombre")
	public String responsableLegalNombreJson() {
		ResponsableLegal responsable = responsableLegalJson();
		return responsable == null ? null : responsable.nombre();
	}

	@JsonGetter("responsableLegalNif")
	public String responsableLegalNifJson() {
		ResponsableLegal responsable = responsableLegalJson();
		return responsable == null ? null : responsable.nif();
	}

	private ResponsableLegal responsableLegalJson() {
		try {
			return ResponsableLegal.de(this);
		} catch (LazyInitializationException e) {
			// Serializado fuera de sesión: no se sabe (antes se leía de las columnas del propio alumno).
			return null;
		}
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	// DEPRECATED: Categoria moved to AlumnoDeporte
	// public Categoria getCategoria() {
	// 	return categoria;
	// }

	// public void setCategoria(Categoria categoria) {
	// 	this.categoria = categoria;
	// }
	
	public Boolean getTieneDerechoExamen() {
		return tieneDerechoExamen;
	}

	public void setTieneDerechoExamen(Boolean tieneDerechoExamen) {
		this.tieneDerechoExamen = tieneDerechoExamen;
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

	public Boolean getActivo() {
		return activo;
	}

	public void setActivo(Boolean activo) {
		this.activo = activo;
	}

	public Date getFechaBaja() {
		return fechaBaja;
	}

	public void setFechaBaja(Date fechaBaja) {
		this.fechaBaja = fechaBaja;
	}

	public Boolean getAutorizacionWeb() {
		return autorizacionWeb;
	}

	public void setAutorizacionWeb(Boolean autorizacionWeb) {
		this.autorizacionWeb = autorizacionWeb;
	}

	public String getObservaciones() {
		return observaciones;
	}

	public void setObservaciones(String observaciones) {
		this.observaciones = observaciones;
	}

	public Boolean getTieneDiscapacidad() {
		return tieneDiscapacidad;
	}

	public void setTieneDiscapacidad(Boolean tieneDiscapacidad) {
		this.tieneDiscapacidad = tieneDiscapacidad;
	}

	public Imagen getFotoAlumno() {
		return fotoAlumno;
	}

	public void setFotoAlumno(Imagen fotoAlumno) {
		this.fotoAlumno = fotoAlumno;
	}

	public List<Documento> getDocumentos() {
		return documentos;
	}

	public void setDocumentos(List<Documento> documentos) {
		this.documentos = documentos;
	}

	public Set<Grupo> getGrupos() {
		return grupos;
	}

	public void setGrupos(Collection<Grupo> grupos) {
		this.grupos = grupos == null ? new LinkedHashSet<>() : new LinkedHashSet<>(grupos);
	}

	public List<Turno> getTurnos() {
		return turnos;
	}

	public void setTurnos(List<Turno> turnos) {
		this.turnos = turnos;
	}

	public void addTurno(Turno turno) {
		if (turno != null && this.turnos.stream().noneMatch(actual -> mismaEntidad(actual, turno))) {
			this.turnos.add(turno);
		}
		if (turno != null && turno.getAlumnos().stream().noneMatch(actual -> mismaEntidad(actual, this))) {
			turno.getAlumnos().add(this);
		}
	}

	public void removeTurno(Turno turno) {
		if (turno != null) {
			this.turnos.removeIf(actual -> mismaEntidad(actual, turno));
			turno.getAlumnos().removeIf(actual -> mismaEntidad(actual, this));
		}
	}

	private boolean mismaEntidad(Turno actual, Turno objetivo) {
		return actual == objetivo || actual.getId() != null && actual.getId().equals(objetivo.getId());
	}

	private boolean mismaEntidad(Alumno actual, Alumno objetivo) {
		return actual == objetivo || actual.getId() != null && actual.getId().equals(objetivo.getId());
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

	public List<ProductoAlumno> getProductosAlumno() {
		return productosAlumno;
	}

	public void setProductosAlumno(List<ProductoAlumno> productosAlumno) {
		this.productosAlumno = productosAlumno;
	}

	public List<AlumnoConvocatoria> getConvocatorias() {
		return convocatorias;
	}

	public void setConvocatorias(List<AlumnoConvocatoria> convocatorias) {
		this.convocatorias = convocatorias;
	}

	public Date getFechaRetoDiarioCompletado() {
		return fechaRetoDiarioCompletado;
	}

	public void setFechaRetoDiarioCompletado(Date fechaRetoDiarioCompletado) {
		this.fechaRetoDiarioCompletado = fechaRetoDiarioCompletado;
	}

	public Integer getRachaRetoDiario() {
		return rachaRetoDiario;
	}

	public void setRachaRetoDiario(Integer rachaRetoDiario) {
		this.rachaRetoDiario = rachaRetoDiario;
	}

	@JsonIgnore
	public Long getSedePrincipalId() {
		return sedePrincipalId;
	}

	/**
	 * Solo tiene efecto antes del primer {@code save} (alta): la columna no se actualiza con la entidad. Para cambiarla
	 * después, {@code AlumnoRepository.asignarSedePrincipal}.
	 */
	public void setSedePrincipalId(Long sedePrincipalId) {
		this.sedePrincipalId = sedePrincipalId;
	}

	public List<AlumnoDeporte> getDeportes() {
		return deportes;
	}

	public void setDeportes(List<AlumnoDeporte> deportes) {
		this.deportes = deportes;
	}

	public void addDeporte(AlumnoDeporte alumnoDeporte) {
		if (!this.deportes.contains(alumnoDeporte)) {
			this.deportes.add(alumnoDeporte);
			alumnoDeporte.setAlumno(this);
		}
	}

	public void removeDeporte(AlumnoDeporte alumnoDeporte) {
		this.deportes.remove(alumnoDeporte);
		alumnoDeporte.setAlumno(null);
	}
}
