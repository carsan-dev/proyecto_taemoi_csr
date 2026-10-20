import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { CrearSolicitud, SegmentoSolicitudes, Solicitud } from '../../interfaces/solicitud';
import { fechaApi } from '../../utils/fecha-api';

function fecha(valor: unknown): string | null {
  return fechaApi(valor)?.toISOString() ?? null;
}

function importe(valor: unknown): number | null {
  if (valor === null || valor === undefined || valor === '') {
    return null;
  }
  const n = Number(valor);
  return Number.isFinite(n) ? n : null;
}

/** Normaliza una solicitud tal como llega de la API (fechas `LocalDateTime` en array, importes `BigDecimal`). */
export function normalizarSolicitud(s: Solicitud): Solicitud {
  return {
    ...s,
    solicitadoEn: fecha(s.solicitadoEn),
    resueltoEn: fecha(s.resueltoEn),
    encargadoEn: fecha(s.encargadoEn),
    recibidoEn: fecha(s.recibidoEn),
    entregadoEn: fecha(s.entregadoEn),
    importe: importe(s.importe),
    devolucionPendiente: !!s.devolucionPendiente,
    propia: !!s.propia,
  };
}

/**
 * Solicitudes al propietario, rutas de gestión (`/api/solicitudes`, piezas 6c-2 y 6c-3). Los errores los presenta la
 * pantalla con el `mensaje` del servidor, así que no pasan por el diálogo global.
 */
@Injectable({ providedIn: 'root' })
export class SolicitudesService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/solicitudes`;

  private opciones(params?: HttpParams) {
    return {
      params,
      withCredentials: true,
      context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
    };
  }

  listar(segmento: SegmentoSolicitudes): Observable<Solicitud[]> {
    return this.http
      .get<Solicitud[]>(this.url, this.opciones(new HttpParams().set('segmento', segmento)))
      .pipe(map((lista) => (lista ?? []).map(normalizarSolicitud)));
  }

  crear(peticion: CrearSolicitud): Observable<Solicitud> {
    return this.http.post<Solicitud>(this.url, peticion, this.opciones()).pipe(map(normalizarSolicitud));
  }

  retirar(id: number): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${this.url}/${id}/retirar`, null, this.opciones()).pipe(map(normalizarSolicitud));
  }

  aceptar(id: number): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${this.url}/${id}/aceptar`, null, this.opciones()).pipe(map(normalizarSolicitud));
  }

  denegar(id: number, respuesta: string): Observable<Solicitud> {
    return this.http
      .post<Solicitud>(`${this.url}/${id}/denegar`, { respuesta }, this.opciones())
      .pipe(map(normalizarSolicitud));
  }

  // ------------------------------------------------------------------ encargos (6c-5) y devolución (6c-6)

  /** PEDIDO → ENCARGADO. Propietario, gestión y responsable de la sede de la solicitud. */
  marcarEncargado(id: number): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${this.url}/${id}/encargado`, null, this.opciones()).pipe(map(normalizarSolicitud));
  }

  /** ENCARGADO → RECIBIDO. */
  marcarRecibido(id: number): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${this.url}/${id}/recibido`, null, this.opciones()).pipe(map(normalizarSolicitud));
  }

  /** PEDIDO, ENCARGADO o RECIBIDO → RECHAZADO, con motivo obligatorio (D6c-1). Cae el encargo (§6.3). */
  rechazar(id: number, motivo: string): Observable<Solicitud> {
    return this.http
      .post<Solicitud>(`${this.url}/${id}/rechazar`, { motivo }, this.opciones())
      .pipe(map(normalizarSolicitud));
  }

  /**
   * Decisión 195: la sede registra la devolución de un encargo caído con el dinero ya en su caja. Sin importe: es el
   * del cobro entero (D6c-11). Nota opcional (500).
   */
  registrarDevolucion(id: number, nota: string | null): Observable<Solicitud> {
    return this.http
      .post<Solicitud>(`${this.url}/${id}/devolucion`, { nota }, this.opciones())
      .pipe(map(normalizarSolicitud));
  }
}
