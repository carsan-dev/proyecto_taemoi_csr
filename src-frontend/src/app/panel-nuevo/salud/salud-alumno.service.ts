import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import type { SaludAlumno } from '../../interfaces/profesor';

export type GuardarSalud = Pick<SaludAlumno, 'lesionEnfermedad' | 'descripcion' | 'impideActividad' | 'impideDetalle' | 'alergias' | 'tieneDiscapacidad' | 'discapacidadTipos' | 'discapacidadOtra' | 'origen'>;

/** Each call is a new audited request. Health data lives only in the open component. */
@Injectable({ providedIn: 'root' })
export class SaludAlumnoService {
  private readonly http = inject(HttpClient);
  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }
  obtener(id: number): Observable<SaludAlumno> {
    return this.http.get<SaludAlumno>(`${environment.apiUrl}/alumnos/${id}/salud`, this.opciones());
  }
  guardar(id: number, cuerpo: GuardarSalud): Observable<SaludAlumno> {
    return this.http.put<SaludAlumno>(`${environment.apiUrl}/alumnos/${id}/salud`, cuerpo, this.opciones());
  }
}
