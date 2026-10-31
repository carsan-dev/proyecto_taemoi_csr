import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable, Subject, catchError, map, of, shareReplay, tap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

/** Lo que la sesión puede hacer en la escuela activa (D8-14). La autorización real sigue siendo del servidor. */
export type Capacidad =
  | 'MIEMBROS'
  | 'DAR_GESTION'
  | 'SEDES'
  | 'ASIGNAR_PROFESORES'
  | 'MIS_GRUPOS'
  | 'MI_CAJA'
  /** Responsable de sede sin gestión: la señal del menú y las pantallas de la decisión 370. */
  | 'PANEL_SEDE'
  /** Responsable de sede y familia sin gestión: elige vista como gestión + familia (decisión 371). */
  | 'ELEGIR_VISTA';

export interface SedeVisible {
  id: number;
  nombre: string;
  activa: boolean;
}

/** `GET /api/auth/permisos` (`PermisosSesionDTO`). */
export interface PermisosSesion {
  roles: string[];
  esAdminPlataforma: boolean;
  sedes: SedeVisible[];
  variasSedes: boolean;
  tieneGrupos: boolean;
  capacidades: Capacidad[];
  /** «Nombre A.» de la cuenta (8b-T), para el botón de cuenta del panel nuevo; null sin nombre. */
  nombreVisible?: string | null;
  /** Nombre para el saludo de Inicio: el preferido de «Mi perfil» o el primer nombre de la cuenta; null sin nombre. */
  nombreSaludo?: string | null;
  /** Hoy es el cumpleaños de la ficha propia de la cuenta (8b-M2); solo el booleano. Opcional: servidor anterior. */
  cumpleanos?: boolean;
}

/**
 * Quién pide los permisos al entrar: ROLE_PROFESOR (Mis grupos y Mi caja), la gestión (ROLE_ADMIN o ROLE_MANAGER) y el
 * responsable de sede. Una vez por sesión: la petición se comparte.
 */
export function pidePermisos(roles: readonly string[]): boolean {
  return (
    roles.includes('ROLE_PROFESOR') ||
    roles.includes('ROLE_ADMIN') ||
    roles.includes('ROLE_MANAGER') ||
    roles.includes('ROLE_RESPONSABLE_SEDE')
  );
}

/**
 * Permisos de la sesión para menús y guards (E8-5): se piden una vez y la petición se comparte. Lo refrescan el
 * login y el cierre de sesión de {@link AuthenticationService}, donde ya se reinician los roles.
 */
@Injectable({ providedIn: 'root' })
export class PermisosSesionService {
  private readonly url = `${environment.apiUrl}/auth/permisos`;
  private peticion$: Observable<PermisosSesion> | null = null;
  /** Las de la última respuesta vigente: {@link tiene} las lee sin suscribirse. */
  private capacidadesLeidas: readonly Capacidad[] = [];
  private readonly refrescos = new Subject<void>();
  /** Avisa cuando la sesión cambió algo que se muestra (p. ej. el nombre de «Mi perfil»): hay que volver a pedirlos. */
  readonly refrescado$: Observable<void> = this.refrescos.asObservable();

  constructor(private readonly http: HttpClient) {}

  /** Los permisos de la sesión; un error se propaga y no se guarda (la siguiente llamada vuelve a pedir). */
  obtener(): Observable<PermisosSesion> {
    if (!this.peticion$) {
      const peticion$: Observable<PermisosSesion> = this.http
        .get<PermisosSesion>(this.url, {
          withCredentials: true,
          headers: { 'X-Skip-Loading': 'true' },
          context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
        })
        .pipe(
          tap((permisos) => {
            if (this.peticion$ === peticion$) {
              this.capacidadesLeidas = permisos?.capacidades ?? [];
            }
          }),
          catchError((error) => {
            if (this.peticion$ === peticion$) {
              this.peticion$ = null;
            }
            return throwError(() => error);
          }),
          shareReplay(1)
        );
      this.peticion$ = peticion$;
    }
    return this.peticion$;
  }

  /** Las capacidades de la sesión; si no se pueden leer, ninguna (falla cerrado). */
  capacidades(): Observable<readonly Capacidad[]> {
    return this.obtener().pipe(
      map((permisos) => permisos.capacidades ?? []),
      catchError(() => of([] as readonly Capacidad[]))
    );
  }

  /**
   * Lectura síncrona para las pantallas: `false` hasta que llegan los permisos. `roleGuard` los espera antes de abrir
   * una pantalla a un responsable de sede, así que dentro de ella `tiene('PANEL_SEDE')` ya es definitivo.
   */
  tiene(capacidad: Capacidad): boolean {
    return this.capacidadesLeidas.includes(capacidad);
  }

  /** Descarta los permisos guardados: la próxima llamada los pide de nuevo (login, cierre de sesión). */
  reiniciar(): void {
    this.peticion$ = null;
    this.capacidadesLeidas = [];
  }

  /** Descarta los permisos y avisa a quien los muestra, sin recargar la página (guardar «Mi perfil»). */
  refrescar(): void {
    // Sigue siendo la misma sesión: `tiene()` conserva las capacidades mientras llega la respuesta nueva.
    this.peticion$ = null;
    this.refrescos.next();
  }
}
