/**
 * CSS de la capa de edición dentro de la vista previa (W12c), copiado del boceto `boceto-panel-contenido-web-comarcal.html`
 * (`#css-editor`): envoltorios de bloque y de parte fija, nombre, nota, contorno punteado de los datos de otras pantallas
 * y aviso con candado, con los ajustes a «La línea comarcal». Tokens de `panel-nuevo.scss` (el acento lo manda el panel
 * por el puente); va en el chunk de la capa, nunca en el del tema.
 */
export const ESTILOS_CAPA_EDITOR = `
:root{
  --pn-acento:#466396;--pn-acento-borde:#6f8abb;--pn-acento-suave:#e6efff;--pn-superficie:#ffffff;--pn-superficie-2:#fafaf8;
  --pn-linea:#e4e4df;--pn-linea-fuerte:#cfcfc8;--pn-borde-control:#868d97;--pn-tinta:#1a2230;--pn-tinta-2:#5d6573;--pn-tinta-3:#7a8290;
  --pn-fuente:"Public Sans","Segoe UI",system-ui,sans-serif;
  --pn-sombra-menu:0 6px 20px -6px rgb(26 34 48/22%),0 2px 4px rgb(26 34 48/6%);
  --pn-peligro:#b42318;--pn-atencion:#a14f0b;
  --pn-anillo:inset 0 0 0 2px var(--pn-acento),inset 0 0 0 3px rgb(255 255 255/90%);
}
/* El iframe mide lo que mide la página (el panel le pone ese alto): lo que en la web depende del alto de la pantalla
   usaría aquí el del iframe y crecería con él. En el editor, la raíz no ocupa una pantalla y las fotos usan una
   pantalla nominal fija (--ed-pantalla), igual en Móvil y en Escritorio. El CSS del tema no cambia. */
:root{--ed-pantalla:800px}
:root .lc{min-height:0}
@media (min-width:900px){
  :root .lc .portada-foto{height:max(360px,min(calc((50vw - var(--hueco) / 2) * 1.25),calc(var(--ed-pantalla) - var(--cabecera) - 220px)))}
  :root .lc .p-sede .sede-foto{height:min(calc((50vw - var(--hueco) / 2) * 1.25),calc(var(--ed-pantalla) - var(--cabecera)))}
}
.ed{font:400 .875rem/1.35 var(--pn-fuente);color:var(--pn-tinta);letter-spacing:0;font-stretch:100%;text-transform:none;text-align:left}
.ed svg{width:16px;height:16px;flex:none}
.ed-oculto{position:absolute!important;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
.ed-bloque{position:relative}
app-lc-linea-sedes.ed-bloque{display:block}
.ed-bloque::after{content:"";position:absolute;inset:0;z-index:14;pointer-events:none;transition:box-shadow 150ms ease-out}
.ed-bloque:is(:hover,:focus-within)::after{box-shadow:var(--pn-anillo)}
.ed-bloque.es-sel::after{box-shadow:inset 0 0 0 3px var(--pn-acento),inset 0 0 0 4px rgb(255 255 255/90%)}

/* Barra del bloque: nombre y nota */
.ed-barra{position:absolute;top:12px;left:12px;right:12px;z-index:16;display:grid;gap:6px;pointer-events:none}
.ed-fila{display:flex;flex-wrap:wrap;align-items:flex-start;justify-content:space-between;gap:6px;pointer-events:none}
.ed-fila > *{pointer-events:auto}
.ed-nombre{opacity:0;transition:opacity 150ms ease-out;display:inline-flex;align-items:center;gap:6px;min-height:30px;padding:0 10px;border:0;border-radius:6px;background:var(--pn-acento);color:#fff;font:600 .8125rem/1 var(--pn-fuente);cursor:pointer;white-space:nowrap}
.ed-acciones{display:flex;flex-wrap:wrap;gap:2px;padding:3px;border:1px solid var(--pn-linea);border-radius:8px;background:var(--pn-superficie);box-shadow:var(--pn-sombra-menu);opacity:0;transition:opacity 150ms ease-out}
.ed-nota{display:inline-flex;align-items:center;min-height:32px;padding:0 8px;color:var(--pn-tinta-2);font:400 .8125rem/1 var(--pn-fuente)}
.ed-bloque:is(:hover,:focus-within,.es-sel) > .ed-barra .ed-nombre,
.ed-bloque:is(:hover,:focus-within,.es-sel) > .ed-barra .ed-acciones{opacity:1}
.ed :focus-visible{outline:3px solid var(--pn-acento-borde);outline-offset:2px;border-radius:6px;box-shadow:0 0 0 2px #fff}

/* Datos que vienen de otras pantallas: contorno gris punteado y un aviso flotante con su candado */
.ed-dato{border-radius:3px;outline:2px dashed transparent;outline-offset:3px;cursor:default;transition:outline-color 150ms ease-out}
.ed-dato--caja{display:inline-block}
.ed-dato:is(:hover,.es-activo){outline-color:var(--pn-borde-control)}
.ed-dato:focus-within{outline:2px solid var(--pn-acento-borde)}
.presentacion-velo .ed-dato:is(:hover,.es-activo),.tarifas .ed-dato:is(:hover,.es-activo){outline-color:rgb(255 255 255/75%)}
.ed-candado{position:absolute;z-index:30;display:flex;align-items:center;gap:6px;margin:0;padding:6px 12px 6px 10px;border-radius:999px;background:var(--pn-superficie);box-shadow:var(--pn-sombra-menu);white-space:nowrap}
.ed-candado[hidden]{display:none}
.ed-candado svg{color:var(--pn-tinta-2)}
.ed-candado a{color:var(--pn-acento);font-weight:600;text-decoration:underline;text-underline-offset:2px}
.ed-candado > span + span{font-weight:600}
/* Una sede sin foto (el hueco solo sale en el editor) y notas del editor en los casos */
.fs-foto--vacia{display:grid;place-items:center;aspect-ratio:3/4;margin-top:16px;background:var(--anden)}
@media (min-width:900px){.fs-foto--vacia{margin-top:20px}}
.ed-vacia-sede{display:grid;justify-items:center;align-content:center;gap:8px;height:100%;padding:12px;text-align:center;color:var(--pn-tinta-2)}
.ed-vacia-sede svg{width:24px;height:24px}
.ed-nota-parte{display:flex;align-items:flex-start;gap:6px;max-width:62ch;margin:20px 0 0;padding:8px 10px;border-radius:8px;background:var(--pn-superficie);border:1px solid var(--pn-linea);color:var(--pn-tinta-2)}
.ed-nota-parte svg{margin-top:1px}
/* En móvil el nombre de «Horarios» va a la derecha: a la izquierda taparía su título, que empieza arriba del todo */
@media (max-width:767.98px){.ed-fija[data-bloque="horarios"] > .ed-barra .ed-fila{flex-direction:row-reverse}}
/* W12d: textos en el sitio (boceto: .ed-texto, .ed-chip, .ed-error). Al editar, la capa pone una copia del elemento
   (.ed-campo) con el texto editable (.ed-campo__texto) y esconde el original (.ed-escondido). */
.ed-texto{cursor:text;border-radius:3px;outline:2px dashed transparent;outline-offset:4px;transition:outline-color 150ms ease-out}
.ed-bloque:hover .ed-texto{outline-color:rgb(22 99 180/40%)}
.ed-texto:hover{outline-color:var(--pn-acento)!important}
.presentacion-velo .ed-texto:hover,.tarifas .ed-texto:hover{outline-color:#fff!important}
.ed-bloque:hover .presentacion-velo .ed-texto,.ed-bloque:hover .tarifas .ed-texto{outline-color:rgb(255 255 255/55%)}
.ed-texto:focus-visible{outline:3px solid var(--pn-acento-borde)!important;outline-offset:2px;border-radius:6px;box-shadow:0 0 0 2px #fff}
.ed-texto.es-vacio{outline-color:rgb(22 99 180/55%);opacity:.62}
.presentacion-velo .ed-texto.es-vacio,.tarifas .ed-texto.es-vacio{outline-color:rgb(255 255 255/70%)}
.ed-texto.es-error{outline:2px solid var(--pn-peligro)!important}
.ed-escondido{display:none!important}
.ed-campo{cursor:text}
.ed-campo__texto{outline:2px solid var(--pn-acento);outline-offset:4px;border-radius:3px;box-shadow:0 0 0 4px rgb(255 255 255/92%);caret-color:currentColor}
.ed-campo__texto:focus{outline:2px solid var(--pn-acento)}
.ed-campo__texto:empty::before{content:attr(data-vacio);opacity:.45}
.ed-campo__texto[aria-invalid="true"]{outline-color:var(--pn-peligro)}
.ed-multi{display:block;white-space:pre-wrap}
.ed-error{display:flex;align-items:flex-start;gap:6px;width:fit-content;max-width:100%;margin:10px 0 0;padding:6px 10px;border-radius:6px;background:var(--pn-superficie);color:var(--pn-peligro);font:600 .875rem/1.35 var(--pn-fuente);box-shadow:var(--pn-sombra-menu)}
.ed-error svg{margin-top:1px}
.ed-chip{position:absolute;z-index:30;display:flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid var(--pn-linea);border-radius:8px;background:var(--pn-superficie);box-shadow:var(--pn-sombra-menu);pointer-events:none;white-space:nowrap}
.ed-chip[hidden]{display:none}
.ed-chip b{font-weight:600}
.ed-marca{color:var(--pn-tinta-2)}
.ed-cont{margin-left:6px;padding-left:8px;border-left:1px solid var(--pn-linea);font-variant-numeric:tabular-nums;color:var(--pn-tinta-2)}
.ed-chip.es-tope .ed-cont{color:var(--pn-atencion);font-weight:600}
::selection{background:#cfe3fb;color:#12161c}
@media (prefers-reduced-motion:reduce){.ed-bloque::after,.ed-nombre,.ed-acciones,.ed-dato,.ed-texto{transition:none}}
`;
