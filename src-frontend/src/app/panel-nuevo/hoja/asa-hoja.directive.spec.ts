import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';

import { AsaHojaDirective } from './asa-hoja.directive';

@Component({
  standalone: true,
  imports: [AsaHojaDirective],
  template: `
    @if (visible) {
    <div #hoja class="hoja" [style.height.px]="alto" style="position: fixed; right: 0; bottom: 0; left: 0">
      <div class="asa" [pnAsaHoja]="hoja" (deslizada)="cerradas = cerradas + 1" style="width: 36px; height: 4px"></div>
    </div>
    }
  `,
})
class Anfitrion {
  visible = true;
  alto = 400;
  cerradas = 0;
}

/**
 * d546: el asa de las hojas inferiores del panel se arrastra hacia abajo para cerrarlas. La hoja sigue al dedo (solo hacia
 * abajo); al soltar, se cierra si bajó lo bastante (120 px o el 30 % de su alto) o si el gesto fue rápido, y si no vuelve.
 */
describe('AsaHojaDirective (d546, cerrar la hoja deslizando el asa)', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let anfitrion: Anfitrion;
  let asa: HTMLElement;
  let hoja: HTMLElement;
  let reloj: number;
  let menosMovimiento: boolean;

  function pulsar(tipo: string, y: number, extra: PointerEventInit = {}): PointerEvent {
    const evento = new PointerEvent(tipo, { pointerId: 7, isPrimary: true, button: 0, clientY: y, bubbles: true, cancelable: true, ...extra });
    asa.dispatchEvent(evento);
    return evento;
  }

  /** Mueve el dedo a `y` cuando el reloj marca `t` ms. */
  function mover(y: number, t: number): void {
    reloj = t;
    pulsar('pointermove', y);
  }

  function soltar(y: number, t: number): void {
    reloj = t;
    pulsar('pointerup', y);
  }

  beforeEach(() => {
    menosMovimiento = false;
    const original = window.matchMedia.bind(window);
    spyOn(window, 'matchMedia').and.callFake((consulta: string) =>
      consulta.includes('prefers-reduced-motion') ? ({ matches: menosMovimiento, media: consulta } as MediaQueryList) : original(consulta)
    );
    TestBed.configureTestingModule({ imports: [Anfitrion] });
    fixture = TestBed.createComponent(Anfitrion);
    anfitrion = fixture.componentInstance;
    fixture.detectChanges();
    asa = fixture.nativeElement.querySelector('.asa');
    hoja = fixture.nativeElement.querySelector('.hoja');
    const directiva = fixture.debugElement.query(By.directive(AsaHojaDirective)).injector.get(AsaHojaDirective);
    reloj = 0;
    spyOn(directiva as unknown as { ahora: () => number }, 'ahora').and.callFake(() => reloj);
  });

  it('la hoja sigue al dedo hacia abajo, sin transición mientras se arrastra', () => {
    const bajada = pulsar('pointerdown', 100);
    expect(bajada.defaultPrevented).toBeTrue();
    mover(150, 200);
    expect(hoja.style.transform).toBe('translateY(50px)');
    expect(hoja.style.transition).toBe('none');
  });

  it('no sube por encima de su sitio', () => {
    pulsar('pointerdown', 100);
    mover(40, 200);
    expect(hoja.style.transform).toBe('translateY(0px)');
  });

  it('se cierra si baja 120 px o más aunque sea despacio: termina de salir y entonces avisa', fakeAsync(() => {
    pulsar('pointerdown', 100);
    mover(180, 1000);
    mover(225, 2000);
    soltar(225, 3000);
    expect(hoja.style.transform).toBe('translateY(100%)');
    expect(hoja.style.transition).toContain('160ms');
    expect(anfitrion.cerradas).toBe(0);
    tick(160);
    expect(anfitrion.cerradas).toBe(1);
    expect(hoja.style.transform).toBe('');
    expect(hoja.style.transition).toBe('');
  }));

  it('en una hoja baja el umbral es el 30 % de su alto', fakeAsync(() => {
    anfitrion.alto = 200;
    fixture.detectChanges();
    pulsar('pointerdown', 100);
    mover(165, 1000);
    soltar(165, 2000);
    tick(160);
    expect(anfitrion.cerradas).toBe(1);
  }));

  it('un gesto rápido hacia abajo la cierra aunque no llegue al umbral', fakeAsync(() => {
    pulsar('pointerdown', 100);
    mover(120, 20);
    mover(140, 40);
    soltar(140, 50);
    tick(160);
    expect(anfitrion.cerradas).toBe(1);
  }));

  it('un gesto rápido de menos de 24 px no la cierra', fakeAsync(() => {
    pulsar('pointerdown', 100);
    mover(120, 10);
    soltar(120, 20);
    tick(500);
    expect(anfitrion.cerradas).toBe(0);
  }));

  it('por debajo del umbral y despacio vuelve a su sitio y limpia los estilos', fakeAsync(() => {
    pulsar('pointerdown', 100);
    mover(150, 1000);
    mover(180, 2000);
    soltar(180, 3000);
    expect(hoja.style.transform).toBe('translateY(0px)');
    expect(hoja.style.transition).toContain('180ms');
    tick(180);
    expect(anfitrion.cerradas).toBe(0);
    expect(hoja.style.transform).toBe('');
    expect(hoja.style.transition).toBe('');
  }));

  it('pointercancel (el navegador se queda el gesto) la devuelve sin cerrarla', fakeAsync(() => {
    pulsar('pointerdown', 100);
    mover(400, 1000);
    pulsar('pointercancel', 400);
    expect(hoja.style.transform).toBe('translateY(0px)');
    tick(500);
    expect(anfitrion.cerradas).toBe(0);
    expect(hoja.style.transform).toBe('');
  }));

  it('ignora el botón secundario, el puntero no primario y otro dedo', () => {
    pulsar('pointerdown', 100, { button: 2 });
    mover(300, 1000);
    expect(hoja.style.transform).toBe('');
    pulsar('pointerdown', 100, { isPrimary: false });
    mover(300, 2000);
    expect(hoja.style.transform).toBe('');
    pulsar('pointerdown', 100);
    pulsar('pointermove', 300, { pointerId: 8 });
    expect(hoja.style.transform).toBe('');
  });

  it('con prefers-reduced-motion se cierra y vuelve al instante, sin animación', fakeAsync(() => {
    menosMovimiento = true;
    pulsar('pointerdown', 100);
    mover(150, 1000);
    soltar(150, 2000);
    expect(hoja.style.transform).toBe('');
    expect(hoja.style.transition).toBe('');
    pulsar('pointerdown', 100);
    mover(300, 3000);
    soltar(300, 4000);
    expect(anfitrion.cerradas).toBe(1);
    expect(hoja.style.transform).toBe('');
  }));

  it('si la hoja se quita a mitad de gesto no queda nada colgado ni avisa', fakeAsync(() => {
    pulsar('pointerdown', 100);
    mover(300, 1000);
    soltar(300, 2000);
    anfitrion.visible = false;
    fixture.detectChanges();
    tick(1000);
    expect(anfitrion.cerradas).toBe(0);
    expect(hoja.style.transform).toBe('');
    expect(hoja.style.transition).toBe('');
  }));

  it('si se destruye mientras arrastra, limpia la hoja', () => {
    pulsar('pointerdown', 100);
    mover(200, 1000);
    anfitrion.visible = false;
    fixture.detectChanges();
    expect(hoja.style.transform).toBe('');
    expect(hoja.style.willChange).toBe('');
  });
});
