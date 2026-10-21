import { inject } from '@angular/core';
import { CanMatchFn } from '@angular/router';
import { catchError, from, map, of, switchMap, take } from 'rxjs';

import { PermisosSesionService } from '../servicios/authentication/permisos-sesion.service';
import { EstilosPanelService } from './estilos-panel.service';

/**
 * Interruptor del diseño nuevo en el router (8b-I, fase3-e8b-plan.md §3). Va en `canMatch` de la entrada nueva de una
 * pantalla, antes de la de hoy con la misma ruta: con `false` el router pasa a la de hoy sin descargar el chunk nuevo.
 * Pasa solo con `disenoNuevo.activo` de `GET /api/auth/permisos` y la hoja `panel-nuevo.css` ya cargada. Falla cerrado:
 * sin permisos, con error o sin la hoja, la pantalla de hoy.
 */
export const disenoNuevoGuard: CanMatchFn = () => {
  const permisos = inject(PermisosSesionService);
  const estilos = inject(EstilosPanelService);
  return permisos.obtener().pipe(
    take(1),
    switchMap((p) => (p?.disenoNuevo?.activo === true ? from(estilos.cargar()).pipe(map(() => true)) : of(false))),
    catchError(() => of(false))
  );
};
