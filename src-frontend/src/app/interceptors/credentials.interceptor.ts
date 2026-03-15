import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Interceptor that adds credentials (cookies) to all HTTP requests.
 * This is necessary for the JWT cookie to be sent with API requests.
 */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  // Clone the request and add withCredentials: true
  const clonedRequest = req.clone({
    withCredentials: true
  });

  return next(clonedRequest);
};
