import { Component, DestroyRef, Input, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  EventoFotosApiService,
  FotoEventoApi,
} from '../../../../panel-nuevo/eventos/evento-fotos-api.service';
import { EventoFotosVisorComponent, FotoVisor } from './evento-fotos-visor.component';
import { textoNumeroFotos, urlFotoConAncho } from './evento-fotos-url';

/** Casillas del mosaico: la grande de 2×2 y 8 pequeñas, tres filas de cuatro columnas sin huecos. */
const MAX_MINIATURAS = 9;
const ANCHO_MINIATURA = 640;

interface Miniatura {
  indice: number;
  src: string;
  alt: string;
  clases: string;
  /** Fotos que no caben, contando la de esta casilla; solo en la novena cuando hay más de nueve. */
  restantes: number;
}

/**
 * Sección «Fotos del evento» de la página pública (E8c · F3). Pide la galería ella misma y, si no hay fotos
 * o la petición falla, no pinta nada (sin esqueleto ni aviso). Va aparte de `EndpointsService`, que está en el bundle inicial.
 */
@Component({
  selector: 'app-evento-fotos-galeria',
  standalone: true,
  imports: [EventoFotosVisorComponent],
  templateUrl: './evento-fotos-galeria.component.html',
  styleUrl: './evento-fotos-galeria.component.scss',
})
export class EventoFotosGaleriaComponent implements OnInit {
  @Input({ required: true }) eventoId!: number;
  @Input() titulo = '';

  readonly fotos = signal<FotoEventoApi[]>([]);
  readonly indiceVisor = signal<number | null>(null);
  retornoFoco: HTMLElement | null = null;

  readonly total = computed(() => this.fotos().length);
  readonly textoTotal = computed(() => textoNumeroFotos(this.total()));
  readonly miniaturas = computed<Miniatura[]>(() => this.crearMiniaturas());
  readonly fotosVisor = computed<FotoVisor[]>(() =>
    this.fotos().map((foto, i) => ({ url: foto.url, alt: this.alt(i) })),
  );

  private readonly api = inject(EventoFotosApiService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.api
      .listar(this.eventoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (fotos) => this.fotos.set(Array.isArray(fotos) ? fotos : []),
        // Un 404 (evento oculto) o un fallo de red: la página sigue como sin fotos, sin aviso.
        error: () => this.fotos.set([]),
      });
  }

  abrir(indice: number, origen?: Event): void {
    this.retornoFoco = origen?.currentTarget instanceof HTMLElement ? origen.currentTarget : null;
    this.indiceVisor.set(indice);
  }

  cerrar(): void {
    this.indiceVisor.set(null);
  }

  private alt(indice: number): string {
    return `Foto ${indice + 1} de ${this.total()} del evento ${this.titulo}`;
  }

  private crearMiniaturas(): Miniatura[] {
    const todas = this.fotos();
    const mostradas = todas.slice(0, MAX_MINIATURAS);
    const n = mostradas.length;
    return mostradas.map((foto, i) => ({
      indice: i,
      src: urlFotoConAncho(foto.url, ANCHO_MINIATURA),
      alt: this.alt(i),
      clases: this.clasesCasilla(i, n),
      restantes: i === MAX_MINIATURAS - 1 && todas.length > MAX_MINIATURAS ? todas.length - (MAX_MINIATURAS - 1) : 0,
    }));
  }

  /**
   * Reparto del mosaico de 4 columnas: la primera ocupa 2×2 y las demás rellenan el resto sin huecos
   * (a la derecha 2×2 casillas y debajo una fila completa). En móvil lo recoloca el CSS.
   */
  private clasesCasilla(i: number, n: number): string {
    let columnas = 1;
    let filas = 1;
    if (n === 1) {
      columnas = 4;
      filas = 3;
    } else if (i === 0) {
      columnas = 2;
      filas = 2;
    } else {
      const otras = n - 1;
      const j = i - 1;
      if (otras === 1) {
        columnas = 2;
        filas = 2;
      } else if (otras === 2) {
        columnas = 2;
      } else if (otras === 3 && j === 0) {
        columnas = 2;
      } else if (otras > 4) {
        const enFila = otras - 4;
        if (j >= 4) {
          if (enFila === 1) {
            columnas = 4;
          } else if (enFila === 2) {
            columnas = 2;
          } else if (enFila === 3 && j === otras - 1) {
            columnas = 2;
          }
        }
      }
    }
    const clases = [`c${columnas}`, `f${filas}`];
    if (i === 0) {
      clases.push('casilla--primera');
    }
    if (i === n - 1 && i > 0 && (n - 1) % 2 === 1) {
      clases.push('casilla--ultima-impar');
    }
    return clases.join(' ');
  }
}
