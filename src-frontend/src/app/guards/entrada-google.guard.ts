import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { AuthenticationService } from '../servicios/authentication/authentication.service';

/**
 * Entrada tras iniciar sesión con Google (decisión 384, ruta `login/google`): el servidor ya dejó la sesión en la
 * cookie y manda aquí a las sesiones cuyo destino depende de la vista preferida o de las capacidades (gestión +
 * familia, profesor + familia, responsable de sede, solo profesor, plataforma). Redirige al mismo destino que el login con contraseña
 * (`resolverRutaInicio`), sin pantalla propia y sin leer nada de la URL. Si no puede resolverlo, a la portada.
 */
export const entradaGoogleGuard: CanActivateFn = () => {
  const auth = inject(AuthenticationService);
  const router = inject(Router);

  return auth.resolverRolesDisponibles().pipe(
    switchMap((roles) => auth.resolverRutaInicio(roles)),
    map((ruta) => router.createUrlTree([ruta])),
    catchError(() => of(router.createUrlTree(['/'])))
  );
};
