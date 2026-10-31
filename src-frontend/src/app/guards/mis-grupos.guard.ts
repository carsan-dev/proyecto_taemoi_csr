import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { PermisosSesionService } from '../servicios/authentication/permisos-sesion.service';

/**
 * «Mis grupos» (E7, E8-5): pasa quien tiene la capacidad MIS_GRUPOS (decisión 217): el solo profesor, la familia +
 * profesor y la gestión + profesor con grupos asignados. Sin ella, la gestión va a su panel y cualquier otra sesión a
 * la raíz; un responsable de sede sin grupos, a su panel; si no se pueden leer los permisos, también a la raíz. Misma
 * condición que el enlace de la cabecera.
 * Se aplica en la ruta padre; las hijas son perezosas y solo se alcanzan a través de ella.
 */
export const misGruposGuard: CanActivateFn = () => {
  const auth = inject(AuthenticationService);
  const permisos = inject(PermisosSesionService);
  const router = inject(Router);

  return permisos.obtener().pipe(
    switchMap((p) => {
      if (p.capacidades?.includes('MIS_GRUPOS')) {
        return of(true);
      }
      return auth.resolverRolesDisponibles().pipe(
        map((roles) =>
          router.createUrlTree([
            roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER') || p.capacidades?.includes('PANEL_SEDE')
              ? '/adminpage'
              : '/',
          ])
        )
      );
    }),
    catchError(() => of(router.createUrlTree(['/'])))
  );
};
