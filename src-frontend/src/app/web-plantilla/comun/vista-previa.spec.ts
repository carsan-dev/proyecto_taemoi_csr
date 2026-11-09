import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterLink, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { VistaPreviaPipe, conVistaPrevia } from './vista-previa';

@Component({
  standalone: true,
  imports: [RouterLink, VistaPreviaPipe],
  template: `
    <a id="solo" routerLink="/tarifas" [queryParams]="null | vistaPrevia">solo</a>
    <a id="propios" routerLink="/preinscripcion" [queryParams]="{ sede: 'casariche' } | vistaPrevia">propios</a>
    <a id="ancla" routerLink="/" fragment="linea" [queryParams]="null | vistaPrevia">ancla</a>
  `,
})
class PaginaFalsaComponent {}

describe('conVistaPrevia', () => {
  it('sin vista previa devuelve los parámetros propios tal cual, o null', () => {
    const propios = { sede: 'casariche' };
    expect(conVistaPrevia(propios, null)).toBe(propios);
    expect(conVistaPrevia(null, null)).toBeNull();
    expect(conVistaPrevia(undefined, null)).toBeNull();
  });

  it('con vista previa la añade, también vacía, y fusiona con los propios', () => {
    expect(conVistaPrevia(null, 'linea-comarcal')).toEqual({ 'vista-previa': 'linea-comarcal' });
    expect(conVistaPrevia(undefined, '')).toEqual({ 'vista-previa': '' });
    expect(conVistaPrevia({ paso: 2, sede: 'casariche' }, 'x')).toEqual({ paso: 2, sede: 'casariche', 'vista-previa': 'x' });
  });
});

describe('VistaPreviaPipe', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', component: PaginaFalsaComponent }])] });
    harness = await RouterTestingHarness.create();
  });

  const href = (id: string) => harness.routeNativeElement!.querySelector<HTMLAnchorElement>(`#${id}`)!.getAttribute('href');

  it('sin el parámetro los enlaces quedan como siempre', async () => {
    await harness.navigateByUrl('/?otro=1');
    expect(href('solo')).toBe('/tarifas');
    expect(href('propios')).toBe('/preinscripcion?sede=casariche');
    expect(href('ancla')).toBe('/#linea');
  });

  it('con el parámetro lo conservan, fusionado con los propios y sin arrastrar otros', async () => {
    await harness.navigateByUrl('/?vista-previa=linea-comarcal&otro=1');
    expect(href('solo')).toBe('/tarifas?vista-previa=linea-comarcal');
    expect(href('propios')).toBe('/preinscripcion?sede=casariche&vista-previa=linea-comarcal');
    expect(href('ancla')).toBe('/?vista-previa=linea-comarcal#linea');
  });

  it('un valor vacío también cuenta como vista previa', async () => {
    await harness.navigateByUrl('/?vista-previa=');
    expect(href('solo')).toBe('/tarifas?vista-previa=');
  });

  it('al navegar a una página sin el parámetro lo deja de llevar', async () => {
    await harness.navigateByUrl('/?vista-previa=x');
    expect(href('solo')).toBe('/tarifas?vista-previa=x');
    await harness.navigateByUrl('/tarifas');
    expect(href('solo')).toBe('/tarifas');
  });
});
