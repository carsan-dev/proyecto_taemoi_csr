import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../environments/environment';
import { LEGAL_DE_PRUEBA, sitioDePrueba } from '../datos/datos-de-prueba';
import { SedeWeb } from '../datos/modelos';
import { WEB_PLANTILLA_ROUTES } from '../web-plantilla.routes';

@Component({ standalone: true, template: '' })
class FueraDeLaPlantillaComponent {}

function sede(id: number, nombre: string, slug: string): SedeWeb {
  return {
    id,
    nombre,
    direccion: null,
    localidad: nombre,
    codigoPostal: null,
    telefono: null,
    slug,
    lugar: null,
    indicacionesAcceso: null,
    fotoUrl: null,
    foto2Url: null,
    grupos: [],
  };
}

const SITIO = sitioDePrueba({
  sedes: [sede(1, 'Estepa', 'estepa'), sede(2, 'Casariche', 'casariche')],
  proximosEventos: [
    { id: 7, titulo: 'Exhibición', fecha: '2026-12-19', conImagen: false, hora: null, lugar: null, sedeId: 1, sedeNombre: 'Estepa' },
  ],
});

/**
 * Pantalla técnica de la plantilla (W4): carga los datos de su ruta y pone el SEO de su página. Sin UI: solo
 * `<main id="contenido"></main>` vacío (portada, sede y tarifas ya son del tema, W5); sin publicar, la página «Web aún no
 * publicada».
 */
describe('PlantillaPendienteComponent · datos y SEO de cada página', () => {
  const url = `${environment.apiUrl}/publico/web`;
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let tituloAnterior: string;

  function propias(): Element[] {
    return Array.from(document.head.querySelectorAll('[data-web-plantilla]'));
  }

  function canonical(): string | null {
    return document.head.querySelector('link[rel="canonical"][data-web-plantilla]')?.getAttribute('href') ?? null;
  }

  function robots(): string | null {
    return document.head.querySelector('meta[name="robots"][data-web-plantilla]')?.getAttribute('content') ?? null;
  }

  function jsonLd(): Record<string, unknown>[] {
    return Array.from(document.head.querySelectorAll('script[type="application/ld+json"][data-web-plantilla]')).map(
      (s) => JSON.parse(s.textContent ?? '') as Record<string, unknown>,
    );
  }

  async function ir(ruta: string): Promise<void> {
    await harness.navigateByUrl(ruta);
    harness.detectChanges();
  }

  beforeEach(async () => {
    tituloAnterior = document.title;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([...WEB_PLANTILLA_ROUTES, { path: 'login', component: FueraDeLaPlantillaComponent }]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify();
    for (const e of propias()) {
      e.remove();
    }
    document.title = tituloAnterior;
  });

  it('una página aún sin tema (preinscripción): una petición, su SEO y la plantilla vacía', async () => {
    await ir('/preinscripcion');
    expect(document.title).toBe(tituloAnterior);
    http.expectOne(url).flush(SITIO);
    harness.detectChanges();

    expect(document.title).toBe('Preinscripción · Taekwondo Sierra Sur');
    expect(canonical()).toBe(`${location.origin}/preinscripcion`);
    expect(jsonLd()).toEqual([]);
    const main = harness.routeNativeElement!.querySelector('main#contenido')!;
    expect(main).not.toBeNull();
    expect(main.innerHTML).toBe('');
    expect(harness.routeNativeElement!.textContent).toBe('');
  });

  it('de página en página: los datos se piden una sola vez y el SEO cambia con cada una', async () => {
    await ir('/eventos/7');
    http.expectOne(url).flush(SITIO);
    harness.detectChanges();
    expect(document.title).toBe('Exhibición · Taekwondo Sierra Sur');
    expect(jsonLd().map((d) => d['@type'])).toEqual(['Event']);

    await ir('/eventos');
    expect(document.title).toBe('Eventos · Taekwondo Sierra Sur');
    expect(canonical()).toBe(`${location.origin}/eventos`);

    await ir('/preinscripcion?sede=estepa');
    expect(document.title).toBe('Preinscripción · Taekwondo Sierra Sur');
    expect(canonical()).toBe(`${location.origin}/preinscripcion`);

    http.expectNone(url);
    expect(document.head.querySelectorAll('link[rel="canonical"][data-web-plantilla]').length).toBe(1);
  });

  it('legales: pide también /legal y pone su SEO', async () => {
    await ir('/legales');
    http.expectOne(url).flush(SITIO);
    http.expectOne(`${url}/legal`).flush(LEGAL_DE_PRUEBA);
    harness.detectChanges();
    expect(document.title).toBe('Aviso legal y privacidad · Taekwondo Sierra Sur');
    expect(canonical()).toBe(`${location.origin}/legales`);
  });

  it('vista previa: pide vistaPrevia=true con el tema y pone noindex', async () => {
    await ir('/eventos?vista-previa=chandal');
    const peticion = http.expectOne((r) => r.url === url);
    expect(peticion.request.params.get('vistaPrevia')).toBe('true');
    expect(peticion.request.params.get('tema')).toBe('chandal');
    peticion.flush(sitioDePrueba({ tema: 'chandal', vistaPrevia: true, publicada: false }));
    harness.detectChanges();

    expect(robots()).toBe('noindex, nofollow');
    expect(canonical()).toBe(`${location.origin}/eventos`);
  });

  it('web no publicada (404): solo noindex y la página «Web aún no publicada» en la misma URL', async () => {
    await ir('/eventos');
    http.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();
    await harness.fixture.whenStable();
    http
      .expectOne(`${environment.apiUrl}/publico/escuela`)
      .flush({ nombre: 'Taekwondo Sierra Sur', logo: 'NINGUNO', logoVersion: null, tema: null });
    harness.detectChanges();
    expect(robots()).toBe('noindex, nofollow');
    expect(canonical()).toBeNull();
    const raiz = harness.routeNativeElement!;
    expect(raiz.querySelector('main#contenido')).toBeNull();
    expect(raiz.querySelector('app-web-sin-publicar .sp-nombre')?.textContent).toBe('Taekwondo Sierra Sur');
    expect(TestBed.inject(Router).url).toBe('/eventos');
  });

  it('al salir de la plantilla quita todas sus etiquetas', async () => {
    await ir('/eventos');
    http.expectOne(url).flush(SITIO);
    harness.detectChanges();
    expect(propias().length).toBeGreaterThan(0);

    await ir('/login');
    expect(propias()).toEqual([]);
  });
});
