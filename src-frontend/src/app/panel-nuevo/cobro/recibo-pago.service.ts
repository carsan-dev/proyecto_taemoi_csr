import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AvisosService } from '../avisos/avisos.service';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';

export const TEXTO_RECIBO_ANULADO = 'Este cobro está anulado o ya no está cobrado: no tiene recibo.';
export const TEXTO_RECIBO_FALLO = 'No se pudo generar el recibo. Inténtalo de nuevo.';
const NOMBRE_RESPALDO = 'recibo.pdf';

/**
 * Descarga del recibo de un cobro desde el panel nuevo (pieza 279): `GET /api/productos-alumno/{id}/recibo`, un PDF con
 * el nombre de `Content-Disposition`. Solo gestión lo ofrece (lo decide cada pantalla; el servidor devuelve 403 al
 * resto). Un 409 (sin cobrar, anulado o devuelto) muestra el mensaje de la API (`{"mensaje": …}`, que llega como `Blob` por
 * el `responseType`) o, si no viene, el texto de respaldo. Mientras un recibo se descarga, `enCurso` lo marca para no pedirlo
 * dos veces. Vive en los chunks del panel nuevo.
 */
@Injectable({ providedIn: 'root' })
export class ReciboPagoService {
  private readonly http = inject(HttpClient);
  private readonly avisos = inject(AvisosService);

  /** Los cargos con un recibo en descarga. */
  readonly enCurso = signal<ReadonlySet<number>>(new Set());

  descargando(productoAlumnoId: number): boolean {
    return this.enCurso().has(productoAlumnoId);
  }

  async descargar(productoAlumnoId: number): Promise<void> {
    if (this.descargando(productoAlumnoId)) {
      return;
    }
    this.enCurso.update((s) => new Set(s).add(productoAlumnoId));
    try {
      const respuesta = await firstValueFrom(
        this.http.get(`${environment.apiUrl}/productos-alumno/${productoAlumnoId}/recibo`, {
          withCredentials: true,
          observe: 'response',
          responseType: 'blob',
          context: contextoErrorLocal(),
        })
      );
      this.guardar(respuesta.body as Blob, nombreDeRecibo(respuesta));
    } catch (error) {
      const respuesta = error as HttpErrorResponse;
      this.avisos.error(
        respuesta?.status === 409 ? ((await mensajeDeApi(respuesta.error)) ?? TEXTO_RECIBO_ANULADO) : TEXTO_RECIBO_FALLO
      );
    } finally {
      this.enCurso.update((s) => {
        const resto = new Set(s);
        resto.delete(productoAlumnoId);
        return resto;
      });
    }
  }

  /** Entrega el archivo al navegador (como el certificado de cobros). */
  guardar(blob: Blob, nombre: string): void {
    const url = globalThis.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nombre;
    a.click();
    globalThis.URL.revokeObjectURL(url);
  }
}

/** El nombre de `Content-Disposition: attachment; filename="…"`, o `recibo.pdf`. */
export function nombreDeRecibo(respuesta: HttpResponse<unknown>): string {
  const cabecera = respuesta.headers.get('Content-Disposition') ?? '';
  const nombre = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cabecera)?.[1]?.trim();
  return nombre || NOMBRE_RESPALDO;
}

/** El `mensaje` del cuerpo de error de la API (`{"mensaje": …}`), en `Blob`, texto u objeto; nulo si no viene. */
async function mensajeDeApi(cuerpo: unknown): Promise<string | null> {
  try {
    const texto = cuerpo instanceof Blob ? await cuerpo.text() : cuerpo;
    const datos: unknown = typeof texto === 'string' ? JSON.parse(texto) : texto;
    const mensaje = (datos as { mensaje?: unknown } | null)?.mensaje;
    return typeof mensaje === 'string' && mensaje.trim() ? mensaje.trim() : null;
  } catch {
    return null;
  }
}
