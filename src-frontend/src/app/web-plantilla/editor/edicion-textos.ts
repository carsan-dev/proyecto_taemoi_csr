import { rellenarHuecos } from '../temas/linea-comarcal/contenido';
import { TEXTOS_LINEA_COMARCAL } from '../temas/linea-comarcal/textos';
import type { PaginaEditor } from './candados-linea-comarcal';
import { type DefHueco, HUECOS, huecoDe } from './huecos-editor';
import { type BloqueBorrador, CANAL_EDITOR_WEB, type MensajeDelEditor } from './puente-editor';
import {
  CAMPOS_TEXTO_MARCO,
  CampoTexto,
  MARCA_HERMANOS,
  bloqueTextoFoto,
  camposTextoInicio,
  camposTextoPagina,
  defTexto,
  defectoDeLaPagina,
  defectoFijo,
} from './textos-editor';

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
 *
 * W12d-bis: los textos de las otras seis páginas (`camposTextoPagina`). Un hueco del texto (`{x}`, `{n}`…, `HUECOS`) va
 * en la copia como una ficha que no se escribe (boceto `conFichas`): enseña el dato de la vista previa, las flechas la
 * saltan como un carácter y al leer el texto vuelve el token. Borrarla (Retroceso o Suprimir junto a ella, cortar, o
 * escribir o pegar sobre una selección que la tenga) no la borra: el panel pregunta antes («¿Quitar … de este texto?»,
 * `pedir-quitar-dato`) y, si se confirma, se quita con un comando de edición (el deshacer del navegador la devuelve).
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
  /** W12d-bis: la respuesta del panel al diálogo de quitar el dato de una ficha. */
  quitarDato(quitar: boolean): void;
  /** Lleva el foco a un texto; da dónde empieza (px desde arriba del documento) o null si no está en la página. */
  irA(clave: string): number | null;
  /** W12e: entra a editar ese texto (el primero vacío de un bloque recién añadido); false si no está en la página. */
  editar(clave: string): boolean;
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
  /** W12d-bis: el dato de cada hueco que enseñan sus fichas. */
  valores: Record<string, string>;
  /** W12d-bis: el texto (con tokens) y las fichas tras el último cambio aceptado, para devolverlos si falta alguna. */
  previo: string;
  fichas: string[];
}

/** W12d-bis: un intento de borrar una ficha, a la espera de la respuesta del panel. */
interface QuitarPendiente {
  edicion: Edicion;
  /** Lo que se borraría y la selección que había (vuelve con «Cancelar»). */
  rango: Range;
  seleccion: Range | null;
  fichas: HTMLElement[];
  /** Si se confirma: borrar (con la limpieza de `rellenarHuecos` si solo se borran fichas) o escribir encima. */
  accion: { tipo: 'borrar'; soloFichas: boolean } | { tipo: 'escribir'; texto: string };
}

const ENVUELTO = 'ed-envuelto';
const FICHA = 'ed-ficha';
const TOKEN = /(\{\w+\})/;
const esFicha = (n: Node | null | undefined): n is HTMLElement =>
  !!n && n.nodeType === Node.ELEMENT_NODE && (n as HTMLElement).classList.contains(FICHA);
const normal = (t: string) => t.replace(/\s+/g, ' ').trim();
/** Lo que se limpia junto a una ficha que se quita: espacios (no saltos de línea) y signos. */
const PEGADO = /[^\S\n]|[·()«»¿¡,;:.!?]/;

export function activarEdicionTextos(opciones: OpcionesEdicionTextos): EdicionTextos {
  const { ventana, enviar, alTema, seleccionar } = opciones;
  const doc = ventana.document;
  let filas: BloqueBorrador[] = [];
  let errores: Record<string, string> = {};
  const defectos = new Map<string, string>();
  let activa: Edicion | null = null;
  let ultimoTope = 0;
  let pendiente: QuitarPendiente | null = null;
  /** Mientras la propia capa edita con `execCommand` (quitar una ficha confirmada): sin comprobar fichas. */
  let ejecutando = false;
  /** Hasta que se quita la capa. */
  let vivo = true;

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
    // W12d-bis: en Tarifas, el texto de hermanos con su marca, para saber qué fila del cuadro es (`MARCA_HERMANOS`).
    if (pagina === 'tarifas') {
      const i = lista.findIndex((f) => f.clave === 'tarifas.hermanos');
      const texto = i >= 0 ? (lista[i].texto ?? '') : (defectoFijo('tarifas.hermanos') ?? TEXTOS_LINEA_COMARCAL['tarifas.hermanos']);
      const marcada = { clave: 'tarifas.hermanos', texto: texto + MARCA_HERMANOS, imagenUrl: null, orden: 0, oculto: false };
      if (i >= 0) {
        lista[i] = { ...lista[i], texto: marcada.texto };
      } else {
        lista.push(marcada);
      }
    }
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
    return (copia.textContent ?? '').replaceAll(MARCA_HERMANOS, '').replace(/\s+/g, ' ').trim();
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
    return pagina === 'inicio'
      ? [...CAMPOS_TEXTO_MARCO, ...camposTextoInicio(conFila)]
      : [...CAMPOS_TEXTO_MARCO, ...camposTextoPagina(pagina, conFila)];
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
    // Un texto editable no puede quedar dentro de algo escondido a los lectores (las cabeceras de la tabla de grupos y
    // la línea de pasos de la preinscripción): en el editor se les quita `aria-hidden` (vuelve al quitar la capa).
    const oculto = el.parentElement?.closest('[aria-hidden="true"]');
    if (oculto?.closest('.lc')) {
      oculto.removeAttribute('aria-hidden');
      oculto.setAttribute('data-ed-aria-oculto', '');
    }
    el.setAttribute('role', 'button');
    el.setAttribute('aria-label', `Editar ${def.nombre.toLowerCase()}${def.obligatorio ? ' (obligatorio)' : ''}`);
    el.classList.toggle('es-vacio', !valor(clave).trim());
  }

  /** Lo que pinta el tema en una clave: su texto o, vacío, su «Escribe aquí…». */
  function ver(clave: string): string {
    const v = normal(valor(clave));
    return v || (defTexto(clave)?.vacio ?? '');
  }

  /**
   * W12d-bis: los textos sueltos del elemento antes de su primer hijo o después del último, envueltos una vez en un
   * `span` (como el envoltorio de los candados): ese `span` es el texto. Null si no hay texto.
   */
  function envuelto(el: HTMLElement, lado: 'antes' | 'despues'): HTMLElement | null {
    const ya = el.querySelector<HTMLElement>(`:scope > .${ENVUELTO}[data-lado="${lado}"]`);
    if (ya) {
      return ya;
    }
    const hijos = Array.from(el.childNodes);
    const elementos = hijos.filter((n) => n.nodeType === Node.ELEMENT_NODE);
    if (!elementos.length) {
      return null;
    }
    const corte = hijos.indexOf(lado === 'antes' ? elementos[0] : elementos[elementos.length - 1]);
    const nodos = (lado === 'antes' ? hijos.slice(0, corte) : hijos.slice(corte + 1)).filter((n) => n.nodeType === Node.TEXT_NODE);
    if (!nodos.some((n) => (n.nodeValue ?? '').trim())) {
      return null;
    }
    const span = doc.createElement('span');
    span.className = ENVUELTO;
    span.dataset['lado'] = lado;
    nodos[0].before(span);
    span.append(...nodos);
    return span;
  }

  function decorar(paginaActual: PaginaEditor): void {
    pagina = paginaActual;
    const vistas = new Set<string>();
    const nuevos: Record<string, string> = {};
    for (const campo of campos()) {
      doc.querySelectorAll<HTMLElement>(campo.selector).forEach((encontrado) => {
        if (encontrado.closest(DENTRO_DE_LA_CAPA) || encontrado.classList.contains('ed-escondido')) {
          return;
        }
        const clave = typeof campo.clave === 'string' ? campo.clave : campo.clave(encontrado, ver);
        if (!clave || !defTexto(clave)) {
          return;
        }
        const el = campo.envolver ? envuelto(encontrado, campo.envolver) : encontrado;
        if (!el || el.classList.contains('ed-escondido')) {
          return;
        }
        marcar(el, clave, vistas.has(clave));
        // Los párrafos de un texto de varias líneas que se está editando: los enseña la copia.
        if (activa && activa.clave === clave && el !== activa.el && el.parentElement === activa.el.parentElement) {
          el.classList.add('ed-escondido');
        }
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
    const valores = valoresDe(el, clave);
    pintarCampo(campo, clave, valor(clave), valores);
    copia.append(campo);
    el.after(copia);
    el.classList.add('ed-escondido');
    if (def.multi) {
      doc.querySelectorAll<HTMLElement>(`.ed-texto[data-campo="${CSS.escape(clave)}"][data-rep]`).forEach((otro) => {
        if (otro.parentElement === el.parentElement) {
          otro.classList.add('ed-escondido');
        }
      });
    }
    activa = { el, clave, copia, campo, valores, previo: textoReal(campo), fichas: tokensDe(campo) };
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
    pendiente = null;
    chip.hidden = true;
    const conFoco = a.copia.contains(doc.activeElement);
    a.el.classList.remove('ed-escondido');
    doc
      .querySelectorAll<HTMLElement>(`.ed-texto.ed-escondido[data-campo="${CSS.escape(a.clave)}"]`)
      .forEach((otro) => otro.classList.remove('ed-escondido'));
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

  /**
   * El texto real del campo, sin `innerText`: este aplica el CSS del tema (`text-transform: uppercase` del titular) y lo
   * guardaría en mayúsculas. Texto tal cual; `<br>` es un salto; un bloque (`div`/`p`) que meta el navegador, salto antes.
   */
  function textoReal(nodo: Node): string {
    let v = '';
    nodo.childNodes.forEach((n) => {
      if (n.nodeType === Node.TEXT_NODE) {
        v += n.nodeValue ?? '';
      } else if (esFicha(n)) {
        // W12d-bis: la ficha de un hueco vuelve a ser su token.
        v += n.dataset['token'] ?? '';
      } else if (n.nodeName === 'BR') {
        v += '\n';
      } else if (n.nodeType === Node.ELEMENT_NODE) {
        if ((n.nodeName === 'DIV' || n.nodeName === 'P') && v && !v.endsWith('\n')) {
          v += '\n';
        }
        v += textoReal(n);
      }
    });
    return v;
  }

  function leer(campo: HTMLElement, multi: boolean): string {
    let v = textoReal(campo).replace(/ /g, ' ');
    v = multi ? v.replace(/\n$/, '') : v.replace(/\n/g, ' ');
    return v.trim() ? v : '';
  }

  // ------------------------------------------------------------------ fichas de los huecos (W12d-bis)

  /**
   * Los datos de los huecos que enseña el tema en `el`: se leen comparando su texto con el de la clave (los huecos son
   * lo que cambia). `sede.leyenda_lleno` va enlazada con `horarios.lleno` (su texto, también el del borrador). Lo que no
   * se pueda leer (otra variante, el «Escribe aquí…»), con el dato de ejemplo del boceto.
   */
  function valoresDe(el: HTMLElement, clave: string): Record<string, string> {
    const huecos = HUECOS[clave];
    if (!huecos) {
      return {};
    }
    const valores: Record<string, string> = {};
    const plantilla = normal(valor(clave));
    const partes = plantilla.split(TOKEN);
    const nombres: string[] = [];
    const patron = partes
      .map((parte) => {
        const token = /^\{(\w+)\}$/.exec(parte);
        if (token) {
          nombres.push(token[1]);
          return '(.+?)';
        }
        return parte.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      })
      .join('');
    const leido = nombres.length ? new RegExp(`^${patron}$`).exec(normal(plano(el))) : null;
    nombres.forEach((n, i) => {
      if (leido && valores[n] === undefined) {
        valores[n] = leido[i + 1];
      }
    });
    for (const [n, def] of Object.entries(huecos)) {
      valores[n] ??= def.ejemplo;
    }
    if (clave === 'sede.leyenda_lleno') {
      const lleno = fila('horarios.lleno');
      valores['x'] = normal(lleno ? (lleno.texto ?? '') : TEXTOS_LINEA_COMARCAL['horarios.lleno']) || huecos['x'].ejemplo;
    }
    return valores;
  }

  /** Boceto `conFichas`: la ficha de un hueco, que no se escribe; el lector oye el dato, su nombre y que no se edita. */
  function ficha(nombre: string, def: DefHueco, dato: string): HTMLElement {
    const f = doc.createElement('span');
    f.className = FICHA;
    f.contentEditable = 'false';
    f.dataset['token'] = `{${nombre}}`;
    f.dataset['que'] = def.que;
    f.dataset['donde'] = def.donde;
    f.dataset['valor'] = dato;
    if (def.candado) {
      f.dataset['candado'] = def.candado;
    }
    const visto = doc.createElement('span');
    visto.setAttribute('aria-hidden', 'true');
    visto.textContent = dato;
    const oido = doc.createElement('span');
    oido.className = 'ed-oculto';
    oido.textContent = `${dato} (${def.que}: no se edita aquí)`;
    f.append(visto, oido);
    return f;
  }

  /** El texto de la clave en la copia: lo escrito como texto y cada hueco de la clave como su ficha. */
  function pintarCampo(campo: HTMLElement, clave: string, texto: string, valores: Record<string, string>): void {
    campo.replaceChildren();
    for (const parte of texto.split(TOKEN)) {
      const token = /^\{(\w+)\}$/.exec(parte);
      const def = token ? huecoDe(clave, token[1]) : null;
      if (token && def) {
        campo.append(ficha(token[1], def, valores[token[1]] ?? def.ejemplo));
      } else if (parte) {
        campo.append(doc.createTextNode(parte));
      }
    }
  }

  const fichasDe = (campo: HTMLElement) => Array.from(campo.querySelectorAll<HTMLElement>(`.${FICHA}`));
  const tokensDe = (campo: HTMLElement) => fichasDe(campo).map((f) => f.dataset['token'] ?? '');

  /** Los tokens de `antes` que ya no están en `ahora` (cada uno, tantas veces como falte). */
  function faltan(antes: readonly string[], ahora: readonly string[]): string[] {
    const quedan = [...ahora];
    return antes.filter((t) => {
      const i = quedan.indexOf(t);
      if (i < 0) {
        return true;
      }
      quedan.splice(i, 1);
      return false;
    });
  }

  /**
   * Red de seguridad (W12d-bis): un cambio que el navegador no deja parar antes (composición IME, teclas muertas,
   * dictado, teclados del móvil, rehacer, soltar texto, el corrector) ha quitado alguna ficha sin confirmar. Vuelve el
   * texto de antes con todas sus fichas, el cursor tras la primera que faltaba, y el panel pregunta por ella como
   * siempre. True si había que devolverlo.
   */
  function devolverFichas(a: Edicion): boolean {
    const perdidas = faltan(a.fichas, tokensDe(a.campo));
    if (!perdidas.length) {
      return false;
    }
    pintarCampo(a.campo, a.clave, a.previo, a.valores);
    const primera = fichasDe(a.campo).find((f) => perdidas.includes(f.dataset['token'] ?? ''));
    if (!primera) {
      return true;
    }
    const cursor = doc.createRange();
    cursor.setStartAfter(primera);
    cursor.collapse(true);
    const sel = ventana.getSelection();
    sel?.removeAllRanges();
    sel?.addRange(cursor);
    const sobre = doc.createRange();
    sobre.selectNode(primera);
    pedirQuitar(a, sobre, [primera], { tipo: 'borrar', soloFichas: true });
    return true;
  }

  /** El nodo junto al cursor (sin texto en medio), hacia atrás o hacia delante; solo si es una ficha. */
  function fichaJunto(rango: Range, atras: boolean): HTMLElement | null {
    const nodo = rango.startContainer;
    const pos = rango.startOffset;
    let vecino: Node | null;
    if (nodo.nodeType === Node.TEXT_NODE) {
      const largo = (nodo.nodeValue ?? '').length;
      if (atras ? pos > 0 : pos < largo) {
        return null;
      }
      vecino = atras ? nodo.previousSibling : nodo.nextSibling;
    } else {
      vecino = nodo.childNodes[atras ? pos - 1 : pos] ?? null;
    }
    while (vecino && vecino.nodeType === Node.TEXT_NODE && !vecino.nodeValue) {
      vecino = atras ? vecino.previousSibling : vecino.nextSibling;
    }
    return esFicha(vecino) ? vecino : null;
  }

  function seleccionEn(campo: HTMLElement): Range | null {
    const sel = ventana.getSelection();
    if (!sel || !sel.rangeCount) {
      return null;
    }
    const r = sel.getRangeAt(0);
    return campo.contains(r.commonAncestorContainer) ? r : null;
  }

  /** Las fichas que toca un rango (no vacío). */
  const fichasEnRango = (campo: HTMLElement, rango: Range) => fichasDe(campo).filter((f) => rango.intersectsNode(f));

  /** El texto (con los tokens) de lo elegido: para el máximo al escribir o pegar encima. */
  function largoElegido(campo: HTMLElement): number {
    const r = seleccionEn(campo);
    return r && !r.collapsed ? textoReal(r.cloneContents()).length : 0;
  }

  /**
   * Las fichas que borraría el cambio que pide `evento` (antes de que pase): las de sus rangos o, si el navegador no
   * los da, las de la selección; con el cursor junto a una ficha, Retroceso o Suprimir hacia ella.
   */
  function fichasQueBorra(evento: InputEvent, campo: HTMLElement): { fichas: HTMLElement[]; rango: Range | null } {
    const objetivos = typeof evento.getTargetRanges === 'function' ? evento.getTargetRanges() : [];
    for (const objetivo of objetivos) {
      const r = doc.createRange();
      r.setStart(objetivo.startContainer, objetivo.startOffset);
      r.setEnd(objetivo.endContainer, objetivo.endOffset);
      if (!r.collapsed && campo.contains(r.commonAncestorContainer)) {
        const fichas = fichasEnRango(campo, r);
        if (fichas.length) {
          return { fichas, rango: r };
        }
      }
    }
    const r = seleccionEn(campo);
    if (!r) {
      return { fichas: [], rango: null };
    }
    if (!r.collapsed) {
      return { fichas: fichasEnRango(campo, r), rango: r.cloneRange() };
    }
    const tipo = evento.inputType;
    if (tipo.startsWith('delete')) {
      const atras = tipo.endsWith('Backward');
      const f = fichaJunto(r, atras);
      if (f) {
        const sobre = doc.createRange();
        sobre.selectNode(f);
        return { fichas: [f], rango: sobre };
      }
    }
    return { fichas: [], rango: null };
  }

  /** Pide al panel el diálogo «¿Quitar … de este texto?» con la frase de hoy y la de sin esos datos. */
  function pedirQuitar(a: Edicion, rango: Range, fichas: HTMLElement[], accion: QuitarPendiente['accion']): void {
    pendiente = { edicion: a, rango, seleccion: seleccionEn(a.campo)?.cloneRange() ?? null, fichas, accion };
    const plantilla = leer(a.campo, defTexto(a.clave)!.multi);
    const valores: Record<string, string> = {};
    for (const [, n] of plantilla.matchAll(/\{(\w+)\}/g)) {
      valores[n] = `{${n}}`;
    }
    for (const f of fichasDe(a.campo)) {
      valores[(f.dataset['token'] ?? '').slice(1, -1)] = f.dataset['valor'] ?? '';
    }
    const huecos = [...new Set(fichas.map((f) => (f.dataset['token'] ?? '').slice(1, -1)))];
    const sin = { ...valores, ...Object.fromEntries(huecos.map((n) => [n, ''])) };
    enviar({
      canal: CANAL_EDITOR_WEB,
      tipo: 'pedir-quitar-dato',
      clave: a.clave,
      huecos,
      antes: normal(rellenarHuecos(plantilla, valores)),
      despues: normal(rellenarHuecos(plantilla, sin)),
    });
  }

  /** El punto del DOM del campo en la posición `n` de su texto con tokens (null si cae dentro de una ficha). */
  function punto(campo: HTMLElement, n: number): [Node, number] | null {
    let cuenta = 0;
    const hijos = Array.from(campo.childNodes);
    for (let i = 0; i < hijos.length; i++) {
      const h = hijos[i];
      const largo = h.nodeType === Node.TEXT_NODE ? (h.nodeValue ?? '').length : esFicha(h) ? (h.dataset['token'] ?? '').length : h.nodeName === 'BR' ? 1 : -1;
      if (largo < 0) {
        return null;
      }
      if (n <= cuenta + largo) {
        if (h.nodeType === Node.TEXT_NODE) {
          return [h, n - cuenta];
        }
        return n === cuenta ? [campo, i] : n === cuenta + largo ? [campo, i + 1] : null;
      }
      cuenta += largo;
    }
    return n === cuenta ? [campo, hijos.length] : null;
  }

  /**
   * Quitar solo fichas: alrededor de cada una, el texto queda como lo deja `rellenarHuecos` sin ese dato (fuera los
   * espacios dobles, el «·» suelto, los paréntesis vacíos…), con un solo comando de edición. Solo se limpia lo que toca
   * a la ficha (los espacios y signos pegados a ella): lo que el usuario escribió en otra parte del texto no cambia.
   * Null si no se puede.
   */
  function rangoLimpio(campo: HTMLElement, fichas: HTMLElement[]): { rango: Range; texto: string } | null {
    const todo = textoReal(campo);
    const quitar = new Set(fichas);
    const quitadas: [number, number][] = [];
    const quedan: [number, number][] = [];
    let cuenta = 0;
    for (const h of Array.from(campo.childNodes)) {
      const t = h.nodeType === Node.TEXT_NODE ? (h.nodeValue ?? '') : esFicha(h) ? (h.dataset['token'] ?? '') : h.nodeName === 'BR' ? '\n' : null;
      if (t === null) {
        return null;
      }
      if (esFicha(h)) {
        (quitar.has(h) ? quitadas : quedan).push([cuenta, cuenta + t.length]);
      }
      cuenta += t.length;
    }
    if (!quitadas.length) {
      return null;
    }
    // Cada ficha con los espacios y signos pegados a ella (sin pasar de línea ni entrar en otra ficha); juntas si se tocan.
    const ventanas: [number, number][] = [];
    for (const [a0, b0] of quitadas) {
      let a = a0;
      let b = b0;
      while (a > 0 && PEGADO.test(todo[a - 1])) {
        a--;
      }
      while (b < todo.length && PEGADO.test(todo[b])) {
        b++;
      }
      const ultima = ventanas[ventanas.length - 1];
      if (ultima && a <= ultima[1]) {
        ultima[1] = Math.max(ultima[1], b);
      } else {
        ventanas.push([a, b]);
      }
    }
    const ini = ventanas[0][0];
    const hasta = ventanas[ventanas.length - 1][1];
    // Sin rehacer como texto una ficha que se queda.
    if (quedan.some(([a, b]) => a < hasta && b > ini)) {
      return null;
    }
    let texto = '';
    let desde = ini;
    for (const [a, b] of ventanas) {
      texto += todo.slice(desde, a);
      // Lo de alrededor, para limpiar como el tema: una letra si sigue el texto, la línea si la hay, nada en los bordes.
      const izquierda = a === 0 ? '' : todo[a - 1] === '\n' ? 'a\n' : 'a';
      const derecha = b === todo.length ? '' : todo[b] === '\n' ? '\na' : 'a';
      let trozo = todo.slice(a, b);
      for (const [qa, qb] of quitadas.filter(([qa]) => qa >= a && qa < b).reverse()) {
        trozo = trozo.slice(0, qa - a) + '{ed_quitado}' + trozo.slice(qb - a);
      }
      const limpio = rellenarHuecos(izquierda + trozo + derecha, { ed_quitado: '' });
      if (!limpio.startsWith(izquierda) || !limpio.endsWith(derecha) || limpio.length < izquierda.length + derecha.length) {
        return null;
      }
      texto += limpio.slice(izquierda.length, limpio.length - derecha.length);
      desde = b;
    }
    const inicio = punto(campo, ini);
    const fin = punto(campo, hasta);
    if (!inicio || !fin) {
      return null;
    }
    const rango = doc.createRange();
    rango.setStart(inicio[0], inicio[1]);
    rango.setEnd(fin[0], fin[1]);
    return { rango, texto };
  }

  /** Con el rango elegido, un comando de edición del navegador (entra en su deshacer). */
  function editarCon(rango: Range, texto: string): void {
    const sel = ventana.getSelection();
    sel?.removeAllRanges();
    sel?.addRange(rango);
    ejecutando = true;
    try {
      doc.execCommand(texto ? 'insertText' : 'delete', false, texto);
    } finally {
      ejecutando = false;
    }
  }

  function quitarDato(quitar: boolean): void {
    const p = pendiente;
    pendiente = null;
    if (!p || activa !== p.edicion || !p.edicion.campo.isConnected) {
      return;
    }
    const { campo } = p.edicion;
    campo.focus({ preventScroll: true });
    if (!quitar) {
      // «Cancelar»: todo como estaba y el foco, en el texto.
      const sel = ventana.getSelection();
      sel?.removeAllRanges();
      sel?.addRange(p.seleccion ?? p.rango);
      mostrarChip();
      return;
    }
    if (p.accion.tipo === 'borrar' && p.accion.soloFichas) {
      const limpio = rangoLimpio(campo, p.fichas);
      if (limpio) {
        editarCon(limpio.rango, limpio.texto);
        return;
      }
    }
    editarCon(p.rango, p.accion.tipo === 'escribir' ? p.accion.texto : '');
  }

  /** Lo elegido como se ve (con el dato de cada ficha), para copiar y cortar. */
  function textoVisible(rango: Range): string {
    const trozo = rango.cloneContents();
    trozo.querySelectorAll('.ed-oculto').forEach((n) => n.remove());
    return trozo.textContent ?? '';
  }

  /** Si un rango solo tiene fichas (y espacios): entonces se limpia el texto como el tema. */
  function soloFichas(rango: Range): boolean {
    const trozo = rango.cloneContents();
    trozo.querySelectorAll(`.${FICHA}`).forEach((n) => n.remove());
    return !(trozo.textContent ?? '').trim();
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
      // W12d-bis: la flecha salta la ficha entera, como un carácter.
      if ((evento.key === 'ArrowLeft' || evento.key === 'ArrowRight') && !evento.shiftKey && !evento.altKey && !evento.ctrlKey && !evento.metaKey) {
        const r = seleccionEn(a.campo);
        const atras = evento.key === 'ArrowLeft';
        const f = r?.collapsed ? fichaJunto(r, atras) : null;
        if (f) {
          evento.preventDefault();
          const nuevo = doc.createRange();
          if (atras) {
            nuevo.setStartBefore(f);
          } else {
            nuevo.setStartAfter(f);
          }
          nuevo.collapse(true);
          const sel = ventana.getSelection();
          sel?.removeAllRanges();
          sel?.addRange(nuevo);
        }
        return;
      }
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
    if (pendiente) {
      evento.preventDefault();
      return;
    }
    if (!def.multi && (evento.inputType === 'insertParagraph' || evento.inputType === 'insertLineBreak')) {
      evento.preventDefault();
      return;
    }
    // W12d-bis: una ficha no se borra sin confirmar (el deshacer del navegador sí la devuelve).
    const tipo = evento.inputType;
    if (!ejecutando && !evento.isComposing && (tipo.startsWith('delete') || tipo.startsWith('insert')) && tipo !== 'insertFromPaste') {
      const { fichas, rango } = fichasQueBorra(evento, a.campo);
      if (fichas.length && rango) {
        evento.preventDefault();
        if (tipo === 'insertFromDrop' || tipo === 'deleteByDrag') {
          return;
        }
        const escrito = tipo.startsWith('insert') ? (evento.data ?? (tipo === 'insertParagraph' || tipo === 'insertLineBreak' ? '\n' : '')) : '';
        pedirQuitar(a, rango, fichas, escrito ? { tipo: 'escribir', texto: escrito } : { tipo: 'borrar', soloFichas: soloFichas(rango) });
        return;
      }
    }
    if (!evento.inputType.startsWith('insert') || evento.inputType === 'insertFromPaste' || evento.isComposing) {
      return;
    }
    const anade = evento.data ? evento.data.length : 1;
    const elegido = largoElegido(a.campo);
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
    if (pendiente) {
      return;
    }
    const sitio = def.max - (leer(a.campo, def.multi).length - largoElegido(a.campo));
    if (sitio <= 0) {
      alTope();
      return;
    }
    if (texto.length > sitio) {
      texto = texto.slice(0, sitio);
      alTope();
    }
    // W12d-bis: pegar encima de una ficha también pregunta antes de quitarla.
    const r = seleccionEn(a.campo);
    const fichas = r && !r.collapsed ? fichasEnRango(a.campo, r) : [];
    if (r && fichas.length) {
      pedirQuitar(a, r.cloneRange(), fichas, { tipo: 'escribir', texto });
      return;
    }
    if (r) {
      editarCon(r.cloneRange(), texto);
    } else {
      doc.execCommand('insertText', false, texto);
    }
  };

  const alEscribir = (evento: Event) => {
    const a = enEdicion(evento);
    if (!a) {
      return;
    }
    // W12d-bis: si falta una ficha sin haberlo confirmado, vuelve y se pregunta (el texto no cambia).
    if (!ejecutando && devolverFichas(a)) {
      mostrarChip();
      return;
    }
    a.previo = textoReal(a.campo);
    a.fichas = tokensDe(a.campo);
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
    // Mientras el panel pregunta si se quita un dato, el foco está en su diálogo: la edición sigue.
    if (a && !pendiente && !(destino && a.copia.contains(destino))) {
      salirEdicion(false);
    }
  };

  /** W12d-bis: copiar y cortar dan el texto como se ve (sin la frase oculta de cada ficha para el lector). */
  const alCopiar = (evento: ClipboardEvent) => {
    const a = enEdicion(evento);
    const r = a ? seleccionEn(a.campo) : null;
    if (!a || !r || r.collapsed || !fichasDe(a.campo).length) {
      return;
    }
    evento.preventDefault();
    evento.clipboardData?.setData('text/plain', textoVisible(r));
    if (evento.type === 'cut' && !pendiente) {
      const fichas = fichasEnRango(a.campo, r);
      if (fichas.length) {
        pedirQuitar(a, r.cloneRange(), fichas, { tipo: 'borrar', soloFichas: soloFichas(r) });
      } else {
        editarCon(r.cloneRange(), '');
      }
    }
  };

  /** W12d-bis: una composición (IME, tecla muerta, dictado) no empieza sobre una ficha: la selección se recoge al final. */
  const alComponer = (evento: CompositionEvent) => {
    const a = enEdicion(evento);
    const r = a ? seleccionEn(a.campo) : null;
    if (a && r && !r.collapsed && fichasEnRango(a.campo, r).length) {
      ventana.getSelection()?.collapseToEnd();
    }
  };

  /** Una ficha no se arrastra fuera del texto (ni el texto que la lleva). */
  const alArrastrar = (evento: DragEvent) => {
    const a = enEdicion(evento);
    const r = a ? seleccionEn(a.campo) : null;
    if (a && r && !r.collapsed && fichasEnRango(a.campo, r).length) {
      evento.preventDefault();
    }
  };

  // En captura: antes que los oyentes de la capa que dejan inertes los formularios y controles de la web.
  doc.addEventListener('mousedown', alBajar, true);
  doc.addEventListener('keydown', alTeclear, true);
  doc.addEventListener('beforeinput', alAntesDeEscribir as EventListener, true);
  doc.addEventListener('paste', alPegar, true);
  doc.addEventListener('input', alEscribir, true);
  doc.addEventListener('focusout', alSalirDelFoco, true);
  doc.addEventListener('copy', alCopiar, true);
  doc.addEventListener('cut', alCopiar, true);
  doc.addEventListener('dragstart', alArrastrar, true);
  doc.addEventListener('compositionstart', alComponer, true);

  return {
    borrador(bloques) {
      salirEdicion(false);
      filas = bloques;
      alTema(paraTema());
      // W12d-bis: el tema cambia el texto de las filas del cuadro sin añadir nodos (el observador de la capa no lo ve):
      // en cuanto pinta la marca de hermanos, se vuelve a ver qué fila es cuál.
      if (pagina === 'tarifas') {
        ventana.setTimeout(() => vivo && decorar(pagina));
      }
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
    editar(clave) {
      const el = principal(clave);
      if (!el) {
        return false;
      }
      entrarEdicion(el);
      return true;
    },
    decorar,
    quitarDato,
    quitar() {
      vivo = false;
      salirEdicion(false);
      doc.querySelectorAll('[data-ed-aria-oculto]').forEach((el) => {
        el.removeAttribute('data-ed-aria-oculto');
        el.setAttribute('aria-hidden', 'true');
      });
      doc.removeEventListener('copy', alCopiar, true);
      doc.removeEventListener('cut', alCopiar, true);
      doc.removeEventListener('dragstart', alArrastrar, true);
      doc.removeEventListener('compositionstart', alComponer, true);
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
