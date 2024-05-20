import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GrupoDTO } from '../../interfaces/grupo-dto';
import { AlumnoDTO } from '../../interfaces/alumno-dto';

@Injectable({
  providedIn: 'root',
})
export class EndpointsService {
  private urlBase = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  obtenerAlumnos(token: string, page: number, size: number): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<any>(`${this.urlBase}/alumnos`, {
      headers: headers,
      params: params,
    });
  }

  crearAlumno(alumnoData: any, imagen: File | null, token: string): Observable<any> {
    const formData = new FormData();
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
    formData.append('nuevo', JSON.stringify(alumnoData));
    if (imagen) {
      formData.append('file', imagen, imagen.name);
    }
    return this.http.post<any>(`${this.urlBase}/alumnos/crear`, formData, {
      headers: headers,
    });
  }

actualizarAlumno(id: number, formData: FormData, token: string): Observable<any> {
  const headers = new HttpHeaders({
    Authorization: `Bearer ${token}`,
  });

    return this.http.put<any>(`${this.urlBase}/alumnos/${id}`, formData, {
      headers: headers,
    });
  }

  eliminarImagenAlumno(id: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.delete(`${this.urlBase}/alumnos/${id}/imagen`, {
      headers: headers,
    });
}

  eliminarAlumnos(id: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.delete<any>(`${this.urlBase}/alumnos/${id}`, { headers });
  }

  obtenerTodosLosGrupos(token: string): Observable<GrupoDTO[]> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.get<GrupoDTO[]>(`${this.urlBase}/grupos`, { headers });
  }

  obtenerGrupoPorId(id: number, token: string): Observable<GrupoDTO> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.get<GrupoDTO>(`${this.urlBase}/grupos/${id}`, { headers });
  }


  crearGrupo(grupoData: any, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.post<any>(`${this.urlBase}/grupos`, grupoData, {
      headers: headers,
    });
  }

  actualizarGrupo(id: number, grupoData: GrupoDTO, token: string): Observable<GrupoDTO> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.put<GrupoDTO>(`${this.urlBase}/grupos/${id}`, grupoData, {
      headers: headers,
    });
  }

  eliminarGrupo(id: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.delete<any>(`${this.urlBase}/grupos/${id}`, {
      headers: headers,
    });
  }

  agregarAlumnoAGrupo(grupoId: number, alumnoId: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.post<any>(`${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}`, {}, { headers });
  }

  eliminarAlumnoDeGrupo(grupoId: number, alumnoId: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.delete<any>(`${this.urlBase}/grupos/${grupoId}/alumnos/${alumnoId}`, { headers });
  }

  obtenerTurnosDelGrupo(grupoId: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.get<any>(`${this.urlBase}/grupos/${grupoId}/turnos`, {
      headers: headers,
    });
  }

  agregarTurnoAGrupo(grupoId: number, turnoId: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.post<any>(`${this.urlBase}/grupos/${grupoId}/turnos/${turnoId}`, null, {
      headers: headers,
    });
  }

  eliminarTurnoDeGrupo(grupoId: number, turnoId: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.delete<any>(`${this.urlBase}/grupos/${grupoId}/turnos/${turnoId}`, {
      headers: headers,
    });
  }

  obtenerTurnos(token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.get<any>(`${this.urlBase}/turnos`, {
      headers: headers,
    });
  }

  obtenerTurnoPorId(turnoId: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.get<any>(`${this.urlBase}/turnos/${turnoId}`, {
      headers: headers,
    });
  }

  crearTurnoSinGrupo(turnoData: any, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.post<any>(`${this.urlBase}/turnos/crear`, turnoData, {
      headers: headers,
    });
  }

  crearTurnoConGrupo(turnoData: any, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.post<any>(`${this.urlBase}/turnos/crear-asignando-grupo`, turnoData, {
      headers: headers,
    });
  }

  actualizarTurno(turnoId: number, turnoData: any, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.put<any>(`${this.urlBase}/turnos/${turnoId}`, turnoData, {
      headers: headers,
    });
  }

  eliminarTurno(turnoId: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.delete<any>(`${this.urlBase}/turnos/${turnoId}`, {
      headers: headers,
    });
  }
}
