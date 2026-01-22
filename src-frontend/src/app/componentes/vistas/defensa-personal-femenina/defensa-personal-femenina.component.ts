import { isPlatformBrowser } from '@angular/common';
import {
  AfterViewInit,
  OnDestroy,
  Component,
  ElementRef,
  Inject,
  PLATFORM_ID,
  QueryList,
  ViewChildren,
} from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-defensa-personal-femenina',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './defensa-personal-femenina.component.html',
  styleUrl: './defensa-personal-femenina.component.scss',
})
export class DefensaPersonalFemeninaComponent implements AfterViewInit, OnDestroy {
  usuarioLogueado: boolean = false;

  @ViewChildren('infoCard')
  infoCards!: QueryList<ElementRef<HTMLElement>>;

  private infoCardObserver: IntersectionObserver | null = null;

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
  }

  ngOnDestroy(): void {
    this.infoCardObserver?.disconnect();
    this.infoCardObserver = null;
  }
}
