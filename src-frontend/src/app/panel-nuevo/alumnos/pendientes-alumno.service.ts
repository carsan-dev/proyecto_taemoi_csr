import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';

export interface CargoPendiente {
  productoAlumnoId: number;
  concepto: string;
  importe: number;
  /** «AAAA-MM-DD»: primer día en que cuenta como vencido. */
  vencidoDesde: string;
}

/** Lo vencido de un alumno (`GET /api/tesoreria/pendientes-por-alumno`, 8b-3a). Solo vienen los que deben algo. */
export interface PendienteAlumno {
  alumnoId: number;
  importe: number;
  cargos: number;
  detalle: CargoPendiente[];
}

/** Lectura del pendiente vencido por alumno para el listado nuevo. Va en su chunk, no en el inicial. */
@Injectable({ providedIn: 'root' })
export class PendientesAlumnoService {
  private readonly http = inject(HttpClient);

  /**
   * Como mucho 25 ids (una página); el servidor ignora los de otra escuela. Con `errorLocal`, quien llama enseña el fallo
   * (el carnet de la ficha, con «Reintentar») y el interceptor no abre además su SweetAlert2 (8b-D2a).
   */
  obtener(alumnoIds: number[], errorLocal = false): Observable<PendienteAlumno[]> {
    const params = new HttpParams().set('alumnoIds', alumnoIds.join(','));
    return this.http.get<PendienteAlumno[]>(`${environment.apiUrl}/tesoreria/pendientes-por-alumno`, {
      params,
      withCredentials: true,
      ...(errorLocal ? { context: contextoErrorLocal() } : {}),
    });
  }
}
