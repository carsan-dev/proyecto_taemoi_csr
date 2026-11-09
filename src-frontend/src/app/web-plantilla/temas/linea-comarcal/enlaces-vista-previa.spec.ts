import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../environments/environment';
import { RELOJ_WEB } from '../../comun/reloj';
import { sitioConSedes } from '../../datos/datos-de-prueba';
import { WEB_PLANTILLA_ROUTES } from '../../web-plantilla.routes';

/** La vista previa del panel (iframe) no debe perder `?vista-previa=` al navegar por la plantilla. */
describe('Enlaces internos del tema con vista previa', () => {
  const url = `${environment.apiUrl}/publico/web`;
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(WEB_PLANTILLA_ROUTES),
        { provide: RELOJ_WEB, useValue: () => new Date(2026, 10, 2, 16, 0) },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify();
    document.body.classList.remove('lc-menu-abierto');
    for (const elemento of Array.from(document.head.querySelectorAll('[data-web-plantilla]'))) {
      elemento.remove();
    }
  });

  async function abrir(ruta: string): Promise<HTMLElement> {
    await harness.navigateByUrl(ruta);
    http.expectOne((r) => r.url === url).flush(sitioConSedes(6));
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.routeNativeElement!;
  }

  const hrefs = (raiz: HTMLElement, selector: string) =>
    Array.from(raiz.querySelectorAll<HTMLAnchorElement>(selector)).map((a) => a.getAttribute('href'));

  it('con el parámetro, el marco y la portada lo conservan (y no arrastran otros)', async () => {
    const raiz = await abrir('/?vista-previa=linea-comarcal&otro=1');
    expect(hrefs(raiz, 'a.marca')).toEqual(['/?vista-previa=linea-comarcal']);
    expect(hrefs(raiz, 'a.cabecera-cta')).toEqual(['/preinscripcion?vista-previa=linea-comarcal']);
    expect(hrefs(raiz, '.acciones .boton')).toEqual(['/?vista-previa=linea-comarcal#linea']);
    expect(hrefs(raiz, '.acciones .enlace-flecha')).toEqual(['/?vista-previa=linea-comarcal#tarifas']);
  });

  it('con el parámetro vacío también lo conservan', async () => {
    const raiz = await abrir('/?vista-previa=');
    expect(hrefs(raiz, 'a.cabecera-cta')).toEqual(['/preinscripcion?vista-previa=']);
  });

  it('sin el parámetro, los enlaces son los de la web publicada', async () => {
    const raiz = await abrir('/?otro=1');
    expect(hrefs(raiz, 'a.marca')).toEqual(['/']);
    expect(hrefs(raiz, 'a.cabecera-cta')).toEqual(['/preinscripcion']);
    expect(hrefs(raiz, '.acciones .boton')).toEqual(['/#linea']);
    expect(hrefs(raiz, '.acciones .enlace-flecha')).toEqual(['/#tarifas']);
  });
});
