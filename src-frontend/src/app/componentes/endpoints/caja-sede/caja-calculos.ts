import { CobroCaja, TipoMovimientoCaja } from '../../../interfaces/caja';

/** Importes en euros con el formato de España: «1.240,00 €». */
const FORMATO_EUROS = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  useGrouping: true,
});

export function formatearEuros(importe: number | null | undefined): string {
  const valor = Number(importe ?? 0);
  // es-ES no agrupa los miles de 4 cifras con `useGrouping: true` en todos los motores ("1240,00 €"); se fuerza.
  const texto = FORMATO_EUROS.format(Number.isFinite(valor) ? valor : 0);
  return texto.replace(/(\d)(\d{3})(,\d{2})/, (_c, a: string, b: string, c: string) => `${a}.${b}${c}`);
}

/**
 * Importe de un movimiento con signo explícito («+50,00 €», «−1,00 €» con signo menos real, «0,00 €»): el sentido no
 * depende solo del color.
 */
export function importeConSignoTexto(importe: number | null | undefined): string {
  const cent = centimos(Number(importe ?? 0) || 0);
  const absoluto = formatearEuros(Math.abs(cent) / 100);
  return cent > 0 ? `+${absoluto}` : cent < 0 ? `−${absoluto}` : absoluto;
}

/** Clase de color por signo: verde si entra, rojo si sale, neutra si es cero. */
export function claseImporte(importe: number | null | undefined): '' | 'importe-positivo' | 'importe-negativo' {
  const cent = centimos(Number(importe ?? 0) || 0);
  return cent > 0 ? 'importe-positivo' : cent < 0 ? 'importe-negativo' : '';
}

/** Céntimos enteros de un importe (evita sumar con coma flotante). */
export function centimos(importe: number): number {
  return Math.round(Number(importe) * 100);
}

/**
 * Importe escrito por el usuario («12,5», «12.50», «1.240,00»): número con como mucho dos decimales y no negativo, o
 * `null` si no es válido.
 */
export function leerImporte(texto: string | number | null | undefined): number | null {
  if (texto === null || texto === undefined) {
    return null;
  }
  let limpio = String(texto).trim().replace(/\s|€/g, '');
  if (!limpio) {
    return null;
  }
  if (limpio.includes(',')) {
    limpio = limpio.replace(/\./g, '').replace(',', '.');
  }
  if (!/^\d+(\.\d{1,2})?$/.test(limpio)) {
    return null;
  }
  return Number(limpio);
}

/** Texto de un importe para un campo editable («45,00»). */
export function importeParaCampo(importe: number): string {
  return (centimos(importe) / 100).toFixed(2).replace('.', ',');
}

export type EstadoCuadre = 'cuadra' | 'falta' | 'sobra';

export interface CalculoEntrega {
  /** Σ de los cobros marcados: lo que debería entregar. */
  declarado: number;
  /** Lo contado; `null` si el campo no es un importe válido. */
  contado: number | null;
  /** contado − declarado (0 si el contado no es válido). */
  diferencia: number;
  estado: EstadoCuadre;
  /** La nota es obligatoria si lo contado no coincide con lo declarado (regla de la API). */
  notaObligatoria: boolean;
}

/** Cuentas del diálogo «Recibir entrega»: lo declarado, lo contado y la diferencia, en céntimos exactos. */
export function calcularEntrega(cobros: CobroCaja[], seleccionados: ReadonlySet<number>, contadoTexto: string): CalculoEntrega {
  const declaradoCent = cobros
    .filter((c) => seleccionados.has(c.cobroId))
    .reduce((suma, c) => suma + centimos(c.importe), 0);
  const contado = leerImporte(contadoTexto);
  const contadoValido = contado !== null && contado >= 0 ? contado : null;
  const diferenciaCent = contadoValido === null ? 0 : centimos(contadoValido) - declaradoCent;
  return {
    declarado: declaradoCent / 100,
    contado: contadoValido,
    diferencia: diferenciaCent / 100,
    estado: diferenciaCent === 0 ? 'cuadra' : diferenciaCent < 0 ? 'falta' : 'sobra',
    notaObligatoria: diferenciaCent !== 0,
  };
}

export type EstadoSeleccion = 'todos' | 'ninguno' | 'algunos';

/** Estado de la casilla «seleccionar todos»: marcada, desmarcada o indeterminada. */
export function estadoSeleccion(total: number, marcados: number): EstadoSeleccion {
  if (total <= 0 || marcados <= 0) {
    return 'ninguno';
  }
  return marcados >= total ? 'todos' : 'algunos';
}

/** Días naturales entre una fecha y hoy (locales). */
export function diasDesde(fecha: string | Date | null | undefined, hoy: Date = new Date()): number | null {
  if (!fecha) {
    return null;
  }
  const d = new Date(fecha);
  if (Number.isNaN(d.getTime())) {
    return null;
  }
  const inicio = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const fin = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate()).getTime();
  return Math.max(0, Math.round((fin - inicio) / 86_400_000));
}

/** «hoy», «ayer», «hace 9 días». */
export function textoRelativo(dias: number | null): string {
  if (dias === null) {
    return '—';
  }
  if (dias === 0) {
    return 'hoy';
  }
  if (dias === 1) {
    return 'ayer';
  }
  return `hace ${dias} días`;
}

/** Más de una semana con el dinero en mano: se avisa. */
export const DIAS_AVISO_EFECTIVO = 7;

export const TIPOS_MOVIMIENTO: ReadonlyArray<{ valor: TipoMovimientoCaja; etiqueta: string }> = [
  { valor: 'DEVOLUCION', etiqueta: 'Devolución' },
  { valor: 'ERROR', etiqueta: 'Corrección de error' },
  { valor: 'SOBRANTE', etiqueta: 'Sobrante' },
  { valor: 'FALTANTE', etiqueta: 'Faltante' },
  { valor: 'OTRO', etiqueta: 'Otro' },
];

export function etiquetaTipoMovimiento(tipo: TipoMovimientoCaja | string): string {
  return TIPOS_MOVIMIENTO.find((t) => t.valor === tipo)?.etiqueta ?? String(tipo);
}

/**
 * Reglas de la API por tipo (TipoMovimientoCaja): signo fijo (devolución y faltante salen, sobrante entra; error y
 * otro, cualquier signo) y alumno (obligatorio en devolución, opcional en error y otro, sin alumno en sobrante y
 * faltante).
 */
export interface ReglaMovimiento {
  signo: -1 | 1 | null;
  alumno: 'obligatorio' | 'opcional' | 'oculto';
}

export function reglaMovimiento(tipo: TipoMovimientoCaja): ReglaMovimiento {
  switch (tipo) {
    case 'DEVOLUCION':
      return { signo: -1, alumno: 'obligatorio' };
    case 'FALTANTE':
      return { signo: -1, alumno: 'oculto' };
    case 'SOBRANTE':
      return { signo: 1, alumno: 'oculto' };
    default:
      return { signo: null, alumno: 'opcional' };
  }
}

/**
 * Importe con signo para la API: con signo fijo se toma el valor absoluto escrito y se le pone el del tipo; en error y
 * otro, el que escribió el usuario. `null` si no es válido o es cero.
 */
export function importeConSigno(tipo: TipoMovimientoCaja, texto: string): number | null {
  const escrito = String(texto ?? '').trim();
  // Como mucho UN signo delante («-5», «+5»); «--5», «+-5» o «5-» no son importes válidos.
  const signo = escrito.startsWith('-') || escrito.startsWith('+') || escrito.startsWith('\u2212') ? escrito[0] : '';
  const negativo = signo === '-' || signo === '\u2212';
  const valor = leerImporte(escrito.slice(signo.length));
  if (valor === null || valor === 0) {
    return null;
  }
  const regla = reglaMovimiento(tipo);
  if (regla.signo !== null) {
    return regla.signo * valor;
  }
  return negativo ? -valor : valor;
}
