import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  Input,
  OnDestroy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';

import { AvisosService } from '../avisos/avisos.service';
import { CONSULTA_HOJA, PilaCapas, anchoDeHoja, contenerFoco } from '../capas/capa-modal';
import { LogoEscuelaComponent } from './logo-escuela.component';
import { EscuelaUsuario, SelectorEscuelaService, metaEscuela } from './selector-escuela.service';

const TEXTO_FALLO = 'No se ha podido cambiar de escuela. Inténtalo otra vez.';

/**
 * «En el panel» (boceto del selector de escuela): con dos escuelas o más, el nombre de la escuela de la cabecera es un
 * botón que abre «Cambiar de escuela»; por debajo de 768 px, una hoja inferior modal. La cabecera le pasa el logo y el
 * nombre (contenido proyectado, con sus estilos) y solo lo monta con dos escuelas o más.
 *
 * - Orden estable (el de la API) y la actual marcada (`menuitemradio`, `aria-checked`). Elegir la actual solo cierra.
 * - Elegir otra cambia la escuela (`SelectorEscuelaService.cambiar`): primero sale de la pantalla (que puede preguntar
 *   por cambios sin guardar; si la persona se queda, no pasa nada y el foco vuelve al botón), luego «Entrando en …» y
 *   la recarga. Si falla, vuelve a la pantalla de antes, la escuela sigue siendo la de antes y un aviso de error ofrece
 *   «Reintentar».
 * - Teclado del patrón «menu button» (decisión 243): flechas con vuelta, Inicio, Fin; Esc cierra y devuelve el foco.
 *   En el menú de escritorio, Tab cierra y deja seguir. La hoja inferior es un `<dialog>` con `showModal()` (capas del
 *   panel, `capa-modal.ts`): el fondo queda inerte, Tab y Mayús+Tab dan la vuelta dentro y Esc la cierra.
 * - Menú o hoja se deciden al abrir: si la ventana cruza los 768 px con uno abierto, se cierra y el foco vuelve al
 *   botón (el menú no se queda debajo de otra capa ni la hoja como menú).
 */
@Component({
  selector: 'pn-selector-escuela',
  standalone: true,
  imports: [NgTemplateOutlet, LogoEscuelaComponent],
  templateUrl: './selector-escuela-cabecera.component.html',
  styleUrl: './selector-escuela-cabecera.component.scss',
  host: {
    // La cabecera se pone por encima de la barra inferior mientras el menú o la hoja están a la vista.
    '[class.pn-selector--encima]': 'abierto()',
  },
})
export class SelectorEscuelaCabeceraComponent implements OnInit, OnDestroy {
  @Input({ required: true }) escuelas: readonly EscuelaUsuario[] = [];

  private readonly selector = inject(SelectorEscuelaService);
  private readonly avisos = inject(AvisosService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pila = inject(PilaCapas);

  readonly abierto = signal(false);
  /** Abierto como hoja inferior modal (por debajo de 768 px al abrir). */
  readonly hoja = signal(false);
  readonly meta = metaEscuela;

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationStart),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => this.ocultar());
    this.vigilarAncho();
  }

  ngOnDestroy(): void {
    this.ocultar();
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
        if (this.hoja()) {
          const dialogo = this.dialogo();
          if (dialogo) {
            contenerFoco(evento, dialogo);
          }
        } else {
          this.cerrar(false);
        }
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
    this.selector.cambiar(escuela).subscribe({
      next: (cambiada) => {
        if (!cambiada) {
          // Se quedó en la pantalla (cambios sin guardar): nada ha cambiado.
          this.boton()?.focus();
        }
      },
      error: () => {
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
    this.ocultar();
    if (devolverFoco) {
      this.cdr.detectChanges();
      this.boton()?.focus();
    }
  }

  @HostListener('document:click', ['$event'])
  alPulsarFuera(evento: MouseEvent): void {
    if (this.abierto() && !this.host.nativeElement.contains(evento.target as Node)) {
      this.ocultar();
    }
  }

  /** Esc en la hoja modal: la cierra (sin que el navegador cierre el `<dialog>` por su cuenta) y devuelve el foco. */
  alCancelarHoja(evento: Event): void {
    evento.preventDefault();
    this.cerrar(true);
  }

  /** Al cruzar los 768 px con el menú o la hoja abiertos: se cierra y el foco vuelve al botón. */
  private vigilarAncho(): void {
    const ventana = this.host.nativeElement.ownerDocument.defaultView;
    if (!ventana || typeof ventana.matchMedia !== 'function') {
      return;
    }
    const consulta = ventana.matchMedia(CONSULTA_HOJA);
    if (typeof consulta?.addEventListener !== 'function') {
      return;
    }
    const alCambiar = (): void => {
      if (this.abierto()) {
        this.cerrar(true);
      }
    };
    consulta.addEventListener('change', alCambiar);
    this.destroyRef.onDestroy(() => consulta.removeEventListener('change', alCambiar));
  }

  private abrir(): void {
    if (!this.abierto()) {
      this.hoja.set(anchoDeHoja());
      this.abierto.set(true);
      this.cdr.detectChanges();
    }
    const dialogo = this.dialogo();
    if (dialogo && !dialogo.open) {
      dialogo.showModal();
      this.pila.abrir(dialogo);
    }
    const opciones = this.opciones();
    (opciones.find((o) => o.getAttribute('aria-checked') === 'true') ?? opciones[0])?.focus();
  }

  /** Cierra el menú o la hoja (sin mover el foco); la hoja sale de la pila de capas. */
  private ocultar(): void {
    const dialogo = this.dialogo();
    if (dialogo) {
      this.pila.cerrar(dialogo);
      if (dialogo.open) {
        dialogo.close();
      }
    }
    this.abierto.set(false);
  }

  private dialogo(): HTMLDialogElement | null {
    return this.host.nativeElement.querySelector<HTMLDialogElement>('dialog.pn-selector__hoja');
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
