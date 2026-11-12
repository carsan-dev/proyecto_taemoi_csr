import type { PaginaEditor } from './candados-linea-comarcal';
import {
  type EstadoProfesor,
  TEXTOS_PROFESORES,
  clavePermiso,
  etiquetaQuitarProfesor,
  numeroPermiso,
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
 */
export interface OpcionesProfesoresEditor {
  ventana: Window;
  enviar: (mensaje: MensajeDelEditor) => void;
  /** Entra a editar un texto (el nombre del profesor recién añadido); false si aún no está. */
  editar: (clave: string) => boolean;
}

export interface ProfesoresEditor {
  /** Llegan los profesores del borrador con su casilla o su constancia (`profesores`). */
  estado(lista: EstadoProfesor[]): void;
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
/** Iconos del boceto (`#i-mas`, `#i-papelera`, `#i-check`, `#i-aviso`). */
const ICONOS: Readonly<Record<string, { trazo: string; figuras: readonly Figura[] }>> = {
  mas: { trazo: '2', figuras: [['path', { d: 'M12 5v14M5 12h14' }]] },
  papelera: { trazo: '2', figuras: [['path', { d: 'M4 7h16M10 11v6M14 11v6M6 7l1 12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-12M9 7V4.5h6V7' }]] },
  check: { trazo: '3', figuras: [['path', { d: 'm5 12 5 5 9-10' }]] },
  aviso: { trazo: '2', figuras: [['circle', { cx: '12', cy: '12', r: '9' }], ['path', { d: 'M12 7.5v5.5M12 16.5h.01' }]] },
};

/** Lo que espera un foco pedido por el panel antes de darlo por perdido (el tema pinta en el siguiente ciclo). */
const ESPERA_FOCO = 1500;
const TARJETAS = 'section#profesores ul.profes > li.profe[data-ed-profesor]';

export function activarProfesoresEditor(opciones: OpcionesProfesoresEditor): ProfesoresEditor {
  const { ventana, enviar, editar } = opciones;
  const doc = ventana.document;
  let lista: EstadoProfesor[] = [];
  let errores: Record<string, string> = {};
  let pagina: PaginaEditor = 'inicio';
  let focoPendiente: { foco: string; hasta: number } | null = null;

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
      if (estado?.permiso.tipo === 'casilla') {
        pintarCasilla(li, n, estado.permiso.marcada);
      } else if (estado?.permiso.tipo === 'constancia') {
        pintarConstancia(li, estado.permiso.texto);
      }
      pintarBarra(li, n, estado?.nombre ?? '');
    }
    pintarAnadir();
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
    const b = objetivo(evento)?.closest<HTMLElement>('.ed-profe-barra [data-accion="quitar-profe"], .ed-anadir-tarjeta[data-accion="anadir-profe"]');
    if (!b) {
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

  doc.addEventListener('click', alPulsar);
  doc.addEventListener('change', alCambiar);
  doc.addEventListener('focusin', alEnfocar);

  return {
    estado(nueva) {
      lista = nueva;
      decorar(pagina);
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
      const input = n === null ? null : casilla(n);
      if (!input) {
        return null;
      }
      input.focus({ preventScroll: true });
      const caja = input.closest<HTMLElement>('.ed-permiso') ?? input;
      return Math.max(0, Math.round(caja.getBoundingClientRect().top + ventana.scrollY));
    },
    decorar,
    quitar() {
      doc.removeEventListener('click', alPulsar);
      doc.removeEventListener('change', alCambiar);
      doc.removeEventListener('focusin', alEnfocar);
      doc.querySelectorAll('li[data-ed-profe], .ed-permiso, .ed-constancia, .ed-profe-barra').forEach((el) => el.remove());
    },
  };
}
