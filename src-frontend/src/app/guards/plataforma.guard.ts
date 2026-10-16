import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, take } from 'rxjs';

import { AuthenticationService } from '../servicios/authentication/authentication.service';

/**
 * Rutas de plataforma. Usa el sondeo de plataforma, no /api/auth/roles: una sesión solo de plataforma recibe 403 ahí.
 * (El sondeo vive en AuthenticationService para no sumar PlataformaService al bundle inicial.)
 */
export const plataformaGuard: CanActivateFn = () => {
  const auth = inject(AuthenticationService);
  const router = inject(Router);

  return auth.esAdminPlataforma().pipe(
    take(1),
    map((esAdmin) => {
      if (esAdmin) {
        return true;
      }
      router.navigate(['/']);
      return false;
    })
  );
};
