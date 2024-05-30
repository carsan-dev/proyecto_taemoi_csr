import { CanActivateFn, Router } from '@angular/router';
import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { inject } from '@angular/core';
import { catchError, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthenticationService);
  const router = inject(Router);

  return authService.getRoles().pipe(
    switchMap((roles) => {
      if (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')) {
        return of(true);
      } else {
        router.navigate(['/inicio']);
        return of(false);
      }
    }),
    catchError(() => {
      router.navigate(['/inicio']);
      return of(false);
    })
  );
};
