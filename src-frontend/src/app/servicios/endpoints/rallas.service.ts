import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { PasoRallas, PrefijoAsistencia, RallasDelAlumno } from '../../interfaces/asistencia';

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

  /** Un paso que se guarda al momento; devuelve cómo queda. */
  paso(prefijo: PrefijoAsistencia, grupoId: number, alumnoId: number, paso: PasoRallas): Observable<RallasDelAlumno> {
    return this.http.post<RallasDelAlumno>(this.base(prefijo, grupoId, alumnoId), { paso }, this.opciones());
  }

  /** Con las 5 rallas: apto para examen y rallas a 0, a la vez. */
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
