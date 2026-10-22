import { HttpClient, HttpContext, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

/**
 * Contexto de una lectura cuyo fallo enseña la pantalla nueva con su bloque de error (8b-D2a): el interceptor de errores
 * no abre además su SweetAlert2. Un 401 de sesión caducada lo sigue tratando el interceptor (cierra la sesión y lleva al
 * login): `ERROR_HANDLED_LOCALLY` no lo salta.
 */
export function contextoErrorLocal(): HttpContext {
  return new HttpContext().set(ERROR_HANDLED_LOCALLY, true);
}

/**
 * Las lecturas de bloque de las pantallas nuevas que hoy pasan por `EndpointsService` (8b-D2a): la MISMA petición
 * (ruta, parámetros y cabeceras) que su método de siempre, con `contextoErrorLocal()`. Así las pantallas de hoy no
 * cambian y esto va en los chunks del panel nuevo, no en el inicial.
 */
@Injectable({ providedIn: 'root' })
export class LecturasPanelService {
  private readonly http = inject(HttpClient);

  /** Igual que `EndpointsService.obtenerAlumnos` (el listado paginado con filtros en el servidor). */
  alumnos(
    page: number,
    size: number,
    nombre = '',
    incluirInactivos = false,
    aptoParaExamen = false,
    deporte: string | null = null
  ): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('incluirInactivos', incluirInactivos.toString());
    if (nombre) {
      params = params.set('nombre', nombre);
    }
    if (aptoParaExamen) {
      params = params.set('aptoParaExamen', 'true');
    }
    if (deporte) {
      params = params.set('deporte', deporte);
    }
    return this.http.get<any>(`${environment.apiUrl}/alumnos`, {
      params,
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Loading': 'true' }),
      context: contextoErrorLocal(),
    });
  }

  /** Igual que `EndpointsService.obtenerLimiteTurno`. */
  limiteTurno(): Observable<number> {
    return this.http.get<number>(`${environment.apiUrl}/turnos/limite`, { context: contextoErrorLocal() });
  }
}
