import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { ProductoAlumnoDTO } from '../../interfaces/producto-alumno-dto';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';
import type { AlumnoProductos, CambiosCargo, ProductoCatalogo } from './productos-alumno-vista';

/**
 * Las peticiones de «Productos y cargos» (8b-3n): las MISMAS (método, ruta y cuerpo, salvo el `PUT`) que los métodos de
 * `EndpointsService` que usa la pantalla de hoy, con `contextoErrorLocal()` para que el fallo lo enseñe la pantalla
 * nueva (en su bloque de error o en la fila) y no además el SweetAlert2 del interceptor. Un 401 lo sigue tratando el
 * interceptor.
 */
@Injectable({ providedIn: 'root' })
export class ProductosAlumnoApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  private opciones() {
    return { withCredentials: true, context: contextoErrorLocal() };
  }

  /** `obtenerProductosDelAlumno`: `GET /productos-alumno/alumno/{alumnoId}`. */
  cargos(alumnoId: number): Observable<ProductoAlumnoDTO[]> {
    return this.http.get<ProductoAlumnoDTO[]>(`${this.base}/productos-alumno/alumno/${alumnoId}`, this.opciones());
  }

  /** `obtenerTodosLosProductos`: `GET /productos/todos`. */
  catalogo(): Observable<ProductoCatalogo[]> {
    return this.http.get<ProductoCatalogo[]>(`${this.base}/productos/todos`, this.opciones());
  }

  /** `obtenerAlumnoPorId`: `GET /alumnos/{id}` (nombre, foto y deportes). */
  alumno(alumnoId: number): Observable<AlumnoProductos> {
    return this.http.get<AlumnoProductos>(`${this.base}/alumnos/${alumnoId}`, this.opciones());
  }

  /**
   * `PUT /productos-alumno/{id}` (la ruta de `actualizarProductoAlumno`) con SOLO los campos editados (decisión 298): el
   * servidor deja como está lo que no llega, así que nunca se envía el pago.
   */
  actualizar(id: number, cambios: CambiosCargo): Observable<ProductoAlumnoDTO> {
    return this.http.put<ProductoAlumnoDTO>(`${this.base}/productos-alumno/${id}`, cambios, this.opciones());
  }

  /** `eliminarProductoAlumno`: `DELETE /productos-alumno/{id}`. */
  eliminar(id: number): Observable<unknown> {
    return this.http.delete<unknown>(`${this.base}/productos-alumno/${id}`, this.opciones());
  }

  /** `asignarProductoAAlumno` o, con deporte (matrícula), `asignarProductoAAlumnoDeporte` (deporte codificado). */
  asignar(alumnoId: number, productoId: number, deporte: string | null, cuerpo: ProductoAlumnoDTO): Observable<ProductoAlumnoDTO> {
    const ruta = `${this.base}/productos-alumno/alumno/${alumnoId}/producto/${productoId}`;
    return this.http.post<ProductoAlumnoDTO>(
      deporte ? `${ruta}/deporte/${encodeURIComponent(deporte)}` : ruta,
      cuerpo,
      this.opciones()
    );
  }

  /** `cancelarProductoAlumno`: `POST /productos-alumno/{id}/cancelar` con `{motivo}`. */
  cancelar(id: number, motivo: string): Observable<ProductoAlumnoDTO> {
    return this.http.post<ProductoAlumnoDTO>(`${this.base}/productos-alumno/${id}/cancelar`, { motivo }, this.opciones());
  }

  /** `reactivarProductoAlumno`: `POST /productos-alumno/{id}/reactivar` sin cuerpo. */
  reactivar(id: number): Observable<ProductoAlumnoDTO> {
    return this.http.post<ProductoAlumnoDTO>(`${this.base}/productos-alumno/${id}/reactivar`, null, this.opciones());
  }
}
