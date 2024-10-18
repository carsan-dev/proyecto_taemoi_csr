import { CanActivateFn, Router } from '@angular/router';
import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { inject } from '@angular/core';
import { map } from 'rxjs/internal/operators/map';
import { catchError } from 'rxjs/internal/operators/catchError';
import { of } from 'rxjs/internal/observable/of';
import { take } from 'rxjs/internal/operators/take';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthenticationService);
  const router = inject(Router);

  // Verificamos si los roles ya están cargados para evitar una llamada innecesaria
  if (authService.rolesEstanCargados()) {
    // Si ya están cargados, verificamos directamente
    return authService.rolesCambio.pipe(
      take(1),
      map((roles) => {
        if (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')) {
          return true; // Permitimos acceso
        } else {
          router.navigate(['/inicio']);
          return false; // Redirigimos al inicio si no tiene permisos
        }
      }),
      catchError(() => {
        router.navigate(['/inicio']);
        return of(false); // En caso de error, redirigimos al inicio
      })
    );
  } else {
    // Si los roles no están cargados, los obtenemos primero
    return authService.obtenerRoles().pipe(
      map((roles) => {
        if (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')) {
          return true; // Permitimos acceso si tiene el rol adecuado
        } else {
          router.navigate(['/inicio']);
          return false; // Redirigimos si no tiene permisos
        }
      }),
      catchError(() => {
        router.navigate(['/inicio']);
        return of(false); // Redirigimos al inicio en caso de error
      })
    );
  }
};