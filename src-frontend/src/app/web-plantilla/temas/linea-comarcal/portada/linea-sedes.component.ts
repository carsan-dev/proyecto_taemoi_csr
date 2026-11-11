import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { EditorWebService } from '../../../comun/editor-web';
import { VistaPreviaPipe } from '../../../comun/vista-previa';

import { CLAVE_SEDE_RECORDADA, VistaHorarios, VistaSede } from '../contenido';

export { CLAVE_SEDE_RECORDADA } from '../contenido';
const ESCRITORIO = '(min-width: 900px)';

/**
 * La línea de la portada (boceto `boceto-linea-comarcal.html`, «La red»): una parada por sede con su próxima clase y,
 * al pulsarla, su panel de salidas (grupos, días, plazas y preinscripción). Una sola sede: parada fija y panel abierto.
 * Varias: abre la recordada; si no hay, en escritorio la primera y en móvil ninguna.
 *
 * El trazo se calcula con la posición real de las paradas (como el boceto): vertical en móvil; en escritorio,
 * horizontal con desnivel a 45° donde las paradas montan sobre el pie de la foto. Se dibuja una vez al entrar en
 * pantalla, solo sin «reducir movimiento».
 */
@Component({
  selector: 'app-lc-linea-sedes',
  standalone: true,
  imports: [VistaPreviaPipe, NgTemplateOutlet, RouterLink],
  templateUrl: './linea-sedes.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LineaSedesComponent {
  readonly horarios = input.required<VistaHorarios>();
  /** La foto de la portada: en escritorio, las paradas que caen bajo ella montan sobre su pie. */
  readonly foto = input<HTMLElement | null>(null);

  private readonly documento = inject(DOCUMENT);
  private readonly editor = inject(EditorWebService);
  private readonly red = viewChild.required<ElementRef<HTMLElement>>('red');
  private readonly lista = viewChild.required<ElementRef<HTMLElement>>('lista');
  private readonly svg = viewChild.required<ElementRef<SVGSVGElement>>('svg');

  protected readonly una = computed(() => this.horarios().sedes.length === 1);
  /** La sede que ha abierto o cerrado la persona; `undefined` hasta que pulsa (sin `linkedSignal`: suma al inicial). */
  private readonly elegida = signal<string | null | undefined>(undefined);
  /** La sede abierta: la elegida o, hasta entonces, la de `inicial()`. */
  protected readonly abierta = computed(() => {
    const elegida = this.elegida();
    return elegida === undefined ? this.inicial() : elegida;
  });

  constructor() {
    const ventana = this.documento.defaultView;
    const destruir = inject(DestroyRef);
    afterNextRender(() => {
      this.rehacer();
      if (!ventana) {
        return;
      }
      let pendiente = false;
      const programar = () => {
        if (pendiente) {
          return;
        }
        pendiente = true;
        ventana.requestAnimationFrame(() => {
          pendiente = false;
          this.rehacer();
        });
      };
      const observador = 'ResizeObserver' in ventana ? new ResizeObserver(programar) : null;
      observador?.observe(this.red().nativeElement);
      ventana.addEventListener('resize', programar);
      void this.documento.fonts?.ready.then(programar);
      const animacion = this.animarTrazo();
      destruir.onDestroy(() => {
        observador?.disconnect();
        ventana.removeEventListener('resize', programar);
        animacion?.disconnect();
      });
    });
  }

  protected alPulsar(sede: VistaSede): void {
    const abrir = this.abierta() !== sede.clave;
    if (abrir) {
      this.guardar(sede.clave);
    }
    this.elegida.set(abrir ? sede.clave : null);
  }

  private inicial(): string | null {
    const sedes = this.horarios().sedes;
    if (this.una()) {
      return sedes[0].clave;
    }
    const recordada = this.leer();
    if (recordada && sedes.some((s) => s.clave === recordada)) {
      return recordada;
    }
    return this.documento.defaultView?.matchMedia?.(ESCRITORIO).matches ? sedes[0].clave : null;
  }

  private leer(): string | null {
    try {
      return this.documento.defaultView?.localStorage.getItem(CLAVE_SEDE_RECORDADA) ?? null;
    } catch {
      return null;
    }
  }

  private guardar(clave: string): void {
    // En el editor de la web no se toca el navegador.
    if (this.editor.enEditor()) {
      return;
    }
    try {
      this.documento.defaultView?.localStorage.setItem(CLAVE_SEDE_RECORDADA, clave);
    } catch {
      /* sin almacenamiento */
    }
  }

  private escritorio(): boolean {
    return this.documento.defaultView?.matchMedia?.(ESCRITORIO).matches ?? false;
  }

  private rehacer(): void {
    this.clasificar();
    this.trazar();
  }

  /** Tramo alto (sobre el pie de la foto) o bajo (bajo el texto, al menos 44 px por debajo) de cada parada. */
  private clasificar(): void {
    const red = this.red().nativeElement;
    const escritorio = this.escritorio();
    const conDesnivel = escritorio && !this.una();
    const cajaRed = red.getBoundingClientRect();
    const foto = this.foto()?.getBoundingClientRect() ?? null;
    const limite = foto ? foto.left - cajaRed.left : Number.POSITIVE_INFINITY;
    const ySup = foto ? Math.round(foto.bottom - cajaRed.top) : 44;
    const yInf = Math.max(60, ySup + 44);
    for (const parada of Array.from(this.lista().nativeElement.querySelectorAll<HTMLElement>('.parada'))) {
      const r = parada.getBoundingClientRect();
      const alta = conDesnivel && foto !== null && r.left + r.width / 2 - cajaRed.left > limite;
      parada.classList.toggle('parada--alta', alta);
      if (escritorio) {
        parada.style.setProperty('--y', `${alta ? ySup : this.una() ? 60 : yInf}px`);
      } else {
        parada.style.removeProperty('--y');
      }
    }
  }

  private trazar(): void {
    const red = this.red().nativeElement;
    const svg = this.svg().nativeElement;
    const caja = red.getBoundingClientRect();
    const ancho = red.clientWidth;
    const alto = red.clientHeight;
    svg.setAttribute('width', String(ancho));
    svg.setAttribute('height', String(alto));
    svg.setAttribute('viewBox', `0 0 ${ancho} ${alto}`);
    const puntos = Array.from(this.lista().nativeElement.querySelectorAll<HTMLElement>('.parada-circulo')).map((c) => {
      const r = c.getBoundingClientRect();
      return { x: r.left + r.width / 2 - caja.left, y: r.top + r.height / 2 - caja.top };
    });
    if (!puntos.length) {
      return;
    }
    const ultimo = puntos[puntos.length - 1];
    let d: string;
    let t: string;
    if (!this.escritorio()) {
      // Móvil: vertical, sale del pie de la foto y acaba en un terminal.
      const x = puntos[0].x;
      const yf = Math.max(ultimo.y + 40, alto - 14);
      d = `M${n(x)},0 V${n(yf)}`;
      t = `M${n(x - 18)},${n(yf)} H${n(x + 18)}`;
    } else {
      const x0 = 6;
      const xf = this.una() ? Math.min(ancho - 6, puntos[0].x + 154) : ancho - 6;
      d = `M${x0},${n(puntos[0].y)}`;
      for (let i = 0; i < puntos.length - 1; i++) {
        if (Math.abs(puntos[i + 1].y - puntos[i].y) > 1) {
          d += desnivel(puntos[i], puntos[i + 1]);
        }
      }
      d += ` H${n(xf)}`;
      t = `M${x0},${n(puntos[0].y - 18)} V${n(puntos[0].y + 18)} M${n(xf)},${n(ultimo.y - 18)} V${n(ultimo.y + 18)}`;
    }
    svg.querySelector('.trazo')?.setAttribute('d', d);
    svg.querySelector('.trazo-funda')?.setAttribute('d', d);
    svg.querySelector('.terminales')?.setAttribute('d', t);
  }

  /**
   * Movimiento firma: la línea está dibujada por defecto; solo se anima con IntersectionObserver, sin movimiento
   * reducido y fuera de navegadores sin interfaz (capturas y pruebas).
   */
  private animarTrazo(): IntersectionObserver | null {
    const ventana = this.documento.defaultView;
    if (
      !ventana ||
      !('IntersectionObserver' in ventana) ||
      ventana.matchMedia?.('(prefers-reduced-motion: reduce)').matches ||
      /HeadlessChrome/.test(ventana.navigator.userAgent)
    ) {
      return null;
    }
    const red = this.red().nativeElement;
    red.classList.add('preparada');
    const io = new IntersectionObserver(
      (entradas) => {
        if (!entradas.some((e) => e.isIntersecting)) {
          return;
        }
        io.disconnect();
        ventana.requestAnimationFrame(() => ventana.requestAnimationFrame(() => red.classList.add('dibujar')));
      },
      { threshold: 0.2 },
    );
    io.observe(red);
    return io;
  }
}

function n(v: number): number {
  return Math.round(v * 10) / 10;
}

/** Desnivel a 45° con giros de radio 24 entre dos paradas a distinta altura. */
function desnivel(a: { x: number; y: number }, b: { x: number; y: number }): string {
  const r = 24;
  const ax = r * Math.SQRT1_2;
  const by = r * (1 - Math.SQRT1_2);
  const dy = b.y - a.y;
  const sube = dy < 0;
  const s = sube ? -1 : 1;
  const medio = (2 * ax + Math.abs(dy) - 2 * by) / 2;
  const xm = (a.x + b.x) / 2;
  const x1 = xm - medio;
  const x2 = xm + medio;
  return (
    ` H${n(x1)}` +
    ` A${r} ${r} 0 0 ${sube ? 0 : 1} ${n(x1 + ax)},${n(a.y + s * by)}` +
    ` L${n(x2 - ax)},${n(b.y - s * by)}` +
    ` A${r} ${r} 0 0 ${sube ? 1 : 0} ${n(x2)},${n(b.y)}`
  );
}
