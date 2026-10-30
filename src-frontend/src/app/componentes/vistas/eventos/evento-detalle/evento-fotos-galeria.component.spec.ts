import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import {
  EventoFotosApiService,
  FotoEventoApi,
} from '../../../../panel-nuevo/eventos/evento-fotos-api.service';
import { ScrollLockService } from '../../../../servicios/generales/scroll-lock.service';
import { EventoFotosGaleriaComponent } from './evento-fotos-galeria.component';

function crearFotos(total: number): FotoEventoApi[] {
  return Array.from({ length: total }, (_, i) => ({
    id: i + 1,
    url: `http://api.test/api/eventos/7/fotos/${i + 1}`,
    orden: i,
  }));
}

describe('EventoFotosGaleriaComponent', () => {
  let fixture: ComponentFixture<EventoFotosGaleriaComponent>;
  let listar: jasmine.Spy;

  function montar(respuesta: Observable<FotoEventoApi[]>): void {
    listar = jasmine.createSpy('listar').and.returnValue(respuesta);
    TestBed.configureTestingModule({
      imports: [EventoFotosGaleriaComponent],
      providers: [{ provide: EventoFotosApiService, useValue: { listar } }],
    });
    fixture = TestBed.createComponent(EventoFotosGaleriaComponent);
    fixture.componentRef.setInput('eventoId', 7);
    fixture.componentRef.setInput('titulo', 'Campeonato de primavera');
    fixture.detectChanges();
  }

  const raiz = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const casillas = (): HTMLButtonElement[] =>
    Array.from(raiz().querySelectorAll<HTMLButtonElement>('.casilla'));
  /** Casillas de la cuadrícula que ocupa el mosaico (columnas × filas de cada una). */
  const area = (): number =>
    casillas().reduce((suma, c) => {
      const columnas = Number(/c(\d)/.exec(c.className)![1]);
      const filas = Number(/f(\d)/.exec(c.className)![1]);
      return suma + columnas * filas;
    }, 0);
  const botonVerTodas = (): HTMLButtonElement | null =>
    raiz().querySelector<HTMLButtonElement>('.fotos-ver-todas');

  it('sin fotos no pinta nada ni esqueleto', () => {
    montar(of([]));

    expect(listar).toHaveBeenCalledOnceWith(7);
    expect(raiz().querySelector('.fotos-evento')).toBeNull();
    expect(raiz().textContent?.trim()).toBe('');
  });

  it('si la carga falla (404 o red) la sección no aparece', () => {
    montar(throwError(() => ({ status: 404 })));

    expect(raiz().querySelector('.fotos-evento')).toBeNull();
    expect(raiz().textContent?.trim()).toBe('');
  });

  it('con 1 foto dice «1 foto», pinta una casilla y no ofrece «Ver las N fotos»', () => {
    montar(of(crearFotos(1)));

    expect(raiz().querySelector('.fotos-total')?.textContent?.trim()).toBe('1 foto');
    expect(casillas().length).toBe(1);
    expect(botonVerTodas()).toBeNull();
  });

  it('con varias fotos usa el plural y muestra la cabecera con su icono', () => {
    montar(of(crearFotos(3)));

    expect(raiz().querySelector('h2')?.textContent?.trim()).toBe('Fotos del evento');
    expect(raiz().querySelector('.fotos-cabecera .bi-images')).not.toBeNull();
    expect(raiz().querySelector('.fotos-total')?.textContent?.trim()).toBe('3 fotos');
    expect(casillas().length).toBe(3);
    expect(raiz().querySelector('.casilla-mas')).toBeNull();
  });

  it('con más de 9 pinta 9 casillas y «+N» (total − 8) en la novena, con botón «Ver las N fotos»', () => {
    montar(of(crearFotos(24)));

    expect(raiz().querySelector('.fotos-total')?.textContent?.trim()).toBe('24 fotos');
    expect(casillas().length).toBe(9);
    const capas = raiz().querySelectorAll('.casilla-mas');
    expect(capas.length).toBe(1);
    expect(capas[0].textContent?.trim()).toBe('+16');
    expect(casillas()[8].contains(capas[0])).toBeTrue();
    expect(casillas()[8].querySelector('img')).not.toBeNull();
    expect(botonVerTodas()?.textContent?.trim()).toBe('Ver las 24 fotos');
  });

  it('con 10 fotos la novena lleva «+2»', () => {
    montar(of(crearFotos(10)));

    expect(casillas().length).toBe(9);
    expect(raiz().querySelector('.casilla-mas')?.textContent?.trim()).toBe('+2');
  });

  it('con exactamente 9 fotos se ven las 9 sin capa «+N»', () => {
    montar(of(crearFotos(9)));

    expect(casillas().length).toBe(9);
    expect(raiz().querySelector('.casilla-mas')).toBeNull();
  });

  it('con 8 fotos se mantiene el reparto sin huecos y sin capa', () => {
    montar(of(crearFotos(8)));

    expect(casillas().length).toBe(8);
    expect(raiz().querySelector('.casilla-mas')).toBeNull();
    expect(area()).toBe(12);
  });

  it('con 9 o más, la grande y 8 pequeñas rellenan 3 filas de 4 columnas sin huecos', () => {
    for (const total of [9, 10, 24]) {
      TestBed.resetTestingModule();
      montar(of(crearFotos(total)));
      expect(area()).toBe(12);
      expect(casillas().slice(1).every((c) => c.classList.contains('c1') && c.classList.contains('f1'))).toBeTrue();
    }
  });

  it('el mosaico vive en una caja contenedora y sus filas usan clamp(120px, 15cqw, 190px)', () => {
    montar(of(crearFotos(9)));

    expect(raiz().querySelector('.mosaico-caja > .mosaico')).not.toBeNull();
    const caja = raiz().querySelector<HTMLElement>('.mosaico-caja')!;
    expect(getComputedStyle(caja).containerType).toBe('inline-size');
    const filas = parseFloat(getComputedStyle(raiz().querySelector<HTMLElement>('.mosaico')!).gridAutoRows);
    expect(filas).toBeGreaterThanOrEqual(120);
    expect(filas).toBeLessThanOrEqual(190);
  });

  it('las miniaturas piden 640 px, cargan en diferido y llevan alt con número y título', () => {
    montar(of(crearFotos(24)));

    const imagen = casillas()[2].querySelector('img') as HTMLImageElement;
    expect(imagen.getAttribute('src')).toBe('http://api.test/api/eventos/7/fotos/3?w=640');
    expect(imagen.getAttribute('loading')).toBe('lazy');
    expect(imagen.getAttribute('decoding')).toBe('async');
    expect(imagen.alt).toBe('Foto 3 de 24 del evento Campeonato de primavera');
  });

  it('la primera casilla ocupa 2×2 y la de móvil queda marcada como primera', () => {
    montar(of(crearFotos(24)));

    expect(casillas()[0].classList).toContain('c2');
    expect(casillas()[0].classList).toContain('f2');
    expect(casillas()[0].classList).toContain('casilla--primera');
  });

  it('«Ver las N fotos» abre el visor en la primera', () => {
    montar(of(crearFotos(24)));

    botonVerTodas()!.click();
    fixture.detectChanges();

    const dialogo = document.querySelector('[role="dialog"]');
    expect(dialogo).not.toBeNull();
    expect(dialogo?.querySelector('.visor-contador')?.textContent?.trim()).toBe('1 de 24');
  });

  it('pulsar una foto abre el visor en esa foto', () => {
    montar(of(crearFotos(24)));

    casillas()[2].click();
    fixture.detectChanges();

    expect(document.querySelector('.visor-contador')?.textContent?.trim()).toBe('3 de 24');
  });

  it('al cerrar con Esc el foco vuelve a la casilla pulsada', () => {
    montar(of(crearFotos(24)));
    const casilla = casillas()[2];
    casilla.focus();
    casilla.click();
    fixture.detectChanges();
    expect(document.activeElement).not.toBe(casilla);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(casilla);
  });

  it('el scroll del fondo se bloquea con el visor abierto y se restaura al cerrar', () => {
    montar(of(crearFotos(24)));
    const bloqueo = TestBed.inject(ScrollLockService);
    const antes = bloqueo.activeLocks;

    casillas()[0].click();
    fixture.detectChanges();
    expect(bloqueo.activeLocks).toBe(antes + 1);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(bloqueo.activeLocks).toBe(antes);
  });

  it('si se destruye con el visor abierto, restaura el scroll del fondo', () => {
    montar(of(crearFotos(24)));
    const bloqueo = TestBed.inject(ScrollLockService);
    const antes = bloqueo.activeLocks;

    casillas()[0].click();
    fixture.detectChanges();
    expect(bloqueo.activeLocks).toBe(antes + 1);

    fixture.destroy();

    expect(bloqueo.activeLocks).toBe(antes);
    expect(document.body.style.overflow).not.toBe('hidden');
  });
});
