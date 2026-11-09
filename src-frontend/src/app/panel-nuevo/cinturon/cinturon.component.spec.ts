import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { CinturonComponent, necesitaHalo } from './cinturon.component';

@Component({
  standalone: true,
  imports: [CinturonComponent],
  template: `<pn-cinturon [color1]="c1" [color2]="c2" [medias]="medias" [tamano]="tamano" [sinGrado]="sin" />`,
})
class AnfitrionComponent {
  c1: string | null = '#FFD700';
  c2: string | null = null;
  medias: number | null = null;
  tamano: 'fila' | 'hoja' = 'fila';
  sin = false;
}

function pintar(cambios: Partial<AnfitrionComponent> = {}): SVGSVGElement {
  const fixture = TestBed.createComponent(AnfitrionComponent);
  Object.assign(fixture.componentInstance, cambios);
  fixture.detectChanges();
  return fixture.nativeElement.querySelector('svg') as SVGSVGElement;
}

const rallas = (svg: SVGSVGElement) => Array.from(svg.querySelectorAll('[data-ralla]'));
const halos = (svg: SVGSVGElement) => Array.from(svg.querySelectorAll('[data-halo]'));
const franjas = (svg: SVGSVGElement) => Array.from(svg.querySelectorAll('[data-franja]'));

describe('CinturonComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [AnfitrionComponent] }));

  it('fila 56x14 y hoja 150x30, decorativo', () => {
    const fila = pintar();
    expect(fila.getAttribute('width')).toBe('56');
    expect(fila.getAttribute('height')).toBe('14');
    expect(fila.getAttribute('viewBox')).toBe('0 0 56 14');
    expect(fila.getAttribute('aria-hidden')).toBe('true');
    const hoja = pintar({ tamano: 'hoja' });
    expect(hoja.getAttribute('width')).toBe('150');
    expect(hoja.getAttribute('height')).toBe('30');
  });

  it('un color: una franja; dos colores: color2 arriba y color1 abajo', () => {
    expect(franjas(pintar())).toHaveSize(1);
    const [arriba, abajo] = franjas(pintar({ c1: '#FFFFFF', c2: '#008000' }));
    expect(arriba.getAttribute('fill')).toBe('#008000');
    expect(abajo.getAttribute('fill')).toBe('#FFFFFF');
    expect(Number(arriba.getAttribute('y'))).toBeLessThan(Number(abajo.getAttribute('y')));
    expect(arriba.getAttribute('width')).toBe(abajo.getAttribute('width'));
  });

  it('sin medias (null): sin rallas', () => {
    expect(rallas(pintar())).toHaveSize(0);
  });

  it('rallas 0, 1, 5, 9 y 10 medias', () => {
    expect(rallas(pintar({ medias: 0 }))).toHaveSize(0);
    expect(rallas(pintar({ medias: 1 }))).toHaveSize(1);
    expect(rallas(pintar({ medias: 5 }))).toHaveSize(3);
    expect(rallas(pintar({ medias: 9 }))).toHaveSize(5);
    expect(rallas(pintar({ medias: 10 }))).toHaveSize(5);
  });

  it('empiezan en el extremo derecho; la media ralla es la mitad de abajo', () => {
    const una = rallas(pintar({ medias: 1 }))[0];
    expect(una.getAttribute('x')).toBe('49'); // 56 - 1 - 3 - 3
    expect(una.getAttribute('y')).toBe('7');
    expect(una.getAttribute('height')).toBe('6');
    const cinco = rallas(pintar({ medias: 5 }));
    expect(cinco.map((r) => r.getAttribute('x'))).toEqual(['49', '43', '37']);
    expect(cinco.map((r) => r.getAttribute('height'))).toEqual(['12', '12', '6']);
    expect(cinco.every((r) => r.getAttribute('fill') === '#000')).toBeTrue();
  });

  it('hoja: ralla de 8 y paso de 14', () => {
    const r = rallas(pintar({ medias: 4, tamano: 'hoja' }));
    expect(r.map((x) => x.getAttribute('x'))).toEqual(['135', '121']);
    expect(r[0].getAttribute('width')).toBe('8');
  });

  it('halo solo en azul', () => {
    expect(necesitaHalo('#0000FF')).toBeTrue();
    expect(necesitaHalo('#FFD700')).toBeFalse();
    expect(necesitaHalo('#FFFFFF')).toBeFalse();
    expect(necesitaHalo('#008000')).toBeFalse();
    expect(halos(pintar({ c1: '#FFD700', medias: 4 }))).toHaveSize(0);
    const azul = pintar({ c1: '#0000FF', medias: 2 });
    expect(halos(azul)).toHaveSize(2); // arriba y abajo de la única ralla completa
    expect(rallas(azul)).toHaveSize(1);
    expect(halos(pintar({ c1: '#0000FF', c2: '#FFFFFF', medias: 2 }))).toHaveSize(1); // solo la mitad azul
  });

  it('sin grado: fondo blanco, borde discontinuo y sin rallas', () => {
    const svg = pintar({ sin: true, medias: 4 });
    expect(rallas(svg)).toHaveSize(0);
    expect(franjas(svg)).toHaveSize(0);
    expect(svg.querySelector('rect[stroke-dasharray]')).not.toBeNull();
    expect(pintar().querySelector('rect[stroke-dasharray]')).toBeNull();
  });
});
