import type { CanDeactivateFn } from '@angular/router';

/** Una pantalla que puede tener cambios sin guardar. */
export interface ConCambiosSinGuardar {
  puedeSalir(): boolean | Promise<boolean>;
}

/**
 * Aviso al salir con cambios sin guardar (8b-3n): pregunta a la propia pantalla. Va en `app.routes.ts` (el inicial):
 * no importa nada del panel nuevo; el diálogo lo abre la pantalla, que está en su chunk.
 */
export const salirSinGuardarGuard: CanDeactivateFn<ConCambiosSinGuardar> = (pantalla) =>
  typeof pantalla?.puedeSalir === 'function' ? pantalla.puedeSalir() : true;
