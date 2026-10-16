/**
 * ÚNICO lugar con comportamiento específico de Taemoi mantenido por decisión del propietario
 * (docs/multi-escuela/catalogo-frontend.md §5). Todo es puro y solo actúa si el flag
 * `organizacion.comportamientoLegadoTaemoi` es `true`; el resto de escuelas recibe el valor neutro.
 * Los literales de disciplina de Taemoi no deben aparecer fuera de este archivo.
 */
import { calcularEdad } from '../../utilities/calcular-edad';
import { Catalogo, CatalogoCategoria, CatalogoDisciplina, CatalogoOrganizacion } from './catalogo.models';
import { categoriasDeDisciplina } from './catalogo-reglas';

/** ¿Aplica el comportamiento legado de Taemoi? Sin organización (catálogo no cargado): no. */
export function esLegadoTaemoi(
  organizacion: Pick<CatalogoOrganizacion, 'comportamientoLegadoTaemoi'> | null | undefined
): boolean {
  return organizacion?.comportamientoLegadoTaemoi === true;
}

/** Atajo sobre un catálogo (o su ausencia). */
export function catalogoEsLegadoTaemoi(catalogo: Pick<Catalogo, 'organizacion'> | null | undefined): boolean {
  return esLegadoTaemoi(catalogo?.organizacion);
}

// ---------------------------------------------------------------------------------------------
// D6: categorías de las disciplinas de competición sin categorías propias
// ---------------------------------------------------------------------------------------------

/**
 * D6. Hoy los competidores de Kickboxing reciben las categorías de Taekwondo (el frontend las daba por
 * "las mismas para todos los deportes"). Con el flag, una disciplina que permite competición y no tiene
 * categorías propias usa las de TAEKWONDO. Sin el flag solo cuentan las categorías propias (o generales).
 */
export function categoriasParaDisciplina(
  legado: boolean,
  categorias: readonly CatalogoCategoria[],
  disciplina: Pick<CatalogoDisciplina, 'codigo' | 'permiteCompeticion'> | null | undefined
): CatalogoCategoria[] {
  if (!disciplina) {
    return [];
  }
  const propias = categoriasDeDisciplina(categorias, disciplina.codigo);
  if (propias.length > 0 || !legado || !disciplina.permiteCompeticion) {
    return propias;
  }
  return categoriasDeDisciplina(categorias, 'TAEKWONDO');
}

// ---------------------------------------------------------------------------------------------
// D9: tarifa inicial en preinscripciones-admin
// ---------------------------------------------------------------------------------------------

/**
 * D9. Taemoi preselecciona la tarifa de una preinscripción: Taekwondo por edad legal (menor de 18 ->
 * `INFANTIL`, si no `ADULTO`; fecha ausente o inválida: `INFANTIL`); el resto de disciplinas, la primera
 * tarifa de la disciplina en orden del catálogo (`tarifasDeLaDisciplina`, p. ej. `tarifasDeDisciplina`).
 * Sin el flag: sin preselección (`null`).
 */
export function tarifaInicialPreinscripcion(
  legado: boolean,
  codigoDisciplina: string | null | undefined,
  fechaNacimiento: string | Date | null | undefined,
  tarifasDeLaDisciplina: readonly { codigo: string }[] = []
): string | null {
  if (!legado) {
    return null;
  }
  if (codigoDisciplina !== 'TAEKWONDO') {
    return tarifasDeLaDisciplina[0]?.codigo ?? null;
  }
  if (!fechaNacimiento) {
    return 'INFANTIL';
  }
  // 'yyyy-MM-dd' (diaApi): edad por año/mes/día; new Date('yyyy-MM-dd') es medianoche UTC y en husos
  // negativos daría el día anterior.
  const dia = typeof fechaNacimiento === 'string' ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(fechaNacimiento.trim()) : null;
  if (dia) {
    const [anio, mes, diaMes] = dia.slice(1).map(Number);
    const hoy = new Date();
    let edad = hoy.getFullYear() - anio;
    if (hoy.getMonth() + 1 < mes || (hoy.getMonth() + 1 === mes && hoy.getDate() < diaMes)) edad--;
    return edad < 18 ? 'INFANTIL' : 'ADULTO';
  }
  const nacimiento = new Date(fechaNacimiento);
  if (Number.isNaN(nacimiento.getTime())) {
    return 'INFANTIL';
  }
  return calcularEdad(nacimiento.toISOString()) < 18 ? 'INFANTIL' : 'ADULTO';
}

// ---------------------------------------------------------------------------------------------
// D10: «derecho a examen» solo en Kickboxing / «recompensa» solo en el resto
// ---------------------------------------------------------------------------------------------

/** D10. Botón «Añadir derecho de examen»: solo Kickboxing de Taemoi. */
export function ofreceDerechoExamenManual(legado: boolean, codigoDisciplina: string | null | undefined): boolean {
  return legado && codigoDisciplina === 'KICKBOXING';
}

/** D10. Botón «Pasar de grado por recompensa»: Taemoi, todo menos Kickboxing; otras escuelas, siempre. */
export function ofreceRecompensa(legado: boolean, codigoDisciplina: string | null | undefined): boolean {
  return !ofreceDerechoExamenManual(legado, codigoDisciplina);
}

// ---------------------------------------------------------------------------------------------
// D11: cinturón rojo bordado
// ---------------------------------------------------------------------------------------------

/** Código del grado cuyo siguiente dispara el aviso del cinturón rojo bordado (producto de Taemoi). */
const GRADO_ROJO = 'ROJO';

/** D11. Aviso del cinturón rojo bordado: solo con legado y cuando el siguiente grado es `ROJO`. */
export function requiereAvisoRojoBordado(legado: boolean, codigoSiguienteGrado: string | null | undefined): boolean {
  return legado && codigoSiguienteGrado === GRADO_ROJO;
}

// ---------------------------------------------------------------------------------------------
// D12: emoji y color por disciplina en el panel de administración
// ---------------------------------------------------------------------------------------------

export const EMOJI_DISCIPLINA_NEUTRO = '⭐';
export const COLOR_DISCIPLINA_NEUTRO = '#6c757d';

const EMOJI_LEGADO: Readonly<Record<string, string>> = {
  TAEKWONDO: '🥋',
  KICKBOXING: '🥊',
  PILATES: '🧘',
  DEFENSA_PERSONAL_FEMENINA: '🛡️',
};

const COLOR_LEGADO: Readonly<Record<string, string>> = {
  TAEKWONDO: '#4a90d9',
  KICKBOXING: '#e74c3c',
  PILATES: '#50c878',
  DEFENSA_PERSONAL_FEMENINA: '#f5a623',
};

/** D12. Emoji de la disciplina: el de Taemoi por código con el flag; si no, neutro. */
export function emojiDisciplina(legado: boolean, codigoDisciplina: string | null | undefined): string {
  return (legado && codigoDisciplina && Object.hasOwn(EMOJI_LEGADO, codigoDisciplina) ? EMOJI_LEGADO[codigoDisciplina] : undefined) ?? EMOJI_DISCIPLINA_NEUTRO;
}

/** D12. Color de la disciplina: el de Taemoi por código con el flag; si no, neutro. */
export function colorDisciplina(legado: boolean, codigoDisciplina: string | null | undefined): string {
  return (legado && codigoDisciplina && Object.hasOwn(COLOR_LEGADO, codigoDisciplina) ? COLOR_LEGADO[codigoDisciplina] : undefined) ?? COLOR_DISCIPLINA_NEUTRO;
}

// ---------------------------------------------------------------------------------------------
// Horarios (página pública): etiqueta de color del turno -> disciplina
// ---------------------------------------------------------------------------------------------

const DISCIPLINA_POR_ETIQUETA_HORARIO: Readonly<Record<string, string>> = {
  taekwondo: 'TAEKWONDO',
  kickboxing: 'KICKBOXING',
  pilates: 'PILATES',
  defensa: 'DEFENSA_PERSONAL_FEMENINA',
};

/**
 * Disciplina de una etiqueta de color de la página pública de horarios (`taekwondo`, `kickboxing`, `pilates`,
 * `defensa`). Sin campo en el catálogo. No depende del flag: la página es pública (sin catálogo autenticado) y
 * la propia página decide la etiqueta buscando esas palabras en la categoría del turno. `null` si no hay
 * correspondencia (p. ej. `competicion`): el llamador usa la primera disciplina.
 */
export function disciplinaDeEtiquetaHorario(etiqueta: string | null | undefined): string | null {
  return etiqueta && Object.hasOwn(DISCIPLINA_POR_ETIQUETA_HORARIO, etiqueta)
    ? DISCIPLINA_POR_ETIQUETA_HORARIO[etiqueta]
    : null;
}
