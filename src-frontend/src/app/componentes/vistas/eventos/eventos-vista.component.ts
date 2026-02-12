import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

type EventoFechaEstado = 'proximo' | 'hoy' | 'realizado' | 'sin-fecha';

interface EventoVistaCacheEntry {
  cacheKey: string;
  item: any;
}

@Component({
  selector: 'app-eventos-vista',
  standalone: true,
  imports: [CommonModule, SkeletonCardComponent],
  templateUrl: './eventos-vista.component.html',
  styleUrl: './eventos-vista.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.preview]': 'previewMode',
  },
})
export class EventosVistaComponent implements OnChanges {
  @Input() eventos: any[] = [];
  @Input() isLoading: boolean = false;
  @Input() enableNavigation: boolean = true;
  @Input() showHero: boolean = true;
  @Input() previewMode: boolean = false;
  @Input() showUserBackButton: boolean = false;

  @Output() eventoClick = new EventEmitter<number>();
  @Output() backToUser = new EventEmitter<void>();

  visibleEventos: any[] = [];
  eventosDestacados: any[] = [];
  eventoPrincipal: any | null = null;
  eventosSecundarios: any[] = [];
  hasMoreEventos: boolean = false;
  destacadoActualIndex: number = 0;

  private readonly loadedImages = new Set<number>();
  private readonly initialBatchSize = 12;
  private readonly batchSize = 8;
  private readonly maxDestacados = 5;
  private readonly imageWidthListado = 720;
  private readonly imageWidthPreview = 560;
  private readonly formateadorFechaCorta = new Intl.DateTimeFormat('es-ES', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
  private visibleCount: number = 0;
  private eventoVistaCache = new WeakMap<any, EventoVistaCacheEntry>();

  constructor(private readonly cdr: ChangeDetectorRef) {}

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

      this.eventoVistaCache = new WeakMap<any, EventoVistaCacheEntry>();
      this.destacadoActualIndex = 0;
      this.reiniciarCargaProgresiva();
    }

    if (changes['previewMode'] && !changes['previewMode'].firstChange) {
      this.eventoVistaCache = new WeakMap<any, EventoVistaCacheEntry>();
      this.reiniciarCargaProgresiva();
    }
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
    this.cdr.markForCheck();
  }

  onImageError(eventoId: number): void {
    if (eventoId === null || eventoId === undefined) {
      return;
    }
    this.loadedImages.add(eventoId);
    this.cdr.markForCheck();
  }

  seleccionarDestacado(index: number, event?: Event): void {
    event?.stopPropagation();
    if (index < 0 || index >= this.eventosDestacados.length) {
      return;
    }
    this.destacadoActualIndex = index;
    this.sincronizarEventoPrincipal();
    this.cdr.markForCheck();
  }

  mostrarDestacadoAnterior(event: Event): void {
    event.stopPropagation();
    const totalDestacados = this.eventosDestacados.length;
    if (totalDestacados <= 1) {
      return;
    }
    this.destacadoActualIndex =
      (this.destacadoActualIndex - 1 + totalDestacados) % totalDestacados;
    this.sincronizarEventoPrincipal();
    this.cdr.markForCheck();
  }

  mostrarDestacadoSiguiente(event: Event): void {
    event.stopPropagation();
    const totalDestacados = this.eventosDestacados.length;
    if (totalDestacados <= 1) {
      return;
    }
    this.destacadoActualIndex = (this.destacadoActualIndex + 1) % totalDestacados;
    this.sincronizarEventoPrincipal();
    this.cdr.markForCheck();
  }

  onCargarMasEventos(): void {
    this.cargarMasEventos();
  }

  private reiniciarCargaProgresiva(): void {
    const totalEventos = Array.isArray(this.eventos) ? this.eventos.length : 0;
    this.visibleCount = Math.min(totalEventos, this.initialBatchSize);
    this.actualizarEventoVisibleState();
  }

  private cargarMasEventos(): void {
    if (!this.hasMoreEventos) {
      return;
    }

    this.visibleCount = Math.min(this.eventos.length, this.visibleCount + this.batchSize);
    this.actualizarEventoVisibleState();
  }

  private actualizarEventoVisibleState(): void {
    const slice = this.eventos.slice(0, this.visibleCount);
    this.visibleEventos = slice.map((evento) => this.enriquecerEventoVista(evento));
    this.hasMoreEventos = this.visibleCount < this.eventos.length;
    this.eventosDestacados = this.visibleEventos.slice(0, this.maxDestacados);
    this.ajustarIndiceDestacado();
    this.sincronizarEventoPrincipal();
    this.eventosSecundarios = this.visibleEventos.slice(this.maxDestacados);
    this.cdr.markForCheck();
  }

  private sincronizarEventoPrincipal(): void {
    if (this.eventosDestacados.length === 0) {
      this.eventoPrincipal = null;
      return;
    }
    this.eventoPrincipal = this.eventosDestacados[this.destacadoActualIndex] ?? null;
  }

  private enriquecerEventoVista(evento: any): any {
    if (!evento || typeof evento !== 'object') {
      return evento;
    }

    const cacheKey = this.crearClaveCache(evento);
    const cache = this.eventoVistaCache.get(evento);
    if (cache?.cacheKey === cacheKey) {
      return cache.item;
    }

    const fecha = this.parsearFechaEvento(evento?.fechaEvento);
    const fechaEstado = this.obtenerEstadoFecha(fecha);
    const fechaTexto = fecha ? this.formateadorFechaCorta.format(fecha) : null;
    const fechaEtiqueta = this.obtenerEtiquetaFecha(fechaEstado);

    const item = {
      ...evento,
      _imageUrl: this.construirEventoImageUrl(evento),
      _fechaTexto: fechaTexto,
      _fechaEtiqueta: fechaEtiqueta,
      _fechaEstado: fechaEstado,
    };

    this.eventoVistaCache.set(evento, { cacheKey, item });
    return item;
  }

  private crearClaveCache(evento: any): string {
    const foto = evento?.fotoEvento;
    const fotoKey = `${foto?.id ?? ''}|${foto?.nombre ?? ''}|${foto?.url ?? ''}`;
    const fechaRaw = Array.isArray(evento?.fechaEvento)
      ? evento.fechaEvento.join('-')
      : String(evento?.fechaEvento ?? '');
    const modo = this.previewMode ? 'preview' : 'listado';
    return `${modo}|${fotoKey}|${fechaRaw}`;
  }

  private construirEventoImageUrl(evento: any): string {
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

  private obtenerEstadoFecha(fecha: Date | null): EventoFechaEstado {
    if (!fecha) {
      return 'sin-fecha';
    }

    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    const fechaMillis = fecha.getTime();
    const hoyMillis = hoy.getTime();
    if (fechaMillis === hoyMillis) {
      return 'hoy';
    }
    return fechaMillis > hoyMillis ? 'proximo' : 'realizado';
  }

  private obtenerEtiquetaFecha(estado: EventoFechaEstado): string | null {
    if (estado === 'sin-fecha') {
      return null;
    }
    if (estado === 'hoy') {
      return 'Hoy';
    }
    return estado === 'proximo' ? 'Pr\u00F3ximo' : 'Realizado';
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

  private parsearFechaEvento(fechaEvento: unknown): Date | null {
    let year: number;
    let month: number;
    let day: number;

    if (typeof fechaEvento === 'string' && fechaEvento.trim()) {
      const match = fechaEvento.match(/^(\d{4})-(\d{2})-(\d{2})$/);
      if (!match) {
        return null;
      }
      year = Number(match[1]);
      month = Number(match[2]);
      day = Number(match[3]);
    } else if (Array.isArray(fechaEvento) && fechaEvento.length >= 3) {
      year = Number(fechaEvento[0]);
      month = Number(fechaEvento[1]);
      day = Number(fechaEvento[2]);
    } else {
      return null;
    }

    if (Number.isNaN(year) || Number.isNaN(month) || Number.isNaN(day)) {
      return null;
    }

    const fecha = new Date(year, month - 1, day);
    if (
      fecha.getFullYear() !== year ||
      fecha.getMonth() !== month - 1 ||
      fecha.getDate() !== day
    ) {
      return null;
    }

    fecha.setHours(0, 0, 0, 0);
    return fecha;
  }

  private ajustarIndiceDestacado(): void {
    const totalDestacados = this.eventosDestacados.length;
    if (totalDestacados === 0) {
      this.destacadoActualIndex = 0;
      return;
    }
    if (this.destacadoActualIndex >= totalDestacados) {
      this.destacadoActualIndex = totalDestacados - 1;
    }
  }
}
