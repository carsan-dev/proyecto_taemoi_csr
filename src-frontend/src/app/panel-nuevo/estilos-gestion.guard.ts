import { inject } from '@angular/core';

import { EstilosPanelService } from './estilos-panel.service';
import { esperarEstilos } from './esperar-estilos';

/** Espera solo los estilos compartidos, sin identificar la ruta como pantalla nueva. */
export const estilosGestionGuard = (): Promise<boolean> =>
  esperarEstilos(inject(EstilosPanelService).cargarGestion());
