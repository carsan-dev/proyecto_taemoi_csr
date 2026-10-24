import type { ProductoAlumnoDTO } from '../../interfaces/producto-alumno-dto';
import { fechaApi } from '../../utils/fecha-api';
import { eur, fecha } from '../formato/formato-es';
import { importeParaCampo, leerImporte } from '../productos/productos-vista';

/**
 * Funciones puras de «Productos y cargos» de un alumno (8b-3n): los campos de cada cargo (fecha de asignación, cantidad
 * y precio), el estado de pago, la matrícula, la paginación recordada y el cuerpo de la asignación (el MISMO de hoy).
 */

/** Cargos por página (la paginación local de hoy). */
export const TAMANO_PAGINA = 10;

/**
 * Los textos de hoy (Anexo A, 8b-3n): errores de carga, del bloque «Asignar producto» y avisos. Los del alumno que no
 * se carga, el error de una fila al guardar y el aviso de salir son los de la ronda 2 (decisiones del orquestador).
 */
export const TEXTOS_PRODUCTOS_ALUMNO = {
  errorCargos: 'No se han podido obtener los productos del alumno',
  errorCatalogo: 'No se han podido obtener los productos',
  /** El alumno no se carga, no existe o está eliminado anonimizando (decisiones 293 y 299). */
  errorAlumno: 'No se han podido cargar los productos del alumno.',
  sinProducto: 'Seleccione un producto',
  sinDeporte: 'Seleccione un deporte',
  sinDeportes: 'No hay deportes disponibles. Compruebe los deportes del alumno antes de asignar la matrícula.',
  vacio: 'No hay productos asignados a este alumno',
  guardado: 'Cambios aplicados correctamente',
  /** El error de una fila al guardar cuando el servidor no da mensaje (decisión 296). */
  errorGuardarCambio: 'No se ha podido guardar este cambio.',
  cancelado: 'Cargo cancelado',
  reactivado: 'Cargo reactivado',
  notaPrecioConCobro: 'Revierte el cobro para cambiar precio o cantidad',
  notaCobrarConCambios: 'Guarda los cambios antes de cobrar',
} as const;

/** El cuerpo de «¿Salir sin guardar?»: «Tienes N cambios sin guardar. Si sales, se pierden.» (singular con 1). */
export function textoSalirSinGuardar(pendientes: number): string {
  const cambios = pendientes === 1 ? '1 cambio' : `${pendientes} cambios`;
  return `Tienes ${cambios} sin guardar. Si sales, se pierden.`;
}

/** «N cambios no se pudieron guardar» (decisión 296). */
export function textoFallosGuardado(fallos: number): string {
  return fallos === 1 ? '1 cambio no se pudo guardar' : `${fallos} cambios no se pudieron guardar`;
}

// ------------------------------------------------------------------ estado de pago

export type EstadoPago = 'pagado' | 'pendiente' | 'cancelado' | 'devuelto';

export function estaCancelado(cargo: ProductoAlumnoDTO): boolean {
  return !!cargo.canceladoEn;
}

/** La pastilla: Cancelado manda; un pagado con devolución, Devuelto; si no, Pagado o Pendiente. */
export function estadoPago(cargo: ProductoAlumnoDTO): EstadoPago {
  if (estaCancelado(cargo)) {
    return 'cancelado';
  }
  if (cargo.pagado) {
    return cargo.devuelto ? 'devuelto' : 'pagado';
  }
  return 'pendiente';
}

/**
 * Decisión 292: el cargo tiene un cobro ACTIVO y el servidor rechaza (409) cambiar su precio o cantidad. La API no dice
 * si hay cobro: desde la 6b-1 todo cargo pagado lo tiene (los pagados antes de la 6b-1 no, y el servidor sí los dejaría
 * cambiar); se toma `pagado` como señal, del lado seguro.
 */
export function tieneCobroActivo(cargo: ProductoAlumnoDTO): boolean {
  return cargo.pagado === true;
}

/** «Cancelado por <nombre> el dd/mm/aaaa · «motivo»» (sin nombre: «Cancelado el …»); el de la pantalla de hoy. */
export function textoCancelacion(cargo: ProductoAlumnoDTO): string {
  if (!cargo.canceladoEn) {
    return '';
  }
  const cuando = fechaApi(cargo.canceladoEn);
  const dia = cuando ? fecha(cuando) : '';
  const quien = cargo.canceladoPorNombre?.trim();
  const sufijo = dia ? ` el ${dia}` : '';
  const base = quien ? `Cancelado por ${quien}${sufijo}` : `Cancelado${sufijo}`;
  const motivo = cargo.motivoCancelacion?.trim();
  return motivo ? `${base} · «${motivo}»` : base;
}

// ------------------------------------------------------------------ fecha de asignación

/** `yyyy-MM-dd` del día LOCAL de la fecha (el del calendario), o vacío si no hay o no es válida. */
export function diaLocal(valor: Date | string | number | null | undefined): string {
  if (valor === null || valor === undefined || valor === '') {
    return '';
  }
  const f = valor instanceof Date ? valor : new Date(valor);
  if (Number.isNaN(f.getTime())) {
    return '';
  }
  const mm = String(f.getMonth() + 1).padStart(2, '0');
  const dd = String(f.getDate()).padStart(2, '0');
  return `${String(f.getFullYear()).padStart(4, '0')}-${mm}-${dd}`;
}

/**
 * La fecha de asignación con el día `dia` (`yyyy-MM-dd`), como la pantalla de hoy: cambia el día local y conserva la
 * hora y los milisegundos; un cargo antiguo sin fecha toma el mediodía. Un día que no existe («2026-02-30») da `null`.
 */
export function fechaConDia(original: Date | string | number | null | undefined, dia: string): Date | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dia)) {
    return null;
  }
  const [anio, mes, d] = dia.split('-').map(Number);
  const base = original === null || original === undefined || original === '' ? null : new Date(original);
  const resultado = base && !Number.isNaN(base.getTime()) ? new Date(base.getTime()) : new Date();
  if (!base || Number.isNaN(base.getTime())) {
    resultado.setHours(12, 0, 0, 0);
  }
  resultado.setFullYear(anio, mes - 1, d);
  return diaLocal(resultado) === dia ? resultado : null;
}

/**
 * Decisión 298: el cuerpo del `PUT /productos-alumno/{id}` del lote, SOLO con los campos editados de la fila. Nunca
 * `pagado`, `fechaPago`, `metodoPago` ni el resto del cargo: el servidor deja como está lo que no llega, y un cuerpo
 * completo y viejo con `pagado:false` desharía un cobro hecho mientras por otra persona.
 */
export type CambiosCargo = Partial<Pick<ProductoAlumnoDTO, 'fechaAsignacion' | 'cantidad' | 'precio' | 'notas'>>;

// ------------------------------------------------------------------ cantidad y precio

/** Motivo por el que un campo no vale: vacío, no es un número, ambiguo («1.234»), por debajo del mínimo o decimales. */
export type ErrorCampoCargo = 'vacio' | 'formato' | 'ambiguo' | 'min' | 'entero' | 'decimales';

export type LecturaCampo = { valor: number; error: null } | { valor: null; error: ErrorCampoCargo };

/** Mayor entero de la columna (`Integer` de la API). */
const MAX_CANTIDAD = 2147483647;

/** Cantidad: entero mayor o igual a 1 (el `min="1"` de hoy, sin decimales). */
export function leerCantidad(texto: string | number | null | undefined): LecturaCampo {
  const s = texto === null || texto === undefined ? '' : String(texto).trim();
  if (s === '') {
    return { valor: null, error: 'vacio' };
  }
  if (/^-?\d+[.,]\d+$/.test(s)) {
    return { valor: null, error: 'entero' };
  }
  if (!/^-?\d+$/.test(s)) {
    return { valor: null, error: 'formato' };
  }
  const n = Number(s);
  if (n < 1) {
    return { valor: null, error: 'min' };
  }
  if (n > MAX_CANTIDAD) {
    return { valor: null, error: 'formato' };
  }
  return { valor: n, error: null };
}

/** Precio: el importe escrito en es-ES (`leerImporte`), mayor o igual a 0 y con dos decimales como mucho (paso 0,01). */
export function leerPrecio(texto: string | number | null | undefined): LecturaCampo {
  const lectura = leerImporte(texto === null || texto === undefined ? null : String(texto));
  switch (lectura.estado) {
    case 'vacio':
      return { valor: null, error: 'vacio' };
    case 'formato':
      return { valor: null, error: 'formato' };
    case 'ambiguo':
      return { valor: null, error: 'ambiguo' };
  }
  if (lectura.valor < 0) {
    return { valor: null, error: 'min' };
  }
  if (Math.abs(Math.round(lectura.valor * 100) - lectura.valor * 100) > 1e-6) {
    return { valor: null, error: 'decimales' };
  }
  return { valor: lectura.valor, error: null };
}

/** El precio del cargo para su campo: «1.010,50», «25,00». */
export function precioParaCampo(valor: number | null | undefined): string {
  return importeParaCampo(valor);
}

// ------------------------------------------------------------------ asignar

/** Producto del catálogo tal como llega de `GET /productos/todos`. */
export interface ProductoCatalogo {
  id: number;
  concepto: string;
  precio: number | null;
}

/** Sin tildes y en mayúsculas, para comparar conceptos y buscar. */
export function normalizar(texto: string | null | undefined): string {
  return (texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase();
}

/** Matrícula: el concepto lleva la palabra MATRICULA, sin tildes (la regla de hoy y del servidor). */
export function esMatricula(concepto: string | null | undefined): boolean {
  return /\bMATRICULA\b/.test(normalizar(concepto));
}

/** «Matrícula taekwondo · 25,00 €» (boceto P1); sin precio, solo el concepto. */
export function etiquetaProducto(producto: ProductoCatalogo): string {
  return typeof producto.precio === 'number' ? `${producto.concepto} · ${eur(producto.precio)}` : producto.concepto;
}

/** Los productos cuyo concepto contiene lo buscado (sin tildes ni mayúsculas); sin búsqueda, todos. */
export function filtrarCatalogo(catalogo: ProductoCatalogo[], busqueda: string): ProductoCatalogo[] {
  const q = normalizar(busqueda).trim();
  return q ? catalogo.filter((p) => normalizar(p.concepto).includes(q)) : catalogo;
}

/**
 * El cuerpo de la asignación de hoy (cuerpo B): el servidor pone concepto, precio y fecha del catálogo y de su reloj
 * (decisión 285) y toma cantidad, pagado y notas. Sin deporte: la matrícula lo lleva en la ruta.
 */
export function cuerpoAsignacion(alumnoId: number, productoId: number, ahora: Date = new Date()): ProductoAlumnoDTO {
  return {
    id: 0,
    productoId,
    alumnoId,
    concepto: '',
    fechaAsignacion: ahora,
    cantidad: 1,
    precio: 0,
    pagado: false,
    fechaPago: null,
    notas: '',
  };
}

// ------------------------------------------------------------------ alumno

/** Lo que la pantalla usa del alumno (`GET /alumnos/{id}`). */
export interface AlumnoProductos {
  id: number;
  nombre: string;
  apellidos: string;
  fotoAlumno?: unknown;
  deportes?: Array<{ deporte: string; activo?: boolean | null }> | null;
  /** Decisión 299: se eliminó anonimizando (`GET /alumnos/{id}`); la pantalla lo enseña como uno que no existe (293). */
  alumnoEliminado?: boolean | null;
}


// ------------------------------------------------------------------ paginación recordada

/** La clave de hoy (`productosAlumnoEstado_{id}`): las dos pantallas recuerdan la misma página. */
export function clavePaginacion(alumnoId: number): string {
  return `productosAlumnoEstado_${alumnoId}`;
}

/** La página guardada (1 si no hay, no se puede leer o no vale). */
export function leerPaginaGuardada(alumnoId: number, almacen: Storage | null = almacenSesion()): number {
  try {
    const texto = almacen?.getItem(clavePaginacion(alumnoId));
    if (!texto) {
      return 1;
    }
    const pagina = Number(JSON.parse(texto)?.paginaActual);
    return Number.isInteger(pagina) && pagina >= 1 ? pagina : 1;
  } catch {
    return 1;
  }
}

/** Guarda la página con el formato de hoy (`{paginaActual, tamanoPagina}`); si el almacén falla, no pasa nada. */
export function guardarPagina(alumnoId: number, pagina: number, almacen: Storage | null = almacenSesion()): void {
  try {
    almacen?.setItem(clavePaginacion(alumnoId), JSON.stringify({ paginaActual: pagina, tamanoPagina: TAMANO_PAGINA }));
  } catch {
    // sessionStorage lleno o bloqueado: la página no se recuerda.
  }
}

function almacenSesion(): Storage | null {
  try {
    return typeof sessionStorage === 'undefined' ? null : sessionStorage;
  } catch {
    return null;
  }
}

/** Páginas para `total` cargos (al menos 1) y la página pedida dentro de ellas. */
export function paginaValida(pagina: number, total: number): number {
  const paginas = Math.max(1, Math.ceil(total / TAMANO_PAGINA));
  return Math.min(Math.max(1, Math.floor(pagina) || 1), paginas);
}
