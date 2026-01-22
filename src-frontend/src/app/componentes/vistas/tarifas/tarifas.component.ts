import {
  Component,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  ViewChildren,
  QueryList,
  Inject,
  PLATFORM_ID,
} from '@angular/core';
import { isPlatformBrowser, CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-tarifas',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './tarifas.component.html',
  styleUrl: './tarifas.component.scss',
})
export class TarifasComponent implements AfterViewInit, OnDestroy {
  @ViewChildren('revealCard')
  revealCards!: QueryList<ElementRef<HTMLElement>>;

  private cardObserver: IntersectionObserver | null = null;

  constructor(@Inject(PLATFORM_ID) private readonly platformId: Object) {}

  ngAfterViewInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

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

    this.revealCards.forEach((card) => {
      const rect = card.nativeElement.getBoundingClientRect();
      const isInViewport = rect.top < window.innerHeight && rect.bottom > 0;
      if (isInViewport) {
        card.nativeElement.classList.add('is-visible', 'no-animation');
      }
    });

    this.cardObserver = new IntersectionObserver((entries, obs) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          (entry.target as HTMLElement).classList.add('is-visible');
          obs.unobserve(entry.target);
        }
      });
    }, cardOptions);

    this.revealCards.forEach((card) => {
      if (!card.nativeElement.classList.contains('is-visible')) {
        this.cardObserver?.observe(card.nativeElement);
      }
    });
  }

  ngOnDestroy(): void {
    this.cardObserver?.disconnect();
    this.cardObserver = null;
  }
}
