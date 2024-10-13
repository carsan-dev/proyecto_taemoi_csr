package com.taemoi.project.entidades;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

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
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Entity
public class Alumno {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "El nombre no puede estar en blanco")
	private String nombre;

	@NotBlank(message = "Los apellidos no pueden estar en blanco")
	private String apellidos;

	@Column(unique = true)
	@NotNull(message = "El número de expediente no puede ser nulo")
	private Integer numeroExpediente;

	@NotNull(message = "La fecha de nacimiento no puede ser nula")
	@Temporal(TemporalType.DATE)
	private Date fechaNacimiento;

	@NotBlank(message = "El NIF no puede estar en blanco")
    @Size(min = 9, max = 9, message = "El NIF debe tener 9 caracteres")
	private String nif;

	@NotBlank(message = "La dirección no puede estar en blanco")
	private String direccion;

	@NotNull(message = "El teléfono no puede ser nulo")
    @Min(value = 100000000, message = "El teléfono debe tener 9 dígitos")
    @Max(value = 999999999, message = "El teléfono debe tener 9 dígitos")
	private Integer telefono;

	@Email(message = "La dirección de correo electrónico debe ser válida")
	private String email;

	@NotNull(message = "El tipo de tarifa no puede ser nulo")
	@Enumerated(EnumType.STRING)
	private TipoTarifa tipoTarifa;

	@NotNull(message = "La cuantía de la tarifa no puede ser nula")
	@PositiveOrZero(message = "La cuantía de la tarifa debe ser un valor positivo o cero")
	private Double cuantiaTarifa;

	@Temporal(TemporalType.DATE)
	private Date fechaAlta;
	
	@NotNull(message = "El estado de la baja no puede ser nulo")
	private Boolean activo = true;
	
	@Temporal(TemporalType.DATE)
	private Date fechaBaja;
	
	@NotNull(message = "La autorización web no puede ser nula")
	private Boolean autorizacionWeb = true;
	
    @NotNull(message = "Si es competidor o no debe ser true o false, no null")
    private Boolean competidor = false;

    private Double peso;

    @Temporal(TemporalType.DATE)
    private Date fechaPeso;
	
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "foto_alumno_id")
    private Imagen fotoAlumno;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "categoria_id")
	@JsonManagedReference
	private Categoria categoria;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "grado_id")
	@JsonManagedReference
	private Grado grado;
	
    @ManyToMany(mappedBy = "alumnos", fetch = FetchType.EAGER)
	private List<Grupo> grupos = new ArrayList<>();;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "alumno_turno",
		joinColumns = @JoinColumn(name = "alumno_id"),
		inverseJoinColumns = @JoinColumn(name = "turno_id"))
	@JsonManagedReference
	private List<Turno> turnos = new ArrayList<>();
    
    @OneToOne(mappedBy = "alumno")
    @JsonManagedReference
    private Usuario usuario;

	@OneToMany(mappedBy = "alumno")
	private List<Examen> examenes;

	@OneToMany(mappedBy = "alumno")
	private List<Pago> pagos;

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

	public List<Examen> getExamenes() {
		return examenes;
	}

	public void setExamenes(List<Examen> examenes) {
		this.examenes = examenes;
	}

	public List<Pago> getPagos() {
		return pagos;
	}

	public void setPagos(List<Pago> pagos) {
		this.pagos = pagos;
	}

	public TipoTarifa getTipoTarifa() {
		return tipoTarifa;
	}

	public void setTipoTarifa(TipoTarifa tipoTarifa) {
		this.tipoTarifa = tipoTarifa;
	}

	public Double getCuantiaTarifa() {
		return cuantiaTarifa;
	}

	public void setCuantiaTarifa(Double cuantiaTarifa) {
		this.cuantiaTarifa = cuantiaTarifa;
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

	public Integer getTelefono() {
		return telefono;
	}

	public void setTelefono(Integer telefono) {
		this.telefono = telefono;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Categoria getCategoria() {
		return categoria;
	}

	public void setCategoria(Categoria categoria) {
		this.categoria = categoria;
	}

	public Grado getGrado() {
		return grado;
	}

	public void setGrado(Grado grado) {
		this.grado = grado;
	}

	public Date getFechaAlta() {
		return fechaAlta;
	}

	public void setFechaAlta(Date fechaAlta) {
		this.fechaAlta = fechaAlta;
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

	public void setFechaPeso(Date fechaPeso) {
		this.fechaPeso = fechaPeso;
	}

	public Imagen getFotoAlumno() {
		return fotoAlumno;
	}

	public void setFotoAlumno(Imagen fotoAlumno) {
		this.fotoAlumno = fotoAlumno;
	}

	public List<Grupo> getGrupos() {
		return grupos;
	}

	public void setGrupos(List<Grupo> grupos) {
		this.grupos = grupos;
	}

	public List<Turno> getTurnos() {
		return turnos;
	}

	public void setTurnos(List<Turno> turnos) {
		this.turnos = turnos;
	}

	public void addTurno(Turno turno) {
	    if (!this.turnos.contains(turno)) {
	        this.turnos.add(turno);
	        turno.getAlumnos().add(this);
	    }
	}


    public void removeTurno(Turno turno) {
        this.turnos.remove(turno);
        turno.getAlumnos().remove(this);
    }

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}
}