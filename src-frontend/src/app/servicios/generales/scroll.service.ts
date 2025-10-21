import { Injectable } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class ScrollService {
  constructor(private router: Router) {
    // Automatically scroll to top on route changes
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        this.scrollToTop();
      });
  }

  /**
   * Scrolls to the top of the window smoothly
   */
  scrollToTop(behavior: ScrollBehavior = 'smooth'): void {
    window.scrollTo({
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
}
