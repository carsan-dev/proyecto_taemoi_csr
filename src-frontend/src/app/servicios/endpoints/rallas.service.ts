import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import {
  ConfirmacionQuitarApto, PasoRallas, PrefijoAsistencia, RallasAlQuitar, RallasDelAlumno,
} from '../../interfaces/asistencia';

/** Una disciplina en «Rallas hacia el examen» de Configuración. */
export interface DisciplinaRallas {
  deporte: string;
  nombre: string;
  activas: boolean;
}

/**
 * Rallas hacia el examen (decisiones 510-513). Los pasos y «Marcar apto» desde la hoja de Pasar lista, siempre desde un
 * grupo y con los dos prefijos de asistencia (profesor o gestión); y la configuración por disciplina, solo del
 * propietario. Los errores los presenta cada pantalla (traen `{mensaje}` en español).
 */
@Injectable({ providedIn: 'root' })
export class RallasService {
  private readonly http = inject(HttpClient);

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  private base(prefijo: PrefijoAsistencia, grupoId: number, alumnoId: number): string {
    const grupo = prefijo === 'profesor' ? `profesor/grupos/${grupoId}` : `grupos/${grupoId}`;
    return `${environment.apiUrl}/${grupo}/alumnos/${alumnoId}/rallas`;
  }

  /**
   * Un paso que se guarda al momento; devuelve cómo queda. Al quitar desde las 5 rallas siendo apto (o con pases o
   * convocatorias), la `confirmacion` lleva los ids que se vieron en el aviso; si algo cambió, el servidor da 409.
   */
  paso(
    prefijo: PrefijoAsistencia, grupoId: number, alumnoId: number, paso: PasoRallas, confirmacion?: ConfirmacionQuitarApto,
  ): Observable<RallasDelAlumno> {
    return this.http.post<RallasDelAlumno>(this.base(prefijo, grupoId, alumnoId), { paso, ...confirmacion }, this.opciones());
  }

  /** Lo que pasaría al quitar desde las 5 rallas: si es apto, sus pases pendientes y sus convocatorias. */
  alQuitar(prefijo: PrefijoAsistencia, grupoId: number, alumnoId: number): Observable<RallasAlQuitar> {
    return this.http.get<RallasAlQuitar>(`${this.base(prefijo, grupoId, alumnoId)}/al-quitar`, this.opciones()).pipe(
      map((r) => ({ ...r, pases: r?.pases ?? [], convocatorias: r?.convocatorias ?? [] })),
    );
  }

  /** Con las 5 rallas: apto para examen; las rallas se quedan hasta que cambie de grado. */
  apto(prefijo: PrefijoAsistencia, grupoId: number, alumnoId: number): Observable<RallasDelAlumno> {
    return this.http.post<RallasDelAlumno>(`${this.base(prefijo, grupoId, alumnoId)}/apto`, null, this.opciones());
  }

  configuracion(): Observable<DisciplinaRallas[]> {
    return this.http
      .get<DisciplinaRallas[]>(`${environment.apiUrl}/admin/configuracion/rallas`, this.opciones())
      .pipe(map((lista) => lista ?? []));
  }

  /** Solo las que cambian; devuelve la lista completa. */
  guardarConfiguracion(cambios: Record<string, boolean>): Observable<DisciplinaRallas[]> {
    return this.http
      .put<DisciplinaRallas[]>(`${environment.apiUrl}/admin/configuracion/rallas`, cambios, this.opciones())
      .pipe(map((lista) => lista ?? []));
  }
}
