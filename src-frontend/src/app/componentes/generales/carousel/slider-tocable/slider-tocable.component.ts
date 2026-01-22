import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  Component,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  Inject,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { Subscription } from 'rxjs/internal/Subscription';

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

  private readonly subscriptions: Subscription = new Subscription();
  private readonly onResize = () => this.updateVisibleCards();

  defaultFotos: any[] = [
    {
      id: 1,
      titulo: 'Evento de ejemplo 1',
      descripcion: 'Descripción del evento de ejemplo 1',
      fotoEvento: { url: '../../../../assets/media/default.webp' },
    },
    {
      id: 2,
      titulo: 'Evento de ejemplo 2',
      descripcion: 'Descripción del evento de ejemplo 2',
      fotoEvento: { url: '../../../../assets/media/default.webp' },
    },
    {
      id: 3,
      titulo: 'Evento de ejemplo 3',
      descripcion: 'Descripción del evento de ejemplo 3',
      fotoEvento: { url: '../../../../assets/media/default.webp' },
    },
  ];

  constructor(
    public endpointsService: EndpointsService,
    @Inject(PLATFORM_ID) private platformId: Object
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
        // Ajustar índice si es necesario
        this.adjustIndex();
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No hemos podido obtener los eventos.',
          icon: 'error',
        });
        this.eventos = this.defaultFotos;
        this.isLoading = false;
      },
    });

    this.subscriptions.add(eventosSubscription);
    this.endpointsService.obtenerEventos();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    if (isPlatformBrowser(this.platformId)) {
      window.removeEventListener('resize', this.onResize);
    }
  }

  // Calcula el translateX como porcentaje (igual que reviews)
  get translateX(): number {
    const cardWidthPercent = 100 / this.visibleCards;
    return -(this.currentIndex * cardWidthPercent);
  }

  // Actualiza el número de cards visibles según el ancho de pantalla
  updateVisibleCards(): void {
    if (!isPlatformBrowser(this.platformId)) return;

    const width = window.innerWidth;
    if (width >= 1024) {
      this.visibleCards = 3;
    } else if (width >= 768) {
      this.visibleCards = 2;
    } else {
      this.visibleCards = 1;
    }

    this.adjustIndex();
  }

  // Ajusta el índice para que no se salga del rango válido
  private adjustIndex(): void {
    const maxIndex = Math.max(0, this.eventos.length - this.visibleCards);
    if (this.currentIndex > maxIndex) {
      this.currentIndex = maxIndex;
    }
  }

  // Genera los índices para los dots
  getDotIndices(): number[] {
    const maxIndex = Math.max(0, this.eventos.length - this.visibleCards);
    return Array.from({ length: maxIndex + 1 }, (_, i) => i);
  }

  nextSlide(): void {
    const maxIndex = this.eventos.length - this.visibleCards;
    this.currentIndex = this.currentIndex >= maxIndex ? 0 : this.currentIndex + 1;
  }

  prevSlide(): void {
    const maxIndex = this.eventos.length - this.visibleCards;
    this.currentIndex = this.currentIndex <= 0 ? maxIndex : this.currentIndex - 1;
  }

  goToSlide(index: number): void {
    this.currentIndex = index;
  }

  getImageUrl(evento: any): string {
    return evento.fotoEvento?.url || '../../../../assets/media/default.webp';
  }
}
