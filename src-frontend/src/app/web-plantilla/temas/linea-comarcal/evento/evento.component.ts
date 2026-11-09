import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewEncapsulation,
  computed,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { paginaWeb } from '../../../comun/pagina-web';
import { VistaPreviaPipe } from '../../../comun/vista-previa';
import { EstadoRecurso, EventoDetalleWeb } from '../../../datos/modelos';
import { WebPublicaService } from '../../../datos/web-publica.service';
import { SinPublicarComponent } from '../../../paginas/sin-publicar.component';
import { MarcoLineaComarcalComponent } from '../marco/marco.component';
import { ContenidoNoEncontradaLineaComarcalComponent } from '../no-encontrada/no-encontrada.component';
import { TEXTOS_FIJOS_LINEA_COMARCAL, TEXTOS_PAGINAS_LINEA_COMARCAL } from '../textos';
import { vistaEventoDetalle } from './evento';

const NO_ENCONTRADO: EstadoRecurso<EventoDetalleWeb> = { estado: 'no-encontrado' };

/**
 * Página de un evento del tema «La línea comarcal» (`/eventos/:id`, boceto `fase4-web/boceto-linea-comarcal-evento.html`)
 * con `GET /api/publico/web/eventos/{id}`. Un id que la API no da (oculto, de otra escuela, inexistente o que no es un
 * número) pinta la 404 del tema (`noindex`); si la web no está publicada, «Web aún no publicada» (lo dice el sitio).
 *
 * Galería: cada foto se amplía en un `<dialog>` modal; Escape o «Cerrar» lo cierran y el foco vuelve a la miniatura;
 * las flechas pasan de foto y Tab no sale del visor.
 */
@Component({
  selector: 'app-lc-evento',
  standalone: true,
  imports: [VistaPreviaPipe, 
    MarcoLineaComarcalComponent,
    ContenidoNoEncontradaLineaComarcalComponent,
    NgTemplateOutlet,
    RouterLink,
    SinPublicarComponent,
  ],
  templateUrl: './evento.component.html',
  styleUrl: './evento.component.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventoLineaComarcalComponent {
  private readonly web = inject(WebPublicaService);
  private readonly id = toSignal(
    inject(ActivatedRoute).paramMap.pipe(
      map((p) => {
        const valor = p.get('id') ?? '';
        return /^\d{1,15}$/.test(valor) ? Number(valor) : null;
      }),
    ),
    { requireSync: true },
  );
  private readonly visor = viewChild<ElementRef<HTMLDialogElement>>('visor');
  private origen: HTMLElement | null = null;

  protected readonly textos = TEXTOS_PAGINAS_LINEA_COMARCAL;
  protected readonly fijos = TEXTOS_FIJOS_LINEA_COMARCAL;
  /** La petición se lanza (o se reutiliza de la caché) fuera del contexto reactivo: escribe en signals. */
  private readonly recurso = computed(() => {
    const id = this.id();
    const previa = this.pagina.vistaPrevia();
    return id === null ? null : untracked(() => this.web.evento(id, previa));
  });
  private readonly evento = computed(() => this.recurso()?.() ?? NO_ENCONTRADO);
  protected readonly pagina = paginaWeb('evento', () => `/eventos/${this.id() ?? ''}`, { evento: this.evento });
  protected readonly estadoEvento = computed(() => this.evento().estado);
  protected readonly vista = computed(() => {
    const actual = this.evento();
    return actual.estado === 'listo' ? vistaEventoDetalle(actual.datos) : null;
  });

  private readonly indice = signal(0);
  protected readonly visorAbierto = signal(false);
  /** La foto del visor (dentro de la galería aunque se pase a otro evento con menos fotos). */
  protected readonly fotoActual = computed(() => {
    const total = this.vista()?.fotos.length ?? 0;
    return total ? Math.min(this.indice(), total - 1) : 0;
  });
  protected readonly deTotal = computed(() =>
    this.textos.de.replace('{total}', String(this.vista()?.fotos.length ?? 0)),
  );

  protected abrir(indice: number, evento: Event): void {
    const visor = this.visor()?.nativeElement;
    if (!visor || typeof visor.showModal !== 'function') {
      return;
    }
    this.origen = evento.currentTarget as HTMLElement | null;
    this.indice.set(indice);
    this.visorAbierto.set(true);
    visor.showModal();
    visor.querySelector<HTMLButtonElement>('.visor-barra button')?.focus();
  }

  protected cerrar(): void {
    this.visor()?.nativeElement.close();
  }

  protected mover(paso: number): void {
    const total = this.vista()?.fotos.length ?? 0;
    if (total) {
      this.indice.set((this.fotoActual() + paso + total) % total);
    }
  }

  protected alCerrar(): void {
    this.visorAbierto.set(false);
    this.origen?.focus();
    this.origen = null;
  }

  /** Pulsar fuera de la foto (en el fondo del diálogo) lo cierra. */
  protected alPulsarVisor(evento: MouseEvent): void {
    if (evento.target === this.visor()?.nativeElement) {
      this.cerrar();
    }
  }

  protected alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'ArrowLeft' || evento.key === 'ArrowRight') {
      evento.preventDefault();
      this.mover(evento.key === 'ArrowLeft' ? -1 : 1);
    } else if (evento.key === 'Tab') {
      // Tab y Mayús+Tab dan la vuelta dentro del visor.
      const botones = Array.from(this.visor()?.nativeElement.querySelectorAll('button') ?? []);
      const activo = this.visor()?.nativeElement.ownerDocument.activeElement;
      const primero = botones[0];
      const ultimo = botones[botones.length - 1];
      if (evento.shiftKey && activo === primero) {
        evento.preventDefault();
        ultimo?.focus();
      } else if (!evento.shiftKey && activo === ultimo) {
        evento.preventDefault();
        primero?.focus();
      }
    }
  }
}
