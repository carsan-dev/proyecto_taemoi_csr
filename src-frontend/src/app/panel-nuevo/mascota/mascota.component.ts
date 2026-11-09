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
import { firstValueFrom } from 'rxjs';

import { menosMovimiento } from '../capas/capa-modal';
import { MarcaEscuelaPanelService } from '../marca-escuela-panel.service';
import { identificarSvg } from './identificar-svg';
import { CARGADORES_MASCOTA, CARGADORES_MASCOTA_MAKI, CargadoresMascota, ClaveSvgMascota, PoseMascota, esPoseMascota } from './poses';

export type TamanoMascota = 'sm' | 'md' | 'lg';
export type AnimacionMascota = 'aparecer' | 'saludar' | 'celebrar' | 'dudar' | 'descansar' | 'cumpleanos';

/** Los chunks de las poses; las pruebas los sustituyen por promesas ya resueltas. */
export const MASCOTA_CARGADORES = new InjectionToken<CargadoresMascota>('MASCOTA_CARGADORES', {
  providedIn: 'root',
  factory: () => CARGADORES_MASCOTA,
});

/** Los chunks de las poses de Maki; las pruebas los sustituyen igual. */
export const MASCOTA_CARGADORES_MAKI = new InjectionToken<CargadoresMascota>('MASCOTA_CARGADORES_MAKI', {
  providedIn: 'root',
  factory: () => CARGADORES_MASCOTA_MAKI,
});

/** Etiqueta cuando la mascota no es decorativa y quien la usa no da ninguna. */
const ETIQUETA_TAMI = 'Gota de esfuerzo, la mascota';
const ETIQUETA_MAKI = 'Maki, la mascota';

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

  /** La mascota de la escuela; null mientras no se sabe cuál es (no se pinta nada, ni Tami por si acaso) o si la marca falló. */
  readonly mascota = signal<'TAMI' | 'MAKI' | null>(null);

  get etiquetaPorDefecto(): string {
    return this.mascota() === 'MAKI' ? ETIQUETA_MAKI : ETIQUETA_TAMI;
  }

  readonly svg = signal<SafeHtml | null>(null);

  private readonly cargadoresTami = inject(MASCOTA_CARGADORES);
  private readonly cargadoresMaki = inject(MASCOTA_CARGADORES_MAKI);
  private readonly marcaServicio = inject(MarcaEscuelaPanelService);
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

  /** La mascota de la marca (una sola petición compartida por el servicio); un valor raro o ausente, Tami; un error, null. */
  private async resolverMascota(): Promise<'TAMI' | 'MAKI' | null> {
    try {
      const marca = await firstValueFrom(this.marcaServicio.marca());
      return marca?.mascota === 'MAKI' ? 'MAKI' : 'TAMI';
    } catch {
      return null;
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

  private async cargar(): Promise<void> {
    const version = ++this.version;
    const claves = this.claves();
    try {
      const mascota = this.mascota() ?? (await this.resolverMascota());
      if (version !== this.version) {
        return;
      }
      if (!mascota) {
        // Sin marca no se sabe qué mascota toca: hueco vacío y se reintenta en la siguiente carga.
        this.svg.set(null);
        return;
      }
      this.mascota.set(mascota);
      const cargadores = mascota === 'MAKI' ? this.cargadoresMaki : this.cargadoresTami;
      const svgs = await Promise.all(claves.map((clave) => cargadores[clave]()));
      if (version === this.version) {
        // Son los SVG propios de `mascota-app/`, empaquetados con la aplicación: el saneador de Angular quitaría los
        // elementos SVG y los `style` que llevan la variable del acento.
        this.svg.set(svgs.length ? this.saneador.bypassSecurityTrustHtml(identificarSvg(svgs[0], this.prefijo)) : null);
      }
    } catch {
      // Sin el chunk (sin conexión) o sin pose: se queda el hueco; la mascota nunca lleva el mensaje.
      if (version === this.version) {
        this.svg.set(null);
      }
    }
  }
}
