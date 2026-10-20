import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { ProfesorDeGrupo } from '../../interfaces/grupo-profesor';
import { RolAsignacionProfesor } from '../../interfaces/profesor';

/** Profesores de un grupo (gestión): listar, asignar o cambiar de rol y quitar. Errores tratados en la pantalla. */
@Injectable({ providedIn: 'root' })
export class GrupoProfesoresService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/grupos`;

  private opciones() {
    return {
      withCredentials: true,
      context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
    };
  }

  listar(grupoId: number): Observable<ProfesorDeGrupo[]> {
    return this.http.get<ProfesorDeGrupo[]>(`${this.api}/${grupoId}/profesores`, this.opciones());
  }

  /** Alta, o cambio de rol si la cuenta ya está en el grupo. */
  asignar(grupoId: number, email: string, rol: RolAsignacionProfesor): Observable<ProfesorDeGrupo> {
    return this.http.post<ProfesorDeGrupo>(`${this.api}/${grupoId}/profesores`, { email, rol }, this.opciones());
  }

  quitar(grupoId: number, usuarioId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/${grupoId}/profesores/${usuarioId}`, this.opciones());
  }
}
