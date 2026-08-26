import { ApplicationRef, Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class ScrollService {
  constructor(private readonly applicationRef: ApplicationRef) {}

  /**
   * Scrolls to the top of the window smoothly
   */
  scrollToTop(behavior: ScrollBehavior = 'smooth'): void {
    const scrollingElement = document.scrollingElement as HTMLElement | null;

    if (behavior === 'auto') {
      // Direct assignment bypasses Bootstrap's global `scroll-behavior: smooth`.
      if (scrollingElement) {
        scrollingElement.scrollTop = 0;
        scrollingElement.scrollLeft = 0;
      }
      return;
    }

    window.scrollTo({ top: 0, left: 0, behavior });
  }

  /**
   * Waits until Angular has rendered a state change before scrolling.
   */
  async scrollToTopAfterRender(behavior: ScrollBehavior = 'auto'): Promise<void> {
    await this.applicationRef.whenStable();
    await new Promise<void>((resolve) => {
      const scroll = () => {
        this.scrollToTop(behavior);
        resolve();
      };

      if (typeof requestAnimationFrame === 'function') {
        requestAnimationFrame(scroll);
      } else {
        scroll();
      }
    });
  }

  /**
   * Scrolls to the top instantly (no animation)
   */
  scrollToTopInstant(): void {
    this.scrollToTop('auto');
  }

  /**
   * Scrolls to a specific element by ID
   */
  scrollToElement(elementId: string, behavior: ScrollBehavior = 'smooth'): void {
    const element = document.getElementById(elementId);
    if (element) {
      element.scrollIntoView({ behavior, block: 'start' });
    }
  }

  /**
   * Scrolls to a specific Y position
   */
  scrollToPosition(y: number, behavior: ScrollBehavior = 'smooth'): void {
    window.scrollTo({
      top: y,
      left: 0,
      behavior,
    });
  }
}
