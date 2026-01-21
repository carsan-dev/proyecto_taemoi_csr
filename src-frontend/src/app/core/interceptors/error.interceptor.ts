import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import type { AuthenticationService } from '../../servicios/authentication/authentication.service';
import Swal from 'sweetalert2';

// Flag para evitar mostrar múltiples diálogos de sesión expirada
let sesionExpiradaMostrada = false;

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
  const injector = inject(Injector);

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
            if (!sesionConfirmada(injector) || esSolicitudAutenticacion(req.url)) {
              return throwError(() => error);
            }
            manejarSesionExpirada(injector, router);
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
            // Podría ser una sesión expirada si el usuario estaba logueado
            if (error.error instanceof ProgressEvent && error.error.type === 'abort') {
              return throwError(() => error);
            }
            if (estaUsuarioLogueado(injector)) {
              console.error('Network error while logged in - possible session expiration');
            }
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

/**
 * Obtiene el AuthenticationService de forma lazy para evitar dependencia circular.
 */
function getAuthService(injector: Injector): AuthenticationService {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { AuthenticationService: AuthService } = require('../../servicios/authentication/authentication.service');
  return injector.get(AuthService) as AuthenticationService;
}

function esSolicitudAutenticacion(url: string): boolean {
  const normalizedUrl = url.toLowerCase();
  return normalizedUrl.includes('/api/auth/');
}

/**
 * Comprueba si el usuario está logueado usando inyección lazy.
 */
function estaUsuarioLogueado(injector: Injector): boolean {
  try {
    const authService = getAuthService(injector);
    return authService.comprobarLogueado();
  } catch {
    return false;
  }
}

function sesionConfirmada(injector: Injector): boolean {
  try {
    const authService = getAuthService(injector);
    return authService.comprobarLogueado() && authService.rolesEstanCargados();
  } catch {
    return false;
  }
}

/**
 * Maneja la expiración de la sesión mostrando un mensaje y redirigiendo al login.
 * Usa inyección lazy para evitar dependencia circular con AuthenticationService.
 */
async function manejarSesionExpirada(injector: Injector, router: Router): Promise<void> {
  // Evitar mostrar múltiples diálogos
  if (sesionExpiradaMostrada) {
    return;
  }
  sesionExpiradaMostrada = true;

  console.error('Session expired - clearing state and redirecting to login');

  // Limpiar el estado de autenticación y esperar a que se complete
  try {
    const authService = getAuthService(injector);
    await authService.forzarCierreSesion();
  } catch (e) {
    console.error('Error clearing auth state:', e);
  }

  // Mostrar mensaje y redirigir
  Swal.fire({
    title: 'Sesión Expirada',
    text: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.',
    icon: 'warning',
    confirmButtonText: 'Ir al Login',
    allowOutsideClick: false
  }).then(() => {
    sesionExpiradaMostrada = false;
    router.navigate(['/login']);
  });
}
