import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';

import { environment } from '../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../core/http-context';

/** Interruptor de una cuenta de gestión (`DisenoPanelDTO.Usuario`). */
export interface DisenoUsuario {
  usuarioId: number;
  permitido: boolean;
  activo: boolean;
}

/** `GET /api/panel/diseno-nuevo` (`DisenoPanelDTO`): solo ids y booleanos. */
export interface EstadoDisenoNuevo {
  porDefecto: boolean;
  usuarios: DisenoUsuario[];
}

/** Lo que usa la pantalla de una fila de `GET /api/miembros` (`MiembrosDTOs.Miembro`). */
export interface MiembroDeGestion {
  usuarioId: number;
  nombre: string | null;
  apellidos: string | null;
  roles: { rol: string; activa: boolean }[];
}

interface PaginaDeMiembros {
  contenido: MiembroDeGestion[];
}

/**
 * API del interruptor del diseño nuevo (8b-I, fase3-e8b-plan.md §3), en el chunk del panel nuevo: solo la pantalla del
 * propietario (y, desde 8b-N, el menú de cuenta nuevo) lo cargan. Errores tratados en la pantalla.
 */
@Injectable({ providedIn: 'root' })
export class DisenoPanelService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/panel`;

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  listar(): Observable<EstadoDisenoNuevo> {
    return this.http.get<EstadoDisenoNuevo>(`${this.api}/diseno-nuevo`, this.opciones());
  }

  /** El propietario da o quita el permiso; al darlo, queda activo. */
  cambiarPermiso(usuarioId: number, permitido: boolean): Observable<DisenoUsuario> {
    return this.http.put<DisenoUsuario>(`${this.api}/diseno-nuevo/${usuarioId}`, { permitido }, this.opciones());
  }

  /** «Para todo el equipo» (R5). */
  cambiarPorDefecto(nuevo: boolean): Observable<{ porDefecto: boolean }> {
    return this.http.put<{ porDefecto: boolean }>(`${this.api}/diseno-nuevo/por-defecto`, { nuevo }, this.opciones());
  }

  /** La propia cuenta lo enciende o apaga para sí. */
  cambiarMiDiseno(activo: boolean): Observable<{ activo: boolean; permitido: boolean }> {
    return this.http.put<{ activo: boolean; permitido: boolean }>(`${this.api}/mi-diseno`, { activo }, this.opciones());
  }

  /** Propietarios y gestores de la escuela (para los nombres), sin familias: dos peticiones a la vez. */
  gestionDeLaEscuela(): Observable<MiembroDeGestion[]> {
    const pedir = (rol: string) =>
      this.http.get<PaginaDeMiembros>(`${environment.apiUrl}/miembros`, {
        ...this.opciones(),
        params: new HttpParams().set('rol', rol).set('size', '100'),
      });
    return forkJoin([pedir('PROPIETARIO'), pedir('ADMINISTRADOR')]).pipe(
      map(([propietarios, gestores]) => [...(propietarios.contenido ?? []), ...(gestores.contenido ?? [])])
    );
  }
}
