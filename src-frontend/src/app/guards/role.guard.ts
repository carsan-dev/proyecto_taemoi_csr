import { CanActivateFn, Router } from '@angular/router';
import { AuthenticationService, esSoloProfesor } from '../servicios/authentication/authentication.service';
import { inject } from '@angular/core';
import { catchError, map, of } from 'rxjs';

type RouteRoleData = {
  requiredRoles?: readonly string[];
};

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthenticationService);
  const router = inject(Router);
  const routeData = route.data as RouteRoleData | undefined;
  const requiredRoles = routeData?.requiredRoles;

  if (!requiredRoles || requiredRoles.length === 0) {
    router.navigate(['/']);
    return of(false);
  }

  return authService.resolverRolesDisponibles().pipe(
      map((roles) => {
        if (requiredRoles.some((role) => roles.includes(role))) {
          return true;
        }
        // Solo profesor (p. ej. tras entrar con Google, que aterriza en /userpage): su sitio es «Mis grupos».
        router.navigate([esSoloProfesor(roles) ? '/mis-grupos' : '/']);
        return false;
      }),
      catchError(() => {
        router.navigate(['/']);
        return of(false);
      })
    );
};
