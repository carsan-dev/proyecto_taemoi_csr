import { ordenCompleto } from '../../../web-plantilla/editor/bloques-editor';
import {
  type EstadoProfesor,
  MAX_NUMERO_PROFESOR,
  TEXTOS_PROFESORES,
  clavePermiso,
  esFotoProfesor,
  numeroProfesor,
} from '../../../web-plantilla/editor/fotos-profesores';
import { conOrden } from './web-contenido-bloques';
import type { BloqueContenido } from './web-contenido-vista';

/**
 * Profesores de «Web · Contenido» (W12f-2b; boceto `boceto-panel-contenido-web-comarcal.html`: `profeHtml`,
 * `anadirProfe`, `quitarProfe`, `errores`, `cambios`; API `ContenidoWebService`). Lógica pura sobre el borrador.
 *
 * Permiso (d521, d526, d531; `ContenidoWebService.comprobarEstructura`, `conservaLaConstancia` y `escribir`):
 * - viaja solo en la fila `profesor.<n>.nombre` y solo como `true` (nunca el objeto del GET);
 * - un profesor con constancia guardada cuyo nombre no cambia la conserva: no se manda nada;
 * - uno nuevo, uno sin constancia o uno cuyo nombre cambia (puede ser otra persona en el mismo puesto) necesita
 *   `permiso: true`, que solo se manda si la persona marcó la casilla en esta pantalla para ese número.
 * El nombre se compara como la API: el guardado y el nuevo sin espacios en los extremos (`String.strip()` de Java),
 * con mayúsculas y espacios de dentro tal cual.
 *
 * d531: la constancia es de la persona, no del puesto. Un número nunca se reutiliza en la pantalla: el de un profesor
 * nuevo es uno más que el mayor guardado, del borrador o ya usado (`usados`), sin rellenar huecos.
 */

const fila = (filas: readonly BloqueContenido[], clave: string) => filas.find((f) => f.clave === clave);

/** Los números de profesor de las filas, de menor a mayor (como los pinta el tema). */
export function numerosProfesores(filas: readonly BloqueContenido[]): number[] {
  const numeros = new Set<number>();
  for (const f of filas) {
    const n = numeroProfesor(f.clave);
    if (n !== null) {
      numeros.add(n);
    }
  }
  return [...numeros].sort((a, b) => a - b);
}

/**
 * El número de un profesor nuevo: uno más que el mayor guardado, del borrador o usado en esta pantalla. Si se pasa del
 * 99, el menor que no esté ni guardado, ni en el borrador, ni usado; si no hay, null (no se añade).
 */
export function numeroProfesorNuevo(
  filas: readonly BloqueContenido[],
  guardado: readonly BloqueContenido[],
  usados: Iterable<number> = [],
): number | null {
  const ocupados = new Set([...numerosProfesores(filas), ...numerosProfesores(guardado), ...usados]);
  const mayor = Math.max(0, ...ocupados);
  if (mayor < MAX_NUMERO_PROFESOR) {
    return mayor + 1;
  }
  for (let n = 1; n <= MAX_NUMERO_PROFESOR; n++) {
    if (!ocupados.has(n)) {
      return n;
    }
  }
  return null;
}

/** Fija el orden de los bloques de Inicio con sus cabeceras (sin ellas, una fila nueva de profesor movería la sección). */
export const conCabeceras = (filas: readonly BloqueContenido[]) => conOrden(filas, ordenCompleto(filas));

/** Boceto `anadirProfe`: la tarjeta nueva, al final, con su nombre vacío. Null si no queda número. */
export function anadirProfesor(
  filas: readonly BloqueContenido[],
  guardado: readonly BloqueContenido[],
  usados: Iterable<number>,
): { filas: BloqueContenido[]; n: number } | null {
  const n = numeroProfesorNuevo(filas, guardado, usados);
  if (n === null) {
    return null;
  }
  const nombre: BloqueContenido = { clave: `profesor.${n}.nombre`, texto: '', imagenId: null, imagenUrl: null, orden: 0, oculto: false, permiso: null };
  return { filas: [...conCabeceras(filas), nombre], n };
}

/** Las filas de un profesor: nombre, cargo, foto y sedes. */
export const filasDeProfesor = (filas: readonly BloqueContenido[], n: number): string[] =>
  filas.filter((f) => numeroProfesor(f.clave) === n).map((f) => f.clave);

/** Boceto `quitarProfe`: fuera todas sus filas (también sus sedes); se quita de la web al guardar. */
export function quitarProfesor(filas: readonly BloqueContenido[], n: number): BloqueContenido[] {
  return filas.filter((f) => numeroProfesor(f.clave) !== n);
}

/** Tras quitar un profesor, a dónde va el foco: al siguiente, al de antes o (sin ninguno) a «Añadir profesor». */
export function vecinoProfesor(filas: readonly BloqueContenido[], n: number): number | null {
  const lista = numerosProfesores(filas);
  const i = lista.indexOf(n);
  return i < 0 ? null : (lista[i + 1] ?? lista[i - 1] ?? null);
}

/** `String.strip()` de Java (`Character.isWhitespace`): sin los espacios de no separación (U+00A0, U+2007, U+202F). */
const BLANCO = '[\\t\\n\\u000B\\f\\r\\u001C-\\u001F \\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029\\u205F\\u3000]';
const EXTREMOS = new RegExp(`^${BLANCO}+|${BLANCO}+$`, 'g');
export const stripJava = (texto: string | null | undefined) => (texto ?? '').replace(EXTREMOS, '');

/**
 * Si el profesor necesita la casilla (`ContenidoWebService.comprobarEstructura`): no tiene constancia guardada o su
 * nombre ya no es el guardado. `pedidos`: los que la API pidió en el último intento (`profesor.<n>.permiso`).
 */
export function pidePermiso(
  guardado: readonly BloqueContenido[],
  borrador: readonly BloqueContenido[],
  n: number,
  pedidos: ReadonlySet<number> = new Set(),
): boolean {
  const clave = `profesor.${n}.nombre`;
  const g = fila(guardado, clave);
  const conserva = !!g && g.permiso != null && stripJava(g.texto) === stripJava(fila(borrador, clave)?.texto);
  return !conserva || pedidos.has(n);
}

/**
 * Los profesores a los que el `PUT` manda `permiso: true`: los que lo necesitan y tienen la casilla marcada en esta
 * pantalla. Uno que vuelve a su nombre guardado conserva su constancia: su casilla deja de contar.
 */
export function permisosParaEnviar(
  guardado: readonly BloqueContenido[],
  borrador: readonly BloqueContenido[],
  marcados: ReadonlySet<number>,
  pedidos: ReadonlySet<number> = new Set(),
): Set<number> {
  return new Set(
    numerosProfesores(borrador).filter(
      (n) => marcados.has(n) && !!fila(borrador, `profesor.${n}.nombre`) && pidePermiso(guardado, borrador, n, pedidos),
    ),
  );
}

const FECHA = /^(\d{4})-(\d{2})-(\d{2})/;

/** La fecha de la constancia como el boceto (`03/11/2026`), sin pasar por la zona horaria. */
export function fechaConstancia(en: string): string {
  const m = FECHA.exec(en ?? '');
  return m ? `${m[3]}/${m[2]}/${m[1]}` : '';
}

/**
 * Boceto `profeHtml` (constancia, d521) y d526: «Permiso marcado por María Ruiz el 03/11/2026» o «Permiso dado en el
 * alta el 01/10/2026». Sin quien lo marcó (la cuenta ya no existe), «Permiso marcado el …».
 */
export function textoConstancia(permiso: NonNullable<BloqueContenido['permiso']>): string {
  const fecha = fechaConstancia(permiso.en);
  const el = fecha ? ` el ${fecha}` : '';
  if (permiso.origen === 'ALTA') {
    return `Permiso dado en el alta${el}`;
  }
  return permiso.por ? `Permiso marcado por ${permiso.por}${el}` : `Permiso marcado${el}`;
}

/** El estado de cada profesor del borrador para la capa: su nombre y su casilla o su constancia. */
export function estadoProfesores(
  guardado: readonly BloqueContenido[],
  borrador: readonly BloqueContenido[],
  marcados: ReadonlySet<number>,
  pedidos: ReadonlySet<number> = new Set(),
): EstadoProfesor[] {
  return numerosProfesores(borrador).map((n) => {
    const nombre = fila(borrador, `profesor.${n}.nombre`)?.texto ?? '';
    const constancia = fila(guardado, `profesor.${n}.nombre`)?.permiso ?? null;
    // Si el nombre vuelve al guardado, vuelve la constancia: la casilla deja de contar.
    const permiso: EstadoProfesor['permiso'] =
      pidePermiso(guardado, borrador, n, pedidos) || !constancia
        ? { tipo: 'casilla', marcada: marcados.has(n) }
        : { tipo: 'constancia', texto: textoConstancia(constancia) };
    return { n, nombre, permiso };
  });
}

/**
 * Boceto `errores` de los profesores: sin la casilla donde hace falta, «Marca la casilla…» (`profesor.<n>.permiso`); sin
 * fila de nombre, «Escribe su nombre.» (con fila vacía lo dice `erroresContenido`).
 */
export function erroresProfesores(
  guardado: readonly BloqueContenido[],
  borrador: readonly BloqueContenido[],
  marcados: ReadonlySet<number>,
  pedidos: ReadonlySet<number> = new Set(),
): Record<string, string> {
  const errores: Record<string, string> = {};
  for (const n of numerosProfesores(borrador)) {
    if (!fila(borrador, `profesor.${n}.nombre`)) {
      errores[`profesor.${n}.nombre`] = 'Escribe su nombre.';
    }
    if (pidePermiso(guardado, borrador, n, pedidos) && !marcados.has(n)) {
      errores[clavePermiso(n)] = TEXTOS_PROFESORES.faltaPermiso;
    }
  }
  return errores;
}

/**
 * Boceto `cambios` de los profesores: cada uno añadido o quitado cuenta uno (`profesor.<n>.anadido` o `.quitado`; sus
 * textos y su foto no cuentan aparte, `fuera`); en los demás, la foto (`profesor.<n>.foto`), su descripción
 * (`profesor.<n>.foto.alt`), sus sedes (`profesor.<n>.sedes`) y la casilla marcada de uno que la necesita
 * (`profesor.<n>.permiso`). El nombre y el cargo los cuenta `clavesCambiadas`.
 */
export function cambiosProfesores(
  guardado: readonly BloqueContenido[],
  borrador: readonly BloqueContenido[],
  marcados: ReadonlySet<number>,
  pedidos: ReadonlySet<number> = new Set(),
): { claves: string[]; fuera: Set<number> } {
  const antes = numerosProfesores(guardado);
  const ahora = numerosProfesores(borrador);
  const claves: string[] = [];
  const fuera = new Set<number>();
  for (const n of ahora.filter((x) => !antes.includes(x))) {
    claves.push(`profesor.${n}.anadido`);
    fuera.add(n);
  }
  for (const n of antes.filter((x) => !ahora.includes(x))) {
    claves.push(`profesor.${n}.quitado`);
    fuera.add(n);
  }
  for (const n of ahora.filter((x) => antes.includes(x))) {
    const foto = `profesor.${n}.foto`;
    const g = fila(guardado, foto);
    const b = fila(borrador, foto);
    if ((g?.imagenId ?? null) !== (b?.imagenId ?? null)) {
      claves.push(foto);
    }
    if ((g?.texto ?? '') !== (b?.texto ?? '')) {
      claves.push(`${foto}.alt`);
    }
    if ((fila(guardado, `profesor.${n}.sedes`)?.texto ?? null) !== (fila(borrador, `profesor.${n}.sedes`)?.texto ?? null)) {
      claves.push(`profesor.${n}.sedes`);
    }
    if (marcados.has(n) && pidePermiso(guardado, borrador, n, pedidos)) {
      claves.push(clavePermiso(n));
    }
  }
  return { claves, fuera };
}

/** Si una clave cambiada es de un profesor añadido o quitado (no cuenta aparte: cuenta el profesor). */
export function esDeProfesorFuera(clave: string, fuera: ReadonlySet<number>): boolean {
  const n = numeroProfesor(clave.endsWith('.alt') ? clave.slice(0, -4) : clave);
  return n !== null && fuera.has(n);
}

/** Una foto de profesor sin descripción: «Describe su foto.» (boceto `errores`). */
export const errorFotoProfesor = (clave: string) => (esFotoProfesor(clave) ? TEXTOS_PROFESORES.describeSuFoto : null);
