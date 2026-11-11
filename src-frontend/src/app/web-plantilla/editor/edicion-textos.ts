import type { PaginaEditor } from './candados-linea-comarcal';
import { type BloqueBorrador, CANAL_EDITOR_WEB, type MensajeDelEditor } from './puente-editor';
import { CAMPOS_TEXTO_MARCO, CampoTexto, bloqueTextoFoto, camposTextoInicio, defTexto, defectoDeLaPagina, defectoFijo } from './textos-editor';

/**
 * Textos en el sitio (W12d; boceto `boceto-panel-contenido-web-comarcal.html`: `texto()`, `entrarEdicion`, `salirEdicion`,
 * `leer`, `mostrarChip`, `tope` y los oyentes de `escuchar`). Parte de la capa de edición (su chunk).
 *
 * El DOM del tema no se escribe nunca: cada texto se marca como botón («Editar titular») y, al editarlo, la capa pone
 * en su sitio una copia del elemento (mismas clases, mismo aspecto) con el texto editable, y esconde el original. Cada
 * cambio va al panel (`texto`) y al tema (el borrador con el cambio), que repinta los demás sitios de la misma clave. Al
 * salir se quita la copia y vuelve el original, ya repintado por el tema.
 *
 * Un texto vacío no se pintaría en la web; en el editor el tema recibe su «Escribe aquí…» y la capa lo marca vacío.
 */
export interface OpcionesEdicionTextos {
  ventana: Window;
  enviar: (mensaje: MensajeDelEditor) => void;
  /** Pasa al tema el borrador que tiene que pintar. */
  alTema: (bloques: BloqueBorrador[]) => void;
  seleccionar: (bloque: string | null) => void;
}

export interface EdicionTextos {
  /** Llega el borrador del panel. */
  borrador(bloques: BloqueBorrador[]): void;
  /** Los errores que hay que señalar (vacío: ninguno). */
  errores(errores: Record<string, string>): void;
  /** Lleva el foco a un texto; da dónde empieza (px desde arriba del documento) o null si no está en la página. */
  irA(clave: string): number | null;
  decorar(pagina: PaginaEditor): void;
  quitar(): void;
}

const ATRIBUTOS_COPIA = /^(class|_ngcontent-.*)$/;
const SVG = 'http://www.w3.org/2000/svg';
/** El aviso por voz del máximo, como mucho cada 2,5 s (boceto `tope`). */
const ESPERA_TOPE = 2500;
const DENTRO_DE_LA_CAPA = '.ed, .ed-campo, .horario';

interface Edicion {
  el: HTMLElement;
  clave: string;
  copia: HTMLElement;
  campo: HTMLElement;
}

export function activarEdicionTextos(opciones: OpcionesEdicionTextos): EdicionTextos {
  const { ventana, enviar, alTema, seleccionar } = opciones;
  const doc = ventana.document;
  let filas: BloqueBorrador[] = [];
  let errores: Record<string, string> = {};
  const defectos = new Map<string, string>();
  let activa: Edicion | null = null;
  let ultimoTope = 0;

  const chip = doc.createElement('div');
  chip.className = 'ed ed-chip';
  chip.setAttribute('aria-hidden', 'true');
  chip.hidden = true;
  const voz = doc.createElement('p');
  voz.className = 'ed ed-oculto';
  voz.setAttribute('role', 'status');
  doc.body.append(chip, voz);

  function decir(texto: string): void {
    voz.textContent = '';
    ventana.setTimeout(() => (voz.textContent = texto), 100);
  }

  // ------------------------------------------------------------------ valores
  const fila = (clave: string) => filas.find((f) => f.clave === clave);

  function valor(clave: string): string {
    const f = fila(clave);
    return f ? (f.texto ?? '') : (defectoFijo(clave) ?? defectos.get(clave) ?? '');
  }

  /** Lo que pinta el tema: los vacíos editables con su «Escribe aquí…» y, en cada texto con foto, sus tres textos. */
  function paraTema(): BloqueBorrador[] {
    const lista = filas.map((f) => {
      const def = defTexto(f.clave);
      return def && !(f.texto ?? '').trim() ? { ...f, texto: def.vacio } : f;
    });
    const bloques = new Set(filas.map((f) => bloqueTextoFoto(f.clave) ?? (/^texto_foto\.\d+$/.test(f.clave) ? f.clave : null)));
    for (const bloque of bloques) {
      for (const campo of ['rotulo', 'titulo', 'texto']) {
        const clave = `${bloque}.${campo}`;
        if (bloque && !fila(clave)) {
          lista.push({ clave, texto: defTexto(clave)!.vacio, imagenUrl: null, orden: 0, oculto: false });
        }
      }
    }
    return lista;
  }

  function poner(clave: string, texto: string): void {
    const f = fila(clave);
    filas = f
      ? filas.map((x) => (x === f ? { ...x, texto } : x))
      : [...filas, { clave, texto, imagenUrl: null, orden: 0, oculto: false }];
  }

  // ------------------------------------------------------------------ lectura de la página
  function plano(el: Element): string {
    const copia = el.cloneNode(true) as Element;
    copia.querySelectorAll('.oculto, .ed, .ed-oculto, svg').forEach((n) => n.remove());
    return (copia.textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  /** El texto que enseña el tema en ese elemento (párrafos separados por una línea en blanco). */
  function textoDeLaPagina(el: HTMLElement): string {
    if (el.matches('.cuerpo')) {
      return Array.from(el.children)
        .filter((p) => p.tagName === 'P')
        .map((p) => plano(p))
        .join('\n\n');
    }
    const pistas = Array.from(el.querySelectorAll<HTMLElement>(':scope > .pista-movil, :scope > .pista-escritorio'));
    const visible = pistas.find((p) => ventana.getComputedStyle(p).display !== 'none') ?? pistas[0];
    return visible ? plano(visible) : plano(el);
  }

  /** Lo que el tema pone delante del texto en el mismo elemento: el número de la sección («02 · »). */
  function prefijo(el: HTMLElement, clave: string): string {
    if (el.matches('.cuerpo') || el.querySelector('.pista-movil')) {
      return '';
    }
    const todo = plano(el);
    const propio = (valor(clave).trim() ? valor(clave) : defTexto(clave)!.vacio).replace(/\s+/g, ' ').trim();
    return propio && todo !== propio && todo.endsWith(propio) ? todo.slice(0, todo.length - propio.length) : '';
  }

  // ------------------------------------------------------------------ marcar la página
  let pagina: PaginaEditor = 'inicio';

  function campos(): CampoTexto[] {
    const conFila = (clave: string) => !!fila(clave);
    return pagina === 'inicio' ? [...CAMPOS_TEXTO_MARCO, ...camposTextoInicio(conFila)] : [...CAMPOS_TEXTO_MARCO];
  }

  const principal = (clave: string) =>
    doc.querySelector<HTMLElement>(`.ed-texto[data-campo="${CSS.escape(clave)}"]:not([data-rep])`);

  function marcar(el: HTMLElement, clave: string, repetido: boolean): void {
    const def = defTexto(clave)!;
    el.classList.add('ed-texto');
    el.dataset['campo'] = clave;
    if (repetido) {
      el.dataset['rep'] = '1';
    } else {
      delete el.dataset['rep'];
    }
    el.tabIndex = 0;
    el.setAttribute('role', 'button');
    el.setAttribute('aria-label', `Editar ${def.nombre.toLowerCase()}${def.obligatorio ? ' (obligatorio)' : ''}`);
    el.classList.toggle('es-vacio', !valor(clave).trim());
  }

  function decorar(paginaActual: PaginaEditor): void {
    pagina = paginaActual;
    const vistas = new Set<string>();
    const nuevos: Record<string, string> = {};
    for (const campo of campos()) {
      doc.querySelectorAll<HTMLElement>(campo.selector).forEach((el) => {
        if (el.closest(DENTRO_DE_LA_CAPA) || el.classList.contains('ed-escondido')) {
          return;
        }
        const clave = typeof campo.clave === 'string' ? campo.clave : campo.clave(el);
        if (!clave || !defTexto(clave)) {
          return;
        }
        marcar(el, clave, vistas.has(clave));
        // Solo las claves cuyo defecto depende de los datos (nunca un texto con foto ni el «Escribe aquí…» de un vacío).
        if (!vistas.has(clave) && !fila(clave) && defectoFijo(clave) === null && !defectos.has(clave)) {
          const texto = textoDeLaPagina(el);
          if (defectoDeLaPagina(clave, texto)) {
            defectos.set(clave, texto);
            nuevos[clave] = texto;
          }
        }
        vistas.add(clave);
      });
    }
    if (Object.keys(nuevos).length) {
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'defectos', textos: nuevos });
    }
    marcarErrores();
  }

  function icono(): SVGSVGElement {
    const svg = doc.createElementNS(SVG, 'svg');
    for (const [k, v] of [['viewBox', '0 0 24 24'], ['fill', 'none'], ['stroke', 'currentColor'], ['stroke-width', '2'], ['stroke-linecap', 'round'], ['stroke-linejoin', 'round'], ['aria-hidden', 'true']]) {
      svg.setAttribute(k, v);
    }
    // `#i-aviso` del boceto.
    const circulo = doc.createElementNS(SVG, 'circle');
    for (const [k, v] of [['cx', '12'], ['cy', '12'], ['r', '9']]) {
      circulo.setAttribute(k, v);
    }
    const marca = doc.createElementNS(SVG, 'path');
    marca.setAttribute('d', 'M12 7.5v5.5M12 16.5h.01');
    svg.append(circulo, marca);
    return svg;
  }

  const idError = (clave: string) => `e-${clave.replace(/[^a-z0-9_-]/gi, '-')}`;

  /** Boceto `texto()` y `refrescar()`: contorno rojo en todos los sitios; el mensaje, solo tras el primero. */
  function marcarErrores(): void {
    doc.querySelectorAll<HTMLElement>('.ed-error[data-de]').forEach((p) => {
      const clave = p.dataset['de']!;
      const el = p.previousElementSibling as HTMLElement | null;
      if (!errores[clave] || !el || el.dataset['campo'] !== clave || el.dataset['rep']) {
        p.remove();
      }
    });
    doc.querySelectorAll<HTMLElement>('.ed-texto[data-campo]').forEach((el) => {
      const clave = el.dataset['campo']!;
      const mensaje = errores[clave];
      el.classList.toggle('es-error', !!mensaje);
      if (!mensaje) {
        el.removeAttribute('aria-invalid');
        el.removeAttribute('aria-describedby');
        return;
      }
      el.setAttribute('aria-invalid', 'true');
      if (el.dataset['rep']) {
        return;
      }
      el.setAttribute('aria-describedby', idError(clave));
      let p = el.nextElementSibling as HTMLElement | null;
      if (p?.classList.contains('ed-campo')) {
        p = p.nextElementSibling as HTMLElement | null;
      }
      if (!p?.classList.contains('ed-error') || p.dataset['de'] !== clave) {
        p = doc.createElement('p');
        p.className = 'ed ed-error';
        p.id = idError(clave);
        p.dataset['de'] = clave;
        p.append(icono(), doc.createElement('span'));
        (activa?.el === el ? activa.copia : el).after(p);
      }
      p.querySelector('span')!.textContent = mensaje;
    });
  }

  // ------------------------------------------------------------------ editar
  function entrarEdicion(el: HTMLElement): void {
    if (activa) {
      salirEdicion(false);
    }
    const clave = el.dataset['campo'];
    const def = clave ? defTexto(clave) : null;
    if (!clave || !def) {
      return;
    }
    const copia = el.cloneNode(false) as HTMLElement;
    Array.from(copia.attributes).forEach((a) => {
      if (!ATRIBUTOS_COPIA.test(a.name)) {
        copia.removeAttribute(a.name);
      }
    });
    copia.classList.remove('ed-texto', 'es-vacio', 'es-error', 'ed-dato', 'ed-dato--caja', 'es-activo');
    copia.classList.add('ed-campo');
    const antes = prefijo(el, clave);
    if (antes) {
      copia.append(doc.createTextNode(antes));
    }
    const campo = doc.createElement('span');
    campo.className = `ed-campo__texto${def.multi ? ' ed-multi' : ''}`;
    try {
      campo.contentEditable = 'plaintext-only';
    } catch {
      campo.contentEditable = 'true';
    }
    if (campo.contentEditable !== 'plaintext-only') {
      campo.contentEditable = 'true';
    }
    campo.setAttribute('role', 'textbox');
    campo.setAttribute('aria-label', `${def.nombre}${def.obligatorio ? ' (obligatorio)' : ''}`);
    if (def.multi) {
      campo.setAttribute('aria-multiline', 'true');
    }
    if (errores[clave]) {
      campo.setAttribute('aria-invalid', 'true');
      campo.setAttribute('aria-describedby', idError(clave));
    }
    campo.dataset['vacio'] = def.vacio;
    campo.textContent = valor(clave);
    copia.append(campo);
    el.after(copia);
    el.classList.add('ed-escondido');
    activa = { el, clave, copia, campo };
    campo.focus({ preventScroll: true });
    const rango = doc.createRange();
    rango.selectNodeContents(campo);
    rango.collapse(false);
    const seleccion = ventana.getSelection();
    seleccion?.removeAllRanges();
    seleccion?.addRange(rango);
    mostrarChip();
    seleccionar(el.closest<HTMLElement>('[data-bloque]')?.dataset['bloque'] ?? null);
  }

  function salirEdicion(devolverFoco: boolean): void {
    const a = activa;
    if (!a) {
      return;
    }
    activa = null;
    chip.hidden = true;
    const conFoco = a.copia.contains(doc.activeElement);
    a.el.classList.remove('ed-escondido');
    a.copia.remove();
    const el = a.el.isConnected ? a.el : principal(a.clave);
    if (el) {
      el.classList.toggle('es-vacio', !valor(a.clave).trim());
      if (devolverFoco || conFoco) {
        el.focus({ preventScroll: true });
      }
    }
    marcarErrores();
  }

  function leer(campo: HTMLElement, multi: boolean): string {
    let v = (campo.innerText ?? campo.textContent ?? '').replace(/ /g, ' ');
    v = multi ? v.replace(/\n$/, '') : v.replace(/\n/g, ' ');
    return v.trim() ? v : '';
  }

  function mostrarChip(): void {
    if (!activa) {
      return;
    }
    const def = defTexto(activa.clave)!;
    const n = leer(activa.campo, def.multi).length;
    const tope = n >= def.max;
    chip.replaceChildren();
    const nombre = doc.createElement('b');
    nombre.textContent = def.nombre;
    const marca = doc.createElement('span');
    marca.className = 'ed-marca';
    marca.textContent = def.obligatorio ? 'obligatorio' : 'opcional';
    const cuenta = doc.createElement('span');
    cuenta.className = 'ed-cont';
    cuenta.textContent = `${n} / ${def.max}${tope ? ' · máximo' : ''}`;
    chip.append(nombre, marca, cuenta);
    chip.classList.toggle('es-tope', tope);
    chip.hidden = false;
    const r = activa.campo.getBoundingClientRect();
    let arriba = r.top + ventana.scrollY - chip.offsetHeight - 14;
    if (arriba < 4) {
      arriba = r.bottom + ventana.scrollY + 14;
    }
    chip.style.top = `${arriba}px`;
    chip.style.left = `${Math.max(8, Math.min(r.right + ventana.scrollX - chip.offsetWidth + 4, doc.documentElement.clientWidth - chip.offsetWidth - 8))}px`;
  }

  function alTope(): void {
    mostrarChip();
    chip.classList.add('es-tope');
    if (Date.now() - ultimoTope > ESPERA_TOPE) {
      ultimoTope = Date.now();
      decir(`Has llegado al máximo de ${defTexto(activa!.clave)!.max} caracteres.`);
    }
  }

  // ------------------------------------------------------------------ eventos
  const objetivo = (evento: Event): Element | null => {
    const t = evento.target as Element | null;
    return t && typeof t.closest === 'function' ? t : null;
  };
  const textoDe = (evento: Event) => objetivo(evento)?.closest<HTMLElement>('.ed-texto') ?? null;
  const enEdicion = (evento: Event) => (activa && objetivo(evento)?.closest('.ed-campo__texto') === activa.campo ? activa : null);

  const alBajar = (evento: MouseEvent) => {
    const el = textoDe(evento);
    if (el && evento.button === 0) {
      evento.preventDefault();
      entrarEdicion(el);
    }
  };

  const alTeclear = (evento: KeyboardEvent) => {
    const a = enEdicion(evento);
    if (a) {
      const multi = defTexto(a.clave)!.multi;
      if (evento.key === 'Escape') {
        evento.preventDefault();
        evento.stopImmediatePropagation();
        salirEdicion(true);
      } else if (evento.key === 'Enter' && !multi) {
        evento.preventDefault();
        salirEdicion(true);
        decir('Texto cambiado.');
      }
      return;
    }
    const el = textoDe(evento);
    if (el && (evento.key === 'Enter' || evento.key === 'F2')) {
      evento.preventDefault();
      evento.stopImmediatePropagation();
      entrarEdicion(el);
    }
  };

  const alAntesDeEscribir = (evento: InputEvent) => {
    const a = enEdicion(evento);
    if (!a) {
      return;
    }
    const def = defTexto(a.clave)!;
    if (!def.multi && (evento.inputType === 'insertParagraph' || evento.inputType === 'insertLineBreak')) {
      evento.preventDefault();
      return;
    }
    if (!evento.inputType.startsWith('insert') || evento.inputType === 'insertFromPaste' || evento.isComposing) {
      return;
    }
    const anade = evento.data ? evento.data.length : 1;
    const elegido = String(ventana.getSelection() ?? '').length;
    if (leer(a.campo, def.multi).length - elegido + anade > def.max) {
      evento.preventDefault();
      alTope();
    }
  };

  const alPegar = (evento: ClipboardEvent) => {
    const a = enEdicion(evento);
    if (!a) {
      return;
    }
    evento.preventDefault();
    const def = defTexto(a.clave)!;
    let texto = evento.clipboardData?.getData('text/plain') ?? '';
    if (!def.multi) {
      texto = texto.replace(/\s*\n\s*/g, ' ');
    }
    const sitio = def.max - (leer(a.campo, def.multi).length - String(ventana.getSelection() ?? '').length);
    if (sitio <= 0) {
      alTope();
      return;
    }
    if (texto.length > sitio) {
      texto = texto.slice(0, sitio);
      alTope();
    }
    doc.execCommand('insertText', false, texto);
  };

  const alEscribir = (evento: Event) => {
    const a = enEdicion(evento);
    if (!a) {
      return;
    }
    const texto = leer(a.campo, defTexto(a.clave)!.multi);
    poner(a.clave, texto);
    enviar({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave: a.clave, texto });
    // El tema repinta los demás sitios de la misma clave (el original está escondido y no se ve).
    alTema(paraTema());
    mostrarChip();
  };

  const alSalirDelFoco = (evento: FocusEvent) => {
    const a = enEdicion(evento);
    const destino = evento.relatedTarget as Node | null;
    if (a && !(destino && a.copia.contains(destino))) {
      salirEdicion(false);
    }
  };

  // En captura: antes que los oyentes de la capa que dejan inertes los formularios y controles de la web.
  doc.addEventListener('mousedown', alBajar, true);
  doc.addEventListener('keydown', alTeclear, true);
  doc.addEventListener('beforeinput', alAntesDeEscribir as EventListener, true);
  doc.addEventListener('paste', alPegar, true);
  doc.addEventListener('input', alEscribir, true);
  doc.addEventListener('focusout', alSalirDelFoco, true);

  return {
    borrador(bloques) {
      salirEdicion(false);
      filas = bloques;
      alTema(paraTema());
    },
    errores(nuevos) {
      errores = nuevos;
      marcarErrores();
      if (activa) {
        const e = errores[activa.clave];
        activa.campo.toggleAttribute('aria-invalid', !!e);
      }
    },
    irA(clave) {
      salirEdicion(false);
      const el =
        clave === 'portada.foto'
          ? doc.querySelector<HTMLElement>('.ed-bloque[data-bloque="portada"] > .ed-barra .ed-nombre')
          : principal(clave);
      if (!el) {
        return null;
      }
      el.focus({ preventScroll: true });
      return Math.max(0, Math.round(el.getBoundingClientRect().top + ventana.scrollY));
    },
    decorar,
    quitar() {
      salirEdicion(false);
      doc.removeEventListener('mousedown', alBajar, true);
      doc.removeEventListener('keydown', alTeclear, true);
      doc.removeEventListener('beforeinput', alAntesDeEscribir as EventListener, true);
      doc.removeEventListener('paste', alPegar, true);
      doc.removeEventListener('input', alEscribir, true);
      doc.removeEventListener('focusout', alSalirDelFoco, true);
      chip.remove();
      voz.remove();
    },
  };
}
