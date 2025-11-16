import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

/**
 * Service for generating reports (Informes)
 * Handles PDF generation for various reports: grades, licenses, attendance, etc.
 */
@Injectable({
  providedIn: 'root',
})
export class InformeService {
  private readonly urlBase = `${environment.apiUrl}/informes`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Generate report of all students by grade
   */
  generarInformeAlumnosPorGrado(): Observable<Blob> {
    return this.http.get(`${this.urlBase}/alumnosPorGrado`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  /**
   * Generate report of Taekwondo students by grade
   */
  generarInformeTaekwondoPorGrado(): Observable<Blob> {
    return this.http.get(`${this.urlBase}/taekwondoPorGrado`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  /**
   * Generate report of Kickboxing students by grade
   */
  generarInformeKickboxingPorGrado(): Observable<Blob> {
    return this.http.get(`${this.urlBase}/kickboxingPorGrado`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  /**
   * Generate report of student licenses
   */
  generarInformeLicencias(): Observable<Blob> {
    return this.http.get(`${this.urlBase}/licencias`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  /**
   * Generate report of minor students eligible for promotion
   */
  generarInformeInfantilesAPromocionar(): Observable<Blob> {
    return this.http.get(`${this.urlBase}/infantilesAPromocionar`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  /**
   * Generate report of adult students eligible for promotion
   */
  generarInformeAdultosAPromocionar(): Observable<Blob> {
    return this.http.get(`${this.urlBase}/adultosAPromocionar`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  /**
   * Generate report of all student monthly fees (mensualidades)
   */
  generarInformeMensualidades(): Observable<Blob> {
    return this.http.get(`${this.urlBase}/mensualidades`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  /**
   * Generate report of Taekwondo student monthly fees
   */
  generarInformeMensualidadesTaekwondo(): Observable<Blob> {
    return this.http.get(`${this.urlBase}/mensualidades/taekwondo`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  /**
   * Generate report of Kickboxing student monthly fees
   */
  generarInformeMensualidadesKickboxing(): Observable<Blob> {
    return this.http.get(`${this.urlBase}/mensualidades/kickboxing`, {
      withCredentials: true,
      responseType: 'blob',
    });
  }

  /**
   * Download attendance sheet for a specific group and schedule
   */
  descargarAsistencia(
    year: number,
    month: number,
    grupo: string,
    turno: string
  ): Observable<Blob> {
    const params = new HttpParams()
      .set('year', year.toString())
      .set('month', month.toString())
      .set('grupo', grupo)
      .set('turno', turno);
    return this.http.get(`${this.urlBase}/asistencia`, {
      params,
      responseType: 'blob',
    });
  }
}
