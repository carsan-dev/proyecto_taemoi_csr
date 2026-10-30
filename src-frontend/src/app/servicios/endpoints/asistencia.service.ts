import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import {
  AsistenciaDeAlumno,
  ClaseAsistencia,
  ClasesDelGrupo,
  ListaDeClase,
  PrefijoAsistencia,
  ResumenAsistenciaAlumno,
} from '../../interfaces/asistencia';

/**
 * Pasar lista (decisión 324): las cinco rutas de asistencia de un grupo. Las dos familias de rutas
 * (`/profesor/grupos/{id}` y `/grupos/{id}`) tienen las mismas formas, así que el prefijo es un parámetro. Los errores
 * los presenta cada pantalla (traen `{mensaje}` en español).
 */
@Injectable({ providedIn: 'root' })
export class AsistenciaService {
  private readonly http = inject(HttpClient);

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  private base(prefijo: PrefijoAsistencia, grupoId: number): string {
    return prefijo === 'profesor'
      ? `${environment.apiUrl}/profesor/grupos/${grupoId}`
      : `${environment.apiUrl}/grupos/${grupoId}`;
  }

  clases(prefijo: PrefijoAsistencia, grupoId: number): Observable<ClasesDelGrupo> {
    return this.http.get<ClasesDelGrupo>(`${this.base(prefijo, grupoId)}/clases`, this.opciones()).pipe(
      map((c) => ({ hoy: c?.hoy ?? [], recientes: c?.recientes ?? [], proxima: c?.proxima ?? null }))
    );
  }

  lista(prefijo: PrefijoAsistencia, grupoId: number, turnoId: number, fecha: string): Observable<ListaDeClase> {
    return this.http
      .get<ListaDeClase>(`${this.base(prefijo, grupoId)}/clases/${turnoId}/${fecha}`, this.opciones())
      .pipe(map((l) => ({ ...l, alumnos: (l.alumnos ?? []).map((a) => ({ ...a, deportes: a.deportes ?? [] })) })));
  }

  guardar(
    prefijo: PrefijoAsistencia,
    grupoId: number,
    turnoId: number,
    fecha: string,
    presentes: number[]
  ): Observable<ClaseAsistencia> {
    return this.http.put<ClaseAsistencia>(
      `${this.base(prefijo, grupoId)}/clases/${turnoId}/${fecha}`,
      { presentes },
      this.opciones()
    );
  }

  resumen(prefijo: PrefijoAsistencia, grupoId: number, year: number, month: number): Observable<ResumenAsistenciaAlumno[]> {
    const params = new HttpParams().set('year', year).set('month', month);
    return this.http
      .get<ResumenAsistenciaAlumno[]>(`${this.base(prefijo, grupoId)}/asistencia/resumen`, { ...this.opciones(), params })
      .pipe(map((lista) => lista ?? []));
  }

  deAlumno(
    prefijo: PrefijoAsistencia,
    grupoId: number,
    alumnoId: number,
    year: number,
    month: number
  ): Observable<AsistenciaDeAlumno> {
    const params = new HttpParams().set('year', year).set('month', month);
    return this.http
      .get<AsistenciaDeAlumno>(`${this.base(prefijo, grupoId)}/alumnos/${alumnoId}/asistencia`, { ...this.opciones(), params })
      .pipe(map((a) => ({ ...a, dias: a?.dias ?? [] })));
  }
}
