import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MaterialMenuComponent, OpcionMenu } from './material-menu.component';

const OPCIONES: OpcionMenu[] = [
  { id: 'ver', texto: 'Ver vídeo' },
  { id: 'subir', texto: 'Subir', deshabilitada: true },
  { id: 'eliminar', texto: 'Eliminar', peligro: true, dialogo: true },
];

describe('MaterialMenuComponent («⋯» del material)', () => {
  let fixture: ComponentFixture<MaterialMenuComponent>;
  let elegidas: string[];

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const boton = (): HTMLButtonElement => el().querySelector<HTMLButtonElement>('.menu__boton')!;
  const opciones = (): HTMLButtonElement[] => Array.from(el().querySelectorAll<HTMLButtonElement>('[role="menuitem"]'));
  const tecla = (destino: Element, key: string): void => {
    destino.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    fixture.detectChanges();
  };

  beforeEach(() => {
    fixture = TestBed.createComponent(MaterialMenuComponent);
    fixture.componentRef.setInput('etiqueta', 'Más opciones de «Taeguk»');
    fixture.componentRef.setInput('opciones', OPCIONES);
    elegidas = [];
    fixture.componentInstance.elegir.subscribe((id) => elegidas.push(id));
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => (fixture.nativeElement as HTMLElement).remove());

  it('el botón dice de qué fila es y anuncia el menú', () => {
    expect(boton().getAttribute('aria-label')).toBe('Más opciones de «Taeguk»');
    expect(boton().getAttribute('aria-haspopup')).toBe('menu');
    expect(boton().getAttribute('aria-expanded')).toBe('false');
  });

  it('al abrir, el foco va a la primera opción activa; las flechas saltan las deshabilitadas', () => {
    boton().click();
    fixture.detectChanges();
    expect(opciones().map((o) => o.textContent)).toEqual(['Ver vídeo', 'Subir', 'Eliminar']);
    expect(document.activeElement).toBe(opciones()[0]);
    tecla(opciones()[0], 'ArrowDown');
    expect(document.activeElement).toBe(opciones()[2]);
    tecla(opciones()[2], 'ArrowDown');
    expect(document.activeElement).toBe(opciones()[0]);
  });

  it('flecha arriba en el botón abre por la última', () => {
    tecla(boton(), 'ArrowUp');
    expect(document.activeElement).toBe(opciones()[2]);
  });

  it('Esc cierra y devuelve el foco al botón', () => {
    boton().click();
    fixture.detectChanges();
    tecla(opciones()[0], 'Escape');
    expect(opciones().length).toBe(0);
    expect(document.activeElement).toBe(boton());
  });

  it('elegir emite, cierra y deja el foco en el botón para que un diálogo lo devuelva ahí', () => {
    boton().click();
    fixture.detectChanges();
    opciones()[2].click();
    fixture.detectChanges();
    expect(elegidas).toEqual(['eliminar']);
    expect(opciones().length).toBe(0);
    expect(document.activeElement).toBe(boton());
  });

  it('pulsar fuera cierra', () => {
    boton().click();
    fixture.detectChanges();
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    fixture.detectChanges();
    expect(opciones().length).toBe(0);
  });

  it('la opción peligrosa va marcada, y la que abre diálogo lo anuncia', () => {
    boton().click();
    fixture.detectChanges();
    expect(opciones()[2].classList).toContain('menu__opcion--peligro');
    expect(opciones()[2].getAttribute('aria-haspopup')).toBe('dialog');
  });
});
