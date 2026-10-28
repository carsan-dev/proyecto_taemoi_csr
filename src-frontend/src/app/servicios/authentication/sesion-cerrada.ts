import { Subject } from 'rxjs';

/**
 * Avisa de que la sesión se ha cerrado o ha caducado (lo emite `AuthenticationService.limpiarEstadoLocal`, al que llegan
 * `logout` y `forzarCierreSesion`). Va sin inyección: quien escucha (los avisos del panel nuevo) no crea por ello el
 * servicio de autenticación, que al nacer consulta la sesión al servidor.
 */
export const sesionCerrada = new Subject<void>();
