/**
 * El tema de la escuela tal como se pinta bajo `.pn` (8b-T y 8b-C). TypeScript puro y sin Angular: lo comparten
 * `MarcaEscuelaPanelService` y `acento-escuela.directive.ts` sin importarse entre sí.
 */
import { AcentoEscuela } from './acento-escuela';

/** Colores del panel de la escuela (`TemaEscuela` del servidor, `#RRGGBB`). */
export interface TemaMarca {
  primario: string;
  sobrePrimario: string;
  sobrePrimario2: string;
  primarioTinta: string;
  primarioSuave: string;
  /** El primario se oscureció para que el texto blanco llegue a 4,5:1. */
  ajustado: boolean;
}

/** Lo que la cabecera aplicó: el tema de la API y el acento calculado con él. */
export interface TemaAplicado {
  tema: TemaMarca;
  acento: AcentoEscuela;
}

/** Variable de `panel-nuevo.scss` (bajo `.pn`) que sustituye cada color del tema. */
export const VARIABLES_MARCA: Record<keyof Omit<TemaMarca, 'ajustado'>, string> = {
  primario: '--pn-marca-primario',
  sobrePrimario: '--pn-marca-sobre-primario',
  sobrePrimario2: '--pn-marca-sobre-primario-2',
  primarioTinta: '--pn-marca-primario-tinta',
  primarioSuave: '--pn-marca-primario-suave',
};

/** Variables del acento (8b-C, decisión 273) bajo `.pn`. */
export const VARIABLES_ACENTO: Record<keyof AcentoEscuela, string> = {
  acento: '--pn-acento',
  acentoSuave: '--pn-acento-suave',
  acentoBorde: '--pn-acento-borde',
};

/** Las ocho variables (cinco de la marca y tres del acento) con su valor; vacío sin tema aplicado. */
export function variablesDelTema(aplicado: TemaAplicado | null): Record<string, string> {
  const variables: Record<string, string> = {};
  if (!aplicado) {
    return variables;
  }
  for (const [clave, variable] of Object.entries(VARIABLES_MARCA) as [keyof typeof VARIABLES_MARCA, string][]) {
    variables[variable] = aplicado.tema[clave];
  }
  for (const [clave, variable] of Object.entries(VARIABLES_ACENTO) as [keyof AcentoEscuela, string][]) {
    variables[variable] = aplicado.acento[clave];
  }
  return variables;
}

/** Todas las variables que escribe el tema, para quitarlas. */
export const TODAS_LAS_VARIABLES: string[] = [...Object.values(VARIABLES_MARCA), ...Object.values(VARIABLES_ACENTO)];
