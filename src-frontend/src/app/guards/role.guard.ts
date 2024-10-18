import { CanActivateFn, Router } from '@angular/router';
import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { inject } from '@angular/core';
import { map } from 'rxjs/internal/operators/map';
import { catchError } from 'rxjs/internal/operators/catchError';
import { of } from 'rxjs/internal/observable/of';
import { take } from 'rxjs/internal/operators/take';
import { switchMap } from 'rxjs/internal/operators/switchMap';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthenticationService);
  const router = inject(Router);

  if (!authService.rolesEstanCargados()) {
    // Si los roles aún no están cargados, los obtenemos y verificamos
    return authService.obtenerRoles().pipe(
      switchMap((roles) => {
        if (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')) {
          return of(true); // Si tiene el rol adecuado, permitimos el acceso
        } else {
          router.navigate(['/inicio']);
          return of(false); // Si no tiene el rol adecuado, bloqueamos
        }
      }),
      catchError(() => {
        router.navigate(['/inicio']);
        return of(false); // En caso de error, redirigimos al inicio
      })
    );
  } else {
    // Si los roles ya están cargados, verificamos directamente
    return authService.rolesCambio.pipe(
      take(1),
      map((roles) => {
        if (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')) {
          return true; // Permitimos acceso si tiene el rol adecuado
        } else {
          router.navigate(['/inicio']);
          return false; // Bloqueamos si no tiene el rol
        }
      }),
      catchError(() => {
        router.navigate(['/inicio']);
        return of(false); // En caso de error, redirigimos al inicio
      })
    );
  }
};
