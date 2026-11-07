import { HttpClient, HttpContext, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, Signal, WritableSignal, inject, signal } from '@angular/core';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { EstadoRecurso, Legal, SitioWeb, TemaWeb, esTemaWeb } from './modelos';

/**
 * Datos de la web por plantilla (fase4-web-plan.md §3.1 y §5): una petición por recurso, compartida por todas las
 * páginas (caché en memoria mientras dure la aplicación) y expuesta como signal con su estado.
 *
 * Vista previa (§3.3): con `?vista-previa=<tema>` en la URL se pide `vistaPrevia=true` (y `tema`, si es uno de los
 * cuatro). La API solo la atiende con sesión de gestión; sin ella devuelve lo publicado. Lo publicado y cada vista
 * previa van con claves distintas: nunca se mezclan. Un 404 es «no publicada» y se guarda; un error no se guarda (la
 * siguiente página vuelve a pedir). La cookie de sesión la añade el interceptor de credenciales de la aplicación.
 */
@Injectable({ providedIn: 'root' })
export class WebPublicaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/publico/web`;
  private readonly cache = new Map<string, WritableSignal<EstadoRecurso<unknown>>>();

  /**
   * `GET /api/publico/web`.
   *
   * @param vistaPrevia valor de `vista-previa` en la URL; null si no está.
   */
  sitio(vistaPrevia: string | null): Signal<EstadoRecurso<SitioWeb>> {
    return this.recurso<SitioWeb>('sitio', this.url, vistaPrevia, true);
  }

  /** `GET /api/publico/web/legal` (sin tema: Legales no depende de él). */
  legal(vistaPrevia: string | null): Signal<EstadoRecurso<Legal>> {
    return this.recurso<Legal>('legal', `${this.url}/legal`, vistaPrevia, false);
  }

  /** Si la URL pide la vista previa (`vista-previa` presente, aunque vacío). */
  esVistaPrevia(vistaPrevia: string | null): boolean {
    return vistaPrevia !== null;
  }

  /** El tema pedido en la vista previa, si es uno de los cuatro; null fuera de ella o con otro valor. */
  temaVistaPrevia(vistaPrevia: string | null): TemaWeb | null {
    return esTemaWeb(vistaPrevia) ? vistaPrevia : null;
  }

  private recurso<T>(
    nombre: string,
    url: string,
    vistaPrevia: string | null,
    conTema: boolean,
  ): Signal<EstadoRecurso<T>> {
    const previa = this.esVistaPrevia(vistaPrevia);
    const tema = conTema ? this.temaVistaPrevia(vistaPrevia) : null;
    const clave = previa ? `${nombre}|vista-previa|${tema ?? ''}` : `${nombre}|publicada`;

    let estado = this.cache.get(clave) as WritableSignal<EstadoRecurso<T>> | undefined;
    if (estado && estado().estado !== 'error') {
      return estado.asReadonly();
    }
    if (!estado) {
      estado = signal<EstadoRecurso<T>>({ estado: 'cargando' });
      this.cache.set(clave, estado as WritableSignal<EstadoRecurso<unknown>>);
    } else {
      estado.set({ estado: 'cargando' });
    }

    let params = new HttpParams();
    if (previa) {
      params = params.set('vistaPrevia', 'true');
      if (tema) {
        params = params.set('tema', tema);
      }
    }
    const destino = estado;
    this.http
      .get<T>(url, { params, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) })
      .subscribe({
        next: (datos) => destino.set({ estado: 'listo', datos }),
        error: (error: unknown) =>
          destino.set(
            error instanceof HttpErrorResponse && error.status === 404 ? { estado: 'no-publicada' } : { estado: 'error' },
          ),
      });
    return estado.asReadonly();
  }
}
