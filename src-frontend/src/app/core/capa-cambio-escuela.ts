import { resolverPortalContexto } from '../app.routes';
import { EVENTO_NOMBRE_CAPA, OpcionesCapaCambioEscuela } from './cambio-escuela-pestanas';

/**
 * Capa «Has cambiado de escuela» de las DEMÁS pestañas (boceto aprobado
 * `docs/multi-escuela/boceto-aviso-cambio-escuela-pestana.html`). Va en su propio chunk: solo la carga
 * `CambioEscuelaPestanasService` cuando hay que mostrarla, con sus estilos (no engorda el bundle inicial).
 *
 * Igual en los tres sitios: `<dialog>` con `showModal()` (fondo inerte), `role=alertdialog`, sin X; Esc y el clic fuera
 * no la cierran; el foco va al botón y no sale de la capa. «Recargar» pasa a «Recargando…» (`aria-busy`,
 * `aria-disabled`: el foco se queda en él) y lo anuncia una región `role=status`. Cambia la forma según el sitio:
 * - `panel`: diálogo del panel (dialogo.component.scss, DESIGN.md «Diálogo y hoja») sin color de escuela; por debajo de
 *   768 px, hoja inferior sin asa;
 * - `familias`: forma de los SweetAlert2 del área con su azul e icono de recarga;
 * - `web`: caja blanca con el logo y botón en píldora del oscuro de la marca.
 */

export type SitioCapaCambioEscuela = 'panel' | 'familias' | 'web';

export const TITULO_CAPA_CAMBIO_ESCUELA = 'Has cambiado de escuela';
const INICIO_FRASE = 'Lo hiciste en otra pestaña. Recarga esta para seguir con los datos de ';
const SIN_NOMBRE = 'la escuela nueva';
const LOGO_WEB = '/assets/media/Logo_escuela.svg';
const ID_ESTILOS = 'taemoi-cambio-escuela-estilos';
const ID_TITULO = 'taemoi-cambio-escuela-titulo';
const ID_TEXTO = 'taemoi-cambio-escuela-texto';

const ICONO_GIRANDO =
  '<svg class="tce-girando" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"><path d="M12 3a9 9 0 1 1-9 9"/></svg>';
const ICONO_RECARGAR =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 11a8 8 0 0 0-14.5-4.6L4 8"/><path d="M4 3.5V8h4.5"/><path d="M4 13a8 8 0 0 0 14.5 4.6L20 16"/><path d="M20 20.5V16h-4.5"/></svg>';

/**
 * El sitio por el área que pinta la pestaña, con la lógica de la app: el marco del panel nuevo (`.marco-panel` del
 * esqueleto, cabecera nueva o su hueco) es el panel; el portal `user` de las rutas, el área de familias; lo demás, la
 * web. Una ruta de gestión con la cabecera de hoy (sin panel nuevo) usa la forma de familias: es la de sus avisos.
 */
export function sitioDeLaPestana(
  documento: Document,
  ruta: string = documento.defaultView?.location.pathname ?? '/'
): SitioCapaCambioEscuela {
  if (documento.querySelector('.marco-panel')) {
    return 'panel';
  }
  const contexto = resolverPortalContexto(ruta);
  return contexto === 'public' ? 'web' : 'familias';
}

/** Pinta la capa en el sitio de la pestaña y la abre como modal. */
export function pintarCapaCambioEscuela(
  documento: Document,
  opciones: OpcionesCapaCambioEscuela,
  sitio: SitioCapaCambioEscuela = sitioDeLaPestana(documento)
): HTMLDialogElement {
  ponerEstilos(documento);
  const capa = documento.createElement('dialog');
  capa.className = `taemoi-cambio-escuela tce-${sitio}`;
  capa.dataset['sitio'] = sitio;
  capa.setAttribute('role', 'alertdialog');
  capa.setAttribute('aria-modal', 'true');
  capa.setAttribute('aria-labelledby', ID_TITULO);
  capa.setAttribute('aria-describedby', ID_TEXTO);
  // Ni el clic fuera ni Esc la cierran (navegadores con `closedby`).
  capa.setAttribute('closedby', 'none');

  const titulo = documento.createElement('h2');
  titulo.id = ID_TITULO;
  titulo.className = `tce-${sitio}__titulo`;
  titulo.textContent = TITULO_CAPA_CAMBIO_ESCUELA;

  const texto = documento.createElement('p');
  texto.id = ID_TEXTO;
  texto.className = `tce-${sitio}__texto`;
  escribirFrase(documento, texto, opciones.nombre);

  const boton = documento.createElement('button');
  boton.type = 'button';
  boton.className = `tce-${sitio}__accion`;
  boton.innerHTML = '<span>Recargar</span>';

  const voz = documento.createElement('p');
  voz.className = 'tce-oculto';
  voz.setAttribute('role', 'status');

  if (sitio === 'familias') {
    const icono = documento.createElement('div');
    icono.className = 'tce-familias__icono';
    icono.setAttribute('aria-hidden', 'true');
    icono.innerHTML = ICONO_RECARGAR;
    const acciones = documento.createElement('div');
    acciones.className = 'tce-familias__acciones';
    acciones.append(boton);
    capa.append(icono, titulo, texto, acciones, voz);
  } else if (sitio === 'web') {
    const logo = documento.createElement('img');
    logo.className = 'tce-web__logo';
    logo.src = LOGO_WEB;
    logo.alt = '';
    capa.append(logo, titulo, texto, boton, voz);
  } else {
    const pie = documento.createElement('div');
    pie.className = 'tce-panel__pie';
    pie.append(boton);
    capa.append(titulo, texto, pie, voz);
  }

  boton.addEventListener('click', () => {
    if (boton.getAttribute('aria-disabled') === 'true') {
      return;
    }
    boton.setAttribute('aria-disabled', 'true');
    boton.setAttribute('aria-busy', 'true');
    boton.innerHTML = `${ICONO_GIRANDO}<span>Recargando…</span>`;
    voz.textContent = 'Recargando…';
    opciones.alRecargar();
  });
  // Único control: Tab y Mayús+Tab se quedan en el botón; Esc no hace nada.
  capa.addEventListener('keydown', (evento) => {
    if (evento.key === 'Tab') {
      evento.preventDefault();
      boton.focus();
    } else if (evento.key === 'Escape') {
      evento.preventDefault();
    }
  });
  capa.addEventListener('cancel', (evento) => evento.preventDefault());
  // Si el navegador la cierra igual (Chrome deja cerrar con un segundo Esc), vuelve a abrirse.
  capa.addEventListener('close', () => {
    if (capa.isConnected) {
      abrir(capa);
      boton.focus();
    }
  });
  capa.addEventListener(EVENTO_NOMBRE_CAPA, (evento) => {
    const nombre = (evento as CustomEvent<unknown>).detail;
    if (typeof nombre === 'string' && nombre) {
      escribirFrase(documento, texto, nombre);
    }
  });

  documento.body.appendChild(capa);
  abrir(capa);
  boton.focus({ preventScroll: true });
  return capa;
}

/** «… con los datos de <strong>{nombre}</strong>.» El nombre llega de otra pestaña: siempre como texto, nunca HTML. */
function escribirFrase(documento: Document, texto: HTMLElement, nombre: string | null): void {
  if (nombre) {
    const fuerte = documento.createElement('strong');
    fuerte.textContent = nombre;
    texto.replaceChildren(INICIO_FRASE, fuerte, '.');
  } else {
    texto.replaceChildren(`${INICIO_FRASE}${SIN_NOMBRE}.`);
  }
}

function abrir(capa: HTMLDialogElement): void {
  if (capa.open) {
    return;
  }
  try {
    capa.showModal();
  } catch {
    // Sin `showModal` (navegador antiguo): al menos a la vista.
    capa.setAttribute('open', '');
  }
}

function ponerEstilos(documento: Document): void {
  if (documento.getElementById(ID_ESTILOS)) {
    return;
  }
  const estilos = documento.createElement('style');
  estilos.id = ID_ESTILOS;
  estilos.textContent = ESTILOS;
  documento.head.appendChild(estilos);
}

// Medidas y colores del boceto aprobado (sus tres apartados de «De dónde sale cada estilo»).
const ESTILOS = `
dialog.taemoi-cambio-escuela{box-sizing:border-box;max-height:calc(100dvh - 32px);margin:auto;overflow-y:auto;overscroll-behavior:contain;border:0;-webkit-font-smoothing:antialiased}
dialog.taemoi-cambio-escuela *,dialog.taemoi-cambio-escuela *::before,dialog.taemoi-cambio-escuela *::after{box-sizing:border-box}
dialog.taemoi-cambio-escuela button{font:inherit}
dialog.taemoi-cambio-escuela button[aria-disabled=true]{cursor:progress}
.tce-oculto{position:absolute;width:1px;height:1px;margin:-1px;padding:0;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
.tce-girando{width:16px;height:16px;flex:none;animation:tce-girar .8s linear infinite}
@keyframes tce-girar{to{transform:rotate(360deg)}}

dialog.tce-panel{width:440px;max-width:calc(100vw - 32px);padding:0;border-radius:8px;background:#fff;color:#1a2230;font:400 .9375rem/1.45 "Public Sans","Segoe UI",system-ui,sans-serif;box-shadow:0 24px 48px -12px rgb(26 34 48/28%),0 4px 10px rgb(26 34 48/8%)}
dialog.tce-panel::backdrop{background:rgb(26 34 48/40%)}
dialog.tce-panel ::selection{background:#e6efff;color:#1a2230}
.tce-panel__titulo{margin:0;padding:24px 24px 0;font:700 1.125rem/1.3 "Montserrat Panel",montserrat,system-ui,sans-serif;color:#1a2230;text-wrap:balance}
.tce-panel__texto{margin:0;padding:8px 24px 0;max-width:60ch;color:#5d6573;text-wrap:pretty}
.tce-panel__texto strong{color:#1a2230;font-weight:600}
.tce-panel__pie{display:flex;justify-content:flex-end;padding:24px}
.tce-panel__accion{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-width:120px;min-height:44px;padding:0 18px;border:0;border-radius:8px;background:#1a2230;color:#fff;font:600 .9375rem/1 "Public Sans","Segoe UI",system-ui,sans-serif;cursor:pointer;transition:background-color 150ms ease-out}
.tce-panel__accion:hover:not([aria-disabled=true]){background:color-mix(in srgb,#1a2230 86%,#000)}
.tce-panel__accion:focus-visible{outline:3px solid #6f8abb;outline-offset:2px}
.tce-panel__accion[aria-disabled=true]{color:#c6c8cb}
@media (max-width:767.98px){
dialog.tce-panel{width:100%;max-width:100%;max-height:calc(100dvh - 16px);margin:auto 0 0;padding:4px 16px calc(24px + env(safe-area-inset-bottom,0px));border-radius:14px 14px 0 0;box-shadow:0 -12px 32px -8px rgb(26 34 48/28%)}
.tce-panel__titulo{padding:20px 0 0}
.tce-panel__texto{padding:8px 0 0}
.tce-panel__pie{padding:24px 0 0}
.tce-panel__accion{width:100%}
}

dialog.tce-familias{width:32em;max-width:calc(100% - 20px);padding:0 0 1.25em;border-radius:5px;background:#fff;color:#545454;font-family:Montserrat,sans-serif;font-size:1rem;text-align:center;animation:tce-swal .3s}
dialog.tce-familias::backdrop{background:rgb(0 0 0/40%)}
.tce-familias__icono{display:grid;place-items:center;width:5em;height:5em;margin:2.5em auto .6em;border:.25em solid #9ec5fe;border-radius:50%;color:#0d6efd}
.tce-familias__icono svg{width:2.3em;height:2.3em}
.tce-familias__titulo{margin:0;padding:.8em 1em 0;font-size:1.875em;font-weight:700;line-height:1.2;color:#545454;text-wrap:balance}
.tce-familias__texto{margin:0;padding:1em 1.6em .3em;font-size:1.125em;line-height:1.5;text-wrap:pretty}
.tce-familias__texto strong{color:#2f2f2f;font-weight:700}
.tce-familias__acciones{display:flex;justify-content:center;margin:1.25em auto 0;padding:0 1.6em}
.tce-familias__accion{display:inline-flex;align-items:center;justify-content:center;gap:.5em;min-width:9em;min-height:44px;margin:.3125em;padding:.625em 1.4em;border:0;border-radius:.25em;background:#0a58ca;color:#fff;font-size:1em;font-weight:700;cursor:pointer;transition:background-color .1s,box-shadow .1s}
.tce-familias__accion:hover:not([aria-disabled=true]){background:#084298}
.tce-familias__accion:focus-visible{outline:3px solid #0a58ca;outline-offset:3px}
@keyframes tce-swal{0%{transform:scale(.7)}45%{transform:scale(1.05)}80%{transform:scale(.95)}100%{transform:scale(1)}}
@media (max-width:575.98px){
.tce-familias__icono{margin-top:1.75em}
.tce-familias__titulo{font-size:1.6em}
.tce-familias__texto{padding:.9em 1.2em .3em;font-size:1.0625em}
.tce-familias__acciones{padding:0 1.2em}
.tce-familias__accion{width:100%;margin:.3125em 0}
}

dialog.tce-web{width:440px;max-width:calc(100% - 40px);padding:32px 32px 28px;border-radius:16px;background:#fff;color:#212529;font-family:Montserrat,sans-serif;text-align:center;box-shadow:0 8px 32px rgb(0 0 0/20%),0 2px 8px rgb(0 0 0/8%);animation:tce-subir .22s cubic-bezier(.22,1,.36,1)}
dialog.tce-web::backdrop{background:rgb(10 18 20/62%);animation:tce-aparecer .22s ease-out}
.tce-web__logo{display:block;width:72px;height:72px;margin:0 auto 18px}
.tce-web__titulo{margin:0;font-size:1.5rem;font-weight:700;line-height:1.2;color:#1b2b2e;text-wrap:balance}
.tce-web__texto{margin:12px 0 0;font-size:1rem;line-height:1.55;color:#4b5563;text-wrap:pretty}
.tce-web__texto strong{color:#1b2b2e;font-weight:700}
.tce-web__accion{display:inline-flex;align-items:center;justify-content:center;gap:10px;min-width:180px;min-height:48px;margin-top:26px;padding:0 32px;border:0;border-radius:50px;background:#1b2b2e;color:#fff;font-size:1rem;font-weight:700;cursor:pointer;transition:background-color .2s ease-out}
.tce-web__accion:hover:not([aria-disabled=true]){background:#284747}
.tce-web__accion:focus-visible{outline:3px solid #0f6a72;outline-offset:3px}
.tce-web__accion[aria-disabled=true]{color:#c6cacb}
@keyframes tce-aparecer{from{opacity:0}}
@keyframes tce-subir{from{opacity:0;transform:translateY(8px)}}
@media (max-width:575.98px){
dialog.tce-web{padding:28px 20px 22px}
.tce-web__titulo{font-size:1.3rem}
.tce-web__accion{width:100%}
}

@media (prefers-reduced-motion:reduce){
dialog.taemoi-cambio-escuela,dialog.taemoi-cambio-escuela::backdrop,dialog.taemoi-cambio-escuela *{animation:none!important;transition:none!important}
}
`;
