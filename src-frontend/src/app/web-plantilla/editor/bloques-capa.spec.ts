import { activarCapaEditor } from './capa-editor';
import { BloqueBorrador, CANAL_EDITOR_WEB, esMensajeDelEditor, esMensajeDelPanel } from './puente-editor';

/** Lo justo de Inicio de «La línea comarcal» para los bloques (W12e). */
const WEB = `
<div class="lc">
  <main id="principal" tabindex="-1">
    <section class="portada" id="portada"><div class="envoltura portada-rejilla"><h1 class="titular">Taekwondo</h1></div></section>
    <section class="presentacion" id="presentacion"><div class="envoltura"><h2 class="titulo-seccion">Clases cerca de casa</h2></div></section>
    <section class="seccion" id="disciplinas"><div class="envoltura"><h2 class="titulo-seccion">Taekwondo</h2></div></section>
    <section class="seccion" id="galeria"><div class="envoltura"><ol class="galeria"></ol></div></section>
    <section class="seccion" id="texto-foto-3"><div class="envoltura tf"><div class="tf-texto">
      <div class="cabeza-seccion"><p class="rotulo">03</p><h2 class="titulo-seccion">Nuestra sala</h2></div>
    </div></div></section>
    <section class="seccion" id="eventos"><div class="envoltura"><h2 class="titulo-seccion">Próximos eventos</h2></div></section>
    <section class="seccion" id="contacto"><div class="envoltura"><h2 class="titulo-seccion">¿Hablamos?</h2></div></section>
  </main>
</div>`;

const fila = (clave: string, texto: string | null, extra: Partial<BloqueBorrador> = {}): BloqueBorrador => ({
  clave,
  texto,
  imagenUrl: null,
  orden: 0,
  oculto: false,
  ...extra,
});
const BORRADOR: BloqueBorrador[] = [
  fila('portada.titular', 'Taekwondo'),
  fila('presentacion', null, { orden: 0 }),
  fila('presentacion.texto', ''),
  fila('galeria', null, { orden: 2, oculto: true }),
  fila('texto_foto.3', null, { orden: 3 }),
  fila('texto_foto.3.titulo', 'Nuestra sala'),
];

describe('bloques en el sitio (W12e, capa de edición)', () => {
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
    estado();
  });

  afterEach(() => {
    desactivar();
    marco.remove();
  });

  function delPanel(data: unknown, origin = ventana.origin, source: MessageEventSource = window): void {
    ventana.dispatchEvent(new MessageEvent('message', { data, origin, source }));
  }
  const estado = (sinGuardar: string[] = [], quitables: string[] = ['texto_foto.3'], estrecho = false) =>
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'estado-bloques', sinGuardar, quitables, estrecho });
  const bloque = (id: string) => doc.querySelector<HTMLElement>(`.ed-bloque[data-bloque="${id}"]`)!;
  const botones = (id: string) => Array.from(bloque(id).querySelectorAll<HTMLButtonElement>(':scope > .ed-barra .ed-acciones button'));
  const boton = (id: string, t: string) => botones(id).find((b) => b.textContent!.trim() === t)!;
  const texto = (el: Element | null) => (el?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const mensajes = (tipo: string) =>
    enviados.calls
      .allArgs()
      .map(([m]) => m as { tipo: string } & Record<string, unknown>)
      .filter((m) => m.tipo === tipo);
  const esperar = () => new Promise((r) => setTimeout(r, 20));
  const tecla = (el: Element, key: string) => el.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));

  it('barra de cada bloque movible: Subir, Bajar, Ocultar o Mostrar y, si se puede, Quitar (boceto `barraHtml`)', () => {
    expect(botones('presentacion').map((b) => b.textContent!.trim())).toEqual(['Subir', 'Bajar', 'Ocultar']);
    expect(bloque('presentacion').querySelector('.ed-acciones')!.getAttribute('role')).toBe('toolbar');
    expect(bloque('presentacion').querySelector('.ed-acciones')!.getAttribute('aria-label')).toBe('Acciones del bloque Presentación');
    // El primero no sube.
    expect(boton('presentacion', 'Subir').getAttribute('aria-disabled')).toBe('true');
    expect(boton('presentacion', 'Bajar').hasAttribute('aria-disabled')).toBeFalse();
    expect(boton('presentacion', 'Bajar').getAttribute('aria-label')).toBe('Bajar el bloque Presentación');
    // Oculto: Mostrar.
    expect(botones('galeria').map((b) => b.textContent!.trim())).toEqual(['Subir', 'Bajar', 'Mostrar']);
    // «Texto con foto»: Quitar, con su título en la etiqueta.
    expect(botones('texto-foto-3').map((b) => b.textContent!.trim())).toEqual(['Subir', 'Bajar', 'Ocultar', 'Quitar']);
    expect(boton('texto-foto-3', 'Quitar').getAttribute('aria-label')).toBe('Quitar el bloque Texto con foto «Nuestra sala»');
    // Las fijas siguen con su nota; la portada, con la suya y sin acciones.
    expect(bloque('disciplinas').querySelector('.ed-acciones button')).toBeNull();
    expect(texto(bloque('portada').querySelector('.ed-acciones'))).toBe('Siempre arriba. Pulsa un texto o la foto para cambiarlos.');
  });

  it('el último bloque de la página no baja (las fijas cuentan como puesto)', () => {
    const ultimo = doc.querySelector('section#eventos')!;
    ultimo.remove();
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: BORRADOR });
    expect(boton('texto-foto-3', 'Bajar').getAttribute('aria-disabled')).toBe('true');
  });

  it('cada botón pide su acción al panel; los deshabilitados no', () => {
    boton('presentacion', 'Subir').click();
    boton('presentacion', 'Bajar').click();
    boton('galeria', 'Mostrar').click();
    boton('texto-foto-3', 'Quitar').click();
    expect(mensajes('bloque')).toEqual([
      { canal: CANAL_EDITOR_WEB, tipo: 'bloque', accion: 'bajar', bloque: 'presentacion' },
      { canal: CANAL_EDITOR_WEB, tipo: 'bloque', accion: 'mostrar', bloque: 'galeria' },
      { canal: CANAL_EDITOR_WEB, tipo: 'bloque', accion: 'quitar', bloque: 'texto_foto.3' },
    ]);
  });

  it('pastillas: «Oculto en la web · Mostrar», «Vacío: no sale en la web» y «Sin guardar» (boceto `estadosHtml`)', () => {
    const oculto = bloque('galeria').querySelector('.ed-estados')!;
    expect(texto(oculto)).toBe('Oculto en la webMostrar');
    expect(bloque('galeria').classList).toContain('es-oculto');
    const mostrar = oculto.querySelector<HTMLButtonElement>('.ed-mini')!;
    expect(mostrar.getAttribute('aria-label')).toBe('Mostrar el bloque Galería');
    mostrar.click();
    expect(mensajes('bloque').pop()).toEqual(jasmine.objectContaining({ accion: 'mostrar', bloque: 'galeria' }));
    // Presentación con su texto vaciado y sin foto.
    expect(texto(bloque('presentacion').querySelector('.ed-estados'))).toBe('Vacío: no sale en la web');
    expect(bloque('texto-foto-3').querySelector('.ed-estados')).toBeNull();
    estado(['texto_foto.3', 'cabecera']);
    expect(texto(bloque('texto-foto-3').querySelector('.ed-estados'))).toBe('Sin guardar');
    expect(bloque('texto-foto-3').querySelector('.ed-pastilla--cambio .ed-punto')).not.toBeNull();
  });

  it('dice al panel los bloques de la página, en su orden, y los vacíos (solo si cambian)', () => {
    expect(mensajes('bloques').pop()).toEqual({
      canal: CANAL_EDITOR_WEB,
      tipo: 'bloques',
      orden: ['presentacion', 'disciplinas', 'galeria', 'texto_foto.3', 'eventos'],
      vacios: ['presentacion', 'galeria'],
    });
    const n = mensajes('bloques').length;
    estado(['cabecera']);
    expect(mensajes('bloques').length).toBe(n);
  });

  it('huecos «Añadir bloque»: uno delante de cada bloque y otro delante de Contacto', () => {
    const huecos = Array.from(doc.querySelectorAll<HTMLElement>('.ed-hueco'));
    expect(huecos.length).toBe(6);
    expect(huecos[0].nextElementSibling).toBe(bloque('presentacion'));
    expect(huecos[5].nextElementSibling).toBe(bloque('contacto'));
    const b = huecos[2].querySelector<HTMLButtonElement>('.ed-anadir')!;
    expect(texto(b)).toBe('Añadir bloque en el puesto 3');
    expect(b.getAttribute('aria-haspopup')).toBe('menu');
    expect(b.getAttribute('aria-expanded')).toBe('false');
  });

  it('menú de tipos: los únicos que ya están, deshabilitados; elegir uno pide añadirlo en ese puesto', () => {
    const b = doc.querySelector<HTMLButtonElement>('.ed-anadir[data-pos="2"]')!;
    b.click();
    const menu = doc.querySelector<HTMLElement>('.flot--anadir')!;
    expect(menu.hidden).toBeFalse();
    expect(b.getAttribute('aria-expanded')).toBe('true');
    expect(texto(menu.querySelector('.flot__titulo'))).toBe('Añadir bloque en el puesto 3');
    const ops = Array.from(menu.querySelectorAll<HTMLElement>('[role=menuitem]'));
    expect(ops.map((o) => texto(o.querySelector('span')!.firstChild as Element))).toEqual([
      'Presentación',
      'Profesores',
      'Galería',
      'Fotos de las sedes',
      'Texto con foto',
    ]);
    expect(ops.slice(0, 3).every((o) => o.getAttribute('aria-disabled') === 'true')).toBeTrue();
    expect(texto(ops[0].querySelector('small'))).toBe('Ya está en tu web. Si no lo quieres, ocúltalo.');
    expect(ops[3].hasAttribute('aria-disabled')).toBeFalse();
    expect(texto(ops[4].querySelector('small'))).toBe('Un título, un texto y una foto. Puedes poner varios.');
    // El foco va al primero que se puede elegir.
    expect(doc.activeElement).toBe(ops[3]);
    ops[0].click();
    expect(mensajes('anadir-bloque')).toEqual([]);
    ops[4].click();
    expect(mensajes('anadir-bloque')).toEqual([{ canal: CANAL_EDITOR_WEB, tipo: 'anadir-bloque', pos: 2, bloque: 'texto_foto' }]);
    expect(menu.hidden).toBeTrue();
  });

  it('menú: flechas, Escape cierra y devuelve el foco al hueco', () => {
    const b = doc.querySelector<HTMLButtonElement>('.ed-anadir[data-pos="0"]')!;
    b.click();
    const ops = Array.from(doc.querySelectorAll<HTMLElement>('.flot--anadir [role=menuitem]'));
    tecla(doc.activeElement!, 'ArrowDown');
    expect(doc.activeElement).toBe(ops[4]);
    tecla(doc.activeElement!, 'Escape');
    expect(doc.querySelector<HTMLElement>('.flot--anadir')!.hidden).toBeTrue();
    expect(doc.activeElement).toBe(b);
    expect(b.getAttribute('aria-expanded')).toBe('false');
  });

  it('con «Fotos de las sedes» ya en la web, también deshabilitada', () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [...BORRADOR, fila('sedes', null)] });
    doc.querySelector<HTMLButtonElement>('.ed-anadir[data-pos="0"]')!.click();
    const ops = Array.from(doc.querySelectorAll<HTMLElement>('.flot--anadir [role=menuitem]'));
    expect(ops[3].getAttribute('aria-disabled')).toBe('true');
  });

  describe('panel estrecho (d541): el menú «Añadir bloque» sale como hoja del panel', () => {
    const hueco = () => doc.querySelector<HTMLButtonElement>('.ed-anadir[data-pos="2"]')!;
    const respuesta = (op: string | null, devolverFoco = false) =>
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-menu', menu: 'anadir', op, devolverFoco });

    beforeEach(() => estado([], ['texto_foto.3'], true));

    it('pide la hoja con título y opciones, sin abrir el menú flotante', () => {
      hueco().click();
      expect(doc.querySelector<HTMLElement>('.flot--anadir')!.hidden).toBeTrue();
      expect(hueco().getAttribute('aria-expanded')).toBe('true');
      const [m] = mensajes('hoja-menu');
      expect(esMensajeDelEditor(m)).toBeTrue();
      expect(m['menu']).toBe('anadir');
      expect(m['titulo']).toBe('Añadir bloque en el puesto 3');
      const ops = m['opciones'] as { op: string; texto: string; pie: string; icono: string; desactivada: boolean; peligro: boolean }[];
      expect(ops.map((o) => [o.op, o.desactivada])).toEqual([
        ['presentacion', true],
        ['profesores', true],
        ['galeria', true],
        ['sedes', false],
        ['texto_foto', false],
      ]);
      expect(ops[0].pie).toBe('Ya está en tu web. Si no lo quieres, ocúltalo.');
      expect(ops[4].icono).toBe('texto');
    });

    it('la opción elegida llega como con el menú flotante y quita el estado abierto', () => {
      hueco().click();
      respuesta('texto_foto');
      expect(mensajes('anadir-bloque')).toEqual([{ canal: CANAL_EDITOR_WEB, tipo: 'anadir-bloque', pos: 2, bloque: 'texto_foto' }]);
      expect(hueco().getAttribute('aria-expanded')).toBe('false');
    });

    it('una opción que no estaba ofrecida (ya existe o no es un tipo) no se añade', () => {
      hueco().click();
      respuesta('presentacion');
      respuesta('portada');
      expect(mensajes('anadir-bloque')).toEqual([]);
    });

    it('cerrada sin elegir: devuelve el foco al hueco solo si el panel lo pide', () => {
      hueco().click();
      respuesta(null, false);
      expect(hueco().getAttribute('aria-expanded')).toBe('false');
      expect(doc.activeElement).not.toBe(hueco());
      hueco().click();
      respuesta(null, true);
      expect(doc.activeElement).toBe(hueco());
      expect(mensajes('anadir-bloque')).toEqual([]);
    });

    it('una respuesta sin hoja abierta no hace nada', () => {
      respuesta('texto_foto');
      expect(mensajes('anadir-bloque')).toEqual([]);
    });
  });

  it('foco tras una acción: al botón pedido por el panel, al hueco o al nombre', async () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'foco-bloque', foco: 'bajar:presentacion' });
    await esperar();
    expect(doc.activeElement).toBe(boton('presentacion', 'Bajar'));
    expect(mensajes('mostrar').length).toBeGreaterThan(0);
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'foco-bloque', foco: 'a:1' });
    await esperar();
    expect(doc.activeElement).toBe(doc.querySelector('.ed-anadir[data-pos="1"]'));
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'foco-bloque', foco: 'sel:texto_foto.3' });
    await esperar();
    expect(doc.activeElement).toBe(bloque('texto-foto-3').querySelector('.ed-nombre'));
  });

  it('panel estrecho: sin acciones en la barra (clase `estrecho`) y el nombre de un bloque movible pide la hoja', () => {
    estado([], ['texto_foto.3'], true);
    expect(doc.documentElement.classList).toContain('estrecho');
    bloque('galeria').querySelector<HTMLButtonElement>('.ed-nombre')!.click();
    expect(mensajes('hoja')).toEqual([{ canal: CANAL_EDITOR_WEB, tipo: 'hoja', bloque: 'galeria' }]);
    expect(bloque('galeria').classList).toContain('es-sel');
    bloque('portada').querySelector<HTMLButtonElement>('.ed-nombre')!.click();
    bloque('disciplinas').querySelector<HTMLButtonElement>('.ed-nombre')!.click();
    expect(mensajes('hoja').length).toBe(1);
    estado([], [], false);
    expect(doc.documentElement.classList).not.toContain('estrecho');
  });

  it('mensajes del panel de otro origen, de otra ventana o con ids no permitidos: ignorados', () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'estado-bloques', sinGuardar: ['galeria'], quitables: [], estrecho: true }, 'https://otro.example');
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'estado-bloques', sinGuardar: ['galeria'], quitables: [], estrecho: true }, ventana.origin, ventana);
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'estado-bloques', sinGuardar: ['contacto.x'], quitables: [], estrecho: true });
    expect(doc.documentElement.classList).not.toContain('estrecho');
    expect(texto(bloque('galeria').querySelector('.ed-estados'))).not.toContain('Sin guardar');
  });
});

describe('puente: mensajes de bloques (W12e)', () => {
  const m = (datos: Record<string, unknown>) => ({ canal: CANAL_EDITOR_WEB, ...datos });

  it('del iframe: acción de la lista cerrada y bloque de Inicio', () => {
    expect(esMensajeDelEditor(m({ tipo: 'bloque', accion: 'subir', bloque: 'galeria' }))).toBeTrue();
    expect(esMensajeDelEditor(m({ tipo: 'bloque', accion: 'quitar', bloque: 'texto_foto.12' }))).toBeTrue();
    expect(esMensajeDelEditor(m({ tipo: 'bloque', accion: 'borrar', bloque: 'galeria' }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'bloque', accion: 'subir', bloque: 'contacto' }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'bloque', accion: 'subir', bloque: 'texto_foto.100' }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'bloque', accion: 'subir', bloque: 'portada.titular' }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'anadir-bloque', pos: 2, bloque: 'texto_foto' }))).toBeTrue();
    expect(esMensajeDelEditor(m({ tipo: 'anadir-bloque', pos: -1, bloque: 'texto_foto' }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'anadir-bloque', pos: 1.5, bloque: 'texto_foto' }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'anadir-bloque', pos: 0, bloque: 'portada' }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'hoja', bloque: 'sedes' }))).toBeTrue();
    expect(esMensajeDelEditor(m({ tipo: 'hoja', bloque: '<img>' }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'bloques', orden: ['presentacion', 'eventos'], vacios: [] }))).toBeTrue();
    expect(esMensajeDelEditor(m({ tipo: 'bloques', orden: ['presentacion', 'presentacion'], vacios: [] }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'bloques', orden: ['cabecera'], vacios: [] }))).toBeFalse();
  });

  it('del panel: estado y foco con forma conocida', () => {
    expect(esMensajeDelPanel(m({ tipo: 'estado-bloques', sinGuardar: ['cabecera', 'texto_foto.2'], quitables: ['texto_foto.2'], estrecho: false }))).toBeTrue();
    expect(esMensajeDelPanel(m({ tipo: 'estado-bloques', sinGuardar: [], quitables: ['cabecera'], estrecho: false }))).toBeFalse();
    expect(esMensajeDelPanel(m({ tipo: 'estado-bloques', sinGuardar: [], quitables: [], estrecho: 'si' }))).toBeFalse();
    expect(esMensajeDelPanel(m({ tipo: 'foco-bloque', foco: 'a:3' }))).toBeTrue();
    expect(esMensajeDelPanel(m({ tipo: 'foco-bloque', foco: 'editar:texto_foto.1' }))).toBeTrue();
    expect(esMensajeDelPanel(m({ tipo: 'foco-bloque', foco: 'subir:contacto' }))).toBeFalse();
    expect(esMensajeDelPanel(m({ tipo: 'foco-bloque', foco: 'x"]:galeria' }))).toBeFalse();
  });

  it('d541: la hoja de un menú de la capa pide y responde con forma conocida', () => {
    const op = { op: 'quitar', texto: 'Quitar foto', pie: '', icono: 'papelera', desactivada: false, peligro: true };
    expect(esMensajeDelEditor(m({ tipo: 'hoja-menu', menu: 'foto', titulo: 'Foto de la portada', opciones: [op] }))).toBeTrue();
    expect(esMensajeDelEditor(m({ tipo: 'hoja-menu', menu: 'otro', titulo: 'x', opciones: [op] }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'hoja-menu', menu: 'foto', titulo: 'x', opciones: [] }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'hoja-menu', menu: 'foto', titulo: 'x', opciones: [{ ...op, icono: 'script' }] }))).toBeFalse();
    expect(esMensajeDelEditor(m({ tipo: 'hoja-menu', menu: 'foto', titulo: 'x'.repeat(121), opciones: [op] }))).toBeFalse();
    expect(esMensajeDelPanel(m({ tipo: 'respuesta-hoja-menu', menu: 'anadir', op: 'galeria', devolverFoco: false }))).toBeTrue();
    expect(esMensajeDelPanel(m({ tipo: 'respuesta-hoja-menu', menu: 'foto', op: null, devolverFoco: true }))).toBeTrue();
    expect(esMensajeDelPanel(m({ tipo: 'respuesta-hoja-menu', menu: 'foto', op: 3, devolverFoco: true }))).toBeFalse();
    expect(esMensajeDelPanel(m({ tipo: 'respuesta-hoja-menu', menu: 'foto', op: null }))).toBeFalse();
  });
});
