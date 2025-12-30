import {
  AfterViewInit,
  Component,
  ElementRef,
  ViewChild,
  ViewChildren,
  QueryList,
  Inject,
  PLATFORM_ID,
  OnInit,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { SliderTocableComponent } from '../../generales/carousel/slider-tocable/slider-tocable.component';
import { MapaComponent } from '../../generales/mapa/mapa.component';
import { Router, RouterLink } from '@angular/router';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';

@Component({
  selector: 'app-escaparate-principal',
  standalone: true,
  imports: [CommonModule, SliderTocableComponent, MapaComponent, RouterLink],
  templateUrl: './escaparate-principal.component.html',
  styleUrl: './escaparate-principal.component.scss',
})
export class EscaparatePrincipalComponent implements AfterViewInit, OnInit {
  @ViewChild('videoPresentacion')
  videoPresentacion!: ElementRef<HTMLVideoElement>;

  @ViewChildren('animatedImage')
  images!: QueryList<ElementRef>;

  usuarioLogueado: boolean = false;

  constructor(
    @Inject(PLATFORM_ID) private readonly platformId: Object,
    private readonly authService: AuthenticationService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado) => {
      this.usuarioLogueado = estado;
      this.redirigirSiAdmin(this.authService.getRolesActuales());
    });
    this.authService.rolesCambio.subscribe((roles) => {
      this.redirigirSiAdmin(roles);
    });
  }

  ngAfterViewInit(): void {
    const video: HTMLVideoElement = this.videoPresentacion.nativeElement;

    video.addEventListener('canplaythrough', () => {
      video.muted = true;
      video.setAttribute('playsinline', 'true');
      video.setAttribute('webkit-playsinline', 'true');
      video.setAttribute('disablepictureinpicture', 'true');
      video.play();
    });

    if (isPlatformBrowser(this.platformId)) {
      const options = {
        root: null,
        rootMargin: '0px',
        threshold: 0.1,
      };

      const observer = new IntersectionObserver(
        this.handleIntersection.bind(this),
        options
      );

      this.images.forEach((image) => {
        observer.observe(image.nativeElement);
      });
    }
  }

  handleIntersection(
    entries: IntersectionObserverEntry[],
    observer: IntersectionObserver
  ): void {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        (entry.target as HTMLElement).classList.add('fade-in');
        observer.unobserve(entry.target);
      }
    });
  }

  scrollToMap() {
    const mapSection = document.getElementById('map-section');
    if (mapSection) {
      mapSection.scrollIntoView({ behavior: 'smooth' });
    }
  }

  private redirigirSiAdmin(roles: string[]): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    if (!this.usuarioLogueado) {
      return;
    }
    if (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')) {
      this.router.navigate(['/adminpage']);
    }
  }
}
