import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { GrupoDTO } from '../../interfaces/grupo-dto';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class EndpointsService {
  private urlBase = environment.apiUrl;

  constructor(private http: HttpClient) {}

  private crearHeaders(token: string): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
  }

  private manejarError(error: any) {
    console.error('Ocurrió un error', error);
    return throwError(() => error);
  }

  obtenerAlumnos(
    token: string,
    page: number,
    size: number,
    nombre: string
  ): Observable<any> {
    const headers = this.crearHeaders(token);
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (nombre) {
      params = params.set('nombre', nombre);
    }

    return this.http
      .get<any>(`${this.urlBase}/alumnos`, {
        headers: headers,
        params: params,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerGruposDelAlumno(alumnoId: number, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .get<any>(`${this.urlBase}/alumnos/${alumnoId}/grupos`, { headers })
      .pipe(catchError(this.manejarError));
  }

  crearAlumno(
    alumnoData: any,
    imagen: File | null,
    token: string
  ): Observable<any> {
    const formData = new FormData();
    const headers = this.crearHeaders(token);
    formData.append('nuevo', JSON.stringify(alumnoData));
    if (imagen) {
      formData.append('file', imagen, imagen.name);
    }
    return this.http
      .post<any>(`${this.urlBase}/alumnos/crear`, formData, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarAlumno(
    id: number,
    formData: FormData,
    token: string
  ): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .put<any>(`${this.urlBase}/alumnos/${id}`, formData, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarImagenAlumno(id: number, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .delete(`${this.urlBase}/alumnos/${id}/imagen`, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarAlumnos(id: number, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .delete<any>(`${this.urlBase}/alumnos/${id}`, { headers })
      .pipe(catchError(this.manejarError));
  }

  obtenerTodosLosGrupos(token: string): Observable<GrupoDTO[]> {
    const headers = this.crearHeaders(token);
    return this.http
      .get<GrupoDTO[]>(`${this.urlBase}/grupos`, { headers })
      .pipe(catchError(this.manejarError));
  }

  obtenerGrupoPorId(id: number, token: string): Observable<GrupoDTO> {
    const headers = this.crearHeaders(token);
    return this.http
      .get<GrupoDTO>(`${this.urlBase}/grupos/${id}`, { headers })
      .pipe(catchError(this.manejarError));
  }

  crearGrupo(grupoData: any, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .post<any>(`${this.urlBase}/grupos`, grupoData, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarGrupo(
    id: number,
    grupoData: GrupoDTO,
    token: string
  ): Observable<GrupoDTO> {
    const headers = this.crearHeaders(token);
    return this.http
      .put<GrupoDTO>(`${this.urlBase}/grupos/${id}`, grupoData, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarGrupo(id: number, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${id}`, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  agregarAlumnoAGrupo(
    grupoId: number,
    alumnoId: number,
    token: string
  ): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .post<any>(
        `${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}`,
        {},
        { headers }
      )
      .pipe(catchError(this.manejarError));
  }

  agregarAlumnosAGrupo(
    grupoId: number,
    alumnosIds: number[],
    token: string
  ): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .post<any>(`${this.urlBase}/grupos/${grupoId}/alumnos`, alumnosIds, {
        headers,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarAlumnoDeGrupo(
    grupoId: number,
    alumnoId: number,
    token: string
  ): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}`, {
        headers,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnosDelGrupo(grupoId: number, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .get<any>(`${this.urlBase}/grupos/${grupoId}/turnos`, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  agregarTurnoAGrupo(
    grupoId: number,
    turnoId: number,
    token: string
  ): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .post<any>(`${this.urlBase}/grupos/${grupoId}/turnos/${turnoId}`, null, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarTurnoDeGrupo(
    grupoId: number,
    turnoId: number,
    token: string
  ): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${grupoId}/turnos/${turnoId}`, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnos(token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .get<any>(`${this.urlBase}/turnos`, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnosDTO(): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/turnos/dto`)
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnoPorId(turnoId: number, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .get<any>(`${this.urlBase}/turnos/${turnoId}`, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  crearTurnoSinGrupo(turnoData: any, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .post<any>(`${this.urlBase}/turnos/crear`, turnoData, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  crearTurnoConGrupo(turnoData: any, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .post<any>(`${this.urlBase}/turnos/crear-asignando-grupo`, turnoData, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarTurno(
    turnoId: number,
    turnoData: any,
    token: string
  ): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .put<any>(`${this.urlBase}/turnos/${turnoId}`, turnoData, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarTurno(turnoId: number, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .delete<any>(`${this.urlBase}/turnos/${turnoId}`, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerEventos(): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/eventos`)
      .pipe(catchError(this.manejarError));
  }

  obtenerEventoPorId(eventoId: number, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .get<any>(`${this.urlBase}/eventos/${eventoId}`, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  crearEvento(
    eventoData: any,
    imagen: File | null,
    token: string
  ): Observable<any> {
    const formData = new FormData();
    const headers = this.crearHeaders(token);
    formData.append('nuevo', JSON.stringify(eventoData));
    if (imagen) {
      formData.append('file', imagen, imagen.name);
    }
    return this.http
      .post<any>(`${this.urlBase}/eventos/crear`, formData, { headers })
      .pipe(catchError(this.manejarError));
  }

  actualizarEvento(
    id: number,
    formData: FormData,
    token: string
  ): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .put<any>(`${this.urlBase}/eventos/${id}`, formData, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarImagenEvento(id: number, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .delete(`${this.urlBase}/eventos/${id}/imagen`, {
        headers: headers,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarEvento(id: number, token: string): Observable<any> {
    const headers = this.crearHeaders(token);
    return this.http
      .delete<any>(`${this.urlBase}/eventos/${id}`, { headers })
      .pipe(catchError(this.manejarError));
  }
}
