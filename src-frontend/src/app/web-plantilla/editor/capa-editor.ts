import {
  CANDADOS_LINEA_COMARCAL,
  Candado,
  NOMBRE_DESTINO,
  NOMBRE_EQUIPO,
  PARTES_LINEA_COMARCAL,
  Parte,
  SIN_ENLACE_LINEA_COMARCAL,
} from './candados-linea-comarcal';
import { ESTILOS_CAPA_EDITOR } from './capa-editor-estilos';
import {
  BloqueBorrador,
  CANAL_EDITOR_WEB,
  MensajeDelEditor,
  enviarPorPuente,
  esDestinoPanel,
  esMensajeDelPanel,
  escucharPuente,
} from './puente-editor';

/**
 * Capa de edición dentro de la vista previa de «Web · Contenido» (W12c; boceto `boceto-panel-contenido-web-comarcal.html`:
 * `envolver`, `envolverFija`, `dato()`, `mostrarCandado()`). Chunk perezoso propio: lo carga `EditorWebService` solo
 * cuando la API confirma la vista previa de la gestión.
 *
 * No toca el tema: anota su DOM cada vez que se pinta (MutationObserver) con
 * - el envoltorio de cada bloque y de cada parte fija: nombre (botón que la elige) y, en las fijas, su nota;
 * - el contorno punteado de cada dato de otra pantalla, el aviso flotante «X · Se cambia en Y» con enlace (o «Lo
 *   cambia el equipo de Taemoi») y un enlace oculto por tipo de dato para el teclado;
 * - los enlaces y botones de la web, fuera del orden de tabulación y sin navegar.
 * Los enlaces de los candados no navegan aquí: piden el destino al panel por el puente.
 */

export interface OpcionesCapaEditor {
  /** La ventana de la vista previa (el iframe). */
  ventana: Window;
  /** Quien la contiene (el panel); por defecto, `ventana.parent`. */
  padre?: Window;
  /** Llega un borrador del panel. */
  alBorrador: (bloques: BloqueBorrador[]) => void;
}

const SVG = 'http://www.w3.org/2000/svg';
/** Controles propios de la web: fuera del tabulador y sin efecto en el editor. */
const CONTROLES_WEB = 'a, button, input, select, textarea, summary, [tabindex]';
const RETRASO_OCULTAR = 160;

function recorta(texto: string, maximo: number): string {
  return texto.length > maximo ? `${texto.slice(0, maximo - 1).trimEnd()}…` : texto;
}

export function activarCapaEditor(opciones: OpcionesCapaEditor): () => void {
  const { ventana, alBorrador } = opciones;
  const padre = opciones.padre ?? ventana.parent;
  const doc = ventana.document;
  let seleccionado: string | null = null;
  let datoActivo: HTMLElement | null = null;
  let temporizador: ReturnType<typeof setTimeout> | null = null;
  let ultimoAlto = -1;

  const enviar = (mensaje: MensajeDelEditor) => enviarPorPuente(ventana, padre, mensaje);

  // ------------------------------------------------------------------ estilos y aviso del candado
  const estilos = doc.createElement('style');
  estilos.setAttribute('data-capa-editor', '');
  estilos.textContent = ESTILOS_CAPA_EDITOR;
  doc.head.appendChild(estilos);

  const candado = doc.createElement('p');
  candado.className = 'ed ed-candado';
  candado.id = 'ed-candado';
  candado.setAttribute('aria-hidden', 'true');
  candado.hidden = true;
  doc.body.appendChild(candado);

  // ------------------------------------------------------------------ anotar el DOM del tema
  function nombreDe(parte: Parte, el: HTMLElement): string {
    if (parte.id !== 'texto_foto') {
      return parte.nombre;
    }
    const titulo = (el.querySelector('.titulo-seccion')?.textContent ?? '').trim();
    return titulo ? `${parte.nombre} «${recorta(titulo, 40)}»` : parte.nombre;
  }

  function envolver(parte: Parte, el: HTMLElement): void {
    const id = parte.id === 'texto_foto' && el.id ? el.id : parte.id;
    const nombre = nombreDe(parte, el);
    el.classList.add('ed-bloque');
    el.classList.toggle('ed-fija', parte.fija);
    el.dataset['bloque'] = id;
    el.setAttribute('role', 'group');
    el.setAttribute('aria-label', `${parte.fija ? 'Parte' : 'Bloque'} ${nombre}`);
    el.removeAttribute('aria-labelledby');
    let barra = el.querySelector<HTMLElement>(':scope > .ed-barra');
    if (!barra) {
      barra = doc.createElement('div');
      barra.className = 'ed ed-barra';
      const fila = doc.createElement('div');
      fila.className = 'ed-fila';
      const boton = doc.createElement('button');
      boton.type = 'button';
      boton.className = 'ed-nombre';
      boton.dataset['accion'] = 'sel';
      boton.textContent = parte.nombre;
      fila.appendChild(boton);
      if (parte.fija && parte.nota) {
        const acciones = doc.createElement('div');
        acciones.className = 'ed-acciones';
        const nota = doc.createElement('span');
        nota.className = 'ed-nota';
        nota.textContent = parte.nota;
        acciones.appendChild(nota);
        fila.appendChild(acciones);
      }
      barra.appendChild(fila);
      el.insertBefore(barra, el.firstChild);
    }
    const boton = barra.querySelector<HTMLButtonElement>('.ed-nombre')!;
    boton.dataset['bloque'] = id;
    boton.setAttribute('aria-label', `${nombre}: elegir ${parte.fija ? 'la parte' : 'el bloque'}`);
    boton.setAttribute('aria-pressed', String(seleccionado === id));
    el.classList.toggle('es-sel', seleccionado === id);
  }

  const claveDato = (el: HTMLElement) => `${el.dataset['que']}|${el.dataset['donde']}`;

  function marcarDato(candadoDef: Candado, el: HTMLElement): void {
    if (el.classList.contains('ed-dato')) {
      return;
    }
    el.classList.add('ed-dato');
    if (candadoDef.caja) {
      el.classList.add('ed-dato--caja');
    }
    el.dataset['que'] = candadoDef.que;
    el.dataset['donde'] = typeof candadoDef.donde === 'string' ? candadoDef.donde : candadoDef.donde.pantalla;
  }

  /** Un enlace oculto por tipo de dato con destino en el panel, en el primero que no esté dentro de un control. */
  function enlacesOcultos(): void {
    const con = new Set<string>();
    doc.querySelectorAll<HTMLElement>('.ed-dato__ir').forEach((a) => con.add(a.dataset['clave'] ?? ''));
    doc.querySelectorAll<HTMLElement>('.ed-dato').forEach((el) => {
      const donde = el.dataset['donde'];
      const clave = claveDato(el);
      if (!esDestinoPanel(donde) || con.has(clave) || el.closest(SIN_ENLACE_LINEA_COMARCAL)) {
        return;
      }
      const a = doc.createElement('a');
      a.href = '#';
      a.className = 'ed-oculto ed-dato__ir';
      a.dataset['accion'] = 'ir';
      a.dataset['destino'] = donde;
      a.dataset['clave'] = clave;
      a.textContent = `${el.dataset['que']}: se cambia en ${NOMBRE_DESTINO[donde]}`;
      el.appendChild(a);
      con.add(clave);
    });
  }

  function sinTabulador(): void {
    doc.querySelectorAll<HTMLElement>('.lc').forEach((raiz) =>
      raiz.querySelectorAll<HTMLElement>(CONTROLES_WEB).forEach((control) => {
        if (!control.closest('.ed') && !control.classList.contains('ed-dato__ir') && control.tabIndex !== -1) {
          control.tabIndex = -1;
        }
      }),
    );
  }

  function decorar(): void {
    for (const parte of PARTES_LINEA_COMARCAL) {
      doc.querySelectorAll<HTMLElement>(parte.selector).forEach((el) => envolver(parte, el));
    }
    for (const def of CANDADOS_LINEA_COMARCAL) {
      doc.querySelectorAll<HTMLElement>(def.selector).forEach((el) => marcarDato(def, el));
    }
    enlacesOcultos();
    sinTabulador();
    if (datoActivo && !datoActivo.isConnected) {
      ocultarCandado(true);
    }
  }

  // ------------------------------------------------------------------ selección
  function seleccionar(id: string | null): void {
    seleccionado = id;
    doc.querySelectorAll<HTMLElement>('.ed-bloque').forEach((bloque) => {
      const si = bloque.dataset['bloque'] === id;
      bloque.classList.toggle('es-sel', si);
      bloque.querySelector(':scope > .ed-barra .ed-nombre')?.setAttribute('aria-pressed', String(si));
    });
  }

  // ------------------------------------------------------------------ aviso del candado
  function mostrarCandado(dato: HTMLElement): void {
    limpiarTemporizador();
    if (datoActivo === dato && !candado.hidden) {
      return;
    }
    datoActivo?.classList.remove('es-activo');
    datoActivo = dato;
    dato.classList.add('es-activo');
    const donde = dato.dataset['donde'];
    candado.replaceChildren();
    const icono = doc.createElementNS(SVG, 'svg');
    icono.setAttribute('viewBox', '0 0 24 24');
    icono.setAttribute('fill', 'none');
    icono.setAttribute('stroke', 'currentColor');
    icono.setAttribute('stroke-width', '2');
    icono.setAttribute('stroke-linecap', 'round');
    icono.setAttribute('stroke-linejoin', 'round');
    icono.setAttribute('aria-hidden', 'true');
    const caja = doc.createElementNS(SVG, 'rect');
    for (const [k, v] of [['x', '5'], ['y', '10.5'], ['width', '14'], ['height', '10'], ['rx', '2']]) {
      caja.setAttribute(k, v);
    }
    const arco = doc.createElementNS(SVG, 'path');
    arco.setAttribute('d', 'M8 10.5V7.5a4 4 0 0 1 8 0v3');
    icono.append(caja, arco);
    const texto = doc.createElement('span');
    const verbo = donde === 'equipo' ? 'Lo cambia' : donde === 'web' ? 'Lo pone la web' : 'Se cambia en';
    texto.textContent = `${dato.dataset['que']} · ${verbo}`;
    candado.append(icono, texto);
    if (donde === 'equipo') {
      const quien = doc.createElement('span');
      quien.textContent = NOMBRE_EQUIPO;
      candado.appendChild(quien);
    } else if (esDestinoPanel(donde)) {
      const ir = doc.createElement('a');
      ir.href = '#';
      ir.tabIndex = -1;
      ir.dataset['accion'] = 'ir';
      ir.dataset['destino'] = donde;
      ir.textContent = NOMBRE_DESTINO[donde];
      candado.appendChild(ir);
    }
    candado.hidden = false;
    const r = dato.getBoundingClientRect();
    const alto = candado.offsetHeight;
    const ancho = candado.offsetWidth;
    let arriba = r.top + ventana.scrollY - alto - 8;
    if (r.top - alto - 8 < 4) {
      arriba = r.bottom + ventana.scrollY + 8;
    }
    candado.style.top = `${arriba}px`;
    candado.style.left = `${Math.max(8, Math.min(r.left + ventana.scrollX, doc.documentElement.clientWidth - ancho - 8))}px`;
  }

  function ocultarCandado(ya = false): void {
    limpiarTemporizador();
    const fin = () => {
      candado.hidden = true;
      datoActivo?.classList.remove('es-activo');
      datoActivo = null;
    };
    if (ya) {
      fin();
    } else {
      temporizador = setTimeout(fin, RETRASO_OCULTAR);
    }
  }

  function limpiarTemporizador(): void {
    if (temporizador !== null) {
      clearTimeout(temporizador);
      temporizador = null;
    }
  }

  // ------------------------------------------------------------------ eventos
  // El evento llega del documento del iframe: `instanceof Element` del panel no sirve entre ventanas.
  const elemento = (evento: Event): Element | null => {
    const t = evento.target as Element | null;
    return t && typeof t.closest === 'function' ? t : null;
  };

  const alPulsar = (evento: MouseEvent) => {
    const t = elemento(evento);
    if (!t) {
      return;
    }
    const accion = t.closest<HTMLElement>('[data-accion]');
    if (accion && (accion.closest('.ed') || accion.classList.contains('ed-dato__ir'))) {
      evento.preventDefault();
      if (accion.dataset['accion'] === 'sel') {
        const id = accion.dataset['bloque'] ?? null;
        seleccionar(seleccionado === id ? null : id);
      } else if (accion.dataset['accion'] === 'ir' && esDestinoPanel(accion.dataset['destino'])) {
        ocultarCandado(true);
        enviar({ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: accion.dataset['destino'] });
      }
      return;
    }
    // Los enlaces y botones de la propia web no navegan ni actúan en el editor.
    if (t.closest('.lc') && t.closest('a, button, input, select, textarea, summary, label')) {
      evento.preventDefault();
      evento.stopImmediatePropagation();
    }
  };

  const alTeclear = (evento: KeyboardEvent) => {
    if (evento.key !== 'Escape') {
      return;
    }
    if (!candado.hidden) {
      ocultarCandado(true);
      return;
    }
    if (seleccionado) {
      const id = seleccionado;
      seleccionar(null);
      doc.querySelector<HTMLElement>(`.ed-bloque[data-bloque="${CSS.escape(id)}"] > .ed-barra .ed-nombre`)?.focus();
    }
  };

  const alPasar = (evento: MouseEvent) => {
    const t = elemento(evento);
    const dato = t?.closest<HTMLElement>('.ed-dato');
    if (dato) {
      mostrarCandado(dato);
    } else if (t?.closest('#ed-candado')) {
      limpiarTemporizador();
    } else if (datoActivo) {
      ocultarCandado();
    }
  };

  const alEnfocar = (evento: FocusEvent) => {
    const t = elemento(evento);
    const ir = t?.closest<HTMLElement>('.ed-dato__ir');
    if (ir) {
      const dato = ir.closest<HTMLElement>('.ed-dato');
      if (dato) {
        mostrarCandado(dato);
      }
    } else if (datoActivo && !t?.closest('#ed-candado')) {
      ocultarCandado(true);
    }
    const bloque = t?.closest<HTMLElement>('.ed-bloque');
    if (bloque && seleccionado && seleccionado !== bloque.dataset['bloque']) {
      seleccionar(null);
    }
  };

  const alDesenfocar = (evento: FocusEvent) => {
    if (elemento(evento)?.closest('.ed-dato__ir')) {
      ocultarCandado();
    }
  };

  doc.addEventListener('click', alPulsar, true);
  doc.addEventListener('keydown', alTeclear);
  doc.addEventListener('mouseover', alPasar);
  doc.addEventListener('focusin', alEnfocar);
  doc.addEventListener('focusout', alDesenfocar);

  // ------------------------------------------------------------------ observadores y puente
  const observador = new MutationObserver(() => decorar());
  observador.observe(doc.body, { childList: true, subtree: true });

  const medir = () => {
    const alto = Math.ceil(doc.documentElement.scrollHeight);
    if (alto !== ultimoAlto) {
      ultimoAlto = alto;
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'alto', alto });
    }
  };
  const tamano = typeof ResizeObserver === 'function' ? new ResizeObserver(medir) : null;
  tamano?.observe(doc.body);

  const dejarDeEscuchar = escucharPuente(ventana, () => padre, esMensajeDelPanel, (mensaje) => {
    if (mensaje.acento) {
      const raiz = doc.documentElement.style;
      raiz.setProperty('--pn-acento', mensaje.acento.acento);
      raiz.setProperty('--pn-acento-borde', mensaje.acento.borde);
      raiz.setProperty('--pn-acento-suave', mensaje.acento.suave);
    }
    alBorrador(mensaje.bloques);
  });

  decorar();
  enviar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
  medir();

  return () => {
    dejarDeEscuchar();
    observador.disconnect();
    tamano?.disconnect();
    limpiarTemporizador();
    doc.removeEventListener('click', alPulsar, true);
    doc.removeEventListener('keydown', alTeclear);
    doc.removeEventListener('mouseover', alPasar);
    doc.removeEventListener('focusin', alEnfocar);
    doc.removeEventListener('focusout', alDesenfocar);
    estilos.remove();
    candado.remove();
  };
}
