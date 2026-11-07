import { HttpClient, HttpContext, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, Signal, WritableSignal, inject, signal } from '@angular/core';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { EstadoRecurso, EventoDetalleWeb, EventosWeb, Legal, SitioWeb, TemaWeb, esTemaWeb } from './modelos';

/**
 * Datos de la web por plantilla (fase4-web-plan.md §3.1 y §5): una petición por recurso, compartida por todas las
 * páginas (caché en memoria mientras dure la aplicación) y expuesta como signal con su estado.
 *
 * Vista previa (§3.3): con `?vista-previa=<tema>` en la URL se pide `vistaPrevia=true` (y `tema`, si es uno de los
 * cuatro). La API solo la atiende con sesión de gestión; sin ella devuelve lo publicado. Lo publicado y cada vista
 * previa van con claves distintas: nunca se mezclan. Un 404 es «no publicada» (el de un evento, «no encontrado») y se
 * guarda; un error no se guarda (la siguiente página vuelve a pedir). La cookie de sesión la añade el interceptor de
 * credenciales de la aplicación.
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

  /** `GET /api/publico/web/eventos` (sin tema). Un 404 es la web sin publicar. */
  eventos(vistaPrevia: string | null): Signal<EstadoRecurso<EventosWeb>> {
    return this.recurso<EventosWeb>('eventos', `${this.url}/eventos`, vistaPrevia, false);
  }

  /**
   * `GET /api/publico/web/eventos/{id}` (sin tema). Un 404 es `no-encontrado`: el evento no se ve (oculto, de otra
   * escuela, sin fecha o inexistente) o la web no está publicada; lo segundo lo dice {@link sitio}.
   */
  evento(id: number, vistaPrevia: string | null): Signal<EstadoRecurso<EventoDetalleWeb>> {
    return this.recurso<EventoDetalleWeb>(`evento:${id}`, `${this.url}/eventos/${id}`, vistaPrevia, false, 'no-encontrado');
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
    en404: 'no-publicada' | 'no-encontrado' = 'no-publicada',
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
            error instanceof HttpErrorResponse && error.status === 404 ? { estado: en404 } : { estado: 'error' },
          ),
      });
    return estado.asReadonly();
  }
}
