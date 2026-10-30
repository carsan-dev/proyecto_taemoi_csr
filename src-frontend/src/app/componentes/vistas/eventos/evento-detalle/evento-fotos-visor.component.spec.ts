import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';

import { ScrollLockService } from '../../../../servicios/generales/scroll-lock.service';
import { EventoFotosVisorComponent, FotoVisor } from './evento-fotos-visor.component';

const fotos = (total: number): FotoVisor[] =>
  Array.from({ length: total }, (_, i) => ({
    url: `http://api.test/api/eventos/7/fotos/${i + 1}`,
    alt: `Foto ${i + 1} de ${total} del evento Prueba`,
  }));

describe('EventoFotosVisorComponent', () => {
  let fixture: ComponentFixture<EventoFotosVisorComponent>;
  let cerrar: jasmine.Spy;

  function montar(total = 5, inicial = 0): void {
    TestBed.configureTestingModule({ imports: [EventoFotosVisorComponent] });
    fixture = TestBed.createComponent(EventoFotosVisorComponent);
    cerrar = jasmine.createSpy('cerrar');
    fixture.componentInstance.cerrar.subscribe(cerrar);
    fixture.componentRef.setInput('fotos', fotos(total));
    fixture.componentRef.setInput('titulo', 'Prueba');
    fixture.componentRef.setInput('indiceInicial', inicial);
    fixture.detectChanges();
  }

  const raiz = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const contador = (): string => raiz().querySelector('.visor-contador')!.textContent!.trim();
  const tecla = (key: string, init: KeyboardEventInit = {}): void => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init }));
    fixture.detectChanges();
  };
  const imagen = (): HTMLImageElement => raiz().querySelector<HTMLImageElement>('.visor-img')!;
  const toque = (tipo: 'touchstart' | 'touchend', x: number, y = 0): void => {
    const evento = new Event(tipo, { bubbles: true }) as TouchEvent;
    Object.defineProperty(evento, 'changedTouches', { value: [{ clientX: x, clientY: y }] });
    raiz().querySelector('.visor')!.dispatchEvent(evento);
    fixture.detectChanges();
  };

  it('es un diálogo modal con etiqueta y contador «3 de 24»', () => {
    montar(24, 2);

    const dialogo = raiz().querySelector('[role="dialog"]')!;
    expect(dialogo.getAttribute('aria-modal')).toBe('true');
    expect(dialogo.getAttribute('aria-label')).toBe('Fotos del evento Prueba');
    expect(contador()).toBe('3 de 24');
  });

  it('pide la foto a 1800 px, con su alt, y precarga la siguiente y la anterior', () => {
    montar(5, 2);

    expect(imagen().getAttribute('src')).toBe('http://api.test/api/eventos/7/fotos/3?w=1800');
    expect(imagen().alt).toBe('Foto 3 de 5 del evento Prueba');
    const precargas = Array.from(raiz().querySelectorAll('.visor-precarga img')).map((i) =>
      i.getAttribute('src'),
    );
    expect(precargas).toEqual([
      'http://api.test/api/eventos/7/fotos/4?w=1800',
      'http://api.test/api/eventos/7/fotos/2?w=1800',
    ]);
  });

  it('las flechas del teclado avanzan y retroceden', () => {
    montar(5, 1);

    tecla('ArrowRight');
    expect(contador()).toBe('3 de 5');
    tecla('ArrowLeft');
    tecla('ArrowLeft');
    expect(contador()).toBe('1 de 5');
  });

  it('no hace bucle: en la primera y en la última se oculta la flecha y el teclado no pasa', () => {
    montar(3, 0);

    expect(raiz().querySelector('.visor-flecha--anterior')).toBeNull();
    expect(raiz().querySelector('.visor-flecha--siguiente')).not.toBeNull();
    tecla('ArrowLeft');
    expect(contador()).toBe('1 de 3');

    tecla('ArrowRight');
    tecla('ArrowRight');
    expect(contador()).toBe('3 de 3');
    expect(raiz().querySelector('.visor-flecha--siguiente')).toBeNull();
    expect(raiz().querySelector('.visor-flecha--anterior')).not.toBeNull();
    tecla('ArrowRight');
    expect(contador()).toBe('3 de 3');
  });

  it('los botones anterior y siguiente cambian de foto', () => {
    montar(3, 1);

    raiz().querySelector<HTMLButtonElement>('.visor-flecha--siguiente')!.click();
    fixture.detectChanges();
    expect(contador()).toBe('3 de 3');
    raiz().querySelector<HTMLButtonElement>('.visor-flecha--anterior')!.click();
    fixture.detectChanges();
    expect(contador()).toBe('2 de 3');
  });

  it('Esc y el botón de cerrar emiten el cierre', () => {
    montar();

    tecla('Escape');
    expect(cerrar).toHaveBeenCalledTimes(1);
    raiz().querySelector<HTMLButtonElement>('.visor-cerrar')!.click();
    expect(cerrar).toHaveBeenCalledTimes(2);
  });

  it('pulsar el fondo cierra y pulsar la foto no', () => {
    montar();
    const escenario = raiz().querySelector<HTMLElement>('.visor-escenario')!;

    imagen().click();
    expect(cerrar).not.toHaveBeenCalled();
    escenario.click();
    expect(cerrar).toHaveBeenCalledTimes(1);
  });

  it('deslizar a la izquierda avanza, a la derecha retrocede y un gesto corto o vertical no hace nada', () => {
    montar(5, 2);

    toque('touchstart', 200);
    toque('touchend', 100);
    expect(contador()).toBe('4 de 5');

    toque('touchstart', 100);
    toque('touchend', 220);
    expect(contador()).toBe('3 de 5');

    toque('touchstart', 100);
    toque('touchend', 120);
    expect(contador()).toBe('3 de 5');

    toque('touchstart', 100, 0);
    toque('touchend', 160, 200);
    expect(contador()).toBe('3 de 5');
  });

  it('al abrir, el foco va al botón de cerrar y Tab no se sale del diálogo', () => {
    montar(3, 1);
    const cerrarBtn = raiz().querySelector<HTMLButtonElement>('.visor-cerrar')!;
    expect(document.activeElement).toBe(cerrarBtn);

    const botones = Array.from(raiz().querySelectorAll<HTMLButtonElement>('button'));
    const ultimo = botones[botones.length - 1];
    ultimo.focus();
    tecla('Tab');
    expect(document.activeElement).toBe(botones[0]);

    tecla('Tab', { shiftKey: true });
    expect(document.activeElement).toBe(ultimo);
  });

  it('si el foco queda fuera del diálogo, Tab lo devuelve dentro', () => {
    montar(3, 1);
    (document.activeElement as HTMLElement).blur();

    tecla('Tab');

    expect(raiz().querySelector('.visor')!.contains(document.activeElement)).toBeTrue();
  });

  it('si la flecha pulsada desaparece en el extremo, el foco no se pierde', fakeAsync(() => {
    montar(2, 0);
    const siguiente = raiz().querySelector<HTMLButtonElement>('.visor-flecha--siguiente')!;
    siguiente.focus();

    siguiente.click();
    fixture.detectChanges();
    tick();

    expect(raiz().querySelector('.visor')!.contains(document.activeElement)).toBeTrue();
  }));

  it('bloquea el scroll del fondo y lo libera al destruirse', () => {
    montar();
    const bloqueo = TestBed.inject(ScrollLockService);
    expect(bloqueo.activeLocks).toBe(1);
    const antes = bloqueo.activeLocks - 1;
    expect(bloqueo.activeLocks).toBe(antes + 1);

    fixture.destroy();

    expect(bloqueo.activeLocks).toBe(antes);
  });

  it('al destruirse devuelve el foco al elemento indicado', () => {
    const disparador = document.createElement('button');
    document.body.appendChild(disparador);
    TestBed.configureTestingModule({ imports: [EventoFotosVisorComponent] });
    fixture = TestBed.createComponent(EventoFotosVisorComponent);
    fixture.componentRef.setInput('fotos', fotos(3));
    fixture.componentRef.setInput('retornoFoco', disparador);
    fixture.detectChanges();
    expect(document.activeElement).not.toBe(disparador);

    fixture.destroy();

    expect(document.activeElement).toBe(disparador);
    disparador.remove();
  });
});
