// Pruebas del script que genera index-plantilla.html. Sin dependencias: npm run test:scripts
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

import { extraerEtiquetas, generarIndexPlantilla } from './generar-index-plantilla.mjs';

const SCRIPT = join(dirname(fileURLToPath(import.meta.url)), 'generar-index-plantilla.mjs');

const CRITICO = '<style>:root{--bs-blue:#0d6efd}body{margin:0}</style>';
const HOJA = `<link rel="stylesheet" href="styles-ABC.css" media="print" onload="this.media='all'">`;
const NOSCRIPT = '<noscript><link rel="stylesheet" href="styles-ABC.css"></noscript>';
const PRECARGAS = '<link rel="modulepreload" href="chunk-1.js"><link rel="modulepreload" href="chunk-2.js">';
const POLYFILLS = '<script src="polyfills-P.js" type="module"></script>';
const SCRIPTS = '<script src="scripts-S.js" defer></script>';
const MAIN = '<script src="main-M.js" type="module"></script>';

/** Como el index.html compilado de Moiskimdo, en pequeño. */
function compilado({ sinMain = false } = {}) {
  return `<!doctype html>
<html lang="es">
<head>
  <script>
    globalThis.dataLayer = globalThis.dataLayer || [];
  </script>
  <meta charset="utf-8">
  <title>Moi's Kim Do Umbrete</title>
  <base href="/">
  <link rel="canonical" href="https://moiskimdo.es/">
  <link rel="icon" href="/favicon.svg" type="image/svg+xml" sizes="any">
  <style>@font-face{font-family:'Roboto';src:url(https://fonts.gstatic.com/r.woff2)}</style>
  <meta property="og:type" content="website">
  <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
  <script type="application/ld+json">{"name":"Moi's Kim Do"}</script>
${CRITICO}${HOJA}${NOSCRIPT}</head>
<body>
  <script>
    (function () { document.documentElement.style.setProperty('--scrollbar-width', '0px'); })();
  </script>
  <app-root></app-root>
${PRECARGAS}${POLYFILLS}${SCRIPTS}${sinMain ? '' : MAIN}</body>
</html>
`;
}

const PLANTILLA = readFileSync(join(dirname(SCRIPT), '..', 'src', 'index-plantilla.html'), 'utf8');

test('extrae las etiquetas del build, en orden, separadas en cabeza y cuerpo', () => {
  const { cabeza, cuerpo } = extraerEtiquetas(compilado());
  assert.deepEqual(cabeza, [CRITICO, HOJA, NOSCRIPT]);
  assert.deepEqual(cuerpo, [
    '<link rel="modulepreload" href="chunk-1.js">',
    '<link rel="modulepreload" href="chunk-2.js">',
    POLYFILLS,
    SCRIPTS,
    MAIN,
  ]);
});

test('la plantilla generada: cabeza neutra con la meta y los bundles en su sitio; nada de Moiskimdo ni GA4', () => {
  const html = generarIndexPlantilla(compilado(), PLANTILLA);
  assert.match(html, /<meta name="taemoi-web" content="plantilla">/);
  assert.ok(html.includes(`${CRITICO}${HOJA}${NOSCRIPT}</head>`));
  assert.ok(html.includes(`<app-root></app-root>\n${PRECARGAS}${POLYFILLS}${SCRIPTS}${MAIN}</body>`));
  for (const prohibido of ['moiskimdo', "moi's kim do", 'datalayer', 'ld+json', 'googletagmanager', 'fonts.g', 'favicon', 'canonical']) {
    assert.ok(!html.toLowerCase().includes(prohibido), `no debe llevar ${prohibido}`);
  }
  // Sin <script> en línea: la CSP no necesita hash para este fichero.
  for (const m of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
    assert.match(m[1], /\ssrc="/);
    assert.equal(m[2].trim(), '');
  }
  // Lo de la plantilla, intacto.
  // (el favicon neutro es de la propia plantilla, no del build: se compara sin él por ambos lados).
  const sinBuild = (t) =>
    t.replace(/<style>[\s\S]*?<\/style>|<noscript>[\s\S]*?<\/noscript>|<link\b[^>]*>|<script\b[^>]*><\/script>/g, '');
  assert.equal(sinBuild(html), sinBuild(PLANTILLA));
});

test('sin el script principal: error', () => {
  assert.throws(() => generarIndexPlantilla(compilado({ sinMain: true }), PLANTILLA), /script principal/);
});

test('una plantilla sin la meta o que ya trae scripts: error', () => {
  assert.throws(() => generarIndexPlantilla(compilado(), PLANTILLA.replace('taemoi-web', 'otra')), /taemoi-web/);
  assert.throws(() => generarIndexPlantilla(compilado(), PLANTILLA.replace('</body>', `${MAIN}</body>`)), /ya trae/);
});

test('por línea de órdenes: escribe index-plantilla.html; sin script principal sale con código 1 y no escribe', () => {
  const dir = mkdtempSync(join(tmpdir(), 'index-plantilla-'));
  try {
    writeFileSync(join(dir, 'index.html'), compilado());
    const bien = spawnSync(process.execPath, [SCRIPT, '--dist', dir], { encoding: 'utf8' });
    assert.equal(bien.status, 0, bien.stderr);
    assert.equal(readFileSync(join(dir, 'index-plantilla.html'), 'utf8'), generarIndexPlantilla(compilado(), PLANTILLA));

    const otro = mkdtempSync(join(tmpdir(), 'index-plantilla-'));
    try {
      writeFileSync(join(otro, 'index.html'), compilado({ sinMain: true }));
      const mal = spawnSync(process.execPath, [SCRIPT, '--dist', otro], { encoding: 'utf8' });
      assert.equal(mal.status, 1);
      assert.match(mal.stderr, /script principal/);
      assert.throws(() => readFileSync(join(otro, 'index-plantilla.html')));
    } finally {
      rmSync(otro, { recursive: true, force: true });
    }

    const sinDist = spawnSync(process.execPath, [SCRIPT, '--dist', join(dir, 'no-existe')], { encoding: 'utf8' });
    assert.equal(sinDist.status, 1);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('la plantilla generada lleva un favicon neutro (data:,) y ningún icono de Moiskimdo', () => {
  const html = generarIndexPlantilla(compilado(), PLANTILLA);
  assert.deepEqual(html.match(/<link\b[^>]*rel="(?:shortcut )?icon"[^>]*>/gi), ['<link rel="icon" href="data:,">']);
  assert.ok(!/apple-touch-icon|favicon\.(?:ico|svg|png)/i.test(html));
});
