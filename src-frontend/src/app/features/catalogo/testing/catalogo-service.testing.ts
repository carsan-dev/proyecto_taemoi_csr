import { HttpClient } from '@angular/common/http';
import { Provider } from '@angular/core';
import { of } from 'rxjs';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { CatalogoService } from '../catalogo.service';
import { Catalogo } from '../catalogo.models';

/**
 * `CatalogoService` real (mismas reglas, mismo puente) ya cargado con `catalogo`, sin HTTP ni sesión.
 * Debe llamarse en un contexto de inyección (p. ej. `TestBed.runInInjectionContext` o un `useFactory`).
 * Con `null` queda sin cargar (estado `vacio`).
 */
export function crearCatalogoServiceTesting(catalogo: Catalogo | null): CatalogoService {
  const http = { get: () => of(catalogo) } as unknown as HttpClient;
  const auth = {
    usuarioLogueadoCambio: of(false),
    emailCambio: of(null),
  } as unknown as AuthenticationService;
  const servicio = new CatalogoService(http, auth);
  if (catalogo) {
    servicio.cargar().subscribe();
  }
  return servicio;
}

/** Proveedor de pruebas: `CatalogoService` cargado con `catalogo`. */
export function proveerCatalogoTesting(catalogo: Catalogo | null): Provider {
  return { provide: CatalogoService, useFactory: () => crearCatalogoServiceTesting(catalogo) };
}
