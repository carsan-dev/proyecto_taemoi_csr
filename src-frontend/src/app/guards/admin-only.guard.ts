import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthenticationService } from '../servicios/authentication/authentication.service';

export const adminOnlyGuard: CanActivateFn = () => {
  const authService = inject(AuthenticationService);
  const router = inject(Router);

  return authService.resolverRolesDisponibles().pipe(
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
