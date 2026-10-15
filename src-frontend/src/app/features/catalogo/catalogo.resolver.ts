import { inject } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { Observable, catchError, defer, map, of, switchMap } from 'rxjs';
import { CatalogoService } from './catalogo.service';

/** Peticiones máximas ante errores (una más, como siempre). */
const MAX_INTENTOS_ERROR = 2;
/** Respuestas de una generación ya descartada (cambio de identidad durante la carga) que se toleran. */
const MAX_INTENTOS_DESCARTADO = 3;

/**
 * Espera a que el catálogo esté cargado antes de activar una ruta autenticada, para que los componentes
 * lo lean de forma síncrona (como hoy leen las constantes).
 *
 * Solo devuelve `true` cuando `cargado()` es cierto: si la petición emite pero su generación fue
 * descartada (la identidad cambió mientras volaba, p. ej. recarga de página antes de /auth-status), vuelve
 * a pedir/esperar la petición vigente, hasta `MAX_INTENTOS_DESCARTADO` veces. Un error se reintenta una vez.
 * Si se agotan los intentos NO bloquea la navegación (la ruta se abre con listas vacías, D15), avisa una
 * sola vez por fallo (`notificarFallo`) y devuelve `false`.
 *
 * Registrado en `app.routes.ts` (`resolve.catalogo`) en todas las rutas protegidas por `roleGuard`
 * (los resolvers se ejecutan tras los guards: sin sesión no se pide el catálogo).
 */
export const catalogoResolver: ResolveFn<boolean> = () => {
  const catalogo = inject(CatalogoService);

  const intentar = (errores: number, descartados: number): Observable<boolean> =>
    defer(() => catalogo.cargar()).pipe(
      switchMap(() => {
        if (catalogo.cargado()) {
          return of(true);
        }
        return descartados + 1 < MAX_INTENTOS_DESCARTADO ? intentar(errores, descartados + 1) : of(false);
      }),
      catchError(() => (errores + 1 < MAX_INTENTOS_ERROR ? intentar(errores + 1, descartados) : of(false)))
    );

  return intentar(0, 0).pipe(
    map((listo) => {
      if (!listo) {
        catalogo.notificarFallo();
      }
      return listo;
    })
  );
};
