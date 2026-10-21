/**
 * Quita el parámetro `token` (enlaces de restablecer contraseña y de invitación) de una URL antes de mandarla a la
 * analítica: ningún hit de Google Analytics lleva un token (revisión E8-6 F1). Los demás parámetros no cambian.
 * `src/index.html` repite esta misma regla en línea para el primer `gtag('config', …)` (no puede importar TS): si
 * cambia una, cambia la otra.
 */
export function urlSinToken(href: string): string {
  let url: URL;
  try {
    url = new URL(href);
  } catch {
    return href.split(/[?#]/)[0]; // sin poder leerla, mejor sin parámetros que con un token
  }
  url.searchParams.delete('token');
  return url.toString();
}
