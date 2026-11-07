import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { SeoService } from './seo.service';

@Component({ standalone: true, template: '' })
class PantallaFalsaComponent {}

/**
 * `SeoService.applyRouteSeo` (fase4-web-plan.md §3.2, punto 7): en una ruta con `data.webPlantilla` no toca nada
 * (título, meta, canonical, hreflang ni JSON-LD); la plantilla pone su SEO. En las demás, como hoy.
 */
describe('SeoService · web por plantilla', () => {
  let tituloAnterior: string;
  let hijosAnteriores: Set<Element>;
  let atributosAnteriores: Map<Element, string>;

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
});
