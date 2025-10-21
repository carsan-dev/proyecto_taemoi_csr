import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';

/**
 * Service for managing event (Evento) operations
 * Handles CRUD operations for public events displayed on the website
 */
@Injectable({
  providedIn: 'root',
})
export class EventoService {
  private readonly urlBase = `${environment.apiUrl}/eventos`;

  // State management
  private readonly eventosSubject = new BehaviorSubject<any[]>([]);
  public eventos$ = this.eventosSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  /**
   * Get all events
   */
  obtenerEventos(): void {
    this.http
      .get<any[]>(this.urlBase)
      .subscribe({
        next: (eventos) => {
          this.eventosSubject.next(eventos);
        },
        error: (error) => {
          console.error('Error al obtener los eventos:', error);
        },
      });
  }

  /**
   * Get event by ID
   */
  obtenerEventoPorId(eventoId: number): Observable<any> {
    return this.http.get<any>(`${this.urlBase}/${eventoId}`, {
      withCredentials: true,
    });
  }

  /**
   * Create a new event
   */
  crearEvento(eventoData: any, imagen: File | null): Observable<any> {
    const formData = new FormData();
    formData.append('nuevo', JSON.stringify(eventoData));
    if (imagen) {
      formData.append('file', imagen, imagen.name);
    }
    return this.http
      .post<any>(`${this.urlBase}/crear`, formData, {
        withCredentials: true,
      })
      .pipe(
        tap((nuevoEvento) => {
          const eventosActuales = this.eventosSubject.getValue();
          this.eventosSubject.next([...eventosActuales, nuevoEvento]);
        })
      );
  }

  /**
   * Update an existing event
   */
  actualizarEvento(id: number, formData: FormData): Observable<any> {
    return this.http.put<any>(`${this.urlBase}/${id}`, formData, {
      withCredentials: true,
    });
  }

  /**
   * Delete event image
   */
  eliminarImagenEvento(id: number): Observable<any> {
    return this.http.delete(`${this.urlBase}/${id}/imagen`, {
      withCredentials: true,
    });
  }

  /**
   * Delete an event
   */
  eliminarEvento(id: number): void {
    this.http
      .delete<any>(`${this.urlBase}/${id}`, { withCredentials: true })
      .subscribe({
        next: () => {
          const eventosActuales = this.eventosSubject.getValue();
          const eventosActualizados = eventosActuales.filter(
            (evento) => evento.id !== id
          );
          this.eventosSubject.next(eventosActualizados);
        },
        error: (error) => {
          console.error('Error al eliminar el evento:', error);
        },
      });
  }
}
