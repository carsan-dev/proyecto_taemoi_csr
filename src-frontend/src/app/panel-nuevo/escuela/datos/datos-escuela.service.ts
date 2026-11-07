import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { contextoErrorLocal } from '../../carga/lecturas-panel.service';
import { DatosEscuela, SolicitudDatosEscuela } from './datos-escuela-vista';

/**
 * `GET` y `PUT /api/escuela/datos` (pieza 394): los datos de la escuela activa. Solo el propietario. Con
 * `contextoErrorLocal()` el fallo lo enseña la pantalla (bloque de error, errores por campo y aviso) y no además el
 * SweetAlert2 del interceptor; un 401 lo sigue tratando el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class DatosEscuelaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/escuela/datos`;

  obtener(): Observable<DatosEscuela> {
    return this.http.get<DatosEscuela>(this.url, { withCredentials: true, context: contextoErrorLocal() });
  }

  guardar(solicitud: SolicitudDatosEscuela): Observable<DatosEscuela> {
    return this.http.put<DatosEscuela>(this.url, solicitud, { withCredentials: true, context: contextoErrorLocal() });
  }
}
