import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

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

  crearAlumno(nuevoAlumno: any, token: string) {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.post<any>(`${this.urlBase}/alumnos`, nuevoAlumno, {
      headers: headers,
    });
  }

  actualizarAlumno(id: number, alumnoActualizado: any, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.put<any>(`${this.urlBase}/alumnos/${id}`, alumnoActualizado, {
      headers: headers,
    });
  }

  eliminarAlumnos(id: number, token: string): Observable<any> {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.delete<any>(`${this.urlBase}/alumnos/${id}`, {
      headers: headers,
    });
  }
}
