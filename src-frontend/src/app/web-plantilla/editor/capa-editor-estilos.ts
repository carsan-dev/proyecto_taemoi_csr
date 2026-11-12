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
/* W12f-1: fotos sueltas (boceto: .ed-foto, .ed-nueva, .ed-subida, .ed-progreso, .ed-fallo, .ed-error--foto, .flot,
   .flot--describir, .campo-et, .area, .campo-pie, .contador, .btn--sec, .ed-btn). La capa va dentro de la foto del tema
   (.lc .foto ya es relative y recorta). */
:root{
  --pn-radio-s:6px;--pn-radio-m:8px;--pn-marca-primario-suave:#e8e9ea;--pn-serie-pista:#eceef1;--pn-transicion:150ms ease-out;
  --pn-etiqueta:600 .8125rem/1.2 var(--pn-fuente);--pn-secundario:400 .875rem/1.35 var(--pn-fuente);
  --pn-titulo-bloque:700 1.0625rem/1.25 var(--pn-fuente);
}
.lc .presentacion-velo{z-index:5;pointer-events:none}
.lc .presentacion-velo :is(.ed-texto,.ed-error,.ed-dato,.ed-campo){pointer-events:auto}
.ed-hueco-foto{background:#eef0f3}
.ed-foto{position:absolute;inset:0;z-index:3;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;width:100%;height:100%;margin:0;padding:12px;border:0;border-radius:inherit;background:none;cursor:pointer;transition:box-shadow 150ms ease-out}
.ed-foto__pista{display:inline-flex;align-items:center;gap:8px;min-height:36px;padding:0 14px;border-radius:999px;background:var(--pn-superficie);color:var(--pn-acento);font:600 .875rem/1 var(--pn-fuente);box-shadow:var(--pn-sombra-menu);opacity:0;transition:opacity 150ms ease-out}
.ed-foto__formatos{font:400 .8125rem/1.3 var(--pn-fuente);color:var(--pn-tinta-2)}
.ed-foto:hover,.ed-foto[aria-expanded=true]{box-shadow:var(--pn-anillo)}
.ed-foto:focus-visible{outline:none!important;box-shadow:inset 0 0 0 3px var(--pn-acento-borde),inset 0 0 0 5px #fff}
.ed-foto:is(:hover,:focus-visible,[aria-expanded=true]) .ed-foto__pista,.ed-foto.es-vacia .ed-foto__pista{opacity:1}
.ed-foto.es-vacia{border:2px dashed var(--pn-borde-control);background:#eef0f3}
.portada-foto > .ed-foto.es-vacia{background:#2a3038;border-color:#868d97}
.portada-foto > .ed-foto.es-vacia .ed-foto__formatos{color:#c3c8d0}
.ed-foto.es-error{box-shadow:inset 0 0 0 3px var(--pn-peligro),inset 0 0 0 5px #fff}
.ed-foto--arriba{align-items:flex-start;justify-content:flex-start;padding-top:64px}
.ed-error--foto{position:absolute;left:10px;bottom:10px;z-index:4;margin:0}
.ed-punto{width:7px;height:7px;border-radius:50%;background:var(--pn-acento);flex:none}
.ed-nueva{position:absolute;left:10px;top:10px;z-index:4;display:inline-flex;align-items:center;gap:6px;min-height:26px;padding:0 10px;border-radius:999px;background:var(--pn-acento-suave);color:var(--pn-acento);font-weight:600}
.ed-foto__previa{position:absolute;inset:0;z-index:2;width:100%;height:100%;max-width:none;object-fit:cover;opacity:.55}
.es-subiendo > img{opacity:.55}
.ed-subida{position:absolute;left:12px;right:12px;bottom:12px;z-index:5;display:grid;gap:8px;padding:12px;border-radius:8px;background:rgb(255 255 255/96%);box-shadow:var(--pn-sombra-menu);font-weight:600}
.ed-subida__fila{display:flex;justify-content:space-between;gap:8px}
.ed-subida__fila span:last-child{font-variant-numeric:tabular-nums;color:var(--pn-tinta-2)}
.ed-progreso{height:6px;border-radius:3px;background:var(--pn-serie-pista);overflow:hidden}
.ed-progreso i{display:block;width:var(--p,0%);height:100%;border-radius:3px;background:var(--pn-acento);transition:width 300ms ease-out}
.ed-subida .ed-btn{justify-self:start;margin-left:-10px}
.ed-fallo{position:absolute;inset:12px;z-index:5;display:grid;align-content:center;justify-items:start;gap:8px;padding:16px;border:2px solid var(--pn-peligro);border-radius:8px;background:rgb(255 255 255/97%)}
.ed-fallo p{margin:0}
.ed-fallo__t{display:flex;align-items:center;gap:6px;color:var(--pn-peligro);font-weight:600;font-size:.9375rem}
.ed-fallo__n{color:var(--pn-tinta-2)}
.ed-fallo__acc{display:flex;flex-wrap:wrap;gap:6px;margin-top:4px}
.ed-btn{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-height:32px;padding:0 10px;border:0;border-radius:6px;background:none;color:var(--pn-acento);font:600 .8125rem/1 var(--pn-fuente);white-space:nowrap;cursor:pointer;transition:background-color 150ms ease-out}
.ed-btn:hover{background:var(--pn-acento-suave)}
.ed-btn--sec{border:1px solid var(--pn-acento-borde);background:var(--pn-superficie)}
/* Menú y diálogo de la foto */
.flot{position:absolute;z-index:58;width:320px;max-width:calc(100vw - 16px);margin:0;padding:6px;border:1px solid var(--pn-linea);border-radius:var(--pn-radio-m);background:var(--pn-superficie);box-shadow:var(--pn-sombra-menu)}
.flot[hidden]{display:none}
.flot__titulo{margin:6px 10px 8px;font:var(--pn-etiqueta);color:var(--pn-tinta-2)}
.flot__lista{display:grid;gap:2px}
.flot__op{display:flex;align-items:flex-start;gap:10px;width:100%;min-height:40px;padding:9px 10px;border:0;border-radius:var(--pn-radio-s);background:none;color:var(--pn-tinta);text-align:left;font:600 .9375rem/1.3 var(--pn-fuente);cursor:pointer;transition:background-color var(--pn-transicion)}
.flot__op svg{width:18px;height:18px;margin-top:1px;flex:none;color:var(--pn-acento)}
.flot__op small{display:block;margin-top:2px;font:var(--pn-secundario);font-size:.8125rem;color:var(--pn-tinta-2)}
.flot__op:hover{background:var(--pn-marca-primario-suave)}
.flot__op:focus-visible{outline-offset:-3px;background:var(--pn-acento-suave)}
.flot__op--peligro,.flot__op--peligro svg{color:var(--pn-peligro)}
.flot__sep{height:1px;margin:4px 6px;background:var(--pn-linea)}
.flot--describir{padding:14px 16px 16px;width:360px}
.flot--describir .flot__titulo{margin:0 0 12px;font:var(--pn-titulo-bloque);color:var(--pn-tinta)}
.campo-et{display:block;margin-bottom:6px;font:var(--pn-etiqueta)}
.campo-et .marca{margin-left:6px;font-weight:400;color:var(--pn-tinta-2)}
.area{display:block;box-sizing:border-box;width:100%;min-height:88px;margin:0;padding:10px 12px;border:1px solid var(--pn-borde-control);border-radius:var(--pn-radio-m);background:var(--pn-superficie);color:var(--pn-tinta);font:400 .9375rem/1.5 var(--pn-fuente);resize:vertical;caret-color:var(--pn-acento)}
.area:hover{border-color:var(--pn-tinta-2)}
.area::placeholder{color:var(--pn-tinta-2);opacity:1}
.campo-pie{display:flex;justify-content:space-between;gap:12px;margin-top:6px;font:var(--pn-secundario);font-size:.8125rem;color:var(--pn-tinta-2)}
.campo-pie p{margin:0}
.contador{flex:none;font-variant-numeric:tabular-nums;white-space:nowrap}
.contador.es-tope{color:var(--pn-atencion);font-weight:600}
.flot__acciones{display:flex;justify-content:flex-end;margin-top:14px}
.btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:40px;padding:0 16px;border-radius:var(--pn-radio-m);font:var(--pn-etiqueta);font-size:.875rem;white-space:nowrap;cursor:pointer;transition:background-color var(--pn-transicion),border-color var(--pn-transicion)}
.btn--sec{border:1px solid var(--pn-acento-borde);background:var(--pn-superficie);color:var(--pn-acento)}
.btn--sec:hover{background:var(--pn-acento-suave)}
/* En el ancho de móvil la pista se ve siempre (boceto .estrecho) y el diálogo ocupa el ancho */
@media (max-width:767.98px){
  .ed-foto__pista{opacity:1}
  .ed-foto:not(.es-vacia) .ed-foto__pista{min-height:30px;padding:0 10px;font-size:.8125rem}
  .flot__op{min-height:48px}
  .flot--describir{width:auto;right:8px}
  .flot--describir .btn{flex:1;min-height:44px}
}
@media (prefers-reduced-motion:reduce){.ed-foto,.ed-foto__pista,.ed-progreso i,.ed-btn,.flot__op,.btn{transition:none}}
/* W12e: barra del bloque (boceto .ed-acciones con .ed-btn, .ed-estados, .ed-pastilla, .ed-mini, .es-oculto), huecos
   «Añadir bloque» (.ed-hueco, .ed-anadir) y su menú. Sin .ed-web en el tema: lo atenuado es lo que no es de la capa. */
.ed-estados{display:flex;flex-wrap:wrap;align-items:flex-start;gap:6px;pointer-events:none}
.ed-estados > *{pointer-events:auto}
.ed-btn[aria-disabled=true]{color:var(--pn-tinta-3);opacity:.55;cursor:default;background:none}
.ed-btn--quitar{color:var(--pn-peligro)}
.ed-btn--quitar:hover{background:#fbf4f3}
.ed-pastilla{display:inline-flex;align-items:center;gap:6px;min-height:30px;padding:0 10px;border-radius:999px;background:var(--pn-superficie);color:var(--pn-tinta);font:600 .8125rem/1 var(--pn-fuente);box-shadow:var(--pn-sombra-menu);white-space:nowrap}
.ed-pastilla svg{color:var(--pn-tinta-2)}
.ed-pastilla--oculto{padding-right:3px}
.ed-pastilla--cambio{color:var(--pn-acento)}
.ed-mini{min-height:24px;padding:0 8px;border:0;border-radius:999px;background:var(--pn-acento);color:#fff;font:600 .8125rem/1 var(--pn-fuente);cursor:pointer}
.ed-mini:hover{background:color-mix(in srgb,var(--pn-acento) 86%,#000)}
.ed-bloque.es-oculto > :not(.ed){opacity:.38;filter:saturate(.4)}
.ed-hueco{position:relative;z-index:17;height:0;display:flex;justify-content:center}
.ed-hueco::before{content:"";position:absolute;left:0;right:0;top:-1px;height:2px;background:var(--pn-acento);opacity:0;transition:opacity 150ms ease-out;pointer-events:none}
.ed-hueco:is(:hover,:focus-within)::before{opacity:1}
.ed-anadir{position:absolute;top:0;transform:translateY(-50%);display:inline-flex;align-items:center;gap:6px;min-height:32px;padding:0 12px 0 10px;border:1px dashed var(--pn-acento-borde);border-radius:999px;background:var(--pn-superficie);color:var(--pn-acento);font:600 .8125rem/1 var(--pn-fuente);cursor:pointer;transition:background-color 150ms ease-out}
.ed-anadir:hover,.ed-anadir[aria-expanded=true]{border-style:solid;background:var(--pn-acento-suave)}
.ed-anadir:focus-visible{outline:3px solid var(--pn-acento-borde);outline-offset:2px;box-shadow:0 0 0 2px #fff}
.flot__op[aria-disabled=true]{color:var(--pn-tinta-2);cursor:default;background:none}
.flot__op[aria-disabled=true] svg{color:var(--pn-tinta-3)}
/* Panel estrecho (< 768, lo dice el panel): nombre siempre a la vista; las acciones salen en la hoja del panel */
.estrecho .ed-nombre{opacity:1}
.estrecho .ed-acciones{display:none}
@media (prefers-reduced-motion:reduce){.ed-hueco::before,.ed-anadir,.ed-mini{transition:none}}
`;
