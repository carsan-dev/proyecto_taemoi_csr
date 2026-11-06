import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { CuerpoFestivo, Festivo, ImpactoFestivo } from './festivos-vista';

/**
 * API de festivos (`/api/festivos`, decisiones 456-458): gestión y responsable de sede (este, en sus sedes). Los errores
 * `{mensaje}` los presenta cada pantalla, así que no pasan por el diálogo global.
 */
@Injectable({ providedIn: 'root' })
export class FestivosService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/festivos`;

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  /** Los próximos (`pasados=false`) o los pasados; `sedeId` null, todas las de la sesión. */
  listar(pasados: boolean, sedeId: number | null = null): Observable<Festivo[]> {
    let params = new HttpParams().set('pasados', pasados);
    if (sedeId !== null) {
      params = params.set('sedeId', sedeId);
    }
    return this.http
      .get<Festivo[]>(this.url, { ...this.opciones(), params })
      .pipe(map((lista) => (lista ?? []).map((f) => ({ ...f, sedes: f.sedes ?? [] }))));
  }

  crear(cuerpo: CuerpoFestivo): Observable<Festivo> {
    return this.http.post<Festivo>(this.url, cuerpo, this.opciones());
  }

  editar(id: number, cuerpo: CuerpoFestivo): Observable<Festivo> {
    return this.http.put<Festivo>(`${this.url}/${id}`, cuerpo, this.opciones());
  }

  quitar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`, this.opciones());
  }

  /** Clases que cubriría; al editar, sin contar el propio festivo (`excluirFestivoId`). */
  impacto(cuerpo: CuerpoFestivo, excluirFestivoId: number | null = null): Observable<ImpactoFestivo> {
    const body = excluirFestivoId === null ? cuerpo : { ...cuerpo, excluirFestivoId };
    return this.http
      .post<ImpactoFestivo>(`${this.url}/impacto`, body, this.opciones())
      .pipe(
        map((i) => ({
          clases: i?.clases ?? 0,
          conLista: i?.conLista ?? 0,
          conListaDetalle: i?.conListaDetalle ?? [],
          clasesDetalle: i?.clasesDetalle ?? [],
        }))
      );
  }
}
