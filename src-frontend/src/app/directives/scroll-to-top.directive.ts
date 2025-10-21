import { Directive, HostListener, Input } from '@angular/core';
import { ScrollService } from '../servicios/generales/scroll.service';

@Directive({
  selector: '[appScrollToTop]',
  standalone: true,
})
export class ScrollToTopDirective {
  /**
   * Delay in milliseconds before scrolling (useful for waiting for animations or SweetAlert to close)
   * Default: 100ms
   */
  @Input() scrollDelay = 100;

  /**
   * Scroll behavior: 'smooth' or 'auto' (instant)
   * Default: 'smooth'
   */
  @Input() scrollBehavior: ScrollBehavior = 'smooth';

  constructor(private scrollService: ScrollService) {}

  @HostListener('click')
  onClick(): void {
    setTimeout(() => {
      if (this.scrollBehavior === 'auto') {
        this.scrollService.scrollToTopInstant();
      } else {
        this.scrollService.scrollToTop(this.scrollBehavior);
      }
    }, this.scrollDelay);
  }
}
