import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, PLATFORM_ID, Inject, HostListener } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
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
  autoPlayInterval: any;
  userInteracted: boolean = false;
  isHovered: boolean = false;

  defaultFotos: any[] = [
    {
      titulo: 'Evento 1',
      descripcion: 'Descripción del evento 1',
      fotoEvento: {
        tipo: 'image/webp',
        datos: '../../../../assets/media/default.webp',
      },
    },
    {
      titulo: 'Evento 2',
      descripcion: 'Descripción del evento 2',
      fotoEvento: {
        tipo: 'image/webp',
        datos: '../../../../assets/media/default.webp',
      },
    },
    {
      titulo: 'Evento 3',
      descripcion: 'Descripción del evento 3',
      fotoEvento: {
        tipo: 'image/webp',
        datos: '../../../../assets/media/default.webp',
      },
    },
  ];
  private readonly subscriptions: Subscription = new Subscription();

  constructor(
    public endpointsService: EndpointsService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  ngOnInit(): void {
    const eventosSubscription = this.endpointsService.eventos$.subscribe({
      next: (eventos) => {
        this.eventos = eventos.length ? eventos.slice(0, 5) : this.defaultFotos;
        this.isLoading = false;
        // Solo iniciar autoplay si hay más de 1 evento
        if (isPlatformBrowser(this.platformId) && this.eventos.length > 1) {
          this.startAutoPlay();
          this.setupVisibilityListener();
        }
      },
      error: (error) => {
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
    this.stopAutoPlay();
    if (isPlatformBrowser(this.platformId)) {
      document.removeEventListener('visibilitychange', this.handleVisibilityChange);
    }
  }

  // Page Visibility API - pausar cuando el tab no está visible
  private setupVisibilityListener(): void {
    if (isPlatformBrowser(this.platformId)) {
      document.addEventListener('visibilitychange', this.handleVisibilityChange);
    }
  }

  private handleVisibilityChange = (): void => {
    if (document.hidden) {
      this.stopAutoPlay();
    } else if (!this.userInteracted && !this.isHovered && this.eventos.length > 1) {
      this.startAutoPlay();
    }
  };

  nextSlide(): void {
    this.userInteracted = true;
    this.stopAutoPlay();
    if (this.currentIndex < this.eventos.length - 1) {
      this.currentIndex++;
    } else {
      this.currentIndex = 0;
    }
  }

  prevSlide(): void {
    this.userInteracted = true;
    this.stopAutoPlay();
    if (this.currentIndex > 0) {
      this.currentIndex--;
    } else {
      this.currentIndex = this.eventos.length - 1;
    }
  }

  goToSlide(index: number): void {
    this.userInteracted = true;
    this.stopAutoPlay();
    this.currentIndex = index;
  }

  startAutoPlay(): void {
    // Limpiar cualquier interval existente primero
    this.stopAutoPlay();

    this.autoPlayInterval = setInterval(() => {
      // Solo avanzar si no hay interacción del usuario y no está en hover
      if (!this.userInteracted && !this.isHovered) {
        if (this.currentIndex < this.eventos.length - 1) {
          this.currentIndex++;
        } else {
          this.currentIndex = 0;
        }
      }
    }, 8000); // 8 segundos - más tiempo para leer
  }

  stopAutoPlay(): void {
    if (this.autoPlayInterval) {
      clearInterval(this.autoPlayInterval);
      this.autoPlayInterval = null;
    }
  }

  pauseAutoPlay(): void {
    this.isHovered = true;
    this.stopAutoPlay();
  }

  resumeAutoPlay(): void {
    this.isHovered = false;
    // Solo reanudar si el usuario NO ha interactuado manualmente
    if (isPlatformBrowser(this.platformId) && !this.userInteracted && this.eventos.length > 1) {
      this.startAutoPlay();
    }
  }

  getImageUrl(evento: any): string {
    return evento.fotoEvento?.url || '../../../../assets/media/default.webp';
  }
}
