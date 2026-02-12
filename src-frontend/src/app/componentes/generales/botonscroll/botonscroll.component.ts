import { AfterViewInit, Component, NgZone, OnDestroy, OnInit } from '@angular/core';

@Component({
  selector: 'app-botonscroll',
  standalone: true,
  imports: [],
  templateUrl: './botonscroll.component.html',
  styleUrl: './botonscroll.component.scss'
})
export class BotonscrollComponent implements OnInit, AfterViewInit, OnDestroy {
  mostrarBoton = false;
  private readonly scrollThreshold = 100;
  private windowScrollHandler?: () => void;
  private scrollTicking = false;

  constructor(private readonly zone: NgZone) {}

  ngOnInit(): void {
    this.updateVisibility();
  }

  ngAfterViewInit(): void {
    if (globalThis.window === undefined) {
      return;
    }

    const win = globalThis.window;
    this.zone.runOutsideAngular(() => {
      this.windowScrollHandler = () => this.programarActualizacion();
      win.addEventListener('scroll', this.windowScrollHandler, { passive: true });
    });
  }

  ngOnDestroy(): void {
    if (this.windowScrollHandler && globalThis.window !== undefined) {
      const win = globalThis.window;
      win.removeEventListener('scroll', this.windowScrollHandler);
      this.windowScrollHandler = undefined;
    }
  }

  scrollArriba(): void {
    if (globalThis.window === undefined || globalThis.document === undefined) {
      return;
    }

    this.scrollWindowToTop();
  }

  private programarActualizacion(): void {
    if (this.scrollTicking) {
      return;
    }

    this.scrollTicking = true;
    requestAnimationFrame(() => {
      this.scrollTicking = false;
      this.updateVisibility();
    });
  }

  private updateVisibility(): void {
    if (globalThis.window === undefined || globalThis.document === undefined) {
      return;
    }

    const shouldShow = this.getWindowScrollTop() > this.scrollThreshold;
    if (shouldShow === this.mostrarBoton) {
      return;
    }

    this.zone.run(() => {
      this.mostrarBoton = shouldShow;
    });
  }

  private getWindowScrollTop(): number {
    const win = globalThis.window;
    const doc = globalThis.document;
    return win.scrollY || doc.documentElement.scrollTop || doc.body.scrollTop || 0;
  }

  private scrollWindowToTop(): void {
    const doc = globalThis.document;
    const win = globalThis.window;
    const behavior = this.getScrollBehavior();
    const scrollingElement = doc.scrollingElement as HTMLElement | null;
    if (scrollingElement) {
      this.scrollElementToTop(scrollingElement, behavior);
    }

    if (doc.documentElement) {
      this.scrollElementToTop(doc.documentElement, behavior);
    }
    if (doc.body) {
      this.scrollElementToTop(doc.body, behavior);
    }

    win.scrollTo({ top: 0, behavior });
  }

  private scrollElementToTop(element: HTMLElement, behavior: ScrollBehavior): void {
    if (typeof element.scrollTo === 'function') {
      element.scrollTo({ top: 0, behavior });
    } else {
      element.scrollTop = 0;
    }
  }

  private getScrollBehavior(): ScrollBehavior {
    const win = globalThis.window;
    if (win === undefined || win.matchMedia === undefined) {
      return 'smooth';
    }

    return win.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
  }
}
