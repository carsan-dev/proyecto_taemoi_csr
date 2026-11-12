import type { PaginaEditor } from './candados-linea-comarcal';
import { encajarEnVista } from './colocar-flotantes';
import { GALERIA_NUEVA, MAX_GALERIA, TEXTOS_GALERIA, esFotoGaleria, ordenGaleria } from './fotos-galeria';
import { TEXTOS_PROFESORES, esFotoProfesor } from './fotos-profesores';
import {
  FORMATOS_FOTO,
  MAX_DESCRIPCION_FOTO,
  claveDescripcion,
  esClaveSubida,
  esFotoEditable,
  fotoDeDescripcion,
  fotoOpcional,
  queFoto,
  textoFotoVacia,
} from './fotos-sueltas';
import {
  type BloqueBorrador,
  CANAL_EDITOR_WEB,
  type EstadoSubidaFoto,
  type MensajeDelEditor,
  type MensajeFotos,
  type OpcionHojaMenu,
} from './puente-editor';
import { recortar } from './textos-editor';

/**
 * Fotos sueltas en el sitio (W12f-1; boceto `boceto-panel-contenido-web-comarcal.html`: `fotoCapa`, `subidaHtml`,
 * `figura`, `abrirMenuFoto`, `abrirDescribir`, `abrirFlot`, `cerrarFlot`). Parte de la capa de edición (su chunk).
 *
 * Sobre la foto de la portada, la de la presentación y la de cada «Texto con foto» pone su capa: un botón que abre el
 * menú de la foto (Cambiar o Añadir, Describir, Volver a la anterior y, salvo en la portada, Quitar), la marca «Nueva,
 * sin guardar», el error y el estado de la subida. Un «Texto con foto» sin foto no la pinta: la capa pone el hueco.
 *
 * La capa no llama a la API: el fichero elegido va al panel (`subir-foto`), que lo sube y devuelve el estado de cada
 * subida (`fotos`). Lo demás (describir, volver, quitar, cancelar) también lo guarda el panel en su borrador. Mientras
 * sube, la foto elegida se ve aquí, atenuada, desde el propio fichero (URL local, nunca enviada).
 *
 * W12f-2a: las fotos de la galería llevan la misma capa (su menú añade «Mover antes», «Mover después» y «Quitar de la
 * galería»). Tras ellas, la capa pone su casilla de subida (`galeria.nueva`) y «Añadir foto» o, con 6, la nota del tope
 * (boceto `galeriaHtml`).
 *
 * W12f-2b: la foto de cada profesor (`profesor.<n>.foto`), en la tarjeta que el tema marca en el editor
 * (`data-ed-profesor`). Sin foto, el tema pinta el monograma (`role="img"`): la capa va en una caja propia encima, no
 * dentro (lo que hay dentro de una imagen no lo oye el lector), con «Añadir foto» (boceto `fotoCapa` con `mono`). Su menú
 * dice «Sale con sus iniciales» en «Quitar foto».
 */
export interface OpcionesFotosEditor {
  ventana: Window;
  enviar: (mensaje: MensajeDelEditor) => void;
  /** d541: el panel es estrecho (< 768 px): el menú de la foto sale como hoja inferior del panel. */
  estrecho?: () => boolean;
}

export interface FotosEditor {
  /** d541: la respuesta de la hoja del panel al menú de la foto (`op` null: se cerró sin elegir). */
  respuestaMenu(id: number, op: string | null, devolverFoco: boolean): void;
  /** Llega el borrador del panel. */
  borrador(bloques: BloqueBorrador[]): void;
  /** Fotos cambiadas, con anterior y subidas (`fotos`). */
  estado(estado: Pick<MensajeFotos, 'cambiadas' | 'anteriores' | 'subidas'>): void;
  errores(errores: Record<string, string>): void;
  /** Terminó la subida: «Foto subida: describe la foto…». */
  describir(clave: string): void;
  /** Lleva el foco a una foto (o abre su descripción); null si no es una foto de aquí. */
  irA(clave: string): number | null;
  decorar(pagina: PaginaEditor): void;
  quitar(): void;
}

interface Sitio {
  clave: string;
  figura: HTMLElement;
  arriba: boolean;
  /** W12f-2b: sobre el monograma de un profesor sin foto (boceto `fotoCapa` con `mono`). */
  mono?: boolean;
}

const SVG = 'http://www.w3.org/2000/svg';
type Figura = readonly [string, Readonly<Record<string, string>>];
/** Iconos del boceto (`#i-foto`, `#i-mas`, `#i-texto`, `#i-deshacer`, `#i-antes`, `#i-despues`, `#i-papelera`, `#i-aviso`). */
const ICONOS: Readonly<Record<string, { trazo: string; figuras: readonly Figura[] }>> = {
  foto: {
    trazo: '1.8',
    figuras: [
      ['rect', { x: '3', y: '5', width: '18', height: '14', rx: '2' }],
      ['circle', { cx: '9', cy: '10', r: '1.8' }],
      ['path', { d: 'm4 17 5-5 4 4 2.5-2.5L20 17' }],
    ],
  },
  mas: { trazo: '2', figuras: [['path', { d: 'M12 5v14M5 12h14' }]] },
  texto: { trazo: '2', figuras: [['path', { d: 'M4 6h16M4 11h16M4 16h10' }]] },
  deshacer: { trazo: '2', figuras: [['path', { d: 'M9 14 4 9l5-5' }], ['path', { d: 'M4 9h10.5a5.5 5.5 0 0 1 0 11H11' }]] },
  antes: { trazo: '2', figuras: [['path', { d: 'M19 12H5M11 6l-6 6 6 6' }]] },
  despues: { trazo: '2', figuras: [['path', { d: 'M5 12h14M13 6l6 6-6 6' }]] },
  papelera: { trazo: '2', figuras: [['path', { d: 'M4 7h16M10 11v6M14 11v6M6 7l1 12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-12M9 7V4.5h6V7' }]] },
  aviso: { trazo: '2', figuras: [['circle', { cx: '12', cy: '12', r: '9' }], ['path', { d: 'M12 7.5v5.5M12 16.5h.01' }]] },
};

const ACEPTA = 'image/jpeg,image/png,image/webp';
const idDe = (clave: string) => clave.replace(/[^a-z0-9_-]/gi, '-');

export function activarFotosEditor(opciones: OpcionesFotosEditor): FotosEditor {
  const { ventana, enviar } = opciones;
  const estrecho = opciones.estrecho ?? (() => false);
  const doc = ventana.document;
  /** Las URL locales de la ventana del iframe (la foto elegida mientras sube). */
  const urls = (ventana as Window & typeof globalThis).URL;
  let filas: BloqueBorrador[] = [];
  let cambiadas = new Set<string>();
  let anteriores = new Set<string>();
  let subidas: Record<string, EstadoSubidaFoto> = {};
  let errores: Record<string, string> = {};
  let pagina: PaginaEditor = 'inicio';
  /** La foto elegida que se está subiendo (URL local); `vista`: el panel ya dijo que sube. */
  const previas = new Map<string, { url: string; vista: boolean }>();
  /** Tras elegir un fichero: al pintar su subida (o su fallo), el foco va a ella (boceto `pintar('c:…')`). */
  let enfocarSubida: string | null = null;
  let eligiendo: string | null = null;
  /** Tras volver a la anterior o quitar: si el foco se pierde al repintar, vuelve a esta foto. */
  let enfocarFoto: string | null = null;
  /** `enHoja`: el menú está en la hoja del panel (con su `id`, las opciones ofrecidas y si hay que avisar al cerrarlo). */
  let abierto: { clave: string; tipo: 'menu' | 'describir'; enHoja?: { id: number; ops: string[]; avisar: boolean } } | null = null;
  /** d541: numera cada apertura de la hoja; el panel lo devuelve y lo que llega con otro número se descarta. */
  let ultimaHoja = 0;
  let esperaDescribir: ReturnType<typeof setTimeout> | null = null;
  /** La foto cuya descripción espera a abrirse (`esperaDescribir`). */
  let esperaDescribirClave: string | null = null;

  const entrada = doc.createElement('input');
  entrada.type = 'file';
  entrada.accept = ACEPTA;
  entrada.className = 'ed ed-oculto';
  entrada.tabIndex = -1;
  entrada.setAttribute('aria-hidden', 'true');
  const flot = doc.createElement('div');
  flot.className = 'ed flot';
  flot.hidden = true;
  doc.body.append(entrada, flot);

  // ------------------------------------------------------------------ utilidades
  const fila = (clave: string) => filas.find((f) => f.clave === clave);
  const tieneFoto = (clave: string) => !!fila(clave)?.imagenUrl;
  const que = (clave: string) =>
    queFoto(
      clave,
      (bloque) => fila(`${bloque}.titulo`)?.texto ?? '',
      (foto) => ordenGaleria(filas).indexOf(foto) + 1,
      (foto) => fila(foto.replace(/\.foto$/, '.nombre'))?.texto ?? '',
    );
  const boton = (clave: string) => doc.querySelector<HTMLButtonElement>(`.ed-foto[data-foto="${CSS.escape(clave)}"]`);
  /** A dónde vuelve el foco: el botón de una foto o, con `galeria.nueva`, «Añadir foto» de la galería. */
  const destinoFoco = (clave: string) =>
    clave === GALERIA_NUEVA ? doc.querySelector<HTMLButtonElement>('.ed-anadir-foto') : boton(clave);

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

  function botonCapa(clase: string, accion: string, clave: string, ...hijos: (Node | string)[]): HTMLButtonElement {
    const b = nodo('button', clase, ...hijos);
    b.type = 'button';
    b.dataset['accion'] = accion;
    b.dataset['foto'] = clave;
    return b;
  }

  // ------------------------------------------------------------------ dónde están las fotos
  function sitios(): Sitio[] {
    if (pagina !== 'inicio') {
      return [];
    }
    const lista: Sitio[] = [];
    const portada = doc.querySelector<HTMLElement>('section#portada .portada-foto');
    if (portada) {
      lista.push({ clave: 'portada.foto', figura: portada, arriba: false });
    }
    const presentacion = doc.querySelector<HTMLElement>('section#presentacion .presentacion-foto');
    if (presentacion) {
      lista.push({ clave: 'presentacion.foto', figura: presentacion, arriba: true });
    }
    doc.querySelectorAll<HTMLElement>('section[id^="texto-foto-"]').forEach((seccion) => {
      const n = /^texto-foto-([1-9][0-9]?)$/.exec(seccion.id);
      const tf = seccion.querySelector<HTMLElement>('.tf');
      if (!n || !tf) {
        return;
      }
      const delTema = tf.querySelector<HTMLElement>(':scope > figure.tf-foto:not(.ed-hueco-foto)');
      let hueco = tf.querySelector<HTMLElement>(':scope > .ed-hueco-foto');
      if (delTema) {
        hueco?.remove();
      } else if (!hueco) {
        // Boceto `textoFotoHtml`: el hueco de la foto se ve aunque no haya foto (solo en el editor).
        hueco = nodo('figure', 'ed foto tf-foto ed-hueco-foto');
        tf.appendChild(hueco);
      }
      lista.push({ clave: `texto_foto.${n[1]}.foto`, figura: delTema ?? hueco!, arriba: false });
    });
    lista.push(...sitiosGaleria(), ...sitiosProfesores());
    return lista;
  }

  /**
   * W12f-2b: la foto de cada tarjeta de profesor del editor (`li.profe[data-ed-profesor]`) o, sin foto, la caja de la capa
   * sobre su monograma (se quita cuando el tema pinta la foto).
   */
  function sitiosProfesores(): Sitio[] {
    if (pagina !== 'inicio') {
      return [];
    }
    const lista: Sitio[] = [];
    doc.querySelectorAll<HTMLElement>('section#profesores li.profe[data-ed-profesor]').forEach((li) => {
      const n = li.dataset['edProfesor'] ?? '';
      if (!/^[1-9][0-9]?$/.test(n)) {
        return;
      }
      const clave = `profesor.${n}.foto`;
      const figura = li.querySelector<HTMLElement>(':scope > figure.profe-foto');
      const monograma = li.querySelector<HTMLElement>(':scope > .monograma');
      let capa = li.querySelector<HTMLElement>(':scope > .ed-mono__capa');
      if (figura || !monograma) {
        capa?.remove();
        if (figura) {
          lista.push({ clave, figura, arriba: false });
        }
        return;
      }
      monograma.classList.add('ed-mono');
      if (!capa) {
        capa = nodo('div', 'ed ed-mono__capa');
      }
      if (monograma.nextElementSibling !== capa) {
        monograma.after(capa);
      }
      lista.push({ clave, figura: capa, arriba: false, mono: true });
    });
    return lista;
  }

  /** La galería del tema (`section#galeria ol.galeria`), o null. */
  const listaGaleria = () => (pagina === 'inicio' ? doc.querySelector<HTMLElement>('section#galeria ol.galeria') : null);

  /**
   * W12f-2a: cada foto de la galería que pinta el tema, con su clave. El tema las pinta en el orden de `ordenGaleria`;
   * por si aún pinta el borrador de antes, cada figura se casa primero por su foto y si no, por su puesto.
   */
  function sitiosGaleria(): Sitio[] {
    const ol = listaGaleria();
    if (!ol) {
      return [];
    }
    const libres = ordenGaleria(filas);
    const figuras = Array.from(ol.querySelectorAll<HTMLElement>(':scope > li:not([data-ed-galeria]) > figure.foto'));
    const porFoto = figuras.map((figura) => {
      const src = figura.querySelector(':scope > img:not(.ed)')?.getAttribute('src');
      const clave = src ? libres.find((c) => fila(c)?.imagenUrl === src) : undefined;
      if (clave) {
        libres.splice(libres.indexOf(clave), 1);
      }
      return clave;
    });
    return figuras
      .map((figura, i) => ({ clave: porFoto[i] ?? libres.shift(), figura, arriba: false }))
      .filter((s): s is Sitio => !!s.clave);
  }

  /**
   * Boceto `galeriaHtml`: tras las fotos, la casilla de la foto que sube (`galeria.nueva`) y «Añadir foto» («Añade la
   * primera foto» sin ninguna) o, con 6 (contando la que sube), la nota «6 de 6 fotos». Solo en el editor.
   */
  function pintarGaleria(): void {
    const ol = listaGaleria();
    const sub = subidas[GALERIA_NUEVA];
    if (!sub) {
      // La foto nueva ya entró (o se canceló): fuera su vista local.
      const previa = previas.get(GALERIA_NUEVA);
      if (previa?.vista) {
        urls.revokeObjectURL(previa.url);
        previas.delete(GALERIA_NUEVA);
      }
    }
    if (!ol) {
      return;
    }
    const activo = doc.activeElement;
    let pendiente = ol.querySelector<HTMLElement>(':scope > li[data-ed-galeria="pendiente"]');
    if (sub) {
      if (!pendiente) {
        pendiente = nodo('li', 'ed', nodo('figure', 'foto'));
        pendiente.dataset['edGaleria'] = 'pendiente';
        // En el documento antes de pintar su subida: su «Cancelar subida» recibe el foco.
        ol.insertBefore(pendiente, ol.querySelector(':scope > li[data-ed-galeria="fin"]'));
      }
      pintarSubida(GALERIA_NUEVA, pendiente.firstElementChild as HTMLElement, sub, false);
    } else if (pendiente) {
      if (pendiente.contains(activo)) {
        enfocarFoto = GALERIA_NUEVA;
      }
      pendiente.remove();
      pendiente = null;
    }

    const fotos = ordenGaleria(filas).length;
    const firma = fotos + (sub ? 1 : 0) >= MAX_GALERIA ? 'llena' : fotos ? 'anadir' : 'primera';
    let fin = ol.querySelector<HTMLElement>(':scope > li[data-ed-galeria="fin"]');
    if (fin?.dataset['firma'] !== firma) {
      const teniaFoco = !!fin?.contains(activo);
      let contenido: HTMLElement;
      if (firma === 'llena') {
        contenido = nodo('div', 'ed ed-galeria-llena', nodo('b', '', TEXTOS_GALERIA.llena), nodo('span', '', TEXTOS_GALERIA.llenaNota));
        contenido.setAttribute('role', 'note');
      } else {
        const texto = firma === 'anadir' ? TEXTOS_GALERIA.anadir : TEXTOS_GALERIA.anadirPrimera;
        contenido = botonCapa('ed ed-anadir-foto', 'foto-subir', GALERIA_NUEVA, icono('mas'), texto, nodo('small', '', FORMATOS_FOTO));
      }
      const nuevo = nodo('li', 'ed', contenido);
      nuevo.dataset['edGaleria'] = 'fin';
      nuevo.dataset['firma'] = firma;
      fin?.remove();
      fin = nuevo;
      ol.appendChild(fin);
      if (teniaFoco && contenido instanceof HTMLButtonElement) {
        contenido.focus({ preventScroll: true });
      }
    }
    // Siempre al final: las fotos del tema, la que sube y el botón (o la nota).
    if (pendiente && (pendiente.parentElement !== ol || pendiente.nextElementSibling !== fin)) {
      ol.insertBefore(pendiente, fin);
    }
    if (ol.lastElementChild !== fin) {
      if (pendiente) {
        ol.appendChild(pendiente);
      }
      ol.appendChild(fin);
    }
  }

  // ------------------------------------------------------------------ pintar la capa de cada foto (boceto `fotoCapa`)
  function pintar(sitio: Sitio): void {
    const { clave, figura, arriba } = sitio;
    const mono = !!sitio.mono;
    const tiene = tieneFoto(clave);
    const otra = figura.querySelector<HTMLButtonElement>(':scope > .ed-foto')?.dataset['foto'];
    if (otra && otra !== clave) {
      // Galería: el tema reutiliza la figura con otra foto. Fuera lo de la otra.
      figura
        .querySelectorAll(':scope > .ed-foto, :scope > .ed-nueva, :scope > .ed-error--foto, :scope > .ed-subida, :scope > .ed-fallo')
        .forEach((el) => el.remove());
    }
    const sub = subidas[clave];
    const error = errores[claveDescripcion(clave)] ?? errores[clave];
    const nombre = que(clave);
    const idError = `e-${idDe(clave)}-f`;

    let b = figura.querySelector<HTMLButtonElement>(':scope > .ed-foto');
    if (!b) {
      b = botonCapa('ed ed-foto', 'foto', clave);
      b.setAttribute('aria-haspopup', 'menu');
      figura.appendChild(b);
    }
    // Boceto `fotoCapa`: sobre el monograma, sin el hueco punteado y con su nombre («Añadir foto de …»).
    b.classList.toggle('es-vacia', !tiene && !mono);
    b.classList.toggle('ed-foto--arriba', arriba);
    b.classList.toggle('es-error', !!error);
    b.setAttribute('aria-expanded', String(abierto?.tipo === 'menu' && abierto.clave === clave));
    if (tiene) {
      b.setAttribute('aria-label', `Cambiar foto ${nombre}`);
    } else if (mono) {
      b.setAttribute('aria-label', `${TEXTOS_PROFESORES.anadirFoto} ${nombre}`);
    } else {
      b.removeAttribute('aria-label');
    }
    if (error) {
      b.setAttribute('aria-describedby', idError);
    } else {
      b.removeAttribute('aria-describedby');
    }
    const firma = `${tiene}|${mono}`;
    if (b.dataset['firma'] !== firma) {
      b.dataset['firma'] = firma;
      const vacia = mono ? TEXTOS_PROFESORES.anadirFoto : textoFotoVacia(clave);
      const pista = nodo('span', 'ed-foto__pista', icono(tiene ? 'foto' : 'mas'), tiene ? 'Cambiar foto' : vacia);
      b.replaceChildren(pista, ...(tiene || mono ? [] : [nodo('span', 'ed-foto__formatos', FORMATOS_FOTO)]));
    }

    // «Nueva, sin guardar»
    let nueva = figura.querySelector<HTMLElement>(':scope > .ed-nueva');
    if (tiene && !sub && cambiadas.has(clave)) {
      nueva ??= figura.appendChild(nodo('span', 'ed ed-nueva', nodo('span', 'ed-punto'), 'Nueva, sin guardar'));
    } else {
      nueva?.remove();
    }

    // Error de la foto (falta, o falta describirla)
    let p = figura.querySelector<HTMLElement>(':scope > .ed-error--foto');
    if (error) {
      if (!p) {
        p = figura.appendChild(nodo('p', 'ed ed-error ed-error--foto', icono('aviso'), nodo('span', '')));
        p.id = idError;
      }
      p.querySelector('span')!.textContent = error;
    } else {
      p?.remove();
    }

    pintarSubida(clave, figura, sub, tiene);
  }

  /** Boceto `subidaHtml` y `figura`: progreso con «Cancelar subida» o fallo con «Elegir otra foto». */
  function pintarSubida(clave: string, figura: HTMLElement, sub: EstadoSubidaFoto | undefined, tiene: boolean): void {
    const subiendo = sub?.estado === 'subiendo';
    figura.classList.toggle('es-subiendo', subiendo);

    // La foto elegida, mientras sube
    const previa = previas.get(clave);
    if (previa && subiendo) {
      previa.vista = true;
    } else if (previa && (sub?.estado === 'error' || previa.vista)) {
      urls.revokeObjectURL(previa.url);
      previas.delete(clave);
    }
    let img = figura.querySelector<HTMLImageElement>(':scope > .ed-foto__previa');
    const url = subiendo ? previas.get(clave)?.url : undefined;
    if (url) {
      if (!img) {
        img = nodo('img', 'ed ed-foto__previa');
        img.alt = '';
        figura.insertBefore(img, figura.querySelector(':scope > .ed-foto'));
      }
      if (img.getAttribute('src') !== url) {
        img.src = url;
      }
    } else {
      img?.remove();
    }

    const actual = figura.querySelector<HTMLElement>(':scope > .ed-subida, :scope > .ed-fallo');
    const firma = !sub ? '' : sub.estado === 'subiendo' ? `s|${sub.nombre}` : `e|${sub.titulo}|${sub.msg}|${tiene}`;
    if (actual && actual.dataset['firma'] === firma) {
      if (sub?.estado === 'subiendo') {
        ponerPorcentaje(actual, sub.pct);
      }
      return;
    }
    const teniaFoco = !!actual?.contains(doc.activeElement);
    actual?.remove();
    if (!sub) {
      if (teniaFoco) {
        boton(clave)?.focus({ preventScroll: true });
      }
      return;
    }
    let panel: HTMLElement;
    let foco: HTMLElement;
    if (sub.estado === 'subiendo') {
      const pct = nodo('span', '');
      pct.dataset['pct'] = clave;
      const barra = nodo('div', 'ed-progreso', nodo('i', ''));
      barra.setAttribute('role', 'progressbar');
      barra.setAttribute('aria-label', 'Subida de la foto');
      barra.setAttribute('aria-valuemin', '0');
      barra.setAttribute('aria-valuemax', '100');
      foco = botonCapa('ed-btn', 'cancelar-subida', clave, 'Cancelar subida');
      panel = nodo('div', 'ed ed-subida', nodo('div', 'ed-subida__fila', nodo('span', '', `Subiendo «${sub.nombre}»…`), pct), barra, foco);
      panel.setAttribute('role', 'status');
      ponerPorcentaje(panel, sub.pct);
    } else {
      foco = botonCapa('ed-btn ed-btn--sec', 'foto-subir', clave, icono('foto'), 'Elegir otra foto');
      panel = nodo(
        'div',
        'ed ed-fallo',
        nodo('p', 'ed-fallo__t', icono('aviso'), sub.titulo),
        nodo('p', '', sub.msg),
        ...(tiene ? [nodo('p', 'ed-fallo__n', 'La foto de antes sigue puesta.')] : []),
        nodo('div', 'ed-fallo__acc', foco, botonCapa('ed-btn', 'cancelar-subida', clave, 'Cerrar')),
      );
      panel.setAttribute('role', 'alert');
    }
    panel.dataset['firma'] = firma;
    figura.appendChild(panel);
    if (teniaFoco || enfocarSubida === clave) {
      enfocarSubida = null;
      foco.focus({ preventScroll: true });
    }
  }

  function ponerPorcentaje(panel: HTMLElement, pct: number): void {
    const n = Math.round(pct);
    const texto = panel.querySelector<HTMLElement>('[data-pct]');
    const barra = panel.querySelector<HTMLElement>('.ed-progreso');
    if (texto && texto.textContent !== `${n} %`) {
      texto.textContent = `${n} %`;
    }
    if (barra) {
      barra.style.setProperty('--p', `${n}%`);
      barra.setAttribute('aria-valuenow', String(n));
    }
  }

  function repintar(): void {
    for (const sitio of sitios()) {
      pintar(sitio);
    }
    pintarGaleria();
    const activo = doc.activeElement;
    if (enfocarFoto && (!activo || activo === doc.body)) {
      const b = destinoFoco(enfocarFoto);
      if (b) {
        enfocarFoto = null;
        b.focus({ preventScroll: true });
      }
    }
  }

  // ------------------------------------------------------------------ menú y diálogo (boceto `abrirFlot`)
  function abrirFlot(contenido: Node[], ancla: HTMLElement, clase: string): void {
    flot.className = `ed flot${clase ? ' ' + clase : ''}`;
    flot.removeAttribute('role');
    flot.removeAttribute('aria-labelledby');
    flot.replaceChildren(...contenido);
    flot.hidden = false;
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
    flot.querySelector<HTMLElement>('[role=menuitem]:not([aria-disabled=true]), textarea')?.focus({ preventScroll: true });
  }

  function cerrar(devolverFoco: boolean): void {
    if (flot.hidden && !abierto?.enHoja) {
      return;
    }
    const clave = abierto?.clave ?? null;
    const hoja = abierto?.enHoja;
    abierto = null;
    if (hoja?.avisar) {
      // d541: la capa cierra sola el menú que el panel tiene en su hoja: que la quite.
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-menu', menu: 'foto', id: hoja.id });
    }
    flot.hidden = true;
    flot.replaceChildren();
    if (clave) {
      const b = boton(clave);
      b?.setAttribute('aria-expanded', 'false');
      if (devolverFoco) {
        b?.focus({ preventScroll: true });
      }
    }
  }

  const datosOpcion = (
    op: string,
    texto: string,
    icono: OpcionHojaMenu['icono'],
    pie: string,
    peligro = false,
    desactivada = false,
  ): OpcionHojaMenu => ({
    op,
    texto,
    pie,
    icono,
    desactivada,
    peligro,
  });

  function opcion(
    op: string,
    texto: string,
    nombreIcono: keyof typeof ICONOS,
    pie: string,
    peligro = false,
    desactivada = false,
  ): HTMLButtonElement {
    const b = nodo(
      'button',
      `flot__op${peligro ? ' flot__op--peligro' : ''}`,
      icono(nombreIcono),
      nodo('span', '', texto, ...(pie ? [nodo('small', '', pie)] : [])),
    );
    b.type = 'button';
    b.setAttribute('role', 'menuitem');
    b.dataset['op'] = op;
    if (desactivada) {
      b.setAttribute('aria-disabled', 'true');
    }
    return b;
  }

  /**
   * Boceto `abrirMenuFoto`. W12f-2a: en la galería, «Mover antes» y «Mover después» (desactivados en los extremos) y
   * «Quitar de la galería». W12f-2b: en un profesor, «Quitar foto» dice «Sale con sus iniciales».
   */
  function abrirMenu(clave: string, b: HTMLElement): void {
    if (abierto?.enHoja) {
      cerrar(false);
    }
    const tiene = tieneFoto(clave);
    const alt = fila(clave)?.texto ?? '';
    const datos: OpcionHojaMenu[] = [datosOpcion('subir', tiene ? 'Cambiar foto' : 'Añadir foto', 'foto', FORMATOS_FOTO)];
    if (tiene) {
      datos.push(datosOpcion('describir', 'Describir la foto', 'texto', alt ? `«${recortar(alt, 46)}»` : 'Falta: qué se ve, para quien no puede verla'));
    }
    if (cambiadas.has(clave) && anteriores.has(clave)) {
      datos.push(datosOpcion('anterior', 'Volver a la anterior', 'deshacer', ''));
    }
    const galeria = esFotoGaleria(clave);
    if (galeria) {
      const lista = ordenGaleria(filas);
      const i = lista.indexOf(clave);
      datos.push(
        datosOpcion('antes', TEXTOS_GALERIA.moverAntes, 'antes', '', false, i <= 0),
        datosOpcion('despues', TEXTOS_GALERIA.moverDespues, 'despues', '', false, i === -1 || i === lista.length - 1),
      );
    }
    if (tiene && fotoOpcional(clave)) {
      const pie = esFotoProfesor(clave) ? TEXTOS_PROFESORES.quitarFotoPie : '';
      datos.push(datosOpcion('quitar', galeria ? TEXTOS_GALERIA.quitar : 'Quitar foto', 'papelera', pie, true));
    }
    if (estrecho()) {
      // d541: hoja inferior del panel; ella devuelve la opción elegida (`respuestaMenu`). Las desactivadas no valen.
      const id = ++ultimaHoja;
      abierto = { clave, tipo: 'menu', enHoja: { id, ops: datos.filter((d) => !d.desactivada).map((d) => d.op), avisar: true } };
      b.setAttribute('aria-expanded', 'true');
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja-menu', menu: 'foto', id, titulo: `Foto ${que(clave)}`, opciones: datos });
      return;
    }
    const ops: HTMLElement[] = [];
    for (const d of datos) {
      if (d.peligro && ops.length) {
        const sep = nodo('div', 'flot__sep');
        sep.setAttribute('role', 'separator');
        ops.push(sep);
      }
      ops.push(opcion(d.op, d.texto, d.icono, d.pie, d.peligro, d.desactivada));
    }
    const titulo = nodo('p', 'flot__titulo', `Foto ${que(clave)}`);
    titulo.id = 'ed-flot-t';
    const lista = nodo('div', 'flot__lista', ...ops);
    lista.setAttribute('role', 'menu');
    lista.setAttribute('aria-labelledby', 'ed-flot-t');
    lista.dataset['foto'] = clave;
    abierto = { clave, tipo: 'menu' };
    b.setAttribute('aria-expanded', 'true');
    abrirFlot([titulo, lista], b.querySelector<HTMLElement>('.ed-foto__pista') ?? b, '');
  }

  /** Boceto `abrirDescribir`. */
  function abrirDescribir(clave: string, trasSubir: boolean): void {
    const b = boton(clave);
    if (!b || !tieneFoto(clave)) {
      return;
    }
    cerrar(false);
    const alt = fila(clave)?.texto ?? '';
    const titulo = nodo('h2', 'flot__titulo', `${trasSubir ? 'Foto subida: ' : ''}describe la foto ${que(clave)}`);
    titulo.id = 'ed-flot-t';
    const etiqueta = nodo('label', 'campo-et', 'Descripción', nodo('span', 'marca', 'obligatorio'));
    etiqueta.htmlFor = 'ed-alt';
    const area = nodo('textarea', 'area');
    area.id = 'ed-alt';
    area.rows = 3;
    area.maxLength = MAX_DESCRIPCION_FOTO;
    area.dataset['foto'] = clave;
    area.setAttribute('aria-describedby', 'ed-alt-a ed-alt-c');
    area.value = alt;
    const ayuda = nodo('p', '', 'Qué se ve, para quien no puede verla. Sin nombres de alumnos.');
    ayuda.id = 'ed-alt-a';
    const contador = nodo('span', 'contador');
    contador.id = 'ed-alt-c';
    const listo = nodo('button', 'btn btn--sec', 'Listo');
    listo.type = 'button';
    listo.dataset['op'] = 'listo';
    abierto = { clave, tipo: 'describir' };
    abrirFlot(
      [titulo, etiqueta, area, nodo('div', 'campo-pie', ayuda, contador), nodo('div', 'flot__acciones', listo)],
      b.querySelector<HTMLElement>('.ed-foto__pista') ?? b,
      'flot--describir',
    );
    flot.setAttribute('role', 'dialog');
    flot.setAttribute('aria-labelledby', 'ed-flot-t');
    contar(contador, alt.length);
  }

  function contar(contador: HTMLElement, n: number): void {
    const tope = n >= MAX_DESCRIPCION_FOTO;
    contador.textContent = `${n} / ${MAX_DESCRIPCION_FOTO}${tope ? ' · máximo' : ''}`;
    contador.classList.toggle('es-tope', tope);
  }

  function elegir(clave: string): void {
    eligiendo = clave;
    entrada.value = '';
    entrada.click();
  }

  // ------------------------------------------------------------------ eventos
  const objetivo = (evento: Event): Element | null => {
    const t = evento.target as Element | null;
    return t && typeof t.closest === 'function' ? t : null;
  };

  const alPulsar = (evento: MouseEvent) => {
    const t = objetivo(evento);
    const accion = t?.closest<HTMLElement>('.ed [data-accion][data-foto], .ed-foto[data-accion]');
    const clave = accion?.dataset['foto'];
    if (!accion || !esClaveSubida(clave)) {
      return;
    }
    switch (accion.dataset['accion']) {
      case 'foto':
        if (!esFotoEditable(clave)) {
          break;
        }
        if (abierto?.tipo === 'menu' && abierto.clave === clave) {
          cerrar(true);
        } else {
          abrirMenu(clave, accion);
        }
        break;
      case 'foto-subir':
        elegir(clave);
        break;
      case 'cancelar-subida':
        enviar({ canal: CANAL_EDITOR_WEB, tipo: 'foto', clave, accion: 'cancelar-subida' });
        break;
    }
  };

  const alElegir = () => {
    const archivo = entrada.files?.[0];
    const clave = eligiendo;
    eligiendo = null;
    if (!archivo || !clave) {
      return;
    }
    const antes = previas.get(clave);
    if (antes) {
      urls.revokeObjectURL(antes.url);
    }
    previas.set(clave, { url: urls.createObjectURL(archivo), vista: false });
    enfocarSubida = clave;
    entrada.value = '';
    enviar({ canal: CANAL_EDITOR_WEB, tipo: 'subir-foto', clave, archivo });
  };

  const alPulsarFlot = (evento: MouseEvent) => {
    const b = objetivo(evento)?.closest<HTMLElement>('[data-op]');
    if (b?.dataset['op'] && b.getAttribute('aria-disabled') !== 'true') {
      ejecutar(b.dataset['op']);
    }
  };

  /** La opción elegida del menú (flotante o de la hoja del panel) o «Listo» de la descripción. */
  function ejecutar(op: string): void {
    const actual = abierto;
    if (!actual) {
      return;
    }
    const { clave } = actual;
    switch (op) {
      case 'listo':
        cerrar(true);
        break;
      case 'subir':
        cerrar(false);
        elegir(clave);
        break;
      case 'describir':
        abrirDescribir(clave, false);
        break;
      case 'anterior':
      case 'quitar':
      case 'antes':
      case 'despues': {
        // La foto movida conserva el foco; al quitar una de la galería, va a la que ocupa su puesto, a la de antes o a
        // «Añadir foto» (boceto `pintar(sig ? … : 'a-foto')`).
        const lista = esFotoGaleria(clave) ? ordenGaleria(filas) : [];
        const i = lista.indexOf(clave);
        const destino = op === 'quitar' && i >= 0 ? (lista[i + 1] ?? lista[i - 1] ?? GALERIA_NUEVA) : clave;
        cerrar(true);
        // Si el tema repinta la foto (o pone el hueco), el foco vuelve a ella (boceto `pintar('f:…')`).
        enfocarFoto = destino;
        enviar({ canal: CANAL_EDITOR_WEB, tipo: 'foto', clave, accion: op as 'anterior' | 'quitar' | 'antes' | 'despues' });
        break;
      }
    }
  }

  const alTeclearFlot = (evento: KeyboardEvent) => {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      evento.stopPropagation();
      cerrar(true);
      return;
    }
    const items = Array.from(flot.querySelectorAll<HTMLElement>('[role=menuitem]'));
    if (evento.key === 'Tab' && items.length) {
      evento.preventDefault();
      cerrar(true);
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

  const alEscribirFlot = (evento: Event) => {
    const area = objetivo(evento);
    if (!area || area.id !== 'ed-alt' || !abierto) {
      return;
    }
    const clave = abierto.clave;
    const texto = (area as HTMLTextAreaElement).value.slice(0, MAX_DESCRIPCION_FOTO);
    filas = filas.map((f) => (f.clave === clave ? { ...f, texto } : f));
    contar(flot.querySelector<HTMLElement>('#ed-alt-c')!, texto.length);
    enviar({ canal: CANAL_EDITOR_WEB, tipo: 'descripcion', clave, texto });
  };

  const alEnfocar = (evento: FocusEvent) => {
    const t = objetivo(evento);
    if (enfocarFoto && t && t !== destinoFoco(enfocarFoto)) {
      enfocarFoto = null;
    }
  };

  const alBajar = (evento: MouseEvent) => {
    const t = objetivo(evento);
    if (!flot.hidden && t && !flot.contains(t) && !t.closest('.ed-foto[aria-expanded="true"]')) {
      cerrar(false);
    }
  };

  doc.addEventListener('click', alPulsar);
  doc.addEventListener('mousedown', alBajar);
  doc.addEventListener('focusin', alEnfocar);
  entrada.addEventListener('change', alElegir);
  flot.addEventListener('click', alPulsarFlot);
  flot.addEventListener('keydown', alTeclearFlot);
  flot.addEventListener('input', alEscribirFlot);

  return {
    respuestaMenu(id, op, devolverFoco) {
      const actual = abierto;
      if (!actual?.enHoja || actual.enHoja.id !== id || (op !== null && !actual.enHoja.ops.includes(op))) {
        return;
      }
      // La respuesta es del panel: cerrar ahora no se le avisa (eco). Cada opción sigue su camino, como en el menú flotante.
      actual.enHoja.avisar = false;
      if (op === null) {
        cerrar(devolverFoco);
      } else {
        ejecutar(op);
        if (abierto === actual) {
          // La opción no cerró el menú (p. ej. la foto ya no está): la hoja del panel ya se cerró.
          cerrar(false);
        }
      }
    },
    borrador(bloques) {
      filas = bloques;
      repintar();
    },
    estado(estado) {
      const antes = cambiadas;
      cambiadas = new Set(estado.cambiadas);
      // La foto recién elegida deja de estar cambiada (descartar, volver a la anterior, quitar): su descripción sobra.
      const sobra = (clave: string | null | undefined) => !!clave && antes.has(clave) && !cambiadas.has(clave);
      if (esperaDescribir !== null && sobra(esperaDescribirClave)) {
        clearTimeout(esperaDescribir);
        esperaDescribir = null;
        esperaDescribirClave = null;
      }
      if (abierto?.tipo === 'describir' && sobra(abierto.clave)) {
        cerrar(false);
      }
      anteriores = new Set(estado.anteriores);
      subidas = estado.subidas;
      repintar();
    },
    errores(nuevos) {
      errores = nuevos;
      repintar();
    },
    describir(clave) {
      // Tras el borrador nuevo el tema repinta la foto: se espera a que esté.
      if (esperaDescribir !== null) {
        clearTimeout(esperaDescribir);
      }
      esperaDescribirClave = clave;
      esperaDescribir = setTimeout(() => {
        esperaDescribir = null;
        esperaDescribirClave = null;
        repintar();
        abrirDescribir(clave, true);
      }, 50);
    },
    irA(clave) {
      const foto = esFotoEditable(clave) ? clave : fotoDeDescripcion(clave);
      const b = foto ? boton(foto) : null;
      if (!foto || !b) {
        return null;
      }
      cerrar(false);
      b.focus({ preventScroll: true });
      if (foto !== clave) {
        if (esperaDescribir !== null) {
          clearTimeout(esperaDescribir);
        }
        esperaDescribirClave = foto;
        esperaDescribir = setTimeout(() => {
          esperaDescribir = null;
          esperaDescribirClave = null;
          abrirDescribir(foto, false);
        }, 50);
      }
      return Math.max(0, Math.round(b.getBoundingClientRect().top + ventana.scrollY));
    },
    decorar(paginaActual) {
      pagina = paginaActual;
      repintar();
    },
    quitar() {
      if (esperaDescribir !== null) {
        clearTimeout(esperaDescribir);
      }
      if (abierto?.enHoja) {
        cerrar(false);
      }
      doc.removeEventListener('click', alPulsar);
      doc.removeEventListener('mousedown', alBajar);
      doc.removeEventListener('focusin', alEnfocar);
      previas.forEach((p) => urls.revokeObjectURL(p.url));
      previas.clear();
      entrada.remove();
      flot.remove();
    },
  };
}
