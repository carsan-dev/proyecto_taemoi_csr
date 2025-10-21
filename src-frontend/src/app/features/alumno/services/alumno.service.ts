import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';

/**
 * Service for managing student (Alumno) operations
 * Handles CRUD operations, groups, schedules, and exam eligibility
 */
@Injectable({
  providedIn: 'root',
})
export class AlumnoService {
  private readonly urlBase = `${environment.apiUrl}/alumnos`;

  // State management
  private readonly gruposDelAlumnoSubject = new BehaviorSubject<any[]>([]);
  public gruposDelAlumno$ = this.gruposDelAlumnoSubject.asObservable();

  private readonly turnosDelAlumnoSubject = new BehaviorSubject<any[]>([]);
  public turnosDelAlumno$ = this.turnosDelAlumnoSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  /**
   * Get paginated list of students
   */
  obtenerAlumnos(
    page: number,
    size: number,
    nombre: string = '',
    incluirInactivos: boolean = false
  ): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('incluirInactivos', incluirInactivos.toString());

    if (nombre) {
      params = params.set('nombre', nombre);
    }

    return this.http.get<any>(this.urlBase, {
      params,
      withCredentials: true,
    });
  }

  /**
   * Get all students without pagination
   */
  obtenerAlumnosSinPaginar(incluirInactivos: boolean = false): Observable<any[]> {
    const params = new HttpParams().set(
      'incluirInactivos',
      incluirInactivos.toString()
    );

    return this.http.get<any[]>(this.urlBase, {
      params,
      withCredentials: true,
    });
  }

  /**
   * Get all students without pagination (alias method)
   */
  obtenerTodosLosAlumnosSinPaginar(mostrarInactivos: boolean = false): Observable<any[]> {
    return this.obtenerAlumnosSinPaginar(mostrarInactivos);
  }

  /**
   * Get student by ID
   */
  obtenerAlumnoPorId(id: number): Observable<any> {
    return this.http.get<any>(`${this.urlBase}/${id}`, {
      withCredentials: true,
    });
  }

  /**
   * Get total count of students
   */
  countAlumnos(): Observable<any[]> {
    return this.http.get<any[]>(`${this.urlBase}/count`, {
      withCredentials: true,
    });
  }

  /**
   * Get groups for a specific student
   */
  obtenerGruposDelAlumno(alumnoId: number): void {
    this.http
      .get<any>(`${this.urlBase}/${alumnoId}/grupos`, {
        withCredentials: true,
      })
      .subscribe({
        next: (grupos) => {
          this.gruposDelAlumnoSubject.next(grupos);
        },
        error: (error) => {
          console.error('Error al obtener los grupos del alumno:', error);
        },
      });
  }

  /**
   * Get schedules (turnos) for a specific student
   */
  obtenerTurnosDelAlumno(alumnoId: number): void {
    this.http
      .get<any[]>(`${this.urlBase}/${alumnoId}/turnos`, {
        withCredentials: true,
      })
      .subscribe({
        next: (turnos) => {
          this.turnosDelAlumnoSubject.next(turnos);
        },
        error: (error) => {
          console.error('Error al obtener los turnos del alumno:', error);
        },
      });
  }

  /**
   * Assign student to a schedule (turno)
   */
  asignarAlumnoATurno(alumnoId: number, turnoId: number): Observable<any> {
    return this.http.post<any>(
      `${this.urlBase}/${alumnoId}/turnos/${turnoId}`,
      {},
      { withCredentials: true }
    );
  }

  /**
   * Remove student from a schedule (turno)
   */
  removerAlumnoDeTurno(alumnoId: number, turnoId: number): Observable<any> {
    return this.http.delete<any>(
      `${this.urlBase}/${alumnoId}/turnos/${turnoId}`,
      { withCredentials: true }
    );
  }

  /**
   * Create a new student
   */
  crearAlumno(alumnoData: any, imagen: File | null): Observable<any> {
    const formData = new FormData();
    formData.append('nuevo', JSON.stringify(alumnoData));
    if (imagen) {
      formData.append('file', imagen, imagen.name);
    }
    return this.http.post<any>(`${this.urlBase}/crear`, formData, {
      withCredentials: true,
    });
  }

  /**
   * Update an existing student
   */
  actualizarAlumno(id: number, formData: FormData): Observable<any> {
    return this.http.put<any>(`${this.urlBase}/${id}`, formData, {
      withCredentials: true,
    });
  }

  /**
   * Delete student image
   */
  eliminarImagenAlumno(id: number): Observable<any> {
    return this.http.delete(`${this.urlBase}/${id}/imagen`, {
      withCredentials: true,
    });
  }

  /**
   * Delete a student
   */
  eliminarAlumnos(id: number): Observable<any> {
    return this.http.delete<any>(`${this.urlBase}/${id}`, {
      withCredentials: true,
    });
  }

  /**
   * Deactivate a student (dar de baja)
   */
  darDeBajaAlumno(alumnoId: number): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/baja`,
      {},
      { withCredentials: true }
    );
  }

  /**
   * Activate a student (dar de alta)
   */
  darDeAltaAlumno(alumnoId: number): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/alta`,
      {},
      { withCredentials: true }
    );
  }

  /**
   * Get all students eligible for exam
   */
  obtenerAlumnosAptosParaExamen(): Observable<any[]> {
    return this.http.get<any[]>(`${this.urlBase}/aptos`, {
      withCredentials: true,
    });
  }

  /**
   * Get students eligible for exam by sport (deporte)
   */
  obtenerAlumnosAptosPorDeporte(deporte: string): Observable<any[]> {
    const params = new HttpParams().set('deporte', deporte);
    return this.http.get<any[]>(`${this.urlBase}/aptos/deporte`, {
      params,
      withCredentials: true,
    });
  }

  /**
   * Get a specific eligible student by ID
   */
  obtenerAlumnoAptoPorId(id: number): Observable<any> {
    return this.http.get<any>(`${this.urlBase}/aptos/${id}`, {
      withCredentials: true,
    });
  }
}
