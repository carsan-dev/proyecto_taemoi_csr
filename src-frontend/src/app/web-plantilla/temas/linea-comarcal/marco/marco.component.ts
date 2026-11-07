import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  ViewEncapsulation,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { RELOJ_WEB } from '../../../comun/reloj';
import { SitioWeb } from '../../../datos/modelos';
import { PaginaMarco, vistaMarco } from '../contenido';
import { TEXTOS_FIJOS_LINEA_COMARCAL as FIJOS } from '../textos';

/**
 * Marco del tema «La línea comarcal»: cabecera (logo o iniciales, menú, «Preinscribirme») y pie, iguales en todas las
 * páginas del tema (bocetos `boceto-linea-comarcal.html` y `boceto-login-404-sin-publicar.html`). Pone el color de la
 * escuela (variables de `derivarColores`) y carga los estilos base y las fuentes del tema (sin encapsulación, bajo
 * `.lc`). El contenido de la página va dentro, en `<main>`.
 *
 * Menú en móvil a pantalla completa: Escape lo cierra y devuelve el foco al botón; un enlace también lo cierra.
 */
@Component({
  selector: 'app-lc-marco',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './marco.component.html',
  styleUrl: './linea-comarcal.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'alPulsarEscape()' },
})
export class MarcoLineaComarcalComponent {
  readonly sitio = input.required<SitioWeb>();
  /** En qué página está: en la portada el menú va a sus secciones; en las demás, «Tarifas» va a su página. */
  readonly pagina = input<PaginaMarco>('portada');

  private readonly documento = inject(DOCUMENT);
  private readonly reloj = inject(RELOJ_WEB);
  private readonly botonMenu = viewChild<ElementRef<HTMLButtonElement>>('botonMenu');
  private readonly principal = viewChild<ElementRef<HTMLElement>>('principal');

  protected readonly fijos = FIJOS;
  protected readonly vista = computed(() => vistaMarco(this.sitio(), this.reloj(), this.pagina()));
  protected readonly menuAbierto = signal(false);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.documento.body.classList.remove('lc-menu-abierto'));
  }

  protected alternarMenu(): void {
    this.ponerMenu(!this.menuAbierto());
  }

  protected alPulsarEnlaceDelMenu(evento: Event): void {
    if ((evento.target as Element | null)?.closest('a')) {
      this.ponerMenu(false);
    }
  }

  protected alPulsarEscape(): void {
    if (this.menuAbierto()) {
      this.ponerMenu(false);
      this.botonMenu()?.nativeElement.focus();
    }
  }

  /** «Saltar al contenido» sin cambiar la URL (un `href="#…"` iría a la raíz por `<base href="/">`). */
  protected saltar(evento: Event): void {
    evento.preventDefault();
    this.principal()?.nativeElement.focus();
  }

  private ponerMenu(abrir: boolean): void {
    this.menuAbierto.set(abrir);
    this.documento.body.classList.toggle('lc-menu-abierto', abrir);
  }
}
