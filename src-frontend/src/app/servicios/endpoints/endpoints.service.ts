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

actualizarAlumno(id: number, formData: FormData, token: String): Observable<any> {
  const headers = new HttpHeaders({
    Authorization: `Bearer ${token}`,
  });

    return this.http.put<any>(`${this.urlBase}/alumnos/${id}`, formData, {
      headers: headers,
    });
  }

  eliminarImagenAlumno(id: number, token: String): Observable<any> {
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

    return this.http.delete<any>(`${this.urlBase}/alumnos/${id}`, {
      headers: headers,
    });
  }
}
