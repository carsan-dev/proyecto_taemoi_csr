import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../../environments/environment';
import { RELOJ_WEB } from '../../../comun/reloj';
import { eventoDetalleDePrueba, sitioConSedes } from '../../../datos/datos-de-prueba';
import { EventoDetalleWeb } from '../../../datos/modelos';
import { WEB_PLANTILLA_ROUTES } from '../../../web-plantilla.routes';
import { EventoLineaComarcalComponent } from './evento.component';
import { tipoDocumento } from './evento';

@Component({ standalone: true, template: '<p class="login">login</p>' })
class LoginFalsoComponent {}

const AHORA = new Date(2026, 10, 2, 16, 0);

/**
 * Página de un evento del tema «La línea comarcal» (W5, boceto `boceto-linea-comarcal-evento.html`): cabeza, cartel,
 * descripción, documentos, galería con visor; sin documentos ni fotos; id desconocido (404 del tema); sin publicar.
 */
describe('EventoLineaComarcalComponent', () => {
  const url = `${environment.apiUrl}/publico/web`;
  const api = `${environment.apiUrl}/eventos/7`;
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let tituloAnterior: string;

  beforeEach(async () => {
    tituloAnterior = document.title;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([...WEB_PLANTILLA_ROUTES, { path: 'login', component: LoginFalsoComponent }]),
        { provide: RELOJ_WEB, useValue: () => AHORA },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    for (const visor of Array.from(document.querySelectorAll<HTMLDialogElement>('dialog[open]'))) {
      visor.close();
    }
    http.verify();
    for (const elemento of Array.from(document.head.querySelectorAll('[data-web-plantilla]'))) {
      elemento.remove();
    }
    document.title = tituloAnterior;
  });

  async function abrir(evento: EventoDetalleWeb | 404, ruta = '/eventos/7'): Promise<HTMLElement> {
    await harness.navigateByUrl(ruta);
    http.expectOne((r) => r.url === url).flush(sitioConSedes(6));
    const peticion = http.expectOne((r) => r.url.startsWith(`${url}/eventos/`));
    if (evento === 404) {
      peticion.flush(null, { status: 404, statusText: 'Not Found' });
    } else {
      peticion.flush(evento);
    }
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.routeNativeElement!;
  }

  const textos = (raiz: Element, selector: string) =>
    Array.from(raiz.querySelectorAll(selector)).map((e) => e.textContent!.replace(/\s+/g, ' ').trim());
  const jsonLd = () =>
    Array.from(document.head.querySelectorAll('script[type="application/ld+json"][data-web-plantilla]')).map(
      (s) => JSON.parse(s.textContent ?? '') as Record<string, unknown>,
    );
  const robots = () =>
    document.head.querySelector('meta[name="robots"][data-web-plantilla]')?.getAttribute('content') ?? null;

  it('cabeza: volver, fecha, día con la hora, lugar y sede, título y cartel', async () => {
    const raiz = await abrir(eventoDetalleDePrueba());
    expect(harness.routeDebugElement!.componentInstance).toBeInstanceOf(EventoLineaComarcalComponent);
    const volver = raiz.querySelector<HTMLAnchorElement>('.evento-cabeza a.atras')!;
    expect(volver.textContent!.trim()).toBe('Volver a eventos');
    expect(volver.getAttribute('href')).toBe('/eventos');
    expect(textos(raiz, '.evento-rotulo .fecha-dia, .evento-rotulo .fecha-mes, .evento-rotulo .fecha-anio')).toEqual([
      '14',
      'nov',
      '2026',
    ]);
    expect(textos(raiz, '.evento-cuando p')).toEqual(['Sábado · 10:00', 'Pabellón cubierto municipal, Estepa']);
    expect(textos(raiz, 'h1#evento-titulo')).toEqual(['Examen de cinturones']);
    expect(raiz.querySelector('.evento-foto img')!.getAttribute('src')).toBe(`${api}/imagen`);
    expect(textos(raiz, '.evento-desc')).toEqual([eventoDetalleDePrueba().descripcion!]);
    expect(raiz.querySelector('.nav-lista a[aria-current="page"]')!.getAttribute('href')).toBe('/eventos');
  });

  it('documentos: enlaces de descarga con su nombre y tipo', async () => {
    const raiz = await abrir(eventoDetalleDePrueba());
    expect(textos(raiz, '#docs-titulo')).toEqual(['Documentos']);
    const enlaces = Array.from(raiz.querySelectorAll<HTMLAnchorElement>('.docs a'));
    expect(enlaces.map((a) => a.getAttribute('href'))).toEqual([
      `${api}/documentos/31/descargar?download=true`,
      `${api}/documentos/32/descargar?download=true`,
    ]);
    expect(enlaces.every((a) => a.hasAttribute('download'))).toBeTrue();
    expect(textos(raiz, '.doc-nombre')).toEqual(['Circular-examen-noviembre.pdf', 'Autorizacion-menores.docx']);
    expect(textos(raiz, '.doc-tipo')).toEqual(['PDF', 'Word']);
  });

  it('galería: cuenta, miniaturas numeradas y el visor (pasa de foto, cierra y devuelve el foco)', async () => {
    const raiz = await abrir(eventoDetalleDePrueba());
    expect(textos(raiz, '#galeria-titulo')).toEqual(['Galería']);
    expect(textos(raiz, '.galeria-cuenta')).toEqual(['3 fotos']);
    expect(textos(raiz, '.galeria-n')).toEqual(['01', '02', '03']);
    const botones = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button.ampliar'));
    expect(botones.map((b) => b.getAttribute('aria-label'))).toEqual([
      'Ampliar la foto 1 de 3',
      'Ampliar la foto 2 de 3',
      'Ampliar la foto 3 de 3',
    ]);
    expect(botones[0].querySelector('img')!.getAttribute('src')).toBe(`${api}/fotos/51?w=640`);
    expect(textos(raiz, '.volver-pie a')).toEqual(['Volver a eventos']);

    const visor = raiz.querySelector<HTMLDialogElement>('dialog.visor')!;
    expect(visor.open).toBeFalse();
    botones[1].click();
    harness.detectChanges();
    expect(visor.open).toBeTrue();
    expect(textos(visor, '.visor-n')).toEqual(['02de 3']);
    expect(visor.querySelector('.visor-foto img')!.getAttribute('src')).toBe(`${api}/fotos/52`);
    expect(visor.querySelector('.visor-foto img')!.getAttribute('alt')).toBe('Foto 2 de 3');
    expect(document.activeElement!.textContent!.trim()).toBe('Cerrar');

    visor.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    harness.detectChanges();
    expect(textos(visor, '.visor-n')).toEqual(['03de 3']);
    visor.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    harness.detectChanges();
    expect(textos(visor, '.visor-n')).toEqual(['01de 3']);
    Array.from(visor.querySelectorAll<HTMLButtonElement>('.visor-pasos button'))[0].click();
    harness.detectChanges();
    expect(textos(visor, '.visor-n')).toEqual(['03de 3']);

    // El evento «close» del diálogo llega en una tarea aparte: se espera a él, no a un plazo.
    const cerrado = new Promise((listo) => visor.addEventListener('close', listo, { once: true }));
    visor.querySelector<HTMLButtonElement>('.visor-barra button')!.click();
    await cerrado;
    harness.detectChanges();
    expect(visor.open).toBeFalse();
    expect(document.activeElement).toBe(botones[1]);
  });

  it('sin documentos, sin fotos, sin cartel, sin hora, lugar ni sede: sin esos bloques', async () => {
    const raiz = await abrir(
      eventoDetalleDePrueba({ documentos: [], fotos: [], conImagen: false, hora: null, lugar: null, sedeId: null, sedeNombre: null }),
    );
    expect(raiz.querySelector('.evento-docs')).toBeNull();
    expect(raiz.querySelector('#galeria')).toBeNull();
    expect(raiz.querySelector('dialog')).toBeNull();
    expect(raiz.querySelector('.evento-foto')).toBeNull();
    expect(textos(raiz, '.evento-cuando p')).toEqual(['Sábado']);
    expect(raiz.querySelectorAll('a.atras').length).toBe(1);
  });

  it('una foto: «1 foto»', async () => {
    const raiz = await abrir(eventoDetalleDePrueba({ fotos: [{ id: 9 }] }));
    expect(textos(raiz, '.galeria-cuenta')).toEqual(['1 foto']);
  });

  it('SEO: título, canonical y su Event con la descripción y el lugar', async () => {
    await abrir(eventoDetalleDePrueba());
    expect(document.title).toBe('Examen de cinturones · Taekwondo Sierra Sur');
    expect(robots()).toBeNull();
    const datos = jsonLd();
    expect(datos.length).toBe(1);
    expect(datos[0]['@type']).toBe('Event');
    expect(datos[0]['description']).toBe(eventoDetalleDePrueba().descripcion!);
    expect(datos[0]['startDate']).toBe('2026-11-14T10:00');
    expect((datos[0]['location'] as Record<string, unknown>)['name']).toBe('Pabellón cubierto municipal');
  });

  it('id desconocido u oculto (404): la 404 del tema con noindex, en la misma URL', async () => {
    const raiz = await abrir(404, '/eventos/99');
    expect(raiz.querySelector('.lc')).not.toBeNull();
    expect(raiz.querySelector('.p-evento')).toBeNull();
    expect(textos(raiz, 'h1.error-titular')).toEqual(['No encontramos esta página']);
    expect(document.title).toBe('No encontramos esta página · Taekwondo Sierra Sur');
    expect(robots()).toBe('noindex, nofollow');
    expect(jsonLd()).toEqual([]);
    expect(TestBed.inject(Router).url).toBe('/eventos/99');
  });

  it('id que no es un número: la 404 del tema sin pedir el evento', async () => {
    await harness.navigateByUrl('/eventos/abc');
    http.expectOne(url).flush(sitioConSedes(6));
    harness.detectChanges();
    const raiz = harness.routeNativeElement!;
    expect(textos(raiz, 'h1.error-titular')).toEqual(['No encontramos esta página']);
    expect(robots()).toBe('noindex, nofollow');
    http.expectNone((r) => r.url.startsWith(`${url}/eventos`));
  });

  it('web sin publicar: «Web aún no publicada» (no la 404), sin el tema', async () => {
    await harness.navigateByUrl('/eventos/7');
    http.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne(`${url}/eventos/7`).flush(null, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    http
      .expectOne(`${environment.apiUrl}/publico/escuela`)
      .flush({ nombre: 'Taekwondo Sierra Sur', logo: 'NINGUNO', logoVersion: null, tema: null });
    harness.detectChanges();
    const raiz = harness.routeNativeElement!;
    expect(raiz.querySelector('.lc')).toBeNull();
    expect(raiz.querySelector('.error-titular')).toBeNull();
    expect(raiz.querySelector('app-web-sin-publicar')).not.toBeNull();
    expect(robots()).toBe('noindex, nofollow');
  });

  it('tipoDocumento: por el tipo MIME y, si no, por la extensión', () => {
    expect(tipoDocumento({ nombre: 'a.pdf', tipo: 'application/pdf' })).toBe('PDF');
    expect(tipoDocumento({ nombre: 'a.doc', tipo: 'application/msword' })).toBe('Word');
    expect(tipoDocumento({ nombre: 'a.xlsx', tipo: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })).toBe('Excel');
    expect(tipoDocumento({ nombre: 'cartel.jpg', tipo: 'image/jpeg' })).toBe('JPG');
    expect(tipoDocumento({ nombre: 'sin-extension', tipo: null })).toBeNull();
  });
});
