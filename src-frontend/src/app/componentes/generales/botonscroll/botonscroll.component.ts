import { AfterViewInit, Component, HostListener, NgZone, OnDestroy, OnInit } from '@angular/core';

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
  private documentScrollHandler?: (event: Event) => void;

  constructor(private readonly zone: NgZone) {}

  ngOnInit(): void {
    this.updateVisibility();
  }

  ngAfterViewInit(): void {
    if (globalThis.document === undefined) {
      return;
    }

    const doc = globalThis.document;
    this.documentScrollHandler = (event: Event) => {
      this.zone.run(() => this.updateVisibility(event));
    };
    doc.addEventListener('scroll', this.documentScrollHandler, true);
  }

  ngOnDestroy(): void {
    if (this.documentScrollHandler && globalThis.document !== undefined) {
      const doc = globalThis.document;
      doc.removeEventListener('scroll', this.documentScrollHandler, true);
      this.documentScrollHandler = undefined;
    }
  }

  scrollArriba(): void {
    if (globalThis.window === undefined || globalThis.document === undefined) {
      return;
    }

    this.scrollWindowToTop();
  }

  @HostListener('window:scroll')
  onWindowScroll(): void {
    this.updateVisibility();
  }

  private updateVisibility(event?: Event): void {
    if (globalThis.window === undefined || globalThis.document === undefined) {
      return;
    }

    const shouldShow = this.getEffectiveScrollTop(event) > this.scrollThreshold;
    if (shouldShow === this.mostrarBoton) {
      return;
    }

    this.mostrarBoton = shouldShow;
  }

  private getWindowScrollTop(): number {
    const win = globalThis.window;
    const doc = globalThis.document;
    return win.scrollY || doc.documentElement.scrollTop || doc.body.scrollTop || 0;
  }

  private getEffectiveScrollTop(event?: Event): number {
    const doc = globalThis.document;
    const target = event?.target;
    const targetScrollTop = target instanceof HTMLElement ? target.scrollTop : 0;
    const scrollingElement = doc.scrollingElement as HTMLElement | null;
    const scrollingElementTop = scrollingElement?.scrollTop ?? 0;
    const windowScrollTop = this.getWindowScrollTop();

    return Math.max(targetScrollTop, scrollingElementTop, windowScrollTop);
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
