import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { GrupoDTO } from '../../interfaces/grupo-dto';
import { environment } from '../../../environments/environment';
import { Turno } from '../../interfaces/turno';

@Injectable({
  providedIn: 'root',
})
export class EndpointsService {
  private urlBase = environment.apiUrl;

  constructor(private http: HttpClient) {}

  private manejarError(error: any) {
    console.error('Ocurrió un error', error);
    return throwError(() => error);
  }

  obtenerAlumnos(
    page: number,
    size: number,
    nombre: string,
    incluirInactivos: boolean
  ): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (nombre) {
      params = params.set('nombre', nombre);
    }

    params = params.set('incluirInactivos', incluirInactivos.toString());

    return this.http
      .get<any>(`${this.urlBase}/alumnos`, {
        params: params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerAlumnoPorId(alumnoId: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/alumnos/${alumnoId}`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerGruposDelAlumno(alumnoId: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/alumnos/${alumnoId}/grupos`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  asignarAlumnoATurno(alumnoId: number, turnoId: number): Observable<any> {
    return this.http
      .post<any>(
        `${this.urlBase}/alumnos/${alumnoId}/turnos/${turnoId}`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  removerAlumnoDeTurno(alumnoId: number, turnoId: number): Observable<Turno[]> {
    return this.http
      .delete<Turno[]>(`${this.urlBase}/alumnos/${alumnoId}/turnos/${turnoId}`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnosDelAlumno(alumnoId: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/alumnos/${alumnoId}/turnos`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  crearAlumno(
    alumnoData: any,
    imagen: File | null
  ): Observable<any> {
    const formData = new FormData();
    formData.append('nuevo', JSON.stringify(alumnoData));
    if (imagen) {
      formData.append('file', imagen, imagen.name);
    }
    return this.http
      .post<any>(`${this.urlBase}/alumnos/crear`, formData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarAlumno(
    id: number,
    formData: FormData
  ): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/alumnos/${id}`, formData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarImagenAlumno(id: number): Observable<any> {
    return this.http
      .delete(`${this.urlBase}/alumnos/${id}/imagen`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarAlumnos(id: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/alumnos/${id}`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  darDeBajaAlumno(alumnoId: number): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/alumnos/${alumnoId}/baja`, {}, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  darDeAltaAlumno(alumnoId: number): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/alumnos/${alumnoId}/alta`, {}, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerGrados(): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/grados`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerGradosPorFechaNacimiento(fechaNacimiento: string): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/grados/disponibles/${fechaNacimiento}`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerTodosLosGrupos(): Observable<GrupoDTO[]> {
    return this.http
      .get<GrupoDTO[]>(`${this.urlBase}/grupos`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerGrupoPorId(id: number): Observable<GrupoDTO> {
    return this.http
      .get<GrupoDTO>(`${this.urlBase}/grupos/${id}`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerConteoAlumnosPorGrupo(): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/grupos/conteo-alumnos`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  crearGrupo(grupoData: any): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/grupos`, grupoData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarGrupo(
    id: number,
    grupoData: GrupoDTO
  ): Observable<GrupoDTO> {
    return this.http
      .put<GrupoDTO>(`${this.urlBase}/grupos/${id}`, grupoData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarGrupo(id: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  agregarAlumnoAGrupo(
    grupoId: number,
    alumnoId: number
  ): Observable<any> {
    return this.http
      .post<any>(
        `${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  agregarAlumnosAGrupo(
    grupoId: number,
    alumnosIds: number[]
  ): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/grupos/${grupoId}/alumnos`, alumnosIds, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarAlumnoDeGrupo(
    grupoId: number,
    alumnoId: number
  ): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnosDelGrupo(grupoId: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/grupos/${grupoId}/turnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  agregarTurnoAGrupo(
    grupoId: number,
    turnoId: number
  ): Observable<any> {
    return this.http
      .post<any>(
        `${this.urlBase}/grupos/${grupoId}/turnos/${turnoId}`,
        null,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  eliminarTurnoDeGrupo(
    grupoId: number,
    turnoId: number
  ): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${grupoId}/turnos/${turnoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnos(): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/turnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnosDTO(): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/turnos/dto`)
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnoPorId(turnoId: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/turnos/${turnoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  crearTurnoSinGrupo(turnoData: any): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/turnos/crear`, turnoData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  crearTurnoConGrupo(turnoData: any): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/turnos/crear-asignando-grupo`, turnoData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarTurno(
    turnoId: number,
    turnoData: any
  ): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/turnos/${turnoId}`, turnoData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarTurno(turnoId: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/turnos/${turnoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerEventos(): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/eventos`)
      .pipe(catchError(this.manejarError));
  }

  obtenerEventoPorId(eventoId: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/eventos/${eventoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  crearEvento(
    eventoData: any,
    imagen: File | null
  ): Observable<any> {
    const formData = new FormData();
    formData.append('nuevo', JSON.stringify(eventoData));
    if (imagen) {
      formData.append('file', imagen, imagen.name);
    }
    return this.http
      .post<any>(`${this.urlBase}/eventos/crear`, formData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarEvento(
    id: number,
    formData: FormData
  ): Observable<any> {
    return this.http
      .put<any>(`${this.urlBase}/eventos/${id}`, formData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarImagenEvento(id: number): Observable<any> {
    return this.http
      .delete(`${this.urlBase}/eventos/${id}/imagen`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarEvento(id: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/eventos/${id}`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }
}
