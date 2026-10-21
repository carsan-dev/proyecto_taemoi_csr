import { borrarCookiesGa, cargarScriptGtag, urlSinToken } from './analitica.util';

/** La URL que ve Google Analytics nunca lleva el `token` de los enlaces de contraseña o invitación. */
describe('urlSinToken', () => {
  const base = 'https://moiskimdo.es';

  it('quita el token y deja la URL sin «?» cuando era el único parámetro', () => {
    expect(urlSinToken(`${base}/reset-password?token=SECRETO1`)).toBe(`${base}/reset-password`);
  });

  it('quita el token y conserva los demás parámetros en su orden', () => {
    expect(urlSinToken(`${base}/crear-contrasena?utm_source=correo&token=SECRETO1&a=b`)).toBe(
      `${base}/crear-contrasena?utm_source=correo&a=b`
    );
  });

  it('quita también un token repetido o vacío', () => {
    expect(urlSinToken(`${base}/reset-password?token=A&x=1&token=B`)).toBe(`${base}/reset-password?x=1`);
    expect(urlSinToken(`${base}/reset-password?token=`)).toBe(`${base}/reset-password`);
  });

  it('sin token deja la URL igual', () => {
    expect(urlSinToken(`${base}/horarios?dia=lunes`)).toBe(`${base}/horarios?dia=lunes`);
    expect(urlSinToken(`${base}/`)).toBe(`${base}/`);
  });

  it('conserva el fragmento', () => {
    expect(urlSinToken(`${base}/reset-password?token=SECRETO1#formulario`)).toBe(`${base}/reset-password#formulario`);
    expect(urlSinToken(`${base}/horarios?dia=lunes&token=X#tabla`)).toBe(`${base}/horarios?dia=lunes#tabla`);
  });

  it('no confunde otros parámetros que contienen «token» en el nombre', () => {
    expect(urlSinToken(`${base}/x?mitoken=1&token=S`)).toBe(`${base}/x?mitoken=1`);
  });

  it('si no puede leer la URL la devuelve sin parámetros ni fragmento', () => {
    expect(urlSinToken('/reset-password?token=SECRETO1#a')).toBe('/reset-password');
    expect(urlSinToken('')).toBe('');
  });
});

/**
 * Consentimiento básico (decisión 233): gtag.js solo se carga tras «Aceptar». Se prueba sobre un documento inerte
 * (createHTMLDocument), donde un <script> insertado no se descarga.
 */
describe('cargarScriptGtag', () => {
  const selector = 'script[src^="https://www.googletagmanager.com/gtag/js"]';

  it('inserta un único <script async> de gtag.js con el ID de medición', () => {
    const doc = document.implementation.createHTMLDocument('prueba');
    expect(doc.querySelector(selector)).toBeNull();
    expect(cargarScriptGtag(doc, 'G-PRUEBA')).toBeTrue();
    const scripts = doc.querySelectorAll<HTMLScriptElement>(selector);
    expect(scripts.length).toBe(1);
    expect(scripts[0].async).toBeTrue();
    expect(scripts[0].src).toBe('https://www.googletagmanager.com/gtag/js?id=G-PRUEBA');
  });

  it('no lo inserta dos veces', () => {
    const doc = document.implementation.createHTMLDocument('prueba');
    cargarScriptGtag(doc, 'G-PRUEBA');
    expect(cargarScriptGtag(doc, 'G-PRUEBA')).toBeFalse();
    expect(doc.querySelectorAll(selector).length).toBe(1);
  });
});

describe('borrarCookiesGa', () => {
  it('borra las cookies _ga y _ga_<id> y deja las demás', () => {
    document.cookie = '_ga=GA1.1.prueba; path=/';
    document.cookie = '_ga_PRUEBA=GS1.1.prueba; path=/';
    document.cookie = 'otra_cookie=1; path=/';
    borrarCookiesGa(document);
    const nombres = document.cookie.split(';').map((c) => c.split('=')[0].trim());
    expect(nombres).not.toContain('_ga');
    expect(nombres).not.toContain('_ga_PRUEBA');
    expect(nombres).toContain('otra_cookie');
    document.cookie = 'otra_cookie=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });
});
