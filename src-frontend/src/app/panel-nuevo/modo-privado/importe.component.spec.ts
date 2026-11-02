import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';

import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { ImporteComponent } from './importe.component';
import { ModoPrivadoService } from './modo-privado.service';

@Component({
  standalone: true,
  imports: [ImporteComponent],
  template: `<p style="font-variant-numeric: tabular-nums">Total <pn-importe>12,00 €</pn-importe></p>
    <pn-importe bloque><div class="grafica"></div></pn-importe>`,
})
class PaginaFalsa {}

describe('ImporteComponent (decisión 419)', () => {
  let fixture: ComponentFixture<PaginaFalsa>;
  const obtener = jasmine.createSpy('obtener');

  beforeEach(() => {
    obtener.calls.reset();
    obtener.and.returnValue(of({ importesTapados: true } as PermisosSesion));
    TestBed.configureTestingModule({
      imports: [PaginaFalsa],
      providers: [{ provide: PermisosSesionService, useValue: { obtener, generacion: 0, refrescado$: new Subject<void>() } }],
    });
    fixture = TestBed.createComponent(PaginaFalsa);
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const cifra = () => el().querySelector<HTMLElement>('.importe')!;
  const oculto = () => el().querySelector<HTMLElement>('.importe__oculto');

  it('carga la preferencia al iniciarse: puede pintarse antes que el botón', () => {
    fixture.detectChanges();

    expect(obtener).toHaveBeenCalled();
  });

  it('tapado: difumina, deja de ser seleccionable y el lector de pantalla oye «Importe oculto»', () => {
    fixture.detectChanges();

    expect(cifra().classList).toContain('importe--tapado');
    expect(cifra().getAttribute('aria-hidden')).toBe('true');
    expect(getComputedStyle(cifra()).filter).toContain('blur(7px)');
    expect(getComputedStyle(cifra()).userSelect).toBe('none');
    expect(oculto()?.textContent).toBe('Importe oculto');
  });

  it('el texto oculto no ocupa sitio: no mueve la línea', () => {
    fixture.detectChanges();

    expect(getComputedStyle(oculto()!).position).toBe('absolute');
    expect(oculto()!.getBoundingClientRect().width).toBeLessThanOrEqual(1);
  });

  it('destapado: la cifra tal cual, sin nada añadido', () => {
    fixture.detectChanges();
    TestBed.inject(ModoPrivadoService).alternar();
    fixture.detectChanges();

    expect(cifra().classList).not.toContain('importe--tapado');
    expect(cifra().hasAttribute('aria-hidden')).toBeFalse();
    expect(oculto()).toBeNull();
    expect(el().querySelector('p')!.textContent).toBe('Total 12,00 €');
  });

  it('hereda del padre y va en línea: no rompe la tipografía ni la alineación', () => {
    fixture.detectChanges();
    const anfitrion = el().querySelector<HTMLElement>('pn-importe')!;

    expect(getComputedStyle(anfitrion).display).toBe('inline');
    expect(getComputedStyle(cifra()).fontVariantNumeric).toBe('tabular-nums');
  });

  it('con «bloque» envuelve una gráfica entera sin dejar de ser un bloque', () => {
    fixture.detectChanges();
    const anfitrion = el().querySelector<HTMLElement>('pn-importe[bloque]')!;

    expect(getComputedStyle(anfitrion).display).toBe('block');
    expect(getComputedStyle(anfitrion.querySelector('.importe')!).display).toBe('block');
  });
});
