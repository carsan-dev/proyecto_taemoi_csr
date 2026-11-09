import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { CatalogoService } from '../features/catalogo/catalogo.service';
import { catalogoEsLegadoTaemoi } from '../features/catalogo/legado-taemoi';

/**
 * Pantallas de la web por plantilla (W7, «Web · Tema»): Moiskimdo (`comportamientoLegadoTaemoi`) tiene web propia y no
 * entra (fase4-web-plan.md §9 n.º 8). Espera al catálogo (los guards van antes que `catalogoResolver`) y, si es la
 * organización legado o el catálogo no se puede leer, redirige a la portada como `adminOnlyGuard`: falla cerrado.
 */
export const sinLegadoTaemoiGuard: CanActivateFn = () => {
  const catalogo = inject(CatalogoService);
  const router = inject(Router);

  return catalogo.cargar().pipe(
    map((datos) => {
      if (!catalogoEsLegadoTaemoi(datos)) {
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
