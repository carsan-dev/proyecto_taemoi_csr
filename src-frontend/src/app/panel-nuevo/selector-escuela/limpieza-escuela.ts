/**
 * Lo que el navegador guarda de la escuela de la sesión y se borra tras cambiar de escuela (fase 4, selector de
 * escuela). Inventario de todos los `localStorage.setItem` del frontend:
 *
 * Se borran (datos de la escuela o de sus alumnos, o estado de la sesión anterior):
 * - `dashboard-user-vistos-eventos`, `dashboard-user-estado-deportes-<alumnoId>` (deporte, grado y aptitud) y
 *   `dashboard-user-vistos-documentos-<alumnoId>`: área de familias (`vista-principal-user.component.ts`).
 * - `taemoi.cajaSede.ultimaSede`: la última sede de «Mi caja» (`CLAVE_ULTIMA_SEDE`, `caja-sede-vista.ts`).
 * - `panelNuevo`: recordatorio de que la sesión era del panel (`RECORDATORIO_PANEL`, `skeleton.component.ts`); sale de
 *   los roles en la escuela anterior y cerrar sesión ya lo borra.
 *
 * Se conservan (preferencias del navegador o de la persona, sin datos de ninguna escuela):
 * - `cookieConsent`: consentimiento de cookies.
 * - `taemoi_vista_preferida`: vista preferida (gestión o familias); solo cuenta si los roles de la escuela nueva dan las
 *   dos (`AuthenticationService.resolverRutaInicio`).
 * - `panelAlumnosVista`: «Filas | Tarjetas» del listado de alumnos.
 *
 * El `sessionStorage` (alumno de familia, filtros, páginas, saludos) se vacía entero, como al cerrar sesión.
 */
export const CLAVES_LOCALES_DE_ESCUELA: readonly string[] = ['taemoi.cajaSede.ultimaSede', 'panelNuevo'];

/** Prefijos de las claves con datos de alumnos del área de familias (una por alumno). */
export const PREFIJOS_LOCALES_DE_ESCUELA: readonly string[] = ['dashboard-user-'];

/** Vacía el `sessionStorage` y borra del `localStorage` lo que es de la escuela anterior. Nunca lanza. */
export function limpiarAlmacenesDeEscuela(ventana: Window | null | undefined): void {
  try {
    ventana?.sessionStorage.clear();
  } catch {
    // Sin sessionStorage utilizable no hay nada que vaciar.
  }
  let almacen: Storage | undefined;
  try {
    almacen = ventana?.localStorage;
  } catch {
    return;
  }
  if (!almacen) {
    return;
  }
  try {
    const claves: string[] = [];
    for (let i = 0; i < almacen.length; i++) {
      const clave = almacen.key(i);
      if (
        clave !== null &&
        (CLAVES_LOCALES_DE_ESCUELA.includes(clave) || PREFIJOS_LOCALES_DE_ESCUELA.some((p) => clave.startsWith(p)))
      ) {
        claves.push(clave);
      }
    }
    claves.forEach((clave) => almacen!.removeItem(clave));
  } catch {
    // Almacén bloqueado: nada más que hacer.
  }
}
