import { Component, ViewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CONSULTA_HOJA } from '../capas/capa-modal';
import { ponerTokensDePrueba } from '../capas/tokens-prueba';
import { PanelLateralComponent } from './panel-lateral.component';

@Component({
  standalone: true,
  imports: [PanelLateralComponent],
  template: `
    <div class="pn">
      <button type="button" class="disparador" (click)="abierto = true">Listado de asistencia</button>
      @if (abierto) {
      <pn-panel-lateral titulo="Listado de asistencia" descripcion="Genera un PDF por cada día elegido."
        (cerrar)="cerrados = cerrados + 1; abierto = false">
        <label for="campo">Mes</label><select id="campo"><option>Octubre</option></select>
        <div pnPie><button type="button" class="ver">Ver PDF</button><button type="button" class="descargar">Descargar PDF</button></div>
      </pn-panel-lateral>
      }
    </div>
  `,
})
class Anfitrion {
  abierto = false;
  cerrados = 0;
  @ViewChild(PanelLateralComponent) panel?: PanelLateralComponent;
}

describe('PanelLateralComponent (8b-D1, panel lateral de 480 px y hoja en el móvil)', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let quitarTokens: () => void;

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function dialogo(): HTMLDialogElement {
    return el().querySelector<HTMLDialogElement>('dialog.pn-panel')!;
  }

  function ancho(hoja: boolean): void {
    spyOn(window, 'matchMedia').and.callFake(
      (consulta: string) =>
        ({
          matches: consulta === CONSULTA_HOJA ? hoja : false,
          media: consulta,
          addEventListener: () => undefined,
          removeEventListener: () => undefined,
        }) as unknown as MediaQueryList
    );
  }

  function abrir(): HTMLButtonElement {
    fixture = TestBed.createComponent(Anfitrion);
    document.body.appendChild(el());
    fixture.detectChanges();
    const disparador = el().querySelector<HTMLButtonElement>('.disparador')!;
    disparador.focus();
    disparador.click();
    fixture.detectChanges();
    return disparador;
  }

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    TestBed.configureTestingModule({ imports: [Anfitrion] });
  });

  afterEach(() => {
    fixture?.destroy();
    el()?.remove();
    quitarTokens();
  });

  it('se abre como modal, con título y descripción enlazados, y el foco dentro', () => {
    ancho(false);
    abrir();
    const d = dialogo();
    expect(d.open).toBeTrue();
    expect(d.matches(':modal')).toBeTrue();
    expect(document.getElementById(d.getAttribute('aria-labelledby')!)!.textContent!.trim()).toBe('Listado de asistencia');
    expect(document.getElementById(d.getAttribute('aria-describedby')!)!.textContent!.trim()).toBe(
      'Genera un PDF por cada día elegido.'
    );
    expect(d.contains(document.activeElement)).toBeTrue();
  });

  it('en escritorio: 480 px a la derecha, bajo la cabecera de 64 px, con el pie fijo abajo', () => {
    ancho(false);
    abrir();
    const d = dialogo();
    const caja = d.getBoundingClientRect();
    expect(caja.width).toBe(480);
    expect(caja.top).toBe(64);
    expect(Math.round(caja.right)).toBe(document.documentElement.clientWidth);
    const pie = d.querySelector<HTMLElement>('.pn-panel__pie')!;
    expect(pie.querySelector('.descargar')).not.toBeNull();
    expect(Math.round(pie.getBoundingClientRect().bottom)).toBe(Math.round(caja.bottom));
    expect(d.classList).not.toContain('pn-panel--hoja');
  });

  it('por debajo de 768 px es una hoja inferior', () => {
    ancho(true);
    abrir();
    expect(dialogo().classList).toContain('pn-panel--hoja');
    expect(dialogo().querySelector('.pn-panel__asa')).not.toBeNull();
  });

  it('Esc, la X y el clic en el velo cierran; el foco vuelve al disparador', () => {
    ancho(false);
    let disparador = abrir();
    dialogo().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.cerrados).toBe(1);
    expect(el().querySelector('dialog.pn-panel')).toBeNull();
    expect(document.activeElement).toBe(disparador);

    disparador.click();
    fixture.detectChanges();
    dialogo().querySelector<HTMLButtonElement>('button[aria-label="Cerrar"]')!.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.cerrados).toBe(2);

    disparador.focus();
    disparador.click();
    fixture.detectChanges();
    dialogo().dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 10, clientY: 300, detail: 1 }));
    fixture.detectChanges();
    expect(fixture.componentInstance.cerrados).toBe(3);
    expect(document.activeElement).toBe(disparador);
    disparador = el().querySelector<HTMLButtonElement>('.disparador')!;
    expect(disparador).toBeTruthy();
  });

  it('un clic dentro no cierra; el foco queda contenido con Tab', () => {
    ancho(false);
    abrir();
    const d = dialogo();
    d.querySelector<HTMLElement>('select')!.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 900, clientY: 200, detail: 1 }));
    fixture.detectChanges();
    expect(fixture.componentInstance.cerrados).toBe(0);
    const descargar = d.querySelector<HTMLButtonElement>('.descargar')!;
    descargar.focus();
    descargar.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(d.querySelector('button[aria-label="Cerrar"]'));
    (document.activeElement as HTMLElement).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true })
    );
    expect(document.activeElement).toBe(descargar);
  });
});
