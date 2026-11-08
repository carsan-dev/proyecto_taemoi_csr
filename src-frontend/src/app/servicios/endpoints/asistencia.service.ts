import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import {
  AsistenciaDeAlumno,
  ClaseAsistencia,
  ClasesDelGrupo,
  FestivoDeClase,
  ListaDeClase,
  MotivoFalta,
  PrefijoAsistencia,
  ResumenAsistenciaAlumno,
} from '../../interfaces/asistencia';

/** El festivo de una clase con los campos que un API anterior no mandaba: fechas en null y una sede. */
function festivoDeClase(f: FestivoDeClase | null | undefined): FestivoDeClase | null {
  return f ? { ...f, fechaDesde: f.fechaDesde ?? null, fechaHasta: f.fechaHasta ?? null, sedes: f.sedes ?? 1 } : null;
}

const conFestivo = (c: ClaseAsistencia): ClaseAsistencia =>
  ({ ...c, festivo: festivoDeClase(c.festivo), festivoConLista: festivoDeClase(c.festivoConLista) });

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
      map((c) => ({ hoy: (c?.hoy ?? []).map(conFestivo), recientes: (c?.recientes ?? []).map(conFestivo), proxima: c?.proxima ?? null }))
    );
  }

  lista(prefijo: PrefijoAsistencia, grupoId: number, turnoId: number, fecha: string): Observable<ListaDeClase> {
    return this.http
      .get<ListaDeClase>(`${this.base(prefijo, grupoId)}/clases/${turnoId}/${fecha}`, this.opciones())
      .pipe(
        map((l) => ({
          ...l,
          clase: { ...l.clase, festivo: festivoDeClase(l.clase?.festivo), festivoConLista: festivoDeClase(l.clase?.festivoConLista) },
          sede: l.sede ?? null,
          alumnos: (l.alumnos ?? []).map((a) => ({ ...a, deportes: a.deportes ?? [], motivo: a.motivo ?? null })),
        }))
      );
  }

  guardar(
    prefijo: PrefijoAsistencia,
    grupoId: number,
    turnoId: number,
    fecha: string,
    presentes: number[],
    motivos: Readonly<Record<number, MotivoFalta>> = {}
  ): Observable<ClaseAsistencia> {
    // Los motivos solo de las faltas (decisión 455), siempre, aunque vayan vacíos: un PUT sin `motivos` deja todas las
    // faltas sin motivo.
    const cuerpo = { presentes, motivos };
    return this.http.put<ClaseAsistencia>(
      `${this.base(prefijo, grupoId)}/clases/${turnoId}/${fecha}`,
      cuerpo,
      this.opciones()
    );
  }

  /**
   * Borra la lista guardada de una clase festiva (decisiones 495-496). Solo gestión (ADMIN, MANAGER o responsable de la
   * sede del grupo), así que va siempre por `/grupos/{id}`, también desde «Mis grupos». 204; 404 sin clase y 409 si ese
   * día no es festivo.
   */
  borrarLista(grupoId: number, turnoId: number, fecha: string): Observable<void> {
    return this.http.delete<void>(`${this.base('gestion', grupoId)}/clases/${turnoId}/${fecha}/lista`, this.opciones());
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
