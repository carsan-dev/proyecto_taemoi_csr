import { CanActivateFn, Router } from '@angular/router';
import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { inject } from '@angular/core';
import { map } from 'rxjs/operators';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthenticationService);
  const router = inject(Router);

  return authService.rolesCambio.pipe(
    map((roles) => {
      if (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')) {
        return true;
      } else {
        router.navigate(['/inicio']);
        return false;
      }
    })
  );
};
