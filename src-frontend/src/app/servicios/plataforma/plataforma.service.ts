import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { ErrorPlataforma, EscuelaPlataforma, FiltrosErroresPlataforma } from '../../interfaces/error-plataforma';
import { PaginatedResponse } from '../../interfaces/paginated-response';
import { AuthenticationService } from '../authentication/authentication.service';

/** Consultas de plataforma (todas las escuelas). Solo lo usan pantallas de carga diferida. */
@Injectable({ providedIn: 'root' })
export class PlataformaService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthenticationService);
  private readonly urlErrores = `${environment.apiUrl}/plataforma/errores`;

  /** true si la sesión actual es administradora de plataforma; una sola consulta por sesión. */
  esAdminPlataforma(): Observable<boolean> {
    return this.auth.esAdminPlataforma();
  }

  /** Todas las escuelas (id y nombre), ordenadas por nombre. */
  escuelas(): Observable<EscuelaPlataforma[]> {
    return this.http.get<EscuelaPlataforma[]>(`${environment.apiUrl}/plataforma/escuelas`, {
      withCredentials: true,
      context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
    });
  }

  errores(filtros: FiltrosErroresPlataforma): Observable<PaginatedResponse<ErrorPlataforma>> {
    let params = new HttpParams()
      .set('page', String(filtros.page ?? 1))
      .set('size', String(filtros.size ?? 25));

    if (filtros.organizacionId !== null && filtros.organizacionId !== undefined) {
      params = params.set('organizacionId', String(filtros.organizacionId));
    }
    if (filtros.estado) {
      params = params.set('estado', filtros.estado);
    }
    if (filtros.desde) {
      params = params.set('desde', filtros.desde);
    }
    if (filtros.hasta) {
      params = params.set('hasta', filtros.hasta);
    }

    return this.http.get<PaginatedResponse<ErrorPlataforma>>(this.urlErrores, {
      params,
      withCredentials: true,
      context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
    });
  }
}
