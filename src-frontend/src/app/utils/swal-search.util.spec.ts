import Swal from 'sweetalert2';
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

// Maquetación real: se abre un SweetAlert de verdad (inyecta su hoja, incluida la regla
// .swal2-input) y las reglas .swal2-select-* salen de src/styles.scss, que Karma carga.
describe('attachSwalSelectSearch (maquetación)', () => {
  const opciones = Array.from({ length: 12 }, (_, i) => `<option value="${i}">Deporte ${i}</option>`).join('');
  const CAJA = 'width: 100%; padding: 10px; margin: 0;';

  const abrir = async (selectStyle: string | null, conFecha = false) => {
    const style = selectStyle === null ? '' : ` style="${selectStyle}"`;
    const fecha = conFecha
      ? `<div><label for="f">Fecha</label><input type="date" id="f" class="swal2-input" style="${CAJA}"></div>`
      : '';
    let original!: DOMRect;
    await new Promise<void>((resuelto) => {
      void Swal.fire({
        html: `<div id="caja"><label for="s" style="display: block">Deporte</label><select id="s" class="swal2-input"${style}>${opciones}</select></div>${fecha}<input id="sonda" class="swal2-input" placeholder="sonda">`,
        showClass: { popup: '' },
        hideClass: { popup: '' },
        didOpen: () => {
          // Caja del select original, medida antes de adjuntar el buscador.
          original = document.getElementById('s')!.getBoundingClientRect();
          attachSwalSelectSearch({ selectId: 's', autoFocus: false, debounceMs: 0 });
          resuelto();
        },
      });
    });
    const campo = document.getElementById('s-input') as HTMLInputElement;
    const caja = document.getElementById('caja') as HTMLElement;
    return { campo, caja, original, rect: (el: Element) => el.getBoundingClientRect() };
  };

  // El iframe de Karma es bajito; se le da una ventana de escritorio (1280x800) para medir.
  const marco = window.frameElement as HTMLElement | null;
  let estiloMarco = '';
  beforeEach(() => {
    estiloMarco = marco?.getAttribute('style') ?? '';
    marco?.setAttribute('style', `${estiloMarco};height:800px !important;width:1280px !important;`);
  });
  afterEach(() => {
    Swal.close();
    marco?.setAttribute('style', estiloMarco);
  });

  const cercano = (x: number, y: number) => expect(Math.abs(x - y)).toBeLessThanOrEqual(1);

  it('con el marcado de Convocatorias el campo tiene la misma caja que el campo de fecha', async () => {
    const { campo, rect } = await abrir(CAJA, true);
    const fecha = rect(document.getElementById('f')!);
    cercano(rect(campo).left, fecha.left);
    cercano(rect(campo).width, fecha.width);
    cercano(rect(campo).height, fecha.height);
  });

  it('con width 80% el campo mide el 80% del contenedor y queda centrado', async () => {
    const { campo, caja, rect } = await abrir('width: 80%;');
    cercano(rect(campo).width, rect(caja).width * 0.8);
    cercano(rect(campo).left - rect(caja).left, rect(caja).right - rect(campo).right);
  });

  it('sin estilo en línea el campo tiene la misma caja que un input swal2-input', async () => {
    const { campo, rect } = await abrir(null);
    const sonda = document.getElementById('sonda')!;
    const caja = document.getElementById('caja')!;
    // Mismos márgenes y alto que el input de referencia; el ancho es el del contenedor menos
    // esos márgenes laterales (el input de referencia, sin ancho, se queda en su ancho intrínseco).
    const margenLateral = parseFloat(getComputedStyle(sonda).marginLeft);
    cercano(rect(campo).left - rect(caja).left, margenLateral);
    cercano(rect(caja).right - rect(campo).right, margenLateral);
    cercano(rect(campo).height, rect(sonda).height);
    expect(getComputedStyle(campo.parentElement!).marginTop).toBe(getComputedStyle(sonda).marginTop);
    expect(getComputedStyle(campo.parentElement!).marginBottom).toBe(getComputedStyle(sonda).marginBottom);
  });

  it('la tipografía del campo es la de un input swal2-input', async () => {
    const { campo } = await abrir(null);
    const sonda = document.getElementById('sonda')!;
    expect(getComputedStyle(campo).fontSize).toBe(getComputedStyle(sonda).fontSize);
    expect(getComputedStyle(campo).fontFamily).toBe(getComputedStyle(sonda).fontFamily);
  });

  it('el desplegable abierto tiene el mismo left y width que el campo', async () => {
    const { campo, rect } = await abrir(CAJA);
    campo.dispatchEvent(new Event('focus'));
    const lista = document.getElementById('s-listbox')!;
    expect(lista.classList.contains('is-open')).toBeTrue();
    expect(Math.abs(rect(lista).left - rect(campo).left)).toBeLessThanOrEqual(1);
    expect(Math.abs(rect(lista).width - rect(campo).width)).toBeLessThanOrEqual(1);
  });

  it('el texto del campo y de las opciones queda alineado a la izquierda', async () => {
    const { campo } = await abrir(null);
    campo.dispatchEvent(new Event('focus'));
    const opcion = document.querySelector('.swal2-select-option') as HTMLElement;
    expect(getComputedStyle(campo).textAlign).toBe('left');
    expect(getComputedStyle(opcion).textAlign).toBe('left');
  });

  it('no copia un display en línea del select al campo', async () => {
    const { campo } = await abrir('display: inline-block; width: 100%;');
    expect(campo.style.display).toBe('');
  });

  const sinRecorte = (campo: HTMLElement) => {
    const contenedorSwal = Swal.getContainer()!;
    const scrollAntes = contenedorSwal.scrollHeight;
    campo.dispatchEvent(new Event('focus'));
    const lista = document.getElementById('s-listbox')!.getBoundingClientRect();
    expect(lista.height).toBeGreaterThan(150);
    expect(lista.height).toBeLessThanOrEqual(222);
    expect(lista.bottom).toBeLessThanOrEqual(window.innerHeight);
    // Ningun ancestro recorta: el centro de la lista responde a la propia lista.
    const centro = document.elementFromPoint(lista.left + lista.width / 2, lista.top + lista.height / 2);
    expect(document.getElementById('s-listbox')!.contains(centro)).toBeTrue();
    // El contenedor del html no recorta ni genera barras; el dialogo no gana scroll al abrir.
    expect(getComputedStyle(Swal.getHtmlContainer()!).overflow).toBe('visible');
    expect(contenedorSwal.scrollHeight).toBe(scrollAntes);
  };

  it('el desplegable abierto se ve entero y sin barras (sin estilo en línea)', async () => {
    const { campo } = await abrir(null);
    sinRecorte(campo);
  });

  it('el desplegable abierto se ve entero y sin barras (Convocatorias)', async () => {
    const { campo } = await abrir(CAJA, true);
    sinRecorte(campo);
  });

  it('al cerrar el desplegable el diálogo vuelve a su estado normal', async () => {
    const { campo } = await abrir(null);
    campo.dispatchEvent(new Event('focus'));
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    const lista = document.getElementById('s-listbox')!;
    expect(lista.classList.contains('is-open')).toBeFalse();
    expect(getComputedStyle(lista).display).toBe('none');
    const contenedor = Swal.getHtmlContainer()!;
    expect(contenedor.scrollHeight).toBeLessThanOrEqual(contenedor.clientHeight + 1);
  });

  it('si debajo no cabe, el desplegable no se sale de la ventana', async () => {
    marco?.setAttribute('style', `${estiloMarco};height:480px !important;width:1280px !important;`);
    const { campo } = await abrir(null);
    campo.dispatchEvent(new Event('focus'));
    const lista = document.getElementById('s-listbox')!.getBoundingClientRect();
    expect(lista.top).toBeGreaterThanOrEqual(0);
    expect(lista.bottom).toBeLessThanOrEqual(window.innerHeight);
  });
});
