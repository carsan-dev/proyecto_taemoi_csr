import { DatePipe } from '@angular/common';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { appConfig } from './app.config';

/**
 * `LOCALE_ID 'es-ES'` global (decisión 258, 8b-Z). Las pantallas que no se rediseñaron (plataforma) usan el pipe de
 * fecha solo con patrones numéricos: con el español global dan lo mismo que antes.
 */
describe('appConfig: LOCALE_ID es-ES global (8b-Z)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: appConfig.providers.filter((p) => (p as { provide?: unknown })?.provide === LOCALE_ID),
    });
  });

  it('da es-ES como LOCALE_ID de la aplicación', () => {
    expect(TestBed.inject(LOCALE_ID)).toBe('es-ES');
  });

  it('el pipe de fecha con los patrones de plataforma-errores da lo mismo que antes', () => {
    const pipe = TestBed.runInInjectionContext(() => new DatePipe(TestBed.inject(LOCALE_ID)));
    const fecha = new Date(2026, 9, 29, 8, 5, 9);
    expect(pipe.transform(fecha, 'HH:mm:ss')).toBe('08:05:09');
    expect(pipe.transform(fecha, 'dd/MM/yyyy')).toBe('29/10/2026');
    expect(pipe.transform(fecha, 'yyyy-MM-dd')).toBe('2026-10-29');
  });

  it('con los datos del español registrados (meses en español)', () => {
    const pipe = new DatePipe('es-ES');
    expect(pipe.transform(new Date(2026, 9, 29), 'MMMM')).toBe('octubre');
  });
});
