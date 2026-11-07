import { DOCUMENT } from '@angular/common';
import { HttpClient, HttpContext } from '@angular/common/http';
import { DestroyRef, Injectable, InjectionToken, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, catchError, distinctUntilChanged, map, of, shareReplay, skip, take, tap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { sesionCerrada } from '../../servicios/authentication/sesion-cerrada';
import { acentoDe, contraste } from '../acento-escuela';
import { etiquetaRol } from '../formato/roles';
import { LOGO_LEGADO } from '../marca-escuela-panel.service';

/** Una escuela de la cuenta (`GET /api/auth/organizaciones`, `OrganizacionUsuarioDTO`). */
export interface EscuelaUsuario {
  id: number;
  slug: string;
  nombre: string;
  /** Autoridades en esa escuela (`ROLE_*`), ordenadas por nombre. */
  roles: string[];
  /** La escuela de la sesión: la del login, que es la última en la que entró. */
  activa: boolean;
  logo: 'SUBIDO' | 'LEGADO' | 'NINGUNO';
  logoVersion: number | null;
  /** `#RRGGBB`; null sin color guardado. */
  colorPrimario: string | null;
  numeroSedes: number;
}

/** Pantalla «Tras entrar» (boceto `boceto-selector-escuela.html`, lugar=tras). */
export const RUTA_ELEGIR_ESCUELA = '/elegir-escuela';

/**
 * Entrada sin pantalla que resuelve el destino de la sesión con la regla del login con contraseña (decisión 384,
 * `entradaGoogleGuard`). Tras cambiar de escuela, la recarga completa va aquí: los roles de la escuela nueva deciden si
 * el destino es el panel, «Mis grupos», «Mi caja» o el área de familias.
 */
export const RUTA_ENTRADA_SESION = '/login/google';

/** Carga completa de una URL de la app (tras cambiar de escuela); en los tests se sustituye. */
export const IR_A_URL = new InjectionToken<(url: string) => void>('IR_A_URL', {
  providedIn: 'root',
  factory: () => {
    const documento = inject(DOCUMENT);
    return (url: string) => documento.defaultView?.location.assign(url);
  },
});

/** Orden del texto de los roles (boceto): de más a menos poder sobre la escuela. */
const ORDEN_ROLES = ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_RESPONSABLE_SEDE', 'ROLE_PROFESOR', 'ROLE_USER'];
const PALABRAS_MENORES = /^(de|del|la|las|los|el|y)$/i;
const BLANCO = '#FFFFFF';
const TINTA = '#1A2230';
const COLOR_POR_DEFECTO = '#1A2230';
const HEX = /^#[0-9a-f]{6}$/i;

/**
 * «Tras entrar» solo con dos escuelas o más y si en alguna la cuenta no es solo familia: el área de familias no cambia
 * con el panel (una cuenta que solo es familia sigue entrando como siempre).
 */
export function debeElegirTrasEntrar(escuelas: readonly EscuelaUsuario[]): boolean {
  return escuelas.length >= 2 && escuelas.some((e) => e.roles.some((rol) => rol !== 'ROLE_USER'));
}

/** «Tras entrar»: primero la de «La última vez» (la activa) y luego las demás en el orden de la API. */
export function ordenTrasEntrar(escuelas: readonly EscuelaUsuario[]): EscuelaUsuario[] {
  return [...escuelas.filter((e) => e.activa), ...escuelas.filter((e) => !e.activa)];
}

/** «Administración», «Gestión y familia», «Gestión, profesorado y familia» (nombres de la decisión 262). */
export function textoRoles(roles: readonly string[]): string {
  const textos = [...roles]
    .filter((rol) => ORDEN_ROLES.includes(rol))
    .sort((a, b) => ORDEN_ROLES.indexOf(a) - ORDEN_ROLES.indexOf(b))
    .map((rol, i) => (i === 0 ? etiquetaRol(rol) : etiquetaRol(rol).toLowerCase()));
  if (textos.length < 2) {
    return textos[0] ?? '';
  }
  return `${textos.slice(0, -1).join(', ')} y ${textos[textos.length - 1]}`;
}

/** Rol y, con más de una sede, «N sedes»: «Gestión · 6 sedes». */
export function metaEscuela(escuela: EscuelaUsuario): string {
  const roles = textoRoles(escuela.roles);
  return escuela.numeroSedes > 1 ? `${roles} · ${escuela.numeroSedes} sedes` : roles;
}

/** «Taekwondo Sierra Sur» → «TS»; «Club de Prueba» → «CP» (sin artículos ni preposiciones). */
export function inicialesEscuela(nombre: string | null | undefined): string {
  const palabras = (nombre ?? '')
    .split(/\s+/)
    .map((p) => p.replace(/[^\p{L}\p{N}]/gu, ''))
    .filter((p) => p && !PALABRAS_MENORES.test(p));
  return palabras
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');
}

/** URL del logo de una escuela de la cuenta (aunque no sea la activa); null sin logo (se pintan las iniciales). */
export function urlLogoEscuela(escuela: EscuelaUsuario): string | null {
  switch (escuela.logo) {
    case 'SUBIDO':
      return escuela.logoVersion === null
        ? null
        : `${environment.apiUrl}/auth/organizaciones/${escuela.id}/logo?v=${escuela.logoVersion}`;
    case 'LEGADO':
      return LOGO_LEGADO;
    default:
      return null;
  }
}

/**
 * Fondo y texto de las iniciales sobre el color de la escuela, siempre a 4,5:1 (The Contraste Automático Rule): blanco
 * si llega; si no, la tinta; si ninguno llega, el color oscurecido (el acento de la escuela) o, en un gris sin tono, el
 * color por defecto, con texto blanco.
 */
export function coloresIniciales(colorPrimario: string | null): { fondo: string; texto: string } {
  const fondo = colorPrimario && HEX.test(colorPrimario) ? colorPrimario.toUpperCase() : COLOR_POR_DEFECTO;
  if (contraste(BLANCO, fondo) >= 4.5) {
    return { fondo, texto: BLANCO };
  }
  if (contraste(TINTA, fondo) >= 4.5) {
    return { fondo, texto: TINTA };
  }
  // Un gris sin tono no se oscurece con el acento (se queda igual): entonces, el color por defecto.
  const oscuro = acentoDe({ primario: fondo, primarioTinta: fondo, primarioSuave: fondo }).acento;
  return { fondo: contraste(BLANCO, oscuro) >= 4.5 ? oscuro : COLOR_POR_DEFECTO, texto: BLANCO };
}

/**
 * Selector de escuela (fase 4; boceto aprobado `docs/multi-escuela/boceto-selector-escuela.html`). Vive en el chunk del
 * panel nuevo: el login lo carga en diferido.
 *
 * - `organizaciones()`: una petición por carga de la app, compartida por el login, «Tras entrar» y la cabecera. Un error
 *   no se guarda; cerrar sesión o entrar con otra cuenta la descarta.
 * - `cambiar(id)`: `POST /api/auth/organizacion`. Si va bien, NO se intenta limpiar caché a caché: se vacía el
 *   `sessionStorage` (filtros, páginas, alumno de familia y saludo de la escuela anterior, como al cerrar sesión) y se
 *   recarga la app entera en {@link RUTA_ENTRADA_SESION}. Así ningún servicio, `shareReplay`, signal ni tema de la
 *   escuela anterior sobrevive. Si falla, nada cambia y el error llega a quien llamó. Una segunda llamada mientras hay
 *   un cambio en vuelo no hace nada.
 */
@Injectable({ providedIn: 'root' })
export class SelectorEscuelaService {
  private readonly http = inject(HttpClient);
  private readonly documento = inject(DOCUMENT);
  private readonly irA = inject(IR_A_URL);
  private readonly urlBase = `${environment.apiUrl}/auth`;
  private peticion$: Observable<EscuelaUsuario[]> | null = null;
  private cambiando = false;

  constructor() {
    const destroyRef = inject(DestroyRef);
    inject(AuthenticationService)
      .emailCambio.pipe(distinctUntilChanged(), skip(1), takeUntilDestroyed(destroyRef))
      .subscribe(() => (this.peticion$ = null));
    sesionCerrada.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => (this.peticion$ = null));
  }

  /** Escuelas de la cuenta en el orden de la API; un error se propaga y no se guarda. */
  organizaciones(): Observable<EscuelaUsuario[]> {
    if (!this.peticion$) {
      const peticion$: Observable<EscuelaUsuario[]> = this.http
        .get<EscuelaUsuario[]>(`${this.urlBase}/organizaciones`, {
          withCredentials: true,
          context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
        })
        .pipe(
          map((lista) => (Array.isArray(lista) ? lista : [])),
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

  /**
   * Destino tras el login con contraseña: «Tras entrar» si toca ({@link debeElegirTrasEntrar}); si no, o si no se
   * pueden leer las escuelas, el destino de siempre.
   */
  destinoTrasEntrar(rutaInicio: string): Observable<string> {
    return this.organizaciones().pipe(
      take(1),
      map((escuelas) => (debeElegirTrasEntrar(escuelas) ? RUTA_ELEGIR_ESCUELA : rutaInicio)),
      catchError(() => of(rutaInicio))
    );
  }

  /** true mientras hay un cambio de escuela en vuelo (o ya hecho y recargando). */
  get ocupado(): boolean {
    return this.cambiando;
  }

  /**
   * Cambia la escuela de la sesión y recarga la app. Con otro cambio en vuelo devuelve `false` sin pedir nada. Si el POST
   * falla, la sesión sigue en la escuela de antes y el error se propaga.
   */
  cambiar(organizacionId: number): Observable<boolean> {
    if (this.cambiando) {
      return of(false);
    }
    this.cambiando = true;
    return this.http
      .post(
        `${this.urlBase}/organizacion`,
        { organizacionId },
        { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) }
      )
      .pipe(
        map(() => true),
        tap(() => {
          // Sigue «ocupado»: la página se va a recargar y no debe salir otro cambio entretanto.
          this.vaciarSesionDelNavegador();
          this.irA(RUTA_ENTRADA_SESION);
        }),
        catchError((error) => {
          this.cambiando = false;
          return throwError(() => error);
        })
      );
  }

  private vaciarSesionDelNavegador(): void {
    try {
      this.documento.defaultView?.sessionStorage.clear();
    } catch {
      // Sin sessionStorage utilizable no hay nada que vaciar.
    }
  }
}
