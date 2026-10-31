import { CanActivateFn, Router } from '@angular/router';
import { AuthenticationService, esSoloProfesor } from '../servicios/authentication/authentication.service';
import { inject } from '@angular/core';
import { catchError, map, of, switchMap } from 'rxjs';

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
      // Un responsable espera a sus capacidades: entra con PANEL_SEDE y las pantallas las leen ya resueltas.
      switchMap((roles) =>
        authService.capacidadesDeResponsable(roles).pipe(
          map((capacidades) => {
            const panelSede = capacidades.includes('PANEL_SEDE');
            const pasa = (role: string) =>
              roles.includes(role) && (role !== 'ROLE_RESPONSABLE_SEDE' || panelSede);
            if (requiredRoles.some(pasa)) {
              return true;
            }
            // Solo profesor (p. ej. tras entrar con Google, que aterriza en /userpage): su sitio es «Mis grupos».
            // Un responsable, en una pantalla que no es suya o tras entrar con Google, va a su panel.
            router.navigate([esSoloProfesor(roles) ? '/mis-grupos' : panelSede ? '/adminpage' : '/']);
            return false;
          })
        )
      ),
      catchError(() => {
        router.navigate(['/']);
        return of(false);
      })
    );
};
