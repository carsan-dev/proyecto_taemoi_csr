import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Tamaños del cinturón (boceto `boceto-rallas-cinturon.html`, decisión 511): medidas en px del viewBox. */
export type TamanoCinturon = 'fila' | 'hoja';

export const MEDIDAS_CINTURON: Record<TamanoCinturon, { w: number; h: number; ancho: number; paso: number; margen: number }> = {
  fila: { w: 56, h: 14, ancho: 3, paso: 6, margen: 3 },
  hoja: { w: 150, h: 30, ancho: 8, paso: 14, margen: 6 },
};

interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
  fill: string;
}

/** Luminancia relativa de un `#RRGGBB` o `#RGB`; `null` si no es un hex válido. */
function luminancia(hex: string): number | null {
  let h = (hex ?? '').trim().replace('#', '');
  if (h.length === 3) {
    h = h.replace(/./g, (c) => c + c);
  }
  if (!/^[0-9a-fA-F]{6}$/.test(h)) {
    return null;
  }
  const [r, g, b] = [0, 2, 4]
    .map((i) => parseInt(h.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Halo blanco solo donde el negro no llega a 3:1 sobre el color (el azul). */
export function necesitaHalo(hex: string): boolean {
  const l = luminancia(hex);
  return l !== null && (l + 0.05) / 0.05 < 3;
}

/**
 * Cinturón común del panel nuevo (decisión 511 y 513): SVG de 56×14 (`fila`) o 150×30 (`hoja`). Un color, o dos franjas
 * horizontales (color 2 arriba, color 1 abajo). Las rallas (`medias`, de 0 a 10; `null` = sin rallas) son siempre negras,
 * desde el extremo derecho, y la media ralla es la mitad de abajo. PUM y DAN no llevan rallas: quien lo usa pasa `null`.
 * El SVG es decorativo (`aria-hidden`): el texto accesible lo pone quien lo usa.
 */
@Component({
  selector: 'pn-cinturon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg
    [attr.viewBox]="'0 0 ' + medida().w + ' ' + medida().h"
    [attr.width]="medida().w"
    [attr.height]="medida().h"
    [attr.data-tamano]="tamano()"
    [attr.shape-rendering]="vacio() ? null : 'crispEdges'"
    aria-hidden="true"
    focusable="false"
  >
    @if (vacio()) {
    <rect x="1" y="1" [attr.width]="medida().w - 2" [attr.height]="medida().h - 2" fill="#fff" />
    } @else {
    @for (r of fondo(); track $index) {
    <rect data-franja [attr.x]="r.x" [attr.y]="r.y" [attr.width]="r.w" [attr.height]="r.h" [attr.fill]="r.fill" />
    }
    @for (r of rallas(); track $index) {
    <rect [attr.data-ralla]="r.fill === '#000' ? '' : null" [attr.data-halo]="r.fill === '#fff' ? '' : null"
      [attr.x]="r.x" [attr.y]="r.y" [attr.width]="r.w" [attr.height]="r.h" [attr.fill]="r.fill" />
    }
    }
    <rect x=".5" y=".5" [attr.width]="medida().w - 1" [attr.height]="medida().h - 1" rx="1.5" fill="none" stroke="#868D97"
      vector-effect="non-scaling-stroke" shape-rendering="geometricPrecision" [attr.stroke-dasharray]="vacio() ? '3 2' : null" />
  </svg>`,
  styles: `
    :host {
      display: inline-block;
      flex: none;
      line-height: 0;
    }

    svg {
      display: block;
      flex: none;
      overflow: visible;
    }
  `,
})
export class CinturonComponent {
  /** `color_1` del grado (mitad de abajo). */
  readonly color1 = input<string | null | undefined>(null);
  /** `color_2` del grado (mitad de arriba); `null` si es de un color. */
  readonly color2 = input<string | null | undefined>(null);
  /** Medias rallas hacia el examen, de 0 a 10; `null` = sin rallas. */
  readonly medias = input<number | null | undefined>(null);
  readonly tamano = input<TamanoCinturon>('fila');
  /** Sin grado: recuadro blanco con borde discontinuo. */
  readonly sinGrado = input(false);

  protected readonly medida = computed(() => MEDIDAS_CINTURON[this.tamano()] ?? MEDIDAS_CINTURON.fila);
  protected readonly vacio = computed(() => this.sinGrado() || !this.color1());

  protected readonly fondo = computed<Rect[]>(() => {
    const { w, h } = this.medida();
    const alto = h - 2;
    const mitad = alto / 2;
    const c1 = this.color1() as string;
    const c2 = this.color2();
    return c2
      ? [
          { x: 1, y: 1, w: w - 2, h: mitad, fill: c2 },
          { x: 1, y: 1 + mitad, w: w - 2, h: mitad, fill: c1 },
        ]
      : [{ x: 1, y: 1, w: w - 2, h: alto, fill: c1 }];
  });

  protected readonly rallas = computed<Rect[]>(() => {
    const bruto = this.medias();
    if (this.vacio() || bruto === null || bruto === undefined) {
      return [];
    }
    const n = Math.min(10, Math.max(0, Math.trunc(bruto)));
    const { w, h, ancho, paso, margen } = this.medida();
    const alto = h - 2;
    const mitad = alto / 2;
    const c1 = this.color1() as string;
    const c2 = this.color2() ?? c1;
    const x0 = w - 1 - margen - ancho;
    const halo = Math.max(0.6, ancho / 6);
    const salida: Rect[] = [];
    const pieza = (x: number, y: number, alturaPieza: number, color: string): void => {
      if (necesitaHalo(color)) {
        salida.push({ x: x - halo, y, w: ancho + 2 * halo, h: alturaPieza, fill: '#fff' });
      }
    };
    const completas = n >> 1;
    for (let k = 0; k <= completas; k++) {
      const media = k === completas;
      if (media && !(n & 1)) {
        break;
      }
      const x = x0 - paso * k;
      if (media) {
        pieza(x, 1 + mitad, mitad, c1);
        salida.push({ x, y: 1 + mitad, w: ancho, h: mitad, fill: '#000' });
      } else {
        pieza(x, 1, mitad, c2);
        pieza(x, 1 + mitad, mitad, c1);
        salida.push({ x, y: 1, w: ancho, h: alto, fill: '#000' });
      }
    }
    return salida;
  });
}
