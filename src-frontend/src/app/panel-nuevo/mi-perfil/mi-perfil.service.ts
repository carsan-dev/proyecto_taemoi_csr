import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';

/** Lo que devuelve `GET` y `PUT /api/panel/mi-perfil`. */
export interface MiPerfil {
  /** Lo que la cuenta eligió para su panel; null si no eligió nada. */
  nombrePreferido: string | null;
  /** El nombre que se ve si no hay preferido («Lucía M.»); null si la cuenta no tiene. */
  nombreBase: string | null;
  cumpleDia: number | null;
  cumpleMes: number | null;
  /** El cumpleaños sale de la ficha de alumno de la cuenta: no se edita aquí. */
  cumpleFicha: { dia: number; mes: number } | null;
}

/** Cuerpo del `PUT`: lo único que se puede cambiar. */
export interface CambiosMiPerfil {
  nombrePreferido: string | null;
  cumpleDia: number | null;
  cumpleMes: number | null;
}

/**
 * «Mi perfil» (8b-3o): `/api/panel/mi-perfil`. Con `contextoErrorLocal()` el fallo lo enseña la pantalla (bloque de
 * error o mensaje bajo el formulario) y no además el SweetAlert2 del interceptor; un 401 lo sigue tratando el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class MiPerfilService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/panel/mi-perfil`;

  obtener(): Observable<MiPerfil> {
    return this.http.get<MiPerfil>(this.url, { withCredentials: true, context: contextoErrorLocal() });
  }

  guardar(cambios: CambiosMiPerfil): Observable<MiPerfil> {
    return this.http.put<MiPerfil>(this.url, cambios, { withCredentials: true, context: contextoErrorLocal() });
  }
}
