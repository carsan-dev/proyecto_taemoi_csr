import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { GrupoDTO } from '../../interfaces/grupo-dto';
import { environment } from '../../../environments/environment';
import { Turno } from '../../interfaces/turno';
import { BehaviorSubject } from 'rxjs/internal/BehaviorSubject';
import { throwError } from 'rxjs/internal/observable/throwError';
import { Observable } from 'rxjs/internal/Observable';
import { catchError } from 'rxjs/internal/operators/catchError';
import { tap } from 'rxjs/internal/operators/tap';

@Injectable({
  providedIn: 'root',
})
export class EndpointsService {
  private readonly urlBase = environment.apiUrl;

  constructor(private readonly http: HttpClient) {}

  private readonly gruposDelAlumnoSubject = new BehaviorSubject<any[]>([]);
  public gruposDelAlumno$ = this.gruposDelAlumnoSubject.asObservable();

  private readonly turnosDelAlumnoSubject = new BehaviorSubject<any[]>([]);
  public turnosDelAlumno$ = this.turnosDelAlumnoSubject.asObservable();

  private readonly conteoAlumnosPorGrupoSubject = new BehaviorSubject<any>({});
  public conteoAlumnosPorGrupo$ =
    this.conteoAlumnosPorGrupoSubject.asObservable();
  public conteoAlumnosPorGrupo: any = {};

  private readonly turnosDelGrupoSubject = new BehaviorSubject<any[]>([]);
  public turnosDelGrupo$ = this.turnosDelGrupoSubject.asObservable();

  private readonly turnosSubject = new BehaviorSubject<Turno[]>([]);
  public turnos$ = this.turnosSubject.asObservable();

  private readonly eventosSubject = new BehaviorSubject<any[]>([]);
  public eventos$ = this.eventosSubject.asObservable();

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
      .get<any>(`${this.urlBase}/alumnos/${alumnoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerGruposDelAlumno(alumnoId: number): void {
    this.http
      .get<any>(`${this.urlBase}/alumnos/${alumnoId}/grupos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (grupos) => {
          this.gruposDelAlumnoSubject.next(grupos);
        },
        error: (error) => {
          console.error('Error al obtener los grupos del alumno:', error);
        },
      });
  }

  obtenerTurnosDelAlumnoEnGrupo(grupoId: number, alumnoId: number): void {
    this.http
      .get<any[]>(
        `${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}/turnos`,
        {
          withCredentials: true,
        }
      )
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (turnos) => {
          this.turnosDelGrupoSubject.next(turnos); // Actualiza el BehaviorSubject con los turnos filtrados
        },
        error: (error) => {
          console.error(
            'Error al obtener los turnos del alumno en el grupo:',
            error
          );
        },
      });
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
      .delete<Turno[]>(
        `${this.urlBase}/alumnos/${alumnoId}/turnos/${turnoId}`,
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnosDelAlumno(alumnoId: number): void {
    this.http
      .get<any[]>(`${this.urlBase}/alumnos/${alumnoId}/turnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (turnos) => {
          this.turnosDelAlumnoSubject.next(turnos);
        },
        error: (error) => {
          console.error('Error al obtener los turnos del alumno:', error);
        },
      });
  }

  crearAlumno(alumnoData: any, imagen: File | null): Observable<any> {
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

  actualizarAlumno(id: number, formData: FormData): Observable<any> {
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
      .put<any>(
        `${this.urlBase}/alumnos/${alumnoId}/baja`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  darDeAltaAlumno(alumnoId: number): Observable<any> {
    return this.http
      .put<any>(
        `${this.urlBase}/alumnos/${alumnoId}/alta`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  // Obtener todos los alumnos aptos para examen (genérico)
  obtenerAlumnosAptosParaExamen(): Observable<any[]> {
    return this.http
      .get<any[]>(`${this.urlBase}/alumnos/aptos`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  // Obtener alumnos aptos para examen por deporte
  obtenerAlumnosAptosPorDeporte(deporte: string): Observable<any[]> {
    const params = new HttpParams().set('deporte', deporte);
    return this.http
      .get<any[]>(`${this.urlBase}/alumnos/aptos/deporte`, {
        params,
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  // Obtener un alumno apto para examen por su ID
  obtenerAlumnoAptoPorId(id: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/alumnos/aptos/${id}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerGrados(): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/grados`, { withCredentials: true })
      .pipe(catchError(this.manejarError));
  }

  obtenerGradosPorFechaNacimiento(fechaNacimiento: string): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/grados/disponibles/${fechaNacimiento}`, {
        withCredentials: true,
      })
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

  obtenerConteoAlumnosPorGrupo(): void {
    this.http
      .get<any>(`${this.urlBase}/grupos/conteo-alumnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (conteo) => {
          this.conteoAlumnosPorGrupoSubject.next(conteo);
          this.conteoAlumnosPorGrupo = conteo; // Actualizar la propiedad si es necesario
        },
        error: (error) => {
          console.error(
            'Error al obtener el conteo de alumnos por grupo:',
            error
          );
        },
      });
  }

  crearGrupo(grupoData: any): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/grupos`, grupoData, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  actualizarGrupo(id: number, grupoData: GrupoDTO): Observable<GrupoDTO> {
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

  agregarAlumnoAGrupo(grupoId: number, alumnoId: number): Observable<any> {
    return this.http
      .post<any>(
        `${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}`,
        {},
        { withCredentials: true }
      )
      .pipe(catchError(this.manejarError));
  }

  agregarAlumnosAGrupo(grupoId: number, alumnosIds: number[]): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/grupos/${grupoId}/alumnos`, alumnosIds, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarAlumnoDeGrupo(grupoId: number, alumnoId: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnosDelGrupo(grupoId: number): void {
    this.http
      .get<any>(`${this.urlBase}/grupos/${grupoId}/turnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (turnos) => {
          console.log('Turnos recibidos:', turnos);
          this.turnosDelGrupoSubject.next(turnos);
        },
        error: (error) => {
          console.error('Error al obtener los turnos del grupo:', error);
        },
      });
  }

  agregarTurnoAGrupo(grupoId: number, turnoId: number): Observable<any> {
    return this.http
      .post<any>(`${this.urlBase}/grupos/${grupoId}/turnos/${turnoId}`, null, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  eliminarTurnoDeGrupo(grupoId: number, turnoId: number): Observable<any> {
    return this.http
      .delete<any>(`${this.urlBase}/grupos/${grupoId}/turnos/${turnoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  obtenerTurnos(): void {
    this.http
      .get<any>(`${this.urlBase}/turnos`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (turnos) => {
          this.turnosSubject.next(turnos);
        },
        error: (error) => {
          console.error('Error al obtener los turnos del alumno:', error);
        },
      });
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

  actualizarTurno(turnoId: number, turnoData: any): Observable<any> {
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

  obtenerEventos(): void {
    this.http
      .get<any[]>(`${this.urlBase}/eventos`)
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: (eventos) => {
          this.eventosSubject.next(eventos);
        },
        error: (error) => {
          console.error('Error al obtener los eventos:', error);
        },
      });
  }

  obtenerEventoPorId(eventoId: number): Observable<any> {
    return this.http
      .get<any>(`${this.urlBase}/eventos/${eventoId}`, {
        withCredentials: true,
      })
      .pipe(catchError(this.manejarError));
  }

  crearEvento(eventoData: any, imagen: File | null): Observable<any> {
    const formData = new FormData();
    formData.append('nuevo', JSON.stringify(eventoData));
    if (imagen) {
      formData.append('file', imagen, imagen.name);
    }
    return this.http
      .post<any>(`${this.urlBase}/eventos/crear`, formData, {
        withCredentials: true,
      })
      .pipe(
        tap((nuevoEvento) => {
          const eventosActuales = this.eventosSubject.getValue();
          this.eventosSubject.next([...eventosActuales, nuevoEvento]);
        }),
        catchError(this.manejarError)
      );
  }

  actualizarEvento(id: number, formData: FormData): Observable<any> {
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

  eliminarEvento(id: number): void {
    this.http
      .delete<any>(`${this.urlBase}/eventos/${id}`, { withCredentials: true })
      .pipe(catchError(this.manejarError))
      .subscribe({
        next: () => {
          const eventosActuales = this.eventosSubject.getValue();
          const eventosActualizados = eventosActuales.filter(
            (evento) => evento.id !== id
          );
          this.eventosSubject.next(eventosActualizados);
        },
        error: (error) => {
          console.error('Error al eliminar el evento:', error);
        },
      });
  }
}
