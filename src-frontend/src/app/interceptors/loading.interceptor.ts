import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { LoadingService } from '../servicios/generales/loading.service';

export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  const loadingService = inject(LoadingService);

  // Show loading spinner
  loadingService.show();

  return next(req).pipe(
    finalize(() => {
      // Hide loading spinner when request completes (success or error)
      loadingService.hide();
    })
  );
};
