import { inject } from '@angular/core';
import { CanMatchFn } from '@angular/router';
import { catchError, map, of, take } from 'rxjs';

import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';

/**
 * Sesión de gestión del panel nuevo (PROPIETARIO o ADMINISTRADOR de `GET /api/auth/permisos`) o de responsable de sede
 * (PANEL_SEDE), como la comprobación con la que la cabecera del panel decide pintarse (`SkeletonComponent`). Es
 * `canMatch` y no `canActivate`: la página «no encontrada» del panel solo debe casar con esa sesión; una de familia, de
 * solo profesor o un visitante siguen con el 404 público, sin redirecciones. Falla cerrado: sin permisos o con error, no
 * casa.
 */
export const gestionPanelGuard: CanMatchFn = () =>
  inject(PermisosSesionService)
    .obtener()
    .pipe(
      take(1),
      map(
        (p) =>
          (p?.roles ?? []).some((rol) => rol === 'PROPIETARIO' || rol === 'ADMINISTRADOR') ||
          !!p?.capacidades?.includes('PANEL_SEDE')
      ),
      catchError(() => of(false))
    );
