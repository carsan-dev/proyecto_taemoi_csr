import { HttpClient, HttpContext, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { fechaApi } from '../../utils/fecha-api';
import {
  CobroCaja,
  CrearMovimientoCaja,
  EntregaCaja,
  MovimientoCaja,
  PaginaCaja,
  PendienteCobrador,
  RegistrarEntregaCaja,
  ResumenCajaSede,
  SedeCaja,
} from '../../interfaces/caja';

/** Período de la caja: fechas locales `YYYY-MM-DD`, ambas incluidas. */
export interface PeriodoCaja {
  desde: string;
  hasta: string;
}

export const TAMANO_PAGINA_ENTREGAS = 25;

/** Primer y último día de un mes (1-12). */
export function periodoDeMes(ano: number, mes: number): PeriodoCaja {
  const dos = (n: number) => String(n).padStart(2, '0');
  const ultimo = new Date(ano, mes, 0).getDate();
  return { desde: `${ano}-${dos(mes)}-01`, hasta: `${ano}-${dos(mes)}-${dos(ultimo)}` };
}

/** Mensaje de error de la API (`{"mensaje"}`), si lo trae. */
export function mensajeDeError(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const cuerpo = error.error;
  if (typeof cuerpo === 'string' && cuerpo.trim()) {
    return cuerpo.trim();
  }
  const mensaje = cuerpo?.mensaje ?? cuerpo?.message;
  return typeof mensaje === 'string' && mensaje.trim() ? mensaje.trim() : null;
}

function numero(valor: unknown): number {
  const n = typeof valor === 'number' ? valor : Number(valor);
  return Number.isFinite(n) ? n : 0;
}

/**
 * Fechas de la API a texto ISO. La API serializa como marca de tiempo (WebConfig con `@EnableWebMvc`): los
 * `LocalDateTime` llegan como [año, mes, día, hora, …] (hora local) y los `java.util.Date` en milisegundos.
 */
function fecha(valor: unknown): string | null {
  return fechaApi(valor)?.toISOString() ?? null;
}

function cobro(c: CobroCaja): CobroCaja {
  return { ...c, importe: numero(c.importe), fechaCobro: fecha(c.fechaCobro) };
}

function entrega(e: EntregaCaja): EntregaCaja {
  return {
    ...e,
    importeDeclarado: numero(e.importeDeclarado),
    importeContado: numero(e.importeContado),
    diferencia: numero(e.diferencia),
    fecha: fecha(e.fecha) ?? '',
    anuladaEn: fecha(e.anuladaEn),
    cobroIds: e.cobroIds ?? [],
    autoentrega: !!e.autoentrega,
    movimientosDeCobro: (e.movimientosDeCobro ?? []).map((m) => ({ ...m, importe: numero(m.importe) })),
  };
}

function movimiento(m: MovimientoCaja): MovimientoCaja {
  return { ...m, importe: numero(m.importe), fecha: fecha(m.fecha) ?? '', anuladoEn: fecha(m.anuladoEn) };
}

/**
 * API de la caja de sede (`/api/caja/sedes/{id}/…`): responsable de la sede, gestor y propietario. Los errores los
 * presenta la pantalla (409 de concurrencia, 400 con `{mensaje}`), así que no pasan por el diálogo global.
 */
@Injectable({ providedIn: 'root' })
export class CajaService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  private opciones(params?: HttpParams) {
    return {
      params,
      withCredentials: true,
      context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
    };
  }

  /**
   * Sedes cuya caja puede ver la sesión. No hay API de sedes todavía (entrega 8): se toman de la tesorería por sede,
   * que ya filtra por alcance (gestión: todas las de la escuela con cargos; responsable: las suyas).
   */
  sedesGestionables(): Observable<SedeCaja[]> {
    return this.http
      .get<{ porSede?: Array<{ sedeId: number | null; nombre: string | null }> }>(
        `${this.api}/tesoreria/resumen`,
        this.opciones()
      )
      .pipe(
        map((resumen) =>
          (resumen?.porSede ?? [])
            .filter((fila) => fila.sedeId !== null && fila.sedeId !== undefined)
            .map((fila) => ({ id: Number(fila.sedeId), nombre: fila.nombre?.trim() || `Sede ${fila.sedeId}` }))
            .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
        )
      );
  }

  resumen(sedeId: number, periodo: PeriodoCaja): Observable<ResumenCajaSede> {
    const params = new HttpParams().set('desde', periodo.desde).set('hasta', periodo.hasta);
    return this.http.get<ResumenCajaSede>(`${this.api}/caja/sedes/${sedeId}/resumen`, this.opciones(params)).pipe(
      map((r) => ({
        ...r,
        pendienteDeEntregar: numero(r.pendienteDeEntregar),
        recibidoEnPeriodo: numero(r.recibidoEnPeriodo),
        diferenciasEnPeriodo: numero(r.diferenciasEnPeriodo),
        saldoCaja: numero(r.saldoCaja),
      }))
    );
  }

  pendienteEntrega(sedeId: number): Observable<PendienteCobrador[]> {
    return this.http
      .get<PendienteCobrador[]>(`${this.api}/caja/sedes/${sedeId}/pendiente-entrega`, this.opciones())
      .pipe(
        map((filas) =>
          (filas ?? []).map((p) => ({
            ...p,
            importe: numero(p.importe),
            cobroMasAntiguo: fecha(p.cobroMasAntiguo),
            cobros: (p.cobros ?? []).map(cobro),
          }))
        )
      );
  }

  registrarEntrega(sedeId: number, peticion: RegistrarEntregaCaja): Observable<EntregaCaja> {
    return this.http
      .post<EntregaCaja>(`${this.api}/caja/sedes/${sedeId}/entregas`, peticion, this.opciones())
      .pipe(map(entrega));
  }

  /** Historial paginado; `pagina` empieza en 1. */
  entregas(sedeId: number, pagina: number): Observable<PaginaCaja<EntregaCaja>> {
    const params = new HttpParams().set('page', String(pagina)).set('size', String(TAMANO_PAGINA_ENTREGAS));
    return this.http
      .get<PaginaCaja<EntregaCaja>>(`${this.api}/caja/sedes/${sedeId}/entregas`, this.opciones(params))
      .pipe(map((p) => ({ ...p, content: (p?.content ?? []).map(entrega) })));
  }

  anularEntrega(entregaId: number, motivo: string): Observable<EntregaCaja> {
    return this.http
      .post<EntregaCaja>(`${this.api}/caja/entregas/${entregaId}/anular`, { motivo }, this.opciones())
      .pipe(map(entrega));
  }

  movimientos(sedeId: number, periodo: PeriodoCaja): Observable<MovimientoCaja[]> {
    const params = new HttpParams().set('desde', periodo.desde).set('hasta', periodo.hasta);
    return this.http
      .get<MovimientoCaja[]>(`${this.api}/caja/sedes/${sedeId}/movimientos`, this.opciones(params))
      .pipe(map((lista) => (lista ?? []).map(movimiento)));
  }

  crearMovimiento(sedeId: number, peticion: CrearMovimientoCaja): Observable<MovimientoCaja> {
    return this.http
      .post<MovimientoCaja>(`${this.api}/caja/sedes/${sedeId}/movimientos`, peticion, this.opciones())
      .pipe(map(movimiento));
  }

  anularMovimiento(movimientoId: number, motivo: string): Observable<MovimientoCaja> {
    return this.http
      .post<MovimientoCaja>(`${this.api}/caja/movimientos/${movimientoId}/anular`, { motivo }, this.opciones())
      .pipe(map(movimiento));
  }
}
