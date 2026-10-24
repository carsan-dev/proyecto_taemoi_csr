import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';

import { ponerTokensDePrueba } from '../capas/tokens-prueba';
import { BloqueErrorComponent } from './bloque-error.component';
import { BloqueVacioComponent } from './bloque-vacio.component';
import { BotonOcupadoComponent } from './boton-ocupado.component';
import { EsqueletoComponent, RETRASO_ESQUELETO } from './esqueleto.component';

@Component({
  standalone: true,
  imports: [EsqueletoComponent, BotonOcupadoComponent, BloqueErrorComponent, BloqueVacioComponent],
  template: `
    <div class="pn">
      @if (cargando) {
      <pn-esqueleto etiqueta="Cargando alumnos"><span class="hueso"></span></pn-esqueleto>
      }
      <button type="button" pnOcupado [ocupado]="ocupado" textoOcupado="Cobrando…" style="padding: 0 16px"
        (click)="pulsados = pulsados + 1">Cobrar</button>
      <pn-bloque-error titulo="No se pudo cargar la ocupación" (reintentar)="reintentos = reintentos + 1" />
      <pn-bloque-vacio titulo="Ningún alumno coincide con la búsqueda"
        texto="Prueba con otro nombre o quita los filtros de disciplina e inactivos." accion="Quitar filtros"
        (accionar)="quitados = quitados + 1" />
    </div>
  `,
})
class Anfitrion {
  cargando = true;
  ocupado = false;
  pulsados = 0;
  reintentos = 0;
  quitados = 0;
}

describe('Carga del panel nuevo (8b-D1): esqueleto, botón ocupado, error y vacío', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let quitarTokens: () => void;

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function movimiento(reducir: boolean): void {
    spyOn(window, 'matchMedia').and.callFake(
      (consulta: string) =>
        ({
          matches: consulta.includes('prefers-reduced-motion') ? reducir : false,
          media: consulta,
          addEventListener: () => undefined,
          removeEventListener: () => undefined,
        }) as unknown as MediaQueryList
    );
  }

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    TestBed.configureTestingModule({ imports: [Anfitrion] });
  });

  afterEach(() => {
    fixture?.destroy();
    quitarTokens();
  });

  function crear(): void {
    fixture = TestBed.createComponent(Anfitrion);
    document.body.appendChild(el());
    fixture.detectChanges();
  }

  it('esqueleto: no aparece hasta los 300 ms; luego, la forma oculta a lectores y un aviso «Cargando…»', fakeAsync(() => {
    movimiento(false);
    crear();
    expect(RETRASO_ESQUELETO).toBe(300);
    expect(el().querySelector('.pn-esqueleto__forma')).toBeNull();
    tick(299);
    fixture.detectChanges();
    expect(el().querySelector('.pn-esqueleto__forma')).toBeNull();
    tick(1);
    fixture.detectChanges();
    const forma = el().querySelector<HTMLElement>('.pn-esqueleto__forma')!;
    expect(forma.getAttribute('aria-hidden')).toBe('true');
    expect(forma.querySelector('.hueso')).not.toBeNull();
    expect(el().querySelector('pn-esqueleto [role="status"]')!.textContent!.trim()).toBe('Cargando alumnos');
    expect(getComputedStyle(forma).animationName).toContain('pn-pulso');
    expect(getComputedStyle(forma).animationDuration).toBe('1.6s');
  }));

  it('esqueleto con menos movimiento: sin pulso', fakeAsync(() => {
    movimiento(true);
    crear();
    tick(RETRASO_ESQUELETO);
    fixture.detectChanges();
    const forma = el().querySelector<HTMLElement>('.pn-esqueleto__forma')!;
    expect(forma.classList).toContain('pn-esqueleto__forma--quieta');
    expect(getComputedStyle(forma).animationName).toBe('none');
  }));

  it('esqueleto: si la carga acaba antes de 300 ms, no llega a verse', fakeAsync(() => {
    movimiento(false);
    crear();
    tick(200);
    fixture.componentInstance.cargando = false;
    fixture.detectChanges();
    tick(200);
    fixture.detectChanges();
    expect(el().querySelector('pn-esqueleto')).toBeNull();
  }));

  it('botón ocupado: mismo ancho, indicador de 16 px, aria-disabled sin perder el foco, aria-busy y «Cobrando…»', () => {
    crear();
    const boton = el().querySelector<HTMLButtonElement>('button[pnOcupado]')!;
    boton.style.width = '';
    const ancho = boton.getBoundingClientRect().width;
    expect(boton.disabled).toBeFalse();
    expect(boton.getAttribute('aria-busy')).toBeNull();
    expect(boton.getAttribute('aria-disabled')).toBeNull();
    boton.focus();
    fixture.componentInstance.ocupado = true;
    fixture.detectChanges();
    // Deshabilitado con aria-disabled y no con `disabled`: el foco se queda en el botón (no cae al body) y el clic no llega.
    expect(boton.disabled).toBeFalse();
    expect(boton.getAttribute('aria-disabled')).toBe('true');
    expect(document.activeElement).toBe(boton);
    boton.click();
    boton.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    expect(fixture.componentInstance.pulsados).toBe(0);
    expect(boton.getAttribute('aria-busy')).toBe('true');
    expect(boton.textContent!.trim()).toBe('Cobrando…');
    const indicador = boton.querySelector('svg')!;
    expect(indicador.getAttribute('aria-hidden')).toBe('true');
    expect(indicador.getBoundingClientRect().width).toBe(16);
    expect(boton.getBoundingClientRect().width).toBeCloseTo(ancho, 0);
    fixture.componentInstance.ocupado = false;
    fixture.detectChanges();
    expect(boton.textContent!.trim()).toBe('Cobrar');
    expect(boton.disabled).toBeFalse();
    expect(boton.getAttribute('aria-disabled')).toBeNull();
    boton.click();
    expect(fixture.componentInstance.pulsados).toBe(1);
  });

  it('bloque de error: título, cómo seguir y «Reintentar»', () => {
    crear();
    const bloque = el().querySelector<HTMLElement>('pn-bloque-error')!;
    expect(bloque.querySelector('[role="alert"]')).not.toBeNull();
    expect(bloque.textContent).toContain('No se pudo cargar la ocupación');
    expect(bloque.textContent).toContain('Comprueba la conexión y vuelve a intentarlo.');
    const boton = bloque.querySelector<HTMLButtonElement>('button')!;
    expect(boton.textContent!.trim()).toBe('Reintentar');
    boton.click();
    expect(fixture.componentInstance.reintentos).toBe(1);
  });

  it('bloque vacío: role=status, el texto y su acción', () => {
    crear();
    const bloque = el().querySelector<HTMLElement>('pn-bloque-vacio [role="status"]')!;
    expect(bloque.textContent).toContain('Ningún alumno coincide con la búsqueda');
    expect(bloque.textContent).toContain('Prueba con otro nombre o quita los filtros de disciplina e inactivos.');
    const boton = bloque.querySelector<HTMLButtonElement>('button')!;
    expect(boton.textContent!.trim()).toBe('Quitar filtros');
    boton.click();
    expect(fixture.componentInstance.quitados).toBe(1);
  });
});

@Component({
  standalone: true,
  imports: [BloqueErrorComponent, BloqueVacioComponent],
  template: `
    <div class="pn">
      <pn-bloque-vacio pose="buscar" titulo="Ningún alumno coincide con la búsqueda" texto="Prueba con otro nombre." />
      <pn-bloque-error pose="dudar" titulo="No se pudo cargar el listado" />
    </div>
  `,
})
class AnfitrionConMascota {}

describe('Carga del panel nuevo con mascota (8b-M)', () => {
  let quitarTokens: () => void;

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    TestBed.configureTestingModule({ imports: [AnfitrionConMascota, Anfitrion] });
  });

  afterEach(() => {
    quitarTokens();
  });

  it('el vacío con pose cambia el icono por la gota de 96 px decorativa; el título y el texto siguen diciendo lo mismo', () => {
    const fixture = TestBed.createComponent(AnfitrionConMascota);
    fixture.detectChanges();
    const vacio = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('pn-bloque-vacio')!;
    const gota = vacio.querySelector<HTMLElement>('pn-mascota')!;
    expect(gota.classList).toContain('pn-mascota--md');
    expect(gota.getAttribute('aria-hidden')).toBe('true');
    expect(vacio.querySelector('.pn-bloque-vacio__icono')).toBeNull();
    expect(vacio.querySelector('[role="status"]')!.textContent).toContain('Ningún alumno coincide con la búsqueda');
    expect(vacio.querySelector('[role="status"]')!.textContent).toContain('Prueba con otro nombre.');
    fixture.destroy();
  });

  it('el error con pose cambia el icono por la gota de 144 px decorativa; el aviso sigue siendo role=alert con su texto', () => {
    const fixture = TestBed.createComponent(AnfitrionConMascota);
    fixture.detectChanges();
    const error = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('pn-bloque-error')!;
    const gota = error.querySelector<HTMLElement>('pn-mascota')!;
    expect(gota.classList).toContain('pn-mascota--lg');
    expect(gota.getAttribute('aria-hidden')).toBe('true');
    expect(error.querySelector('.pn-bloque-error__icono')).toBeNull();
    expect(error.querySelector('[role="alert"]')!.textContent).toContain('No se pudo cargar el listado');
    fixture.destroy();
  });

  it('sin pose siguen con el icono de siempre (dinero, bajas y errores serios no llevan mascota)', () => {
    const base = TestBed.createComponent(Anfitrion);
    base.detectChanges();
    const el = base.nativeElement as HTMLElement;
    expect(el.querySelector('pn-mascota')).toBeNull();
    expect(el.querySelector('.pn-bloque-vacio__icono')).not.toBeNull();
    expect(el.querySelector('.pn-bloque-error__icono')).not.toBeNull();
    base.destroy();
  });
});
