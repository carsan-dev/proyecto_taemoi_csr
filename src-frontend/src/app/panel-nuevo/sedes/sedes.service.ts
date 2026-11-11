import { HttpClient, HttpContext, HttpEvent, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { CuerpoSede, NumeroFoto, Sede, SedeEditada } from './sedes-vista';

/** Las URL de las dos fotos de cada sede, por id de sede (de la vista previa de gestión de la web). */
export type UrlsFotosSede = ReadonlyMap<number, readonly [string | null, string | null]>;

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
  editar(id: number, cuerpo: CuerpoSede): Observable<SedeEditada> {
    return this.http.put<SedeEditada>(`${this.url}/${id}`, cuerpo, this.opciones());
  }

  cambiarActiva(id: number, activa: boolean): Observable<Sede> {
    return this.http.put<Sede>(`${this.url}/${id}/activa`, { activa }, this.opciones());
  }

  /** W12g: `PUT /api/sedes/{id}/fotos/{n}` (multipart `archivo`), con el progreso de la subida. 200 con la sede. */
  subirFoto(id: number, numero: NumeroFoto, archivo: File): Observable<HttpEvent<Sede>> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.put<Sede>(`${this.url}/${id}/fotos/${numero}`, datos, {
      ...this.opciones(),
      reportProgress: true,
      observe: 'events',
    });
  }

  /** W12g: `DELETE /api/sedes/{id}/fotos/{n}`. 200 con la sede, también si no tenía. */
  quitarFoto(id: number, numero: NumeroFoto): Observable<Sede> {
    return this.http.delete<Sede>(`${this.url}/${id}/fotos/${numero}`, this.opciones());
  }

  /**
   * W12g: las URL de las fotos guardadas, para la miniatura (`SedeDTO` solo trae el id de la imagen). Salen de la vista
   * previa de gestión de la web (`GET /api/publico/web?vistaPrevia=true`), que solo trae las sedes activas. Un fallo da
   * un mapa vacío: el formulario sigue sin miniatura.
   */
  urlsFotos(): Observable<UrlsFotosSede> {
    const params = new HttpParams().set('vistaPrevia', 'true');
    return this.http
      .get<{ sedes?: { id: number; fotoUrl?: string | null; foto2Url?: string | null }[] }>(`${environment.apiUrl}/publico/web`, {
        ...this.opciones(),
        params,
      })
      .pipe(
        map((web) => new Map((web?.sedes ?? []).map((s) => [s.id, [s.fotoUrl ?? null, s.foto2Url ?? null] as const]))),
        catchError(() => of(new Map()))
      );
  }
}
