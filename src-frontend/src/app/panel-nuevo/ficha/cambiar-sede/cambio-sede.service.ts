import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { contextoErrorLocal } from '../../carga/lecturas-panel.service';

/** `CambioSedeAlumnoDTO`: lo que pasa con los cargos y los grupos del alumno al cambiarlo de sede. */
export interface CambioSede {
  sedeActualId: number | null;
  sedeActualNombre: string | null;
  sedeNuevaId: number;
  sedeNuevaNombre: string;
  cargosPendientes: number;
  importePendiente: number;
  cargosCobrados: number;
  importeCobrado: number;
  gruposEnSedeActual: number;
}

/**
 * Las dos llamadas del panel «Cambiar de sede» (E9-1), con `contextoErrorLocal()`: el fallo lo enseña el panel y el
 * interceptor no abre además su SweetAlert2. Va en el chunk de la ficha, no en el inicial.
 */
@Injectable({ providedIn: 'root' })
export class CambioSedeService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** `GET /alumnos/{id}/cambio-sede?sedeId=`: la lectura previa, sin cambiar nada. */
  lectura(alumnoId: number, sedeId: number): Observable<CambioSede> {
    return this.http.get<CambioSede>(`${this.base}/alumnos/${alumnoId}/cambio-sede`, {
      params: { sedeId },
      withCredentials: true,
      context: contextoErrorLocal(),
    });
  }

  /** `PUT /alumnos/{id}/sede`: aplica el cambio y devuelve la misma forma. */
  aplicar(alumnoId: number, sedeId: number): Observable<CambioSede> {
    return this.http.put<CambioSede>(`${this.base}/alumnos/${alumnoId}/sede`, { sedeId }, {
      withCredentials: true,
      context: contextoErrorLocal(),
    });
  }
}
