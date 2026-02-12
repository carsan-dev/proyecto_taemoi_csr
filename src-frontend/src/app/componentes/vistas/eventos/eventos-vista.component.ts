import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild } from '@angular/core';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

@Component({
  selector: 'app-eventos-vista',
  standalone: true,
  imports: [CommonModule, SkeletonCardComponent],
  templateUrl: './eventos-vista.component.html',
  styleUrl: './eventos-vista.component.scss',
  host: {
    '[class.preview]': 'previewMode'
  }
})
export class EventosVistaComponent implements OnChanges, OnDestroy {
  @Input() eventos: any[] = [];
  @Input() isLoading: boolean = false;
  @Input() enableNavigation: boolean = true;
  @Input() showHero: boolean = true;
  @Input() previewMode: boolean = false;
  @Input() showUserBackButton: boolean = false;

  @Output() eventoClick = new EventEmitter<number>();
  @Output() backToUser = new EventEmitter<void>();

  visibleEventos: any[] = [];

  private readonly loadedImages = new Set<number>();
  private readonly initialBatchSize = 10;
  private readonly batchSize = 8;
  private readonly imageWidthListado = 720;
  private readonly imageWidthPreview = 560;
  private visibleCount: number = 0;
  private loadMoreObserver: IntersectionObserver | null = null;

  @ViewChild('scrollSentinel')
  set scrollSentinelRef(sentinelRef: ElementRef<HTMLElement> | undefined) {
    this.configurarObservadorCarga(sentinelRef?.nativeElement ?? null);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['eventos']) {
      const currentIds = new Set(
        this.eventos
          .map((evento) => evento?.id)
          .filter((id) => id !== null && id !== undefined)
      );
      for (const id of this.loadedImages) {
        if (!currentIds.has(id)) {
          this.loadedImages.delete(id);
        }
      }

      this.reiniciarCargaProgresiva();
    }

    if (changes['previewMode'] && !changes['previewMode'].firstChange) {
      this.reiniciarCargaProgresiva();
    }
  }

  ngOnDestroy(): void {
    this.desconectarObservadorCarga();
  }

  onEventoClick(eventoId: number): void {
    if (!this.enableNavigation) {
      return;
    }
    this.eventoClick.emit(eventoId);
  }

  onBackToUser(): void {
    this.backToUser.emit();
  }

  isImageLoaded(eventoId: number): boolean {
    return this.loadedImages.has(eventoId);
  }

  onImageLoad(eventoId: number): void {
    if (eventoId === null || eventoId === undefined) {
      return;
    }
    this.loadedImages.add(eventoId);
  }

  onImageError(eventoId: number): void {
    if (eventoId === null || eventoId === undefined) {
      return;
    }
    this.loadedImages.add(eventoId);
  }

  getEventoImageUrl(evento: any): string {
    const fallback = '../../../../assets/media/default.webp';
    const rawUrl = evento?.fotoEvento?.url;
    if (!rawUrl) {
      return fallback;
    }
    const width = this.previewMode ? this.imageWidthPreview : this.imageWidthListado;
    const version = this.obtenerVersionImagen(evento?.fotoEvento);
    const urlConAncho = this.agregarParametroAncho(rawUrl, width);
    return this.agregarParametroVersion(urlConAncho, version);
  }

  get hasMoreEventos(): boolean {
    return this.visibleCount < this.eventos.length;
  }

  private reiniciarCargaProgresiva(): void {
    const totalEventos = Array.isArray(this.eventos) ? this.eventos.length : 0;
    this.visibleCount = Math.min(totalEventos, this.initialBatchSize);
    this.visibleEventos = this.eventos.slice(0, this.visibleCount);
  }

  private cargarMasEventos(): void {
    if (!this.hasMoreEventos) {
      return;
    }

    this.visibleCount = Math.min(this.eventos.length, this.visibleCount + this.batchSize);
    this.visibleEventos = this.eventos.slice(0, this.visibleCount);
  }

  private desconectarObservadorCarga(): void {
    if (this.loadMoreObserver) {
      this.loadMoreObserver.disconnect();
      this.loadMoreObserver = null;
    }
  }

  private configurarObservadorCarga(sentinel: HTMLElement | null): void {
    this.desconectarObservadorCarga();
    if (!sentinel || typeof IntersectionObserver === 'undefined') {
      if (typeof IntersectionObserver === 'undefined' && this.hasMoreEventos) {
        this.visibleCount = this.eventos.length;
        this.visibleEventos = this.eventos.slice(0, this.visibleCount);
      }
      return;
    }

    this.loadMoreObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          this.cargarMasEventos();
        }
      },
      {
        root: null,
        rootMargin: '500px 0px',
        threshold: 0.01,
      }
    );

    this.loadMoreObserver.observe(sentinel);
  }

  private agregarParametroAncho(url: string, width: number): string {
    const widthValue = Math.max(160, Math.floor(width));
    if (/([?&])w=\d+/.test(url)) {
      return url.replace(/([?&])w=\d+/, `$1w=${widthValue}`);
    }
    const separador = url.includes('?') ? '&' : '?';
    return `${url}${separador}w=${widthValue}`;
  }

  private agregarParametroVersion(url: string, version: string): string {
    const versionSegura = encodeURIComponent(version);
    if (/([?&])v=[^&]*/.test(url)) {
      return url.replace(/([?&])v=[^&]*/, `$1v=${versionSegura}`);
    }
    const separador = url.includes('?') ? '&' : '?';
    return `${url}${separador}v=${versionSegura}`;
  }

  private obtenerVersionImagen(fotoEvento: any): string {
    const version = fotoEvento?.id ?? fotoEvento?.nombre ?? '0';
    return String(version);
  }
}
