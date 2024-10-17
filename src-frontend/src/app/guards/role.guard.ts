import { CanActivateFn, Router } from '@angular/router';
import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { inject } from '@angular/core';
import { catchError, map, take } from 'rxjs/operators';
import { of } from 'rxjs';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthenticationService);
  const router = inject(Router);

  if (!authService.rolesEstanCargados()) {
    return authService.obtenerRoles().pipe(
      map((roles) => {
        if (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')) {
          return true;
        } else {
          router.navigate(['/inicio']);
          return false;
        }
      }),
      catchError(() => {
        router.navigate(['/inicio']);
        return of(false);
      })
    );
  } else {
    return authService.rolesCambio.pipe(
      take(1),
      map((roles) => {
        if (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')) {
          return true;
        } else {
          router.navigate(['/inicio']);
          return false;
        }
      }),
      catchError(() => {
        router.navigate(['/inicio']);
        return of(false);
      })
    );
  }
};
