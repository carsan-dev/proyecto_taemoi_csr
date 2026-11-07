import { DOCUMENT } from '@angular/common';
import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, InjectionToken, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import {
  Observable,
  catchError,
  defer,
  distinctUntilChanged,
  from,
  map,
  of,
  shareReplay,
  skip,
  switchMap,
  take,
  tap,
  throwError,
  timeout,
} from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CambioEscuelaPestanasService,
  IR_A_URL,
  PASA_BLOQUEO_CAMBIO_ESCUELA,
  RUTA_ENTRADA_SESION,
} from '../../core/cambio-escuela-pestanas';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { sesionCerrada } from '../../servicios/authentication/sesion-cerrada';
import { acentoDe, contraste } from '../acento-escuela';
import { etiquetaRol } from '../formato/roles';
import { LOGO_LEGADO } from '../marca-escuela-panel.service';
import { limpiarAlmacenesDeEscuela } from './limpieza-escuela';

export { IR_A_URL, RUTA_ENTRADA_SESION } from '../../core/cambio-escuela-pestanas';

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
 * Ruta neutra a la que se sale ANTES de pedir el cambio (sin cambiar la URL del navegador): salir de la pantalla
 * actual pide las confirmaciones de cambios sin guardar (`canDeactivate`) y destruye sus componentes, y con ellos sus
 * `beforeunload`. Pinta «Entrando en …» hasta la recarga.
 */
export const RUTA_CAMBIANDO_ESCUELA = '/cambiando-escuela';

/**
 * Tiempo máximo del `POST` del cambio. Sin respuesta en este tiempo, el resultado es incierto (la API pudo hacerlo):
 * no se vuelve a la pantalla de antes, se recarga la app entera.
 */
export const ESPERA_MAXIMA_CAMBIO_ESCUELA_MS = 15_000;

/** {@link ESPERA_MAXIMA_CAMBIO_ESCUELA_MS}; en los tests se acorta. */
export const ESPERA_CAMBIO_ESCUELA = new InjectionToken<number>('ESPERA_CAMBIO_ESCUELA', {
  providedIn: 'root',
  factory: () => ESPERA_MAXIMA_CAMBIO_ESCUELA_MS,
});

/**
 * Rechazo confirmado: solo un 4xx (la API respondió que no y la sesión sigue en la escuela de antes). Un 5xx, status 0,
 * timeout u otro error es incierto: la API pudo hacerlo.
 */
function esRechazoConfirmado(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500;
}

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
 * - `cambiar(escuela)`, en este orden y sin tocar nada hasta que la salida está confirmada:
 *   1. sale de la pantalla actual a {@link RUTA_CAMBIANDO_ESCUELA} con el router, que pregunta por los cambios sin
 *      guardar; si la persona se queda (o otra navegación la sustituye), no se pide nada y todo sigue igual;
 *   2. anuncia el cambio (`CambioEscuelaPestanasService.anunciarCambio`): bloquea las peticiones de esta pestaña y de
 *      las demás ANTES del `POST`, para que ninguna escriba con la memoria de una escuela y la cookie de otra;
 *   3. `POST /api/auth/organizacion` (la única petición que pasa el bloqueo), con {@link ESPERA_MAXIMA_CAMBIO_ESCUELA_MS};
 *   4. si va bien, NO se intenta limpiar caché a caché: se confirma a las demás pestañas (bloqueo definitivo), se vacía
 *      el `sessionStorage` y lo de la escuela del `localStorage` (`limpieza-escuela.ts`) y se recarga la app entera en
 *      {@link RUTA_ENTRADA_SESION}. Así ningún servicio, `shareReplay`, signal ni tema de la escuela anterior sobrevive.
 *      Los componentes de la pantalla anterior ya no existen: ningún `beforeunload` frena la recarga;
 *   5. si la API lo rechaza (solo respuesta 4xx), nada cambia: se cancela el anuncio (todas las pestañas se
 *      desbloquean), vuelve a la pantalla de antes, guarda la escuela para {@link tomarFallo} («Tras entrar» se ha
 *      vuelto a crear) y el error llega a quien llamó;
 *   6. si no se sabe (5xx, status 0, tiempo agotado u otro error), NO se vuelve a la escuela de antes: bloqueo
 *      definitivo, aviso a las demás pestañas (capa sin nombre, ya), limpieza y recarga completa (la sesión dirá en qué
 *      escuela está); la ruta neutra deja salir.
 *   Una segunda llamada mientras hay un cambio en vuelo no hace nada.
 */
@Injectable({ providedIn: 'root' })
export class SelectorEscuelaService {
  private readonly http = inject(HttpClient);
  private readonly documento = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly pestanas = inject(CambioEscuelaPestanasService);
  private readonly irA = inject(IR_A_URL);
  private readonly esperaMaxima = inject(ESPERA_CAMBIO_ESCUELA);
  private readonly urlBase = `${environment.apiUrl}/auth`;
  private peticion$: Observable<EscuelaUsuario[]> | null = null;
  private cambiando = false;
  /** Resultado incierto, recargando: la ruta neutra ya no retiene (nadie se queda atrapado si la recarga no llega). */
  private salidaLibre = false;
  private fallo: number | null = null;
  private readonly escuelaEntrando = signal<EscuelaUsuario | null>(null);

  /** La escuela en la que se está entrando (la pinta «Entrando en …» en la ruta neutra); null sin cambio en vuelo. */
  readonly entrando = this.escuelaEntrando.asReadonly();

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

  /** La ruta neutra no deja salir mientras el cambio está en vuelo o hecho; sí tras un error o un resultado incierto. */
  get retieneLaRuta(): boolean {
    return this.cambiando && !this.salidaLibre;
  }

  /** El id de la escuela cuyo último cambio falló; leerlo lo olvida. */
  tomarFallo(): number | null {
    const fallo = this.fallo;
    this.fallo = null;
    return fallo;
  }

  /**
   * Cambia la escuela de la sesión y recarga la app. Emite `false` sin pedir nada si hay otro cambio en vuelo o si no se
   * pudo salir de la pantalla actual (la persona se quedó para guardar). Si el POST falla, la sesión sigue en la
   * escuela de antes, se vuelve a la pantalla anterior y el error se propaga.
   */
  cambiar(escuela: EscuelaUsuario): Observable<boolean> {
    if (this.cambiando) {
      return of(false);
    }
    this.cambiando = true;
    this.fallo = null;
    const urlAnterior = this.router.url;
    this.escuelaEntrando.set(escuela);
    return from(this.router.navigateByUrl(RUTA_CAMBIANDO_ESCUELA, { skipLocationChange: true })).pipe(
      catchError(() => of(false)),
      switchMap((salio) => {
        if (salio !== true) {
          this.terminarSinCambio();
          return of(false);
        }
        return this.pedirCambio(escuela, urlAnterior);
      })
    );
  }

  private pedirCambio(escuela: EscuelaUsuario, urlAnterior: string): Observable<boolean> {
    return defer(() => {
      // Antes del POST: desde aquí ninguna pestaña escribe (salvo esta petición) hasta que el cambio se cierre.
      const idCambio = this.pestanas.anunciarCambio({ id: escuela.id, nombre: escuela.nombre });
      return this.http
        .post(
          `${this.urlBase}/organizacion`,
          { organizacionId: escuela.id },
          {
            withCredentials: true,
            context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true).set(PASA_BLOQUEO_CAMBIO_ESCUELA, true),
          }
        )
        .pipe(
          timeout({ first: this.esperaMaxima }),
          map(() => true),
          tap(() => {
            // Sigue «ocupado» y con «Entrando en …»: la página se va a recargar y no debe salir nada más entretanto.
            this.pestanas.confirmarCambio(idCambio);
            this.recargarLimpia();
          }),
          catchError((error) => {
            if (!esRechazoConfirmado(error)) {
              // Incierto: la API pudo cambiarla. Volver a la pantalla de antes sería seguir con la escuela anterior.
              this.pestanas.bloquear(idCambio);
              this.salidaLibre = true;
              this.recargarLimpia();
              return of(true);
            }
            this.pestanas.cancelarCambio(idCambio);
            this.terminarSinCambio();
            this.fallo = escuela.id;
            return from(this.router.navigateByUrl(urlAnterior, { replaceUrl: true })).pipe(
              catchError(() => of(false)),
              switchMap(() => throwError(() => error))
            );
          })
        );
    });
  }

  /** Vacía lo de la escuela del navegador y recarga la app entera en la entrada de la sesión. */
  private recargarLimpia(): void {
    limpiarAlmacenesDeEscuela(this.documento.defaultView);
    this.irA(RUTA_ENTRADA_SESION);
  }

  private terminarSinCambio(): void {
    this.cambiando = false;
    this.escuelaEntrando.set(null);
  }
}
