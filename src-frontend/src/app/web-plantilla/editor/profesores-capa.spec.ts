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
  { n: 1, nombre: 'Antonio Ruiz', permiso: { tipo: 'constancia', texto: 'Permiso marcado por María Ruiz el 03/11/2026' }, sedes: [] },
  { n: 4, nombre: '', permiso: { tipo: 'casilla', marcada: false }, sedes: [] },
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
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: LISTA, sedes: [] });
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
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: [{ ...LISTA[0], nombre: 'Antonio R.', permiso: { tipo: 'casilla', marcada: false } }, LISTA[1]], sedes: [] });
    expect(tarjeta(1).querySelector('.ed-constancia')).toBeNull();
    expect(texto(tarjeta(1).querySelector('.ed-permiso label'))).toBe('Tengo su permiso para publicar su nombre y su foto (obligatorio)');
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: LISTA, sedes: [] });
    expect(tarjeta(1).querySelector('.ed-permiso')).toBeNull();
    expect(texto(tarjeta(1).querySelector('.ed-constancia'))).toBe('Permiso marcado por María Ruiz el 03/11/2026');
  });

  it('constancia del alta (d526) con su fecha', () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: [{ ...LISTA[0], permiso: { tipo: 'constancia', texto: 'Permiso dado en el alta el 01/10/2026' } }, LISTA[1]], sedes: [] });
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
        // Sin transición: el valor final no depende de cuánto tarde la máquina (falló en la CI con la de 150 ms a medias).
        barraDe(n).style.transition = 'none';
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

  describe('sedes de cada profesor (W12f-3; boceto `profeHtml`, `abrirSedesProfe`)', () => {
    const SEDES = [
      { id: 6, nombre: 'Casariche' },
      { id: 3, nombre: 'Estepa' },
      { id: 4, nombre: 'Herrera' },
    ];
    const conSedes = (s1: number[], s4: number[] = [], sedes = SEDES) =>
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: [{ ...LISTA[0], sedes: s1 }, { ...LISTA[1], sedes: s4 }], sedes });
    const boton = (n: number) => tarjeta(n).querySelector<HTMLButtonElement>(':scope > .ed-sedes button')!;
    const flot = () => q('.flot--sedes');
    const casillas = () => Array.from(doc.querySelectorAll<HTMLInputElement>('.flot--sedes input[type=checkbox]'));
    const marcadas = () => casillas().map((c) => c.checked);
    const ultimoProfesor = () => mensajes('profesor').at(-1);
    const estado = (estrecho: boolean) => delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'estado-bloques', sinGuardar: [], quitables: [], estrecho });

    beforeEach(() => conSedes([3, 4]));

    it('sin sedes que elegir (lista sin cargar o escuela sin sedes activas) no se pinta ni el botón ni el párrafo', () => {
      conSedes([], [], []);
      expect(doc.querySelector('.ed-sedes, .ed-sin-sedes')).toBeNull();
    });

    it('«Cambiar sus sedes» con sedes, «Elegir sus sedes» y el párrafo sin ellas; con su nombre solo para el lector', () => {
      const b1 = boton(1);
      expect(texto(b1.querySelector('span:not(.ed-oculto)'))).toBe('Cambiar sus sedes');
      expect(b1.querySelector('.ed-oculto')!.textContent).toBe(' de Antonio Ruiz');
      expect(b1.getAttribute('aria-haspopup')).toBe('dialog');
      expect(b1.getAttribute('aria-expanded')).toBe('false');
      expect(b1.querySelector('svg')).not.toBeNull();
      expect(tarjeta(1).querySelector('.ed-sin-sedes')).toBeNull();

      const b4 = boton(4);
      expect(texto(b4.querySelector('span:not(.ed-oculto)'))).toBe('Elegir sus sedes');
      expect(b4.querySelector('.ed-oculto')!.textContent).toBe(' de Profesor nuevo');
      expect(texto(tarjeta(4).querySelector('.ed-sedes > .ed-sin-sedes'))).toBe('Sin sedes: su tarjeta sale sin la línea.');

      // Al llegar sus sedes, cambia el texto y se va el párrafo.
      conSedes([3, 4], [6]);
      expect(texto(boton(4).querySelector('span:not(.ed-oculto)'))).toBe('Cambiar sus sedes');
      expect(tarjeta(4).querySelector('.ed-sin-sedes')).toBeNull();
    });

    it('orden en la tarjeta como `profeHtml`: sedes → permiso o constancia → «Quitar profesor»', () => {
      for (const n of [1, 4]) {
        const clases = Array.from(tarjeta(n).children)
          .map((hijo) => ['ed-sedes', 'ed-permiso', 'ed-constancia', 'ed-profe-barra'].find((c) => hijo.classList.contains(c)))
          .filter((c) => !!c);
        expect(clases).withContext(`tarjeta ${n}`).toEqual(['ed-sedes', n === 1 ? 'ed-constancia' : 'ed-permiso', 'ed-profe-barra']);
      }
    });

    it('el diálogo: título, ayuda, leyenda oculta, una casilla por sede (sin «todas»), las suyas marcadas y «Listo»', () => {
      boton(1).click();
      const d = flot();
      expect(d.hidden).toBeFalse();
      expect(d.getAttribute('role')).toBe('dialog');
      expect(d.getAttribute('aria-labelledby')).toBe('ed-sedes-t');
      // Como el boceto: la ayuda describe el grupo de casillas, no el diálogo.
      expect(d.hasAttribute('aria-describedby')).toBeFalse();
      expect(d.querySelector('fieldset.casillas')!.getAttribute('aria-describedby')).toBe('ed-sedes-a');
      expect(texto(q('#ed-sedes-t'))).toBe('Sedes de Antonio Ruiz');
      expect(texto(q('#ed-sedes-a'))).toBe('Salen en su tarjeta, sobre la línea. No cambia en qué grupos da clase.');
      const leyenda = d.querySelector('fieldset.casillas > legend')!;
      expect(texto(leyenda)).toBe('Sedes donde da clase');
      expect(leyenda.classList).toContain('ed-oculto');
      expect(Array.from(d.querySelectorAll('label.casilla')).map(texto)).toEqual(['Casariche', 'Estepa', 'Herrera']);
      expect(marcadas()).toEqual([false, true, true]);
      const botones = Array.from(d.querySelectorAll('button'));
      expect(botones.map(texto)).toEqual(['Listo']);
      expect(botones[0].classList).toContain('btn--sec');
      expect(doc.activeElement).toBe(casillas()[0]);
      expect(boton(1).getAttribute('aria-expanded')).toBe('true');
      expect(mensajes('hoja-sedes')).toEqual([]);

      boton(4).click();
      expect(texto(q('#ed-sedes-t'))).toBe('Sedes de el profesor nuevo');
      expect(marcadas()).toEqual([false, false, false]);
      expect(boton(1).getAttribute('aria-expanded')).toBe('false');
    });

    it('cada cambio pide el conjunto entero; las casillas pintan lo que contesta el panel (si no acepta, vuelven)', () => {
      boton(1).click();
      casillas()[0].click();
      expect(ultimoProfesor()).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'sedes', n: 1, sedes: [6, 3, 4] });
      conSedes([6, 3, 4]);
      expect(marcadas()).toEqual([true, true, true]);
      expect(texto(boton(1).querySelector('span:not(.ed-oculto)'))).toBe('Cambiar sus sedes');

      casillas()[2].click();
      expect(ultimoProfesor()).toEqual(jasmine.objectContaining({ accion: 'sedes', n: 1, sedes: [6, 3] }));
      expect(marcadas()).toEqual([true, true, false]);
      // El panel no lo acepta (guardando): contesta con lo que vale y la casilla vuelve.
      conSedes([6, 3, 4]);
      expect(marcadas()).toEqual([true, true, true]);
      expect(flot().hidden).toBeFalse();
    });

    it('«Listo» y Escape cierran y devuelven el foco al botón; solo «Listo» pide la voz; pulsar fuera cierra sin foco ni voz', () => {
      boton(1).click();
      q<HTMLButtonElement>('.flot--sedes [data-op="listo-sedes"]').click();
      expect(flot().hidden).toBeTrue();
      expect(doc.activeElement).toBe(boton(1));
      expect(boton(1).getAttribute('aria-expanded')).toBe('false');
      expect(ultimoProfesor()).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'anunciar-sedes', n: 1 });

      // Boceto (keydown de `.flot`): Escape solo cierra y devuelve el foco, sin voz.
      const voces = mensajes('profesor').filter((m) => m['accion'] === 'anunciar-sedes').length;
      boton(4).click();
      casillas()[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
      expect(flot().hidden).toBeTrue();
      expect(doc.activeElement).toBe(boton(4));
      expect(boton(4).getAttribute('aria-expanded')).toBe('false');
      expect(mensajes('profesor').filter((m) => m['accion'] === 'anunciar-sedes').length).toBe(voces);

      const antes = mensajes('profesor').length;
      boton(1).click();
      doc.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
      expect(flot().hidden).toBeTrue();
      expect(mensajes('profesor').length).toBe(antes);
    });

    it('se cierra solo si el profesor se va o ya no hay sedes que elegir', () => {
      boton(1).click();
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: [{ ...LISTA[1], sedes: [] }], sedes: SEDES });
      expect(flot().hidden).toBeTrue();
      conSedes([3]);
      boton(1).click();
      conSedes([], [], []);
      expect(flot().hidden).toBeTrue();
    });

    it('enlace del resumen (`profesor.<n>.sedes`): al botón de sus sedes', () => {
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'profesor.4.sedes' });
      expect(doc.activeElement).toBe(boton(4));
      expect(mensajes('mostrar').length).toBeGreaterThan(0);
    });

    describe('dentro de la parte que se ve en el panel (zona `zona-visible`)', () => {
      // Como en el panel: el iframe mide todo el documento (no se desplaza) y la tarjeta queda lejos del principio.
      beforeEach(() => {
        marco.style.cssText = 'width:1000px;height:2400px;border:0';
        doc.body.style.cssText = 'margin:0;padding-top:1200px';
      });
      const zona = (top: number, alto: number) => delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'zona-visible', top, alto });
      const caja = (el: Element) => el.getBoundingClientRect();
      /** La zona de `alto` px con el botón al `parte` (0-1) de su alto. */
      const zonaConBoton = (parte: number, alto = 500) => {
        const top = Math.round(caja(boton(1)).top - parte * alto);
        zona(top, alto);
        return { top, bottom: top + alto };
      };
      function dentro(z: { top: number; bottom: number }): void {
        const r = caja(flot());
        expect(r.top).withContext('borde de arriba').toBeGreaterThanOrEqual(z.top + 4 - 1);
        expect(r.bottom).withContext('borde de abajo').toBeLessThanOrEqual(z.bottom - 4 + 1);
      }

      it('botón arriba, en medio o abajo de la zona: el diálogo queda entero dentro (bajo el botón o sobre él)', () => {
        for (const parte of [0.2, 0.5, 0.85]) {
          const z = zonaConBoton(parte);
          boton(1).click();
          expect(flot().hidden).toBeFalse();
          dentro(z);
          expect(flot().style.maxHeight).withContext('cabe: sin alto limitado').toBe('');
          if (parte === 0.2) {
            expect(caja(flot()).top).withContext('bajo el botón').toBeGreaterThanOrEqual(caja(boton(1)).bottom);
          }
          if (parte === 0.85) {
            expect(caja(flot()).bottom).withContext('sobre el botón').toBeLessThanOrEqual(caja(boton(1)).top);
          }
          q<HTMLButtonElement>('.flot--sedes [data-op="listo-sedes"]').click();
          expect(flot().hidden).toBeTrue();
        }
      });

      it('más alto que la zona: alto limitado a ella, las casillas se desplazan por dentro y «Listo» se ve', () => {
        const z = zonaConBoton(0.5, 230);
        boton(1).click();
        dentro(z);
        expect(flot().style.maxHeight).not.toBe('');
        const lista = q('.flot--sedes fieldset.casillas');
        expect(lista.scrollHeight).toBeGreaterThan(lista.clientHeight);
        const listo = caja(q('.flot--sedes [data-op="listo-sedes"]'));
        expect(listo.height).toBeGreaterThan(0);
        expect(listo.top).toBeGreaterThanOrEqual(caja(flot()).top - 1);
        expect(listo.bottom).toBeLessThanOrEqual(caja(flot()).bottom + 1);
      });

      it('si la zona cambia con el diálogo abierto, se recoloca dentro de la nueva', () => {
        zonaConBoton(0.2);
        boton(1).click();
        const antes = caja(flot()).top;
        const z = zonaConBoton(0.85);
        dentro(z);
        expect(caja(flot()).top).toBeLessThan(antes);
        expect(doc.activeElement).toBe(casillas()[0]);
      });

      it('sin zona del panel, como antes: bajo el botón aunque quede lejos', () => {
        boton(1).click();
        expect(Math.abs(caja(flot()).top - (caja(boton(1)).bottom + 8))).toBeLessThan(1);
        expect(flot().style.maxHeight).toBe('');
      });

      it('el menú de la foto también queda dentro de la zona', () => {
        const z = zonaConBoton(0.95);
        tarjeta(1).querySelector<HTMLButtonElement>('.ed-foto')!.click();
        const menu = caja(Array.from(doc.querySelectorAll<HTMLElement>('.flot:not(.flot--sedes)')).find((f) => !f.hidden)!);
        expect(menu.height).toBeGreaterThan(0);
        expect(menu.top).toBeGreaterThanOrEqual(z.top + 4 - 1);
        expect(menu.bottom).toBeLessThanOrEqual(z.bottom - 4 + 1);
      });
    });

    describe('panel estrecho: la hoja del panel', () => {
      beforeEach(() => estado(true));
      const idHoja = () => mensajes('hoja-sedes').at(-1)!['id'] as number;

      it('el botón mide al menos 44 px de alto', async () => {
        await esperar(20);
        expect(boton(1).getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
      });

      it('pide la hoja (sin diálogo en la vista previa); al cerrarla el panel con foco, el foco vuelve al botón', () => {
        boton(1).click();
        expect(mensajes('hoja-sedes').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'hoja-sedes', n: 1, id: idHoja() });
        expect(flot().hidden).toBeTrue();
        expect(boton(1).getAttribute('aria-expanded')).toBe('true');
        // Otro número: no es de esta apertura.
        delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id: idHoja() + 1, devolverFoco: true });
        expect(boton(1).getAttribute('aria-expanded')).toBe('true');
        delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id: idHoja(), devolverFoco: true });
        expect(doc.activeElement).toBe(boton(1));
        expect(boton(1).getAttribute('aria-expanded')).toBe('false');
        expect(mensajes('cerrar-hoja-sedes')).toEqual([]);
        // La voz la da el panel: la capa no la pide.
        expect(mensajes('profesor').filter((m) => m['accion'] === 'anunciar-sedes')).toEqual([]);
      });

      it('si la capa la cierra sola (el profesor ya no está), avisa al panel con su número', () => {
        boton(1).click();
        const id = idHoja();
        delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: [{ ...LISTA[1], sedes: [] }], sedes: SEDES });
        expect(mensajes('cerrar-hoja-sedes')).toEqual([{ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-sedes', id }]);
      });
    });

    it('al desactivar la capa no queda nada de las sedes', () => {
      boton(1).click();
      desactivar();
      expect(doc.querySelector('.ed-sedes, .ed-sin-sedes, .flot--sedes')).toBeNull();
      desactivar = () => undefined;
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
