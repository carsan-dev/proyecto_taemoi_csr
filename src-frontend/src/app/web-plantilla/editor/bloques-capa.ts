import type { PaginaEditor } from './candados-linea-comarcal';
import { encajarEnVista } from './colocar-flotantes';
import { cuentaGaleria, ordenGaleria } from './fotos-galeria';
import {
  type AccionBloque,
  MENU_TIPOS,
  NOTA_PORTADA,
  TEXTOS_BLOQUES,
  YA_ESTA,
  bloqueVacio,
  esMovible,
  estaOculto,
  etiquetaAccion,
  existeTipo,
  esTipoBloque,
  idDeDom,
  idEnDom,
  nombreBloque,
  tituloMenuAnadir,
} from './bloques-editor';
import {
  type BloqueBorrador,
  CANAL_EDITOR_WEB,
  type MensajeDelEditor,
  type MensajeEstadoBloques,
  type OpcionHojaMenu,
} from './puente-editor';

/**
 * Bloques de Inicio en el sitio (W12e; boceto `boceto-panel-contenido-web-comarcal.html`: `barraHtml`, `estadosHtml`,
 * `hueco`, `abrirMenuAnadir`, `abrirFlot`, `enfocar`). Parte de la capa de edición (su chunk).
 *
 * En la barra de cada bloque movible pone Subir y Bajar (deshabilitados en los extremos), Ocultar o Mostrar y, en los
 * que se pueden quitar, Quitar; en la de la portada, su nota («Siempre arriba…»); en todas, las pastillas «Sin
 * guardar», «Oculto en la web · Mostrar» y «Vacío: no sale en la web». Entre los bloques, los huecos «Añadir bloque»
 * con su menú de tipos. Con el panel estrecho (< 768 px) las acciones no salen en la barra: el nombre abre la hoja del
 * panel.
 *
 * La capa no cambia el borrador: pide cada acción al panel, que la hace, manda el borrador nuevo y a dónde va el foco.
 * También le dice qué bloques hay en la página (las fijas sin datos no se pintan) y cuáles están vacíos.
 */
export interface OpcionesBloquesEditor {
  ventana: Window;
  enviar: (mensaje: MensajeDelEditor) => void;
  /** Entra a editar el primer texto vacío de un bloque recién añadido (boceto `anadirBloque`). */
  editar: (clave: string) => boolean;
}

export interface BloquesEditor {
  borrador(bloques: BloqueBorrador[]): void;
  estado(estado: Pick<MensajeEstadoBloques, 'sinGuardar' | 'quitables' | 'estrecho'>): void;
  /** Tras una acción, el foco (boceto `enfocar`); se pone cuando el tema haya pintado. */
  enfocar(foco: string): void;
  /** El panel es estrecho: el nombre de un bloque abre su hoja (true) en lugar de elegirlo. */
  estrecho(): boolean;
  /** Con el panel estrecho, pide la hoja de un bloque movible (id del DOM); false si no es movible. */
  abrirHoja(idDom: string): boolean;
  /** d541: la respuesta de la hoja del panel al menú «Añadir bloque» (`op` null: se cerró sin elegir). */
  respuestaMenu(id: number, op: string | null, devolverFoco: boolean): void;
  decorar(pagina: PaginaEditor): void;
  quitar(): void;
}

const SVG = 'http://www.w3.org/2000/svg';
type Figura = readonly [string, Readonly<Record<string, string>>];
/** Iconos del boceto (`#i-subir`, `#i-bajar`, `#i-oculto`, `#i-ojo`, `#i-papelera`, `#i-mas`, `#i-info`, `#i-texto`, `#i-foto`, `#i-alumnos`). */
const ICONOS: Readonly<Record<string, { trazo: string; figuras: readonly Figura[] }>> = {
  subir: { trazo: '2', figuras: [['path', { d: 'M12 19V5M6 11l6-6 6 6' }]] },
  bajar: { trazo: '2', figuras: [['path', { d: 'M12 5v14M6 13l6 6 6-6' }]] },
  oculto: {
    trazo: '2',
    figuras: [
      ['path', { d: 'M3 3l18 18M10.6 6.1A10 10 0 0 1 12 6c5 0 8.5 4 9.5 6a14 14 0 0 1-2.6 3.4M6.6 6.7C4.6 8 3.2 10 2.5 12c1 2 4.5 6 9.5 6a9.6 9.6 0 0 0 4.4-1' }],
      ['path', { d: 'M9.9 9.9a3 3 0 0 0 4.2 4.2' }],
    ],
  },
  ojo: {
    trazo: '2',
    figuras: [
      ['path', { d: 'M2.5 12C3.5 10 7 6 12 6s8.5 4 9.5 6c-1 2-4.5 6-9.5 6s-8.5-4-9.5-6Z' }],
      ['circle', { cx: '12', cy: '12', r: '3' }],
    ],
  },
  papelera: { trazo: '2', figuras: [['path', { d: 'M4 7h16M10 11v6M14 11v6M6 7l1 12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-12M9 7V4.5h6V7' }]] },
  mas: { trazo: '2', figuras: [['path', { d: 'M12 5v14M5 12h14' }]] },
  info: {
    trazo: '2',
    figuras: [
      ['circle', { cx: '12', cy: '12', r: '9' }],
      ['path', { d: 'M12 11v5.5M12 7.5h.01' }],
    ],
  },
  texto: { trazo: '2', figuras: [['path', { d: 'M4 6h16M4 11h16M4 16h10' }]] },
  foto: {
    trazo: '1.8',
    figuras: [
      ['rect', { x: '3', y: '5', width: '18', height: '14', rx: '2' }],
      ['circle', { cx: '9', cy: '10', r: '1.8' }],
      ['path', { d: 'm4 17 5-5 4 4 2.5-2.5L20 17' }],
    ],
  },
  alumnos: {
    trazo: '1.8',
    figuras: [
      ['circle', { cx: '9', cy: '8.5', r: '3.5' }],
      ['path', { d: 'M2.5 20c.6-3.6 3.2-5.5 6.5-5.5s5.9 1.9 6.5 5.5M16 5.2a3.5 3.5 0 0 1 0 6.6M18.5 14.8c1.7.7 2.8 2.4 3 5.2' }],
    ],
  },
};

/** Lo que espera un foco pedido por el panel antes de darlo por perdido (el tema pinta en el siguiente ciclo). */
const ESPERA_FOCO = 1500;

export function activarBloquesEditor(opciones: OpcionesBloquesEditor): BloquesEditor {
  const { ventana, enviar, editar } = opciones;
  const doc = ventana.document;
  let filas: BloqueBorrador[] = [];
  let sinGuardar = new Set<string>();
  let quitables = new Set<string>();
  let esEstrecho = false;
  let pagina: PaginaEditor = 'inicio';
  let ultimoInforme = '';
  let focoPendiente: { foco: string; hasta: number; mostrado: boolean } | null = null;
  let menuAbierto: number | null = null;
  /** d541: id de la apertura del menú cuando está en la hoja del panel (null: flotante o cerrado). */
  let idHoja: number | null = null;
  let ultimaHoja = 0;

  const flot = doc.createElement('div');
  flot.className = 'ed flot flot--anadir';
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

  /** La parte de un envoltorio (`.ed-bloque`): el id del bloque (`texto_foto.3`) o el de la parte fija. */
  const parteDe = (el: HTMLElement) => idDeDom(el.dataset['bloque']) ?? el.dataset['bloque'] ?? '';

  /** Los bloques de Inicio de la página (movibles y fijas que se pintan), en su orden. */
  function bloquesDeLaPagina(): { el: HTMLElement; id: string }[] {
    if (pagina !== 'inicio') {
      return [];
    }
    return Array.from(doc.querySelectorAll<HTMLElement>('.ed-bloque[data-bloque]'))
      .map((el) => ({ el, id: idDeDom(el.dataset['bloque']) }))
      .filter((b): b is { el: HTMLElement; id: string } => b.id !== null);
  }

  const vacio = (id: string, el: HTMLElement) => bloqueVacio(id, filas, !!el.querySelector('.fs-foto img'));

  // ------------------------------------------------------------------ barra del bloque (boceto `barraHtml`)
  function boton(accion: AccionBloque, texto: string, nombreIcono: keyof typeof ICONOS, id: string, nombre: string, desactivado: boolean, k: string): HTMLButtonElement {
    const b = nodo('button', `ed-btn${accion === 'quitar' ? ' ed-btn--quitar' : ''}`, icono(nombreIcono), texto);
    b.type = 'button';
    b.dataset['accion'] = accion;
    b.dataset['bloqueId'] = id;
    b.dataset['k'] = `${k}:${id}`;
    b.setAttribute('aria-label', etiquetaAccion(texto, nombre));
    if (desactivado) {
      b.setAttribute('aria-disabled', 'true');
    }
    return b;
  }

  function acciones(el: HTMLElement, id: string, i: number, n: number): void {
    const fila = el.querySelector<HTMLElement>(':scope > .ed-barra > .ed-fila');
    if (!fila) {
      return;
    }
    const nombre = nombreBloque(id, filas);
    const oculto = estaOculto(filas, id);
    const quitable = quitables.has(id);
    const firma = [i === 0, i === n - 1, oculto, quitable, nombre].join('|');
    let caja = fila.querySelector<HTMLElement>(':scope > .ed-acciones');
    if (caja && caja.dataset['firma'] === firma) {
      return;
    }
    const enfocado = caja?.contains(doc.activeElement) ? (doc.activeElement as HTMLElement).dataset['k'] : undefined;
    if (!caja) {
      caja = nodo('div', 'ed-acciones');
      caja.setAttribute('role', 'toolbar');
      fila.appendChild(caja);
    }
    caja.dataset['firma'] = firma;
    caja.setAttribute('aria-label', `Acciones del bloque ${nombre}`);
    const t = TEXTOS_BLOQUES;
    caja.replaceChildren(
      boton('subir', t.subir, 'subir', id, nombre, i === 0, 'subir'),
      boton('bajar', t.bajar, 'bajar', id, nombre, i === n - 1, 'bajar'),
      oculto ? boton('mostrar', t.mostrar, 'ojo', id, nombre, false, 'vis') : boton('ocultar', t.ocultar, 'oculto', id, nombre, false, 'vis'),
      ...(quitable ? [boton('quitar', t.quitar, 'papelera', id, nombre, false, 'quitar')] : []),
    );
    if (enfocado) {
      caja.querySelector<HTMLElement>(`[data-k="${CSS.escape(enfocado)}"]`)?.focus({ preventScroll: true });
    }
  }

  /** Boceto `barraHtml` de la portada: sin acciones, con su nota. */
  function notaPortada(): void {
    const fila = doc.querySelector<HTMLElement>('.ed-bloque[data-bloque="portada"] > .ed-barra > .ed-fila');
    if (fila && !fila.querySelector(':scope > .ed-acciones')) {
      fila.appendChild(nodo('div', 'ed-acciones', nodo('span', 'ed-nota', NOTA_PORTADA)));
    }
  }

  // ------------------------------------------------------------------ pastillas (boceto `estadosHtml`)
  function estados(el: HTMLElement, movible: string | null): void {
    const barra = el.querySelector<HTMLElement>(':scope > .ed-barra');
    if (!barra) {
      return;
    }
    const parte = parteDe(el);
    const cambio = sinGuardar.has(parte);
    const oculto = !!movible && estaOculto(filas, movible);
    const esVacio = !!movible && !oculto && vacio(movible, el);
    const nombre = movible ? nombreBloque(movible, filas) : '';
    // W12f-2a (boceto `estadosHtml`): la galería dice cuántas fotos tiene de las 6.
    const cuenta = movible === 'galeria' ? cuentaGaleria(ordenGaleria(filas).length) : '';
    const firma = [cambio, oculto, esVacio, nombre, cuenta].join('|');
    let caja = barra.querySelector<HTMLElement>(':scope > .ed-estados');
    if (!cambio && !oculto && !esVacio && !cuenta) {
      caja?.remove();
      return;
    }
    if (caja && caja.dataset['firma'] === firma) {
      return;
    }
    const enfocado = caja?.contains(doc.activeElement);
    caja ??= barra.appendChild(nodo('div', 'ed-estados'));
    caja.dataset['firma'] = firma;
    const pastillas: HTMLElement[] = [];
    if (cambio) {
      pastillas.push(nodo('span', 'ed-pastilla ed-pastilla--cambio', nodo('span', 'ed-punto'), TEXTOS_BLOQUES.sinGuardar));
    }
    if (oculto && movible) {
      const mostrar = nodo('button', 'ed-mini', TEXTOS_BLOQUES.mostrar);
      mostrar.type = 'button';
      mostrar.dataset['accion'] = 'mostrar';
      mostrar.dataset['bloqueId'] = movible;
      mostrar.dataset['k'] = `vp:${movible}`;
      mostrar.setAttribute('aria-label', `Mostrar el bloque ${nombre}`);
      pastillas.push(nodo('span', 'ed-pastilla ed-pastilla--oculto', icono('oculto'), TEXTOS_BLOQUES.ocultoEnLaWeb, mostrar));
    } else if (esVacio) {
      pastillas.push(nodo('span', 'ed-pastilla', icono('info'), TEXTOS_BLOQUES.vacio));
    }
    if (cuenta) {
      pastillas.push(nodo('span', 'ed-pastilla', cuenta));
    }
    caja.replaceChildren(...pastillas);
    if (enfocado && oculto) {
      caja.querySelector<HTMLElement>('.ed-mini')?.focus({ preventScroll: true });
    }
  }

  // ------------------------------------------------------------------ huecos «Añadir bloque» (boceto `hueco`)
  function hueco(pos: number): HTMLElement {
    const b = nodo('button', 'ed-anadir', icono('mas'), TEXTOS_BLOQUES.anadir, nodo('span', 'ed-oculto', ` en el puesto ${pos + 1}`));
    b.type = 'button';
    b.dataset['accion'] = 'anadir';
    b.dataset['pos'] = String(pos);
    b.dataset['k'] = `a:${pos}`;
    b.setAttribute('aria-haspopup', 'menu');
    b.setAttribute('aria-expanded', String(menuAbierto === pos));
    const caja = nodo('div', 'ed ed-hueco', b);
    caja.dataset['pos'] = String(pos);
    return caja;
  }

  /** Un hueco delante de cada bloque y otro tras el último (delante de Contacto). Solo se toca lo que no está en su sitio. */
  function huecos(bloques: readonly { el: HTMLElement }[]): void {
    const destinos: { antes?: HTMLElement; tras?: HTMLElement }[] = bloques.map((b) => ({ antes: b.el }));
    const contacto = doc.querySelector<HTMLElement>('.ed-bloque[data-bloque="contacto"]');
    if (bloques.length || contacto) {
      destinos.push(contacto ? { antes: contacto } : { tras: bloques[bloques.length - 1].el });
    }
    const existentes = new Map<number, HTMLElement>();
    doc.querySelectorAll<HTMLElement>('.ed-hueco[data-pos]').forEach((h) => {
      const pos = Number(h.dataset['pos']);
      if (pos >= destinos.length || existentes.has(pos)) {
        h.remove();
      } else {
        existentes.set(pos, h);
      }
    });
    destinos.forEach((d, pos) => {
      const h = existentes.get(pos) ?? hueco(pos);
      if (d.antes && d.antes.previousElementSibling !== h) {
        d.antes.before(h);
      } else if (d.tras && d.tras.nextElementSibling !== h) {
        d.tras.after(h);
      }
    });
  }

  // ------------------------------------------------------------------ pintar
  function decorar(paginaActual: PaginaEditor): void {
    pagina = paginaActual;
    doc.documentElement.classList.toggle('estrecho', esEstrecho);
    const bloques = bloquesDeLaPagina();
    const n = bloques.length;
    bloques.forEach(({ el, id }, i) => {
      const movible = esMovible(id) ? id : null;
      if (movible) {
        acciones(el, movible, i, n);
        el.classList.toggle('es-oculto', estaOculto(filas, movible));
      }
      estados(el, movible);
    });
    // Las partes fijas (cabecera, portada, línea, contacto, pie y las de las otras páginas): solo «Sin guardar».
    doc.querySelectorAll<HTMLElement>('.ed-bloque[data-bloque]').forEach((el) => {
      if (!idDeDom(el.dataset['bloque'])) {
        estados(el, null);
      }
    });
    if (pagina === 'inicio') {
      notaPortada();
      huecos(bloques);
    } else {
      doc.querySelectorAll('.ed-hueco').forEach((h) => h.remove());
    }
    informar(bloques);
    aplicarFoco();
  }

  /** Al panel: qué bloques hay en la página y cuáles están vacíos (solo si cambia). */
  function informar(bloques: readonly { el: HTMLElement; id: string }[]): void {
    if (pagina !== 'inicio') {
      return;
    }
    const orden = bloques.map((b) => b.id);
    const vacios = bloques.filter((b) => esMovible(b.id) && vacio(b.id, b.el)).map((b) => b.id);
    const firma = `${orden.join(',')}|${vacios.join(',')}`;
    if (firma !== ultimoInforme) {
      ultimoInforme = firma;
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'bloques', orden, vacios });
    }
  }

  // ------------------------------------------------------------------ foco tras una acción (boceto `enfocar`)
  function elementoDelFoco(foco: string): HTMLElement | null {
    const [tipo, resto] = [foco.slice(0, foco.indexOf(':')), foco.slice(foco.indexOf(':') + 1)];
    if (tipo === 'a') {
      return doc.querySelector<HTMLElement>(`.ed-anadir[data-pos="${CSS.escape(resto)}"]`);
    }
    const bloque = doc.querySelector<HTMLElement>(`.ed-bloque[data-bloque="${CSS.escape(idEnDom(resto))}"]`);
    if (!bloque) {
      return null;
    }
    if (tipo === 'sel' || tipo === 'editar') {
      return bloque.querySelector<HTMLElement>(':scope > .ed-barra .ed-nombre');
    }
    return bloque.querySelector<HTMLElement>(`:scope > .ed-barra [data-k="${CSS.escape(foco)}"]`);
  }

  /**
   * El foco pedido se pone en cuanto está su elemento y se mantiene mientras el tema termina de pintar (mover un bloque
   * lo saca y lo vuelve a meter en el DOM y el foco se pierde), hasta que pasa la espera o el usuario va a otro sitio.
   */
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
    if (pendiente.foco.startsWith('editar:')) {
      focoPendiente = null;
      const id = pendiente.foco.slice('editar:'.length);
      const texto = doc
        .querySelector<HTMLElement>(`.ed-bloque[data-bloque="${CSS.escape(idEnDom(id))}"]`)
        ?.querySelector<HTMLElement>('.ed-texto.es-vacio[data-campo]');
      if (texto && editar(texto.dataset['campo']!)) {
        // Al editar, el original se esconde (`display:none`, mediría 0: la página subiría arriba del todo): se enseña la
        // copia que se edita, que está en su sitio.
        const copia = (doc.activeElement as HTMLElement | null)?.closest?.<HTMLElement>('.ed-campo');
        mostrar(copia ?? el.closest<HTMLElement>('.ed-bloque') ?? el);
        return;
      }
      el.focus({ preventScroll: true });
      mostrar(el.closest<HTMLElement>('.ed-bloque') ?? el);
      return;
    }
    if (doc.activeElement !== el) {
      el.focus({ preventScroll: true });
    }
    if (!pendiente.mostrado) {
      pendiente.mostrado = true;
      mostrar(el.closest<HTMLElement>('.ed-bloque, .ed-hueco') ?? el);
    }
  }

  /** Pide al panel que enseñe lo que tiene el foco (boceto `asegurarVisible`). */
  function mostrar(el: HTMLElement): void {
    const arriba = Math.max(0, Math.round(el.getBoundingClientRect().top + ventana.scrollY));
    enviar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba });
  }

  // ------------------------------------------------------------------ menú «Añadir bloque» (boceto `abrirMenuAnadir`)
  function opcion(tipo: string, texto: string, nombreIcono: keyof typeof ICONOS, pie: string, desactivada: boolean): HTMLButtonElement {
    const b = nodo('button', 'flot__op', icono(nombreIcono), nodo('span', '', texto, nodo('small', '', pie)));
    b.type = 'button';
    b.setAttribute('role', 'menuitem');
    b.dataset['op'] = tipo;
    if (desactivada) {
      b.setAttribute('aria-disabled', 'true');
    }
    return b;
  }

  const botonHueco = (pos: number) => doc.querySelector<HTMLElement>(`.ed-anadir[data-pos="${pos}"]`);

  function abrirMenu(pos: number, ancla: HTMLElement): void {
    cerrarMenu(false);
    const datos: OpcionHojaMenu[] = MENU_TIPOS.map((t) => {
      const ya = existeTipo(filas, t.tipo);
      return { op: t.tipo, texto: t.nombre, pie: ya ? YA_ESTA : t.desc, icono: t.icono, desactivada: ya, peligro: false };
    });
    if (esEstrecho) {
      // d541: con el panel estrecho el menú sale como hoja inferior del panel (la vista previa no llega al borde de la pantalla).
      menuAbierto = pos;
      idHoja = ++ultimaHoja;
      ancla.setAttribute('aria-expanded', 'true');
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja-menu', menu: 'anadir', id: idHoja, titulo: tituloMenuAnadir(pos), opciones: datos });
      return;
    }
    const ops = datos.map((d) => opcion(d.op, d.texto, d.icono, d.pie, d.desactivada));
    const titulo = nodo('p', 'flot__titulo', tituloMenuAnadir(pos));
    titulo.id = 'ed-anadir-t';
    const lista = nodo('div', 'flot__lista', ...ops);
    lista.setAttribute('role', 'menu');
    lista.setAttribute('aria-labelledby', 'ed-anadir-t');
    flot.replaceChildren(titulo, lista);
    flot.hidden = false;
    menuAbierto = pos;
    ancla.setAttribute('aria-expanded', 'true');
    const r = ancla.getBoundingClientRect();
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
    // Ni abajo ni arriba cabe entero: se desplaza lo justo para que se vea dentro de la vista.
    arriba = encajarEnVista(arriba - ventana.scrollY, alto, ventana.innerHeight) + ventana.scrollY;
    flot.style.left = `${Math.max(8, izquierda)}px`;
    flot.style.top = `${arriba}px`;
    flot.querySelector<HTMLElement>('[role=menuitem]:not([aria-disabled=true])')?.focus({ preventScroll: true });
  }

  function cerrarMenu(devolverFoco: boolean, avisar = true): void {
    if (menuAbierto === null) {
      return;
    }
    const pos = menuAbierto;
    const enHoja = idHoja;
    menuAbierto = null;
    idHoja = null;
    if (enHoja !== null && avisar) {
      // d541: la capa cierra sola el menú que el panel tiene en su hoja: que la quite.
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-menu', menu: 'anadir', id: enHoja });
    }
    flot.hidden = true;
    flot.replaceChildren();
    if (pos !== null) {
      const b = botonHueco(pos);
      b?.setAttribute('aria-expanded', 'false');
      if (devolverFoco) {
        b?.focus({ preventScroll: true });
      }
    }
  }

  // ------------------------------------------------------------------ eventos
  const objetivo = (evento: Event): Element | null => {
    const t = evento.target as Element | null;
    return t && typeof t.closest === 'function' ? t : null;
  };

  const alPulsar = (evento: MouseEvent) => {
    const b = objetivo(evento)?.closest<HTMLElement>('.ed-acciones [data-accion], .ed-estados [data-accion], .ed-anadir[data-accion]');
    if (!b || b.getAttribute('aria-disabled') === 'true') {
      return;
    }
    const accion = b.dataset['accion'];
    if (accion === 'anadir') {
      const pos = Number(b.dataset['pos']);
      if (menuAbierto === pos) {
        cerrarMenu(true);
      } else {
        abrirMenu(pos, b);
      }
      return;
    }
    const id = b.dataset['bloqueId'];
    if (id && esMovible(id) && (accion === 'subir' || accion === 'bajar' || accion === 'ocultar' || accion === 'mostrar' || accion === 'quitar')) {
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'bloque', accion, bloque: id });
    }
  };

  const alPulsarMenu = (evento: MouseEvent) => {
    const b = objetivo(evento)?.closest<HTMLElement>('[data-op]');
    const pos = menuAbierto;
    const tipo = b?.dataset['op'];
    if (!b || pos === null || b.getAttribute('aria-disabled') === 'true' || !esTipoBloque(tipo)) {
      return;
    }
    cerrarMenu(false);
    enviar({ canal: CANAL_EDITOR_WEB, tipo: 'anadir-bloque', pos, bloque: tipo });
  };

  const alTeclearMenu = (evento: KeyboardEvent) => {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      evento.stopPropagation();
      cerrarMenu(true);
      return;
    }
    const items = Array.from(flot.querySelectorAll<HTMLElement>('[role=menuitem]'));
    if (evento.key === 'Tab') {
      evento.preventDefault();
      cerrarMenu(true);
      return;
    }
    const i = items.indexOf(doc.activeElement as HTMLElement);
    if (i === -1) {
      return;
    }
    const destino: Record<string, number> = {
      ArrowDown: (i + 1) % items.length,
      ArrowUp: (i - 1 + items.length) % items.length,
      Home: 0,
      End: items.length - 1,
    };
    if (evento.key in destino) {
      evento.preventDefault();
      items[destino[evento.key]].focus();
    }
  };

  const alBajar = (evento: MouseEvent) => {
    const t = objetivo(evento);
    if (!flot.hidden && t && !flot.contains(t) && !t.closest('.ed-anadir[aria-expanded="true"]')) {
      cerrarMenu(false);
    }
  };

  const alEnfocar = (evento: FocusEvent) => {
    // Otro foco del usuario anula el que esperaba el panel.
    const t = objetivo(evento);
    if (focoPendiente && t && t !== elementoDelFoco(focoPendiente.foco)) {
      focoPendiente = null;
    }
  };

  doc.addEventListener('click', alPulsar);
  doc.addEventListener('mousedown', alBajar);
  doc.addEventListener('focusin', alEnfocar);
  flot.addEventListener('click', alPulsarMenu);
  flot.addEventListener('keydown', alTeclearMenu);

  return {
    borrador(bloques) {
      filas = bloques;
      decorar(pagina);
    },
    estado(estado) {
      sinGuardar = new Set(estado.sinGuardar);
      quitables = new Set(estado.quitables);
      esEstrecho = estado.estrecho;
      decorar(pagina);
    },
    enfocar(foco) {
      focoPendiente = { foco, hasta: Date.now() + ESPERA_FOCO, mostrado: false };
      // Si el tema no cambia (p. ej. la acción no movió nada a la vista), se intenta ya; si no, al pintar.
      ventana.setTimeout(() => aplicarFoco(), 0);
    },
    estrecho: () => esEstrecho,
    respuestaMenu(id, op, devolverFoco) {
      const pos = menuAbierto;
      if (pos === null || idHoja === null || id !== idHoja) {
        return;
      }
      if (op === null) {
        cerrarMenu(devolverFoco, false);
        return;
      }
      // Solo lo que la hoja ofreció: un tipo y que no esté ya (los únicos que ya están salen desactivados).
      if (!esTipoBloque(op) || existeTipo(filas, op)) {
        return;
      }
      cerrarMenu(false, false);
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'anadir-bloque', pos, bloque: op });
    },
    abrirHoja(idDom) {
      const id = idDeDom(idDom);
      if (!id || !esMovible(id)) {
        return false;
      }
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja', bloque: id });
      return true;
    },
    decorar,
    quitar() {
      doc.removeEventListener('click', alPulsar);
      doc.removeEventListener('mousedown', alBajar);
      doc.removeEventListener('focusin', alEnfocar);
      if (idHoja !== null) {
        cerrarMenu(false);
      }
      doc.querySelectorAll('.ed-hueco').forEach((h) => h.remove());
      doc.documentElement.classList.remove('estrecho');
      flot.remove();
    },
  };
}
