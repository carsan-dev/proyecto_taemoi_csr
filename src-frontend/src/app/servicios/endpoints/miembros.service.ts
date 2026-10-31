import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
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

/** Los roles de membresía que expone la API (`RolMembresia`). «Gestor» en pantalla es ADMINISTRADOR. */
export type RolMembresia = 'PROPIETARIO' | 'ADMINISTRADOR' | 'RESPONSABLE_SEDE' | 'PROFESOR' | 'FAMILIA';

/** Un rol de la cuenta en la escuela, activo o no (`MiembrosDTOs.Rol`). */
export interface RolDeMiembro {
  rol: RolMembresia;
  activa: boolean;
  cargo: string | null;
}

export interface SedeDeMiembro {
  id: number;
  nombre: string;
}

/** Un grupo asignado a la cuenta como profesor (`MiembrosDTOs.Grupo`). */
export interface GrupoDeMiembro {
  grupoId: number;
  nombre: string;
  sedeId: number | null;
  rol: RolAsignacionProfesor;
}

/** Fila de la lista (`MiembrosDTOs.Miembro`). */
export interface Miembro {
  usuarioId: number;
  nombre: string;
  apellidos: string;
  email: string;
  authProvider?: string;
  invitacionPendiente: boolean;
  roles: RolDeMiembro[];
  sedes: SedeDeMiembro[];
  numeroGrupos: number;
}

/** Cómo está la contraseña de la cuenta (`MiembrosDTOs.Ficha.contrasena`): solo la trae la ficha. */
export type EstadoContrasenaCuenta = 'RESTABLECIBLE' | 'GOOGLE' | 'OTRA_ESCUELA' | 'INVITACION_PENDIENTE';

/** Ficha completa (`MiembrosDTOs.Ficha`): la fila más sus grupos y el estado de la contraseña. */
export interface FichaCompleta extends Miembro {
  grupos: GrupoDeMiembro[];
  contrasena?: EstadoContrasenaCuenta;
}

/** Una página de la lista (`MiembrosDTOs.Pagina`); `pagina` desde 0. */
export interface PaginaDeMiembros {
  contenido: Miembro[];
  pagina: number;
  tamano: number;
  total: number;
  totalPaginas: number;
}

export interface FiltroMiembros {
  q: string;
  rol: RolMembresia | '';
  incluirFamilias: boolean;
  /** Desde 0. */
  pagina: number;
  tamano: number;
}

/** Cuerpo de `POST /api/miembros` completo (`MiembrosRequests.Alta`). */
export interface AltaDeMiembro {
  email: string;
  rol: RolMembresia;
  cargo?: string;
  sedeIds?: number[];
  grupos?: AsignacionDeAlta[];
  nombre?: string;
  apellidos?: string;
  reactivar?: boolean;
}

/** Respuesta de cancelar una invitación: si la cuenta dejó de existir. */
export interface InvitacionCancelada {
  cuentaBorrada: boolean;
}

/** API de miembros (E8-3, E8-4): la pantalla Miembros (E9-4) y «Profesores» de la ficha del grupo. Errores tratados en la pantalla. */
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

  /** Una página del personal (con `incluirFamilias`, también las familias). La búsqueda va en la query de la API, no en la URL de la pantalla. */
  listar(filtro: FiltroMiembros): Observable<PaginaDeMiembros> {
    let params = new HttpParams().set('page', filtro.pagina).set('size', filtro.tamano);
    if (filtro.q.trim() !== '') {
      params = params.set('q', filtro.q.trim());
    }
    if (filtro.rol) {
      params = params.set('rol', filtro.rol);
    }
    if (filtro.incluirFamilias) {
      params = params.set('incluirFamilias', true);
    }
    return this.http.get<PaginaDeMiembros>(this.api, { ...this.opciones(), params });
  }

  ficha(usuarioId: number): Observable<FichaCompleta> {
    return this.http.get<FichaCompleta>(`${this.api}/${usuarioId}`, this.opciones());
  }

  /** Alta de una cuenta existente con un rol; con `nombre` y `apellidos` y sin cuenta, la invitación. */
  altaDeMiembro(cuerpo: AltaDeMiembro): Observable<FichaCompleta> {
    return this.http.post<FichaCompleta>(this.api, cuerpo, this.opciones());
  }

  /** Da o reactiva el rol; `cargo` ausente lo conserva y en blanco lo borra. `sedeIds` solo para el responsable. */
  darRol(usuarioId: number, rol: RolMembresia, cuerpo: { cargo?: string; sedeIds?: number[] } = {}): Observable<FichaCompleta> {
    return this.http.put<FichaCompleta>(`${this.api}/${usuarioId}/roles/${rol}`, cuerpo, this.opciones());
  }

  /** Como `reenviarInvitacion`, con la ficha completa que devuelve la API (pantalla Miembros). */
  reenviarConFicha(usuarioId: number): Observable<FichaCompleta> {
    return this.http.post<FichaCompleta>(`${this.api}/${usuarioId}/invitacion`, null, this.opciones());
  }

  /** Cancela la invitación pendiente (decisión 389): el enlace deja de valer y la cuenta se borra si no tiene nada más. */
  cancelarInvitacion(usuarioId: number): Observable<InvitacionCancelada> {
    return this.http.delete<InvitacionCancelada>(`${this.api}/${usuarioId}/invitacion`, this.opciones());
  }

  /** Da el rol de familia (solo el propietario): sin cuerpo, sin cargo ni sedes. */
  darFamilia(usuarioId: number): Observable<FichaCompleta> {
    return this.http.put<FichaCompleta>(`${this.api}/${usuarioId}/roles/FAMILIA`, null, this.opciones());
  }

  /** Restablece la contraseña de la cuenta (solo el propietario). La contraseña no se registra en ninguna parte. */
  restablecerContrasena(usuarioId: number, nuevaContrasena: string): Observable<FichaCompleta> {
    return this.http.put<FichaCompleta>(`${this.api}/${usuarioId}/contrasena`, { nuevaContrasena }, this.opciones());
  }

  /** Desactiva el rol (nunca borra la fila). */
  quitarRol(usuarioId: number, rol: RolMembresia): Observable<FichaCompleta> {
    return this.http.delete<FichaCompleta>(`${this.api}/${usuarioId}/roles/${rol}`, this.opciones());
  }

  /** Reemplaza las sedes del responsable. */
  reemplazarSedes(usuarioId: number, sedeIds: number[]): Observable<FichaCompleta> {
    return this.http.put<FichaCompleta>(`${this.api}/${usuarioId}/sedes`, { sedeIds }, this.opciones());
  }

  /** Reemplaza el conjunto de grupos del profesor. */
  reemplazarGrupos(usuarioId: number, asignaciones: AsignacionDeAlta[]): Observable<FichaCompleta> {
    return this.http.put<FichaCompleta>(`${this.api}/${usuarioId}/grupos`, { asignaciones }, this.opciones());
  }
}
