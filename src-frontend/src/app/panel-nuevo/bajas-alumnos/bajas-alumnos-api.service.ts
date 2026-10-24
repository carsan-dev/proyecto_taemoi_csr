import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';
import { ResumenCargos } from './bajas-alumnos-vista';

/**
 * Escrituras y lecturas propias de «Bajas y eliminaciones» (8b-3l): las MISMAS peticiones que
 * `EndpointsService.darDeBajaAlumno` y `eliminarAlumnos` (método, ruta y cuerpo), con `contextoErrorLocal()`: el fallo
 * de cada alumno lo enseña el diálogo y el interceptor no abre además un SweetAlert2 por alumno. Y el resumen de cargos
 * nuevo (P-3). Va en el chunk de la pantalla, no en el inicial.
 */
@Injectable({ providedIn: 'root' })
export class BajasAlumnosApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** `PUT /alumnos/{id}/baja` con `{}`, como hoy. */
  darDeBaja(id: number): Observable<any> {
    return this.http.put<any>(`${this.base}/alumnos/${id}/baja`, {}, { withCredentials: true, context: contextoErrorLocal() });
  }

  /** `DELETE /alumnos/{id}`, como hoy. Solo el propietario: al resto, 403 con su motivo. */
  eliminar(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/alumnos/${id}`, { withCredentials: true, context: contextoErrorLocal() });
  }

  /**
   * 8b-3l bis (decisión 287): `POST /alumnos/{id}/anonimizar`, eliminar anonimizando al alumno con pagos. Solo el
   * propietario: al resto, 403 con su motivo.
   */
  anonimizar(id: number): Observable<any> {
    return this.http.post<any>(`${this.base}/alumnos/${id}/anonimizar`, {}, { withCredentials: true, context: contextoErrorLocal() });
  }

  /** `GET /alumnos/cargos-resumen?alumnoIds=…` (P-3): los cargos de los que se van a eliminar. */
  resumenCargos(ids: number[]): Observable<ResumenCargos[]> {
    const params = new HttpParams().set('alumnoIds', ids.join(','));
    return this.http.get<ResumenCargos[]>(`${this.base}/alumnos/cargos-resumen`, {
      params,
      withCredentials: true,
      context: contextoErrorLocal(),
    });
  }
}
