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
  vista: { ancho: number; alto: number };
  evitar: readonly Caja[];
}

export function colocarEvitando(p: PeticionColocar): { top: number; left: number } {
  const left = Math.max(MARGEN_LATERAL, Math.min(p.izquierda, p.vista.ancho - p.ancho - MARGEN_LATERAL));
  const sobre = p.ancla.top - p.alto - p.separacion;
  const bajo = p.ancla.bottom + p.separacion;
  const porDefecto = p.arriba ? sobre : bajo;
  const caja = (top: number): Caja => ({ left, top, right: left + p.ancho, bottom: top + p.alto });
  const libre = (top: number) => !p.evitar.some((o) => cortan(caja(top), o, HOLGURA_FLOTANTES));
  const enVista = (top: number) => top >= MARGEN_VERTICAL && top + p.alto <= p.vista.alto - MARGEN_VERTICAL;

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
  const fuera = (t: number) => Math.max(0, MARGEN_VERTICAL - t) + Math.max(0, t + p.alto - (p.vista.alto - MARGEN_VERTICAL));
  const resto = validas.sort((a, b) => fuera(a) - fuera(b) || distancia(a) - distancia(b));
  return { top: resto.length ? resto[0] : porDefecto, left };
}

/**
 * Un menú o diálogo con su sitio de siempre (bajo el ancla, o sobre ella si abajo no cabe) pero sin salirse de la
 * vista por abajo ni por arriba: si es más alto que la vista, se ve al menos su principio. `top` en coordenadas de
 * la vista.
 */
export function encajarEnVista(top: number, alto: number, vistaAlto: number): number {
  const maximo = vistaAlto - alto - MARGEN_VERTICAL;
  return Math.max(MARGEN_VERTICAL, Math.min(top, maximo));
}
