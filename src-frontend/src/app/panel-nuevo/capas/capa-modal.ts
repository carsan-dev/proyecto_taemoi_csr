import { Injectable, signal } from '@angular/core';
import Swal from 'sweetalert2';

/**
 * Lo común de las capas modales del panel nuevo (8b-D1, decisiones 235, 248 y 271): diálogo, hoja inferior y panel
 * lateral. Todas son un `<dialog>` abierto con `showModal()` (el resto de la página queda inerte) y:
 * - Esc, el clic fuera (en el velo) y la X cancelan sin hacer nada;
 * - el foco queda contenido (Tab y Mayús+Tab dan la vuelta dentro) y vuelve al disparador al cerrar.
 */

/** Ancho por debajo del cual el diálogo es una hoja inferior y el panel lateral también (decisión 271). */
export const CONSULTA_HOJA = '(max-width: 767.98px)';

const ENFOCABLES = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

/** Los elementos que reciben el foco con Tab dentro de la capa, en su orden. */
export function enfocables(capa: HTMLElement): HTMLElement[] {
  return Array.from(capa.querySelectorAll<HTMLElement>(ENFOCABLES)).filter(
    (e) => !e.closest('[inert]') && e.getClientRects().length > 0
  );
}

/** Tab y Mayús+Tab dan la vuelta dentro de la capa (el navegador dejaría salir el foco a su barra). */
export function contenerFoco(evento: KeyboardEvent, capa: HTMLElement): void {
  if (evento.key !== 'Tab') {
    return;
  }
  const lista = enfocables(capa);
  if (lista.length === 0) {
    evento.preventDefault();
    return;
  }
  const primero = lista[0];
  const ultimo = lista[lista.length - 1];
  const activo = capa.ownerDocument.activeElement as HTMLElement | null;
  const dentro = !!activo && capa.contains(activo);
  if (evento.shiftKey && (activo === primero || !dentro)) {
    evento.preventDefault();
    ultimo.focus();
  } else if (!evento.shiftKey && (activo === ultimo || !dentro)) {
    evento.preventDefault();
    primero.focus();
  }
}

/**
 * El clic cayó en el velo: en un `<dialog>` modal, el `::backdrop` entrega el clic al propio `<dialog>`. La caja de
 * contenido ocupa todo el `<dialog>` (sin relleno propio), así que un clic con destino el `<dialog>` es fuera.
 */
export function esClicEnElVelo(evento: MouseEvent, dialogo: HTMLDialogElement): boolean {
  // Un clic sintético de teclado (Intro o Espacio) llega con `detail` 0 y en 0,0: no es el velo.
  const deTeclado = evento.detail === 0 && evento.clientX === 0 && evento.clientY === 0;
  return evento.target === dialogo && !deTeclado;
}

/**
 * El interceptor de errores de hoy abre un SweetAlert2 con cada respuesta de error; en las pantallas de hoy el aviso
 * propio de la pantalla (otro SweetAlert2) lo sustituye. En el panel nuevo, el aviso o el diálogo que responden a ese
 * error hacen lo mismo: cierran el SweetAlert2 que haya abierto (si no, quedaría detrás o debajo).
 */
export function cerrarSweetAlertAbierto(): void {
  if (Swal.isVisible()) {
    Swal.close();
  }
}

/** El elemento enfocado ahora, para devolverle el foco al cerrar. */
export function disparadorActual(documento: Document): HTMLElement | null {
  const activo = documento.activeElement;
  return activo instanceof HTMLElement && activo !== documento.body ? activo : null;
}

/** Devuelve el foco al disparador si sigue en la página. */
export function devolverFoco(disparador: HTMLElement | null): void {
  if (disparador?.isConnected) {
    disparador.focus();
  }
}

/** ¿El ancho actual es de hoja inferior? Sin `matchMedia` (pruebas antiguas), no. */
export function anchoDeHoja(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia(CONSULTA_HOJA).matches
    : false;
}

/** ¿El usuario pidió menos movimiento? */
export function menosMovimiento(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false;
}

/**
 * Pila de capas modales abiertas. Los avisos se pintan dentro de la de arriba: un `showModal()` deja inerte todo lo
 * que no es suyo, y un aviso fuera de él no se vería ni se podría cerrar.
 */
@Injectable({ providedIn: 'root' })
export class PilaCapas {
  private readonly pila = signal<HTMLElement[]>([]);

  /** La capa modal de arriba, o nula si no hay ninguna. */
  readonly arriba = () => {
    const lista = this.pila();
    return lista.length ? lista[lista.length - 1] : null;
  };

  abrir(capa: HTMLElement): void {
    this.pila.update((lista) => [...lista.filter((c) => c !== capa), capa]);
  }

  cerrar(capa: HTMLElement): void {
    this.pila.update((lista) => lista.filter((c) => c !== capa));
  }
}
