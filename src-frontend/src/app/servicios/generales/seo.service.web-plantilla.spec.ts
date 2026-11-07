import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
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
 * al navegar ni por sus métodos públicos. Sin la meta, como hoy.
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
        provideRouter([
          {
            path: 'plantilla',
            component: PantallaFalsaComponent,
            data: { webPlantilla: true, seo: { title: 'No debe salir', description: 'No debe salir', noIndex: true } },
          },
          { path: 'sedes/:slug', component: PantallaFalsaComponent, data: { webPlantilla: true } },
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

    for (const url of ['/login', '/mi-caja', '/taekwondo']) {
      it(`${url}: no cambia nada de la cabeza (título, meta, canonical, hreflang ni JSON-LD)`, async () => {
        const antes = fotoDeLaCabeza();
        await TestBed.inject(Router).navigateByUrl(url);
        expect(fotoDeLaCabeza()).toBe(antes);
        expect(document.querySelector('link[rel="canonical"][href*="moiskimdo"]')).toBeNull();
        expect(document.querySelector('script[type="application/ld+json"][data-route-schema]')).toBeNull();
      });
    }

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
