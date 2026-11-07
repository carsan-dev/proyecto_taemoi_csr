import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../../environments/environment';
import { RELOJ_WEB } from '../../../comun/reloj';
import { eventoListaDePrueba, sitioConSedes } from '../../../datos/datos-de-prueba';
import { EventoListaWeb, EventosWeb, SitioWeb } from '../../../datos/modelos';
import { WEB_PLANTILLA_ROUTES } from '../../../web-plantilla.routes';
import { EventosLineaComarcalComponent } from './eventos.component';
import { fechaEvento } from './eventos';

@Component({ standalone: true, template: '<p class="login">login</p>' })
class LoginFalsoComponent {}

const AHORA = new Date(2026, 10, 2, 16, 0);

const ev = (id: number, fecha: string, titulo: string, cambios: Partial<EventoListaWeb> = {}): EventoListaWeb =>
  eventoListaDePrueba({ id, fecha, titulo, ...cambios });

/** Los del boceto: tres próximos y cuatro anteriores en dos años. */
const PROXIMOS = [
  ev(1, '2026-11-14', 'Examen de cinturones'),
  ev(2, '2026-11-29', 'Torneo comarcal infantil', { hora: null, lugar: null, sedeId: null, sedeNombre: null, conImagen: false }),
  ev(3, '2026-12-12', 'Exhibición de Navidad', { hora: '18:00', lugar: null, descripcion: null }),
];
const ANTERIORES = [
  ev(10, '2026-06-20', 'Exhibición de fin de curso'),
  ev(11, '2026-03-14', 'Campeonato provincial', { conImagen: false }),
  ev(12, '2025-12-13', 'Exhibición de Navidad'),
  ev(13, '2025-09-15', 'Empieza el curso 2025-2026'),
];

/**
 * Lista de eventos del tema «La línea comarcal» (W5, boceto `boceto-linea-comarcal-eventos.html`): destacado, filas,
 * anteriores por año, sin próximos, sin anteriores, sin ninguno, con y sin hora, lugar, sede y cartel.
 */
describe('EventosLineaComarcalComponent', () => {
  const url = `${environment.apiUrl}/publico/web`;
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
    http.verify();
    for (const elemento of Array.from(document.head.querySelectorAll('[data-web-plantilla]'))) {
      elemento.remove();
    }
    document.title = tituloAnterior;
  });

  async function abrir(eventos: EventosWeb, sitio: SitioWeb = sitioConSedes(6)): Promise<HTMLElement> {
    await harness.navigateByUrl('/eventos');
    http.expectOne((r) => r.url === url).flush(sitio);
    http.expectOne((r) => r.url === `${url}/eventos`).flush(eventos);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.routeNativeElement!;
  }

  const textos = (raiz: HTMLElement, selector: string) =>
    Array.from(raiz.querySelectorAll(selector)).map((e) => e.textContent!.replace(/\s+/g, ' ').trim());
  const jsonLd = () =>
    Array.from(document.head.querySelectorAll('script[type="application/ld+json"][data-web-plantilla]')).map(
      (s) => JSON.parse(s.textContent ?? '') as Record<string, unknown>,
    );

  it('cabeza, próximos con el primero destacado y el resto en filas, y anteriores por año', async () => {
    const raiz = await abrir({ proximos: PROXIMOS, anteriores: ANTERIORES });
    expect(harness.routeDebugElement!.componentInstance).toBeInstanceOf(EventosLineaComarcalComponent);
    expect(textos(raiz, 'h1.pagina-titular')).toEqual(['Eventos']);
    expect(textos(raiz, '.pagina-entrada')).toEqual([
      'Exámenes, torneos y exhibiciones del club, con sus circulares y sus fotos.',
    ]);
    expect(textos(raiz, '#proximos-titulo')).toEqual(['Próximos']);
    expect(textos(raiz, '.bloque-cuenta')).toEqual(['3 eventos']);

    const destacado = raiz.querySelector('.destacado')!;
    expect(textos(destacado as HTMLElement, '.destacado-titulo')).toEqual(['Examen de cinturones']);
    expect(destacado.querySelector('time.fecha')!.getAttribute('datetime')).toBe('2026-11-14');
    expect(textos(destacado as HTMLElement, '.fecha-dia, .fecha-mes, .fecha-anio')).toEqual(['14', 'nov', '2026']);
    expect(textos(destacado as HTMLElement, 'time .oculto')).toEqual(['sábado, 14 de noviembre de 2026']);
    expect(destacado.querySelector('img')!.getAttribute('src')).toBe(`${environment.apiUrl}/eventos/1/imagen`);
    expect(textos(destacado as HTMLElement, '.destacado-desc')).toEqual([PROXIMOS[0].descripcion!]);
    const ver = destacado.querySelector<HTMLAnchorElement>('a.boton')!;
    expect(ver.getAttribute('href')).toBe('/eventos/1');
    expect(ver.textContent!.replace(/\s+/g, ' ').trim()).toBe('Ver evento: Examen de cinturones');

    const filas = Array.from(raiz.querySelectorAll<HTMLElement>('ol.ev-filas > li.ev-fila'));
    expect(raiz.querySelector('ol.ev-filas')!.getAttribute('aria-label')).toBe('Más eventos próximos');
    expect(filas.map((f) => textos(f, '.ev-titulo')[0])).toEqual(['Torneo comarcal infantil', 'Exhibición de Navidad']);
    expect(filas.map((f) => f.querySelector('a.enlace-flecha')!.getAttribute('href'))).toEqual(['/eventos/2', '/eventos/3']);

    expect(textos(raiz, '#anteriores-titulo')).toEqual(['Anteriores']);
    expect(textos(raiz, '.anio-titulo')).toEqual(['2026', '2025']);
    const minis = Array.from(raiz.querySelectorAll<HTMLElement>('.ev-mini'));
    expect(minis.map((m) => textos(m, '.ev-mini-titulo')[0])).toEqual([
      'Exhibición de fin de curso',
      'Campeonato provincial',
      'Exhibición de Navidad',
      'Empieza el curso 2025-2026',
    ]);
    expect(textos(raiz, '.ev-mini-fecha')).toEqual(['20 jun 2026', '14 mar 2026', '13 dic 2025', '15 sep 2025']);
    expect(minis[0].querySelector('a')!.getAttribute('href')).toBe('/eventos/10');
    expect(minis[1].querySelector('img')).toBeNull();
    expect(minis[0].querySelector('img')!.getAttribute('alt')).toBe('');
  });

  it('hora, lugar y sede: «lugar, sede · hora» con lo que haya; sin ninguno, nada; sin cartel ni descripción, sin ellos', async () => {
    const raiz = await abrir({ proximos: PROXIMOS, anteriores: [] });
    expect(textos(raiz, '.destacado .ev-donde')).toEqual(['Pabellón cubierto municipal, Estepa · 10:00']);
    const filas = Array.from(raiz.querySelectorAll<HTMLElement>('li.ev-fila'));
    expect(filas[0].querySelector('.ev-donde')).toBeNull();
    expect(filas[0].querySelector('img')).toBeNull();
    expect(textos(filas[1], '.ev-donde')).toEqual(['Estepa · 18:00']);
    expect(filas[1].querySelector('.ev-desc')).toBeNull();
  });

  it('un solo próximo y sin cartel: «1 evento», destacado sin foto y sin filas', async () => {
    const raiz = await abrir({ proximos: [{ ...PROXIMOS[0], conImagen: false }], anteriores: ANTERIORES });
    expect(textos(raiz, '.bloque-cuenta')).toEqual(['1 evento']);
    expect(raiz.querySelector('.destacado')!.classList).toContain('destacado--sin-foto');
    expect(raiz.querySelector('.destacado img')).toBeNull();
    expect(raiz.querySelector('.ev-filas')).toBeNull();
  });

  it('solo anteriores: «No hay eventos próximos» con el enlace a los anteriores', async () => {
    const raiz = await abrir({ proximos: [], anteriores: ANTERIORES });
    expect(textos(raiz, '.bloque-cuenta')).toEqual(['']);
    expect(raiz.querySelector('.destacado')).toBeNull();
    expect(textos(raiz, '#proximos .ev-vacio h3')).toEqual(['No hay eventos próximos']);
    expect(textos(raiz, '#proximos .ev-vacio p')).toEqual(['Cuando el club anuncie uno, aparecerá aquí.']);
    const enlace = raiz.querySelector<HTMLAnchorElement>('#proximos .ev-vacio a')!;
    expect(enlace.textContent!.trim()).toBe('Ver los eventos anteriores');
    expect(enlace.getAttribute('href')).toBe('/eventos#anteriores');
    expect(raiz.querySelectorAll('.ev-mini').length).toBe(4);
  });

  it('solo próximos: «Todavía no hay eventos anteriores.»', async () => {
    const raiz = await abrir({ proximos: PROXIMOS, anteriores: [] });
    expect(raiz.querySelector('#anteriores')).not.toBeNull();
    expect(textos(raiz, '#anteriores p.ev-vacio')).toEqual(['Todavía no hay eventos anteriores.']);
  });

  it('ninguno: «No hay eventos próximos», sin enlace y sin la sección de anteriores', async () => {
    const raiz = await abrir({ proximos: [], anteriores: [] });
    expect(textos(raiz, '#proximos .ev-vacio h3')).toEqual(['No hay eventos próximos']);
    expect(raiz.querySelector('#proximos .ev-vacio a')).toBeNull();
    expect(raiz.querySelector('#anteriores')).toBeNull();
  });

  it('cabecera: «Eventos» es la página actual; SEO con un Event por próximo, con su descripción', async () => {
    const raiz = await abrir({ proximos: PROXIMOS, anteriores: ANTERIORES });
    const actual = raiz.querySelector<HTMLAnchorElement>('.nav-lista a[aria-current="page"]')!;
    expect(actual.getAttribute('href')).toBe('/eventos');
    expect(actual.textContent!.trim()).toBe('Eventos');
    expect(document.title).toBe('Eventos · Taekwondo Sierra Sur');
    const datos = jsonLd();
    expect(datos.map((d) => d['url'])).toEqual([1, 2, 3].map((id) => new URL(`/eventos/${id}`, location.origin).href));
    expect(datos[0]['description']).toBe(PROXIMOS[0].descripcion!);
  });

  it('vista previa: pide los eventos de la vista previa', async () => {
    await harness.navigateByUrl('/eventos?vista-previa=linea-comarcal');
    http.expectOne((r) => r.url === url && r.params.get('vistaPrevia') === 'true').flush(sitioConSedes(1));
    http
      .expectOne((r) => r.url === `${url}/eventos` && r.params.get('vistaPrevia') === 'true')
      .flush({ proximos: PROXIMOS, anteriores: [] });
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelectorAll('.ev-fila').length).toBe(2);
  });

  it('web sin publicar: «Web aún no publicada», sin el tema', async () => {
    await harness.navigateByUrl('/eventos');
    http.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne(`${url}/eventos`).flush(null, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    http
      .expectOne(`${environment.apiUrl}/publico/escuela`)
      .flush({ nombre: 'Taekwondo Sierra Sur', logo: 'NINGUNO', logoVersion: null, tema: null });
    harness.detectChanges();
    const raiz = harness.routeNativeElement!;
    expect(raiz.querySelector('.lc')).toBeNull();
    expect(raiz.querySelector('app-web-sin-publicar')).not.toBeNull();
  });

  it('fechaEvento: sin pasar por la zona horaria; null si no es una fecha', () => {
    expect(fechaEvento('2026-01-01')!.larga).toBe('jueves, 1 de enero de 2026');
    expect(fechaEvento('2026-02-30')).toBeNull();
    expect(fechaEvento(null)).toBeNull();
  });
});
