#!/usr/bin/env node
/**
 * Genera `index-plantilla.html` junto al `index.html` compilado (fase4-web-plan.md §3.2, punto 2).
 *
 * La web por plantilla usa una cabeza neutra (`src/index-plantilla.html`: sin GA4, JSON-LD ni meta de Moiskimdo). El
 * `index.html` de Moiskimdo no se toca. Tras `ng build`, este script copia de ese `index.html` compilado las etiquetas
 * que Angular inyecta (hojas de estilo con sus variantes `media`/`onload` y su `<noscript>`, el CSS crítico en línea,
 * los `modulepreload` y los `<script src>`) y las pone en la plantilla en el mismo sitio: las de la cabeza antes de
 * `</head>` y las del cuerpo antes de `</body>`. nginx sirve el resultado en los hosts de plantilla (pieza W11).
 *
 * Uso: node scripts/generar-index-plantilla.mjs [--dist <carpeta del index.html compilado>] [--plantilla <html>]
 * Sale con código distinto de 0 si falta algo o si el index.html compilado no tiene el script principal.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const DIST_POR_DEFECTO = join(RAIZ, 'dist', 'taemoi-angular-front', 'browser');
const PLANTILLA_POR_DEFECTO = join(RAIZ, 'src', 'index-plantilla.html');
const SALIDA = 'index-plantilla.html';

/** Una etiqueta por coincidencia, en orden: el `<noscript>` de una hoja va entero (su `<link>` no se cuenta aparte). */
const ETIQUETA =
  /<noscript>\s*<link\b[^>]*>\s*<\/noscript>|<link\b[^>]*>|<script\b[^>]*>[\s\S]*?<\/script>|<style\b[^>]*>[\s\S]*?<\/style>/gi;

function atributo(etiqueta, nombre) {
  const m = new RegExp(`\\s${nombre}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s>]+))`, 'i').exec(etiqueta);
  return m ? (m[1] ?? m[2] ?? m[3]) : null;
}

/** Fichero del propio build (relativo o del mismo origen), no de otro dominio (Google Fonts, etc.). */
function esLocal(url) {
  return !!url && !/^(?:[a-z][a-z0-9+.-]*:)?\/\//i.test(url) && !/^data:/i.test(url);
}

function esHojaLocal(etiqueta) {
  return /^<link\b/i.test(etiqueta) && atributo(etiqueta, 'rel')?.toLowerCase() === 'stylesheet' && esLocal(atributo(etiqueta, 'href'));
}

function esModulepreloadLocal(etiqueta) {
  return /^<link\b/i.test(etiqueta) && atributo(etiqueta, 'rel')?.toLowerCase() === 'modulepreload' && esLocal(atributo(etiqueta, 'href'));
}

/** `<script src="…"></script>` del build (módulo o `defer`); nunca un script en línea ni un JSON-LD. */
function esScriptLocal(etiqueta) {
  const m = /^<script\b([^>]*)>([\s\S]*?)<\/script>$/i.exec(etiqueta);
  return !!m && esLocal(atributo(`<x ${m[1]}>`, 'src')) && m[2].trim() === '';
}

/**
 * Extrae del index.html compilado las etiquetas inyectadas por Angular, separadas en cabeza y cuerpo y en su orden.
 * El CSS crítico (`inlineCritical`) es el `<style>` pegado a la hoja del build; los `<style>` de Google Fonts que
 * Angular incrusta en el sitio de los `<link>` de Moiskimdo no van pegados a ninguna hoja y se quedan fuera.
 */
export function extraerEtiquetas(compilado) {
  const finCabeza = compilado.search(/<\/head>/i);
  if (finCabeza < 0) {
    throw new Error('el index.html compilado no tiene </head>');
  }
  const coincidencias = [...compilado.matchAll(ETIQUETA)].map((m) => ({
    texto: m[0],
    inicio: m.index,
    fin: m.index + m[0].length,
  }));
  const cabeza = [];
  const cuerpo = [];
  coincidencias.forEach((c, i) => {
    let vale = false;
    if (/^<noscript>/i.test(c.texto)) {
      vale = esHojaLocal(/<link\b[^>]*>/i.exec(c.texto)[0]);
    } else if (/^<link\b/i.test(c.texto)) {
      vale = esHojaLocal(c.texto) || esModulepreloadLocal(c.texto);
    } else if (/^<script\b/i.test(c.texto)) {
      vale = esScriptLocal(c.texto);
    } else {
      const siguiente = coincidencias[i + 1];
      vale = !!siguiente && compilado.slice(c.fin, siguiente.inicio).trim() === '' && esHojaLocal(siguiente.texto);
    }
    if (vale) {
      (c.inicio < finCabeza ? cabeza : cuerpo).push(c.texto);
    }
  });
  const principal = cuerpo
    .concat(cabeza)
    .some((t) => esScriptLocal(t) && atributo(t, 'type') === 'module' && /(^|\/)main[-.][^/]*\.js$/i.test(atributo(t, 'src')));
  if (!principal) {
    throw new Error('el index.html compilado no tiene el script principal (<script src="main-….js" type="module">)');
  }
  return { cabeza, cuerpo };
}

/** Pone las etiquetas en la plantilla: las de la cabeza antes de `</head>` y las del cuerpo antes de `</body>`. */
export function generarIndexPlantilla(compilado, plantilla) {
  if (!/<meta\s+name="taemoi-web"\s+content="plantilla"\s*\/?>/i.test(plantilla)) {
    throw new Error('la plantilla no tiene <meta name="taemoi-web" content="plantilla">');
  }
  if (/<script\b/i.test(plantilla) || /<link\b[^>]*rel="(?:stylesheet|modulepreload)"/i.test(plantilla)) {
    throw new Error('la plantilla ya trae scripts u hojas de estilo: el script solo copia los del build');
  }
  const finCabeza = plantilla.search(/<\/head>/i);
  const finCuerpo = plantilla.search(/<\/body>/i);
  if (finCabeza < 0 || finCuerpo < 0 || finCuerpo < finCabeza) {
    throw new Error('la plantilla no tiene </head> y </body>');
  }
  const { cabeza, cuerpo } = extraerEtiquetas(compilado);
  return (
    plantilla.slice(0, finCabeza) +
    cabeza.join('') +
    plantilla.slice(finCabeza, finCuerpo) +
    cuerpo.join('') +
    plantilla.slice(finCuerpo)
  );
}

function leerArgumentos(argv) {
  const opciones = { dist: DIST_POR_DEFECTO, plantilla: PLANTILLA_POR_DEFECTO };
  for (let i = 0; i < argv.length; i += 2) {
    const [clave, valor] = [argv[i], argv[i + 1]];
    if ((clave !== '--dist' && clave !== '--plantilla') || !valor) {
      throw new Error(`argumento no válido: ${clave ?? ''}`);
    }
    opciones[clave.slice(2)] = resolve(valor);
  }
  return opciones;
}

function principal() {
  try {
    const { dist, plantilla } = leerArgumentos(process.argv.slice(2));
    const compilado = readFileSync(join(dist, 'index.html'), 'utf8');
    const resultado = generarIndexPlantilla(compilado, readFileSync(plantilla, 'utf8'));
    writeFileSync(join(dist, SALIDA), resultado, 'utf8');
    console.log(`generar-index-plantilla: ${join(dist, SALIDA)}`);
  } catch (error) {
    console.error(`generar-index-plantilla: ${error instanceof Error ? error.message : error}`);
    process.exit(1);
  }
}

/** Solo al ejecutarlo (`node scripts/…`), no al importarlo desde la prueba. */
function seEjecutaDirectamente() {
  if (!process.argv[1]) {
    return false;
  }
  const normalizar = (ruta) => (process.platform === 'win32' ? ruta.toLowerCase() : ruta);
  return normalizar(resolve(process.argv[1])) === normalizar(fileURLToPath(import.meta.url));
}

if (seEjecutaDirectamente()) {
  principal();
}
