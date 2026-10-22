import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export type TipoPendiente = 'MENSUALIDAD' | 'EXAMEN';

export interface CargoVencido {
  productoAlumnoId: number;
  alumnoId: number | null;
  alumnoNombre: string;
  concepto: string;
  tipo: TipoPendiente;
  importe: number;
  /** «AAAA-MM». */
  mes: string;
  /** «AAAA-MM-DD»: primer día en que cuenta como vencido. */
  vencidoDesde: string;
  sedeId: number | null;
}

/** `GET /api/tesoreria/pendiente-vencido` (8b-2, decisiones 242 y 267). */
export interface PendienteVencido {
  total: number;
  cargos: number;
  alumnos: number;
  diaLimiteMensualidad: number;
  porTipo: { tipo: TipoPendiente; importe: number; cargos: number }[];
  porMes: { mes: string; importe: number; cargos: number }[];
  porSede: { sedeId: number | null; sede: string | null; importe: number; cargos: number }[];
  masAntiguos: CargoVencido[];
}

/** Lectura del pendiente vencido para el Inicio nuevo. Va en el chunk del Inicio, no en el inicial. */
@Injectable({ providedIn: 'root' })
export class PendienteVencidoService {
  private readonly http = inject(HttpClient);

  obtener(): Observable<PendienteVencido> {
    return this.http.get<PendienteVencido>(`${environment.apiUrl}/tesoreria/pendiente-vencido`, {
      withCredentials: true,
    });
  }
}
