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
   * Get all students with their sports embedded (batch endpoint - optimized)
   * Returns AlumnoConDeportesDTO[] with all sports data included
   */
  obtenerAlumnosConDeportes(incluirInactivos: boolean = false): Observable<any[]> {
    const params = new HttpParams().set(
      'incluirInactivos',
      incluirInactivos.toString()
    );

    return this.http.get<any[]>(`${this.urlBase}/con-deportes`, {
      params,
      withCredentials: true,
    });
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

  // ==================== MULTI-SPORT METHODS ====================

  /**
   * Get all sports (deportes) for a specific student
   * Returns array of AlumnoDeporteDTO
   */
  obtenerDeportesDelAlumno(alumnoId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.urlBase}/${alumnoId}/deportes`, {
      withCredentials: true,
    });
  }

  /**
   * Get complete student data with all sports
   * Returns AlumnoConDeportesDTO
   */
  obtenerAlumnoCompleto(alumnoId: number): Observable<any> {
    return this.http.get<any>(`${this.urlBase}/${alumnoId}/completo`, {
      withCredentials: true,
    });
  }

  /**
   * Add a sport to a student
   * @param alumnoId Student ID
   * @param deporte Sport name (TAEKWONDO, KICKBOXING, etc.)
   * @param gradoInicial Initial grade for this sport
   * @param fechaAlta Registration date for this sport (YYYY-MM-DD)
   * @param fechaGrado Grade date for this sport (YYYY-MM-DD)
   */
  agregarDeporteAAlumno(
    alumnoId: number,
    deporte: string,
    gradoInicial: string | null,
    fechaAlta: string,
    fechaGrado: string | null,
    fechaAltaInicial: string | null
  ): Observable<any> {
    return this.http.post<any>(
      `${this.urlBase}/${alumnoId}/deportes`,
      {
        deporte,
        gradoInicial: gradoInicial ?? null,
        fechaAlta,
        fechaGrado: fechaGrado ?? null,
        fechaAltaInicial: fechaAltaInicial ?? null,
      },
      { withCredentials: true }
    );
  }

  /**
   * Deactivate a sport from a student (soft delete - keeps all data)
   * @param alumnoId Student ID
   * @param deporte Sport name to deactivate
   */
  desactivarDeporteDeAlumno(alumnoId: number, deporte: string): Observable<any> {
    return this.http.put(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/desactivar`,
      {},
      { withCredentials: true, responseType: 'text' }
    );
  }

  /**
   * Activate a sport for a student that was inactive (preserves all data)
   * @param alumnoId Student ID
   * @param deporte Sport name to activate
   */
  activarDeporteDeAlumno(alumnoId: number, deporte: string): Observable<any> {
    return this.http.put(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/activar`,
      {},
      { withCredentials: true, responseType: 'text' }
    );
  }

  /**
   * Set a sport as principal for a student
   * @param alumnoId Student ID
   * @param deporte Sport name to mark as principal
   */
  establecerDeportePrincipal(alumnoId: number, deporte: string): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/principal`,
      {},
      { withCredentials: true }
    );
  }

  /**
   * Remove a sport from a student completely (hard delete - physical deletion)
   * @param alumnoId Student ID
   * @param deporte Sport name to remove
   */
  removerDeporteDeAlumno(alumnoId: number, deporte: string): Observable<any> {
    return this.http.delete(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}`,
      { withCredentials: true, responseType: 'text' }
    );
  }

  /**
   * Update grade for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param nuevoGrado New grade (TipoGrado value)
   */
  actualizarGradoPorDeporte(
    alumnoId: number,
    deporte: string,
    nuevoGrado: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/grado`,
      { nuevoGrado },
      { withCredentials: true }
    );
  }

  /**
   * Update exam eligibility status for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param aptoParaExamen New exam eligibility status
   */
  actualizarAptoParaExamen(
    alumnoId: number,
    deporte: string,
    aptoParaExamen: boolean
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/apto-examen`,
      { aptoParaExamen },
      { withCredentials: true }
    );
  }

  /**
   * Update grade date for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param fechaGrado New grade date (YYYY-MM-DD format)
   */
  actualizarFechaGrado(
    alumnoId: number,
    deporte: string,
    fechaGrado: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/fecha-grado`,
      { fechaGrado },
      { withCredentials: true }
    );
  }

  /**
   * Update fechaAltaInicial for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param fechaAltaInicial New initial enrollment date (YYYY-MM-DD format)
   */
  actualizarFechaAltaInicialDeporte(
    alumnoId: number,
    deporte: string,
    fechaAltaInicial: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/fecha-alta-inicial`,
      { fechaAltaInicial },
      { withCredentials: true }
    );
  }

  /**
   * Update fechaAlta for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param fechaAlta Enrollment date (YYYY-MM-DD format)
   */
  actualizarFechaAltaDeporte(
    alumnoId: number,
    deporte: string,
    fechaAlta: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/fecha-alta`,
      { fechaAlta },
      { withCredentials: true }
    );
  }

  /**
   * Update fechaBaja for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param fechaBaja Leave date (YYYY-MM-DD format) or null to clear
   */
  actualizarFechaBajaDeporte(
    alumnoId: number,
    deporte: string,
    fechaBaja: string | null
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/fecha-baja`,
      { fechaBaja },
      { withCredentials: true }
    );
  }

  /**
   * Update tipo de tarifa for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param tipoTarifa New tarifa type
   */
  actualizarTipoTarifaDeporte(
    alumnoId: number,
    deporte: string,
    tipoTarifa: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/tipo-tarifa`,
      { tipoTarifa },
      { withCredentials: true }
    );
  }

  /**
   * Update cuantia de tarifa for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param cuantiaTarifa New tarifa amount
   */
  actualizarCuantiaTarifaDeporte(
    alumnoId: number,
    deporte: string,
    cuantiaTarifa: number
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/cuantia-tarifa`,
      { cuantiaTarifa },
      { withCredentials: true }
    );
  }

  /**
   * Update rol familiar for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param rolFamiliar New family role
   */
  actualizarRolFamiliarDeporte(
    alumnoId: number,
    deporte: string,
    rolFamiliar: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/rol-familiar`,
      { rolFamiliar },
      { withCredentials: true }
    );
  }

  /**
   * Update grupo familiar for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param grupoFamiliar New family group
   */
  actualizarGrupoFamiliarDeporte(
    alumnoId: number,
    deporte: string,
    grupoFamiliar: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/grupo-familiar`,
      { grupoFamiliar },
      { withCredentials: true }
    );
  }

  /**
   * Update tiene licencia for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param tieneLicencia Has federation license
   */
  actualizarTieneLicenciaDeporte(
    alumnoId: number,
    deporte: string,
    tieneLicencia: boolean
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/tiene-licencia`,
      { tieneLicencia },
      { withCredentials: true }
    );
  }

  /**
   * Update numero de licencia for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param numeroLicencia License number
   */
  actualizarNumeroLicenciaDeporte(
    alumnoId: number,
    deporte: string,
    numeroLicencia: number
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/numero-licencia`,
      { numeroLicencia },
      { withCredentials: true }
    );
  }

  /**
   * Update fecha de licencia for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param fechaLicencia License date (YYYY-MM-DD format)
   */
  actualizarFechaLicenciaDeporte(
    alumnoId: number,
    deporte: string,
    fechaLicencia: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/fecha-licencia`,
      { fechaLicencia },
      { withCredentials: true }
    );
  }

  /**
   * Update competidor status for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param competidor Is competitor
   */
  actualizarCompetidorDeporte(
    alumnoId: number,
    deporte: string,
    competidor: boolean
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/competidor`,
      { competidor },
      { withCredentials: true }
    );
  }

  /**
   * Update peso for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param peso Weight in kg
   */
  actualizarPesoDeporte(
    alumnoId: number,
    deporte: string,
    peso: number
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/peso`,
      { peso },
      { withCredentials: true }
    );
  }

  /**
   * Update fecha de peso for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param fechaPeso Weight measurement date (YYYY-MM-DD format)
   */
  actualizarFechaPesoDeporte(
    alumnoId: number,
    deporte: string,
    fechaPeso: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/fecha-peso`,
      { fechaPeso },
      { withCredentials: true }
    );
  }

  /**
   * Update fecha alta competicion for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param fechaAltaCompeticion Competition registration date (YYYY-MM-DD format)
   */
  actualizarFechaAltaCompeticionDeporte(
    alumnoId: number,
    deporte: string,
    fechaAltaCompeticion: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/fecha-alta-competicion`,
      { fechaAltaCompeticion },
      { withCredentials: true }
    );
  }

  /**
   * Update fecha alta competidor inicial for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param fechaAltaCompetidorInicial Initial competitor registration date (YYYY-MM-DD format)
   */
  actualizarFechaAltaCompetidorInicialDeporte(
    alumnoId: number,
    deporte: string,
    fechaAltaCompetidorInicial: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/fecha-alta-competidor-inicial`,
      { fechaAltaCompetidorInicial },
      { withCredentials: true }
    );
  }

  /**
   * Update categoria for a specific sport
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param categoria Categoria name (INFANTIL, PRECADETE, CADETE, JUNIOR, SENIOR)
   */
  actualizarCategoriaDeporte(
    alumnoId: number,
    deporte: string,
    categoria: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/categoria`,
      { categoria },
      { withCredentials: true }
    );
  }

  /**
   * Update all competitor data in a single transaction (avoids race conditions)
   * @param alumnoId Student ID
   * @param deporte Sport name
   * @param datosCompetidor Object with all competitor fields
   */
  actualizarDatosCompetidor(
    alumnoId: number,
    deporte: string,
    datosCompetidor: {
      competidor?: boolean;
      fechaAltaCompeticion?: string;
      fechaAltaCompetidorInicial?: string;
      categoria?: string;
      peso?: number;
      fechaPeso?: string;
    }
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/deportes/${deporte}/datos-competidor`,
      datosCompetidor,
      { withCredentials: true }
    );
  }

  /**
   * Update the student's initial enrollment date (fechaAltaInicial)
   * This date affects the antiguedad calculation for all sports
   * @deprecated Use actualizarFechaAltaInicialDeporte for per-sport updates
   *
   * @param alumnoId Student ID
   * @param fechaAltaInicial New initial enrollment date (YYYY-MM-DD format)
   */
  actualizarFechaAltaInicial(
    alumnoId: number,
    fechaAltaInicial: string
  ): Observable<any> {
    return this.http.put<any>(
      `${this.urlBase}/${alumnoId}/fecha-alta-inicial`,
      { fechaAltaInicial },
      { withCredentials: true }
    );
  }
}
