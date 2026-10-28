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

let siguienteInstancia = 0;

/**
 * La gota de esfuerzo: 14 SVG animados inline con IDs únicos por instancia y acento --school-accent.
 * Cada pose llega en su propio chunk; el hueco conserva el tamaño mientras carga.
 * sm usa el dibujo compacto estático de 32 px; md mide 96 px y lg, 144 px.
 * El SVG ejecuta su gesto y como máximo un reposo; con menos movimiento queda quieto.
 * animacion se conserva por compatibilidad: solo aparecer añade una entrada de 240 ms.
 * Decorativa por defecto; si no, role="img" y etiqueta accesible.
 */
@Component({
  selector: 'pn-mascota',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'pn-mascota',
    '[class.pn-mascota--quieta]': 'quieta',
    '[class.pn-mascota--sm]': "tam === 'sm'",
    '[class.pn-mascota--md]': "tam === 'md'",
    '[class.pn-mascota--lg]': "tam === 'lg'",
    '[attr.aria-hidden]': 'decorativa ? "true" : null',
    '[attr.role]': 'decorativa ? null : "img"',
    '[attr.aria-label]': 'decorativa ? null : (etiqueta || etiquetaPorDefecto)',
  },
  template: `@if (svg(); as dibujo) {<span class="pn-mascota__lienzo" [class]="claseLienzo" [innerHTML]="dibujo"></span>}`,
  styleUrls: ['./mascota.component.scss'],
})
export class MascotaComponent implements OnInit, OnChanges {
  /** Nombre de la pose (`mascota-app/animaciones/gota-<pose>-animada.svg`). Una que no exista deja el hueco vacío. */
  @Input() pose: PoseMascota | (string & {}) = 'saludar';
  @Input() tamano: TamanoMascota = 'md';
  @Input() animacion: AnimacionMascota | null = null;
  @Input() decorativa = true;
  @Input() etiqueta = '';

  readonly etiquetaPorDefecto = ETIQUETA_POR_DEFECTO;
  readonly svg = signal<SafeHtml | null>(null);

  private readonly cargadores = inject(MASCOTA_CARGADORES);
  private readonly saneador = inject(DomSanitizer);
  readonly quieta = menosMovimiento();
  private readonly prefijo = `pn-mascota-${++siguienteInstancia}-`;
  private version = 0;
  private iniciado = false;

  /** El tamaño pedido; sin valor (o `undefined` desde una plantilla), `md`. */
  get tam(): TamanoMascota {
    return this.tamano ?? 'md';
  }

  /** Clase de la animación, o nada con `prefers-reduced-motion`. */
  get claseLienzo(): string {
    return !this.quieta && this.animacion === 'aparecer' ? 'pn-mascota__lienzo--aparecer' : '';
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

  /** El compacto con sm; en los demás tamaños, la pose animada. */
  private claves(): ClaveSvgMascota[] {
    if (this.tam === 'sm') {
      return ['compacta'];
    }
    if (!esPoseMascota(this.pose)) {
      return [];
    }
    return [this.pose];
  }

  /** Los fragmentos y el título accesible deben apuntar solo a esta instancia. */
  private identificar(svg: string): string {
    return svg
      .replace(/\bid="([^"]+)"/g, (_, id: string) => `id="${this.prefijo}${id}"`)
      .replace(/url\(#([^)]+)\)/g, (_, id: string) => `url(#${this.prefijo}${id})`)
      .replace(/\b(href|xlink:href)="#([^"]+)"/g, (_, atributo: string, id: string) => `${atributo}="#${this.prefijo}${id}"`)
      .replace(/\b(aria-labelledby|aria-describedby)="([^"]+)"/g, (_, atributo: string, ids: string) =>
        `${atributo}="${ids.split(/\s+/).map((id) => this.prefijo + id).join(' ')}"`);
  }

  private async cargar(): Promise<void> {
    const version = ++this.version;
    const claves = this.claves();
    try {
      const svgs = await Promise.all(claves.map((clave) => this.cargadores[clave]()));
      if (version === this.version) {
        // Son los SVG propios de `mascota-app/`, empaquetados con la aplicación: el saneador de Angular quitaría los
        // elementos SVG y los `style` que llevan la variable del acento.
        this.svg.set(svgs.length ? this.saneador.bypassSecurityTrustHtml(this.identificar(svgs[0])) : null);
      }
    } catch {
      // Sin el chunk (sin conexión) o sin pose: se queda el hueco; la mascota nunca lleva el mensaje.
      if (version === this.version) {
        this.svg.set(null);
      }
    }
  }
}
