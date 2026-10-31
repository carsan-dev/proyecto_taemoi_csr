import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { CuerpoSede, Sede } from './sedes-vista';

/**
 * API de sedes (`/api/sedes`, propietario y gestión; pieza E8-2). Los errores `{mensaje}` los presenta la pantalla
 * (409 de nombre repetido o de última sede activa), así que no pasan por el diálogo global.
 */
@Injectable({ providedIn: 'root' })
export class SedesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/sedes`;

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  /** Todas las sedes de la escuela, también las inactivas, en el orden de la API. */
  listar(): Observable<Sede[]> {
    return this.http.get<Sede[]>(this.url, this.opciones());
  }

  crear(cuerpo: CuerpoSede): Observable<Sede> {
    return this.http.post<Sede>(this.url, cuerpo, this.opciones());
  }

  /** Reemplaza todos los datos editables: el cuerpo lleva también el `aforoTurno`. */
  editar(id: number, cuerpo: CuerpoSede): Observable<Sede> {
    return this.http.put<Sede>(`${this.url}/${id}`, cuerpo, this.opciones());
  }

  cambiarActiva(id: number, activa: boolean): Observable<Sede> {
    return this.http.put<Sede>(`${this.url}/${id}/activa`, { activa }, this.opciones());
  }
}
