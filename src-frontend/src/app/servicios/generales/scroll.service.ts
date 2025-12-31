import { Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class ScrollService {
  constructor(private router: Router) {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.scrollToTopInstant();
      });
  }

  /**
   * Scrolls to the top of the window smoothly
   */
  scrollToTop(behavior: ScrollBehavior = 'smooth'): void {
    const doc = document;
    const win = window;
    const scrollingElement = doc.scrollingElement as HTMLElement | null;

    this.scrollElementToTop(scrollingElement, behavior);
    this.scrollElementToTop(doc.documentElement, behavior);
    this.scrollElementToTop(doc.body, behavior);

    const content = doc.getElementById('content');
    if (content) {
      this.scrollElementToTop(content, behavior);
    }

    win.scrollTo({
      top: 0,
      left: 0,
      behavior: behavior,
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
      behavior: behavior,
    });
  }

  private scrollElementToTop(element: HTMLElement | null, behavior: ScrollBehavior): void {
    if (!element) {
      return;
    }

    if (typeof element.scrollTo === 'function') {
      element.scrollTo({ top: 0, left: 0, behavior });
    } else {
      element.scrollTop = 0;
    }
  }
}
