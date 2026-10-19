import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { fechaApi } from '../../utils/fecha-api';
import {
  AlumnoDeMisGrupos,
  CargoPendienteProfesor,
  CobroCaja,
  MisCobrosPendientes,
  MotivoDeshacerCobro,
  ProductoVendible,
} from '../../interfaces/caja';

function numero(valor: unknown): number {
  const n = typeof valor === 'number' ? valor : Number(valor);
  return Number.isFinite(n) ? n : 0;
}

function fecha(valor: unknown): string | null {
  return fechaApi(valor)?.toISOString() ?? null;
}

function cobro(c: CobroCaja): CobroCaja {
  return { ...c, importe: numero(c.importe), fechaCobro: fecha(c.fechaCobro), venta: !!c.venta };
}

/**
 * «Mi caja» del profesor (`/api/caja/profesor/**`, pieza 6b-3b): cobra en efectivo cargos de sus alumnos, vende
 * productos de precio fijo y ve lo que lleva encima. Es la única API que una sesión «solo profesor» puede usar, así
 * que nada de aquí toca otros endpoints. Los errores los presenta la pantalla (los 400 traen `{mensaje}` en español).
 */
@Injectable({ providedIn: 'root' })
export class CajaProfesorService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/caja/profesor`;

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  cargosPendientes(): Observable<CargoPendienteProfesor[]> {
    return this.http.get<CargoPendienteProfesor[]>(`${this.api}/cargos-pendientes`, this.opciones()).pipe(
      map((lista) =>
        (lista ?? []).map((c) => ({ ...c, importe: numero(c.importe), fechaAsignacion: fecha(c.fechaAsignacion) }))
      )
    );
  }

  cobrarCargo(cargoId: number): Observable<CobroCaja> {
    return this.http.post<CobroCaja>(`${this.api}/cargos/${cargoId}/cobrar`, null, this.opciones()).pipe(map(cobro));
  }

  deshacerCobro(cobroId: number, motivo: MotivoDeshacerCobro): Observable<void> {
    return this.http.post<void>(`${this.api}/cobros/${cobroId}/deshacer`, { motivo }, this.opciones());
  }

  productos(): Observable<ProductoVendible[]> {
    return this.http
      .get<ProductoVendible[]>(`${this.api}/productos`, this.opciones())
      .pipe(map((lista) => (lista ?? []).map((p) => ({ ...p, precio: numero(p.precio) }))));
  }

  venderProducto(alumnoId: number, productoId: number): Observable<CobroCaja> {
    return this.http
      .post<CobroCaja>(`${this.api}/alumnos/${alumnoId}/productos/${productoId}/cobrar`, null, this.opciones())
      .pipe(map(cobro));
  }

  cobrosPendientesEntrega(): Observable<MisCobrosPendientes> {
    return this.http.get<MisCobrosPendientes>(`${this.api}/cobros-pendientes-entrega`, this.opciones()).pipe(
      map((m) => ({
        numeroCobros: numero(m?.numeroCobros),
        importe: numero(m?.importe),
        cobros: (m?.cobros ?? []).map(cobro),
      }))
    );
  }

  alumnos(): Observable<AlumnoDeMisGrupos[]> {
    return this.http
      .get<AlumnoDeMisGrupos[]>(`${this.api}/alumnos`, this.opciones())
      .pipe(map((lista) => (lista ?? []).map((a) => ({ ...a, grupos: a.grupos ?? [] }))));
  }
}
