import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { SitioWeb, TemaWeb } from '../../../web-plantilla/datos/modelos';
import { contextoErrorLocal } from '../../carga/lecturas-panel.service';
import { EstadoWebEscuela } from './web-tema-vista';

/**
 * «Web · Tema» (W7): `GET` y `PUT /api/escuela/web/publicacion` (W3) y el slug de la primera sede para la vista previa
 * de «Una sede» (`GET /api/publico/web?vistaPrevia=true`, que con la sesión del propietario da los datos aunque la web
 * no esté publicada). Con `contextoErrorLocal()` el fallo lo enseña la pantalla y no además el SweetAlert2 del
 * interceptor; un 401 lo sigue tratando el interceptor. Se provee en la pantalla: el slug guardado no pasa de una
 * visita (ni de una escuela) a otra.
 */
@Injectable()
export class WebTemaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/escuela/web`;
  private readonly urlPublica = `${environment.apiUrl}/publico/web`;
  private slug$: Observable<string | null> | null = null;

  obtener(): Observable<EstadoWebEscuela> {
    return this.http.get<EstadoWebEscuela>(this.url, { withCredentials: true, context: contextoErrorLocal() });
  }

  publicar(tema: TemaWeb): Observable<EstadoWebEscuela> {
    return this.http.put<EstadoWebEscuela>(`${this.url}/publicacion`, { tema }, { withCredentials: true, context: contextoErrorLocal() });
  }

  /**
   * El slug de la primera sede con página (`/sedes/<slug>`); null si no hay ninguna o la petición falla. Un éxito se
   * guarda toda la visita (las sedes no dependen del tema); un fallo no, para que el siguiente acceso lo repita.
   */
  slugPrimeraSede(tema: TemaWeb): Observable<string | null> {
    if (!this.slug$) {
      const params = new HttpParams().set('vistaPrevia', 'true').set('tema', tema);
      this.slug$ = this.http
        .get<SitioWeb>(this.urlPublica, { params, withCredentials: true, context: contextoErrorLocal() })
        .pipe(
          map((sitio) => sitio.sedes.find((s) => !!s.slug)?.slug ?? null),
          catchError(() => {
            this.slug$ = null;
            return of(null);
          }),
          shareReplay(1)
        );
    }
    return this.slug$;
  }
}
