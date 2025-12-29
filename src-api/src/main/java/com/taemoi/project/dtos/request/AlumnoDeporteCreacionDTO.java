package com.taemoi.project.dtos.request;

import java.util.Date;

import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.TipoTarifa;

/**
 * DTO para crear deportes al crear un alumno
 * Permite asignar múltiples deportes durante la creación del alumno con sus datos específicos
 */
public class AlumnoDeporteCreacionDTO {

    /**
     * Deporte a asignar al alumno
     */
    private Deporte deporte;

    /**
     * Grado inicial en este deporte (como String, ej: "BLANCO", "AMARILLO")
     */
    private String grado;

    /**
     * Fecha en que se obtuvo el grado
     */
    private Date fechaGrado;

    /**
     * Fecha de alta en este deporte específico
     */
    private Date fechaAlta;

    /**
     * Fecha de alta inicial para calcular antigüedad en este deporte
     * Si es null, se usa fechaAlta
     */
    private Date fechaAltaInicial;

    /**
     * Tipo de tarifa para este deporte
     */
    private TipoTarifa tipoTarifa;

    /**
     * Cuantía de la tarifa (puede ser personalizada)
     */
    private Double cuantiaTarifa;

    /**
     * Rol familiar para tarifas PADRES_HIJOS (PADRE, MADRE, HIJO, HIJA)
     */
    private String rolFamiliar;

    /**
     * Grupo familiar para tarifa HERMANOS
     */
    private String grupoFamiliar;

    /**
     * Si es competidor en este deporte (solo para Taekwondo y Kickboxing)
     */
    private Boolean competidor;

    /**
     * Peso del competidor (si aplica)
     */
    private Double peso;

    /**
     * Fecha de medición del peso
     */
    private Date fechaPeso;

    /**
     * Si tiene licencia federativa en este deporte (solo para Taekwondo y Kickboxing)
     */
    private Boolean tieneLicencia;

    /**
     * Número de licencia federativa
     */
    private Integer numeroLicencia;

    /**
     * Fecha de expedición de la licencia
     */
    private Date fechaLicencia;

    // Constructors

    public AlumnoDeporteCreacionDTO() {
    }

    public AlumnoDeporteCreacionDTO(Deporte deporte, String grado, Date fechaGrado, Date fechaAlta) {
        this.deporte = deporte;
        this.grado = grado;
        this.fechaGrado = fechaGrado;
        this.fechaAlta = fechaAlta;
    }

    // Getters and Setters

    public Deporte getDeporte() {
        return deporte;
    }

    public void setDeporte(Deporte deporte) {
        this.deporte = deporte;
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

    public String getRolFamiliar() {
        return rolFamiliar;
    }

    public void setRolFamiliar(String rolFamiliar) {
        this.rolFamiliar = rolFamiliar;
    }

    public String getGrupoFamiliar() {
        return grupoFamiliar;
    }

    public void setGrupoFamiliar(String grupoFamiliar) {
        this.grupoFamiliar = grupoFamiliar;
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
}
