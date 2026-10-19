import { attachSwalSelectSearch } from './swal-search.util';

describe('attachSwalSelectSearch', () => {
  let contenedor: HTMLDivElement;

  const montar = (opcionesHtml: string, id = 'sel'): HTMLSelectElement => {
    contenedor = document.createElement('div');
    contenedor.innerHTML = `<label for="${id}">Deporte</label><select id="${id}">${opcionesHtml}</select>`;
    document.body.appendChild(contenedor);
    const select = document.getElementById(id) as HTMLSelectElement;
    attachSwalSelectSearch({ selectId: id, autoFocus: false, debounceMs: 0 });
    return select;
  };
  const input = (id = 'sel') => document.getElementById(`${id}-input`) as HTMLInputElement;
  const visibles = (id = 'sel') =>
    Array.from(
      document.getElementById(`${id}-listbox`)!.querySelectorAll<HTMLElement>('.swal2-select-option')
    )
      .filter((o) => o.style.display !== 'none')
      .map((o) => (o.textContent ?? '').trim());
  const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));
  const escribir = (el: HTMLInputElement, texto: string) => {
    el.value = texto;
    el.dispatchEvent(new Event('input'));
  };

  afterEach(() => contenedor?.remove());

  it('al abrir con una opcion elegida se ven todas las opciones', () => {
    const select = montar(`
      <option value="">Selecciona un deporte...</option>
      <option value="Taekwondo">Taekwondo</option>
      <option value="Pilates">Pilates</option>`);
    select.value = 'Pilates';
    select.dispatchEvent(new Event('change'));
    expect(input().value).toBe('Pilates');

    input().dispatchEvent(new Event('focus'));

    expect(visibles()).toEqual(['Selecciona un deporte...', 'Taekwondo', 'Pilates']);
    const activa = document.querySelector('.swal2-select-option.is-active');
    expect(activa?.textContent?.trim()).toBe('Pilates');
  });

  it('escribir filtra y oculta el marcador', async () => {
    montar(`
      <option value="">Selecciona un deporte...</option>
      <option value="Taekwondo">Taekwondo</option>
      <option value="Pilates">Pilates</option>`);
    input().dispatchEvent(new Event('focus'));

    escribir(input(), 'pila');
    await wait(20);

    expect(visibles()).toEqual(['Pilates']);
  });

  it('un marcador con value vacio sin disabled deja el input vacio con su texto como placeholder', () => {
    montar(`
      <option value="">Selecciona un deporte...</option>
      <option value="Taekwondo">Taekwondo</option>`);

    expect(input().value).toBe('');
    expect(input().placeholder).toBe('Selecciona un deporte...');
  });

  it('una opcion con data-opcion-real se muestra como valor elegido', () => {
    montar(`
      <option value="" data-opcion-real>Sin grupo asignado</option>
      <option value="a">Infantil</option>`);

    expect(input().value).toBe('Sin grupo asignado');
  });

  it('escribir y cerrar sin elegir restaura la etiqueta de la opcion elegida', async () => {
    const select = montar(`
      <option value="">Selecciona un deporte...</option>
      <option value="Taekwondo">Taekwondo</option>
      <option value="Pilates">Pilates</option>`);
    select.value = 'Taekwondo';
    select.dispatchEvent(new Event('change'));
    input().dispatchEvent(new Event('focus'));

    escribir(input(), 'zzz');
    input().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(input().value).toBe('Taekwondo');
    expect(select.value).toBe('Taekwondo');

    // reabrir muestra todas otra vez
    input().dispatchEvent(new Event('focus'));
    await wait(20);
    expect(visibles().length).toBe(3);
  });
});
