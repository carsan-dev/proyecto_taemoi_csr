import {
  HttpBackend,
  HttpClient,
  HttpEvent,
  HttpHeaders,
  HttpXhrBackend,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { EnvironmentInjector, Injectable, InjectionToken, createEnvironmentInjector, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { credentialsInterceptor } from '../../interceptors/credentials.interceptor';
import { bloqueoCambioEscuelaInterceptor } from '../../core/cambio-escuela-pestanas';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';
import { DetalleBloque, ListadoMaterial, SubidaMaterial } from './material-examen.models';

/**
 * El cliente de las subidas con progreso. La app va con `withFetch()`, y fetch no informa del progreso de subida
 * (`FetchBackend` lo dice en `HttpRequest.reportProgress`), así que aquí se usa `HttpXhrBackend` en un inyector propio, con
 * los tres interceptores que importan: el bloqueo del cambio de escuela, la cookie de sesión y el 401 de sesión caducada. Ninguno toca el cuerpo: `clone`
 * conserva la referencia al `File`. El proveedor explícito de `HttpBackend` hace falta porque `provideHttpClient` hereda
 * el `FETCH_BACKEND` del inyector padre.
 */
export const CLIENTE_SUBIDAS = new InjectionToken<HttpClient>('cliente de subidas de material', {
  providedIn: 'root',
  factory: () =>
    createEnvironmentInjector(
      [
        provideHttpClient(withInterceptors([bloqueoCambioEscuelaInterceptor, credentialsInterceptor, errorInterceptor])),
        { provide: HttpBackend, useExisting: HttpXhrBackend },
      ],
      inject(EnvironmentInjector)
    ).get(HttpClient),
});

function cabecerasDe(archivo: File): HttpHeaders {
  return new HttpHeaders({ 'Content-Type': archivo.type || 'application/octet-stream' });
}

/**
 * Las peticiones de `/api/material-examen` (pieza R1), en el chunk de la pantalla y no en `EndpointsService`, que va en
 * el bundle inicial. Los errores los pinta la pantalla. Los ficheros van crudos en el cuerpo, nunca en un FormData.
 */
@Injectable({ providedIn: 'root' })
export class MaterialExamenApiService {
  private readonly http = inject(HttpClient);
  private readonly subidas = inject(CLIENTE_SUBIDAS);
  private readonly base = `${environment.apiUrl}/material-examen`;

  private bloque(disciplina: string, bloque: string): string {
    return `${this.base}/${encodeURIComponent(disciplina)}/${encodeURIComponent(bloque)}`;
  }

  private opciones() {
    return { context: contextoErrorLocal() };
  }

  private opcionesFichero(archivo: File) {
    return { ...this.opciones(), headers: cabecerasDe(archivo) };
  }

  private opcionesProgreso(archivo: File) {
    return { ...this.opcionesFichero(archivo), reportProgress: true, observe: 'events' as const };
  }

  listar(): Observable<ListadoMaterial> {
    return this.http.get<ListadoMaterial>(this.base, this.opciones());
  }

  detalle(disciplina: string, bloque: string): Observable<DetalleBloque> {
    return this.http.get<DetalleBloque>(this.bloque(disciplina, bloque), this.opciones());
  }

  // ------------------------------------------------------------------ vídeos

  subirVideo(disciplina: string, bloque: string, archivo: File): Observable<HttpEvent<SubidaMaterial>> {
    const url = `${this.bloque(disciplina, bloque)}/videos?nombre=${encodeURIComponent(archivo.name)}`;
    return this.subidas.post<SubidaMaterial>(url, archivo, this.opcionesProgreso(archivo));
  }

  sustituirVideo(disciplina: string, bloque: string, fichero: string, archivo: File): Observable<HttpEvent<DetalleBloque>> {
    const url = `${this.urlVideo(disciplina, bloque, fichero)}/contenido`;
    return this.subidas.put<DetalleBloque>(url, archivo, this.opcionesProgreso(archivo));
  }

  cambiarTitulo(disciplina: string, bloque: string, fichero: string, titulo: string): Observable<DetalleBloque> {
    return this.http.patch<DetalleBloque>(this.urlVideo(disciplina, bloque, fichero), { titulo }, this.opciones());
  }

  ordenarVideos(disciplina: string, bloque: string, ficheros: string[]): Observable<DetalleBloque> {
    return this.http.put<DetalleBloque>(`${this.bloque(disciplina, bloque)}/videos/orden`, ficheros, this.opciones());
  }

  eliminarVideo(disciplina: string, bloque: string, fichero: string): Observable<DetalleBloque> {
    return this.http.delete<DetalleBloque>(this.urlVideo(disciplina, bloque, fichero), this.opciones());
  }

  /** Para `<video src>`: lleva la cookie de sesión y pide por `Range`, como el material de las familias. */
  urlVideo(disciplina: string, bloque: string, fichero: string): string {
    return `${this.bloque(disciplina, bloque)}/videos/${encodeURIComponent(fichero)}`;
  }

  // ------------------------------------------------------------------ temario y documentación

  temario(disciplina: string, bloque: string): Observable<Blob> {
    return this.http.get(`${this.bloque(disciplina, bloque)}/temario`, { ...this.opciones(), responseType: 'blob' });
  }

  subirTemario(disciplina: string, bloque: string, archivo: File): Observable<DetalleBloque> {
    return this.http.put<DetalleBloque>(`${this.bloque(disciplina, bloque)}/temario`, archivo, this.opcionesFichero(archivo));
  }

  documento(disciplina: string, bloque: string, fichero: string): Observable<Blob> {
    return this.http.get(this.urlDocumento(disciplina, bloque, fichero), { ...this.opciones(), responseType: 'blob' });
  }

  subirDocumento(disciplina: string, bloque: string, archivo: File): Observable<SubidaMaterial> {
    const url = `${this.bloque(disciplina, bloque)}/documentacion?nombre=${encodeURIComponent(archivo.name)}`;
    return this.http.post<SubidaMaterial>(url, archivo, this.opcionesFichero(archivo));
  }

  sustituirDocumento(disciplina: string, bloque: string, fichero: string, archivo: File): Observable<DetalleBloque> {
    const url = `${this.urlDocumento(disciplina, bloque, fichero)}/contenido`;
    return this.http.put<DetalleBloque>(url, archivo, this.opcionesFichero(archivo));
  }

  eliminarDocumento(disciplina: string, bloque: string, fichero: string): Observable<DetalleBloque> {
    return this.http.delete<DetalleBloque>(this.urlDocumento(disciplina, bloque, fichero), this.opciones());
  }

  private urlDocumento(disciplina: string, bloque: string, fichero: string): string {
    return `${this.bloque(disciplina, bloque)}/documentacion/${encodeURIComponent(fichero)}`;
  }
}
