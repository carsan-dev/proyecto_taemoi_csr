import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { LoadingService } from '../servicios/generales/loading.service';

/**
 * HTTP interceptor that can show a loading spinner during HTTP requests.
 *
 * By default, NO loading spinner is shown. Components should:
 * - Use skeleton cards for initial page loads
 * - Use LoadingService.show()/hide() manually for button actions (PDF generation, etc.)
 *
 * Alternatively, add 'X-Show-Loading' header to automatically show/hide spinner.
 */
export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  const loadingService = inject(LoadingService);

  // Check if request explicitly wants loading indicator
  const showLoading = req.headers.has('X-Show-Loading');

  if (!showLoading) {
    // Remove skip header if present (for backwards compatibility)
    if (req.headers.has('X-Skip-Loading')) {
      const modifiedReq = req.clone({
        headers: req.headers.delete('X-Skip-Loading')
      });
      return next(modifiedReq);
    }
    return next(req);
  }

  // Remove the custom header before sending the request
  const modifiedReq = req.clone({
    headers: req.headers.delete('X-Show-Loading')
  });

  // Show loading spinner
  loadingService.show();

  return next(modifiedReq).pipe(
    finalize(() => {
      // Hide loading spinner when request completes
      loadingService.hide();
    })
  );
};
