import { inject } from '@angular/core';

import { EstilosPanelService } from './estilos-panel.service';

/**
 * Carga la hoja global del panel nuevo (`panel-nuevo.css`) antes de abrir una pantalla del panel. Con la retirada del
 * diseño anterior (8b-Z) sustituye a `disenoNuevoGuard` sin el interruptor: va en `canMatch` de cada pantalla del panel
 * (en `canActivate` en el 404 del panel, para no cargar la hoja a quien no casa con él). Si la hoja no carga, la
 * pantalla se abre igual: ya no hay otra a la que pasar. También marca la ruta como pantalla del panel
 * (`enPantallaNueva`, capas/capa-modal.ts).
 */
export const estilosPanelGuard = (): Promise<boolean> =>
  inject(EstilosPanelService)
    .cargar()
    .then(
      () => true,
      () => true
    );
