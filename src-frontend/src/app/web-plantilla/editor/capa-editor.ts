import {
  Candado,
  HUECOS_FOTO_SEDE,
  NOMBRE_DESTINO,
  NOTA_DE_PAGINA,
  NOMBRE_EQUIPO,
  Parte,
  SIN_ENLACE_LINEA_COMARCAL,
  TEXTO_SIN_FOTO_SEDE,
  candadosDe,
  paginaDelDocumento,
  partesDe,
} from './candados-linea-comarcal';
import { NOTA_DEL_CASO, PARTE_DEL_CASO, SimuladorCaso, simuladorDeLaVista } from './casos-vista';
import { activarBloquesEditor } from './bloques-capa';
import { ESTILOS_CAPA_EDITOR } from './capa-editor-estilos';
import { cajasVisibles, colocarEvitando, fijarZonaVisible, MARGEN_VERTICAL, recolocarFlotantes, vistaDe } from './colocar-flotantes';
import { activarEdicionTextos, iconoCandado } from './edicion-textos';
import { activarFotosEditor } from './fotos-editor';
import { activarProfesoresEditor } from './profesores-capa';
import {
  BloqueBorrador,
  CANAL_EDITOR_WEB,
  type CasoVista,
  type DestinoPanel,
  type PasoPreinscripcion,
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
 * - los enlaces y botones de la web, fuera del orden de tabulación y sin navegar;
 * - W12c-ter: los formularios de la web, inertes: no se escribe, no se marca, no se firma y no se envía;
 * - W12d: los textos que se cambian en el sitio (`edicion-textos.ts`);
 * - W12f-1: las fotos sueltas (portada, presentación y «Texto con foto»: `fotos-editor.ts`);
 * - W12e: la barra de acciones de cada bloque de Inicio, sus pastillas y los huecos «Añadir bloque» (`bloques-capa.ts`);
 * - W12f-2b: la casilla del permiso o la constancia de cada profesor, su «Quitar» y «Añadir profesor» (`profesores-capa.ts`).
 * Los enlaces de los candados no navegan aquí: piden el destino al panel por el puente.
 */

export interface OpcionesCapaEditor {
  /** La ventana de la vista previa (el iframe). */
  ventana: Window;
  /** Quien la contiene (el panel); por defecto, `ventana.parent`. */
  padre?: Window;
  /** Llega un borrador del panel. */
  alBorrador: (bloques: BloqueBorrador[]) => void;
  /**
   * «Ver el caso» (W12c-bis) y el paso de la preinscripción (W12c-ter): cómo cambiar los datos de la vista previa;
   * null, los datos reales.
   */
  alCaso?: (simulador: SimuladorCaso | null) => void;
}

const SVG = 'http://www.w3.org/2000/svg';
/** Controles propios de la web: fuera del tabulador y sin efecto en el editor. */
const CONTROLES_WEB = 'a, button, input, select, textarea, summary, [tabindex]';
const RETRASO_OCULTAR = 160;
/** Controles del tema que pueden contener un dato: su enlace oculto va después de ellos, nunca dentro. */
const CONTROL_DEL_TEMA = 'a, button';
/** Partes plegadas del tema: sin enlace oculto, ni dentro ni al lado. */
const OCULTO_DEL_TEMA = '.horario:not(.abierto)';
/** Envoltorio que pone la capa alrededor de una parte de un elemento del tema para darle su candado (`dato()`). */
const ENVOLTORIO = 'ed-envoltorio';
/** Lo que se espera a que el tema pinte el caso o el paso de un texto del resumen de errores. */
const ESPERA_IR = 2000;

/** Iconos del boceto (`#i-info`, `#i-foto`): trazo y figuras. */
type Figura = readonly [string, Readonly<Record<string, string>>];
interface Icono {
  trazo: string;
  figuras: readonly Figura[];
}
const ICONO_INFO: Icono = {
  trazo: '2',
  figuras: [
    ['circle', { cx: '12', cy: '12', r: '9' }],
    ['path', { d: 'M12 11v5.5M12 7.5h.01' }],
  ],
};
const ICONO_FOTO: Icono = {
  trazo: '1.8',
  figuras: [
    ['rect', { x: '3', y: '5', width: '18', height: '14', rx: '2' }],
    ['circle', { cx: '9', cy: '10', r: '1.8' }],
    ['path', { d: 'm4 17 5-5 4 4 2.5-2.5L20 17' }],
  ],
};

function recorta(texto: string, maximo: number): string {
  return texto.length > maximo ? `${texto.slice(0, maximo - 1).trimEnd()}…` : texto;
}

export function activarCapaEditor(opciones: OpcionesCapaEditor): () => void {
  const { ventana, alBorrador, alCaso } = opciones;
  const padre = opciones.padre ?? ventana.parent;
  const doc = ventana.document;
  let seleccionado: string | null = null;
  let datoActivo: HTMLElement | null = null;
  let temporizador: ReturnType<typeof setTimeout> | null = null;
  let ultimoAlto = -1;
  /** «Ver el caso»: el caso que se ve, para sus notas del editor. */
  let casoActual: CasoVista | null = null;
  /** El paso de la preinscripción que se ve (selector «Paso», W12c-ter); null hasta que el panel lo manda. */
  let pasoActual: PasoPreinscripcion | null = null;

  const enviar = (mensaje: MensajeDelEditor) => enviarPorPuente(ventana, padre, mensaje);
  /** El dato del resumen al que ir cuando el tema pinte su caso o su paso. */
  let irPendiente: { clave: string; hasta: number } | null = null;

  function irAlTexto(clave: string): boolean {
    const arriba = fotos.irA(clave) ?? profesores.irA(clave) ?? textos.irA(clave);
    if (arriba !== null) {
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba });
    }
    return arriba !== null;
  }

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

  // W12d: textos en el sitio. El tema recibe el borrador por aquí (con los vacíos marcados para el editor).
  const textos = activarEdicionTextos({ ventana, enviar: (m) => enviar(m), alTema: alBorrador, seleccionar: (id) => seleccionar(id) });
  // W12f-1: fotos sueltas. El panel sube el fichero; la capa solo lo elige y pinta el estado.
  const fotos = activarFotosEditor({ ventana, enviar: (m) => enviar(m), estrecho: () => bloques.estrecho() });
  // W12e: bloques de Inicio. El panel hace cada acción; la capa la pide y pinta el estado.
  const bloques = activarBloquesEditor({ ventana, enviar: (m) => enviar(m), editar: (clave) => textos.editar(clave) });
  // W12f-2b: profesores. El panel confirma, numera y guarda; la capa pide cada acción y pinta la casilla o la constancia.
  // W12f-3: sus sedes, en un diálogo junto al botón o, con el panel estrecho, en la hoja del panel.
  const profesores = activarProfesoresEditor({
    ventana,
    enviar: (m) => enviar(m),
    editar: (clave) => textos.editar(clave),
    estrecho: () => bloques.estrecho(),
  });

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

  /** El candado de una parte del elemento (`envolver`): la envuelve una vez en un `span` y el candado va en él. */
  function envoltorio(candadoDef: Candado, el: HTMLElement): HTMLElement | null {
    const envolver = candadoDef.envolver;
    if (!envolver) {
      return el;
    }
    const ya = el.querySelector<HTMLElement>(`:scope > .${ENVOLTORIO}`);
    if (ya) {
      return ya;
    }
    const nodos: ChildNode[] =
      'texto' in envolver
        ? Array.from(el.childNodes).filter((n) => n.nodeType === Node.TEXT_NODE && (n.textContent ?? '').trim() !== '')
        : Array.from(el.children).filter((h) => h.matches(envolver.hijos));
    if (!nodos.length) {
      return null;
    }
    const span = doc.createElement('span');
    span.className = ENVOLTORIO;
    nodos[0].before(span);
    span.append(...nodos);
    return span;
  }

  function icono(def: Icono): SVGSVGElement {
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

  /** Una sede sin foto (boceto `fotoSedeHtml`): el hueco «Sin foto. Se pone en Sedes.»; si llega la foto, se quita. */
  function huecosFoto(): void {
    for (const def of HUECOS_FOTO_SEDE) {
      doc.querySelectorAll<HTMLElement>(def.caja).forEach((caja) => {
        const hueco = caja.querySelector<HTMLElement>(`:scope > .fs-foto--vacia.${def.clase}`);
        const foto = caja.querySelector(`:scope > .${def.clase}:not(.fs-foto--vacia)`);
        const tras = caja.querySelector(`:scope > ${def.tras}`);
        if (foto || !tras) {
          hueco?.remove();
          return;
        }
        if (hueco) {
          return;
        }
        const nuevo = doc.createElement('div');
        nuevo.className = `ed ${def.clase} fs-foto--vacia`;
        const dentro = doc.createElement('span');
        dentro.className = 'ed ed-vacia-sede';
        const texto = doc.createElement('span');
        texto.textContent = TEXTO_SIN_FOTO_SEDE;
        dentro.append(icono(ICONO_FOTO), texto);
        nuevo.appendChild(dentro);
        tras.after(nuevo);
      });
    }
  }

  /** Las notas del editor del caso que se ve (boceto `ed-nota-parte`), tras su parte; sin caso, ninguna. */
  function notasDelCaso(): void {
    const nota = casoActual ? NOTA_DEL_CASO[casoActual] : undefined;
    doc.querySelectorAll<HTMLElement>('.ed-nota-caso').forEach((el) => {
      if (!nota || el.dataset['caso'] !== casoActual || !el.previousElementSibling?.matches(nota.tras)) {
        el.remove();
      }
    });
    const parte = nota ? doc.querySelector<HTMLElement>(nota.tras) : null;
    if (!nota || !parte || parte.nextElementSibling?.classList.contains('ed-nota-caso')) {
      return;
    }
    const caja = doc.createElement('div');
    caja.className = 'envoltura ed ed-nota-caso';
    caja.dataset['caso'] = casoActual!;
    const p = doc.createElement('p');
    p.className = 'ed ed-nota-parte';
    const texto = doc.createElement('span');
    texto.textContent = nota.texto;
    p.append(icono(ICONO_INFO), texto);
    caja.appendChild(p);
    parte.after(caja);
  }

  /** La nota del editor fija de la página (boceto `ed-nota-parte`), al final de su sitio; una sola. */
  function notaDePagina(pagina: ReturnType<typeof paginaDelDocumento>): void {
    const def = NOTA_DE_PAGINA[pagina];
    const dentro = def ? doc.querySelector<HTMLElement>(def.dentro) : null;
    if (!def || !dentro) {
      return;
    }
    const ya = dentro.querySelector<HTMLElement>(':scope > .ed-nota-pagina');
    if (ya && ya === dentro.lastElementChild) {
      return;
    }
    ya?.remove();
    const p = doc.createElement('p');
    p.className = 'ed ed-nota-parte ed-nota-pagina';
    const texto = doc.createElement('span');
    texto.textContent = def.texto;
    p.append(icono(ICONO_INFO), texto);
    dentro.appendChild(p);
  }

  /** Enlaces ocultos puestos junto al control del tema que contiene su dato (no se anidan controles): su dato. */
  const datoDeEnlace = new WeakMap<HTMLElement, HTMLElement>();

  function enlaceOculto(dato: HTMLElement, donde: DestinoPanel, clave: string): HTMLAnchorElement {
    const a = doc.createElement('a');
    a.href = '#';
    a.className = 'ed-oculto ed-dato__ir';
    a.dataset['accion'] = 'ir';
    a.dataset['destino'] = donde;
    a.dataset['clave'] = clave;
    a.textContent = `${dato.dataset['que']}: se cambia en ${NOMBRE_DESTINO[donde]}`;
    return a;
  }

  /**
   * Un enlace oculto por tipo de dato con destino en el panel, en el primero que no esté dentro de un control. Si todos
   * los de un tipo están dentro de un enlace o un botón del tema (p. ej. «Próxima clase» en el botón de la parada), va
   * justo después de ese control, como hermano.
   */
  function enlacesOcultos(): void {
    doc.querySelectorAll<HTMLElement>('.ed-dato__ir[data-suelto]').forEach((a) => {
      if (!datoDeEnlace.get(a)?.isConnected) {
        a.remove();
      }
    });
    const con = new Set<string>();
    doc.querySelectorAll<HTMLElement>('.ed-dato__ir').forEach((a) => con.add(a.dataset['clave'] ?? ''));
    const datos = Array.from(doc.querySelectorAll<HTMLElement>('.ed-dato'));
    for (const el of datos) {
      const donde = el.dataset['donde'];
      const clave = claveDato(el);
      if (!esDestinoPanel(donde) || con.has(clave) || el.closest(SIN_ENLACE_LINEA_COMARCAL)) {
        continue;
      }
      el.appendChild(enlaceOculto(el, donde, clave));
      con.add(clave);
    }
    for (const el of datos) {
      const donde = el.dataset['donde'];
      const clave = claveDato(el);
      const control = el.closest<HTMLElement>(CONTROL_DEL_TEMA);
      if (!esDestinoPanel(donde) || con.has(clave) || !control || el.closest(OCULTO_DEL_TEMA)) {
        continue;
      }
      const a = enlaceOculto(el, donde, clave);
      a.dataset['suelto'] = '';
      datoDeEnlace.set(a, el);
      control.after(a);
      con.add(clave);
    }
  }

  /** El dato de un enlace oculto: el que lo contiene o, si va junto a un control, el que se guardó al ponerlo. */
  const datoDe = (ir: HTMLElement) => ir.closest<HTMLElement>('.ed-dato') ?? datoDeEnlace.get(ir) ?? null;

  function sinTabulador(): void {
    doc.querySelectorAll<HTMLElement>('.lc').forEach((raiz) =>
      raiz.querySelectorAll<HTMLElement>(CONTROLES_WEB).forEach((control) => {
        if (
          !control.closest('.ed, .ed-campo') &&
          !control.classList.contains('ed-dato__ir') &&
          !control.classList.contains('ed-texto') &&
          control.tabIndex !== -1
        ) {
          control.tabIndex = -1;
        }
      }),
    );
  }

  function decorar(): void {
    const pagina = paginaDelDocumento(doc);
    for (const parte of partesDe(pagina)) {
      doc.querySelectorAll<HTMLElement>(parte.selector).forEach((el) => envolver(parte, el));
    }
    if (pagina === 'sede') {
      huecosFoto();
    }
    for (const def of candadosDe(pagina)) {
      doc.querySelectorAll<HTMLElement>(def.selector).forEach((el) => {
        const dato = envoltorio(def, el);
        if (dato) {
          marcarDato(def, dato);
        }
      });
    }
    notasDelCaso();
    notaDePagina(pagina);
    textos.decorar(pagina);
    fotos.decorar(pagina);
    profesores.decorar(pagina);
    bloques.decorar(pagina);
    enlacesOcultos();
    sinTabulador();
    if (irPendiente) {
      const { clave, hasta } = irPendiente;
      irPendiente = null;
      if (Date.now() <= hasta && !irAlTexto(clave)) {
        irPendiente = { clave, hasta };
      }
    }
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
    const icono = iconoCandado(doc);
    const texto = doc.createElement('span');
    // W12d-bis: la ficha de un hueco puede traer su propio texto (`DefHueco.candado`, aprobado por el propietario):
    // el teléfono que escribe la familia, o «Lleno», que se cambia en otro texto de esta página.
    const verbo =
      dato.dataset['candado'] ?? (donde === 'equipo' ? 'Lo cambia' : donde === 'web' ? 'Lo pone la web' : 'Se cambia en');
    texto.textContent = `${dato.dataset['que']} · ${verbo}`;
    // El texto y lo que le sigue van juntos (en estrecho se parten como un párrafo); el candado, aparte y sin encogerse.
    const linea = doc.createElement('span');
    linea.className = 'ed-candado__t';
    linea.appendChild(texto);
    if (donde === 'equipo') {
      const quien = doc.createElement('span');
      quien.textContent = NOMBRE_EQUIPO;
      linea.append(doc.createElement('wbr'), quien);
    } else if (esDestinoPanel(donde)) {
      const ir = doc.createElement('a');
      ir.href = '#';
      ir.tabIndex = -1;
      ir.dataset['accion'] = 'ir';
      ir.dataset['destino'] = donde;
      ir.textContent = NOMBRE_DESTINO[donde];
      linea.append(doc.createElement('wbr'), ir);
    }
    // Se monta entero antes de ponerlo: un cambio dentro de un nodo que ya no está no lo reconocería el observador.
    candado.append(icono, linea);
    candado.hidden = false;
    // Se mide desde el borde izquierdo: puesto donde estaba el anterior, su ancho saldría recortado por la derecha.
    candado.style.left = '0px';
    const r = dato.getBoundingClientRect();
    const alto = candado.offsetHeight;
    const ancho = candado.offsetWidth;
    // Sobre el dato, o bajo él si arriba no cabe; y sin pisar la etiqueta del campo ni la barra del bloque. Dentro de la
    // parte que se ve en el panel (su zona), si la ha dicho.
    const vista = vistaDe(ventana);
    const sitio = colocarEvitando({
      ancho,
      alto,
      ancla: r,
      separacion: 8,
      izquierda: r.left,
      arriba: r.top - alto - 8 >= vista.arriba + MARGEN_VERTICAL,
      vista,
      evitar: [
        ...cajasVisibles(doc, [doc.body], ':scope > .ed-chip'),
        ...cajasVisibles(doc, [dato.closest('.ed-bloque')], ':scope > .ed-barra .ed-nombre, :scope > .ed-barra .ed-acciones, .ed-error'),
      ],
    });
    candado.style.top = `${sitio.top + ventana.scrollY}px`;
    candado.style.left = `${sitio.left + ventana.scrollX}px`;
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
    // W12f-2b: la casilla del permiso de un profesor se marca (boceto: `.ed-texto, .ed-permiso` no se tocan aquí).
    if (!t || t.closest('.ed-permiso')) {
      return;
    }
    const accion = t.closest<HTMLElement>('[data-accion]');
    if (accion && (accion.closest('.ed') || accion.classList.contains('ed-dato__ir'))) {
      evento.preventDefault();
      if (accion.dataset['accion'] === 'sel') {
        const id = accion.dataset['bloque'] ?? null;
        // W12e (boceto, panel estrecho): el nombre de un bloque movible lo elige y abre su hoja en el panel.
        if (bloques.estrecho()) {
          seleccionar(id);
          if (id) {
            bloques.abrirHoja(id);
          }
        } else {
          seleccionar(seleccionado === id ? null : id);
        }
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

  /**
   * W12c-ter: los formularios de la web no escriben, no marcan, no firman ni envían en el editor (el tema no recibe
   * esos eventos): nada llega al almacenamiento del navegador ni a la API.
   */
  const delFormulario = (evento: Event): boolean => {
    const t = elemento(evento);
    return !!t && !!t.closest('.lc') && !t.closest('.ed, .ed-campo');
  };
  const alEscribir = (evento: Event) => {
    if (delFormulario(evento)) {
      evento.preventDefault();
      evento.stopImmediatePropagation();
    }
  };
  const alFirmar = (evento: Event) => {
    if (delFormulario(evento) && elemento(evento)!.closest('canvas')) {
      evento.preventDefault();
      evento.stopImmediatePropagation();
    }
  };
  const EVENTOS_FORMULARIO = ['beforeinput', 'input', 'change', 'submit'] as const;

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
    // W12d-bis: también la ficha de un hueco dentro de un texto (boceto `mouseover` con `.ed-ficha, .ed-dato`).
    const dato = t?.closest<HTMLElement>('.ed-dato, .ed-ficha');
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
      const dato = datoDe(ir);
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
  for (const tipo of EVENTOS_FORMULARIO) {
    doc.addEventListener(tipo, alEscribir, true);
  }
  doc.addEventListener('pointerdown', alFirmar, true);

  // ------------------------------------------------------------------ observadores y puente
  // Lo que escribe la propia capa (aviso del candado, barras, enlaces ocultos) no vuelve a decorar la página.
  const esNodoDeLaCapa = (nodo: Node) =>
    nodo.nodeType === Node.ELEMENT_NODE &&
    ['ed', 'ed-campo', 'ed-dato__ir', 'ed-envuelto', ENVOLTORIO].some((clase) => (nodo as Element).classList.contains(clase));
  const esDeLaCapa = (cambio: MutationRecord) => {
    if ((cambio.target as Element).closest?.('.ed, .ed-campo')) {
      return true;
    }
    const nodos = [...Array.from(cambio.addedNodes), ...Array.from(cambio.removedNodes)];
    return nodos.length > 0 && nodos.every(esNodoDeLaCapa);
  };
  const observador = new MutationObserver((cambios) => {
    if (cambios.some((cambio) => !esDeLaCapa(cambio))) {
      decorar();
      medir();
    }
  });
  observador.observe(doc.body, { childList: true, subtree: true });

  // Se mide el contenido (la raíz del tema), no el documento: su scrollHeight nunca baja del alto del iframe, así que
  // el marco no encogería (de Móvil a Escritorio). Con `.lc{min-height:0}` en los estilos de la capa.
  const Observador: typeof ResizeObserver | undefined = (ventana as Window & typeof globalThis).ResizeObserver;
  const tamano = typeof Observador === 'function' ? new Observador(() => medir()) : null;
  let raizMedida: HTMLElement | null = null;
  function medir(): void {
    const raiz = doc.querySelector<HTMLElement>('.lc') ?? doc.body;
    if (raiz !== raizMedida) {
      if (raizMedida) {
        tamano?.unobserve(raizMedida);
      }
      raizMedida = raiz;
      tamano?.observe(raiz);
    }
    const alto = Math.ceil(raiz.offsetHeight);
    if (alto !== ultimoAlto) {
      ultimoAlto = alto;
      enviar({ canal: CANAL_EDITOR_WEB, tipo: 'alto', alto });
    }
  }

  // «Ver el caso»: cuando el tema ha pintado el caso, se dice al panel dónde empieza su parte para enseñarla.
  let esperaMostrar: ReturnType<typeof setTimeout> | null = null;
  function mostrarParte(ids: readonly string[]): void {
    if (esperaMostrar !== null) {
      clearTimeout(esperaMostrar);
    }
    esperaMostrar = setTimeout(() => {
      esperaMostrar = null;
      const parte = ids.map((id) => doc.querySelector<HTMLElement>(`.ed-bloque[data-bloque="${id}"]`)).find((el) => !!el);
      if (parte) {
        const arriba = Math.max(0, Math.round(parte.getBoundingClientRect().top + ventana.scrollY));
        enviar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba });
      }
    }, 0);
  }

  const dejarDeEscuchar = escucharPuente(ventana, () => padre, esMensajeDelPanel, (mensaje) => {
    if (mensaje.tipo === 'zona-visible') {
      // La parte del iframe que se ve en el panel: los flotantes abiertos vuelven a quedar dentro.
      fijarZonaVisible(ventana, { top: mensaje.top, alto: mensaje.alto });
      recolocarFlotantes(ventana);
      return;
    }
    if (mensaje.tipo === 'estado-bloques') {
      bloques.estado(mensaje);
      return;
    }
    if (mensaje.tipo === 'respuesta-hoja-menu') {
      // d541: la hoja del panel (panel estrecho) devuelve la opción del menú de la foto o de «Añadir bloque».
      (mensaje.menu === 'foto' ? fotos : bloques).respuestaMenu(mensaje.id, mensaje.op, mensaje.devolverFoco);
      return;
    }
    if (mensaje.tipo === 'foco-bloque') {
      bloques.enfocar(mensaje.foco);
      return;
    }
    if (mensaje.tipo === 'errores') {
      textos.errores(mensaje.errores);
      fotos.errores(mensaje.errores);
      profesores.errores(mensaje.errores);
      return;
    }
    if (mensaje.tipo === 'profesores') {
      profesores.estado(mensaje.lista, mensaje.sedes);
      return;
    }
    if (mensaje.tipo === 'respuesta-hoja-sedes') {
      profesores.respuestaHoja(mensaje.id, mensaje.devolverFoco);
      return;
    }
    if (mensaje.tipo === 'foco-profesor') {
      profesores.enfocar(mensaje.foco);
      return;
    }
    if (mensaje.tipo === 'fotos') {
      fotos.estado(mensaje);
      return;
    }
    if (mensaje.tipo === 'describir') {
      fotos.describir(mensaje.clave);
      return;
    }
    if (mensaje.tipo === 'quitar-dato') {
      textos.quitarDato(mensaje.quitar);
      return;
    }
    if (mensaje.tipo === 'ir-texto') {
      // W12d-bis: el texto de un caso o de un paso sale cuando el tema lo pinta; hasta entonces se espera (poco).
      irPendiente = null;
      if (!irAlTexto(mensaje.clave)) {
        irPendiente = { clave: mensaje.clave, hasta: Date.now() + ESPERA_IR };
      }
      return;
    }
    if (mensaje.tipo === 'paso') {
      pasoActual = mensaje.paso;
      alCaso?.(simuladorDeLaVista(casoActual, pasoActual));
      return;
    }
    if (mensaje.tipo === 'caso') {
      casoActual = mensaje.caso;
      alCaso?.(simuladorDeLaVista(casoActual, pasoActual));
      notasDelCaso();
      if (mensaje.caso) {
        mostrarParte(PARTE_DEL_CASO[mensaje.caso]);
      }
      return;
    }
    if (mensaje.acento) {
      const raiz = doc.documentElement.style;
      raiz.setProperty('--pn-acento', mensaje.acento.acento);
      raiz.setProperty('--pn-acento-borde', mensaje.acento.borde);
      raiz.setProperty('--pn-acento-suave', mensaje.acento.suave);
    }
    textos.borrador(mensaje.bloques);
    fotos.borrador(mensaje.bloques);
    bloques.borrador(mensaje.bloques);
  });

  decorar();
  enviar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
  medir();

  return () => {
    dejarDeEscuchar();
    observador.disconnect();
    tamano?.disconnect();
    limpiarTemporizador();
    if (esperaMostrar !== null) {
      clearTimeout(esperaMostrar);
    }
    doc.removeEventListener('click', alPulsar, true);
    doc.removeEventListener('keydown', alTeclear);
    doc.removeEventListener('mouseover', alPasar);
    doc.removeEventListener('focusin', alEnfocar);
    doc.removeEventListener('focusout', alDesenfocar);
    for (const tipo of EVENTOS_FORMULARIO) {
      doc.removeEventListener(tipo, alEscribir, true);
    }
    doc.removeEventListener('pointerdown', alFirmar, true);
    textos.quitar();
    fotos.quitar();
    profesores.quitar();
    bloques.quitar();
    estilos.remove();
    candado.remove();
    fijarZonaVisible(ventana, null);
  };
}
