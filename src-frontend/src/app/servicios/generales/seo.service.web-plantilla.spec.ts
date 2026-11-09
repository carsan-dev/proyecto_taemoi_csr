import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { ponerMetaWebPlantilla, quedaMetaWebPlantilla } from '../../testing/meta-web-plantilla';
import { SeoService } from './seo.service';

@Component({ standalone: true, template: '' })
class PantallaFalsaComponent {}

/**
 * `SeoService.applyRouteSeo` (fase4-web-plan.md §3.2, punto 7): en una ruta con `data.webPlantilla` no toca nada
 * (título, meta, canonical, hreflang ni JSON-LD); la plantilla pone su SEO. En las demás, como hoy.
 *
 * W4, parte C: con la meta de `index-plantilla.html` no escribe nada de Moiskimdo en ninguna ruta (login, panel…), ni
 * al navegar ni por sus métodos públicos. Decisión 509: fuera de la web pública pone el nombre de la escuela como título
 * y un `noindex` marcado; en sus rutas lo quita. Sin la meta, como hoy.
 */
describe('SeoService · web por plantilla', () => {
  let tituloAnterior: string;
  let hijosAnteriores: Set<Element>;
  let atributosAnteriores: Map<Element, string>;
  let quitarMeta: (() => void) | null = null;

  function fotoDeLaCabeza(): string {
    return `${document.title}\n${document.head.innerHTML}`;
  }

  beforeEach(() => {
    tituloAnterior = document.title;
    hijosAnteriores = new Set(Array.from(document.head.children));
    atributosAnteriores = new Map(Array.from(document.head.children).map((e) => [e, e.outerHTML]));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          {
            path: 'plantilla',
            component: PantallaFalsaComponent,
            data: { webPlantilla: true, seo: { title: 'No debe salir', description: 'No debe salir', noIndex: true } },
          },
          { path: 'sedes/:slug', component: PantallaFalsaComponent, data: { webPlantilla: true } },
          { path: 'otra-publica', component: PantallaFalsaComponent, data: { webPlantilla: true } },
          { path: 'login', component: PantallaFalsaComponent, data: { seo: { title: 'Login de hoy', noIndex: true } } },
          { path: 'mi-caja', component: PantallaFalsaComponent },
          {
            path: 'taekwondo',
            component: PantallaFalsaComponent,
            data: { seo: { title: 'Taekwondo de hoy', description: 'Descripción de hoy' } },
          },
        ]),
      ],
    });
    TestBed.inject(SeoService);
  });

  afterEach(() => {
    quitarMeta?.();
    quitarMeta = null;
    expect(quedaMetaWebPlantilla()).withContext('la meta no se queda').toBeFalse();
    // Lo que el servicio haya añadido a la cabeza de Karma, fuera; lo que existía y haya cambiado, como estaba.
    for (const hijo of Array.from(document.head.children)) {
      if (!hijosAnteriores.has(hijo)) {
        hijo.remove();
      }
    }
    for (const [elemento, html] of atributosAnteriores) {
      if (elemento.outerHTML !== html && elemento.tagName === 'META') {
        const copia = document.createElement('template');
        copia.innerHTML = html;
        elemento.replaceWith(copia.content.firstElementChild!);
      }
    }
    document.title = tituloAnterior;
  });

  it('en una ruta de la plantilla no cambia nada de la cabeza (ni con data.seo propia)', async () => {
    const antes = fotoDeLaCabeza();
    await TestBed.inject(Router).navigateByUrl('/plantilla');
    expect(fotoDeLaCabeza()).toBe(antes);
    await TestBed.inject(Router).navigateByUrl('/sedes/casariche');
    expect(fotoDeLaCabeza()).toBe(antes);
    expect(document.querySelector('link[rel="canonical"][href*="sedes"]')).toBeNull();
  });

  it('fuera de la plantilla, como hoy: título, descripción y canonical de Moiskimdo', async () => {
    await TestBed.inject(Router).navigateByUrl('/taekwondo');
    expect(document.title).toBe('Taekwondo de hoy');
    expect(document.querySelector('meta[name="description"]')?.getAttribute('content')).toBe('Descripción de hoy');
    expect(document.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe('https://moiskimdo.es/taekwondo');
  });

  it('de una ruta de hoy a una de la plantilla: deja la cabeza como estaba (la plantilla pondrá la suya)', async () => {
    await TestBed.inject(Router).navigateByUrl('/taekwondo');
    const antes = fotoDeLaCabeza();
    await TestBed.inject(Router).navigateByUrl('/plantilla');
    expect(fotoDeLaCabeza()).toBe(antes);
  });

  const evento = { name: 'Campeonato', description: 'Descripción del evento', url: '/eventos/1' };
  const resenas = [{ author: 'Ana', rating: 5, text: 'Muy bien' }];

  describe('sin la meta (Moiskimdo), como hoy', () => {
    it('login y panel: título, robots y canonical de Moiskimdo', async () => {
      await TestBed.inject(Router).navigateByUrl('/login');
      expect(document.title).toBe('Login de hoy');
      expect(document.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe('noindex, nofollow');
      expect(document.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe('https://moiskimdo.es/login');
      await TestBed.inject(Router).navigateByUrl('/mi-caja');
      expect(document.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe('https://moiskimdo.es/mi-caja');
    });

    it('updateDynamicSeo, setEventSchema y setReviewsSchema escriben en la cabeza', async () => {
      await TestBed.inject(Router).navigateByUrl('/mi-caja');
      const seo = TestBed.inject(SeoService);
      seo.updateDynamicSeo({ title: 'Título dinámico', description: 'Descripción dinámica' });
      expect(document.title).toBe('Título dinámico');
      expect(document.querySelector('meta[property="og:title"]')?.getAttribute('content')).toBe('Título dinámico');
      seo.setEventSchema(evento);
      expect(document.querySelector('script[data-event-schema]')).not.toBeNull();
      seo.setReviewsSchema(resenas);
      expect(document.querySelector('script[data-reviews-schema]')).not.toBeNull();
      seo.removeEventSchema();
      expect(document.querySelector('script[data-event-schema]')).toBeNull();
    });
  });

  describe('con la meta (index-plantilla.html)', () => {
    beforeEach(() => {
      quitarMeta = ponerMetaWebPlantilla();
    });

    const robotsDeFuera = () => document.head.querySelectorAll('meta[name="robots"][data-web-plantilla="fuera"]');

    /** El servicio de la marca se carga en perezoso: espera a que pida `GET …/publico/escuela` y la responde. */
    async function responderEscuela(nombre: string | null): Promise<void> {
      const http = TestBed.inject(HttpTestingController);
      for (let i = 0; i < 100; i++) {
        const peticiones = http.match((r) => r.url.endsWith('/publico/escuela'));
        if (peticiones.length) {
          expect(peticiones.length).withContext('una sola petición').toBe(1);
          if (nombre === null) {
            peticiones[0].flush('error', { status: 500, statusText: 'Error' });
          } else {
            peticiones[0].flush({ nombre, logo: 'NINGUNO', logoVersion: null, tema: null });
          }
          await new Promise((r) => setTimeout(r, 0));
          return;
        }
        await new Promise((r) => setTimeout(r, 10));
      }
      fail('no pidió la escuela');
    }

    for (const url of ['/login', '/mi-caja']) {
      it(`${url} (login/panel): título vacío sin nombre, luego el de la escuela; noindex marcado; sin SEO de Moiskimdo`, async () => {
        document.title = 'Título de la página anterior';
        await TestBed.inject(Router).navigateByUrl(url);
        expect(document.title).toBe('');
        expect(robotsDeFuera().length).toBe(1);
        expect(robotsDeFuera()[0].getAttribute('content')).toBe('noindex, nofollow');
        expect(document.head.querySelectorAll('meta[name="robots"]').length).withContext('un solo robots').toBe(1);
        await responderEscuela('Taekwondo Sierra Sur');
        expect(document.title).toBe('Taekwondo Sierra Sur');
        expect(document.querySelector('link[rel="canonical"][href*="moiskimdo"]')).toBeNull();
        expect(document.querySelector('script[type="application/ld+json"][data-route-schema]')).toBeNull();
        expect(document.querySelector('meta[property="og:title"][content="Login de hoy"]')).toBeNull();
      });
    }

    it('si la API falla, título vacío (nunca Moiskimdo) y noindex igualmente', async () => {
      document.title = 'Título de la página anterior';
      await TestBed.inject(Router).navigateByUrl('/login');
      await responderEscuela(null);
      expect(document.title).toBe('');
      expect(robotsDeFuera().length).toBe(1);
    });

    it('a una ruta pública quita el noindex y no toca el título; al volver, vuelve (sin pedir la escuela otra vez)', async () => {
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/login');
      await responderEscuela('Club Prueba');
      document.title = 'Tarifas · Club Prueba';
      await router.navigateByUrl('/otra-publica');
      expect(robotsDeFuera().length).toBe(0);
      expect(document.head.querySelectorAll('meta[name="robots"][data-web-plantilla="fuera"]').length).toBe(0);
      expect(document.title).toBe('Tarifas · Club Prueba');
      await router.navigateByUrl('/mi-caja');
      expect(robotsDeFuera().length).toBe(1);
      expect(document.head.querySelectorAll('meta[name="robots"]').length).toBe(1);
      expect(document.title).toBe('Club Prueba');
      TestBed.inject(HttpTestingController).expectNone((r) => r.url.endsWith('/publico/escuela'));
    });

    it('una meta robots de la plantilla (la de SeoEscuelaService) no se quita al entrar en una ruta pública', async () => {
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/login');
      const propia = document.createElement('meta');
      propia.setAttribute('name', 'robots');
      propia.setAttribute('content', 'noindex, nofollow');
      propia.setAttribute('data-web-plantilla', '');
      document.head.appendChild(propia);
      await router.navigateByUrl('/otra-publica');
      expect(document.head.contains(propia)).toBeTrue();
    });

    it('/taekwondo (ruta de Moiskimdo en host de plantilla): tampoco escribe nada de Moiskimdo', async () => {
      await TestBed.inject(Router).navigateByUrl('/taekwondo');
      expect(document.title).not.toContain('Taekwondo de hoy');
      expect(document.querySelector('link[rel="canonical"][href*="moiskimdo"]')).toBeNull();
    });

    it('updateDynamicSeo, setEventSchema, setReviewsSchema y removeEventSchema no tocan la cabeza', async () => {
      await TestBed.inject(Router).navigateByUrl('/mi-caja');
      const ajeno = document.createElement('script');
      ajeno.type = 'application/ld+json';
      ajeno.setAttribute('data-event-schema', 'true');
      document.head.appendChild(ajeno);
      const antes = fotoDeLaCabeza();
      const seo = TestBed.inject(SeoService);
      seo.updateDynamicSeo({ title: 'No debe salir', description: 'No debe salir', noIndex: true });
      seo.setEventSchema(evento);
      seo.setReviewsSchema(resenas);
      seo.removeEventSchema();
      expect(fotoDeLaCabeza()).toBe(antes);
    });
  });
});
