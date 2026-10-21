import { DOCUMENT } from '@angular/common';
import { InjectionToken, inject } from '@angular/core';

/** Recarga de la página tras cambiar el diseño del panel (decisión 256); en los tests se sustituye. */
export const RECARGAR_PAGINA = new InjectionToken<() => void>('RECARGAR_PAGINA', {
  providedIn: 'root',
  factory: () => {
    const documento = inject(DOCUMENT);
    return () => documento.defaultView?.location.reload();
  },
});
