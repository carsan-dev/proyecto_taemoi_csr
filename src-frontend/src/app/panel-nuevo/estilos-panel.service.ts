import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/** Carga las hojas diferidas de gestión y del panel una sola vez por página. */
@Injectable({ providedIn: 'root' })
export class EstilosPanelService {
  private readonly document = inject(DOCUMENT);
  private readonly pendientes = new Map<string, Promise<void>>();

  cargar(): Promise<void> {
    return Promise.all([this.cargarGestion(), this.cargarHoja('panel-nuevo')]).then(() => undefined);
  }

  cargarGestion(): Promise<void> {
    return this.cargarHoja('gestion-compartida');
  }

  private cargarHoja(nombre: string): Promise<void> {
    const pendiente = this.pendientes.get(nombre);
    if (pendiente) {
      return pendiente;
    }
    const enlace = this.document.createElement('link');
    enlace.rel = 'stylesheet';
    enlace.href = `${nombre}.css`;
    enlace.setAttribute(`data-${nombre}`, '');

    const carga = new Promise<void>((resolver, rechazar) => {
      enlace.addEventListener('load', () => resolver());
      enlace.addEventListener('error', () => {
        enlace.remove();
        this.pendientes.delete(nombre);
        rechazar(new Error(`No se pudo cargar ${nombre}.css`));
      });
    });
    this.pendientes.set(nombre, carga);

    if (nombre === 'gestion-compartida') {
      const inicial = Array.from(this.document.head.querySelectorAll<HTMLLinkElement>('link[rel="stylesheet"]'))
        .find((hoja) => /(?:^|\/)styles(?:-[^/]+)?\.css(?:[?#]|$)/.test(hoja.getAttribute('href') ?? ''));
      // Justo después de la hoja inicial (Bootstrap incluido) y antes de los estilos de componentes que Angular añade:
      // el mismo sitio de la cascada que tenían dentro de styles.scss.
      this.document.head.insertBefore(enlace, inicial ? inicial.nextSibling : this.document.head.firstChild);
    } else {
      this.document.head.appendChild(enlace);
    }
    return carga;
  }
}
