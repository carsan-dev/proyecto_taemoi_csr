package com.taemoi.project.repositories;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import com.taemoi.project.entities.PreinscripcionDocumentos;
/**
 * R2-P7c: lectura de los binarios de una preinscripción, cada uno por separado (la firma no trae el PDF ni al revés).
 * Consultas escalares: no dejan entidades en el contexto de persistencia. JPQL, así que Hibernate las restringe a la
 * escuela activa. Sin {@code save} ni {@code delete}: los escribe el alta y los borra el borrado de la preinscripción.
 *
 * <p>Devuelven null si no existe (el servicio responde «no encontrada»). NO {@code Optional<byte[]>}: Spring Data
 * convierte entonces el array y un PDF de 144 KB tardaba ~38 ms en lugar de ~3 ms (medido en R2-P7c).
 */
public interface PreinscripcionDocumentosRepository extends Repository<PreinscripcionDocumentos,Long>{
 @Query("select d.firma from PreinscripcionDocumentos d where d.referencia = :referencia")
 byte[] findFirmaByReferencia(@Param("referencia") String referencia);
 @Query("select d.pdfFirmado from PreinscripcionDocumentos d where d.referencia = :referencia")
 byte[] findPdfFirmadoByReferencia(@Param("referencia") String referencia);
 @Query("select d.pdfFirmado from PreinscripcionDocumentos d where d.id = :id")
 byte[] findPdfFirmadoById(@Param("id") Long id);
 @Query("select d.plantillaSnapshot from PreinscripcionDocumentos d where d.id = :id")
 String findPlantillaSnapshotById(@Param("id") Long id);
}
