import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of, take } from 'rxjs';
import { AuthenticationService } from '../servicios/authentication/authentication.service';

export const adminOnlyGuard: CanActivateFn = () => {
  const authService = inject(AuthenticationService);
  const router = inject(Router);

  if (authService.rolesEstanCargados()) {
    return authService.rolesCambio.pipe(
      take(1),
      map((roles) => {
        if (roles.includes('ROLE_ADMIN')) {
          return true;
        }
        router.navigate(['/']);
        return false;
      }),
      catchError(() => {
        router.navigate(['/']);
        return of(false);
      })
    );
  }

  return authService.obtenerRoles().pipe(
    map((roles) => {
      if (roles.includes('ROLE_ADMIN')) {
        return true;
      }
      router.navigate(['/']);
      return false;
    }),
    catchError(() => {
      router.navigate(['/']);
      return of(false);
    })
  );
};
