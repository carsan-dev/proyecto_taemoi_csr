import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import Swal from 'sweetalert2';

/**
 * Error interceptor that handles HTTP errors globally
 *
 * Handles common HTTP error scenarios:
 * - 401 Unauthorized: Redirects to login
 * - 403 Forbidden: Shows access denied message
 * - 404 Not Found: Shows resource not found message
 * - 500 Internal Server Error: Shows server error message
 * - Network errors: Shows connection error message
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let errorMessage = 'Ha ocurrido un error inesperado';
      let errorTitle = 'Error';

      // Handle different error types
      if (error.error instanceof ErrorEvent) {
        // Client-side or network error
        errorMessage = 'Error de conexión. Por favor, verifica tu conexión a internet.';
        errorTitle = 'Error de Conexión';
        console.error('Client-side error:', error.error.message);
      } else {
        // Backend returned an unsuccessful response code
        switch (error.status) {
          case 401:
            // Unauthorized - redirect to login
            errorTitle = 'Sesión Expirada';
            errorMessage = 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.';
            console.error('Unauthorized access - redirecting to login');

            // Show error and redirect to login
            Swal.fire({
              title: errorTitle,
              text: errorMessage,
              icon: 'warning',
              confirmButtonText: 'Ir al Login',
              allowOutsideClick: false
            }).then(() => {
              // Clear any stored tokens/session data
              localStorage.removeItem('token');
              sessionStorage.clear();
              router.navigate(['/login']);
            });

            return throwError(() => error);

          case 403:
            // Forbidden - access denied
            errorTitle = 'Acceso Denegado';
            errorMessage = 'No tienes permisos para realizar esta acción.';
            console.error('Access forbidden:', error.message);
            break;

          case 404:
            // Not Found
            errorTitle = 'No Encontrado';
            errorMessage = 'El recurso solicitado no existe.';
            console.error('Resource not found:', error.url);
            break;

          case 500:
            // Internal Server Error
            errorTitle = 'Error del Servidor';
            errorMessage = 'Ha ocurrido un error en el servidor. Por favor, inténtalo más tarde.';
            console.error('Server error:', error.message);
            break;

          case 503:
            // Service Unavailable
            errorTitle = 'Servicio No Disponible';
            errorMessage = 'El servicio no está disponible en este momento. Por favor, inténtalo más tarde.';
            console.error('Service unavailable:', error.message);
            break;

          case 0:
            // Network error (no response from server)
            errorTitle = 'Error de Red';
            errorMessage = 'No se pudo conectar con el servidor. Verifica tu conexión a internet.';
            console.error('Network error - no response from server');
            break;

          default:
            // Generic error
            errorMessage = error.error?.message || error.message || 'Ha ocurrido un error inesperado';
            console.error(`HTTP Error ${error.status}:`, error.message);
        }
      }

      // Show error message (except for 401 which is handled above)
      if (error.status !== 401) {
        Swal.fire({
          title: errorTitle,
          text: errorMessage,
          icon: 'error',
          confirmButtonText: 'Aceptar'
        });
      }

      // Return the error to be handled by component if needed
      return throwError(() => error);
    })
  );
};
