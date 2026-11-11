import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { contextoErrorLocal } from '../../carga/lecturas-panel.service';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import { ContenidoWeb } from './web-contenido-vista';

export interface DatosContenido {
  estado: EstadoWebEscuela;
  contenido: ContenidoWeb;
}

/**
 * «Web · Contenido» (W12c): el estado de la web (`GET /api/escuela/web`, W3) y su contenido editable
 * (`GET /api/escuela/web/contenido`, W12a). Solo lectura en esta pieza. Con `contextoErrorLocal()` el fallo lo enseña
 * la pantalla.
 */
@Injectable()
export class WebContenidoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/escuela/web`;

  obtener(): Observable<DatosContenido> {
    const opciones = { withCredentials: true, context: contextoErrorLocal() };
    return forkJoin({
      estado: this.http.get<EstadoWebEscuela>(this.url, opciones),
      contenido: this.http.get<ContenidoWeb>(`${this.url}/contenido`, opciones),
    });
  }
}
