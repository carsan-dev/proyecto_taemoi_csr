import {
  Component,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  ViewChild,
  ViewChildren,
  QueryList,
  Inject,
  PLATFORM_ID,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-taekwondo',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './taekwondo.component.html',
  styleUrl: './taekwondo.component.scss',
})
export class TaekwondoComponent implements AfterViewInit, OnDestroy {
  usuarioLogueado: boolean = false;

  @ViewChildren('infoCard')
  infoCards!: QueryList<ElementRef<HTMLElement>>;

  @ViewChildren('relatedLink')
  relatedLinks!: QueryList<ElementRef<HTMLElement>>;

  @ViewChild('ctaButton')
  ctaButton!: ElementRef<HTMLElement>;

  private infoCardObserver: IntersectionObserver | null = null;
  private relatedLinkObserver: IntersectionObserver | null = null;
  private ctaObserver: IntersectionObserver | null = null;

  constructor(@Inject(PLATFORM_ID) private readonly platformId: Object) {}

  ngAfterViewInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    // Wait for DOM to be fully rendered after navigation
    requestAnimationFrame(() => {
      this.initScrollReveal();
    });
  }

  private initScrollReveal(): void {
    const cardOptions: IntersectionObserverInit = {
      root: null,
      rootMargin: '0px 0px -10% 0px',
      threshold: 0.2,
    };

    // Check which cards are already visible and show them immediately without animation
    this.infoCards.forEach((card) => {
      const rect = card.nativeElement.getBoundingClientRect();
      const isInViewport = rect.top < window.innerHeight && rect.bottom > 0;
      if (isInViewport) {
        card.nativeElement.classList.add('is-visible', 'no-animation');
      }
    });

    this.infoCardObserver = new IntersectionObserver((entries, obs) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          (entry.target as HTMLElement).classList.add('is-visible');
          obs.unobserve(entry.target);
        }
      });
    }, cardOptions);

    this.infoCards.forEach((card) => {
      if (!card.nativeElement.classList.contains('is-visible')) {
        this.infoCardObserver?.observe(card.nativeElement);
      }
    });

    // Related links observer
    this.relatedLinks.forEach((link) => {
      const rect = link.nativeElement.getBoundingClientRect();
      const isInViewport = rect.top < window.innerHeight && rect.bottom > 0;
      if (isInViewport) {
        link.nativeElement.classList.add('is-visible', 'no-animation');
      }
    });

    this.relatedLinkObserver = new IntersectionObserver((entries, obs) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          (entry.target as HTMLElement).classList.add('is-visible');
          obs.unobserve(entry.target);
        }
      });
    }, cardOptions);

    this.relatedLinks.forEach((link) => {
      if (!link.nativeElement.classList.contains('is-visible')) {
        this.relatedLinkObserver?.observe(link.nativeElement);
      }
    });

    // CTA button observer
    if (this.ctaButton) {
      const rect = this.ctaButton.nativeElement.getBoundingClientRect();
      const isInViewport = rect.top < window.innerHeight && rect.bottom > 0;
      if (isInViewport) {
        this.ctaButton.nativeElement.classList.add('is-visible', 'no-animation');
      } else {
        this.ctaObserver = new IntersectionObserver((entries, obs) => {
          entries.forEach((entry) => {
            if (entry.isIntersecting) {
              (entry.target as HTMLElement).classList.add('is-visible');
              obs.unobserve(entry.target);
            }
          });
        }, cardOptions);
        this.ctaObserver.observe(this.ctaButton.nativeElement);
      }
    }
  }

  ngOnDestroy(): void {
    this.infoCardObserver?.disconnect();
    this.infoCardObserver = null;
    this.relatedLinkObserver?.disconnect();
    this.relatedLinkObserver = null;
    this.ctaObserver?.disconnect();
    this.ctaObserver = null;
  }
}
