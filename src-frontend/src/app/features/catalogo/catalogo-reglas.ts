/**
 * Reglas del catálogo: funciones puras, sin Angular, que replican las reglas del backend que el
 * frontend necesita. Se alimentan solo de datos del catálogo (docs/multi-escuela/catalogo-frontend.md §2).
 * Nada de lo que es específico de Taemoi vive aquí: va en `legado-taemoi.ts`.
 */
import { calcularEdad } from '../../utilities/calcular-edad';
import {
  AplicaGrado,
  CatalogoCategoria,
  CatalogoDisciplina,
  CatalogoGrado,
  CatalogoSistemaGrados,
  CatalogoTarifa,
} from './catalogo.models';

/** Edad adulta cuando no hay disciplina (o no define `edadAdulto`): la de Taekwondo, como hasta ahora. */
export const EDAD_ADULTO_SIN_DISCIPLINA = 14;

/** Mayoría de edad legal (tutor en preinscripción). No es dato del catálogo (igual que PreinscripcionService). */
export const EDAD_MAYORIA_LEGAL = 18;

const COLOR_GRIS = '#CCCCCC';
const COLOR_BLANCO = '#FFFFFF';

type FechaNacimiento = string | Date | null | undefined;

// ---------------------------------------------------------------------------------------------
// Disciplinas
// ---------------------------------------------------------------------------------------------

/** Compara por `orden` (nulo = 0) conservando el orden de llegada en los empates. */
function ordenarPorOrden<T extends { orden: number | null }>(items: readonly T[]): T[] {
  return items
    .map((item, indice) => ({ item, indice }))
    .sort((a, b) => (a.item.orden ?? 0) - (b.item.orden ?? 0) || a.indice - b.indice)
    .map(({ item }) => item);
}

/** Disciplinas activas por `orden` (para los desplegables). */
export function disciplinasActivasOrdenadas(disciplinas: readonly CatalogoDisciplina[]): CatalogoDisciplina[] {
  return ordenarPorOrden(disciplinas.filter((d) => d.activa));
}

/** Clase CSS de una disciplina: minúsculas y `_` -> `-` (p. ej. `DEFENSA_PERSONAL_FEMENINA` -> `defensa-personal-femenina`). */
export function claseCssDisciplina(codigo: string | null | undefined): string {
  return (codigo ?? '').toLowerCase().replaceAll('_', '-');
}

// ---------------------------------------------------------------------------------------------
// Edad
// ---------------------------------------------------------------------------------------------

function aIsoSiFecha(fechaNacimiento: string | Date): string {
  // Un Date inválido lanza RangeError (toISOString), como la utilidad actual.
  return fechaNacimiento instanceof Date ? fechaNacimiento.toISOString() : fechaNacimiento;
}

/** Edad cumplida hoy; `null` si no hay fecha o no es válida. Un `Date` inválido lanza `RangeError`. */
export function edadDesde(fechaNacimiento: FechaNacimiento): number | null {
  if (!fechaNacimiento) {
    return null;
  }
  const edad = calcularEdad(aIsoSiFecha(fechaNacimiento));
  return Number.isNaN(edad) ? null : edad;
}

/** Verifica si el alumno cumple `edad` años en el año actual. */
function cumpleEdadEsteAnio(fechaNacimiento: string | Date, edad: number): boolean {
  const fechaNac = fechaNacimiento instanceof Date ? fechaNacimiento : new Date(fechaNacimiento);
  const fechaCumple = new Date(fechaNac);
  fechaCumple.setFullYear(fechaNac.getFullYear() + edad);
  return fechaCumple.getFullYear() === new Date().getFullYear();
}

/**
 * ¿Es menor según la edad adulta de la disciplina? Misma regla que `utilities/categoria-por-edad.ts`:
 * menor si edad < E-1, o edad = E-1 y no cumple E este año. Sin fecha o fecha inválida: menor.
 * Sin disciplina o sin `edadAdulto`: 14 (`EDAD_ADULTO_SIN_DISCIPLINA`).
 */
export function esMenor(
  fechaNacimiento: FechaNacimiento,
  disciplina: Pick<CatalogoDisciplina, 'edadAdulto'> | null | undefined
): boolean {
  const edad = edadDesde(fechaNacimiento);
  if (edad === null) {
    return true;
  }
  const edadAdulto = disciplina?.edadAdulto ?? EDAD_ADULTO_SIN_DISCIPLINA;
  const edadLimiteMenor = edadAdulto - 1;

  if (edad < edadLimiteMenor) {
    return true;
  }
  if (edad === edadLimiteMenor) {
    // Solo es adulto si cumple edadAdulto este año.
    return !cumpleEdadEsteAnio(fechaNacimiento as string | Date, edadAdulto);
  }
  return false;
}

// ---------------------------------------------------------------------------------------------
// Categorías
// ---------------------------------------------------------------------------------------------

/** Categorías de una disciplina (las suyas y las generales, sin disciplina), por `orden`. */
export function categoriasDeDisciplina(
  categorias: readonly CatalogoCategoria[],
  codigoDisciplina: string | null | undefined
): CatalogoCategoria[] {
  return ordenarPorOrden(
    categorias.filter((c) => c.disciplina === null || c.disciplina === (codigoDisciplina ?? undefined))
  );
}

/** Primera categoría (por `orden`) cuyo rango contiene la edad; `null` si ninguna. */
export function categoriaPorEdad(categorias: readonly CatalogoCategoria[], edad: number): CatalogoCategoria | null {
  return (
    ordenarPorOrden(categorias).find(
      (c) => (c.edadMin === null || edad >= c.edadMin) && (c.edadMax === null || edad <= c.edadMax)
    ) ?? null
  );
}

/**
 * Nombre de la categoría para una fecha de nacimiento; `''` si no hay fecha, es inválida o ninguna
 * categoría la contiene (mismos casos límite que `calcularCategoriaPorEdad`).
 */
export function nombreCategoriaPorFecha(
  categorias: readonly CatalogoCategoria[],
  fechaNacimiento: FechaNacimiento
): string {
  const edad = edadDesde(fechaNacimiento);
  if (edad === null) {
    return '';
  }
  return categoriaPorEdad(categorias, edad)?.nombre ?? '';
}

// ---------------------------------------------------------------------------------------------
// Grados
// ---------------------------------------------------------------------------------------------

/** Grados del sistema de la disciplina por `orden`; `[]` si no tiene sistema. */
export function gradosDeDisciplina(
  sistemas: readonly CatalogoSistemaGrados[],
  disciplina: Pick<CatalogoDisciplina, 'sistemaGradosId'> | null | undefined
): CatalogoGrado[] {
  if (disciplina?.sistemaGradosId === null || disciplina?.sistemaGradosId === undefined) {
    return [];
  }
  const sistema = sistemas.find((s) => s.id === disciplina.sistemaGradosId);
  return sistema ? ordenarPorOrden(sistema.grados) : [];
}

/**
 * Grados que se pueden elegir al dar de alta o editar: los del sistema con `aplicaA` distinto de `NINGUNO`.
 * Si se indica `esMenor`, solo los que aplican a ese grupo de edad o a `TODOS`
 * (réplica de `ProgresionGradosService.listarGradosAplicables`).
 */
export function gradosSeleccionables(
  sistemas: readonly CatalogoSistemaGrados[],
  disciplina: Pick<CatalogoDisciplina, 'sistemaGradosId'> | null | undefined,
  esMenorAlumno?: boolean
): CatalogoGrado[] {
  const grados = gradosDeDisciplina(sistemas, disciplina);
  if (esMenorAlumno === undefined) {
    return grados.filter((g) => g.aplicaA !== 'NINGUNO');
  }
  const grupo: AplicaGrado = esMenorAlumno ? 'MENORES' : 'ADULTOS';
  return grados.filter((g) => g.aplicaA === 'TODOS' || g.aplicaA === grupo);
}

/**
 * Siguiente grado: réplica de `ProgresionGradosServiceImpl.siguiente`. Primer grado con `orden` mayor
 * cuyo `aplicaA` es `TODOS` o el del grupo de edad; si el grado actual no es de ese grupo (ni `TODOS`),
 * solo puede seguir uno `TODOS`. `null` si el grado no existe o no hay siguiente.
 */
export function siguienteGrado(
  grados: readonly CatalogoGrado[],
  codigoActual: string | null | undefined,
  esMenorAlumno: boolean
): CatalogoGrado | null {
  if (!codigoActual) {
    return null;
  }
  const ordenados = ordenarPorOrden(grados);
  const actual = ordenados.find((g) => g.codigo === codigoActual);
  if (!actual) {
    return null;
  }
  const grupo: AplicaGrado = esMenorAlumno ? 'MENORES' : 'ADULTOS';
  const actualEsDelGrupo = actual.aplicaA === 'TODOS' || actual.aplicaA === grupo;
  return (
    ordenados.find(
      (g) => g.orden > actual.orden && (g.aplicaA === 'TODOS' || (actualEsDelGrupo && g.aplicaA === grupo))
    ) ?? null
  );
}

// ---------------------------------------------------------------------------------------------
// Colores de grado
// ---------------------------------------------------------------------------------------------

export interface ColoresGrado {
  /** `[color1]` o `[color1, color2]` (primero = mitad inferior, segundo = mitad superior). */
  colors: string[];
  isSplit: boolean;
  needsBorder: boolean;
}

/**
 * Colores de un grado a partir de `color1` (inferior) y `color2` (superior; nulo = un solo color).
 * Misma forma que `getGradoColors`. Sin grado o sin `color1`: gris, como un código desconocido.
 */
export function coloresGrado(grado: Pick<CatalogoGrado, 'color1' | 'color2'> | null | undefined): ColoresGrado {
  if (!grado?.color1) {
    return { colors: [COLOR_GRIS], isSplit: false, needsBorder: false };
  }
  const colors = grado.color2 ? [grado.color1, grado.color2] : [grado.color1];
  return {
    colors,
    isSplit: colors.length === 2,
    needsBorder: colors.some((c) => c.toUpperCase() === COLOR_BLANCO),
  };
}

function esColorClaro(hexColor: string): boolean {
  const hex = hexColor.replace('#', '');
  const r = Number.parseInt(hex.substring(0, 2), 16);
  const g = Number.parseInt(hex.substring(2, 4), 16);
  const b = Number.parseInt(hex.substring(4, 6), 16);
  const luminancia = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminancia > 0.7;
}

/** Estilo CSS de texto con el color del grado. Misma salida que `getGradoTextStyle`. */
export function estiloTextoGrado(grado: Pick<CatalogoGrado, 'color1' | 'color2'> | null | undefined): string {
  const info = coloresGrado(grado);

  if (info.isSplit) {
    const gradiente = `linear-gradient(to bottom, ${info.colors[1]} 50%, ${info.colors[0]} 50%)`;
    let estilo = `background: ${gradiente}; -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; font-weight: bold;`;
    if (info.colors.some((c) => esColorClaro(c)) || info.needsBorder) {
      estilo += ' filter: drop-shadow(0px 1px 1px rgba(0,0,0,0.4)) drop-shadow(0px 0px 2px rgba(0,0,0,0.3));';
    }
    return estilo;
  }

  const color = info.colors[0];
  let estilo = `color: ${color}; font-weight: bold;`;
  if (esColorClaro(color) || info.needsBorder) {
    estilo += ' text-shadow: 0px 1px 2px rgba(0,0,0,0.5), 0px 0px 3px rgba(0,0,0,0.3), 0px 0px 5px rgba(0,0,0,0.2);';
  }
  return estilo;
}

// ---------------------------------------------------------------------------------------------
// Tarifas
// ---------------------------------------------------------------------------------------------

/**
 * Tarifas que admite la disciplina (las suyas y las de cualquier disciplina), por `orden`.
 * Por defecto solo las activas.
 */
export function tarifasDeDisciplina(
  tarifas: readonly CatalogoTarifa[],
  codigoDisciplina: string | null | undefined,
  incluirInactivas = false
): CatalogoTarifa[] {
  return ordenarPorOrden(
    tarifas.filter(
      (t) =>
        (incluirInactivas || t.activa) && (t.disciplina === null || t.disciplina === (codigoDisciplina ?? undefined))
    )
  );
}

/**
 * Cuantía estándar de una tarifa según el rol familiar: réplica de `TarifasServiceImpl.calcularImporte`.
 * PADRE/MADRE -> `importeAdulto` y HIJO/HIJA -> `importeMenor` si están definidos; en otro caso `importe`.
 * Tarifa desconocida (nula): 0, como `obtenerCuantiaTarifaEstandar`.
 */
export function cuantiaEstandar(tarifa: CatalogoTarifa | null | undefined, rolFamiliar?: string | null): number {
  if (!tarifa) {
    return 0;
  }
  if ((rolFamiliar === 'PADRE' || rolFamiliar === 'MADRE') && tarifa.importeAdulto !== null) {
    return tarifa.importeAdulto;
  }
  if ((rolFamiliar === 'HIJO' || rolFamiliar === 'HIJA') && tarifa.importeMenor !== null) {
    return tarifa.importeMenor;
  }
  return tarifa.importe;
}
