import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

/**
 * Service for managing belt rank (Grado) operations
 * Handles retrieving available grades based on student age
 */
@Injectable({
  providedIn: 'root',
})
export class GradoService {
  private readonly urlBase = `${environment.apiUrl}/grados`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Get all available grades
   */
  obtenerGrados(): Observable<any> {
    return this.http.get<any>(this.urlBase, {
      withCredentials: true,
    });
  }

  /**
   * Get available grades based on student's birth date
   * This determines whether student gets minor or adult grade progression
   */
  obtenerGradosPorFechaNacimiento(fechaNacimiento: string, deporte: string = 'TAEKWONDO'): Observable<any> {
    return this.http.get<any>(`${this.urlBase}/disponibles/${fechaNacimiento}?deporte=${deporte}`, {
      withCredentials: true,
    });
  }
}
