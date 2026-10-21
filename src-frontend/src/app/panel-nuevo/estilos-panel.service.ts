import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/**
 * Carga la hoja global del panel nuevo (tokens, fuentes y base bajo `.pn`) una sola vez.
 * No va en el bundle inicial: `panel-nuevo.css` se compila con `inject: false`.
 */
@Injectable({ providedIn: 'root' })
export class EstilosPanelService {
  private readonly document = inject(DOCUMENT);
  private pendiente: Promise<void> | null = null;

  cargar(): Promise<void> {
    if (this.pendiente) {
      return this.pendiente;
    }
    const enlace = this.document.createElement('link');
    enlace.rel = 'stylesheet';
    enlace.href = 'panel-nuevo.css';
    enlace.setAttribute('data-panel-nuevo', '');

    this.pendiente = new Promise<void>((resolver, rechazar) => {
      enlace.addEventListener('load', () => resolver());
      enlace.addEventListener('error', () => {
        enlace.remove();
        this.pendiente = null;
        rechazar(new Error('No se pudo cargar panel-nuevo.css'));
      });
    });
    this.document.head.appendChild(enlace);
    return this.pendiente;
  }
}
