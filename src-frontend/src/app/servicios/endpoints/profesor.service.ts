import { HttpClient, HttpContext, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import {
  AlumnoDeGrupo,
  DeporteDelAlumno,
  FichaAlumnoProfesor,
  GrupoProfesor,
  HojaAsistencia,
  PaseCreado,
  SaludAlumno,
  TipoPase,
} from '../../interfaces/profesor';

function numero(valor: unknown): number {
  const n = typeof valor === 'number' ? valor : Number(valor);
  return Number.isFinite(n) ? n : 0;
}

/** Nombre del fichero en `Content-Disposition` (`filename*=` antes que `filename=`), o el propuesto. */
export function nombreDeContentDisposition(cabecera: string | null, porDefecto: string): string {
  if (!cabecera) {
    return porDefecto;
  }
  const extendido = /filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i.exec(cabecera);
  if (extendido) {
    try {
      return decodeURIComponent(extendido[1].trim().replace(/^"|"$/g, ''));
    } catch {
      // cae al nombre simple
    }
  }
  const simple = /filename\s*=\s*"?([^";]+)"?/i.exec(cabecera);
  return simple?.[1]?.trim() || porDefecto;
}

/** El `{mensaje}` de un error cuya respuesta se pidió como blob (la hoja de asistencia). */
export async function mensajeDeErrorBlob(error: unknown): Promise<string | null> {
  if (!(error instanceof HttpErrorResponse) || !(error.error instanceof Blob)) {
    return null;
  }
  try {
    const cuerpo = JSON.parse(await error.error.text());
    const mensaje = cuerpo?.mensaje ?? cuerpo?.message;
    return typeof mensaje === 'string' && mensaje.trim() ? mensaje.trim() : null;
  } catch {
    return null;
  }
}

/**
 * Pantallas del profesor (`/api/profesor/**`, entrega 7): sus grupos, sus alumnos, la ficha reducida, la salud a
 * petición, la hoja de asistencia y los grados por apto, fecha y pases (decisión 207). Una sesión solo profesor puede
 * usar esta API y la de «Mi caja», nada más: nada de aquí toca el catálogo ni rutas de gestión. Los errores los
 * presenta la pantalla (`{mensaje}` en español).
 *
 * La salud no se guarda aquí: cada llamada a {@link salud} va al servidor (que la registra) y la pantalla la descarta
 * al plegarla o al salir.
 */
@Injectable({ providedIn: 'root' })
export class ProfesorService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/profesor`;

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  misGrupos(): Observable<GrupoProfesor[]> {
    return this.http
      .get<GrupoProfesor[]>(`${this.api}/grupos`, this.opciones())
      .pipe(map((lista) => (lista ?? []).map((g) => ({ ...g, alumnosActivos: numero(g.alumnosActivos) }))));
  }

  alumnosDelGrupo(grupoId: number): Observable<AlumnoDeGrupo[]> {
    return this.http
      .get<AlumnoDeGrupo[]>(`${this.api}/grupos/${grupoId}/alumnos`, this.opciones())
      .pipe(map((lista) => (lista ?? []).map((a) => ({ ...a, deportes: a.deportes ?? [] }))));
  }

  ficha(alumnoId: number): Observable<FichaAlumnoProfesor> {
    return this.http.get<FichaAlumnoProfesor>(`${this.api}/alumnos/${alumnoId}`, this.opciones()).pipe(
      map((f) => ({ ...f, grupos: f.grupos ?? [], contacto: f.contacto ?? [], deportes: f.deportes ?? [] }))
    );
  }

  /** Cada llamada queda registrada en el servidor. Sin caché (el servidor responde `no-store`). */
  salud(alumnoId: number): Observable<SaludAlumno> {
    return this.http.get<SaludAlumno>(`${this.api}/alumnos/${alumnoId}/salud`, this.opciones());
  }

  hojaAsistencia(grupoId: number, year: number, month: number): Observable<HojaAsistencia> {
    const params = new HttpParams().set('year', year).set('month', month);
    const porDefecto = `Asistencia-grupo-${grupoId}-${year}-${String(month).padStart(2, '0')}.pdf`;
    return this.http
      .get(`${this.api}/grupos/${grupoId}/asistencia`, {
        ...this.opciones(),
        params,
        responseType: 'blob',
        observe: 'response',
      })
      .pipe(
        map((respuesta) => ({
          blob: respuesta.body ?? new Blob([], { type: 'application/pdf' }),
          nombre: nombreDeContentDisposition(respuesta.headers.get('Content-Disposition'), porDefecto),
        }))
      );
  }

  actualizarApto(alumnoId: number, deporte: string, aptoParaExamen: boolean): Observable<DeporteDelAlumno> {
    return this.http.put<DeporteDelAlumno>(
      `${this.deporte(alumnoId, deporte)}/apto`,
      { aptoParaExamen },
      this.opciones()
    );
  }

  /** `fechaGrado` en `yyyy-MM-dd`. */
  actualizarFechaGrado(alumnoId: number, deporte: string, fechaGrado: string): Observable<DeporteDelAlumno> {
    return this.http.put<DeporteDelAlumno>(
      `${this.deporte(alumnoId, deporte)}/fecha-grado`,
      { fechaGrado },
      this.opciones()
    );
  }

  /** Crea el cargo pendiente del pase; el grado sube al cobrarlo en Mi caja. */
  pasarGrado(alumnoId: number, deporte: string, tipo: TipoPase, rojoBordado: boolean): Observable<PaseCreado> {
    const ruta = tipo === 'examen' ? 'pase-examen' : 'pase-recompensa';
    return this.http
      .post<PaseCreado>(`${this.deporte(alumnoId, deporte)}/${ruta}`, { rojoBordado }, this.opciones())
      .pipe(map((p) => ({ ...p, cargo: { ...p.cargo, importe: numero(p.cargo?.importe) } })));
  }

  private deporte(alumnoId: number, deporte: string): string {
    return `${this.api}/alumnos/${alumnoId}/deportes/${encodeURIComponent(deporte)}`;
  }
}
