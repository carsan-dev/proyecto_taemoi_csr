package com.taemoi.project.entities;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "plantilla_preinscripcion")
public class PlantillaPreinscripcion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private Deporte deporte;
 @Column(nullable=false) private Integer version;
 @Column(nullable=false) private Boolean activa = true;
 @Lob @Column(nullable=false, columnDefinition="LONGTEXT") private String contenido;
 @Lob @Column(nullable=false, columnDefinition="LONGTEXT") private String instrucciones;
 @Column(name="creada_en", nullable=false) private Instant creadaEn;
 @PrePersist void prePersist(){ if(creadaEn==null) creadaEn=Instant.now(); }
 public Long getId(){return id;} public Deporte getDeporte(){return deporte;} public void setDeporte(Deporte v){deporte=v;}
 public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public Boolean getActiva(){return activa;} public void setActiva(Boolean v){activa=v;}
 public String getContenido(){return contenido;} public void setContenido(String v){contenido=v;} public String getInstrucciones(){return instrucciones;} public void setInstrucciones(String v){instrucciones=v;}
 public Instant getCreadaEn(){return creadaEn;}
}
