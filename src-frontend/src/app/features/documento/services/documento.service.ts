import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Documento } from '../../../interfaces/documento';

/**
 * Service for managing document (Documento) operations
 * Handles file uploads and downloads for student documents
 */
@Injectable({
  providedIn: 'root',
})
export class DocumentoService {
  private readonly urlBase = environment.apiUrl;

  constructor(private readonly http: HttpClient) {}

  /**
   * Get all documents for a specific student
   */
  obtenerDocumentosDeAlumno(alumnoId: number): Observable<Documento[]> {
    return this.http.get<Documento[]>(
      `${this.urlBase}/alumnos/${alumnoId}/documentos`,
      { withCredentials: true }
    );
  }

  /**
   * Upload a document for a student
   */
  subirDocumentoAlumno(alumnoId: number, archivo: File): Observable<Documento> {
    const formData = new FormData();
    formData.append('archivo', archivo);
    return this.http.post<Documento>(
      `${this.urlBase}/alumnos/${alumnoId}/documentos`,
      formData,
      { withCredentials: true }
    );
  }

  /**
   * Delete a document from a student
   */
  eliminarDocumentoAlumno(
    alumnoId: number,
    documentoId: number
  ): Observable<void> {
    return this.http.delete<void>(
      `${this.urlBase}/alumnos/${alumnoId}/documentos/${documentoId}`,
      { withCredentials: true }
    );
  }
}
