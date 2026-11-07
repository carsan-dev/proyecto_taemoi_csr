import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';
import { CanMatchFn } from '@angular/router';

/**
 * ¿La página se sirvió con `index-plantilla.html`? Solo esa cabeza lleva `<meta name="taemoi-web" content="plantilla">`
 * (fase4-web-plan.md §3.2); el `index.html` de Moiskimdo no la tiene.
 */
export function hayMetaWebPlantilla(documento: Document): boolean {
  return documento.head?.querySelector('meta[name="taemoi-web"][content="plantilla"]') !== null;
}

/**
 * Grupo de la web por plantilla (§3.2, punto 3): casa solo con la meta, de forma síncrona y sin petición, para que la
 * portada de Moiskimdo no espere nada. Vive fuera de `web-plantilla/`: `app.routes.ts` no importa nada de esa carpeta
 * de forma estática (§7).
 */
export const esWebPlantilla: CanMatchFn = () => hayMetaWebPlantilla(inject(DOCUMENT));
