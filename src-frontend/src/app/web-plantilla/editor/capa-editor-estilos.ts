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
  --pn-anillo:inset 0 0 0 2px var(--pn-acento),inset 0 0 0 3px rgb(255 255 255/90%);
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
/* En móvil el nombre de «Horarios» va a la derecha: a la izquierda taparía su título, que empieza arriba del todo */
@media (max-width:767.98px){.ed-fija[data-bloque="horarios"] > .ed-barra .ed-fila{flex-direction:row-reverse}}
::selection{background:#cfe3fb;color:#12161c}
@media (prefers-reduced-motion:reduce){.ed-bloque::after,.ed-nombre,.ed-acciones,.ed-dato{transition:none}}
`;
