import type { PaginaEditor } from './candados-linea-comarcal';
import { encajarEnVista } from './colocar-flotantes';
import {
  type EstadoProfesor,
  type SedeElegible,
  TEXTOS_PROFESORES,
  clavePermiso,
  etiquetaQuitarProfesor,
  nombreProfesor,
  numeroPermiso,
  numeroSedes,
  tituloSedesProfesor,
} from './fotos-profesores';
import { CANAL_EDITOR_WEB, type MensajeDelEditor } from './puente-editor';

/**
 * Profesores en el sitio (W12f-2b; boceto `boceto-panel-contenido-web-comarcal.html`: `profeHtml`, `profesoresHtml`,
 * `anadirProfe`, `quitarProfe`, la casilla `[data-permiso]` y `enfocar`). Parte de la capa de edición (su chunk).
 *
 * En cada tarjeta que el tema marca en el editor (`li.profe[data-ed-profesor]`) pone, tras sus textos:
 * - la casilla obligatoria «Tengo su permiso…» (profesor nuevo o con el nombre cambiado) o la constancia guardada
 *   («Permiso marcado por … el …», «Permiso dado en el alta el …»), que el panel manda ya en texto (d521: solo el editor);
 * - la barra con «Quitar» (el panel confirma).
 * Tras las tarjetas, «Añadir profesor» («Añade el primer profesor» sin ninguno). La foto y los textos los ponen
 * `fotos-editor.ts` y `edicion-textos.ts`. La capa no cambia el borrador: pide cada acción al panel (`profesor`).
 *
 * W12f-3 (boceto `profeHtml`, `abrirSedesProfe`, `listo-sedes`; d520): bajo la línea de sedes del tema, «Elegir sus
 * sedes» o «Cambiar sus sedes» (y, sin ninguna, «Sin sedes: su tarjeta sale sin la línea.»), solo si hay sedes que
 * elegir. Abre un diálogo con una casilla por sede; cada cambio pide al panel el conjunto entero (`sedes`) y las casillas
 * pintan lo que el panel contesta (`profesores`). «Listo» o Escape lo cierran y devuelven el foco al botón; solo «Listo»
 * pide al panel que diga las sedes (`anunciar-sedes`). Con el panel estrecho, el diálogo es una hoja del panel (`hoja-sedes`).
 */
export interface OpcionesProfesoresEditor {
  ventana: Window;
  enviar: (mensaje: MensajeDelEditor) => void;
  /** Entra a editar un texto (el nombre del profesor recién añadido); false si aún no está. */
  editar: (clave: string) => boolean;
  /** W12f-3: el panel es estrecho (< 768 px): las sedes de un profesor se eligen en la hoja inferior del panel. */
  estrecho?: () => boolean;
}

export interface ProfesoresEditor {
  /** Llegan los profesores del borrador con su casilla o su constancia y (W12f-3) las sedes elegibles (`profesores`). */
  estado(lista: EstadoProfesor[], sedes?: SedeElegible[]): void;
  /** W12f-3: el panel cerró la hoja de sedes `id` (`respuesta-hoja-sedes`). */
  respuestaHoja(id: number, devolverFoco: boolean): void;
  errores(errores: Record<string, string>): void;
  /** Tras una acción del panel, el foco (`foco-profesor`); se pone cuando el tema haya pintado. */
  enfocar(foco: string): void;
  /** Lleva el foco a la casilla de un profesor (enlace del resumen); null si no es de aquí o no está. */
  irA(clave: string): number | null;
  decorar(pagina: PaginaEditor): void;
  quitar(): void;
}

const SVG = 'http://www.w3.org/2000/svg';
type Figura = readonly [string, Readonly<Record<string, string>>];
/** Iconos del boceto (`#i-mas`, `#i-papelera`, `#i-check`, `#i-aviso`, `#i-pin`). */
const ICONOS: Readonly<Record<string, { trazo: string; figuras: readonly Figura[] }>> = {
  mas: { trazo: '2', figuras: [['path', { d: 'M12 5v14M5 12h14' }]] },
  pin: {
    trazo: '1.8',
    figuras: [
      ['path', { d: 'M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z' }],
      ['circle', { cx: '12', cy: '10', r: '2.3' }],
    ],
  },
  papelera: { trazo: '2', figuras: [['path', { d: 'M4 7h16M10 11v6M14 11v6M6 7l1 12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-12M9 7V4.5h6V7' }]] },
  check: { trazo: '3', figuras: [['path', { d: 'm5 12 5 5 9-10' }]] },
  aviso: { trazo: '2', figuras: [['circle', { cx: '12', cy: '12', r: '9' }], ['path', { d: 'M12 7.5v5.5M12 16.5h.01' }]] },
};

/** Lo que espera un foco pedido por el panel antes de darlo por perdido (el tema pinta en el siguiente ciclo). */
const ESPERA_FOCO = 1500;
const TARJETAS = 'section#profesores ul.profes > li.profe[data-ed-profesor]';

export function activarProfesoresEditor(opciones: OpcionesProfesoresEditor): ProfesoresEditor {
  const { ventana, enviar, editar } = opciones;
  const estrecho = opciones.estrecho ?? (() => false);
  const doc = ventana.document;
  let lista: EstadoProfesor[] = [];
  /** W12f-3: las sedes que se pueden elegir (vacía: no se pinta nada para elegir). */
  let elegibles: SedeElegible[] = [];
  let errores: Record<string, string> = {};
  let pagina: PaginaEditor = 'inicio';
  let focoPendiente: { foco: string; hasta: number } | null = null;
  /** W12f-3: el diálogo de sedes abierto (de qué profesor) o, con `enHoja`, la hoja del panel que lo hace de diálogo. */
  let abierto: { n: number; enHoja?: { id: number; avisar: boolean } } | null = null;
  /** Numera cada apertura de la hoja de sedes; lo que llega con otro número se descarta. */
  let ultimaHoja = 0;

  const flot = doc.createElement('div');
  flot.className = 'ed flot flot--sedes';
  flot.hidden = true;
  doc.body.appendChild(flot);

  // ------------------------------------------------------------------ utilidades
  function icono(nombre: keyof typeof ICONOS): SVGSVGElement {
    const def = ICONOS[nombre];
    const svg = doc.createElementNS(SVG, 'svg');
    const atributos: [string, string][] = [
      ['viewBox', '0 0 24 24'],
      ['fill', 'none'],
      ['stroke', 'currentColor'],
      ['stroke-width', def.trazo],
      ['stroke-linecap', 'round'],
      ['stroke-linejoin', 'round'],
      ['aria-hidden', 'true'],
    ];
    for (const [k, v] of atributos) {
      svg.setAttribute(k, v);
    }
    for (const [etiqueta, propios] of def.figuras) {
      const figura = doc.createElementNS(SVG, etiqueta);
      for (const [k, v] of Object.entries(propios)) {
        figura.setAttribute(k, v);
      }
      svg.appendChild(figura);
    }
    return svg;
  }

  function nodo<K extends keyof HTMLElementTagNameMap>(etiqueta: K, clase: string, ...hijos: (Node | string)[]): HTMLElementTagNameMap[K] {
    const el = doc.createElement(etiqueta);
    if (clase) {
      el.className = clase;
    }
    el.append(...hijos);
    return el;
  }

  const numeroDe = (li: HTMLElement) => {
    const n = li.dataset['edProfesor'] ?? '';
    return /^[1-9][0-9]?$/.test(n) ? Number(n) : null;
  };
  const tarjetas = () => (pagina === 'inicio' ? Array.from(doc.querySelectorAll<HTMLElement>(TARJETAS)) : []);
  const casilla = (n: number) => doc.querySelector<HTMLInputElement>(`input[data-permiso="${n}"]`);
  const botonSedes = (n: number) => doc.querySelector<HTMLButtonElement>(`.ed-sedes [data-k="sp:${n}"]`);

  // ------------------------------------------------------------------ sedes (W12f-3; boceto `profeHtml`, d520)
  /**
   * «Elegir sus sedes» o «Cambiar sus sedes» (con « de {nombre}» solo para el lector) y, sin ninguna, el párrafo. Va antes
   * de la casilla o la constancia y de «Quitar» (orden de `profeHtml`). Sin sedes que elegir no se pinta.
   */
  function pintarSedes(li: HTMLElement, n: number, estado: EstadoProfesor | undefined): void {
    let caja = li.querySelector<HTMLElement>(':scope > .ed-sedes');
    if (!estado || !elegibles.length) {
      if (caja?.contains(doc.activeElement)) {
        li.querySelector<HTMLElement>(':scope > .ed-profe-barra button')?.focus({ preventScroll: true });
      }
      caja?.remove();
      return;
    }
    if (!caja) {
      const b = nodo('button', 'ed-btn', icono('pin'), nodo('span', ''), nodo('span', 'ed-oculto'));
      b.type = 'button';
      b.dataset['accion'] = 'sedes-profe';
      b.dataset['n'] = String(n);
      b.dataset['k'] = `sp:${n}`;
      b.setAttribute('aria-haspopup', 'dialog');
      b.setAttribute('aria-expanded', String(abierto?.n === n));
      caja = nodo('div', 'ed ed-sedes', b);
    }
    const tiene = estado.sedes.length > 0;
    const [visible, oculto] = Array.from(caja.querySelectorAll<HTMLElement>(':scope > button > span'));
    const textoBoton = tiene ? TEXTOS_PROFESORES.cambiarSedes : TEXTOS_PROFESORES.elegirSedes;
    const deQuien = ` de ${nombreProfesor(estado.nombre)}`;
    if (visible.textContent !== textoBoton) {
      visible.textContent = textoBoton;
    }
    if (oculto.textContent !== deQuien) {
      oculto.textContent = deQuien;
    }
    let p = caja.querySelector<HTMLElement>(':scope > .ed-sin-sedes');
    if (tiene) {
      p?.remove();
    } else if (!p) {
      p = caja.appendChild(nodo('p', 'ed-sin-sedes', TEXTOS_PROFESORES.sinSedes));
    }
    const siguiente = li.querySelector<HTMLElement>(':scope > .ed-permiso, :scope > .ed-constancia, :scope > .ed-profe-barra');
    if (caja.parentElement !== li || (siguiente && caja.nextElementSibling !== siguiente)) {
      const teniaFoco = caja.contains(doc.activeElement);
      li.insertBefore(caja, siguiente);
      if (teniaFoco) {
        caja.querySelector<HTMLElement>('button')?.focus({ preventScroll: true });
      }
    }
  }

  /** Boceto `abrirSedesProfe`: diálogo anclado al botón o, con el panel estrecho, la hoja del panel. */
  function abrirSedes(n: number, b: HTMLElement): void {
    const estado = lista.find((p) => p.n === n);
    if (!estado || !elegibles.length) {
      return;
    }
    cerrar(false);
    b.setAttribute('aria-expanded', 'true');
    if (estrecho()) {
      const id = ++ultimaHoja;
      abierto = { n, enHoja: { id, avisar: true } };
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja-sedes', n, id });
      return;
    }
    abierto = { n };
    const titulo = nodo('h2', 'flot__titulo', tituloSedesProfesor(estado.nombre));
    titulo.id = 'ed-sedes-t';
    const ayuda = nodo('p', 'flot__ayuda', TEXTOS_PROFESORES.sedesAyuda);
    ayuda.id = 'ed-sedes-a';
    const listo = nodo('button', 'btn btn--sec', TEXTOS_PROFESORES.listo);
    listo.type = 'button';
    listo.dataset['op'] = 'listo-sedes';
    // Como el boceto: la ayuda describe el grupo de casillas; el diálogo solo lleva su título.
    const casillas = nodo('fieldset', 'casillas');
    casillas.setAttribute('aria-describedby', 'ed-sedes-a');
    flot.replaceChildren(titulo, ayuda, casillas, nodo('div', 'flot__acciones', listo));
    flot.setAttribute('role', 'dialog');
    flot.setAttribute('aria-labelledby', 'ed-sedes-t');
    pintarCasillas(estado, true);
    flot.hidden = false;
    // Como `abrirFlot` de las fotos: bajo el botón (o sobre él si abajo no cabe) y siempre dentro de la vista.
    const r = b.getBoundingClientRect();
    const ancho = flot.offsetWidth;
    const alto = flot.offsetHeight;
    const izquierda = Math.min(
      Math.max(8 + ventana.scrollX, r.left + ventana.scrollX + r.width / 2 - ancho / 2),
      ventana.scrollX + doc.documentElement.clientWidth - ancho - 8,
    );
    let arriba = r.top + ventana.scrollY + r.height + 8;
    if (arriba + alto > ventana.scrollY + ventana.innerHeight - 8 && r.top + ventana.scrollY - alto - 8 > ventana.scrollY) {
      arriba = r.top + ventana.scrollY - alto - 8;
    }
    arriba = encajarEnVista(arriba - ventana.scrollY, alto, ventana.innerHeight) + ventana.scrollY;
    flot.style.left = `${Math.max(8, izquierda)}px`;
    flot.style.top = `${arriba}px`;
    flot.querySelector<HTMLElement>('input')?.focus({ preventScroll: true });
  }

  /**
   * Una casilla por sede elegible, en su orden, sin «todas». Se rehacen solo si cambian las sedes elegibles (el foco
   * sigue en la misma sede). `marcar`: las casillas pasan a lo que dice el panel (también si no aceptó un cambio).
   */
  function pintarCasillas(estado: EstadoProfesor, marcar: boolean): void {
    const fieldset = flot.querySelector<HTMLFieldSetElement>('fieldset.casillas');
    if (!fieldset) {
      return;
    }
    const firma = JSON.stringify(elegibles);
    if (fieldset.dataset['firma'] !== firma) {
      const enfocada = (doc.activeElement as HTMLInputElement | null)?.closest?.('fieldset.casillas') === fieldset
        ? (doc.activeElement as HTMLInputElement).value
        : null;
      const leyenda = nodo('legend', 'ed-oculto', TEXTOS_PROFESORES.sedesLeyenda);
      const casillas = elegibles.map((s) => {
        const input = nodo('input', '');
        input.type = 'checkbox';
        input.value = String(s.id);
        input.dataset['sedeProfe'] = String(estado.n);
        return nodo('label', 'casilla', input, nodo('span', '', s.nombre));
      });
      fieldset.replaceChildren(leyenda, ...casillas);
      fieldset.dataset['firma'] = firma;
      marcar = true;
      if (enfocada !== null) {
        fieldset.querySelector<HTMLElement>(`input[value="${enfocada}"]`)?.focus({ preventScroll: true });
      }
    }
    if (marcar) {
      fieldset.querySelectorAll<HTMLInputElement>('input[data-sede-profe]').forEach((input) => {
        input.checked = estado.sedes.includes(Number(input.value));
      });
    }
  }

  /** Tras un repintado o un estado nuevo: el diálogo se cierra si ya no hay qué elegir o el profesor no está. */
  function refrescarSedes(marcar: boolean): void {
    if (!abierto) {
      return;
    }
    const estado = lista.find((p) => p.n === abierto!.n);
    if (!estado || !elegibles.length || pagina !== 'inicio' || !botonSedes(abierto.n)) {
      cerrar(false);
      return;
    }
    if (abierto.enHoja) {
      return;
    }
    const titulo = flot.querySelector<HTMLElement>('#ed-sedes-t');
    const texto = tituloSedesProfesor(estado.nombre);
    if (titulo && titulo.textContent !== texto) {
      titulo.textContent = texto;
    }
    pintarCasillas(estado, marcar);
  }

  /** Cierra el diálogo (o la hoja del panel, avisándole si la cierra la capa); el foco vuelve al botón si se pide. */
  function cerrar(devolverFoco: boolean): void {
    const actual = abierto;
    if (!actual) {
      return;
    }
    abierto = null;
    if (actual.enHoja?.avisar) {
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-sedes', id: actual.enHoja.id });
    }
    flot.hidden = true;
    flot.replaceChildren();
    flot.removeAttribute('role');
    flot.removeAttribute('aria-labelledby');
    flot.removeAttribute('aria-describedby');
    const b = botonSedes(actual.n);
    b?.setAttribute('aria-expanded', 'false');
    if (devolverFoco) {
      b?.focus({ preventScroll: true });
    }
  }

  /**
   * «Listo» o Escape: cierra y el foco vuelve al botón. Solo con «Listo» (boceto `listo-sedes`) el panel dice las sedes
   * que quedan; Escape solo cierra (boceto: keydown de `.flot`).
   */
  function terminarSedes(anunciar: boolean): void {
    const n = abierto?.n;
    if (n === undefined) {
      return;
    }
    cerrar(true);
    if (anunciar) {
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'anunciar-sedes', n });
    }
  }

  // ------------------------------------------------------------------ casilla o constancia (boceto `profeHtml`)
  /** La casilla obligatoria; se actualiza en su sitio (marcarla no la vuelve a crear ni le quita el foco). */
  function pintarCasilla(li: HTMLElement, n: number, marcada: boolean): void {
    li.querySelector(':scope > .ed-constancia')?.remove();
    let caja = li.querySelector<HTMLElement>(':scope > .ed-permiso');
    if (!caja) {
      const input = nodo('input', '');
      input.type = 'checkbox';
      input.dataset['permiso'] = String(n);
      input.dataset['k'] = `pm:${n}`;
      input.setAttribute('aria-required', 'true');
      const ayuda = nodo('small', '', TEXTOS_PROFESORES.permisoAyuda);
      ayuda.id = `pm-a-${n}`;
      caja = nodo(
        'div',
        'ed ed-permiso',
        nodo('label', '', input, nodo('span', '', `${TEXTOS_PROFESORES.permiso} `, nodo('span', 'ed-marca', TEXTOS_PROFESORES.obligatorio))),
        ayuda,
      );
      li.appendChild(caja);
    }
    const input = caja.querySelector<HTMLInputElement>('input[data-permiso]')!;
    // Solo cuando el panel cambia la marca: un repintado mientras llega su respuesta no deshace el clic.
    if (caja.dataset['marcada'] !== String(marcada)) {
      caja.dataset['marcada'] = String(marcada);
      input.checked = marcada;
    }
    const error = errores[clavePermiso(n)];
    caja.classList.toggle('es-error', !!error);
    let p = caja.querySelector<HTMLElement>(':scope > .ed-error');
    if (error) {
      if (!p) {
        p = caja.appendChild(nodo('p', 'ed-error ed-error--plano', icono('aviso'), nodo('span', '')));
        p.id = `e-pm-${n}`;
      }
      p.querySelector('span')!.textContent = error;
      input.setAttribute('aria-invalid', 'true');
      input.setAttribute('aria-describedby', `pm-a-${n} e-pm-${n}`);
    } else {
      p?.remove();
      input.removeAttribute('aria-invalid');
      input.setAttribute('aria-describedby', `pm-a-${n}`);
    }
  }

  function pintarConstancia(li: HTMLElement, texto: string): void {
    const caja = li.querySelector<HTMLElement>(':scope > .ed-permiso');
    const teniaFoco = !!caja?.contains(doc.activeElement);
    caja?.remove();
    let p = li.querySelector<HTMLElement>(':scope > .ed-constancia');
    if (!p) {
      p = li.appendChild(nodo('p', 'ed ed-constancia', icono('check'), nodo('span', '')));
    }
    const span = p.querySelector('span')!;
    if (span.textContent !== texto) {
      span.textContent = texto;
    }
    if (teniaFoco) {
      // La casilla se fue (el nombre volvió al guardado): el foco, al «Quitar» de la tarjeta.
      li.querySelector<HTMLElement>(':scope > .ed-profe-barra button')?.focus({ preventScroll: true });
    }
  }

  function pintarBarra(li: HTMLElement, n: number, nombre: string): void {
    let barra = li.querySelector<HTMLElement>(':scope > .ed-profe-barra');
    if (!barra) {
      const b = nodo('button', 'ed-btn ed-btn--quitar', icono('papelera'), TEXTOS_PROFESORES.quitar);
      b.type = 'button';
      b.dataset['accion'] = 'quitar-profe';
      b.dataset['n'] = String(n);
      b.dataset['k'] = `qp:${n}`;
      barra = nodo('div', 'ed ed-profe-barra', b);
    }
    const etiqueta = etiquetaQuitarProfesor(nombre);
    const b = barra.querySelector<HTMLButtonElement>('button')!;
    if (b.getAttribute('aria-label') !== etiqueta) {
      b.setAttribute('aria-label', etiqueta);
    }
    // Siempre la última de la tarjeta (boceto `profeHtml`).
    if (li.lastElementChild !== barra) {
      const teniaFoco = barra.contains(doc.activeElement);
      li.appendChild(barra);
      if (teniaFoco) {
        b.focus({ preventScroll: true });
      }
    }
  }

  /** Boceto `profesoresHtml`: la última celda, «Añadir profesor» («Añade el primer profesor» sin ninguno). */
  function pintarAnadir(): void {
    const ul = pagina === 'inicio' ? doc.querySelector<HTMLElement>('section#profesores ul.profes') : null;
    doc.querySelectorAll<HTMLElement>('li[data-ed-profe="anadir"]').forEach((li) => {
      if (li.parentElement !== ul) {
        li.remove();
      }
    });
    if (!ul) {
      return;
    }
    const hay = ul.querySelectorAll(':scope > li.profe').length > 0;
    const texto = hay ? TEXTOS_PROFESORES.anadir : TEXTOS_PROFESORES.anadirPrimero;
    let li = ul.querySelector<HTMLElement>(':scope > li[data-ed-profe="anadir"]');
    if (!li) {
      const b = nodo('button', 'ed ed-anadir-tarjeta', icono('mas'), nodo('span', '', texto));
      b.type = 'button';
      b.dataset['accion'] = 'anadir-profe';
      b.dataset['k'] = 'a-profe';
      li = nodo('li', 'ed', b);
      li.dataset['edProfe'] = 'anadir';
    }
    const span = li.querySelector<HTMLElement>('button > span')!;
    if (span.textContent !== texto) {
      span.textContent = texto;
    }
    if (ul.lastElementChild !== li) {
      const teniaFoco = li.contains(doc.activeElement);
      ul.appendChild(li);
      if (teniaFoco) {
        li.querySelector<HTMLElement>('button')?.focus({ preventScroll: true });
      }
    }
  }

  function decorar(paginaActual: PaginaEditor): void {
    pagina = paginaActual;
    for (const li of tarjetas()) {
      const n = numeroDe(li);
      if (n === null) {
        continue;
      }
      const estado = lista.find((p) => p.n === n);
      pintarSedes(li, n, estado);
      if (estado?.permiso.tipo === 'casilla') {
        pintarCasilla(li, n, estado.permiso.marcada);
      } else if (estado?.permiso.tipo === 'constancia') {
        pintarConstancia(li, estado.permiso.texto);
      }
      pintarBarra(li, n, estado?.nombre ?? '');
    }
    pintarAnadir();
    refrescarSedes(false);
    aplicarFoco();
  }

  // ------------------------------------------------------------------ foco tras una acción (boceto `pintar(foco)`)
  function elementoDelFoco(foco: string): HTMLElement | null {
    if (foco === 'anadir') {
      return doc.querySelector<HTMLElement>('.ed-anadir-tarjeta');
    }
    const [tipo, n] = foco.split(':');
    if (tipo === 'quitar') {
      return doc.querySelector<HTMLElement>(`[data-k="qp:${n}"]`);
    }
    if (tipo === 'permiso') {
      return casilla(Number(n));
    }
    return doc.querySelector<HTMLElement>(`.ed-texto[data-campo="profesor.${n}.nombre"]`);
  }

  function aplicarFoco(): void {
    const pendiente = focoPendiente;
    if (!pendiente) {
      return;
    }
    if (Date.now() > pendiente.hasta) {
      focoPendiente = null;
      return;
    }
    const el = elementoDelFoco(pendiente.foco);
    if (!el) {
      return;
    }
    focoPendiente = null;
    if (pendiente.foco.startsWith('editar:')) {
      // Boceto `anadirProfe`: el nombre del profesor nuevo, ya en edición.
      if (editar(el.dataset['campo']!)) {
        const copia = (doc.activeElement as HTMLElement | null)?.closest?.<HTMLElement>('.ed-campo');
        mostrar(copia ?? el);
        return;
      }
    }
    el.focus({ preventScroll: true });
    mostrar(el);
  }

  /** Pide al panel que enseñe lo que tiene el foco (boceto `asegurarVisible`). */
  function mostrar(el: HTMLElement): void {
    const caja = el.closest<HTMLElement>('li') ?? el;
    const arriba = Math.max(0, Math.round(caja.getBoundingClientRect().top + ventana.scrollY));
    enviar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba });
  }

  // ------------------------------------------------------------------ eventos
  const objetivo = (evento: Event): Element | null => {
    const t = evento.target as Element | null;
    return t && typeof t.closest === 'function' ? t : null;
  };

  const alPulsar = (evento: MouseEvent) => {
    const b = objetivo(evento)?.closest<HTMLElement>(
      '.ed-profe-barra [data-accion="quitar-profe"], .ed-anadir-tarjeta[data-accion="anadir-profe"], .ed-sedes [data-accion="sedes-profe"]',
    );
    if (!b) {
      return;
    }
    if (b.dataset['accion'] === 'sedes-profe') {
      const n = Number(b.dataset['n']);
      if (Number.isInteger(n) && n >= 1 && n <= 99) {
        abrirSedes(n, b);
      }
      return;
    }
    if (b.dataset['accion'] === 'anadir-profe') {
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'anadir' });
      return;
    }
    const n = Number(b.dataset['n']);
    if (Number.isInteger(n) && n >= 1 && n <= 99) {
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'quitar', n });
    }
  };

  const alCambiar = (evento: Event) => {
    const input = objetivo(evento)?.closest<HTMLInputElement>('.ed-permiso input[data-permiso]');
    const n = Number(input?.dataset['permiso']);
    if (!input || !Number.isInteger(n) || n < 1 || n > 99) {
      return;
    }
    enviar({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'permiso', n, marcada: input.checked });
  };

  const alEnfocar = (evento: FocusEvent) => {
    // Otro foco del usuario anula el que esperaba el panel.
    const t = objetivo(evento);
    if (focoPendiente && t && t !== elementoDelFoco(focoPendiente.foco)) {
      focoPendiente = null;
    }
  };

  /** Cada casilla del diálogo pide al panel el conjunto entero de marcadas (boceto: listener `change` de `.flot`). */
  const alCambiarSede = (evento: Event) => {
    const input = objetivo(evento)?.closest<HTMLInputElement>('input[data-sede-profe]');
    const n = abierto?.n;
    if (!input || n === undefined || abierto?.enHoja || input.dataset['sedeProfe'] !== String(n)) {
      return;
    }
    const sedes = Array.from(flot.querySelectorAll<HTMLInputElement>('input[data-sede-profe]:checked')).map((x) => Number(x.value));
    enviar({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'sedes', n, sedes });
  };

  const alPulsarFlot = (evento: MouseEvent) => {
    if (objetivo(evento)?.closest('[data-op="listo-sedes"]')) {
      terminarSedes(true);
    }
  };

  const alTeclearFlot = (evento: KeyboardEvent) => {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      evento.stopPropagation();
      terminarSedes(false);
    }
  };

  /** Pulsar fuera del diálogo (y fuera de su botón) lo cierra sin mover el foco, como los menús de las fotos. */
  const alBajar = (evento: MouseEvent) => {
    const t = objetivo(evento);
    if (!flot.hidden && t && !flot.contains(t) && !t.closest('.ed-sedes [aria-expanded="true"]')) {
      cerrar(false);
    }
  };

  doc.addEventListener('click', alPulsar);
  doc.addEventListener('change', alCambiar);
  doc.addEventListener('focusin', alEnfocar);
  doc.addEventListener('mousedown', alBajar);
  flot.addEventListener('change', alCambiarSede);
  flot.addEventListener('click', alPulsarFlot);
  flot.addEventListener('keydown', alTeclearFlot);

  return {
    estado(nueva, sedes = elegibles) {
      lista = nueva;
      elegibles = sedes;
      decorar(pagina);
      // Las casillas del diálogo, a lo que dice el panel (también si no aceptó el cambio).
      refrescarSedes(true);
    },
    respuestaHoja(id, devolverFoco) {
      if (!abierto?.enHoja || abierto.enHoja.id !== id) {
        return;
      }
      // La cierra el panel: no se le avisa (eco). El anuncio de las sedes ya lo hace él.
      abierto.enHoja.avisar = false;
      cerrar(devolverFoco);
    },
    errores(nuevos) {
      errores = nuevos;
      decorar(pagina);
    },
    enfocar(foco) {
      focoPendiente = { foco, hasta: Date.now() + ESPERA_FOCO };
      ventana.setTimeout(() => aplicarFoco(), 0);
    },
    irA(clave) {
      const n = numeroPermiso(clave);
      const s = numeroSedes(clave);
      // W12f-3: el error de las sedes de un profesor lleva al botón de sus sedes.
      const el = n !== null ? casilla(n) : s !== null ? botonSedes(s) : null;
      if (!el) {
        return null;
      }
      el.focus({ preventScroll: true });
      const caja = el.closest<HTMLElement>('.ed-permiso, .ed-sedes') ?? el;
      return Math.max(0, Math.round(caja.getBoundingClientRect().top + ventana.scrollY));
    },
    decorar,
    quitar() {
      cerrar(false);
      doc.removeEventListener('click', alPulsar);
      doc.removeEventListener('change', alCambiar);
      doc.removeEventListener('focusin', alEnfocar);
      doc.removeEventListener('mousedown', alBajar);
      flot.remove();
      doc.querySelectorAll('li[data-ed-profe], .ed-sedes, .ed-permiso, .ed-constancia, .ed-profe-barra').forEach((el) => el.remove());
    },
  };
}
