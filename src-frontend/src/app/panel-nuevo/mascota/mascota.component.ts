import {
  ChangeDetectionStrategy,
  Component,
  InjectionToken,
  Input,
  OnChanges,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

import { menosMovimiento } from '../capas/capa-modal';
import { CARGADORES_MASCOTA, CargadoresMascota, ClaveSvgMascota, PoseMascota, esPoseMascota } from './poses';

export type TamanoMascota = 'sm' | 'md' | 'lg';
export type AnimacionMascota = 'aparecer' | 'saludar' | 'celebrar' | 'dudar' | 'descansar' | 'cumpleanos';

/** Los chunks de las poses; las pruebas los sustituyen por promesas ya resueltas. */
export const MASCOTA_CARGADORES = new InjectionToken<CargadoresMascota>('MASCOTA_CARGADORES', {
  providedIn: 'root',
  factory: () => CARGADORES_MASCOTA,
});

/** Etiqueta cuando la mascota no es decorativa y quien la usa no da ninguna. */
const ETIQUETA_POR_DEFECTO = 'Gota de esfuerzo, la mascota';

/** Piezas de confeti de «celebrar»: a dónde va cada una (en fracciones del lado) y cuánto gira. */
const CONFETI = [
  { dx: -0.46, dy: -0.4, giro: -200, retraso: 0 },
  { dx: -0.22, dy: -0.58, giro: 160, retraso: 40 },
  { dx: 0.04, dy: -0.64, giro: -120, retraso: 0 },
  { dx: 0.28, dy: -0.56, giro: 220, retraso: 60 },
  { dx: 0.5, dy: -0.38, giro: -180, retraso: 20 },
  { dx: 0.36, dy: -0.2, giro: 140, retraso: 80 },
];

/**
 * La gota de esfuerzo, mascota de la plataforma (pieza 8b-M; `mascota-app/LEEME.txt` fija sus usos). SVG inline, para que
 * la cinta (y el gorro y el confeti de cumpleaños) tomen `--school-accent`, que aquí es el acento de la escuela
 * (`--pn-acento`, 8b-C). Cada pose es un chunk aparte (`poses.ts`): nada entra en el bundle inicial. Mientras llega, el
 * hueco ya tiene el tamaño final.
 *
 * - `tamano`: `sm` 32 px (con el dibujo compacto, que no es la pose reducida), `md` 96 px, `lg` 144 px.
 * - `animacion`: solo CSS y solo `transform` y `opacity`; con `prefers-reduced-motion` la pose queda quieta (y la de
 *   cumpleaños, directamente en su fotograma final).
 * - `decorativa` (por defecto): `aria-hidden`. La mascota nunca es el único portador de un mensaje; si lo fuera,
 *   `decorativa=false` la pone como `role="img"` con `etiqueta`.
 */
@Component({
  selector: 'pn-mascota',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'pn-mascota',
    '[class.pn-mascota--sm]': "tam === 'sm'",
    '[class.pn-mascota--md]': "tam === 'md'",
    '[class.pn-mascota--lg]': "tam === 'lg'",
    '[attr.aria-hidden]': 'decorativa ? "true" : null',
    '[attr.role]': 'decorativa ? null : "img"',
    '[attr.aria-label]': 'decorativa ? null : (etiqueta || etiquetaPorDefecto)',
  },
  template: `@if (capas().length) {<span class="pn-mascota__lienzo" [class]="claseLienzo">@for (capa of capas(); track $index) {<span class="pn-mascota__capa" [class.pn-mascota__capa--final]="capas().length > 1 && $last" [innerHTML]="capa"></span>}</span>@if (confeti) {@for (c of piezasConfeti; track $index) {<span class="pn-mascota__confeti" aria-hidden="true" [style.--dx]="c.dx" [style.--dy]="c.dy" [style.--giro]="c.giro + 'deg'" [style.animation-delay]="c.retraso + 'ms'"></span>}}}`,
  styleUrls: ['./mascota.component.scss'],
})
export class MascotaComponent implements OnInit, OnChanges {
  /** Nombre de la pose (`mascota-app/svg/gota-<pose>.svg`). Una que no exista deja el hueco vacío. */
  @Input() pose: PoseMascota | (string & {}) = 'saludar';
  @Input() tamano: TamanoMascota = 'md';
  @Input() animacion: AnimacionMascota | null = null;
  @Input() decorativa = true;
  @Input() etiqueta = '';

  readonly etiquetaPorDefecto = ETIQUETA_POR_DEFECTO;
  readonly piezasConfeti = CONFETI;
  /** Una capa, o dos (inicio y final) en la animación de cumpleaños. */
  readonly capas = signal<SafeHtml[]>([]);

  private readonly cargadores = inject(MASCOTA_CARGADORES);
  private readonly saneador = inject(DomSanitizer);
  private readonly quieta = menosMovimiento();
  private version = 0;
  private iniciado = false;

  /** El tamaño pedido; sin valor (o `undefined` desde una plantilla), `md`. */
  get tam(): TamanoMascota {
    return this.tamano ?? 'md';
  }

  /** Clase de la animación, o nada con `prefers-reduced-motion`. */
  get claseLienzo(): string {
    return this.animacionActiva ? `pn-mascota__lienzo--${this.animacionActiva}` : '';
  }

  get confeti(): boolean {
    return this.animacionActiva === 'celebrar';
  }

  private get animacionActiva(): AnimacionMascota | null {
    return this.quieta ? null : this.animacion;
  }

  ngOnInit(): void {
    this.iniciado = true;
    this.cargar();
  }

  ngOnChanges(): void {
    if (this.iniciado) {
      this.cargar();
    }
  }

  /** Qué SVG hacen falta: el compacto con `sm`; cumpleaños animado, los dos fotogramas; si no, la pose. */
  private claves(): ClaveSvgMascota[] {
    if (this.tam === 'sm') {
      return ['compacta'];
    }
    if (!esPoseMascota(this.pose)) {
      return [];
    }
    if (this.pose === 'cumpleanos' && this.animacionActiva === 'cumpleanos') {
      return ['cumpleanos-inicio', 'cumpleanos'];
    }
    return [this.pose];
  }

  private async cargar(): Promise<void> {
    const version = ++this.version;
    const claves = this.claves();
    try {
      const svgs = await Promise.all(claves.map((clave) => this.cargadores[clave]()));
      if (version === this.version) {
        // Son los SVG propios de `mascota-app/`, empaquetados con la aplicación: el saneador de Angular quitaría los
        // elementos SVG y los `style` que llevan la variable del acento.
        this.capas.set(svgs.map((svg) => this.saneador.bypassSecurityTrustHtml(svg)));
      }
    } catch {
      // Sin el chunk (sin conexión) o sin pose: se queda el hueco; la mascota nunca lleva el mensaje.
      if (version === this.version) {
        this.capas.set([]);
      }
    }
  }
}
