import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { BARRA_INFERIOR, ENLACES_LEGALES, IdSeccion, SeccionMenu, entradaActual } from './menu-panel';
import { enfocarOpcion, teclaEnMenu } from './teclado-menu';

/**
 * Barra inferior del panel nuevo por debajo de 1024 px (8b-N; DESIGN.md, Navigation): Inicio, Alumnos, Tesorería y
 * «Más», que abre todas las entradas (cada opción a dos toques), lo legal y «Configurar cookies» (decisión 259).
 * Fija abajo, respeta la zona segura; `skeleton` deja el hueco debajo del contenido para que no lo tape.
 */
@Component({
  selector: 'app-barra-inferior',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './barra-inferior.component.html',
  styleUrl: './barra-inferior.component.scss',
})
export class BarraInferiorComponent implements OnChanges {
  @Input({ required: true }) secciones: SeccionMenu[] = [];
  @Input() activa: IdSeccion | null = null;
  @Input() url = '';
  @Input() contador = 0;
  @Output() readonly configurarCookies = new EventEmitter<void>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly legales = ENLACES_LEGALES;
  readonly abierta = signal(false);

  ngOnChanges(cambios: SimpleChanges): void {
    // Al navegar, «Más» se cierra.
    if (cambios['url'] && !cambios['url'].firstChange) {
      this.abierta.set(false);
    }
  }

  get principales(): SeccionMenu[] {
    return BARRA_INFERIOR.map((id) => this.secciones.find((s) => s.id === id)).filter(
      (s): s is SeccionMenu => !!s
    );
  }

  /** Inicio y Solicitudes (sin desplegable) van juntas arriba de «Más»; el resto, cada una con su título. */
  get sueltas(): SeccionMenu[] {
    return this.secciones.filter((s) => s.ruta);
  }

  get conOpciones(): SeccionMenu[] {
    return this.secciones.filter((s) => !s.ruta && s.entradas.length > 0);
  }

  /** Alumnos y Tesorería van a su primera opción (el listado y los cobros). */
  rutaDe(seccion: SeccionMenu): string {
    return seccion.ruta ?? seccion.entradas[0]?.ruta ?? '/adminpage';
  }

  actual(seccion: SeccionMenu): 'page' | 'true' | null {
    if (entradaActual(this.rutaDe(seccion), this.url)) {
      return 'page';
    }
    return this.activa === seccion.id ? 'true' : null;
  }

  esActual(ruta: string): boolean {
    return entradaActual(ruta, this.url);
  }

  /** «Más» se marca cuando la página actual no está en la barra (Grupos, Solicitudes o Escuela). */
  get masActual(): boolean {
    return this.activa !== null && !BARRA_INFERIOR.includes(this.activa);
  }

  textoContador(total: number): string {
    return total > 99 ? '99+' : String(total);
  }

  etiquetaMas(): string | null {
    return this.contador > 0 ? `Más: ${this.contador} solicitudes por atender` : null;
  }

  alternar(evento: MouseEvent): void {
    if (this.abierta()) {
      this.cerrar(false);
      return;
    }
    this.abierta.set(true);
    if (evento.detail === 0) {
      this.cdr.detectChanges();
      enfocarOpcion(this.hoja(), 'primera');
    }
  }

  teclaMas(evento: KeyboardEvent): void {
    if (evento.key === 'ArrowUp' || evento.key === 'ArrowDown') {
      evento.preventDefault();
      this.abierta.set(true);
      this.cdr.detectChanges();
      enfocarOpcion(this.hoja(), evento.key === 'ArrowDown' ? 'primera' : 'ultima');
    } else if (evento.key === 'Escape' && this.abierta()) {
      evento.preventDefault();
      this.cerrar(true);
    }
  }

  teclaHoja(evento: KeyboardEvent): void {
    const hoja = this.hoja();
    const accion = hoja ? teclaEnMenu(evento, hoja) : null;
    if (accion) {
      this.cerrar(accion === 'cerrar');
    }
  }

  cerrar(devolverFoco: boolean): void {
    this.abierta.set(false);
    if (devolverFoco) {
      this.cdr.detectChanges();
      this.host.nativeElement.querySelector<HTMLElement>('#pn-mas-boton')?.focus();
    }
  }

  alConfigurarCookies(): void {
    this.cerrar(false);
    this.configurarCookies.emit();
  }

  @HostListener('document:click', ['$event'])
  alPulsarFuera(evento: MouseEvent): void {
    if (!this.abierta()) {
      return;
    }
    const zona = this.host.nativeElement.querySelector('[data-pn-menu="mas"]');
    if (!zona || !zona.contains(evento.target as Node)) {
      this.abierta.set(false);
    }
  }

  private hoja(): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>('#pn-menu-mas');
  }
}
