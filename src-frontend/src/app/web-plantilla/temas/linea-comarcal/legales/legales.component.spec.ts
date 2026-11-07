import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../../environments/environment';
import { RELOJ_WEB } from '../../../comun/reloj';
import { LEGAL_DE_PRUEBA, sitioConSedes } from '../../../datos/datos-de-prueba';
import { Legal } from '../../../datos/modelos';
import { WEB_PLANTILLA_ROUTES } from '../../../web-plantilla.routes';
import { LegalesLineaComarcalComponent } from './legales.component';
import { normasDe } from './legales';

@Component({ standalone: true, template: '<p class="login">login</p>' })
class LoginFalsoComponent {}

const AHORA = new Date(2026, 10, 2, 16, 0);

const NORMAS_TKD = [
  'Las clases empiezan y acaban a su hora: llega cinco minutos antes.',
  'Se entrena con dobok limpio, uñas cortas y sin pendientes, anillos ni relojes.',
];

function legal(cambios: Partial<Legal> = {}): Legal {
  return {
    ...LEGAL_DE_PRUEBA,
    normas: [
      { disciplina: 'TAEKWONDO', disciplinaNombre: 'Taekwondo', version: 2, contenido: { normas: NORMAS_TKD }, instrucciones: null },
    ],
    ...cambios,
  };
}

/**
 * Legales del tema «La línea comarcal» (W5, boceto `boceto-linea-comarcal-legales.html`): anclas, datos del titular
 * sustituidos, normas por disciplina, «Última actualización» y su ausencia en la vista previa sin publicar.
 */
describe('LegalesLineaComarcalComponent', () => {
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

  async function abrir(datos: Legal, ruta = '/legales'): Promise<HTMLElement> {
    await harness.navigateByUrl(ruta);
    http.expectOne((r) => r.url === url).flush(sitioConSedes(6));
    http.expectOne((r) => r.url === `${url}/legal`).flush(datos);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.routeNativeElement!;
  }

  const texto = (e: Element | null) => e!.textContent!.replace(/\s+/g, ' ').trim();
  const textos = (raiz: Element, selector: string) => Array.from(raiz.querySelectorAll(selector)).map(texto);

  it('las cuatro secciones con sus anclas y el índice que las enlaza', async () => {
    const raiz = await abrir(legal());
    expect(harness.routeDebugElement!.componentInstance).toBeInstanceOf(LegalesLineaComarcalComponent);
    expect(textos(raiz, 'h1.legal-titular')).toEqual(['Información legal']);
    expect(Array.from(raiz.querySelectorAll('.legal-seccion')).map((s) => s.id)).toEqual([
      'aviso-legal',
      'privacidad',
      'cookies',
      'normas',
    ]);
    expect(textos(raiz, '.legal-h2')).toEqual(['Aviso legal', 'Privacidad', 'Cookies', 'Normas del club']);
    const indice = Array.from(raiz.querySelectorAll<HTMLAnchorElement>('.indice-lista a'));
    expect(indice.map((a) => a.getAttribute('href'))).toEqual([
      '/legales#aviso-legal',
      '/legales#privacidad',
      '/legales#cookies',
      '/legales#normas',
    ]);
    expect(indice.map(texto)).toEqual(['Aviso legal', 'Privacidad', 'Cookies', 'Normas del club']);
    expect(indice[0].getAttribute('aria-current')).toBe('true');
    expect(textos(raiz, '.pie-legal a')).toEqual(['Aviso legal', 'Privacidad', 'Cookies']);
    expect(Array.from(raiz.querySelectorAll('.pie-legal a')).map((a) => a.getAttribute('href'))).toEqual([
      '/legales#aviso-legal',
      '/legales#privacidad',
      '/legales#cookies',
    ]);
  });

  it('datos del titular sustituidos en la ficha y en el texto; «Última actualización» con la fecha', async () => {
    const raiz = await abrir(legal());
    expect(textos(raiz, '.ficha dt')).toEqual(['Titular', 'CIF', 'Domicilio', 'Correo', 'Teléfono']);
    expect(textos(raiz, '.ficha dd')).toEqual([
      'Club Taekwondo Sierra Sur',
      'G00000000',
      'Calle Mayor 1, 41560 Estepa (Sevilla)',
      'info@example.com',
      '600 000 000',
    ]);
    expect(raiz.querySelector('.ficha a[href^="mailto:"]')!.getAttribute('href')).toBe('mailto:info@example.com');
    expect(raiz.querySelector('.ficha a[href^="tel:"]')!.getAttribute('href')).toBe('tel:+34600000000');
    const privacidad = raiz.querySelector('#privacidad')!;
    expect(texto(privacidad.querySelectorAll('p')[1])).toBe(
      'Club Taekwondo Sierra Sur, con CIF G00000000. Para cualquier cosa sobre tus datos, escribe a info@example.com.',
    );
    expect(textos(privacidad, 'p').some((p) => p.includes('pedir su portabilidad escribiendo a info@example.com. Si crees'))).toBeTrue();
    expect(texto(raiz.querySelector('#aviso-legal')!.querySelectorAll('p')[2])).toBe(
      'Los textos, fotos, logotipos y diseño de esta web son de Club Taekwondo Sierra Sur o de sus autores. No se pueden copiar ni usar con otros fines sin permiso.',
    );
    expect(texto(raiz.querySelector('.actualizado'))).toBe('Última actualización: 1 de noviembre de 2026');
    expect(raiz.querySelector('.actualizado time')!.getAttribute('datetime')).toBe('2026-11-01');
  });

  it('sin nombre legal, CIF ni correo: el nombre de la escuela y sin esas frases ni filas', async () => {
    const raiz = await abrir(
      legal({ titular: { ...LEGAL_DE_PRUEBA.titular, nombreLegal: null, cif: null, email: null, telefono: null, direccion: null } }),
    );
    expect(textos(raiz, '.ficha dt')).toEqual(['Titular', 'Domicilio']);
    expect(textos(raiz, '.ficha dd')).toEqual(['Taekwondo Sierra Sur', '41560 Estepa (Sevilla)']);
    const privacidad = raiz.querySelector('#privacidad')!;
    expect(texto(privacidad.querySelectorAll('p')[1])).toBe('Taekwondo Sierra Sur.');
    expect(textos(privacidad, 'p').some((p) => p.includes('pedir su portabilidad. Si crees'))).toBeTrue();
    expect(raiz.querySelector('#privacidad a[href^="mailto:"]')).toBeNull();
  });

  it('normas: la lista numerada de la disciplina y el enlace a Tarifas', async () => {
    const raiz = await abrir(legal());
    expect(textos(raiz, '#normas .normas-lista li')).toEqual(NORMAS_TKD);
    expect(raiz.querySelector('#normas .legal-h3')).toBeNull();
    expect(raiz.querySelector('#normas p a')!.getAttribute('href')).toBe('/tarifas');
  });

  it('normas de varias disciplinas: un título por disciplina; sin normas válidas, fuera', async () => {
    const raiz = await abrir(
      legal({
        normas: [
          { disciplina: 'TAEKWONDO', disciplinaNombre: 'Taekwondo', version: 1, contenido: JSON.stringify({ normas: ['Una'] }), instrucciones: null },
          { disciplina: 'KICKBOXING', disciplinaNombre: 'Kickboxing', version: 1, contenido: { normas: ['Dos', ' '] }, instrucciones: null },
          { disciplina: 'PILATES', disciplinaNombre: 'Pilates', version: 1, contenido: 'no es JSON', instrucciones: null },
        ],
      }),
    );
    expect(textos(raiz, '#normas .legal-h3')).toEqual(['Taekwondo', 'Kickboxing']);
    expect(textos(raiz, '#normas .normas-lista li')).toEqual(['Una', 'Dos']);
  });

  it('sin normas: sin la sección ni su entrada en el índice', async () => {
    const raiz = await abrir(legal({ normas: [] }));
    expect(raiz.querySelector('#normas')).toBeNull();
    expect(raiz.querySelectorAll('.indice-lista a').length).toBe(3);
  });

  it('vista previa sin publicar (actualizadaEn null): sin «Última actualización» y con noindex', async () => {
    await harness.navigateByUrl('/legales?vista-previa=linea-comarcal');
    http.expectOne((r) => r.url === url && r.params.get('vistaPrevia') === 'true').flush(sitioConSedes(1));
    http
      .expectOne((r) => r.url === `${url}/legal` && r.params.get('vistaPrevia') === 'true')
      .flush(legal({ actualizadaEn: null }));
    harness.detectChanges();
    const raiz = harness.routeNativeElement!;
    expect(raiz.querySelector('.legal-titular')).not.toBeNull();
    expect(raiz.querySelector('.actualizado')).toBeNull();
    expect(document.head.querySelector('meta[name="robots"][data-web-plantilla]')!.getAttribute('content')).toBe(
      'noindex, nofollow',
    );
    expect(raiz.querySelector('.indice-lista a')!.getAttribute('href')).toBe(
      '/legales?vista-previa=linea-comarcal#aviso-legal',
    );
  });

  it('SEO: título de legales y canonical', async () => {
    await abrir(legal());
    expect(document.title).toBe('Aviso legal y privacidad · Taekwondo Sierra Sur');
    expect(document.head.querySelector('link[rel="canonical"][data-web-plantilla]')!.getAttribute('href')).toBe(
      new URL('/legales', location.origin).href,
    );
  });

  it('web sin publicar: «Web aún no publicada», sin el tema', async () => {
    await harness.navigateByUrl('/legales');
    http.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne(`${url}/legal`).flush(null, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    http
      .expectOne(`${environment.apiUrl}/publico/escuela`)
      .flush({ nombre: 'Taekwondo Sierra Sur', logo: 'NINGUNO', logoVersion: null, tema: null });
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('.lc')).toBeNull();
    expect(harness.routeNativeElement!.querySelector('app-web-sin-publicar')).not.toBeNull();
  });

  it('normasDe: objeto o JSON en texto, sin vacías', () => {
    expect(normasDe({ contenido: { normas: [' a ', '', 3, 'b'] } })).toEqual(['a', 'b']);
    expect(normasDe({ contenido: '{"normas":["c"]}' })).toEqual(['c']);
    expect(normasDe({ contenido: null })).toEqual([]);
    expect(normasDe({ contenido: '{' })).toEqual([]);
  });
});
