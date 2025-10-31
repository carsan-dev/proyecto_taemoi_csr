import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class LoadingService {
  private loadingSubject = new BehaviorSubject<boolean>(false);
  public loading$ = this.loadingSubject.asObservable();

  private requestCount = 0;
  private loadingTimeout: any = null;

  // Delay before showing spinner (in milliseconds)
  // Only show spinner if request takes longer than this
  private readonly LOADING_DELAY = 300;

  show(): void {
    this.requestCount++;

    // Only show spinner if this is the first request
    if (this.requestCount === 1) {
      // Delay showing the spinner to avoid flash for fast requests
      this.loadingTimeout = setTimeout(() => {
        // Only show if still loading (request hasn't completed)
        if (this.requestCount > 0) {
          this.loadingSubject.next(true);
        }
      }, this.LOADING_DELAY);
    }
  }

  hide(): void {
    this.requestCount--;

    if (this.requestCount <= 0) {
      this.requestCount = 0;

      // Clear the timeout if request completed before delay
      if (this.loadingTimeout) {
        clearTimeout(this.loadingTimeout);
        this.loadingTimeout = null;
      }

      // Hide spinner
      this.loadingSubject.next(false);
    }
  }

  forceHide(): void {
    this.requestCount = 0;

    // Clear any pending timeout
    if (this.loadingTimeout) {
      clearTimeout(this.loadingTimeout);
      this.loadingTimeout = null;
    }

    this.loadingSubject.next(false);
  }
}
