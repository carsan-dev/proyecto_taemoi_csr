import { HttpClient, HttpContext, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { GrupoResumen } from '../../interfaces/grupo-resumen';
import { PaginatedResponse } from '../../interfaces/paginated-response';
import { TesoreriaMovimiento } from '../../interfaces/tesoreria-movimiento';
import { TesoreriaResumen } from '../../interfaces/tesoreria-resumen';
import { TurnoResumen } from '../../interfaces/turno-resumen';

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

  /** Igual que `EndpointsService.obtenerResumenGrupos` (Grupos, 8b-3c). */
  resumenGrupos(): Observable<GrupoResumen[]> {
    return this.http.get<GrupoResumen[]>(`${environment.apiUrl}/grupos/resumen`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Loading': 'true' }),
      context: contextoErrorLocal(),
    });
  }

  /** Igual que `EndpointsService.obtenerResumenTurnos` (Grupos, 8b-3c). */
  resumenTurnos(): Observable<TurnoResumen[]> {
    return this.http.get<TurnoResumen[]>(`${environment.apiUrl}/turnos/resumen`, {
      withCredentials: true,
      headers: new HttpHeaders({ 'X-Skip-Loading': 'true' }),
      context: contextoErrorLocal(),
    });
  }

  /**
   * La petición de `EndpointsService.obtenerConteoAlumnosPorGrupo` (Grupos, 8b-3c), que hoy se suscribe sola y guarda
   * el resultado en el servicio: aquí se devuelve, para que la pantalla sepa si falló.
   */
  conteoAlumnosPorGrupo(): Observable<Record<string, { active?: number; total?: number }>> {
    return this.http.get<Record<string, { active?: number; total?: number }>>(`${environment.apiUrl}/grupos/conteo-alumnos`, {
      withCredentials: true,
      context: contextoErrorLocal(),
    });
  }

  /** Igual que `EndpointsService.obtenerAlumnosPorTipo` (Grupos, 8b-3c). */
  alumnosPorTipo(tipo: string): Observable<any[]> {
    return this.http.get<any[]>(`${environment.apiUrl}/grupos/tipo/${encodeURIComponent(tipo)}/alumnos`, {
      withCredentials: true,
      context: contextoErrorLocal(),
    });
  }
  /** Igual que `EndpointsService.obtenerTesoreriaResumen` (Tesorería, 8b-3d). */
  tesoreriaResumen(mes: number | null, ano: number | null, deporte = 'TODOS', soloActivos = true): Observable<TesoreriaResumen> {
    return this.http.get<TesoreriaResumen>(`${environment.apiUrl}/tesoreria/resumen`, {
      params: paramsTesoreria(mes, ano, deporte, undefined, undefined, soloActivos),
      withCredentials: true,
      context: contextoErrorLocal(),
    });
  }

  /** Igual que `EndpointsService.obtenerTesoreriaMovimientos` (Tesorería, 8b-3d). */
  tesoreriaMovimientos(
    mes: number | null,
    ano: number | null,
    deporte = 'TODOS',
    pagado?: boolean,
    texto?: string,
    soloActivos = true,
    page = 1,
    size = 25
  ): Observable<PaginatedResponse<TesoreriaMovimiento>> {
    const params = paramsTesoreria(mes, ano, deporte, pagado, texto, soloActivos)
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PaginatedResponse<TesoreriaMovimiento>>(`${environment.apiUrl}/tesoreria/movimientos`, {
      params,
      withCredentials: true,
      context: contextoErrorLocal(),
    });
  }

  /** Igual que `EndpointsService.obtenerTesoreriaAniosDisponibles` (Tesorería, 8b-3d; hoy su fallo se ignora). */
  tesoreriaAnios(): Observable<number[]> {
    return this.http.get<number[]>(`${environment.apiUrl}/tesoreria/anios-disponibles`, {
      withCredentials: true,
      context: contextoErrorLocal(),
    });
  }

  /** Igual que `EndpointsService.obtenerTodosLosAlumnosSinPaginar` (certificados de la Tesorería, 8b-3d). */
  alumnosSinPaginar(incluirInactivos = false): Observable<any[]> {
    return this.http.get<any[]>(`${environment.apiUrl}/alumnos`, {
      params: new HttpParams().set('incluirInactivos', incluirInactivos.toString()),
      withCredentials: true,
      context: contextoErrorLocal(),
    });
  }
}

/** Los parámetros de la tesorería de hoy (`EndpointsService.construirParamsTesoreria`, privado), en el mismo orden. */
export function paramsTesoreria(
  mes: number | null,
  ano: number | null,
  deporte: string,
  pagado?: boolean,
  texto?: string,
  soloActivos?: boolean
): HttpParams {
  let params = new HttpParams();
  if (ano !== null && ano !== undefined) {
    params = params.set('ano', ano.toString());
  }
  if (mes !== null && mes !== undefined) {
    params = params.set('mes', mes.toString());
  }
  if (deporte && deporte !== 'TODOS') {
    params = params.set('deporte', deporte);
  }
  if (pagado !== undefined && pagado !== null) {
    params = params.set('pagado', pagado.toString());
  }
  const textoNormalizado = texto?.trim();
  if (textoNormalizado) {
    params = params.set('texto', textoNormalizado);
  }
  if (soloActivos !== undefined && soloActivos !== null) {
    params = params.set('soloActivos', soloActivos.toString());
  }
  return params;
}
