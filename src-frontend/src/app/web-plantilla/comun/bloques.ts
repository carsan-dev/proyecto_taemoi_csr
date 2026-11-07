import { BloqueWeb } from '../datos/modelos';

/**
 * Lectura de los bloques editables de la web (`web_bloque`, fase4-web-plan.md §4.1 y §9 n.º 4 y 11), sin estilo.
 *
 * - Clave que no está: el texto por defecto del tema (el del boceto).
 * - Clave que está con el texto vacío (y sin imagen): la escuela lo ha vaciado y no se pinta (§9 n.º 11).
 * - Las imágenes no tienen valor por defecto: las fotos de los bocetos son de ejemplo.
 */
export interface ImagenBloque {
  url: string;
  /** Texto alternativo (el texto del bloque); vacío si no lo tiene. */
  alt: string;
}

export interface ProfesorBloque {
  n: number;
  nombre: string;
  cargo: string | null;
  foto: ImagenBloque | null;
}

export interface FotoGaleria extends ImagenBloque {
  n: number;
}

export class BloquesWeb {
  private readonly porClave = new Map<string, BloqueWeb>();

  constructor(private readonly bloques: readonly BloqueWeb[]) {
    for (const bloque of bloques) {
      this.porClave.set(bloque.clave, bloque);
    }
  }

  tiene(clave: string): boolean {
    return this.porClave.has(clave);
  }

  /** El texto del bloque; el de por defecto si la clave no está; vacío si está vacío (no se pinta). */
  texto(clave: string, porDefecto: string): string {
    const bloque = this.porClave.get(clave);
    if (!bloque) {
      return porDefecto;
    }
    return bloque.texto?.trim() ?? '';
  }

  /** El texto del bloque sin valor por defecto; null si no está o está vacío. */
  textoPropio(clave: string): string | null {
    const texto = this.porClave.get(clave)?.texto?.trim();
    return texto ? texto : null;
  }

  /** La imagen del bloque (el texto, como alternativo); null sin imagen. */
  imagen(clave: string, alt?: string): ImagenBloque | null {
    const bloque = this.porClave.get(clave);
    if (!bloque?.imagenUrl) {
      return null;
    }
    return { url: bloque.imagenUrl, alt: (alt ?? bloque.texto ?? '').trim() };
  }

  /** El menor `orden` de las claves con ese prefijo (`presentacion` casa con `presentacion.texto`); null si no hay. */
  orden(prefijo: string): number | null {
    const ordenes = this.bloques.filter((b) => casa(b.clave, prefijo)).map((b) => b.orden);
    return ordenes.length ? Math.min(...ordenes) : null;
  }

  /** Los números `<n>` de las claves `<prefijo>.<n>` o `<prefijo>.<n>.<campo>`, de menor a mayor. */
  numeros(prefijo: string): number[] {
    const patron = new RegExp(`^${prefijo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\.(\\d+)(\\.|$)`);
    const numeros = new Set<number>();
    for (const bloque of this.bloques) {
      const casaClave = patron.exec(bloque.clave);
      if (casaClave) {
        numeros.add(Number(casaClave[1]));
      }
    }
    return [...numeros].sort((a, b) => a - b);
  }

  /** Profesores (`profesor.<n>.nombre|cargo|foto`, §10 n.º 11): solo los que tienen nombre. */
  profesores(): ProfesorBloque[] {
    return this.numeros('profesor')
      .map((n) => {
        const nombre = this.textoPropio(`profesor.${n}.nombre`);
        return nombre
          ? {
              n,
              nombre,
              cargo: this.textoPropio(`profesor.${n}.cargo`),
              foto: this.imagen(`profesor.${n}.foto`, nombre),
            }
          : null;
      })
      .filter((p): p is ProfesorBloque => p !== null);
  }

  /** Fotos de la galería (`galeria.<n>`): solo las que tienen imagen, por `orden` y luego por número. */
  galeria(): FotoGaleria[] {
    return this.numeros('galeria')
      .map((n) => {
        const imagen = this.imagen(`galeria.${n}`);
        return imagen ? { ...imagen, n, orden: this.porClave.get(`galeria.${n}`)!.orden } : null;
      })
      .filter((f): f is FotoGaleria & { orden: number } => f !== null)
      .sort((a, b) => a.orden - b.orden || a.n - b.n)
      .map(({ orden: _orden, ...foto }) => foto);
  }
}

function casa(clave: string, prefijo: string): boolean {
  return clave === prefijo || clave.startsWith(`${prefijo}.`);
}

/**
 * Ordena las secciones de una página (§9 n.º 11; sus bloques, por `prefijo` o, si no, por `clave`): las fijas se
 * quedan en su hueco; las movibles ocupan los huecos de
 * las movibles, por su `orden` (la que no tiene bloques toma como orden su posición en el tema; al empatar, la del
 * tema).
 */
export function ordenarSecciones<T extends { clave: string; movible: boolean; prefijo?: string }>(
  secciones: readonly T[],
  bloques: BloquesWeb,
): T[] {
  const movibles = secciones
    .map((s, indice) => ({ s, indice, orden: bloques.orden(s.prefijo ?? s.clave) }))
    .filter((x) => x.s.movible)
    .sort((a, b) => (a.orden ?? a.indice) - (b.orden ?? b.indice) || a.indice - b.indice)
    .map((x) => x.s);
  let siguiente = 0;
  return secciones.map((s) => (s.movible ? movibles[siguiente++] : s));
}
