import { TestBed } from '@angular/core/testing';
import { Route, UrlSegment } from '@angular/router';

import { esWebPlantilla, hayMetaWebPlantilla } from './web-plantilla.guard';
import { ponerMetaWebPlantilla, quedaMetaWebPlantilla } from '../testing/meta-web-plantilla';

describe('esWebPlantilla (fase4-web-plan.md §3.2)', () => {
  let quitarMeta: (() => void) | null = null;

  const ejecutar = () => TestBed.runInInjectionContext(() => esWebPlantilla({} as Route, [] as UrlSegment[]));

  afterEach(() => {
    quitarMeta?.();
    quitarMeta = null;
    expect(quedaMetaWebPlantilla()).withContext('la meta no se queda').toBeFalse();
  });

  it('sin la meta (index.html de Moiskimdo): no casa', () => {
    expect(ejecutar()).toBeFalse();
    expect(hayMetaWebPlantilla(document)).toBeFalse();
  });

  it('con la meta de index-plantilla.html: casa, de forma síncrona (booleano, no observable ni promesa)', () => {
    quitarMeta = ponerMetaWebPlantilla();
    expect(ejecutar()).toBeTrue();
    expect(hayMetaWebPlantilla(document)).toBeTrue();
  });

  it('una meta taemoi-web con otro valor no casa', () => {
    quitarMeta = ponerMetaWebPlantilla('otra');
    expect(ejecutar()).toBeFalse();
  });
});
