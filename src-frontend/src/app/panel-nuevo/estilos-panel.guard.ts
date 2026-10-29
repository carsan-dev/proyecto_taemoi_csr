import { inject } from '@angular/core';

import { EstilosPanelService } from './estilos-panel.service';
import { esperarEstilos } from './esperar-estilos';

/**
 * Carga las hojas de gestión y del panel nuevo antes de abrir una pantalla del panel.
 * Va en `canMatch` de cada pantalla del panel
 * (en `canActivate` en el 404 del panel, para no cargar la hoja a quien no casa con él). Si la hoja no carga, la
 * pantalla se abre igual: ya no hay otra a la que pasar. También marca la ruta como pantalla del panel
 * (`enPantallaNueva`, capas/capa-modal.ts).
 */
export const estilosPanelGuard = (): Promise<boolean> =>
  esperarEstilos(inject(EstilosPanelService).cargar());
