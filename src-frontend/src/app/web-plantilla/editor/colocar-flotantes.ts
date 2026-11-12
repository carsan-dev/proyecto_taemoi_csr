/**
 * Colocación de los flotantes de la capa de edición (etiqueta del campo, aviso del candado...): «colocar evitando
 * estas cajas». Todo en coordenadas de la vista (`getBoundingClientRect`); quien llama suma el desplazamiento.
 *
 * Regla: se coloca donde siempre (el lado que pide quien llama); si así su caja corta alguna de las que hay que
 * evitar, pasa al otro lado del ancla; si tampoco cabe dentro de la vista, se desplaza lo justo en vertical hasta
 * quedar libre. Siempre dentro del ancho visible, con el margen lateral de la capa.
 */

export interface Caja {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

/** Aire mínimo entre dos flotantes: más cerca que esto cuenta como pegados. */
export const HOLGURA_FLOTANTES = 4;
export const MARGEN_LATERAL = 8;
export const MARGEN_VERTICAL = 4;

export function cortan(a: Caja, b: Caja, holgura = 0): boolean {
  return a.left < b.right + holgura && a.right > b.left - holgura && a.top < b.bottom + holgura && a.bottom > b.top - holgura;
}

/**
 * Las cajas de lo que se ve en pantalla y casa con `selector` dentro de cada una de las `raices` (acotado: se llama
 * al pasar el ratón por un dato, nunca se recorre el documento entero). Un nombre o una barra de bloque con opacidad 0 solo
 * cuentan si su bloque está a punto de enseñarlos (ratón encima, foco dentro o elegido): la transición de 150 ms
 * aún no los ha pintado, pero van a estar ahí.
 */
export function cajasVisibles(doc: Document, raices: readonly (ParentNode | null)[], selector: string): Caja[] {
  const ventana = doc.defaultView;
  const cajas: Caja[] = [];
  const elementos = new Set<HTMLElement>();
  raices.forEach((raiz) => raiz?.querySelectorAll<HTMLElement>(selector).forEach((el) => elementos.add(el)));
  elementos.forEach((el) => {
    if (el.hidden || !ventana) {
      return;
    }
    const estilo = ventana.getComputedStyle(el);
    if (estilo.display === 'none' || estilo.visibility === 'hidden') {
      return;
    }
    const bloque = el.closest('.ed-bloque');
    const aPunto = !!bloque && bloque.matches(':hover, :focus-within, .es-sel');
    if (parseFloat(estilo.opacity) === 0 && !aPunto) {
      return;
    }
    const r = el.getBoundingClientRect();
    if (r.width > 0 && r.height > 0) {
      cajas.push({ left: r.left, top: r.top, right: r.right, bottom: r.bottom });
    }
  });
  return cajas;
}

export interface PeticionColocar {
  ancho: number;
  alto: number;
  /** La caja a la que acompaña el flotante (campo, ficha...). */
  ancla: Caja;
  /** Distancia al ancla, arriba y abajo. */
  separacion: number;
  /** Borde izquierdo que se querría, antes de meterlo en el ancho visible. */
  izquierda: number;
  /** El lado de siempre: sobre el ancla (`true`) o bajo ella. */
  arriba: boolean;
  /** `arriba`: dónde empieza la parte visible (0 sin zona del panel). */
  vista: { ancho: number; alto: number; arriba?: number };
  evitar: readonly Caja[];
}

export function colocarEvitando(p: PeticionColocar): { top: number; left: number } {
  const left = Math.max(MARGEN_LATERAL, Math.min(p.izquierda, p.vista.ancho - p.ancho - MARGEN_LATERAL));
  const sobre = p.ancla.top - p.alto - p.separacion;
  const bajo = p.ancla.bottom + p.separacion;
  const porDefecto = p.arriba ? sobre : bajo;
  const caja = (top: number): Caja => ({ left, top, right: left + p.ancho, bottom: top + p.alto });
  const libre = (top: number) => !p.evitar.some((o) => cortan(caja(top), o, HOLGURA_FLOTANTES));
  const inicio = p.vista.arriba ?? 0;
  const fin = inicio + p.vista.alto;
  const enVista = (top: number) => top >= inicio + MARGEN_VERTICAL && top + p.alto <= fin - MARGEN_VERTICAL;

  if (libre(porDefecto)) {
    return { top: porDefecto, left };
  }
  const otro = p.arriba ? bajo : sobre;
  if (libre(otro) && enVista(otro)) {
    return { top: otro, left };
  }
  // Ni uno ni otro lado: se desplaza lo justo, sin tapar el ancla, hasta quedar libre de cada caja a evitar.
  const candidatas = [porDefecto, otro];
  p.evitar.forEach((o) => candidatas.push(o.top - p.alto - HOLGURA_FLOTANTES, o.bottom + HOLGURA_FLOTANTES));
  const validas = candidatas.filter((t) => libre(t) && !cortan(caja(t), p.ancla));
  const distancia = (t: number) => Math.abs(t - porDefecto);
  const dentro = validas.filter(enVista).sort((a, b) => distancia(a) - distancia(b));
  if (dentro.length) {
    return { top: dentro[0], left };
  }
  const fuera = (t: number) => Math.max(0, inicio + MARGEN_VERTICAL - t) + Math.max(0, t + p.alto - (fin - MARGEN_VERTICAL));
  const resto = validas.sort((a, b) => fuera(a) - fuera(b) || distancia(a) - distancia(b));
  return { top: resto.length ? resto[0] : porDefecto, left };
}

/**
 * Un menú o diálogo con su sitio de siempre (bajo el ancla, o sobre ella si abajo no cabe) pero sin salirse de la
 * vista por abajo ni por arriba: si es más alto que la vista, se ve al menos su principio. `top` en coordenadas de
 * la vista; `vistaArriba`, dónde empieza la parte visible (la zona del panel; 0 sin ella).
 */
export function encajarEnVista(top: number, alto: number, vistaAlto: number, vistaArriba = 0): number {
  const maximo = vistaArriba + vistaAlto - alto - MARGEN_VERTICAL;
  return Math.max(vistaArriba + MARGEN_VERTICAL, Math.min(top, maximo));
}

// ------------------------------------------------------------------ la parte que se ve (zona del panel)

/**
 * La parte del iframe que se ve en el panel, en coordenadas del documento (mensaje `zona-visible`): el iframe mide todo
 * el documento y quien se desplaza es el panel, así que `innerHeight` y `scrollY` del iframe no dicen qué se ve.
 */
export interface ZonaVisible {
  top: number;
  alto: number;
}

/** La vista donde caben los flotantes, en coordenadas de la vista del iframe. `zona`: viene del panel. */
export interface Vista {
  ancho: number;
  alto: number;
  arriba: number;
  zona: boolean;
}

const zonas = new WeakMap<Window, ZonaVisible>();

/** La última zona que dijo el panel (null al cerrar la capa: vuelve a la vista del iframe). */
export function fijarZonaVisible(ventana: Window, zona: ZonaVisible | null): void {
  if (zona) {
    zonas.set(ventana, { top: zona.top, alto: zona.alto });
  } else {
    zonas.delete(ventana);
    abiertos.delete(ventana);
  }
}

/** La vista del iframe recortada a la zona del panel; sin zona (o si no se cruzan), la vista del iframe entera. */
export function vistaDe(ventana: Window): Vista {
  const ancho = ventana.document.documentElement.clientWidth;
  const entera: Vista = { ancho, alto: ventana.innerHeight, arriba: 0, zona: false };
  const zona = zonas.get(ventana);
  if (!zona) {
    return entera;
  }
  const arriba = Math.max(0, zona.top - ventana.scrollY);
  const abajo = Math.min(ventana.innerHeight, zona.top + zona.alto - ventana.scrollY);
  return abajo > arriba ? { ancho, alto: abajo - arriba, arriba, zona: true } : entera;
}

interface Abierto {
  ancla: HTMLElement;
  /** Su alto sin limitar, medido al abrirlo (recolocarlo no lo vuelve a medir: perdería el desplazamiento interno). */
  alto: number;
}

/** Por ventana, cada menú o diálogo colocado junto a su botón (para recolocarlo si cambia la zona). */
const abiertos = new WeakMap<Window, Map<HTMLElement, Abierto>>();

/**
 * Boceto `abrirFlot`: un menú o diálogo bajo su botón (o sobre él si abajo no cabe) y siempre dentro de la parte que se
 * ve. Si es más alto que la zona del panel, su alto se limita a ella y se desplaza por dentro.
 */
export function colocarJuntoAlAncla(flot: HTMLElement, ancla: HTMLElement, ventana: Window): void {
  flot.style.maxHeight = '';
  flot.style.overflowY = '';
  const alto = flot.offsetHeight;
  let mapa = abiertos.get(ventana);
  if (!mapa) {
    mapa = new Map();
    abiertos.set(ventana, mapa);
  }
  mapa.set(flot, { ancla, alto });
  situar(flot, ancla, alto, ventana);
}

/** Tras un cambio de zona: los menús y diálogos que siguen abiertos vuelven a quedar dentro de ella. */
export function recolocarFlotantes(ventana: Window): void {
  const mapa = abiertos.get(ventana);
  mapa?.forEach((a, flot) => {
    if (flot.hidden || !flot.isConnected || !a.ancla.isConnected) {
      mapa.delete(flot);
      return;
    }
    situar(flot, a.ancla, a.alto, ventana);
  });
}

function situar(flot: HTMLElement, ancla: HTMLElement, altoNatural: number, ventana: Window): void {
  const vista = vistaDe(ventana);
  const cabe = Math.floor(vista.alto - 2 * MARGEN_VERTICAL);
  const limitar = vista.zona && altoNatural > cabe;
  let tope = cabe;
  if (limitar) {
    // `max-height` sin `border-box` no cuenta el relleno ni el borde.
    const estilo = ventana.getComputedStyle(flot);
    if (estilo.boxSizing !== 'border-box') {
      tope -= ['paddingTop', 'paddingBottom', 'borderTopWidth', 'borderBottomWidth'].reduce(
        (suma, p) => suma + (parseFloat(estilo[p as 'paddingTop']) || 0),
        0,
      );
    }
  }
  flot.style.maxHeight = limitar ? `${Math.max(0, Math.floor(tope))}px` : '';
  flot.style.overflowY = limitar ? 'auto' : '';
  const r = ancla.getBoundingClientRect();
  const ancho = flot.offsetWidth;
  const alto = flot.offsetHeight;
  const { scrollX, scrollY } = ventana;
  const izquierda = Math.min(Math.max(8 + scrollX, r.left + scrollX + r.width / 2 - ancho / 2), scrollX + vista.ancho - ancho - 8);
  const fin = vista.arriba + vista.alto;
  let arriba = r.bottom + 8;
  if (arriba + alto > fin - 8 && r.top - alto - 8 > vista.arriba) {
    arriba = r.top - alto - 8;
  }
  // Ni abajo ni arriba cabe entero: se desplaza lo justo para que se vea dentro de la vista.
  arriba = encajarEnVista(arriba, alto, vista.alto, vista.arriba);
  flot.style.left = `${Math.max(8, izquierda)}px`;
  flot.style.top = `${arriba + scrollY}px`;
}
