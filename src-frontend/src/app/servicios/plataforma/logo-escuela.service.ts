import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { EscuelaLogo } from '../../interfaces/escuela-logo';

/** Logo y color de cada escuela (solo la sesión de plataforma). Los errores los muestra la pantalla en la fila. */
@Injectable({ providedIn: 'root' })
export class LogoEscuelaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/plataforma/escuelas`;

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  escuelas(): Observable<EscuelaLogo[]> {
    return this.http.get<EscuelaLogo[]>(this.base, this.opciones());
  }

  subir(organizacionId: number, archivo: File): Observable<EscuelaLogo> {
    const cuerpo = new FormData();
    cuerpo.append('archivo', archivo);
    return this.http.put<EscuelaLogo>(`${this.base}/${organizacionId}/logo`, cuerpo, this.opciones());
  }

  quitar(organizacionId: number): Observable<EscuelaLogo> {
    return this.http.delete<EscuelaLogo>(`${this.base}/${organizacionId}/logo`, this.opciones());
  }

  /** Imagen del logo (404 si la escuela no tiene). */
  logo(organizacionId: number): Observable<Blob> {
    return this.http.get(`${this.base}/${organizacionId}/logo`, { ...this.opciones(), responseType: 'blob' });
  }

  /** Guarda (`#RRGGBB`) o quita (`null`) el color de la escuela (8b-T). El servidor responde también con el tema. */
  guardarColor(organizacionId: number, colorPrimario: string | null): Observable<EscuelaLogo> {
    return this.http.put<EscuelaLogo>(`${this.base}/${organizacionId}/marca`, { colorPrimario }, this.opciones());
  }
}
