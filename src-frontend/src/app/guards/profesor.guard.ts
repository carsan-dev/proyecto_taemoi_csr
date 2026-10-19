import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthenticationService } from '../servicios/authentication/authentication.service';

/**
 * «Mi caja» (pieza 6b-3b): cualquier sesión con ROLE_PROFESOR (solo profesor, familia + profesor, gestión + profesor).
 * Sin catálogo ni otros resolvers: una sesión solo profesor recibe 403 en todo lo que no sea su caja.
 */
export const profesorGuard: CanActivateFn = () => {
  const auth = inject(AuthenticationService);
  const router = inject(Router);

  return auth.resolverRolesDisponibles().pipe(
    map((roles) => roles.includes('ROLE_PROFESOR') || router.createUrlTree(['/'])),
    catchError(() => of(router.createUrlTree(['/'])))
  );
};
