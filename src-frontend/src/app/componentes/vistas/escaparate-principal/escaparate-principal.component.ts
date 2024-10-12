import {
  AfterViewInit,
  Component,
  ElementRef,
  ViewChild,
  ViewChildren, 
  QueryList, 
  Inject, 
  PLATFORM_ID
} from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { SliderTocableComponent } from '../../generales/carousel/slider-tocable/slider-tocable.component';
import { HammerModule } from '@angular/platform-browser';
import { MapaComponent } from '../../generales/mapa/mapa.component';
import { Router } from '@angular/router';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service'; // Importar el servicio de eventos

@Component({
  selector: 'app-escaparate-principal',
  standalone: true,
  imports: [CommonModule, SliderTocableComponent, HammerModule, MapaComponent],
  templateUrl: './escaparate-principal.component.html',
  styleUrl: './escaparate-principal.component.scss',
})
export class EscaparatePrincipalComponent implements AfterViewInit {
  @ViewChild('videoPresentacion')
  videoPresentacion!: ElementRef<HTMLVideoElement>;

  @ViewChildren('animatedImage')
  images!: QueryList<ElementRef>;

  usuarioLogueado: boolean = false;

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router,
    private readonly endpointsService: EndpointsService, // Inyectar el servicio de eventos
    @Inject(PLATFORM_ID) private readonly platformId: Object
  ) {}

  ngAfterViewInit(): void {
    const video: HTMLVideoElement = this.videoPresentacion.nativeElement;

    video.addEventListener('canplaythrough', () => {
      video.muted = true;
      video.play();
    });

    if (isPlatformBrowser(this.platformId)) {
      const options = {
        root: null,
        rootMargin: '0px',
        threshold: 0.1
      };

      const observer = new IntersectionObserver(this.handleIntersection.bind(this), options);

      this.images.forEach(image => {
        observer.observe(image.nativeElement);
      });
    }
  }

  handleIntersection(entries: IntersectionObserverEntry[], observer: IntersectionObserver): void {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        (entry.target as HTMLElement).classList.add('fade-in');
        observer.unobserve(entry.target);
      }
    });
  }

  irARuta(ruta: string) {
    this.router.navigate([ruta]);
  }

  scrollToMap() {
    const mapSection = document.getElementById('map-section');
    if (mapSection) {
      mapSection.scrollIntoView({ behavior: 'smooth' });
    }
  }
}
