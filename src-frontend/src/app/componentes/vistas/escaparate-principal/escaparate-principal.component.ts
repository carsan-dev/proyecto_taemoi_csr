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
  OnDestroy,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { SliderTocableComponent } from '../../generales/carousel/slider-tocable/slider-tocable.component';
import { MapaComponent } from '../../generales/mapa/mapa.component';
import { Router, RouterLink } from '@angular/router';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { SeoService } from '../../../servicios/generales/seo.service';

@Component({
  selector: 'app-escaparate-principal',
  standalone: true,
  imports: [CommonModule, SliderTocableComponent, MapaComponent, RouterLink],
  templateUrl: './escaparate-principal.component.html',
  styleUrl: './escaparate-principal.component.scss',
})
export class EscaparatePrincipalComponent implements AfterViewInit, OnInit, OnDestroy {
  @ViewChild('videoPresentacion')
  videoPresentacion!: ElementRef<HTMLVideoElement>;

  @ViewChildren('animatedImage')
  images!: QueryList<ElementRef>;

  usuarioLogueado: boolean = false;

  // Reviews carousel
  currentReview: number = 0;
  totalReviews: number = 10;
  visibleCards: number = 3;
  private reviewInterval: ReturnType<typeof setInterval> | null = null;

  constructor(
    @Inject(PLATFORM_ID) private readonly platformId: Object,
    private readonly authService: AuthenticationService,
    private readonly router: Router,
    private readonly seoService: SeoService
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

    // Add Review Schema for SEO
    this.setReviewSchema();

    // Start reviews carousel auto-rotation
    if (isPlatformBrowser(this.platformId)) {
      this.updateVisibleCards();
      window.addEventListener('resize', this.updateVisibleCards.bind(this));
      this.startReviewAutoRotation();
    }
  }

  private setReviewSchema(): void {
    const reviews = [
      {
        author: 'K Leal',
        rating: 5,
        text: 'Mi hija de 11 finalmente ha encontrado un deporte que le apasiona. Yo también aprovecho y entreno con ella. Las clases son divertidas y con el nivel de exigencia indicado según la edad. Todo el equipo es profesional, amistoso y motiva adecuadamente al alumnado. El Club Moi\'s Kim Do ofrece la posibilidad de vivir el taekwondo de forma compatible con diferentes objetivos deportivos.'
      },
      {
        author: 'Carlos Sánchez',
        rating: 5,
        text: 'La mejor escuela de artes marciales de Umbrete y del Aljarafe sin lugar a dudas. Llevo muchos años haciendo taekwondo allí y recientemente me apunté a kickboxing y a pilates. El kickboxing light está súper bien y el pilates balance me ha venido genial para descargar tensión y fortalecer el core. Si buscas instructores profesionales y calidad en el servicio, Moi\'s Kim Do es tu sitio.'
      },
      {
        author: 'Miguel Marín Rodríguez',
        rating: 5,
        text: 'El mejor dojan donde aprender taekwondo en Umbrete.'
      },
      {
        author: 'F. Javier Vargas',
        rating: 5,
        text: 'Muy buena escuela de artes marciales. Profesionalidad y buen ambiente.'
      }
    ];

    this.seoService.setReviewsSchema(reviews);
  }

  ngOnDestroy(): void {
    this.stopReviewAutoRotation();
    if (isPlatformBrowser(this.platformId)) {
      window.removeEventListener('resize', this.updateVisibleCards.bind(this));
    }
  }

  // Reviews carousel methods
  get totalPages(): number {
    return Math.ceil(this.totalReviews / this.visibleCards);
  }

  get translateX(): number {
    // Each card takes up (100 / visibleCards)% of the visible area
    // We move by one card width at a time
    const cardWidthPercent = 100 / this.visibleCards;
    return -(this.currentReview * cardWidthPercent);
  }

  updateVisibleCards(): void {
    const width = window.innerWidth;
    if (width >= 1024) {
      this.visibleCards = 3;
    } else if (width >= 768) {
      this.visibleCards = 2;
    } else {
      this.visibleCards = 1;
    }
    // Reset to valid position if needed
    if (this.currentReview >= this.totalReviews - this.visibleCards + 1) {
      this.currentReview = Math.max(0, this.totalReviews - this.visibleCards);
    }
  }

  nextReview(): void {
    const maxIndex = this.totalReviews - this.visibleCards;
    this.currentReview = this.currentReview >= maxIndex ? 0 : this.currentReview + 1;
    this.resetAutoRotation();
  }

  prevReview(): void {
    const maxIndex = this.totalReviews - this.visibleCards;
    this.currentReview = this.currentReview <= 0 ? maxIndex : this.currentReview - 1;
    this.resetAutoRotation();
  }

  goToReview(index: number): void {
    this.currentReview = index;
    this.resetAutoRotation();
  }

  private startReviewAutoRotation(): void {
    this.reviewInterval = setInterval(() => {
      const maxIndex = this.totalReviews - this.visibleCards;
      this.currentReview = this.currentReview >= maxIndex ? 0 : this.currentReview + 1;
    }, 6000); // Change review every 6 seconds
  }

  private stopReviewAutoRotation(): void {
    if (this.reviewInterval) {
      clearInterval(this.reviewInterval);
      this.reviewInterval = null;
    }
  }

  private resetAutoRotation(): void {
    this.stopReviewAutoRotation();
    this.startReviewAutoRotation();
  }

  ngAfterViewInit(): void {
    const video: HTMLVideoElement = this.videoPresentacion.nativeElement;

    // Asegurar que el video esté muted ANTES de cualquier reproducción
    video.muted = true;
    video.defaultMuted = true;
    video.volume = 0;
    video.setAttribute('playsinline', 'true');
    video.setAttribute('webkit-playsinline', 'true');
    video.setAttribute('disablepictureinpicture', 'true');

    video.addEventListener('canplaythrough', () => {
      // Asegurar nuevamente que está muted antes de reproducir
      video.muted = true;
      video.volume = 0;
      video.play().catch(error => {
        // Si falla el autoplay, intentar reproducir después de interacción del usuario
        console.log('Autoplay prevented:', error);
      });
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
