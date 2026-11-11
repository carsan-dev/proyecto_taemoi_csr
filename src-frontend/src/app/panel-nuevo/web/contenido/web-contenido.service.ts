import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { EventoListaWeb, EventosWeb, SedeWeb, SitioWeb, TemaWeb } from '../../../web-plantilla/datos/modelos';
import { contextoErrorLocal } from '../../carga/lecturas-panel.service';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import { PeticionContenido } from './web-contenido-guardar';
import { ContenidoWeb } from './web-contenido-vista';

export interface DatosContenido {
  estado: EstadoWebEscuela;
  contenido: ContenidoWeb;
}

/** Para elegir cuál se ve en «Una sede» y «Un evento» (W12c-bis). */
export interface ListasVista {
  sedes: Pick<SedeWeb, 'nombre' | 'slug'>[];
  /** Los próximos (como el boceto); sin próximos, los anteriores. */
  eventos: Pick<EventoListaWeb, 'id' | 'titulo' | 'fecha'>[];
}

/**
 * «Web · Contenido» (W12c): el estado de la web (`GET /api/escuela/web`, W3) y su contenido editable
 * (`GET /api/escuela/web/contenido`, W12a). Solo lectura en esta pieza. Con `contextoErrorLocal()` el fallo lo enseña
 * la pantalla.
 *
 * W12d: `guardar` (`PUT /api/escuela/web/contenido`, reemplazo completo con la versión leída): 200 con el contenido y la
 * versión nueva; 400 `{mensaje, errores}`; 409 si otra persona guardó antes. El fallo lo trata la pantalla.
 *
 * W12c-bis: las sedes y los eventos de la vista previa de gestión (`GET /api/publico/web?vistaPrevia=true` y
 * `/eventos`, como «Web · Tema»), solo para el selector de «Una sede» y «Un evento». Un fallo da null.
 */
@Injectable()
export class WebContenidoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/escuela/web`;
  private readonly urlPublica = `${environment.apiUrl}/publico/web`;

  obtener(): Observable<DatosContenido> {
    const opciones = { withCredentials: true, context: contextoErrorLocal() };
    return forkJoin({
      estado: this.http.get<EstadoWebEscuela>(this.url, opciones),
      contenido: this.http.get<ContenidoWeb>(`${this.url}/contenido`, opciones),
    });
  }

  guardar(peticion: PeticionContenido): Observable<ContenidoWeb> {
    return this.http.put<ContenidoWeb>(`${this.url}/contenido`, peticion, { withCredentials: true, context: contextoErrorLocal() });
  }

  listas(tema: TemaWeb): Observable<ListasVista | null> {
    const opciones = { withCredentials: true, context: contextoErrorLocal() };
    const previa = new HttpParams().set('vistaPrevia', 'true');
    return forkJoin({
      sitio: this.http.get<SitioWeb>(this.urlPublica, { ...opciones, params: previa.set('tema', tema) }),
      eventos: this.http.get<EventosWeb>(`${this.urlPublica}/eventos`, { ...opciones, params: previa }),
    }).pipe(
      map(({ sitio, eventos }) => ({
        sedes: sitio.sedes.map(({ nombre, slug }) => ({ nombre, slug })),
        eventos: (eventos.proximos.length ? eventos.proximos : eventos.anteriores).map(({ id, titulo, fecha }) => ({ id, titulo, fecha })),
      })),
      catchError(() => of(null)),
    );
  }
}
