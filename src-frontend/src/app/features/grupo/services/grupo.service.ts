import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { GrupoDTO } from '../../../interfaces/grupo-dto';

/**
 * Service for managing group (Grupo) operations
 * Handles CRUD operations, student assignments, and schedule management
 */
@Injectable({
  providedIn: 'root',
})
export class GrupoService {
  private readonly urlBase = `${environment.apiUrl}/grupos`;

  // State management
  private readonly conteoAlumnosPorGrupoSubject = new BehaviorSubject<any>({});
  public conteoAlumnosPorGrupo$ = this.conteoAlumnosPorGrupoSubject.asObservable();
  public conteoAlumnosPorGrupo: any = {};

  private readonly turnosDelGrupoSubject = new BehaviorSubject<any[]>([]);
  public turnosDelGrupo$ = this.turnosDelGrupoSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  /**
   * Get all groups
   */
  obtenerTodosLosGrupos(): Observable<GrupoDTO[]> {
    return this.http.get<GrupoDTO[]>(this.urlBase, {
      withCredentials: true,
    });
  }

  /**
   * Get group by ID
   */
  obtenerGrupoPorId(id: number): Observable<GrupoDTO> {
    return this.http.get<GrupoDTO>(`${this.urlBase}/${id}`, {
      withCredentials: true,
    });
  }

  /**
   * Get student count per group
   */
  obtenerConteoAlumnosPorGrupo(): void {
    this.http
      .get<any>(`${this.urlBase}/conteo-alumnos`, {
        withCredentials: true,
      })
      .subscribe({
        next: (conteo) => {
          this.conteoAlumnosPorGrupoSubject.next(conteo);
          this.conteoAlumnosPorGrupo = conteo;
        },
        error: (error) => {
          console.error(
            'Error al obtener el conteo de alumnos por grupo:',
            error
          );
        },
      });
  }

  /**
   * Get students by type (sport)
   */
  obtenerAlumnosPorTipo(tipo: string): Observable<any[]> {
    const encodedTipo = encodeURIComponent(tipo);
    return this.http.get<any[]>(`${this.urlBase}/tipo/${encodedTipo}/alumnos`, {
      withCredentials: true,
    });
  }

  /**
   * Create a new group
   */
  crearGrupo(grupoData: any): Observable<any> {
    return this.http.post<any>(this.urlBase, grupoData, {
      withCredentials: true,
    });
  }

  /**
   * Update an existing group
   */
  actualizarGrupo(id: number, grupoData: GrupoDTO): Observable<GrupoDTO> {
    return this.http.put<GrupoDTO>(`${this.urlBase}/${id}`, grupoData, {
      withCredentials: true,
    });
  }

  /**
   * Delete a group
   */
  eliminarGrupo(id: number): Observable<any> {
    return this.http.delete<any>(`${this.urlBase}/${id}`, {
      withCredentials: true,
    });
  }

  /**
   * Add a single student to a group
   */
  agregarAlumnoAGrupo(grupoId: number, alumnoId: number): Observable<any> {
    return this.http.post<any>(
      `${this.urlBase}/${grupoId}/alumnos/${alumnoId}`,
      {},
      { withCredentials: true }
    );
  }

  /**
   * Add multiple students to a group
   */
  agregarAlumnosAGrupo(grupoId: number, alumnosIds: number[]): Observable<any> {
    return this.http.post<any>(
      `${this.urlBase}/${grupoId}/alumnos`,
      alumnosIds,
      { withCredentials: true }
    );
  }

  /**
   * Remove a student from a group
   */
  eliminarAlumnoDeGrupo(grupoId: number, alumnoId: number): Observable<any> {
    return this.http.delete<any>(
      `${this.urlBase}/${grupoId}/alumnos/${alumnoId}`,
      { withCredentials: true }
    );
  }

  /**
   * Get schedules (turnos) for a specific group
   */
  obtenerTurnosDelGrupo(grupoId: number): void {
    this.http
      .get<any>(`${this.urlBase}/${grupoId}/turnos`, {
        withCredentials: true,
      })
      .subscribe({
        next: (turnos) => {
          console.log('Turnos recibidos:', turnos);
          this.turnosDelGrupoSubject.next(turnos);
        },
        error: (error) => {
          console.error('Error al obtener los turnos del grupo:', error);
        },
      });
  }

  /**
   * Get schedules for a student in a specific group
   */
  obtenerTurnosDelAlumnoEnGrupo(grupoId: number, alumnoId: number): void {
    this.http
      .get<any[]>(
        `${this.urlBase}/${grupoId}/alumnos/${alumnoId}/turnos`,
        { withCredentials: true }
      )
      .subscribe({
        next: (turnos) => {
          this.turnosDelGrupoSubject.next(turnos);
        },
        error: (error) => {
          console.error(
            'Error al obtener los turnos del alumno en el grupo:',
            error
          );
        },
      });
  }

  /**
   * Add a schedule (turno) to a group
   */
  agregarTurnoAGrupo(grupoId: number, turnoId: number): Observable<any> {
    return this.http.post<any>(
      `${this.urlBase}/${grupoId}/turnos/${turnoId}`,
      null,
      { withCredentials: true }
    );
  }

  /**
   * Remove a schedule (turno) from a group
   */
  eliminarTurnoDeGrupo(grupoId: number, turnoId: number): Observable<any> {
    return this.http.delete<any>(
      `${this.urlBase}/${grupoId}/turnos/${turnoId}`,
      { withCredentials: true }
    );
  }
}
