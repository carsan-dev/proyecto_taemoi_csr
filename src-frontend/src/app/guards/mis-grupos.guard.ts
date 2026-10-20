import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthenticationService, veMisGrupos } from '../servicios/authentication/authentication.service';

/**
 * «Mis grupos» (E7): ROLE_PROFESOR sin gestión (solo profesor, o familia + profesor). El servidor deniega
 * `/api/profesor/**` a ADMIN y MANAGER (D7-10), así que una cuenta gestión + profesor va a su panel y cualquier otra
 * sin ROLE_PROFESOR (o si fallan los roles) a la raíz. Misma condición que el enlace de la cabecera.
 * Se aplica en la ruta padre; las hijas son perezosas y solo se alcanzan a través de ella.
 */
export const misGruposGuard: CanActivateFn = () => {
  const auth = inject(AuthenticationService);
  const router = inject(Router);

  return auth.resolverRolesDisponibles().pipe(
    map((roles) => {
      if (veMisGrupos(roles)) {
        return true;
      }
      const esGestion = roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER');
      return router.createUrlTree([roles.includes('ROLE_PROFESOR') && esGestion ? '/adminpage' : '/']);
    }),
    catchError(() => of(router.createUrlTree(['/'])))
  );
};
