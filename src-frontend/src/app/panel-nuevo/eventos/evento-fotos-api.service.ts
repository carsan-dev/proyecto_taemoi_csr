import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

/** Una foto del evento tal como la devuelve la API (`url` absoluta al endpoint de la imagen). */
export interface FotoEventoApi {
  id: number;
  url: string;
  orden: number;
}

/**
 * Las cinco peticiones de la galería de fotos de un evento (E8c · F2). Vive junto al bloque, en el chunk del formulario,
 * y no en `EndpointsService`, que va en el bundle inicial. Los errores los trata el bloque en línea (sin el aviso global).
 */
@Injectable({ providedIn: 'root' })
export class EventoFotosApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  /** `GET /eventos/{id}/fotos`. */
  listar(eventoId: number): Observable<FotoEventoApi[]> {
    return this.http.get<FotoEventoApi[]>(`${this.base}/eventos/${eventoId}/fotos`, this.opciones());
  }

  /** `POST /eventos/{id}/fotos`: multipart con el campo «archivo». */
  subir(eventoId: number, archivo: File): Observable<FotoEventoApi> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post<FotoEventoApi>(`${this.base}/eventos/${eventoId}/fotos`, datos, this.opciones());
  }

  /** `PUT /eventos/{id}/fotos/orden`: los ids en el orden final (el conjunto exacto de las guardadas). */
  ordenar(eventoId: number, ids: number[]): Observable<FotoEventoApi[]> {
    return this.http.put<FotoEventoApi[]>(`${this.base}/eventos/${eventoId}/fotos/orden`, ids, this.opciones());
  }

  /** `DELETE /eventos/{id}/fotos/{fotoId}`. */
  eliminar(eventoId: number, fotoId: number): Observable<unknown> {
    return this.http.delete(`${this.base}/eventos/${eventoId}/fotos/${fotoId}`, this.opciones());
  }
}
