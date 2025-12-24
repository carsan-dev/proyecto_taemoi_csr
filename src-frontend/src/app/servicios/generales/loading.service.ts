import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { NgxSpinnerService } from 'ngx-spinner';

export interface LoadingState {
  isLoading: boolean;
  message?: string;
}

/**
 * Global loading service that manages loading states across the application.
 * Uses NgxSpinner for visual feedback and tracks concurrent requests.
 */
@Injectable({
  providedIn: 'root'
})
export class LoadingService {
  private loadingSubject = new BehaviorSubject<LoadingState>({ isLoading: false });
  private requestCount = 0;

  public loading$: Observable<LoadingState> = this.loadingSubject.asObservable();

  constructor(private readonly spinner: NgxSpinnerService) {}

  /**
   * Shows the loading spinner with an optional message
   * @param message Optional message to display
   */
  show(message?: string): void {
    this.requestCount++;
    this.loadingSubject.next({ isLoading: true, message });
    this.spinner.show();
  }

  /**
   * Hides the loading spinner
   * Only hides when all concurrent requests have completed
   */
  hide(): void {
    this.requestCount--;
    if (this.requestCount <= 0) {
      this.requestCount = 0;
      this.loadingSubject.next({ isLoading: false });
      this.spinner.hide();
    }
  }

  /**
   * Force hides the spinner regardless of pending requests
   */
  forceHide(): void {
    this.requestCount = 0;
    this.loadingSubject.next({ isLoading: false });
    this.spinner.hide();
  }

  /**
   * Updates the loading message without changing visibility
   * @param message The new message to display
   */
  setMessage(message: string): void {
    const currentState = this.loadingSubject.value;
    if (currentState.isLoading) {
      this.loadingSubject.next({ ...currentState, message });
    }
  }

  /**
   * Returns whether loading is currently active
   */
  get isLoading(): boolean {
    return this.loadingSubject.value.isLoading;
  }
}
