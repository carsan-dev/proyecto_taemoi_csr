import { inject } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { catchError, defer, map, of, retry } from 'rxjs';
import { CatalogoService } from './catalogo.service';

/**
 * Espera a que el catálogo esté cargado antes de activar una ruta autenticada, para que los componentes
 * lo lean de forma síncrona (como hoy leen las constantes).
 *
 * Reintenta una vez. Si sigue fallando NO bloquea la navegación (la ruta se abre con el catálogo en
 * estado `error` y listas vacías, D15) y devuelve `false`; devuelve `true` si el catálogo está listo.
 *
 * Sin registrar en `app.routes.ts` todavía: se añade a las rutas autenticadas (admin, manager, usuario)
 * cuando se migren los primeros consumidores (rebanadas siguientes).
 */
export const catalogoResolver: ResolveFn<boolean> = () => {
  const catalogo = inject(CatalogoService);
  return defer(() => catalogo.cargar()).pipe(
    retry({ count: 1 }),
    map(() => true),
    catchError(() => of(false))
  );
};
