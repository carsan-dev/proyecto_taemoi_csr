import {
  ChangeDetectorRef,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  Input,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';

import { AvisosService } from '../avisos/avisos.service';
import { EntrandoEscuelaComponent } from './entrando-escuela.component';
import { LogoEscuelaComponent } from './logo-escuela.component';
import { EscuelaUsuario, SelectorEscuelaService, metaEscuela } from './selector-escuela.service';

const TEXTO_FALLO = 'No se ha podido cambiar de escuela. Inténtalo otra vez.';

/**
 * «En el panel» (boceto del selector de escuela): con dos escuelas o más, el nombre de la escuela de la cabecera es un
 * botón que abre «Cambiar de escuela»; por debajo de 768 px, una hoja inferior. La cabecera le pasa el logo y el nombre
 * (contenido proyectado, con sus estilos) y solo lo monta con dos escuelas o más.
 *
 * - Orden estable (el de la API) y la actual marcada (`menuitemradio`, `aria-checked`). Elegir la actual solo cierra.
 * - Elegir otra tapa la pantalla («Entrando en …») y cambia la escuela (`SelectorEscuelaService.cambiar`), que recarga la
 *   app. Si falla, se quita la pantalla, la escuela sigue siendo la de antes y un aviso de error ofrece «Reintentar».
 * - Teclado del patrón «menu button» (decisión 243): flechas con vuelta, Inicio, Fin; Esc cierra y devuelve el foco;
 *   Tab cierra y deja seguir.
 */
@Component({
  selector: 'pn-selector-escuela',
  standalone: true,
  imports: [LogoEscuelaComponent, EntrandoEscuelaComponent],
  templateUrl: './selector-escuela-cabecera.component.html',
  styleUrl: './selector-escuela-cabecera.component.scss',
  host: {
    // La cabecera se pone por encima de la barra inferior mientras el menú, la hoja o «Entrando» están a la vista.
    '[class.pn-selector--encima]': 'abierto() || !!entrando()',
  },
})
export class SelectorEscuelaCabeceraComponent implements OnInit {
  @Input({ required: true }) escuelas: readonly EscuelaUsuario[] = [];

  private readonly selector = inject(SelectorEscuelaService);
  private readonly avisos = inject(AvisosService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly abierto = signal(false);
  readonly entrando = signal<EscuelaUsuario | null>(null);
  readonly meta = metaEscuela;

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationStart),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => this.abierto.set(false));
  }

  alternar(): void {
    if (this.abierto()) {
      this.cerrar(false);
      return;
    }
    this.abrir();
  }

  /** Abajo o arriba en el botón abren el menú (en la actual); Esc lo cierra. */
  teclaBoton(evento: KeyboardEvent): void {
    if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
      evento.preventDefault();
      this.abrir();
    } else if (evento.key === 'Escape' && this.abierto()) {
      evento.preventDefault();
      this.cerrar(true);
    }
  }

  teclaMenu(evento: KeyboardEvent): void {
    const opciones = this.opciones();
    const actual = opciones.indexOf(this.host.nativeElement.ownerDocument.activeElement as HTMLButtonElement);
    let siguiente: number | null = null;
    switch (evento.key) {
      case 'ArrowDown':
        siguiente = actual < 0 ? 0 : (actual + 1) % opciones.length;
        break;
      case 'ArrowUp':
        siguiente = actual < 0 ? opciones.length - 1 : (actual - 1 + opciones.length) % opciones.length;
        break;
      case 'Home':
        siguiente = 0;
        break;
      case 'End':
        siguiente = opciones.length - 1;
        break;
      case 'Escape':
        evento.preventDefault();
        evento.stopPropagation();
        this.cerrar(true);
        return;
      case 'Tab':
        this.cerrar(false);
        return;
      default:
        return;
    }
    evento.preventDefault();
    opciones[siguiente]?.focus();
  }

  elegir(escuela: EscuelaUsuario): void {
    if (escuela.activa) {
      this.cerrar(true);
      return;
    }
    if (this.selector.ocupado) {
      return;
    }
    this.cerrar(false);
    this.entrando.set(escuela);
    this.selector.cambiar(escuela.id).subscribe({
      error: () => {
        this.entrando.set(null);
        this.cdr.detectChanges();
        this.boton()?.focus();
        this.avisos.error(TEXTO_FALLO, { texto: 'Reintentar', alPulsar: () => this.elegir(escuela) });
      },
    });
  }

  cerrar(devolverFoco: boolean): void {
    if (!this.abierto()) {
      return;
    }
    this.abierto.set(false);
    if (devolverFoco) {
      this.cdr.detectChanges();
      this.boton()?.focus();
    }
  }

  @HostListener('document:click', ['$event'])
  alPulsarFuera(evento: MouseEvent): void {
    if (this.abierto() && !this.host.nativeElement.contains(evento.target as Node)) {
      this.abierto.set(false);
    }
  }

  private abrir(): void {
    this.abierto.set(true);
    this.cdr.detectChanges();
    const opciones = this.opciones();
    (opciones.find((o) => o.getAttribute('aria-checked') === 'true') ?? opciones[0])?.focus();
  }

  private opciones(): HTMLButtonElement[] {
    return Array.from(
      this.host.nativeElement.querySelectorAll<HTMLButtonElement>('#pn-menu-escuela [role="menuitemradio"]')
    );
  }

  private boton(): HTMLButtonElement | null {
    return this.host.nativeElement.querySelector<HTMLButtonElement>('#pn-escuela-boton');
  }
}
