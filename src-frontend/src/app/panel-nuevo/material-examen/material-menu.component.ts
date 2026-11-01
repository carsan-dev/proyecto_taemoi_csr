import { ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild, inject } from '@angular/core';

import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';

export interface OpcionMenu {
  id: string;
  texto: string;
  peligro?: boolean;
  deshabilitada?: boolean;
  /** Abre un diálogo: se anuncia con `aria-haspopup`. */
  dialogo?: boolean;
}

/** Medidas del menú para colocarlo dentro de la pantalla: va con `position: fixed` y su alto depende de las opciones. */
const MENU_ANCHO = 220;
const OPCION_ALTO = 44;
const MENU_RELLENO = 10;
const MENU_MARGEN = 8;

let siguienteId = 0;

/**
 * Menú «⋯» de una fila de material (boceto `Bloque.dc.html`), con el teclado del patrón «menu button» de
 * `navegacion/teclado-menu`. Al elegir una opción se cierra y el foco vuelve al botón, de modo que un diálogo abierto
 * después lo devuelve al mismo sitio.
 */
@Component({
  selector: 'pn-material-menu',
  standalone: true,
  template: `
    <div class="menu" (focusout)="alSalir($event)">
      <button #boton type="button" class="menu__boton" [id]="id" [disabled]="deshabilitado" [attr.aria-label]="etiqueta" aria-haspopup="menu"
        [attr.aria-expanded]="abierto" [attr.aria-controls]="abierto ? id + '-lista' : null" (click)="alternar($event)" (keydown)="teclaBoton($event)">
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="1.8"></circle><circle cx="12" cy="12" r="1.8"></circle><circle cx="19" cy="12" r="1.8"></circle></svg>
      </button>
      @if (abierto) {
      <div #lista class="menu__lista" role="menu" [id]="id + '-lista'" [attr.aria-labelledby]="id" [style.top.px]="posicion.top" [style.left.px]="posicion.left"
        (keydown)="teclaLista($event)">
        @for (opcion of opciones; track opcion.id) {
        <button type="button" role="menuitem" class="menu__opcion" [class.menu__opcion--peligro]="opcion.peligro" [disabled]="opcion.deshabilitada"
          [attr.aria-haspopup]="opcion.dialogo ? 'dialog' : null" (click)="elegirOpcion(opcion)">{{ opcion.texto }}</button>
        }
      </div>
      }
    </div>
  `,
  styleUrls: ['./material-menu.component.scss'],
})
export class MaterialMenuComponent implements OnDestroy {
  private readonly cdr = inject(ChangeDetectorRef);

  /** Nombre accesible del botón: dice de qué fila es. */
  @Input({ required: true }) etiqueta = '';
  @Input({ required: true }) opciones: readonly OpcionMenu[] = [];
  @Input() deshabilitado = false;
  @Output() readonly elegir = new EventEmitter<string>();

  @ViewChild('boton', { static: true }) private botonRef!: ElementRef<HTMLButtonElement>;
  @ViewChild('lista') private listaRef?: ElementRef<HTMLElement>;

  readonly id = `pn-material-menu-${++siguienteId}`;
  abierto = false;
  posicion = { top: 0, left: 0 };
  private escuchando = false;

  private readonly alPulsarFuera = (evento: Event): void => {
    const destino = evento.target as Node | null;
    if (destino && this.botonRef.nativeElement.parentElement?.contains(destino)) {
      return;
    }
    this.cerrar(false);
    this.cdr.markForCheck();
  };

  /** Con `position: fixed` se quedaría suelto al desplazar o girar. */
  private readonly alMoverPantalla = (): void => {
    this.cerrar(false);
    this.cdr.markForCheck();
  };

  ngOnDestroy(): void {
    this.quitarEscucha();
  }

  alternar(evento?: Event): void {
    if (this.abierto) {
      this.cerrar(false);
      return;
    }
    this.colocar();
    this.abierto = true;
    this.ponerEscucha();
    this.cdr.detectChanges();
    enfocarOpcion(this.listaRef?.nativeElement ?? null, (evento as KeyboardEvent | undefined)?.key === 'ArrowUp' ? 'ultima' : 'primera');
  }

  teclaBoton(evento: KeyboardEvent): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && !this.abierto) {
      evento.preventDefault();
      this.alternar(evento);
    }
  }

  teclaLista(evento: KeyboardEvent): void {
    const lista = this.listaRef?.nativeElement;
    if (!lista) {
      return;
    }
    const accion = teclaEnMenu(evento, lista);
    if (accion === 'cerrar') {
      this.cerrar(true);
    } else if (accion === 'salir') {
      this.cerrar(false);
    }
  }

  elegirOpcion(opcion: OpcionMenu): void {
    this.cerrar(true);
    this.elegir.emit(opcion.id);
  }

  alSalir(evento: FocusEvent): void {
    const zona = evento.currentTarget as HTMLElement | null;
    const destino = evento.relatedTarget as Node | null;
    if (zona && destino && !zona.contains(destino)) {
      this.cerrar(false);
    }
  }

  private cerrar(devolverFoco: boolean): void {
    this.abierto = false;
    this.quitarEscucha();
    if (devolverFoco) {
      this.cdr.detectChanges();
      this.botonRef.nativeElement.focus();
    }
  }

  private colocar(): void {
    const caja = this.botonRef.nativeElement.getBoundingClientRect();
    const alto = this.opciones.length * OPCION_ALTO + MENU_RELLENO;
    const izquierda = Math.min(Math.max(caja.right - MENU_ANCHO, MENU_MARGEN), Math.max(window.innerWidth - MENU_ANCHO - MENU_MARGEN, MENU_MARGEN));
    const abajo = caja.bottom + 4;
    const arriba = caja.top - alto - 4;
    this.posicion = { top: abajo + alto > window.innerHeight && arriba >= MENU_MARGEN ? arriba : abajo, left: izquierda };
  }

  private ponerEscucha(): void {
    if (!this.escuchando) {
      document.addEventListener('pointerdown', this.alPulsarFuera, true);
      window.addEventListener('scroll', this.alMoverPantalla, true);
      window.addEventListener('resize', this.alMoverPantalla);
      this.escuchando = true;
    }
  }

  private quitarEscucha(): void {
    if (this.escuchando) {
      document.removeEventListener('pointerdown', this.alPulsarFuera, true);
      window.removeEventListener('scroll', this.alMoverPantalla, true);
      window.removeEventListener('resize', this.alMoverPantalla);
      this.escuchando = false;
    }
  }
}
