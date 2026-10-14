/**
 * Puente síncrono al catálogo cargado para las utilidades que son funciones sueltas (sin inyección):
 * `utilities/*`, `constants/tarifa.constants.ts`, `enums/deporte.ts`.
 *
 * `CatalogoService` se registra aquí al crearse. Sin catálogo (no cargado o error) todo devuelve vacío:
 * D15, nunca constantes de Taemoi. Los componentes nuevos deben inyectar `CatalogoService`, no usar esto.
 */
import { Catalogo, CatalogoDisciplina } from './catalogo.models';
import { disciplinasActivasOrdenadas } from './catalogo-reglas';
import { catalogoEsLegadoTaemoi } from './legado-taemoi';

type LectorCatalogo = () => Catalogo | null;

let lector: LectorCatalogo | null = null;

/** Registra la fuente del catálogo vigente (la llama `CatalogoService`; en pruebas, `testing/catalogo-actual.testing.ts`). */
export function registrarLectorCatalogo(nuevoLector: LectorCatalogo | null): void {
  lector = nuevoLector;
}

/** Retira el lector si sigue siendo el vigente (lo llama `CatalogoService.ngOnDestroy`; evita fugas entre pruebas). */
export function retirarLectorCatalogo(antiguo: LectorCatalogo): void {
  if (lector === antiguo) {
    lector = null;
  }
}

/** Catálogo cargado, o `null`. Dentro de un `computed`/plantilla es reactivo (el lector lee un signal). */
export function catalogoActual(): Catalogo | null {
  return lector?.() ?? null;
}

export function legadoTaemoiActual(): boolean {
  return catalogoEsLegadoTaemoi(catalogoActual());
}

/** Disciplina por código; sin código, la primera activa por `orden` (sustituye al antiguo valor por defecto 'TAEKWONDO'). */
export function disciplinaActual(codigo: string | null | undefined): CatalogoDisciplina | undefined {
  const catalogo = catalogoActual();
  if (!catalogo) {
    return undefined;
  }
  return codigo
    ? catalogo.disciplinas.find((d) => d.codigo === codigo)
    : disciplinasActivasOrdenadas(catalogo.disciplinas)[0];
}
