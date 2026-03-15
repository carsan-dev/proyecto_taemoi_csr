import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ConvocatoriaDTO } from '../../../interfaces/convocatoria-dto';

/**
 * Service for managing exam call (Convocatoria) operations
 * Handles exam calls, student assignments, and grade updates
 */
@Injectable({
  providedIn: 'root',
})
export class ConvocatoriaService {
  private readonly urlBase = `${environment.apiUrl}/convocatorias`;
  private readonly alumnoBase = `${environment.apiUrl}/alumnos`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Get all exam calls, optionally filtered by sport
   */
  obtenerConvocatorias(deporte?: string): Observable<any[]> {
    let params = new HttpParams();
    if (deporte) {
      params = params.set('deporte', deporte);
    }
    return this.http.get<any[]>(this.urlBase, {
      params,
      withCredentials: true,
    });
  }

  /**
   * Get exam call by ID
   */
  obtenerConvocatoriaPorId(id: number): Observable<any> {
    return this.http.get<any>(`${this.urlBase}/${id}`, {
      withCredentials: true,
    });
  }

  /**
   * Create a new exam call
   */
  crearConvocatoria(convocatoria: any): Observable<any> {
    return this.http.post<any>(this.urlBase, convocatoria, {
      withCredentials: true,
    });
  }

  /**
   * Delete an exam call
   */
  eliminarConvocatoria(id: number): Observable<void> {
    return this.http.delete<void>(`${this.urlBase}/${id}`, {
      withCredentials: true,
    });
  }

  /**
   * Get all students in an exam call
   */
  obtenerAlumnosDeConvocatoria(convocatoriaId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.urlBase}/${convocatoriaId}/alumnos`, {
      withCredentials: true,
    });
  }

  /**
   * Add a student to an exam call
   */
  agregarAlumnoAConvocatoria(
    alumnoId: number,
    convocatoriaId: number,
    porRecompensa: boolean,
    rojoBordado?: boolean
  ): Observable<any> {
    const payload: any = { porRecompensa };
    if (rojoBordado !== undefined) {
      payload.rojoBordado = rojoBordado;
    }
    return this.http.post<any>(
      `${this.alumnoBase}/${convocatoriaId}/alumno/${alumnoId}`,
      payload,
      { withCredentials: true }
    );
  }

  /**
   * Remove a student from an exam call
   */
  eliminarAlumnoDeConvocatoria(
    alumnoId: number,
    convocatoriaId: number
  ): Observable<any> {
    return this.http.delete<any>(
      `${this.alumnoBase}/${convocatoriaId}/alumno/${alumnoId}`,
      { withCredentials: true }
    );
  }

  /**
   * Get all exam calls for a specific student
   */
  obtenerConvocatoriasDeAlumno(alumnoId: number): Observable<ConvocatoriaDTO[]> {
    return this.http.get<ConvocatoriaDTO[]>(
      `${this.urlBase}/alumnos/${alumnoId}`,
      { withCredentials: true }
    );
  }

  /**
   * Update grades for all students in an exam call
   */
  actualizarGradosDeConvocatoria(convocatoriaId: number): Observable<void> {
    return this.http.put<void>(
      `${this.urlBase}/${convocatoriaId}/actualizar-grados`,
      {},
      { withCredentials: true }
    );
  }

  /**
   * Update a specific student's exam call details
   */
  actualizarAlumnoConvocatoria(
    alumnoConvocatoriaId: number,
    datos: any
  ): Observable<void> {
    return this.http.put<void>(
      `${this.urlBase}/alumno/${alumnoConvocatoriaId}`,
      datos,
      { withCredentials: true }
    );
  }
}
