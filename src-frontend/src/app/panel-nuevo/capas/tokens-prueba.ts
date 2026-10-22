import { ApplicationRef, NgZone } from '@angular/core';
import { TestBed } from '@angular/core/testing';

/**
 * Solo para los specs: los tokens de `panel-nuevo.scss` que miran los estilos calculados de las capas (Karma no carga
 * esa hoja, que va aparte con `inject: false`). Mismos valores que DESIGN.md.
 */
const TOKENS = `.pn {
  --pn-marca-primario-tinta: #1a2230; --pn-marca-sobre-primario: #ffffff; --pn-marca-sobre-primario-2: #c6c8cb;
  --pn-marca-primario-suave: #e8e9ea; --pn-marca-por-defecto: #1a2230;
  --pn-marca-suave-por-defecto: #e8e9ea; --pn-superficie: #ffffff; --pn-linea: #e4e4df; --pn-linea-fuerte: #cfcfc8;
  --pn-borde-control: #868d97; --pn-tinta: #1a2230; --pn-tinta-2: #5d6573; --pn-peligro: #b42318; --pn-bien: #2f6b3a;
  --pn-atencion: #a14f0b; --pn-serie-pista: #eceef1; --pn-radio-s: 6px; --pn-radio-m: 8px; --pn-radio-carnet: 14px;
  --pn-radio-pastilla: 999px; --pn-velo: rgb(26 34 48 / 40%);
  --pn-acento: #466396; --pn-acento-borde: #6f8abb; --pn-acento-suave: #e6efff;
  --pn-sombra-dialogo: 0 24px 48px -12px rgb(26 34 48 / 28%), 0 4px 10px rgb(26 34 48 / 8%);
  --pn-sombra-hoja: 0 -12px 32px -8px rgb(26 34 48 / 28%);
  --pn-sombra-menu: 0 6px 20px -6px rgb(26 34 48 / 22%), 0 2px 4px rgb(26 34 48 / 6%);
  --pn-transicion: 0s;
}`;

/** Añade los tokens al `head`; devuelve con qué quitarlos. */
export function ponerTokensDePrueba(): () => void {
  const estilo = document.createElement('style');
  estilo.textContent = TOKENS;
  document.head.appendChild(estilo);
  return () => estilo.remove();
}

/**
 * `ApplicationRef.tick()` dentro de la zona de Angular, como en la aplicación: el diálogo, los avisos y el panel lateral
 * viven fuera del fixture y se pintan con el tick de la aplicación. Desde fuera de la zona, un evento que salta durante
 * el tick (el foco que pone una capa al abrirse) lanzaría otro tick anidado (NG0101).
 */
export function pintarCapas(): void {
  TestBed.inject(NgZone).run(() => TestBed.inject(ApplicationRef).tick());
}
