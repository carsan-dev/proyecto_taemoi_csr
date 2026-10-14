package com.taemoi.project.entities;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "plantilla_preinscripcion")
public class PlantillaPreinscripcion extends EntidadOrganizacion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 /** Disciplina (V26, NOT NULL). Desde V28 la única fuente del deporte (la columna {@code deporte} se borró). */
 @com.fasterxml.jackson.annotation.JsonIgnore @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="disciplina_id",nullable=false) private Disciplina disciplina;
 public Disciplina getDisciplina(){return disciplina;} public void setDisciplina(Disciplina v){disciplina=v;}
 @Column(nullable=false) private Integer version;
 @Column(nullable=false) private Boolean activa = true;
 @Lob @Column(nullable=false, columnDefinition="LONGTEXT") private String contenido;
 @Lob @Column(nullable=false, columnDefinition="LONGTEXT") private String instrucciones;
 @Column(name="creada_en", nullable=false) private Instant creadaEn;
 @PrePersist void prePersist(){ if(creadaEn==null) creadaEn=Instant.now(); }
 public Long getId(){return id;}
 /** Código de la disciplina: el valor de la antigua columna {@code deporte}. */
 @com.fasterxml.jackson.annotation.JsonProperty("deporte") public String getDeporte(){return disciplina==null?null:disciplina.getCodigo();}
 public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public Boolean getActiva(){return activa;} public void setActiva(Boolean v){activa=v;}
 public String getContenido(){return contenido;} public void setContenido(String v){contenido=v;} public String getInstrucciones(){return instrucciones;} public void setInstrucciones(String v){instrucciones=v;}
 public Instant getCreadaEn(){return creadaEn;}
}
