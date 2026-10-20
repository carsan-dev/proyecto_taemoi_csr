import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { RolAsignacionProfesor } from '../../interfaces/profesor';

/** Un grupo y el rol en él (`MiembrosRequests.Asignacion`). */
export interface AsignacionDeAlta {
  grupoId: number;
  rol: RolAsignacionProfesor;
}

/**
 * Cuerpo de `POST /api/miembros` (D8-6) para el alta de un profesor desde la ficha del grupo (D8-12). `nombre` y
 * `apellidos` solo para invitar un email sin cuenta (E8-4); `reactivar` para un rol de profesor desactivado.
 */
export interface AltaDeProfesor {
  email: string;
  rol: 'PROFESOR';
  grupos: AsignacionDeAlta[];
  nombre?: string;
  apellidos?: string;
  reactivar?: boolean;
}

/** Lo que usa la pantalla de la ficha que devuelve la API (`MiembrosDTOs.Ficha`). */
export interface FichaDeMiembro {
  usuarioId: number;
  invitacionPendiente: boolean;
}

/** API de miembros (E8-3, E8-4) en lo que usa «Profesores» de la ficha del grupo. Errores tratados en la pantalla. */
@Injectable({ providedIn: 'root' })
export class MiembrosService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/miembros`;

  private opciones() {
    return {
      withCredentials: true,
      context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
    };
  }

  /** Alta todo o nada: asegura la membresía PROFESOR y añade las asignaciones (o invita, con nombre y apellidos). */
  alta(cuerpo: AltaDeProfesor): Observable<FichaDeMiembro> {
    return this.http.post<FichaDeMiembro>(this.api, cuerpo, this.opciones());
  }

  /** Reenvía la invitación pendiente (token nuevo). Sin cuerpo. */
  reenviarInvitacion(usuarioId: number): Observable<FichaDeMiembro> {
    return this.http.post<FichaDeMiembro>(`${this.api}/${usuarioId}/invitacion`, null, this.opciones());
  }
}
