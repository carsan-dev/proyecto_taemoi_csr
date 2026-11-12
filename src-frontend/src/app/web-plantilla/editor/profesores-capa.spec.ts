import { activarCapaEditor } from './capa-editor';
import type { EstadoProfesor } from './fotos-profesores';
import { BloqueBorrador, CANAL_EDITOR_WEB } from './puente-editor';

/** Lo justo de Inicio de «La línea comarcal» con dos tarjetas de profesor tal como las pinta el tema en el editor. */
const WEB = `
<div class="lc">
  <main id="principal" tabindex="-1">
    <section class="seccion" id="profesores" aria-labelledby="profesores-titulo"><div class="envoltura">
      <div class="cabeza-seccion"><p class="rotulo">03 · Profesores</p><h2 class="titulo-seccion" id="profesores-titulo">Quién da la clase</h2></div>
      <ul class="profes">
        <li class="profe" data-ed-profesor="1"><figure class="foto profe-foto"><img src="https://x/a.webp" alt="Antonio en el tatami" loading="lazy"></figure><h3 class="profe-nombre">Antonio Ruiz</h3><p class="profe-grado">Cinturón negro 5.º dan</p></li>
        <li class="profe" data-ed-profesor="4"><div class="monograma" role="img" aria-label="Sin foto: iniciales de Profesor nuevo">?</div><h3 class="profe-nombre"></h3><p class="profe-grado">Cargo o grado (opcional)</p></li>
      </ul>
    </div></section>
  </main>
</div>`;

const fila = (clave: string, texto: string | null, imagenUrl: string | null = null): BloqueBorrador => ({ clave, texto, imagenUrl, orden: 0, oculto: false });
const BORRADOR: BloqueBorrador[] = [
  fila('portada.titular', 'Taekwondo en seis pueblos'),
  fila('profesores', null),
  fila('profesor.1.nombre', 'Antonio Ruiz'),
  fila('profesor.1.cargo', 'Cinturón negro 5.º dan'),
  fila('profesor.1.foto', 'Antonio en el tatami', 'https://x/a.webp'),
  fila('profesor.4.nombre', ''),
];
const LISTA: EstadoProfesor[] = [
  { n: 1, nombre: 'Antonio Ruiz', permiso: { tipo: 'constancia', texto: 'Permiso marcado por María Ruiz el 03/11/2026' } },
  { n: 4, nombre: '', permiso: { tipo: 'casilla', marcada: false } },
];
const FALTA = 'Marca la casilla si tienes su permiso. Sin él no puede salir en la web.';

describe('profesores en el sitio (W12f-2b, capa de edición)', () => {
  let marco: HTMLIFrameElement;
  let ventana: Window;
  let doc: Document;
  let enviados: jasmine.Spy;
  let desactivar: () => void;

  beforeEach(() => {
    marco = document.createElement('iframe');
    document.body.appendChild(marco);
    ventana = marco.contentWindow!;
    doc = marco.contentDocument!;
    doc.body.innerHTML = WEB;
    enviados = spyOn(window, 'postMessage');
    desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: () => undefined });
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: BORRADOR });
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: LISTA });
  });

  afterEach(() => {
    desactivar();
    marco.remove();
  });

  function delPanel(data: unknown): void {
    ventana.dispatchEvent(new MessageEvent('message', { data, origin: ventana.origin, source: window }));
  }
  const q = <T extends HTMLElement = HTMLElement>(selector: string) => doc.querySelector<T>(selector)!;
  const tarjeta = (n: number) => q(`li.profe[data-ed-profesor="${n}"]`);
  const texto = (el: Element | null) => (el?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const mensajes = (tipo: string) =>
    enviados.calls
      .allArgs()
      .map(([m]) => m as { tipo: string } & Record<string, unknown>)
      .filter((m) => m.tipo === tipo);
  const esperar = (ms = 60) => new Promise((r) => setTimeout(r, ms));

  it('guardado: la constancia, sin casilla; nuevo: la casilla obligatoria con su ayuda (boceto `profeHtml`)', () => {
    const constancia = tarjeta(1).querySelector('.ed-constancia');
    expect(texto(constancia)).toBe('Permiso marcado por María Ruiz el 03/11/2026');
    expect(constancia!.classList).toContain('ed');
    expect(tarjeta(1).querySelector('input[type=checkbox]')).toBeNull();

    const caja = tarjeta(4).querySelector<HTMLElement>('.ed-permiso')!;
    const input = caja.querySelector<HTMLInputElement>('input[type=checkbox]')!;
    expect(texto(caja.querySelector('label'))).toBe('Tengo su permiso para publicar su nombre y su foto (obligatorio)');
    expect(texto(caja.querySelector('.ed-marca'))).toBe('(obligatorio)');
    expect(input.checked).toBeFalse();
    expect(input.getAttribute('aria-required')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe('pm-a-4');
    expect(input.hasAttribute('aria-invalid')).toBeFalse();
    expect(texto(q('#pm-a-4'))).toBe('Se guarda quién lo marca y cuándo. No sale en la web.');
    expect(tarjeta(4).querySelector('.ed-constancia')).toBeNull();
  });

  it('monograma: la capa de la foto va encima (no dentro de la imagen), con «Añadir foto»; el nombre vacío se edita', () => {
    const monograma = tarjeta(4).querySelector<HTMLElement>('.monograma')!;
    expect(monograma.getAttribute('role')).toBe('img');
    expect(monograma.getAttribute('aria-label')).toBe('Sin foto: iniciales de Profesor nuevo');
    expect(monograma.querySelector('.ed-foto')).toBeNull();
    const capa = monograma.nextElementSibling as HTMLElement;
    expect(capa.classList).toContain('ed-mono__capa');
    const b = capa.querySelector<HTMLButtonElement>('.ed-foto[data-foto="profesor.4.foto"]')!;
    expect(b.getAttribute('aria-label')).toBe('Añadir foto de el profesor nuevo');
    expect(b.classList).not.toContain('es-vacia');
    expect(texto(b)).toBe('Añadir foto');

    const nombre = tarjeta(4).querySelector<HTMLElement>('.profe-nombre')!;
    expect(nombre.dataset['campo']).toBe('profesor.4.nombre');
    expect(nombre.classList).toContain('es-vacio');
    expect(nombre.dataset['vacio']).toBe('Nombre');
    expect(nombre.getAttribute('aria-label')).toBe('Editar nombre (obligatorio)');
    expect(tarjeta(4).querySelector<HTMLElement>('.profe-grado')!.dataset['campo']).toBe('profesor.4.cargo');
    // La foto del guardado lleva la capa de siempre.
    expect(tarjeta(1).querySelector('figure.profe-foto > .ed-foto')!.getAttribute('aria-label')).toBe('Cambiar foto de Antonio Ruiz');
  });

  it('menú de la foto de un profesor: «Quitar foto» dice «Sale con sus iniciales»; sin Mover', () => {
    tarjeta(1).querySelector<HTMLButtonElement>('.ed-foto')!.click();
    expect(texto(q('.flot__titulo'))).toBe('Foto de Antonio Ruiz');
    const opciones = Array.from(doc.querySelectorAll<HTMLElement>('.flot [role=menuitem]')).map(texto);
    expect(opciones).toEqual(['Cambiar fotoJPG, PNG o WebP, hasta 50 MB', 'Describir la foto«Antonio en el tatami»', 'Quitar fotoSale con sus iniciales']);
    expect(doc.querySelector('.flot [role=separator]')).not.toBeNull();
  });

  it('la casilla se marca de verdad y se lo dice al panel; el error se asocia por `aria-describedby`', () => {
    const input = tarjeta(4).querySelector<HTMLInputElement>('input[data-permiso]')!;
    input.click();
    expect(input.checked).toBeTrue();
    expect(mensajes('profesor').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'permiso', n: 4, marcada: true });
    input.click();
    expect(mensajes('profesor').at(-1)).toEqual(jasmine.objectContaining({ accion: 'permiso', n: 4, marcada: false }));

    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'errores', errores: { 'profesor.4.permiso': FALTA } });
    const caja = tarjeta(4).querySelector<HTMLElement>('.ed-permiso')!;
    expect(caja.classList).toContain('es-error');
    expect(texto(q('#e-pm-4'))).toBe(FALTA);
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe('pm-a-4 e-pm-4');
    // Enlace del resumen: a la casilla.
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'profesor.4.permiso' });
    expect(doc.activeElement).toBe(input);
    expect(mensajes('mostrar').length).toBeGreaterThan(0);
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'errores', errores: {} });
    expect(caja.classList).not.toContain('es-error');
    expect(doc.querySelector('#e-pm-4')).toBeNull();
    expect(input.hasAttribute('aria-invalid')).toBeFalse();
  });

  it('nombre cambiado de uno guardado: la misma casilla en lugar de la constancia; al volver, la constancia', () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: [{ ...LISTA[0], nombre: 'Antonio R.', permiso: { tipo: 'casilla', marcada: false } }, LISTA[1]] });
    expect(tarjeta(1).querySelector('.ed-constancia')).toBeNull();
    expect(texto(tarjeta(1).querySelector('.ed-permiso label'))).toBe('Tengo su permiso para publicar su nombre y su foto (obligatorio)');
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: LISTA });
    expect(tarjeta(1).querySelector('.ed-permiso')).toBeNull();
    expect(texto(tarjeta(1).querySelector('.ed-constancia'))).toBe('Permiso marcado por María Ruiz el 03/11/2026');
  });

  it('constancia del alta (d526) con su fecha', () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: [{ ...LISTA[0], permiso: { tipo: 'constancia', texto: 'Permiso dado en el alta el 01/10/2026' } }, LISTA[1]] });
    expect(texto(tarjeta(1).querySelector('.ed-constancia'))).toBe('Permiso dado en el alta el 01/10/2026');
  });

  it('«Quitar» en la barra de la tarjeta (con su nombre) y «Añadir profesor» al final: piden la acción al panel', () => {
    const quitar = tarjeta(1).querySelector<HTMLButtonElement>('.ed-profe-barra button')!;
    expect(texto(quitar)).toBe('Quitar profesor');
    expect(quitar.getAttribute('aria-label')).toBe('Quitar a Antonio Ruiz de la web');
    expect(tarjeta(1).lastElementChild).toBe(quitar.parentElement);
    expect(tarjeta(4).querySelector('.ed-profe-barra button')!.getAttribute('aria-label')).toBe('Quitar a Profesor nuevo de la web');
    quitar.click();
    expect(mensajes('profesor').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'quitar', n: 1 });

    const ul = q('ul.profes');
    const anadir = ul.lastElementChild!.querySelector<HTMLButtonElement>('.ed-anadir-tarjeta')!;
    expect(texto(anadir)).toBe('Añadir profesor');
    anadir.click();
    expect(mensajes('profesor').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'anadir' });
  });

  it('sin profesores: «Añade el primer profesor»', async () => {
    doc.querySelectorAll('li.profe').forEach((li) => li.remove());
    await esperar();
    expect(texto(q('.ed-anadir-tarjeta'))).toBe('Añade el primer profesor');
  });

  it('foco tras las acciones del panel: al «Quitar» del vecino, al botón de añadir o a escribir el nombre nuevo', async () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'foco-profesor', foco: 'quitar:4' });
    await esperar(20);
    expect(doc.activeElement).toBe(tarjeta(4).querySelector('.ed-profe-barra button'));
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'foco-profesor', foco: 'anadir' });
    await esperar(20);
    expect(doc.activeElement).toBe(q('.ed-anadir-tarjeta'));
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'foco-profesor', foco: 'editar:4' });
    await esperar(20);
    expect((doc.activeElement as HTMLElement).classList).toContain('ed-campo__texto');
    expect(doc.activeElement!.closest('li.profe')).toBe(tarjeta(4));
  });

  describe('panel estrecho: «Quitar» siempre visible en la tarjeta (decisión del propietario, 12/11)', () => {
    const idActual = () => mensajes('hoja-menu').at(-1)!['id'] as number;
    const opsHoja = () => (mensajes('hoja-menu').at(-1)!['opciones'] as { op: string; texto: string; pie: string; icono: string; peligro: boolean }[]);
    const fotoDe = (n: number) => tarjeta(n).querySelector<HTMLButtonElement>('.ed-foto')!;
    const barraDe = (n: number) => tarjeta(n).querySelector<HTMLElement>(':scope > .ed-profe-barra')!;
    const estado = (estrecho: boolean) => delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'estado-bloques', sinGuardar: [], quitables: [], estrecho });

    it('la barra se ve sin pasar el ratón ni tener foco; en ancho sigue oculta hasta entonces', async () => {
      estado(false);
      await esperar(250);
      expect(ventana.getComputedStyle(barraDe(1)).opacity).toBe('0');
      estado(true);
      await esperar(250);
      for (const n of [1, 4]) {
        const css = ventana.getComputedStyle(barraDe(n));
        expect(css.opacity).withContext(`tarjeta ${n}`).toBe('1');
        expect(css.display).toBe('flex');
        expect(css.position).toBe('absolute');
        expect(css.top).toBe('10px');
        expect(css.right).toBe('10px');
      }
    });

    it('el botón mide al menos 44 × 44 px y lleva el icono y «Quitar»', async () => {
      estado(true);
      await esperar(250);
      const boton = barraDe(1).querySelector<HTMLButtonElement>('button')!;
      const caja = boton.getBoundingClientRect();
      expect(caja.width).toBeGreaterThanOrEqual(44);
      expect(caja.height).toBeGreaterThanOrEqual(44);
      expect(texto(boton)).toBe('Quitar profesor');
      expect(boton.querySelector('svg')).not.toBeNull();
    });

    it('pulsarla pide al panel quitar a ese profesor (el panel confirma)', () => {
      estado(true);
      barraDe(4).querySelector<HTMLButtonElement>('button')!.click();
      expect(mensajes('profesor').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'quitar', n: 4 });
    });

    it('la hoja de la foto no ofrece «Quitar profesor»: con foto, …, «Quitar foto»; sin foto, solo «Añadir foto»', () => {
      estado(true);
      fotoDe(1).click();
      expect(opsHoja().map((o) => [o.op, o.texto, o.pie, o.icono, o.peligro])).toEqual([
        ['subir', 'Cambiar foto', 'JPG, PNG o WebP, hasta 50 MB', 'foto', false],
        ['describir', 'Describir la foto', '«Antonio en el tatami»', 'texto', false],
        ['quitar', 'Quitar foto', 'Sale con sus iniciales', 'papelera', true],
      ]);
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-menu', menu: 'foto', id: idActual(), op: null, devolverFoco: false });
      fotoDe(4).click();
      expect(opsHoja().map((o) => [o.op, o.texto, o.peligro])).toEqual([['subir', 'Añadir foto', false]]);
    });

    it('con el panel ancho, el menú flotante tampoco lleva «Quitar profesor»', () => {
      estado(false);
      fotoDe(1).click();
      const opciones = Array.from(doc.querySelectorAll<HTMLElement>('.flot [role=menuitem]')).map(texto);
      expect(opciones).toEqual(['Cambiar fotoJPG, PNG o WebP, hasta 50 MB', 'Describir la foto«Antonio en el tatami»', 'Quitar fotoSale con sus iniciales']);
      expect(mensajes('hoja-menu')).toEqual([]);
    });
  });

  it('la constancia no llega al tema ni sale fuera de las tarjetas del editor', () => {
    expect(doc.querySelectorAll('.ed-constancia').length).toBe(1);
    expect(doc.querySelector('.ed-constancia')!.closest('li.profe[data-ed-profesor]')).not.toBeNull();
    desactivar();
    expect(doc.querySelector('.ed-constancia, .ed-permiso, .ed-profe-barra, li[data-ed-profe]')).toBeNull();
    desactivar = () => undefined;
  });
});
