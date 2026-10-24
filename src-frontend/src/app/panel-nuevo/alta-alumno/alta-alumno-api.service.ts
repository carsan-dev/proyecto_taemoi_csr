import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { disciplinaActual } from '../../features/catalogo/catalogo-actual';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';

/**
 * Las llamadas del alta de alumno nueva (8b-3l): las MISMAS peticiones que `EndpointsService.crearAlumno`,
 * `obtenerGrados` y `obtenerGradosPorFechaNacimiento` (ruta, cuerpo y partes del multipart), con `contextoErrorLocal()`:
 * el fallo lo enseña la pantalla (bajo el campo, en el resumen y en el aviso) y el interceptor no abre además su
 * SweetAlert2. Va en el chunk del alta, no en el inicial.
 */
@Injectable({ providedIn: 'root' })
export class AltaAlumnoApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** `POST /alumnos/crear` multipart: «nuevo» (JSON) y, si hay, «file» con su nombre. */
  crear(cuerpo: Record<string, any>, imagen: File | null): Observable<any> {
    const datos = new FormData();
    datos.append('nuevo', JSON.stringify(cuerpo));
    if (imagen) {
      datos.append('file', imagen, imagen.name);
    }
    return this.http.post<any>(`${this.base}/alumnos/crear`, datos, { withCredentials: true, context: contextoErrorLocal() });
  }

  /** `GET /grados`: todos los grados, para filtrarlos por disciplina y edad. */
  grados(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/grados`, { withCredentials: true, context: contextoErrorLocal() });
  }

  /** `GET /grados/disponibles/{fecha}` con la disciplina por defecto, como hoy. */
  gradosDisponibles(fechaNacimiento: string): Observable<string[]> {
    const codigo = disciplinaActual(null)?.codigo;
    const consulta = codigo ? `?deporte=${encodeURIComponent(codigo)}` : '';
    return this.http.get<string[]>(`${this.base}/grados/disponibles/${fechaNacimiento}${consulta}`, {
      withCredentials: true,
      context: contextoErrorLocal(),
    });
  }
}
