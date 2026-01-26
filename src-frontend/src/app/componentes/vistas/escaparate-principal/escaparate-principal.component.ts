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
export class EscaparatePrincipalComponent
  implements AfterViewInit, OnInit, OnDestroy
{
  @ViewChild('videoPresentacion')
  videoPresentacion!: ElementRef<HTMLVideoElement>;

  @ViewChildren('infoCard')
  infoCards!: QueryList<ElementRef<HTMLElement>>;

  @ViewChildren('instructorTile')
  instructorTiles!: QueryList<ElementRef<HTMLElement>>;

  usuarioLogueado: boolean = false;

  // Reviews carousel
  currentReview: number = 0;
  totalReviews: number = 10;
  visibleCards: number = 3;
  private reviewInterval: ReturnType<typeof setInterval> | null = null;

  private infoCardObserver: IntersectionObserver | null = null;

  private instructorObserver: IntersectionObserver | null = null;

  private readonly onResize = () => this.updateVisibleCards();

  constructor(
    @Inject(PLATFORM_ID) private readonly platformId: Object,
    private readonly authService: AuthenticationService,
    private readonly router: Router,
    private readonly seoService: SeoService,
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
      window.addEventListener('resize', this.onResize);
      this.startReviewAutoRotation();
    }
  }

  private setReviewSchema(): void {
    const reviews = [
      {
        author: 'K Leal',
        rating: 5,
        text: "Mi hija de 11 finalmente ha encontrado un deporte que le apasiona. Yo también aprovecho y entreno con ella. Las clases son divertidas y con el nivel de exigencia indicado según la edad. Todo el equipo es profesional, amistoso y motiva adecuadamente al alumnado. El Club Moi's Kim Do ofrece la posibilidad de vivir el taekwondo de forma compatible con diferentes objetivos deportivos.",
      },
      {
        author: 'Cristina FRANCO BOURRELLIER',
        rating: 5,
        text: 'Las clases de defensa personal son geniales, aprendemos tecnicas de defensa a la vez que hacemos ejercicios de fuerza. Muy contenta con el grupo y el profesor.',
      },
      {
        author: 'Miguel Marín Rodríguez',
        rating: 5,
        text: 'El mejor dojan donde aprender taekwondo en Umbrete.',
      },
      {
        author: 'F. Javier Vargas',
        rating: 5,
        text: 'Muy buena escuela de artes marciales. Profesionalidad y buen ambiente.',
      },
    ];

    this.seoService.setReviewsSchema(reviews);
  }

  ngOnDestroy(): void {
    this.stopReviewAutoRotation();

    if (isPlatformBrowser(this.platformId)) {
      window.removeEventListener('resize', this.onResize);
    }

    this.infoCardObserver?.disconnect();
    this.infoCardObserver = null;
    this.instructorObserver?.disconnect();
    this.instructorObserver = null;
  }

  // Reviews carousel methods
  get totalPages(): number {
    return Math.ceil(this.totalReviews / this.visibleCards);
  }

  get translateX(): number {
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

    if (this.currentReview >= this.totalReviews - this.visibleCards + 1) {
      this.currentReview = Math.max(0, this.totalReviews - this.visibleCards);
    }
  }

  nextReview(): void {
    const maxIndex = this.totalReviews - this.visibleCards;
    this.currentReview =
      this.currentReview >= maxIndex ? 0 : this.currentReview + 1;
    this.resetAutoRotation();
  }

  prevReview(): void {
    const maxIndex = this.totalReviews - this.visibleCards;
    this.currentReview =
      this.currentReview <= 0 ? maxIndex : this.currentReview - 1;
    this.resetAutoRotation();
  }

  goToReview(index: number): void {
    this.currentReview = index;
    this.resetAutoRotation();
  }

  private startReviewAutoRotation(): void {
    this.reviewInterval = setInterval(() => {
      const maxIndex = this.totalReviews - this.visibleCards;
      this.currentReview =
        this.currentReview >= maxIndex ? 0 : this.currentReview + 1;
    }, 6000);
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
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const video: HTMLVideoElement = this.videoPresentacion.nativeElement;

    // Asegurar que el video esté muted ANTES de cualquier reproducción
    video.muted = true;
    video.defaultMuted = true;
    video.volume = 0;
    video.setAttribute('playsinline', 'true');
    video.setAttribute('webkit-playsinline', 'true');
    video.setAttribute('disablepictureinpicture', 'true');

    video.addEventListener('canplaythrough', () => {
      video.muted = true;
      video.volume = 0;
      video.play().catch((error) => {
        console.log('Autoplay prevented:', error);
      });
    });

    // Observer exclusivo para info-cards (card reveal)
    const cardOptions: IntersectionObserverInit = {
      root: null,
      rootMargin: '0px 0px -10% 0px',
      threshold: 0.2,
    };

    this.infoCardObserver = new IntersectionObserver((entries, obs) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          (entry.target as HTMLElement).classList.add('is-visible');
          obs.unobserve(entry.target);
        }
      });
    }, cardOptions);

    this.infoCards.forEach((card) => {
      this.infoCardObserver?.observe(card.nativeElement);
    });

    const instructorOptions: IntersectionObserverInit = {
      root: null,
      rootMargin: '0px 0px -10% 0px',
      threshold: 0.2,
    };

    this.instructorObserver = new IntersectionObserver((entries, obs) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          (entry.target as HTMLElement).classList.add('is-visible');
          obs.unobserve(entry.target);
        }
      });
    }, instructorOptions);

    this.instructorTiles.forEach((tile) => {
      this.instructorObserver?.observe(tile.nativeElement);
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
