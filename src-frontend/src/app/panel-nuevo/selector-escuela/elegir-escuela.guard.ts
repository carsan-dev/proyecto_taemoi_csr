import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of, switchMap, take } from 'rxjs';

import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { SelectorEscuelaService, debeElegirTrasEntrar } from './selector-escuela.service';

/**
 * «Tras entrar» solo se abre si toca elegir ({@link debeElegirTrasEntrar}: dos escuelas o más y alguna que no sea solo
 * de familia). Si no, o si no se pueden leer las escuelas, al destino de siempre sin activar la pantalla. Va detrás de
 * `roleGuard` (sin sesión no se pide nada).
 */
export const elegirEscuelaGuard: CanActivateFn = () => {
  const auth = inject(AuthenticationService);
  const router = inject(Router);
  const destinoDeSiempre = () =>
    auth.resolverRutaInicio().pipe(
      take(1),
      map((ruta) => router.createUrlTree([ruta])),
      catchError(() => of(router.createUrlTree(['/'])))
    );
  return inject(SelectorEscuelaService)
    .organizaciones()
    .pipe(
      take(1),
      map((escuelas) => debeElegirTrasEntrar(escuelas)),
      catchError(() => of(false)),
      switchMap((toca) => (toca ? of(true) : destinoDeSiempre()))
    );
};
