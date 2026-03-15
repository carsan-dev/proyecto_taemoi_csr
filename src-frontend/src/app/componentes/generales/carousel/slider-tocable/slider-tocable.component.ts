import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  Component,
  Inject,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';

@Component({
  selector: 'app-slider-tocable',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './slider-tocable.component.html',
  styleUrl: './slider-tocable.component.scss',
})
export class SliderTocableComponent implements OnInit, OnDestroy {
  eventos: any[] = [];
  isLoading: boolean = true;
  currentIndex: number = 0;
  visibleCards: number = 3;
  isAutoPaused: boolean = false;

  private readonly subscriptions: Subscription = new Subscription();
  private readonly onResize = () => this.updateVisibleCards();
  private autoRotationInterval: ReturnType<typeof setInterval> | null = null;

  defaultFotos: any[] = [
    {
      id: 1,
      titulo: 'Evento de ejemplo 1',
      descripcion: 'Descripcion del evento de ejemplo 1',
      fotoEvento: { url: '../../../../assets/media/default.webp' },
    },
    {
      id: 2,
      titulo: 'Evento de ejemplo 2',
      descripcion: 'Descripcion del evento de ejemplo 2',
      fotoEvento: { url: '../../../../assets/media/default.webp' },
    },
    {
      id: 3,
      titulo: 'Evento de ejemplo 3',
      descripcion: 'Descripcion del evento de ejemplo 3',
      fotoEvento: { url: '../../../../assets/media/default.webp' },
    },
  ];

  constructor(
    public endpointsService: EndpointsService,
    @Inject(PLATFORM_ID) private readonly platformId: Object
  ) {}

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.updateVisibleCards();
      window.addEventListener('resize', this.onResize);
    }

    const eventosSubscription = this.endpointsService.eventos$.subscribe({
      next: (eventos) => {
        this.eventos = eventos.length ? eventos.slice(0, 6) : this.defaultFotos;
        this.isLoading = false;
        this.adjustIndex();
        this.syncAutoRotation();
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No hemos podido obtener los eventos.',
          icon: 'error',
        });
        this.eventos = this.defaultFotos;
        this.isLoading = false;
        this.adjustIndex();
        this.syncAutoRotation();
      },
    });

    this.subscriptions.add(eventosSubscription);
    this.endpointsService.obtenerEventos();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.stopAutoRotation();
    if (isPlatformBrowser(this.platformId)) {
      window.removeEventListener('resize', this.onResize);
    }
  }

  get translateX(): number {
    const cardWidthPercent = 100 / this.visibleCards;
    return -(this.currentIndex * cardWidthPercent);
  }

  get canAutoRotate(): boolean {
    return this.eventos.length > this.visibleCards;
  }

  updateVisibleCards(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const width = window.innerWidth;
    if (width >= 1024) {
      this.visibleCards = 3;
    } else if (width >= 768) {
      this.visibleCards = 2;
    } else {
      this.visibleCards = 1;
    }

    this.adjustIndex();
    this.syncAutoRotation();
  }

  private adjustIndex(): void {
    const maxIndex = Math.max(0, this.eventos.length - this.visibleCards);
    if (this.currentIndex > maxIndex) {
      this.currentIndex = maxIndex;
    }
  }

  getDotIndices(): number[] {
    const maxIndex = Math.max(0, this.eventos.length - this.visibleCards);
    return Array.from({ length: maxIndex + 1 }, (_, i) => i);
  }

  toggleAutoRotation(): void {
    this.isAutoPaused = !this.isAutoPaused;
    if (this.isAutoPaused) {
      this.stopAutoRotation();
      return;
    }
    this.startAutoRotation();
  }

  nextSlide(): void {
    this.advanceToNextSlide();
    this.restartAutoRotation();
  }

  prevSlide(): void {
    const maxIndex = Math.max(0, this.eventos.length - this.visibleCards);
    this.currentIndex = this.currentIndex <= 0 ? maxIndex : this.currentIndex - 1;
    this.restartAutoRotation();
  }

  goToSlide(index: number): void {
    this.currentIndex = index;
    this.restartAutoRotation();
  }

  getImageUrl(evento: any): string {
    const fallback = '../../../../assets/media/default.webp';
    const rawUrl = evento?.fotoEvento?.url;
    if (!rawUrl) {
      return fallback;
    }

    const version = String(evento?.fotoEvento?.id ?? evento?.fotoEvento?.nombre ?? '0');
    let url = this.actualizarParametroUrl(rawUrl, 'w', '900');
    url = this.actualizarParametroUrl(url, 'v', version);
    return url;
  }

  private advanceToNextSlide(): void {
    const maxIndex = Math.max(0, this.eventos.length - this.visibleCards);
    this.currentIndex = this.currentIndex >= maxIndex ? 0 : this.currentIndex + 1;
  }

  private startAutoRotation(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    if (this.isAutoPaused || this.autoRotationInterval || !this.canAutoRotate) {
      return;
    }

    this.autoRotationInterval = setInterval(() => {
      this.advanceToNextSlide();
    }, 6500);
  }

  private stopAutoRotation(): void {
    if (this.autoRotationInterval) {
      clearInterval(this.autoRotationInterval);
      this.autoRotationInterval = null;
    }
  }

  private restartAutoRotation(): void {
    if (this.isAutoPaused || !this.canAutoRotate) {
      return;
    }
    this.stopAutoRotation();
    this.startAutoRotation();
  }

  private syncAutoRotation(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    if (!this.canAutoRotate) {
      this.stopAutoRotation();
      this.currentIndex = 0;
      return;
    }
    if (this.isAutoPaused) {
      this.stopAutoRotation();
      return;
    }
    this.startAutoRotation();
  }

  private actualizarParametroUrl(url: string, key: string, value: string): string {
    const valueSeguro = encodeURIComponent(value);
    const regex = new RegExp(`([?&])${key}=[^&]*`);
    if (regex.test(url)) {
      return url.replace(regex, `$1${key}=${valueSeguro}`);
    }
    const separador = url.includes('?') ? '&' : '?';
    return `${url}${separador}${key}=${valueSeguro}`;
  }
}
