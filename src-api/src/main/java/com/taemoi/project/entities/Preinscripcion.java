package com.taemoi.project.entities;

import java.time.*;
import java.util.LinkedHashSet;
import java.util.Set;
import jakarta.persistence.*;

@Entity
@Table(name="preinscripcion")
public class Preinscripcion extends EntidadOrganizacion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=32) private String referencia;
 @Column(nullable=false,length=9) private String temporada;
 /** Disciplina (V26, NOT NULL). Desde V28 la única fuente del deporte (la columna {@code deporte} se borró). */
 @com.fasterxml.jackson.annotation.JsonIgnore @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="disciplina_id",nullable=false) private Disciplina disciplina;
 public Disciplina getDisciplina(){return disciplina;} public void setDisciplina(Disciplina v){disciplina=v;}
 /** Código de la disciplina: el valor de la antigua columna {@code deporte}. */
 @com.fasterxml.jackson.annotation.JsonProperty("deporte") public String getDeporte(){return disciplina==null?null:disciplina.getCodigo();}
 @Enumerated(EnumType.STRING) @Column(nullable=false) private EstadoPreinscripcion estado=EstadoPreinscripcion.PENDIENTE;
 @Column(nullable=false,length=100) private String nombre; @Column(nullable=false,length=160) private String apellidos;
 @Column(length=16) private String dni; @Column(name="identidad_hash",nullable=false,length=64) private String identidadHash; @Column(name="fecha_nacimiento",nullable=false) private LocalDate fechaNacimiento;
 @Column(nullable=false) private String direccion; @Column(nullable=false,length=20) private String telefono; @Column(length=20) private String telefono2; @Column(nullable=false,length=180) private String email;
 @Column(name="tiene_discapacidad") private Boolean tieneDiscapacidad;
 @Column(length=1000) private String observaciones;
 @Column(name="tutor_nombre",length=180) private String tutorNombre; @Column(name="tutor_dni",length=16) private String tutorDni;
 @Column(name="consentimiento_fotografico",nullable=false) private Boolean consentimientoFotografico=false;
 @Column(name="aceptacion_normas",nullable=false) private Boolean aceptacionNormas;
 @Column(name="firmante_nombre",nullable=false,length=180) private String firmanteNombre;
 /**
  * R2-P7c: {@code firma}, {@code pdfFirmado} y {@code plantillaSnapshot} (NOT NULL sin valor por defecto) se escriben en el
  * INSERT del alta, pero esta entidad NO los lee: {@code read="null"} los saca de todo SELECT (sin bytecode enhancement,
  * {@code @Basic(fetch=LAZY)} no tendría efecto) y {@code updatable=false} los saca de todo UPDATE (antes cada guardado
  * de la solicitud los reescribía). En una solicitud cargada valen null: se leen con {@link PreinscripcionDocumentos}.
  */
 @Lob @Column(nullable=false,updatable=false,columnDefinition="MEDIUMBLOB") @org.hibernate.annotations.ColumnTransformer(read="null") private byte[] firma;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="turno_id") private Turno turno;
 @Lob @Column(name="turno_snapshot",columnDefinition="LONGTEXT") private String turnoSnapshot;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="grupo_id") private Grupo grupo;
 @Lob @Column(name="grupo_snapshot",columnDefinition="LONGTEXT") private String grupoSnapshot;
 @ManyToMany(fetch=FetchType.LAZY)
 @JoinTable(name="preinscripcion_turno",joinColumns=@JoinColumn(name="preinscripcion_id"),inverseJoinColumns=@JoinColumn(name="turno_id"))
 private Set<Turno> turnos=new LinkedHashSet<>();
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="plantilla_id") private PlantillaPreinscripcion plantilla;
 /** R2-P7c: solo se escribe en el alta; se lee con {@link PreinscripcionDocumentos} (ver {@link #firma}). */
 @Lob @Column(name="plantilla_snapshot",nullable=false,updatable=false,columnDefinition="LONGTEXT") @org.hibernate.annotations.ColumnTransformer(read="null") private String plantillaSnapshot;
 /** R2-P7c: solo se escribe en el alta; se lee con {@link PreinscripcionDocumentos} (ver {@link #firma}). */
 @Lob @Column(name="pdf_firmado",nullable=false,updatable=false,columnDefinition="MEDIUMBLOB") @org.hibernate.annotations.ColumnTransformer(read="null") private byte[] pdfFirmado;
 @Column(name="token_documento_hash",nullable=false,length=64) private String tokenDocumentoHash;
 @Column(name="idempotency_key_hash",length=64,unique=true) private String idempotencyKeyHash;
 @Column(name="email_enviado",nullable=false) private Boolean emailEnviado=false; @Column(name="email_intentos",nullable=false) private Integer emailIntentos=0;
 @Column(name="email_ultimo_error",length=500) private String emailUltimoError;
 @Enumerated(EnumType.STRING) @Column(name="email_finalizacion_estado",nullable=false,length=20) private EstadoEmailFinalizacion estadoEmailFinalizacion=EstadoEmailFinalizacion.NO_ENVIADO;
 @Column(name="email_finalizacion_enviado_en") private Instant emailFinalizacionEnviadoEn;
 @Column(name="email_finalizacion_intentos",nullable=false) private Integer emailFinalizacionIntentos=0;
 @Column(name="email_finalizacion_ultimo_error",length=500) private String emailFinalizacionUltimoError;
 @Enumerated(EnumType.STRING) @Column(name="email_cambio_turnos_estado",nullable=false,length=20) private EstadoEmailCambioTurnos estadoEmailCambioTurnos=EstadoEmailCambioTurnos.NO_ENVIADO;
 @Column(name="email_cambio_turnos_intentos",nullable=false) private Integer emailCambioTurnosIntentos=0;
 @Column(name="email_cambio_turnos_enviado_en") private Instant emailCambioTurnosEnviadoEn;
 @Column(name="email_cambio_turnos_ultimo_error",length=500) private String emailCambioTurnosUltimoError;
 @Column(name="turnos_modificados_en") private Instant turnosModificadosEn;
 @Lob @Column(name="email_cambio_turnos_anterior_snapshot",columnDefinition="LONGTEXT") private String emailCambioTurnosAnteriorSnapshot;
 @Lob @Column(name="email_cambio_turnos_nuevo_snapshot",columnDefinition="LONGTEXT") private String emailCambioTurnosNuevoSnapshot;
 @Column(name="creada_en",nullable=false) private Instant creadaEn; @Column(name="actualizada_en",nullable=false) private Instant actualizadaEn;
 @Column(name="finalizada_en") private Instant finalizadaEn; @Column(name="cancelada_en") private Instant canceladaEn;
 @Column(name="promocionada_en") private Instant promocionadaEn;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="alumno_id") private Alumno alumno;
 /** V31_1: columnas NULL; las filas anteriores leen NO_ENVIADO y 0 intentos en los getters. */
 @Column(name="motivo_cancelacion",length=500) private String motivoCancelacion;
 @Enumerated(EnumType.STRING) @Column(name="email_cancelacion_estado",length=20) private EstadoEmailCancelacion estadoEmailCancelacion=EstadoEmailCancelacion.NO_ENVIADO;
 @Column(name="email_cancelacion_intentos") private Integer emailCancelacionIntentos=0;
 @Column(name="email_cancelacion_enviado_en") private Instant emailCancelacionEnviadoEn;
 @Column(name="email_cancelacion_ultimo_error",length=500) private String emailCancelacionUltimoError;
 @PrePersist void create(){creadaEn=actualizadaEn=Instant.now();} @PreUpdate void update(){actualizadaEn=Instant.now();}
 public Long getId(){return id;} public String getReferencia(){return referencia;} public void setReferencia(String v){referencia=v;} public String getTemporada(){return temporada;} public void setTemporada(String v){temporada=v;}
 public EstadoPreinscripcion getEstado(){return estado;} public void setEstado(EstadoPreinscripcion v){estado=v;}
 public String getNombre(){return nombre;} public void setNombre(String v){nombre=v;} public String getApellidos(){return apellidos;} public void setApellidos(String v){apellidos=v;} public String getDni(){return dni;} public void setDni(String v){dni=v;} public String getIdentidadHash(){return identidadHash;} public void setIdentidadHash(String v){identidadHash=v;}
 public LocalDate getFechaNacimiento(){return fechaNacimiento;} public void setFechaNacimiento(LocalDate v){fechaNacimiento=v;} public String getDireccion(){return direccion;} public void setDireccion(String v){direccion=v;} public String getTelefono(){return telefono;} public void setTelefono(String v){telefono=v;} public String getTelefono2(){return telefono2;} public void setTelefono2(String v){telefono2=v;} public String getEmail(){return email;} public void setEmail(String v){email=v;} public Boolean getTieneDiscapacidad(){return tieneDiscapacidad;} public void setTieneDiscapacidad(Boolean v){tieneDiscapacidad=v;}
 public String getObservaciones(){return observaciones;} public void setObservaciones(String v){observaciones=v;}
 public String getTutorNombre(){return tutorNombre;} public void setTutorNombre(String v){tutorNombre=v;} public String getTutorDni(){return tutorDni;} public void setTutorDni(String v){tutorDni=v;}
 public Boolean getConsentimientoFotografico(){return consentimientoFotografico;} public void setConsentimientoFotografico(Boolean v){consentimientoFotografico=v;} public Boolean getAceptacionNormas(){return aceptacionNormas;} public void setAceptacionNormas(Boolean v){aceptacionNormas=v;} public String getFirmanteNombre(){return firmanteNombre;} public void setFirmanteNombre(String v){firmanteNombre=v;} public byte[] getFirma(){return firma;} public void setFirma(byte[] v){firma=v;}
 public Turno getTurno(){return turno;} public void setTurno(Turno v){turno=v;} public String getTurnoSnapshot(){return turnoSnapshot;} public void setTurnoSnapshot(String v){turnoSnapshot=v;} public Grupo getGrupo(){return grupo;} public void setGrupo(Grupo v){grupo=v;} public String getGrupoSnapshot(){return grupoSnapshot;} public void setGrupoSnapshot(String v){grupoSnapshot=v;} public PlantillaPreinscripcion getPlantilla(){return plantilla;} public void setPlantilla(PlantillaPreinscripcion v){plantilla=v;} public String getPlantillaSnapshot(){return plantillaSnapshot;} public void setPlantillaSnapshot(String v){plantillaSnapshot=v;}
 public Set<Turno> getTurnos(){return turnos;} public void setTurnos(Set<Turno> v){turnos=v==null?new LinkedHashSet<>():new LinkedHashSet<>(v);} public void addTurno(Turno v){if(v!=null)turnos.add(v);}
 public byte[] getPdfFirmado(){return pdfFirmado;} public void setPdfFirmado(byte[] v){pdfFirmado=v;} public String getTokenDocumentoHash(){return tokenDocumentoHash;} public void setTokenDocumentoHash(String v){tokenDocumentoHash=v;}
 public String getIdempotencyKeyHash(){return idempotencyKeyHash;} public void setIdempotencyKeyHash(String v){idempotencyKeyHash=v;}
 public Boolean getEmailEnviado(){return emailEnviado;} public void setEmailEnviado(Boolean v){emailEnviado=v;} public Integer getEmailIntentos(){return emailIntentos;} public void setEmailIntentos(Integer v){emailIntentos=v;} public String getEmailUltimoError(){return emailUltimoError;} public void setEmailUltimoError(String v){emailUltimoError=v;}
 public EstadoEmailFinalizacion getEstadoEmailFinalizacion(){return estadoEmailFinalizacion;} public void setEstadoEmailFinalizacion(EstadoEmailFinalizacion v){estadoEmailFinalizacion=v;}
 public Instant getEmailFinalizacionEnviadoEn(){return emailFinalizacionEnviadoEn;} public void setEmailFinalizacionEnviadoEn(Instant v){emailFinalizacionEnviadoEn=v;}
 public Integer getEmailFinalizacionIntentos(){return emailFinalizacionIntentos==null?0:emailFinalizacionIntentos;} public void setEmailFinalizacionIntentos(Integer v){emailFinalizacionIntentos=v;}
 public String getEmailFinalizacionUltimoError(){return emailFinalizacionUltimoError;} public void setEmailFinalizacionUltimoError(String v){emailFinalizacionUltimoError=v;}
 public EstadoEmailCambioTurnos getEstadoEmailCambioTurnos(){return estadoEmailCambioTurnos;} public void setEstadoEmailCambioTurnos(EstadoEmailCambioTurnos v){estadoEmailCambioTurnos=v;}
 public Integer getEmailCambioTurnosIntentos(){return emailCambioTurnosIntentos==null?0:emailCambioTurnosIntentos;} public void setEmailCambioTurnosIntentos(Integer v){emailCambioTurnosIntentos=v;}
 public Instant getEmailCambioTurnosEnviadoEn(){return emailCambioTurnosEnviadoEn;} public void setEmailCambioTurnosEnviadoEn(Instant v){emailCambioTurnosEnviadoEn=v;}
 public String getEmailCambioTurnosUltimoError(){return emailCambioTurnosUltimoError;} public void setEmailCambioTurnosUltimoError(String v){emailCambioTurnosUltimoError=v;}
 public Instant getTurnosModificadosEn(){return turnosModificadosEn;} public void setTurnosModificadosEn(Instant v){turnosModificadosEn=v;}
 public String getEmailCambioTurnosAnteriorSnapshot(){return emailCambioTurnosAnteriorSnapshot;} public void setEmailCambioTurnosAnteriorSnapshot(String v){emailCambioTurnosAnteriorSnapshot=v;}
 public String getEmailCambioTurnosNuevoSnapshot(){return emailCambioTurnosNuevoSnapshot;} public void setEmailCambioTurnosNuevoSnapshot(String v){emailCambioTurnosNuevoSnapshot=v;}
 public Instant getCreadaEn(){return creadaEn;} public Instant getFinalizadaEn(){return finalizadaEn;} public void setFinalizadaEn(Instant v){finalizadaEn=v;} public Instant getCanceladaEn(){return canceladaEn;} public void setCanceladaEn(Instant v){canceladaEn=v;} public Alumno getAlumno(){return alumno;} public void setAlumno(Alumno v){alumno=v;}
 public Instant getPromocionadaEn(){return promocionadaEn;} public void setPromocionadaEn(Instant v){promocionadaEn=v;}
 public String getMotivoCancelacion(){return motivoCancelacion;} public void setMotivoCancelacion(String v){motivoCancelacion=v;}
 public EstadoEmailCancelacion getEstadoEmailCancelacion(){return estadoEmailCancelacion==null?EstadoEmailCancelacion.NO_ENVIADO:estadoEmailCancelacion;} public void setEstadoEmailCancelacion(EstadoEmailCancelacion v){estadoEmailCancelacion=v;}
 public Integer getEmailCancelacionIntentos(){return emailCancelacionIntentos==null?0:emailCancelacionIntentos;} public void setEmailCancelacionIntentos(Integer v){emailCancelacionIntentos=v;}
 public Instant getEmailCancelacionEnviadoEn(){return emailCancelacionEnviadoEn;} public void setEmailCancelacionEnviadoEn(Instant v){emailCancelacionEnviadoEn=v;}
 public String getEmailCancelacionUltimoError(){return emailCancelacionUltimoError;} public void setEmailCancelacionUltimoError(String v){emailCancelacionUltimoError=v;}
}
