import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

import { agruparMiles, plural } from '../formato/formato-es';

/**
 * Funciones puras de Productos del panel nuevo (8b-3g): tipos y grupos, orden de cada grupo, chips de filtros, el
 * precio escrito en es-ES y el cuerpo que se envía (el MISMO de hoy: concepto, precio y vendibleProfesor).
 */

export type TipoProducto = 'MENSUALIDAD' | 'LICENCIA' | 'RESERVA_PLAZA' | 'COMPETIDOR' | 'EXAMEN' | 'RECOMPENSA' | 'OTRO';

/** Los siete tipos de la API (`TipoProducto`), en el orden de los grupos; `etiqueta` es la del filtro «Tipo» de hoy. */
export const TIPOS_PRODUCTO: ReadonlyArray<{ valor: TipoProducto; etiqueta: string; grupo: string }> = [
  { valor: 'MENSUALIDAD', etiqueta: 'Mensualidad', grupo: 'Mensualidades' },
  { valor: 'LICENCIA', etiqueta: 'Licencia', grupo: 'Licencias' },
  { valor: 'RESERVA_PLAZA', etiqueta: 'Reserva de plaza', grupo: 'Reservas de plaza' },
  { valor: 'COMPETIDOR', etiqueta: 'Competidor', grupo: 'Competidor' },
  { valor: 'EXAMEN', etiqueta: 'Examen', grupo: 'Derechos de examen' },
  { valor: 'RECOMPENSA', etiqueta: 'Recompensa', grupo: 'Recompensas' },
  { valor: 'OTRO', etiqueta: 'Otro', grupo: 'Otros productos' },
];

/** Tamaño de cada página leída: un grupo se lee entero, página a página, sin enseñar páginas. */
export const TAMANO_LECTURA = 200;

/** Lo que la pantalla usa de cada producto (el JSON de `GET /productos`; el tipo no viaja: sale de la lectura por tipo). */
export interface ProductoPanel {
  id: number;
  concepto: string;
  precio: number | null;
  vendibleProfesor?: boolean;
}

// ------------------------------------------------------------------ importe escrito

export type LecturaImporte =
  | { estado: 'vacio' }
  | { estado: 'valido'; valor: number }
  | { estado: 'formato' }
  | { estado: 'ambiguo' };

const VACIO: LecturaImporte = { estado: 'vacio' };
const FORMATO: LecturaImporte = { estado: 'formato' };
const AMBIGUO: LecturaImporte = { estado: 'ambiguo' };

/** Espacios por fuera y un «€» final (con o sin el espacio, también el duro de Intl). */
function limpiar(texto: string): string {
  return texto.trim().replace(/[\s  ]*€$/, '');
}

/**
 * Lee el precio escrito. Acepta el formato es-ES («25», «25,5», «1.010,50», «1.234.567») y el punto decimal del campo de
 * hoy («1010.50», «1.5»: sigue siendo 1,5 y no 15). Un solo punto seguido de tres cifras («1.234») es ambiguo (miles en
 * es-ES, decimales en el campo de hoy) y no se adivina. El número es el mismo `Number` que hoy daba el campo. El signo
 * menos se lee: lo rechaza después la validación de hoy (mayor o igual a 0).
 */
export function leerImporte(texto: string | null | undefined): LecturaImporte {
  if (texto === null || texto === undefined) {
    return VACIO;
  }
  let s = limpiar(String(texto));
  if (s === '') {
    return VACIO;
  }
  const negativo = s.startsWith('-');
  if (negativo) {
    s = s.slice(1);
  }
  let entero: string;
  let decimales = '';
  let m: RegExpExecArray | null;
  if ((m = /^(\d+)$/.exec(s))) {
    entero = m[1];
  } else if ((m = /^(\d*),(\d+)$/.exec(s))) {
    entero = m[1];
    decimales = m[2];
  } else if ((m = /^(\d*)\.(\d+)$/.exec(s))) {
    // Un solo punto y sin coma: el punto decimal de hoy, salvo «1.234» (de 1 a 3 cifras sin cero delante y 3 detrás).
    if (/^[1-9]\d{0,2}\.\d{3}$/.test(s)) {
      return AMBIGUO;
    }
    entero = m[1];
    decimales = m[2];
  } else if ((m = /^(\d{1,3}(?:\.\d{3})+)(?:,(\d+))?$/.exec(s))) {
    // Miles con punto: varios grupos («1.234.567») o con coma decimal («1.010,50»).
    entero = m[1].replace(/\./g, '');
    decimales = m[2] ?? '';
  } else {
    return FORMATO;
  }
  const valor = Number(`${entero || '0'}${decimales ? `.${decimales}` : ''}`);
  if (!Number.isFinite(valor)) {
    return FORMATO;
  }
  return { estado: 'valido', valor: negativo && valor !== 0 ? -valor : valor };
}

/** El precio guardado para el campo: «1.010,50», «25,00»; con más decimales, todos («12,345»). Sin número, vacío. */
export function importeParaCampo(valor: number | null | undefined): string {
  if (typeof valor !== 'number' || !Number.isFinite(valor)) {
    return '';
  }
  const absoluto = String(Math.abs(valor));
  if (/e/i.test(absoluto)) {
    return String(valor);
  }
  const [entero, decimales = ''] = absoluto.split('.');
  return `${valor < 0 ? '-' : ''}${agruparMiles(entero)},${decimales.padEnd(2, '0')}`;
}

/** La validación de hoy del precio (obligatorio y mayor o igual a 0) más el formato y lo ambiguo. */
export const validarPrecio: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const lectura = leerImporte(control.value as string | null);
  switch (lectura.estado) {
    case 'vacio':
      return { required: true };
    case 'formato':
      return { formato: true };
    case 'ambiguo':
      return { ambiguo: true };
    default:
      return lectura.valor < 0 ? { min: { min: 0, actual: lectura.valor } } : null;
  }
};

export type CampoProducto = 'concepto' | 'precio';

/** Los mensajes de cada error, bajo el campo y en el resumen. `escrito` es lo que hay en el campo (para lo ambiguo). */
export function mensajesError(campo: CampoProducto, errores: ValidationErrors | null, escrito: string | null): string[] {
  if (!errores) {
    return [];
  }
  if (campo === 'concepto') {
    return errores['required'] ? ['El concepto es obligatorio'] : [];
  }
  if (errores['required']) {
    return ['El precio es obligatorio'];
  }
  if (errores['formato']) {
    return ['Escribe el precio con cifras, por ejemplo 25 o 1.010,50'];
  }
  if (errores['ambiguo']) {
    const texto = limpiar(escrito ?? '');
    const [entero, decimales] = texto.split('.');
    return [`«${texto}» puede ser ${entero}${decimales} € o ${entero},${decimales} €: escríbelo sin punto (${entero}${decimales}) o con coma (${entero},${decimales})`];
  }
  if (errores['min']) {
    return ['El precio debe ser mayor o igual a 0'];
  }
  return [];
}

export interface ValoresProducto {
  concepto: string;
  precio: string;
  vendibleProfesor: boolean;
}

export interface CuerpoProducto {
  concepto: string;
  precio: number;
  vendibleProfesor: boolean;
}

/**
 * El cuerpo de `POST /productos` y `PUT /productos/{id}`: el `productoForm.value` de hoy, con sus tres claves y en su
 * orden. El concepto va tal cual (hoy no se recorta). Un precio que no se puede leer no produce cuerpo.
 */
export function cuerpoProducto(valores: ValoresProducto): CuerpoProducto {
  const lectura = leerImporte(valores.precio);
  if (lectura.estado !== 'valido') {
    throw new Error(`El precio no es válido: ${lectura.estado}`);
  }
  return { concepto: valores.concepto, precio: lectura.valor, vendibleProfesor: !!valores.vendibleProfesor };
}

// ------------------------------------------------------------------ listado

export type CampoOrden = 'id' | 'concepto' | 'precio';

export interface Orden {
  campo: CampoOrden;
  sentido: 'asc' | 'desc';
}

/** El orden de hoy al abrir: concepto, de la A a la Z. */
export const ORDEN_POR_DEFECTO: Orden = { campo: 'concepto', sentido: 'asc' };

export const OPCIONES_ORDEN: ReadonlyArray<{ orden: Orden; etiqueta: string }> = [
  { orden: { campo: 'concepto', sentido: 'asc' }, etiqueta: 'Concepto, de la A a la Z' },
  { orden: { campo: 'concepto', sentido: 'desc' }, etiqueta: 'Concepto, de la Z a la A' },
  { orden: { campo: 'precio', sentido: 'asc' }, etiqueta: 'Precio, de menor a mayor' },
  { orden: { campo: 'precio', sentido: 'desc' }, etiqueta: 'Precio, de mayor a menor' },
  { orden: { campo: 'id', sentido: 'asc' }, etiqueta: 'Nº, de menor a mayor' },
  { orden: { campo: 'id', sentido: 'desc' }, etiqueta: 'Nº, de mayor a menor' },
];

export function mismoOrden(a: Orden, b: Orden): boolean {
  return a.campo === b.campo && a.sentido === b.sentido;
}

export function etiquetaOrden(orden: Orden): string {
  return OPCIONES_ORDEN.find((o) => mismoOrden(o.orden, orden))?.etiqueta ?? OPCIONES_ORDEN[0].etiqueta;
}

const comparador = new Intl.Collator('es-ES', { sensitivity: 'base', numeric: true });

/** Una copia ordenada; los productos sin precio, al final en los dos sentidos; a igualdad, por nº. */
export function ordenar<T extends ProductoPanel>(lista: readonly T[], orden: Orden): T[] {
  const signo = orden.sentido === 'asc' ? 1 : -1;
  return [...lista].sort((a, b) => {
    let r = 0;
    if (orden.campo === 'concepto') {
      r = signo * comparador.compare(a.concepto ?? '', b.concepto ?? '');
    } else if (orden.campo === 'precio') {
      const pa = typeof a.precio === 'number' ? a.precio : null;
      const pb = typeof b.precio === 'number' ? b.precio : null;
      if (pa === null || pb === null) {
        r = pa === pb ? 0 : pa === null ? 1 : -1;
      } else {
        r = signo * (pa - pb);
      }
    } else {
      r = signo * (a.id - b.id);
    }
    return r || a.id - b.id;
  });
}

export interface Filtros {
  concepto: string;
  tipo: '' | TipoProducto;
  miCaja: '' | 'true' | 'false';
}

export type ClaveFiltro = 'concepto' | 'tipo' | 'miCaja';

/** Los chips de hoy: concepto recortado, tipo con su etiqueta y Mi caja Sí o No. */
export function filtrosActivos(f: Filtros): { clave: ClaveFiltro; texto: string }[] {
  const activos: { clave: ClaveFiltro; texto: string }[] = [];
  const concepto = f.concepto.trim();
  if (concepto) {
    activos.push({ clave: 'concepto', texto: `«${concepto}»` });
  }
  if (f.tipo) {
    const tipo = TIPOS_PRODUCTO.find((t) => t.valor === f.tipo);
    activos.push({ clave: 'tipo', texto: `Tipo: ${tipo ? tipo.etiqueta : f.tipo}` });
  }
  if (f.miCaja) {
    activos.push({ clave: 'miCaja', texto: `Mi caja: ${f.miCaja === 'true' ? 'Sí' : 'No'}` });
  }
  return activos;
}

/** Como hoy: «» no filtra; «true» y «false» van como booleano en `vendibleProfesor`. */
export function miCajaParaApi(miCaja: Filtros['miCaja']): boolean | null {
  return miCaja === '' ? null : miCaja === 'true';
}

export function textoTotal(n: number): string {
  return plural(n, 'producto', 'productos');
}
