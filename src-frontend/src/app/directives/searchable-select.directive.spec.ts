import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { SearchableSelectDirective } from './searchable-select.directive';

@Component({
  standalone: true,
  imports: [FormsModule, SearchableSelectDirective],
  template: `
    <select id="real" appSearchableSelect [(ngModel)]="real">
      <option [ngValue]="null">Seleccione un producto</option>
      <option value="TODOS">Todos los Alumnos</option>
      <option value="a">Kimono azul</option>
      <option value="b">Cinturón negro</option>
    </select>
    <select id="vacio" appSearchableSelect [(ngModel)]="vacio">
      <option value="">Elige un grupo</option>
      <option value="a">Infantil</option>
    </select>
    <select id="real-vacio" appSearchableSelect [(ngModel)]="realVacio">
      <option value="" data-opcion-real>Sin grupo asignado</option>
      <option value="a">Infantil</option>
    </select>
  `,
})
class HostComponent {
  real: string | null = 'a';
  vacio = '';
  realVacio = '';
}

describe('SearchableSelectDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  const inputs = () =>
    Array.from(document.querySelectorAll<HTMLInputElement>('input.admin-select-input'));
  const dropdowns = () =>
    Array.from(document.querySelectorAll<HTMLElement>('.admin-select-dropdown'));
  const visible = (i: number) =>
    Array.from(dropdowns()[i].querySelectorAll<HTMLElement>('.admin-select-option'))
      .filter((o) => o.style.display !== 'none')
      .map((o) => (o.textContent ?? '').trim());
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const type = (el: HTMLInputElement, text: string) => {
    el.value = text;
    el.dispatchEvent(new Event('input'));
  };
  const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    await settle();
  });

  afterEach(() => fixture.destroy());

  it('muestra todas las opciones al abrir con una opción real seleccionada', () => {
    const input = inputs()[0];
    expect(input.value).toBe('Kimono azul');
    input.dispatchEvent(new Event('focus'));
    expect(visible(0).length).toBe(4);
    input.dispatchEvent(new Event('click'));
    expect(visible(0).length).toBe(4);
    expect(dropdowns()[0].querySelector('.is-active')?.textContent?.trim()).toBe('Kimono azul');
  });

  it('al escribir filtra por el término y oculta el marcador', async () => {
    const input = inputs()[0];
    input.dispatchEvent(new Event('focus'));
    type(input, 'kimono');
    await wait(300);
    expect(visible(0)).toEqual(['Kimono azul']);
  });

  it('con [ngValue]=null seleccionado deja el input vacío y usa su texto de placeholder', async () => {
    host.real = null;
    await settle();
    const input = inputs()[0];
    expect(input.value).toBe('');
    expect(input.placeholder).toBe('Seleccione un producto');
    input.dispatchEvent(new Event('focus'));
    expect(visible(0).length).toBe(4);
  });

  it('con value="" sin disabled deja el input vacío y usa su texto de placeholder', () => {
    const input = inputs()[1];
    expect(input.value).toBe('');
    expect(input.placeholder).toBe('Elige un grupo');
  });

  it('muestra como valor una opción con value real como TODOS', async () => {
    host.real = 'TODOS';
    await settle();
    expect(inputs()[0].value).toBe('Todos los Alumnos');
  });

  it('una opción value="" con data-opcion-real se muestra como valor y se encuentra al filtrar', async () => {
    const input = inputs()[2];
    expect(input.value).toBe('Sin grupo asignado');
    input.dispatchEvent(new Event('focus'));
    type(input, 'sin');
    await wait(300);
    expect(visible(2)).toEqual(['Sin grupo asignado']);
  });

  it('click en el input tras escribir no pierde el filtro', async () => {
    const input = inputs()[0];
    input.dispatchEvent(new Event('focus'));
    type(input, 'kimono');
    await wait(300);
    input.dispatchEvent(new Event('click'));
    expect(visible(0)).toEqual(['Kimono azul']);
  });

  it('con un filtrado pendiente del debounce, abrir muestra todas las opciones', async () => {
    const input = inputs()[0];
    input.dispatchEvent(new Event('focus'));
    type(input, 'kimono');
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    input.dispatchEvent(new Event('focus'));
    await wait(300);
    expect(visible(0).length).toBe(4);
  });

  it('al escribir y cerrar sin elegir restaura la etiqueta de la selección', async () => {
    const input = inputs()[0];
    input.dispatchEvent(new Event('focus'));
    type(input, 'xyz');
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(input.value).toBe('Kimono azul');
    await wait(300);
    input.dispatchEvent(new Event('focus'));
    expect(visible(0).length).toBe(4);
  });
});
