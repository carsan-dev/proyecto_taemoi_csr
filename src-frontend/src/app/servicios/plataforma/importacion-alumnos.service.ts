import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

/** Un problema de una fila de la hoja. Se identifica por fila y número de federación, nunca por nombre ni DNI. */
export interface Incidencia {
  fila: number;
  numeroFederacion: number | null;
  columna: string | null;
  motivo: string;
}

export interface FilaYaCargada {
  fila: number;
  numeroFederacion: number | null;
}

/** Resultado de ensayar o cargar una hoja de alumnos. */
export interface InformeImportacion {
  filas: number;
  listas: number;
  creados: number;
  yaCargados: number;
  errores: Incidencia[];
  avisos: Incidencia[];
  yaCargadas: FilaYaCargada[];
}

/** La hoja con errores lista para guardar en el navegador. */
export interface HojaDescargable {
  blob: Blob;
  nombre: string;
}

/**
 * Importación de alumnos de una escuela (solo la sesión de plataforma). Los errores los muestra la pantalla. No se
 * envía `disciplina`: el servidor usa la única activa de la escuela. Nada de lo que viaja se guarda ni se registra.
 */
@Injectable({ providedIn: 'root' })
export class ImportacionAlumnosService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/plataforma/escuelas`;

  private opciones() {
    return { withCredentials: true, context: new HttpContext().set(ERROR_HANDLED_LOCALLY, true) };
  }

  private url(organizacionId: number, accion: string): string {
    return `${this.base}/${organizacionId}/importacion/alumnos/${accion}`;
  }

  private cuerpo(archivo: File): FormData {
    const cuerpo = new FormData();
    cuerpo.append('file', archivo);
    return cuerpo;
  }

  /** Ensayo: valida la hoja y no escribe nada. 400 si el fichero no es la hoja. */
  ensayar(organizacionId: number, archivo: File): Observable<InformeImportacion> {
    return this.http.post<InformeImportacion>(this.url(organizacionId, 'ensayo'), this.cuerpo(archivo), this.opciones());
  }

  /** Carga, todo o nada. 422 (con el informe en el cuerpo) si alguna fila tiene errores; 500 con `{mensaje}`. */
  cargar(organizacionId: number, archivo: File): Observable<InformeImportacion> {
    return this.http.post<InformeImportacion>(this.url(organizacionId, 'carga'), this.cuerpo(archivo), this.opciones());
  }

  /** La misma hoja con la columna «revisar» rellena. Se genera al momento y no se guarda en el servidor. */
  hojaConErrores(organizacionId: number, archivo: File): Observable<HojaDescargable> {
    return this.http
      .post(this.url(organizacionId, 'hoja-con-errores'), this.cuerpo(archivo), {
        ...this.opciones(),
        responseType: 'blob',
        observe: 'response',
      })
      .pipe(
        map((respuesta) => ({
          blob: respuesta.body as Blob,
          nombre: nombreDescarga(respuesta.headers.get('Content-Disposition'), archivo.name),
        }))
      );
  }
}

/** Nombre de `Content-Disposition` (`filename*` primero); sin él, `<original>-revisar.xlsx`. */
export function nombreDescarga(cabecera: string | null, original: string): string {
  if (cabecera) {
    const codificado = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(cabecera);
    if (codificado) {
      try {
        return decodeURIComponent(codificado[1].trim());
      } catch {
        // cae al siguiente
      }
    }
    const simple = /filename\s*=\s*"?([^";]+)"?/i.exec(cabecera);
    if (simple?.[1].trim()) {
      return simple[1].trim();
    }
  }
  const base = original.replace(/\.xlsx$/i, '');
  return `${base}-revisar.xlsx`;
}
