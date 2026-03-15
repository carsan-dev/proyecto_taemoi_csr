import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Turno } from '../../../interfaces/turno';

/**
 * Service for managing schedule (Turno) operations
 * Handles CRUD operations for class schedules
 */
@Injectable({
  providedIn: 'root',
})
export class TurnoService {
  private readonly urlBase = `${environment.apiUrl}/turnos`;

  // State management
  private readonly turnosSubject = new BehaviorSubject<Turno[]>([]);
  public turnos$ = this.turnosSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  /**
   * Get all schedules (turnos)
   */
  obtenerTurnos(): void {
    this.http
      .get<any>(this.urlBase, {
        withCredentials: true,
      })
      .subscribe({
        next: (turnos) => {
          this.turnosSubject.next(turnos);
        },
        error: (error) => {
          console.error('Error al obtener los turnos:', error);
        },
      });
  }

  /**
   * Get all schedules as DTO (public endpoint)
   */
  obtenerTurnosDTO(): Observable<any> {
    return this.http.get<any>(`${this.urlBase}/dto`);
  }

  /**
   * Get schedule by ID
   */
  obtenerTurnoPorId(turnoId: number): Observable<any> {
    return this.http.get<any>(`${this.urlBase}/${turnoId}`, {
      withCredentials: true,
    });
  }

  /**
   * Create a schedule without assigning to a group
   */
  crearTurnoSinGrupo(turnoData: any): Observable<any> {
    return this.http.post<any>(`${this.urlBase}/crear`, turnoData, {
      withCredentials: true,
    });
  }

  /**
   * Create a schedule and assign it to a group
   */
  crearTurnoConGrupo(turnoData: any): Observable<any> {
    return this.http.post<any>(
      `${this.urlBase}/crear-asignando-grupo`,
      turnoData,
      { withCredentials: true }
    );
  }

  /**
   * Update an existing schedule
   */
  actualizarTurno(turnoId: number, turnoData: any): Observable<any> {
    return this.http.put<any>(`${this.urlBase}/${turnoId}`, turnoData, {
      withCredentials: true,
    });
  }

  /**
   * Delete a schedule
   */
  eliminarTurno(turnoId: number): Observable<any> {
    return this.http.delete<any>(`${this.urlBase}/${turnoId}`, {
      withCredentials: true,
    });
  }
}
