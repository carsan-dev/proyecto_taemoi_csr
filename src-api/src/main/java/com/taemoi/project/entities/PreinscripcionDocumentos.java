package com.taemoi.project.entities;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;

/**
 * R2-P7c: los binarios de una preinscripción (firma, PDF firmado y plantilla congelada), mapeados de SOLO LECTURA sobre
 * la MISMA tabla {@code preinscripcion} (sin migración). {@link Preinscripcion} los escribe en el INSERT del alta pero no
 * los lee nunca, así que el listado, la cola de espera y cualquier carga de la solicitud dejan de traerlos; el servicio
 * los pide con consultas escalares de esta entidad solo donde hacen falta (firma, documentos, reenvío, detalle).
 *
 * <p>No se guarda ni se borra por sí misma: {@code @Immutable}, columnas no insertables ni actualizables y un repositorio
 * sin {@code save}/{@code delete}. Borrar la {@link Preinscripcion} borra la fila entera, binarios incluidos. Hereda el
 * filtro por organización ({@code @TenantId}) como el resto de entidades de la escuela.
 */
@Entity
@Immutable
@Table(name="preinscripcion")
public class PreinscripcionDocumentos extends EntidadOrganizacion {
 @Id private Long id;
 @Column(insertable=false,updatable=false,length=32) private String referencia;
 @Lob @Column(insertable=false,updatable=false,columnDefinition="MEDIUMBLOB") private byte[] firma;
 @Lob @Column(name="pdf_firmado",insertable=false,updatable=false,columnDefinition="MEDIUMBLOB") private byte[] pdfFirmado;
 @Lob @Column(name="plantilla_snapshot",insertable=false,updatable=false,columnDefinition="LONGTEXT") private String plantillaSnapshot;
 protected PreinscripcionDocumentos(){}
 public Long getId(){return id;} public String getReferencia(){return referencia;} public byte[] getFirma(){return firma;} public byte[] getPdfFirmado(){return pdfFirmado;} public String getPlantillaSnapshot(){return plantillaSnapshot;}
}
