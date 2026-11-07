import { Routes } from '@angular/router';

import { CambiandoEscuelaComponent, sinSalirDuranteElCambio } from './cambiando-escuela.component';
import { ElegirEscuelaComponent } from './elegir-escuela.component';
import { elegirEscuelaGuard } from './elegir-escuela.guard';

/** «Tras entrar» (`/elegir-escuela`): el guard mira cuántas escuelas hay antes de activar la pantalla. */
export const RUTAS_ELEGIR_ESCUELA: Routes = [
  { path: '', canActivate: [elegirEscuelaGuard], component: ElegirEscuelaComponent },
];

/** Ruta neutra del cambio de escuela (`/cambiando-escuela`, siempre con `skipLocationChange`). */
export const RUTAS_CAMBIANDO_ESCUELA: Routes = [
  { path: '', canDeactivate: [sinSalirDuranteElCambio], component: CambiandoEscuelaComponent },
];
