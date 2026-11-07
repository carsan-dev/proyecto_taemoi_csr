import { HttpClient, HttpContext, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { DestroyRef, Signal, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';
import { MENSAJES, RespuestaPreinscripcion, SolicitudPreinscripcion, normasDeContenido } from './preinscripcion';

/**
 * Llamadas de la preinscripción de la web por plantilla: las mismas rutas, cuerpo y cabecera `Idempotency-Key` que el
 * formulario de Moiskimdo (`PreinscripcionService.crear` y `.configuracion`), pero con los errores en la propia página
 * (`ERROR_HANDLED_LOCALLY`): el aviso global de la aplicación es de Moiskimdo y no va en la web de otra escuela.
 */

const BASE = `${environment.apiUrl}/preinscripciones`;
const LOCAL = () => new HttpContext().set(ERROR_HANDLED_LOCALLY, true);

export type EstadoEnvio =
  | { estado: 'inactivo' }
  | { estado: 'enviando' }
  | { estado: 'hecho'; respuesta: RespuestaPreinscripcion }
  | { estado: 'error'; mensaje: string };

export interface EnvioPreinscripcion {
  estado: Signal<EstadoEnvio>;
  /** Envía la solicitud; no hace nada si ya se está enviando o ya se envió. */
  enviar(solicitud: SolicitudPreinscripcion): void;
  /** Tras una enviada, para empezar otra: vuelve a inactivo con otra clave de idempotencia. */
  reiniciar(): void;
}

/**
 * Envío de la solicitud, en el contexto de inyección del componente. Una clave de idempotencia por formulario (como
 * Moiskimdo): si el usuario reintenta tras un error, la API no la duplica. Error: el `mensaje` de la API (400, 409…) o
 * el de Moiskimdo.
 */
export function envioPreinscripcion(): EnvioPreinscripcion {
  const http = inject(HttpClient);
  const estado = signal<EstadoEnvio>({ estado: 'inactivo' });
  let clave = nuevaClave();
  let peticion: Subscription | null = null;
  inject(DestroyRef).onDestroy(() => peticion?.unsubscribe());

  return {
    estado: estado.asReadonly(),
    enviar(solicitud) {
      const actual = estado().estado;
      if (actual === 'enviando' || actual === 'hecho') {
        return;
      }
      estado.set({ estado: 'enviando' });
      peticion = http
        .post<RespuestaPreinscripcion>(BASE, solicitud, {
          headers: new HttpHeaders({ 'Idempotency-Key': clave }),
          context: LOCAL(),
        })
        .subscribe({
          next: (respuesta) => estado.set({ estado: 'hecho', respuesta }),
          error: (error: unknown) => estado.set({ estado: 'error', mensaje: mensajeDeError(error) }),
        });
    },
    reiniciar() {
      if (estado().estado === 'hecho') {
        clave = nuevaClave();
        estado.set({ estado: 'inactivo' });
      }
    },
  };
}

export function mensajeDeError(error: unknown): string {
  const cuerpo = error instanceof HttpErrorResponse ? (error.error as { message?: unknown; mensaje?: unknown } | null) : null;
  const mensaje = cuerpo && typeof cuerpo === 'object' ? (cuerpo.message ?? cuerpo.mensaje) : null;
  return typeof mensaje === 'string' && mensaje.trim() ? mensaje.trim() : MENSAJES.envioFallido;
}

export type EstadoNormas =
  | { estado: 'cargando' }
  | { estado: 'listo'; normas: string[]; importes: string | null }
  | { estado: 'error' };

/**
 * Las normas de la disciplina del grupo: `GET /api/preinscripciones/public/configuracion/{deporte}`, la plantilla de
 * preinscripción activa que lee Moiskimdo. Sin normas (o con un formato que no se entiende), error: no se puede enviar.
 */
export function normasPreinscripcion(): { estado: Signal<EstadoNormas>; cargar(deporte: string): void } {
  const http = inject(HttpClient);
  const estado = signal<EstadoNormas>({ estado: 'cargando' });
  let deporteActual: string | null = null;
  let peticion: Subscription | null = null;
  inject(DestroyRef).onDestroy(() => peticion?.unsubscribe());

  return {
    estado: estado.asReadonly(),
    cargar(deporte) {
      if (deporte === deporteActual && estado().estado !== 'error') {
        return;
      }
      deporteActual = deporte;
      peticion?.unsubscribe();
      estado.set({ estado: 'cargando' });
      peticion = http
        .get<{ contenido: unknown }>(`${BASE}/public/configuracion/${encodeURIComponent(deporte)}`, { context: LOCAL() })
        .subscribe({
          next: (respuesta) => {
            const { normas, importes } = normasDeContenido(respuesta?.contenido);
            estado.set(normas.length ? { estado: 'listo', normas, importes } : { estado: 'error' });
          },
          error: () => estado.set({ estado: 'error' }),
        });
    },
  };
}

/** `GET /api/preinscripciones/{ref}/documento?token=`: la solicitud firmada en PDF (la misma descarga que Moiskimdo). */
export function enlaceDocumento(respuesta: Pick<RespuestaPreinscripcion, 'referencia' | 'tokenDocumento'>): string {
  return `${BASE}/${encodeURIComponent(respuesta.referencia)}/documento?token=${encodeURIComponent(respuesta.tokenDocumento)}`;
}

function nuevaClave(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
