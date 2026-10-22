import { HttpClient, HttpContext } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, catchError, distinctUntilChanged, shareReplay, skip, tap, throwError } from 'rxjs';

import { environment } from '../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../core/http-context';
import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { AcentoEscuela, acentoDe } from './acento-escuela';

/** Colores del panel de la escuela (`TemaEscuela` del servidor, `#RRGGBB`). */
export interface TemaMarca {
  primario: string;
  sobrePrimario: string;
  sobrePrimario2: string;
  primarioTinta: string;
  primarioSuave: string;
  /** El primario se oscureció para que el texto blanco llegue a 4,5:1. */
  ajustado: boolean;
}

/** `GET /api/escuela/marca` (`MarcaPanelDTO`): la marca de la escuela activa. */
export interface MarcaPanel {
  nombre: string;
  logo: 'SUBIDO' | 'LEGADO' | 'NINGUNO';
  /** Id de la imagen del logo subido (va en su URL para no servir uno viejo de la caché); null sin él. */
  logoVersion: number | null;
  tema: TemaMarca;
}

/** Logo de siempre de Moiskimdo (`LEGADO`), el mismo que la cabecera de hoy. */
export const LOGO_LEGADO = 'assets/media/Logo_escuela.svg';

/** Variable de `panel-nuevo.scss` (bajo `.pn`) que sustituye cada color del tema. */
const VARIABLES: Record<keyof Omit<TemaMarca, 'ajustado'>, string> = {
  primario: '--pn-marca-primario',
  sobrePrimario: '--pn-marca-sobre-primario',
  sobrePrimario2: '--pn-marca-sobre-primario-2',
  primarioTinta: '--pn-marca-primario-tinta',
  primarioSuave: '--pn-marca-primario-suave',
};

/** Variables del acento (8b-C, decisión 273), derivadas del primario con `acentoDe`. */
const VARIABLES_ACENTO: Record<keyof AcentoEscuela, string> = {
  acento: '--pn-acento',
  acentoSuave: '--pn-acento-suave',
  acentoBorde: '--pn-acento-borde',
};

/**
 * Marca de la escuela en el panel nuevo (pieza 8b-T, fase3-e8b-plan.md §4). Vive en el chunk de `panel-nuevo`: con el
 * interruptor apagado nadie la carga ni la pide.
 *
 * - Una petición por sesión: la respuesta se comparte; un error no se guarda (la siguiente llamada vuelve a pedir) y
 *   cambiar de cuenta (cierre de sesión o entrada de otra) la descarta.
 * - Los colores se escriben como variables `--pn-marca-*` en el elemento `.pn` del panel nuevo, NUNCA en `:root`, en
 *   `documentElement` ni en `body`: el resto de la aplicación (web pública, familias, pantallas de hoy) no los ve.
 * - Con la marca se calcula el acento (8b-C) y se escribe en el mismo `.pn`; el resto de pantallas y capas lo toman de
 *   `acento` con la directiva `pnAcento`.
 */
@Injectable({ providedIn: 'root' })
export class MarcaEscuelaPanelService {
  private readonly http = inject(HttpClient);
  private readonly documento = inject(DOCUMENT);
  private readonly url = `${environment.apiUrl}/escuela/marca`;
  private peticion$: Observable<MarcaPanel> | null = null;
  private readonly acentoActual = signal<AcentoEscuela | null>(null);

  /** El acento de la escuela de la última marca aplicada; `null` antes de aplicarla o tras cambiar de sesión. */
  readonly acento = this.acentoActual.asReadonly();

  constructor() {
    inject(AuthenticationService)
      .emailCambio.pipe(distinctUntilChanged(), skip(1), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => {
        this.peticion$ = null;
        this.acentoActual.set(null);
      });
  }

  /** La marca de la escuela activa; un error se propaga y no se guarda. */
  marca(): Observable<MarcaPanel> {
    if (!this.peticion$) {
      const peticion$: Observable<MarcaPanel> = this.http
        .get<MarcaPanel>(this.url, {
          withCredentials: true,
          context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true),
        })
        .pipe(
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

  /** Escribe el tema en el elemento `.pn`. Lanza si el elemento no es un `.pn` (o es `documentElement` o `body`). */
  aplicarEn(elemento: HTMLElement, tema: TemaMarca): void {
    this.comprobarPn(elemento);
    for (const [clave, variable] of Object.entries(VARIABLES) as [keyof typeof VARIABLES, string][]) {
      elemento.style.setProperty(variable, tema[clave]);
    }
    const acento = acentoDe(tema);
    this.escribirAcento(elemento, acento);
    this.acentoActual.set(acento);
  }

  /** Escribe el acento en el `.pn` (o lo quita con `null`, y queda el de `panel-nuevo.scss`). Lanza como `aplicarEn`. */
  escribirAcento(elemento: HTMLElement, acento: AcentoEscuela | null): void {
    this.comprobarPn(elemento);
    for (const [clave, variable] of Object.entries(VARIABLES_ACENTO) as [keyof AcentoEscuela, string][]) {
      if (acento) {
        elemento.style.setProperty(variable, acento[clave]);
      } else {
        elemento.style.removeProperty(variable);
      }
    }
  }

  private comprobarPn(elemento: HTMLElement): void {
    if (
      elemento === this.documento.documentElement ||
      elemento === this.documento.body ||
      !elemento.classList.contains('pn')
    ) {
      throw new Error('La marca solo se escribe en el elemento .pn del panel nuevo');
    }
  }

  /**
   * Pide la marca (una vez por sesión) y la aplica al `.pn`. Si no se puede leer, el panel se queda con los colores
   * por defecto de `panel-nuevo.scss` y el observable termina sin error.
   */
  pintar(elemento: HTMLElement): Observable<MarcaPanel> {
    return this.marca().pipe(
      tap((marca) => this.aplicarEn(elemento, marca.tema)),
      catchError(() => EMPTY)
    );
  }

  /** URL del logo para la cabecera: el subido (con su versión), el SVG de siempre o ninguno. */
  urlLogo(marca: MarcaPanel): string | null {
    switch (marca.logo) {
      case 'SUBIDO':
        return `${environment.apiUrl}/escuela/logo?v=${marca.logoVersion}`;
      case 'LEGADO':
        return LOGO_LEGADO;
      default:
        return null;
    }
  }
}
