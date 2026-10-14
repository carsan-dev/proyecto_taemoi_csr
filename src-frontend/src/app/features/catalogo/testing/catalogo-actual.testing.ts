import { registrarLectorCatalogo } from '../catalogo-actual';
import { Catalogo } from '../catalogo.models';

/** Fija el catálogo que ven las utilidades (specs sin TestBed). Llamar a `restablecerCatalogoActual` en `afterEach`. */
export function fijarCatalogoActual(catalogo: Catalogo | null): void {
  registrarLectorCatalogo(() => catalogo);
}

export function restablecerCatalogoActual(): void {
  registrarLectorCatalogo(null);
}
