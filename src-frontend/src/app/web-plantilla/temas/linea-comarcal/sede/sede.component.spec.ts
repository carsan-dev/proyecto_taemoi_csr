import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../../environments/environment';
import { RELOJ_WEB } from '../../../comun/reloj';
import { PLAZAS, grupoDePrueba, sedeDePrueba, sitioConSedes, turnoDePrueba } from '../../../datos/datos-de-prueba';
import { SitioWeb } from '../../../datos/modelos';
import { WEB_PLANTILLA_ROUTES } from '../../../web-plantilla.routes';
import { CLAVE_SEDE_RECORDADA } from '../contenido';
import { SedeLineaComarcalComponent } from './sede.component';

@Component({ standalone: true, template: '<p class="login">login</p>' })
class LoginFalsoComponent {}

/** Lunes 2 de noviembre de 2026, 16:00 (hora local). */
const AHORA = new Date(2026, 10, 2, 16, 0);

/** Seis sedes; Estepa (la 2.ª) con fotos, acceso, teléfono y un grupo lleno y otro con lista de espera. */
function seisSedes(cambios: Partial<SitioWeb> = {}): SitioWeb {
  const sitio = sitioConSedes(6, cambios);
  sitio.sedes[1] = sedeDePrueba(2, 'Estepa', {
    lugar: 'Pabellón cubierto municipal',
    indicacionesAcceso: 'Entrada por la calle lateral, junto al aparcamiento.',
    telefono: '600000002',
    fotoUrl: 'https://example.com/estepa-1.jpg',
    foto2Url: 'https://example.com/estepa-2.jpg',
    grupos: [
      grupoDePrueba('Peques', [turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.quedan(2)), turnoDePrueba('Miércoles', '17:00', '17:45', PLAZAS.quedan(4))]),
      grupoDePrueba('Infantil', [turnoDePrueba('Martes', '18:00', '19:00', PLAZAS.lleno)], { rangoEdadMin: 7, rangoEdadMax: 11 }),
      grupoDePrueba('Jóvenes y adultos', [turnoDePrueba('Jueves', '19:30', '21:00', PLAZAS.espera)], { rangoEdadMin: 12, rangoEdadMax: null }),
    ],
  });
  return sitio;
}

/**
 * Página de sede del tema «La línea comarcal» (W5, boceto `boceto-linea-comarcal-sede.html`): `/sedes/:slug` con los
 * datos de `GET /api/publico/web`, una y seis sedes, plazas (§4.3), sede sin fotos ni acceso, slug desconocido (404 del
 * tema) y SEO de la sede.
 */
describe('SedeLineaComarcalComponent', () => {
  const url = `${environment.apiUrl}/publico/web`;
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let tituloAnterior: string;

  beforeEach(async () => {
    tituloAnterior = document.title;
    localStorage.removeItem(CLAVE_SEDE_RECORDADA);
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
    http.verify();
    localStorage.removeItem(CLAVE_SEDE_RECORDADA);
    for (const elemento of Array.from(document.head.querySelectorAll('[data-web-plantilla]'))) {
      elemento.remove();
    }
    document.title = tituloAnterior;
  });

  async function abrir(sitio: SitioWeb, ruta: string): Promise<HTMLElement> {
    await harness.navigateByUrl(ruta);
    http.expectOne((r) => r.url === url).flush(sitio);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.routeNativeElement!;
  }

  const textos = (raiz: HTMLElement, selector: string) =>
    Array.from(raiz.querySelectorAll(selector)).map((e) => e.textContent!.replace(/\s+/g, ' ').trim());
  const robots = () => document.head.querySelector('meta[name="robots"][data-web-plantilla]')?.getAttribute('content') ?? null;
  const jsonLd = () =>
    Array.from(document.head.querySelectorAll('script[type="application/ld+json"][data-web-plantilla]')).map(
      (s) => JSON.parse(s.textContent ?? '') as Record<string, unknown>,
    );

  describe('seis sedes', () => {
    it('cartel: el tramo con la anterior y la siguiente, el nombre, el lugar, la foto y «Preinscribirme» con la sede', async () => {
      const raiz = await abrir(seisSedes(), '/sedes/estepa');
      expect(harness.routeDebugElement!.componentInstance).toBeInstanceOf(SedeLineaComarcalComponent);
      expect(textos(raiz, '.tramo-parada--actual')).toEqual(['Parada 2 de 6']);
      const vecinas = Array.from(raiz.querySelectorAll<HTMLAnchorElement>('a.tramo-parada'));
      expect(vecinas.map((a) => a.getAttribute('href'))).toEqual(['/sedes/herrera', '/sedes/casariche']);
      expect(textos(raiz, 'a.tramo-parada .tramo-nombre')).toEqual(['Herrera', 'Casariche']);
      expect(textos(raiz, 'h1.sede-titular')).toEqual(['Estepa']);
      expect(textos(raiz, '.sede-lugar strong')).toEqual(['Pabellón cubierto municipal']);
      expect(textos(raiz, '.sede-lugar span')).toEqual(['C/ del Deporte, 2, Estepa']);
      expect(raiz.querySelector<HTMLImageElement>('.sede-foto img')!.src).toBe('https://example.com/estepa-1.jpg');
      const pre = raiz.querySelector<HTMLAnchorElement>('.sede-texto .acciones a.boton')!;
      expect(pre.getAttribute('href')).toBe('/preinscripcion?sede=estepa');
      expect(pre.getAttribute('aria-label')).toBe('Preinscribirme en Estepa');
      expect(raiz.querySelector('.sede-texto .acciones a.enlace-flecha')!.getAttribute('href')).toBe('/sedes/estepa#horarios');
    });

    it('primera y última sede: el trazo acaba en un terminal y falta la vecina de ese lado', async () => {
      const raiz = await abrir(seisSedes(), '/sedes/herrera');
      expect(textos(raiz, '.tramo-parada--actual')).toEqual(['Parada 1 de 6']);
      expect(raiz.querySelectorAll('a.tramo-parada').length).toBe(1);
      expect(raiz.querySelector('.tramo-hueco')).not.toBeNull();
      expect(raiz.querySelectorAll('.tramo-term').length).toBe(1);
    });

    it('tablero: rótulo con el curso, próxima clase con su grupo, HOY, plazas (§4.3) y lista de espera', async () => {
      const raiz = await abrir(seisSedes(), '/sedes/estepa');
      expect(textos(raiz, '#horarios .cabeza-seccion .rotulo')).toEqual(['Horarios · curso 2026-2027']);
      expect(textos(raiz, '#horarios-titulo')).toEqual(['Grupos y horas']);
      expect(textos(raiz, '.proxima-cuando')).toEqual(['HOY17:00']);
      expect(textos(raiz, '.proxima-grupo')).toEqual(['Peques, 4 a 6 años']);
      expect(textos(raiz, '.salida .grupo-nombre')).toEqual(['Peques', 'Infantil', 'Jóvenes y adultos']);
      expect(textos(raiz, '.salida .grupo-edades')).toEqual(['4 a 6 años · Taekwondo', '7 a 11 años · Taekwondo', 'Desde 12 años · Taekwondo']);
      expect(raiz.querySelectorAll('.salida')[0].querySelector('mark.hoy')).not.toBeNull();
      expect(raiz.querySelectorAll('.salida')[1].querySelector('mark.hoy')).toBeNull();
      expect(raiz.querySelectorAll('.salida')[0].querySelectorAll('.dia--hoy').length).toBe(1);
      // El grupo toma el peor de sus turnos; sin «Últimas N».
      expect(textos(raiz, '.salida .plazas')).toEqual(['Quedan 2 plazas', 'Lleno', 'Lista de espera']);
      expect(Array.from(raiz.querySelectorAll('.salida .plazas')).map((p) => p.className)).toEqual([
        'plazas plazas--quedan',
        'plazas plazas--lleno',
        'plazas plazas--espera',
      ]);
      const acciones = Array.from(raiz.querySelectorAll<HTMLAnchorElement>('.salida-accion a'));
      expect(acciones.map((a) => a.textContent!.trim())).toEqual([
        'Preinscribirme',
        'Apuntarme a la lista de espera',
        'Apuntarme a la lista de espera',
      ]);
      expect(acciones.map((a) => a.classList.contains('boton--secundario'))).toEqual([false, true, true]);
      expect(acciones[1].getAttribute('href')).toContain('/preinscripcion?sede=estepa&grupo=');
      expect(acciones[1].getAttribute('aria-label')).toBe('Apuntarme a la lista de espera: Infantil, 7 a 11 años, Estepa');
      expect(textos(raiz, '.leyenda li')).toEqual([
        'Quedan plazas',
        'Lleno: puedes apuntarte a la lista de espera',
        'Ya hay lista de espera',
      ]);
    });

    it('cómo llegar: lugar, entrada, teléfono, mapa y la segunda foto', async () => {
      const raiz = await abrir(seisSedes(), '/sedes/estepa');
      expect(textos(raiz, '.ficha dt')).toEqual(['Lugar', 'Entrada', 'Teléfono']);
      expect(textos(raiz, '.ficha dd')[0]).toBe('Pabellón cubierto municipalC/ del Deporte, 2, Estepa');
      expect(textos(raiz, '.ficha dd')[1]).toBe('Entrada por la calle lateral, junto al aparcamiento.');
      expect(raiz.querySelector('.ficha dd a')!.getAttribute('href')).toBe('tel:+34600000002');
      expect(textos(raiz, '.ficha dd a')).toEqual(['600 000 002']);
      const mapa = raiz.querySelector<HTMLAnchorElement>('.llegar-acciones a')!;
      expect(mapa.href).toContain('https://www.google.com/maps/search/?api=1&query=');
      expect(mapa.target).toBe('_blank');
      expect(raiz.querySelector<HTMLImageElement>('.llegar-foto img')!.src).toBe('https://example.com/estepa-2.jpg');
    });

    it('tarifa de la sede «igual en todas las sedes» y la línea entera con «Estás aquí»', async () => {
      const raiz = await abrir(seisSedes(), '/sedes/estepa');
      expect(textos(raiz, '.tarifa-importe')).toEqual(['25 €']);
      expect(textos(raiz, '.tarifa-igual')).toEqual(['Igual en todas las sedes']);
      expect(raiz.querySelector('.tarifas a.boton--blanco')!.getAttribute('href')).toBe('/preinscripcion?sede=estepa');
      expect(textos(raiz, '.ruta-sede--actual')).toEqual(['EstepaEstás aquíPabellón cubierto municipal · C/ del Deporte, 2']);
      expect(raiz.querySelectorAll('.ruta a.ruta-nombre').length).toBe(5);
    });

    it('una sede con mensualidad propia: su importe y sin «Igual en todas las sedes»', async () => {
      const sitio = seisSedes({
        tarifasSede: [{ tarifaId: 1, sedeId: 2, importe: 28, importeAdulto: null, importeMenor: null, vigenteDesde: '2026-09-01' }],
      });
      const raiz = await abrir(sitio, '/sedes/estepa');
      expect(textos(raiz, '.tarifa-importe')).toEqual(['28 €']);
      expect(raiz.querySelector('.tarifa-igual')).toBeNull();
    });

    it('SEO: título y descripción de la sede, canonical y solo su SportsActivityLocation; la sede se recuerda', async () => {
      await abrir(seisSedes(), '/sedes/estepa');
      expect(document.title).toBe('Estepa · Taekwondo Sierra Sur');
      expect(document.head.querySelector('link[rel="canonical"][data-web-plantilla]')!.getAttribute('href')).toBe(
        `${location.origin}/sedes/estepa`,
      );
      expect(jsonLd().map((d) => [d['@type'], d['name']])).toEqual([['SportsActivityLocation', 'Taekwondo Sierra Sur · Estepa']]);
      expect(robots()).toBeNull();
      expect(localStorage.getItem(CLAVE_SEDE_RECORDADA)).toBe('estepa');
    });

    it('de una sede a otra (mismo componente): cambian el contenido y el SEO, sin pedir otra vez los datos', async () => {
      const raiz = await abrir(seisSedes(), '/sedes/estepa');
      await harness.navigateByUrl('/sedes/casariche');
      harness.detectChanges();
      expect(textos(raiz, 'h1.sede-titular')).toEqual(['Casariche']);
      expect(textos(raiz, '.tramo-parada--actual')).toEqual(['Parada 3 de 6']);
      expect(document.title).toBe('Casariche · Taekwondo Sierra Sur');
      expect(jsonLd().length).toBe(1);
      http.expectNone(url);
    });

    it('cabecera de una página interior: «Tarifas» va a su página; el resto, a la portada', async () => {
      const raiz = await abrir(seisSedes(), '/sedes/estepa');
      const menu = Array.from(raiz.querySelectorAll<HTMLAnchorElement>('.nav-lista a'));
      expect(menu.map((a) => a.getAttribute('href'))).toEqual(['/#linea', '/#sedes', '/tarifas', '/#contacto']);
      expect(menu.some((a) => a.hasAttribute('aria-current'))).toBeFalse();
    });
  });

  describe('una sede', () => {
    it('«Única sede», sin vecinas ni línea entera, y «Para todos los grupos»', async () => {
      const raiz = await abrir(sitioConSedes(1), '/sedes/estepa');
      expect(textos(raiz, '.tramo-parada--actual')).toEqual(['Única sede']);
      expect(raiz.querySelector('.tramo')!.classList).toContain('tramo--una');
      expect(raiz.querySelectorAll('.tramo-term').length).toBe(2);
      expect(raiz.querySelector('a.tramo-parada')).toBeNull();
      expect(raiz.querySelector('#sedes')).toBeNull();
      expect(textos(raiz, '.tarifa-igual')).toEqual(['Para todos los grupos']);
    });
  });

  it('sede sin fotos, sin acceso ni teléfono: sin figuras; la ficha solo con el lugar', async () => {
    const raiz = await abrir(sitioConSedes(6), '/sedes/casariche');
    expect(raiz.querySelector('figure')).toBeNull();
    expect(textos(raiz, '.ficha dt')).toEqual(['Lugar']);
    expect(raiz.querySelector('.llegar-acciones a')).not.toBeNull();
  });

  it('sede sin lugar ni dirección: sin «Cómo llegar»', async () => {
    const sitio = sitioConSedes(6);
    sitio.sedes[2] = { ...sitio.sedes[2], lugar: null, direccion: null };
    const raiz = await abrir(sitio, '/sedes/casariche');
    expect(raiz.querySelector('#llegar')).toBeNull();
    expect(raiz.querySelector('.sede-lugar')).toBeNull();
  });

  it('sin tarifas: sin la sección de tarifa', async () => {
    const raiz = await abrir(sitioConSedes(6, { tarifas: [] }), '/sedes/estepa');
    expect(raiz.querySelector('.tarifas')).toBeNull();
  });

  it('slug desconocido: la 404 del tema con noindex, en la misma URL', async () => {
    const raiz = await abrir(seisSedes(), '/sedes/no-existe');
    expect(raiz.querySelector('.lc')).not.toBeNull();
    expect(raiz.querySelector('.p-sede')).toBeNull();
    expect(textos(raiz, 'h1.error-titular')).toEqual(['No encontramos esta página']);
    expect(document.title).toBe('No encontramos esta página · Taekwondo Sierra Sur');
    expect(robots()).toBe('noindex, nofollow');
    expect(jsonLd()).toEqual([]);
    expect(TestBed.inject(Router).url).toBe('/sedes/no-existe');
  });

  it('web sin publicar: «Web aún no publicada», sin el tema', async () => {
    await harness.navigateByUrl('/sedes/estepa');
    http.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    http
      .expectOne(`${environment.apiUrl}/publico/escuela`)
      .flush({ nombre: 'Taekwondo Sierra Sur', logo: 'NINGUNO', logoVersion: null, tema: null });
    harness.detectChanges();
    const raiz = harness.routeNativeElement!;
    expect(raiz.querySelector('.lc')).toBeNull();
    expect(raiz.querySelector('app-web-sin-publicar a.sp-entrar')!.getAttribute('href')).toBe('/login');
    expect(robots()).toBe('noindex, nofollow');
  });
});
