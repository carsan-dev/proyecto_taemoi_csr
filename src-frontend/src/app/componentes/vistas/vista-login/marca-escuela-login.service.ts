import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, shareReplay, throwError } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';

/** Colores de la escuela que usa el login (parte de `TemaEscuela`; `#RRGGBB`, texto ≥ 4,5:1 sobre el primario). */
export interface TemaEscuelaLogin {
  primario: string;
  sobrePrimario: string;
}

/** `GET /api/publico/escuela` (`EscuelaPublicaDTO`): la marca pública de la escuela del Host. */
export interface EscuelaPublica {
  nombre: string;
  logo: 'SUBIDO' | 'LEGADO' | 'NINGUNO';
  /** Id de la imagen del logo subido (va en su URL para no servir uno viejo de la caché); null sin él. */
  logoVersion: number | null;
  tema: TemaEscuelaLogin | null;
}

/** Lo que pinta la zona de marca del login. */
export type MarcaLogin =
  | { tipo: 'pendiente' }
  | { tipo: 'legado' }
  | { tipo: 'subido'; nombre: string; url: string; tema: TemaEscuelaLogin | null }
  | { tipo: 'ninguno'; nombre: string; iniciales: string; tema: TemaEscuelaLogin | null };

export const MARCA_LEGADO: MarcaLogin = { tipo: 'legado' };

/**
 * Marca de la escuela del Host para el login (fase 4, pieza 5 de `fase4-dominio-plan.md` §4.3). Vive en el chunk
 * perezoso del login. Una petición por carga de la aplicación: la respuesta se comparte; un error no se guarda.
 */
@Injectable({ providedIn: 'root' })
export class MarcaEscuelaLoginService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/publico/escuela`;
  private peticion$: Observable<EscuelaPublica> | null = null;

  /** La escuela del Host; un error se propaga y no se guarda (la siguiente llamada vuelve a pedir). */
  escuela(): Observable<EscuelaPublica> {
    if (!this.peticion$) {
      const peticion$: Observable<EscuelaPublica> = this.http
        .get<EscuelaPublica>(this.url, { context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) })
        .pipe(
          catchError((error) => {
            if (this.peticion$ === peticion$) {
              this.peticion$ = null;
            }
            return throwError(() => error);
          }),
          shareReplay({ bufferSize: 1, refCount: true })
        );
      this.peticion$ = peticion$;
    }
    return this.peticion$;
  }

  /** Qué pinta el login con la respuesta: `LEGADO` o cualquier respuesta rara, el SVG de siempre. */
  marcaDe(escuela: EscuelaPublica | null | undefined): MarcaLogin {
    const nombre = escuela?.nombre?.trim() ?? '';
    const tema = escuela?.tema ?? null;
    if (escuela?.logo === 'SUBIDO' && escuela.logoVersion != null) {
      return { tipo: 'subido', nombre, url: `${this.url}/logo?v=${escuela.logoVersion}`, tema };
    }
    if (escuela?.logo === 'NINGUNO' && nombre) {
      return { tipo: 'ninguno', nombre, iniciales: inicialesEscuela(nombre), tema };
    }
    return MARCA_LEGADO;
  }
}

/**
 * «Taekwondo Sierra Sur» → «TSS»; «Club de Taekwondo Estepa» → «CTE». Hasta tres iniciales; si hay palabras en
 * mayúscula, solo ellas (se saltan «de», «la», «y»…).
 */
export function inicialesEscuela(nombre: string): string {
  const palabras = nombre
    .split(/\s+/)
    .map((p) => p.replace(/[^\p{L}\p{N}]/gu, ''))
    .filter(Boolean);
  const principales = palabras.filter((p) => p[0] !== p[0].toLowerCase());
  return (principales.length ? principales : palabras)
    .slice(0, 3)
    .map((p) => p[0].toUpperCase())
    .join('');
}
