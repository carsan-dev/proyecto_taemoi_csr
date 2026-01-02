import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, PLATFORM_ID, Inject, ElementRef, ViewChild } from '@angular/core';
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
  @ViewChild('carouselTrack') carouselTrack!: ElementRef<HTMLElement>;

  eventos: any[] = [];
  isLoading: boolean = true;
  currentIndex: number = 0;
  autoPlayInterval: any;
  isDragging: boolean = false;
  startX: number = 0;
  scrollLeft: number = 0;

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
    {
      titulo: 'Evento 4',
      descripcion: 'Descripción del evento 4',
      fotoEvento: {
        tipo: 'image/webp',
        datos: '../../../../assets/media/default.webp',
      },
    },
    {
      titulo: 'Evento 5',
      descripcion: 'Descripción del evento 5',
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
        if (isPlatformBrowser(this.platformId)) {
          this.startAutoPlay();
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
  }

  nextSlide(): void {
    if (this.currentIndex < this.eventos.length - 1) {
      this.currentIndex++;
      this.scrollToSlide();
    }
  }

  prevSlide(): void {
    if (this.currentIndex > 0) {
      this.currentIndex--;
      this.scrollToSlide();
    }
  }

  goToSlide(index: number): void {
    this.currentIndex = index;
    this.scrollToSlide();
  }

  scrollToSlide(): void {
    if (isPlatformBrowser(this.platformId) && this.carouselTrack) {
      const track = this.carouselTrack.nativeElement;
      const slideWidth = track.children[0]?.clientWidth || 0;
      const gap = 24; // 1.5rem gap
      track.scrollTo({
        left: (slideWidth + gap) * this.currentIndex,
        behavior: 'smooth'
      });
    }
  }

  startAutoPlay(): void {
    this.autoPlayInterval = setInterval(() => {
      if (this.currentIndex < this.eventos.length - 1) {
        this.currentIndex++;
      } else {
        this.currentIndex = 0;
      }
      this.scrollToSlide();
    }, 5000);
  }

  stopAutoPlay(): void {
    if (this.autoPlayInterval) {
      clearInterval(this.autoPlayInterval);
    }
  }

  pauseAutoPlay(): void {
    this.stopAutoPlay();
  }

  resumeAutoPlay(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.startAutoPlay();
    }
  }

  onMouseDown(event: MouseEvent): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.isDragging = true;
    this.startX = event.pageX - this.carouselTrack.nativeElement.offsetLeft;
    this.scrollLeft = this.carouselTrack.nativeElement.scrollLeft;
    this.pauseAutoPlay();
  }

  onMouseMove(event: MouseEvent): void {
    if (!this.isDragging || !isPlatformBrowser(this.platformId)) return;
    event.preventDefault();
    const x = event.pageX - this.carouselTrack.nativeElement.offsetLeft;
    const walk = (x - this.startX) * 2;
    this.carouselTrack.nativeElement.scrollLeft = this.scrollLeft - walk;
  }

  onMouseUp(): void {
    this.isDragging = false;
    this.resumeAutoPlay();
  }

  getImageUrl(evento: any): string {
    return evento.fotoEvento?.url || '../../../../assets/media/default.webp';
  }
}
