/**
 * Quita el parámetro `token` (enlaces de restablecer contraseña y de invitación) de una URL antes de mandarla a la
 * analítica: ningún hit de Google Analytics lleva un token (revisión E8-6 F1). Los demás parámetros no cambian.
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

/**
 * Consentimiento básico (decisión 233): gtag.js no está en index.html; se inserta aquí solo cuando el usuario acepta
 * las cookies de análisis (ahora o en una visita anterior). Sin aceptar no hay ninguna petición a Google.
 * Devuelve `false` si el script ya estaba.
 */
export function cargarScriptGtag(doc: Document, measurementId: string): boolean {
  if (doc.querySelector('script[src^="https://www.googletagmanager.com/gtag/js"]')) {
    return false;
  }
  const script = doc.createElement('script');
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${measurementId}`;
  doc.head.appendChild(script);
  return true;
}

/**
 * Borra las cookies de Google Analytics (`_ga` y `_ga_<id>`) al rechazar o retirar el consentimiento. GA4 las escribe
 * en el dominio más alto posible (p. ej. `.moiskimdo.es`), así que se caducan sin dominio y en cada dominio padre.
 */
export function borrarCookiesGa(doc: Document): void {
  const host = doc.location?.hostname ?? '';
  for (const cookie of doc.cookie.split(';')) {
    const nombre = cookie.split('=')[0].trim();
    if (!/^_ga(_|$)/.test(nombre)) {
      continue;
    }
    doc.cookie = `${nombre}=; max-age=0; path=/`;
    for (let dominio = host; dominio.includes('.'); dominio = dominio.slice(dominio.indexOf('.') + 1)) {
      doc.cookie = `${nombre}=; max-age=0; path=/; domain=.${dominio}`;
    }
  }
}
