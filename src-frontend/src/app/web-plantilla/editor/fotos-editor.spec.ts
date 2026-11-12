import { activarCapaEditor } from './capa-editor';
import { BloqueBorrador, CANAL_EDITOR_WEB, EstadoSubidaFoto, esMensajeDelEditor } from './puente-editor';

/** Lo justo de Inicio de «La línea comarcal» para las fotos sueltas (W12f-1). */
const WEB = `
<div class="lc">
  <main id="principal" tabindex="-1">
    <section class="portada" id="portada">
      <div class="envoltura portada-rejilla">
        <div class="portada-texto"><h1 class="titular" id="portada-titulo">Taekwondo en seis pueblos</h1></div>
        <figure class="foto portada-foto"><img src="https://x/p.webp" alt="Niños en fila"></figure>
      </div>
    </section>
    <section class="presentacion" id="presentacion">
      <figure class="foto presentacion-foto"><div class="presentacion-velo"><div class="envoltura">
        <p class="rotulo">02 · El club</p><h2 class="titulo-seccion" id="presentacion-titulo">Clases cerca de casa</h2>
      </div></div></figure>
      <div class="presentacion-cuerpo"><div class="envoltura"><div class="cuerpo"><p>Uno.</p></div></div></div>
    </section>
    <section class="seccion" id="profesores"><div class="envoltura"><ul class="profes">
      <li class="profe"><figure class="foto profe-foto"><img src="https://x/a.webp" alt="Ana"></figure><h3 class="profe-nombre">Ana</h3></li>
    </ul></div></section>
    <section class="seccion" id="galeria"><div class="envoltura"><ol class="galeria">
      <li><figure class="foto"><img src="https://x/g.webp" alt="Exhibición"></figure></li>
    </ol></div></section>
    <section class="seccion" id="texto-foto-3"><div class="envoltura tf"><div class="tf-texto">
      <div class="cabeza-seccion"><p class="rotulo">04</p><h2 class="titulo-seccion" id="texto-foto-3-titulo">Nuestra sala</h2></div>
      <div class="cuerpo"><p>Tatami nuevo.</p></div>
    </div></div></section>
  </main>
</div>`;

const fila = (clave: string, texto: string | null, imagenUrl: string | null = null): BloqueBorrador => ({ clave, texto, imagenUrl, orden: 0, oculto: false });
const BORRADOR: BloqueBorrador[] = [
  fila('portada.titular', 'Taekwondo en seis pueblos'),
  fila('portada.foto', 'Niños en fila', 'https://x/p.webp'),
  fila('texto_foto.3', null),
  fila('texto_foto.3.titulo', 'Nuestra sala'),
  fila('galeria.1', 'Exhibición', 'https://x/g.webp'),
];

describe('fotos sueltas en el sitio (W12f-1, capa de edición)', () => {
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
  });

  afterEach(() => {
    desactivar();
    marco.remove();
  });

  function delPanel(data: unknown): void {
    ventana.dispatchEvent(new MessageEvent('message', { data, origin: ventana.origin, source: window }));
  }
  const estado = (subidas: Record<string, EstadoSubidaFoto> = {}, cambiadas: string[] = [], anteriores: string[] = []) =>
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'fotos', cambiadas, anteriores, subidas });
  const q = <T extends HTMLElement = HTMLElement>(selector: string) => doc.querySelector<T>(selector)!;
  const foto = (clave: string) => doc.querySelector<HTMLButtonElement>(`.ed-foto[data-foto="${clave}"]`)!;
  const texto = (el: Element | null) => (el?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const mensajes = (tipo: string) =>
    enviados.calls
      .allArgs()
      .map(([m]) => m as { tipo: string } & Record<string, unknown>)
      .filter((m) => m.tipo === tipo);
  const flot = () => q('.flot');
  const opciones = () => Array.from(doc.querySelectorAll<HTMLElement>('.flot [role=menuitem]'));
  const tecla = (el: Element, key: string) => el.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
  const esperar = (ms = 60) => new Promise((r) => setTimeout(r, ms));

  it('capa sobre las tres fotos sueltas; la galería y los profesores siguen sin ser editables', () => {
    const portada = foto('portada.foto');
    expect(portada.parentElement!.classList).toContain('portada-foto');
    expect(portada.getAttribute('aria-label')).toBe('Cambiar foto de la portada');
    expect(portada.getAttribute('aria-haspopup')).toBe('menu');
    expect(portada.getAttribute('aria-expanded')).toBe('false');
    expect(texto(portada)).toBe('Cambiar foto');
    expect(portada.tabIndex).toBe(0);

    const presentacion = foto('presentacion.foto');
    expect(presentacion.classList).toContain('es-vacia');
    expect(presentacion.classList).toContain('ed-foto--arriba');
    expect(presentacion.hasAttribute('aria-label')).toBeFalse();
    expect(texto(presentacion)).toBe('Añade una foto del clubJPG, PNG o WebP, hasta 50 MB');

    // «Texto con foto» sin foto: la capa pone el hueco (boceto `textoFotoHtml`).
    const tf = foto('texto_foto.3.foto');
    expect(tf.parentElement!.matches('#texto-foto-3 .tf > figure.ed-hueco-foto.tf-foto')).toBeTrue();
    expect(texto(tf.querySelector('.ed-foto__pista'))).toBe('Añade una foto');

    expect(doc.querySelectorAll('.ed-foto').length).toBe(3);
    expect(q('#galeria figure').querySelector('.ed-foto')).toBeNull();
    expect(q('#profesores figure').querySelector('.ed-foto')).toBeNull();
  });

  it('menú de la portada: Cambiar y Describir, sin Quitar (es obligatoria); teclado y Escape', () => {
    foto('portada.foto').click();
    expect(flot().hidden).toBeFalse();
    expect(texto(q('.flot__titulo'))).toBe('Foto de la portada');
    expect(q('.flot__lista').getAttribute('role')).toBe('menu');
    expect(opciones().map(texto)).toEqual(['Cambiar fotoJPG, PNG o WebP, hasta 50 MB', 'Describir la foto«Niños en fila»']);
    expect(foto('portada.foto').getAttribute('aria-expanded')).toBe('true');
    expect(doc.activeElement).toBe(opciones()[0]);
    tecla(opciones()[0], 'ArrowDown');
    expect(doc.activeElement).toBe(opciones()[1]);
    tecla(opciones()[1], 'ArrowDown');
    expect(doc.activeElement).toBe(opciones()[0]);
    tecla(opciones()[0], 'End');
    expect(doc.activeElement).toBe(opciones()[1]);
    tecla(opciones()[1], 'Escape');
    expect(flot().hidden).toBeTrue();
    expect(foto('portada.foto').getAttribute('aria-expanded')).toBe('false');
    expect(doc.activeElement).toBe(foto('portada.foto'));
  });

  it('menú de una foto opcional cambiada: Volver a la anterior y Quitar foto', () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [...BORRADOR, fila('texto_foto.3.foto', '', 'https://x/n.webp')] });
    estado({}, ['texto_foto.3.foto'], ['texto_foto.3.foto']);
    foto('texto_foto.3.foto').click();
    expect(texto(q('.flot__titulo'))).toBe('Foto de «Nuestra sala»');
    expect(opciones().map(texto)).toEqual([
      'Cambiar fotoJPG, PNG o WebP, hasta 50 MB',
      'Describir la fotoFalta: qué se ve, para quien no puede verla',
      'Volver a la anterior',
      'Quitar foto',
    ]);
    expect(opciones()[3].classList).toContain('flot__op--peligro');
    expect(doc.querySelector('.flot__sep[role=separator]')).not.toBeNull();
    opciones()[3].click();
    expect(mensajes('foto').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'foto', clave: 'texto_foto.3.foto', accion: 'quitar' });
    foto('texto_foto.3.foto').click();
    opciones()[2].click();
    expect(mensajes('foto').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'foto', clave: 'texto_foto.3.foto', accion: 'anterior' });
  });

  it('cambiada sin anterior: «Nueva, sin guardar» y sin «Volver a la anterior»', () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [...BORRADOR, fila('presentacion.foto', '', 'https://x/n.webp')] });
    estado({}, ['presentacion.foto'], []);
    const figura = q('.presentacion-foto');
    expect(texto(figura.querySelector('.ed-nueva'))).toBe('Nueva, sin guardar');
    foto('presentacion.foto').click();
    expect(opciones().map(texto)).not.toContain('Volver a la anterior');
    estado();
    expect(figura.querySelector('.ed-nueva')).toBeNull();
  });

  it('Cambiar foto: elige el fichero y lo manda al panel (la capa no llama a la API)', () => {
    const entrada = q<HTMLInputElement>('input[type=file]');
    expect(entrada.accept).toBe('image/jpeg,image/png,image/webp');
    const abrirSelector = spyOn(entrada, 'click');
    foto('portada.foto').click();
    opciones()[0].click();
    expect(abrirSelector).toHaveBeenCalled();
    expect(flot().hidden).toBeTrue();
    const archivo = new File(['x'], 'foto-nueva.jpg', { type: 'image/jpeg' });
    Object.defineProperty(entrada, 'files', { value: [archivo], configurable: true });
    entrada.dispatchEvent(new Event('change'));
    expect(mensajes('subir-foto')).toEqual([{ canal: CANAL_EDITOR_WEB, tipo: 'subir-foto', clave: 'portada.foto', archivo }]);
  });

  it('subiendo: progreso, la foto elegida atenuada y «Cancelar subida» con el foco', () => {
    const entrada = q<HTMLInputElement>('input[type=file]');
    spyOn(entrada, 'click');
    foto('portada.foto').click();
    opciones()[0].click();
    Object.defineProperty(entrada, 'files', { value: [new File(['x'], 'entrenamiento-mollina.jpg', { type: 'image/jpeg' })], configurable: true });
    entrada.dispatchEvent(new Event('change'));
    estado({ 'portada.foto': { estado: 'subiendo', pct: 0, nombre: 'entrenamiento-mollina.jpg' } });
    const figura = q('.portada-foto');
    const panel = figura.querySelector<HTMLElement>('.ed-subida')!;
    expect(panel.getAttribute('role')).toBe('status');
    expect(texto(panel.querySelector('.ed-subida__fila'))).toBe('Subiendo «entrenamiento-mollina.jpg»…0 %');
    expect(figura.classList).toContain('es-subiendo');
    expect(figura.querySelector<HTMLImageElement>('.ed-foto__previa')!.src).toMatch(/^blob:/);
    const cancelar = panel.querySelector<HTMLButtonElement>('button')!;
    expect(texto(cancelar)).toBe('Cancelar subida');
    expect(doc.activeElement).toBe(cancelar);

    estado({ 'portada.foto': { estado: 'subiendo', pct: 62, nombre: 'entrenamiento-mollina.jpg' } });
    expect(figura.querySelector('.ed-subida')).toBe(panel);
    const barra = panel.querySelector<HTMLElement>('[role=progressbar]')!;
    expect(barra.getAttribute('aria-valuenow')).toBe('62');
    expect(barra.getAttribute('aria-label')).toBe('Subida de la foto');
    expect(barra.style.getPropertyValue('--p')).toBe('62%');
    expect(texto(panel.querySelector('[data-pct]'))).toBe('62 %');

    cancelar.click();
    expect(mensajes('foto').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'foto', clave: 'portada.foto', accion: 'cancelar-subida' });
    estado();
    expect(figura.querySelector('.ed-subida')).toBeNull();
    expect(figura.querySelector('.ed-foto__previa')).toBeNull();
    expect(figura.classList).not.toContain('es-subiendo');
    expect(doc.activeElement).toBe(foto('portada.foto'));
  });

  it('fallo: «Demasiado grande» con «La foto de antes sigue puesta.», «Elegir otra foto» y «Cerrar»', () => {
    estado({ 'portada.foto': { estado: 'error', titulo: 'Demasiado grande', msg: '«exhibicion-navidad.jpg» pesa 64 MB. Usa una foto de menos de 50 MB.' } });
    const fallo = q('.portada-foto .ed-fallo');
    expect(fallo.getAttribute('role')).toBe('alert');
    expect(Array.from(fallo.querySelectorAll('p')).map(texto)).toEqual([
      'Demasiado grande',
      '«exhibicion-navidad.jpg» pesa 64 MB. Usa una foto de menos de 50 MB.',
      'La foto de antes sigue puesta.',
    ]);
    const [otra, cerrar] = Array.from(fallo.querySelectorAll<HTMLButtonElement>('button'));
    expect(texto(otra)).toBe('Elegir otra foto');
    expect(texto(cerrar)).toBe('Cerrar');
    const abrirSelector = spyOn(q<HTMLInputElement>('input[type=file]'), 'click');
    otra.click();
    expect(abrirSelector).toHaveBeenCalled();
    cerrar.click();
    expect(mensajes('foto').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'foto', clave: 'portada.foto', accion: 'cancelar-subida' });
  });

  it('fallo sin foto de antes: «Formato no admitido» sin la nota', () => {
    estado({ 'presentacion.foto': { estado: 'error', titulo: 'Formato no admitido', msg: '«grupo-estepa.heic»: convierte la foto a JPG o desactiva HEIF en la cámara.' } });
    const fallo = q('.presentacion-foto .ed-fallo');
    expect(texto(fallo.querySelector('.ed-fallo__t'))).toBe('Formato no admitido');
    expect(fallo.querySelector('.ed-fallo__n')).toBeNull();
  });

  it('tras subir: «Foto subida: describe la foto…» (obligatoria, 150, «Sin nombres de alumnos.»)', async () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [...BORRADOR, fila('presentacion.foto', '', 'https://x/n.webp')] });
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'describir', clave: 'presentacion.foto' });
    await esperar();
    expect(flot().getAttribute('role')).toBe('dialog');
    expect(texto(q('.flot h2'))).toBe('Foto subida: describe la foto de la presentación');
    expect(flot().getAttribute('aria-labelledby')).toBe(q('.flot h2').id);
    expect(texto(q('.flot label'))).toBe('Descripciónobligatorio');
    const area = q<HTMLTextAreaElement>('.flot textarea');
    expect(doc.activeElement).toBe(area);
    expect(area.maxLength).toBe(150);
    expect(texto(q('#ed-alt-a'))).toBe('Qué se ve, para quien no puede verla. Sin nombres de alumnos.');
    expect(texto(q('#ed-alt-c'))).toBe('0 / 150');
    area.value = 'Niños saludando';
    area.dispatchEvent(new Event('input', { bubbles: true }));
    expect(mensajes('descripcion').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'descripcion', clave: 'presentacion.foto', texto: 'Niños saludando' });
    expect(texto(q('#ed-alt-c'))).toBe('15 / 150');
    area.value = 'x'.repeat(150);
    area.dispatchEvent(new Event('input', { bubbles: true }));
    expect(texto(q('#ed-alt-c'))).toBe('150 / 150 · máximo');
    expect(q('#ed-alt-c').classList).toContain('es-tope');
    q<HTMLButtonElement>('.flot [data-op=listo]').click();
    expect(flot().hidden).toBeTrue();
    expect(doc.activeElement).toBe(foto('presentacion.foto'));
    // Describir desde el menú: sin «Foto subida:» y con la descripción que lleva.
    foto('presentacion.foto').click();
    expect(texto(opciones()[1].querySelector('small'))).toBe(`«${'x'.repeat(45)}…»`);
    opciones()[1].click();
    expect(texto(q('.flot h2'))).toBe('describe la foto de la presentación');
  });

  it('descartar la foto recién subida cierra «Describir la foto» sin mover el foco', async () => {
    const conFoto = [...BORRADOR, fila('presentacion.foto', '', 'https://x/n.webp')];
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: conFoto });
    estado({}, ['presentacion.foto'], []);
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'describir', clave: 'presentacion.foto' });
    await esperar();
    expect(flot().hidden).toBeFalse();
    const fuera = doc.createElement('button');
    doc.body.appendChild(fuera);
    fuera.focus();
    // «Descartar» en el panel: la foto deja de estar cambiada.
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: BORRADOR });
    estado({}, [], []);
    expect(flot().hidden).toBeTrue();
    expect(doc.activeElement).toBe(fuera);
  });

  it('descartar antes de que se abra «Describir la foto» cancela la espera', async () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [...BORRADOR, fila('presentacion.foto', '', 'https://x/n.webp')] });
    estado({}, ['presentacion.foto'], []);
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'describir', clave: 'presentacion.foto' });
    estado({}, [], []);
    await esperar();
    expect(flot().hidden).toBeTrue();
  });

  it('«Describir la foto» de una foto sin cambiar sigue abierto al llegar el estado', () => {
    estado({}, ['texto_foto.3.foto'], []);
    foto('portada.foto').click();
    opciones()[1].click();
    expect(flot().getAttribute('role')).toBe('dialog');
    estado({}, [], []);
    expect(flot().hidden).toBeFalse();
    expect(texto(q('.flot h2'))).toBe('describe la foto de la portada');
  });

  it('errores: «Describe la foto.» y «Añade la foto de portada.» sobre su foto; ir al dato', async () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'errores', errores: { 'portada.foto.alt': 'Describe la foto.', 'texto_foto.3.titulo': 'Escribe el título.' } });
    const portada = foto('portada.foto');
    expect(portada.classList).toContain('es-error');
    const error = q('.portada-foto .ed-error--foto');
    expect(texto(error)).toBe('Describe la foto.');
    expect(portada.getAttribute('aria-describedby')).toBe(error.id);
    expect(enviados.calls.count()).toBeGreaterThan(0);

    const antes = mensajes('mostrar').length;
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'portada.foto.alt' });
    expect(doc.activeElement).toBe(portada);
    expect(mensajes('mostrar').length).toBe(antes + 1);
    await esperar();
    expect(texto(q('.flot h2'))).toBe('describe la foto de la portada');

    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: BORRADOR.filter((f) => f.clave !== 'portada.foto') });
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'errores', errores: { 'portada.foto': 'Añade la foto de portada.' } });
    expect(texto(q('.portada-foto .ed-error--foto'))).toBe('Añade la foto de portada.');
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'portada.foto' });
    expect(doc.activeElement).toBe(foto('portada.foto'));
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'errores', errores: {} });
    expect(q('.portada-foto').querySelector('.ed-error--foto')).toBeNull();
  });

  it('el puente de la capa no acepta estados de fotos de la galería', () => {
    estado({ 'galeria.1': { estado: 'error', titulo: 'x', msg: 'y' } } as never);
    expect(doc.querySelector('.ed-fallo')).toBeNull();
  });

  describe('panel estrecho (d541): el menú de la foto sale como hoja del panel', () => {
    const estrecho = () => delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'estado-bloques', sinGuardar: [], quitables: [], estrecho: true });
    const idActual = () => mensajes('hoja-menu').at(-1)!['id'] as number;
    const respuesta = (op: string | null, devolverFoco = false, id = idActual()) =>
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-menu', menu: 'foto', id, op, devolverFoco });

    beforeEach(() => {
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [...BORRADOR, fila('texto_foto.3.foto', '', 'https://x/n.webp')] });
      estado({}, ['texto_foto.3.foto'], ['texto_foto.3.foto']);
      estrecho();
    });

    it('pide la hoja con título y opciones (el quitar, en peligro) y no abre el menú flotante', () => {
      foto('texto_foto.3.foto').click();
      expect(flot().hidden).toBeTrue();
      expect(foto('texto_foto.3.foto').getAttribute('aria-expanded')).toBe('true');
      const [m] = mensajes('hoja-menu');
      expect(esMensajeDelEditor(m)).toBeTrue();
      expect(m['menu']).toBe('foto');
      expect(m['titulo']).toBe('Foto de «Nuestra sala»');
      const ops = m['opciones'] as { op: string; texto: string; pie: string; icono: string; peligro: boolean }[];
      expect(ops.map((o) => [o.op, o.texto, o.icono, o.peligro])).toEqual([
        ['subir', 'Cambiar foto', 'foto', false],
        ['describir', 'Describir la foto', 'texto', false],
        ['anterior', 'Volver a la anterior', 'deshacer', false],
        ['quitar', 'Quitar foto', 'papelera', true],
      ]);
      expect(ops[0].pie).toBe('JPG, PNG o WebP, hasta 50 MB');
    });

    it('Cambiar foto, Volver a la anterior y Quitar foto llegan igual que con el menú flotante', () => {
      const entrada = q<HTMLInputElement>('input[type=file]');
      const abrirSelector = spyOn(entrada, 'click');
      foto('texto_foto.3.foto').click();
      respuesta('subir');
      expect(abrirSelector).toHaveBeenCalled();
      expect(foto('texto_foto.3.foto').getAttribute('aria-expanded')).toBe('false');
      foto('texto_foto.3.foto').click();
      respuesta('anterior');
      expect(mensajes('foto').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'foto', clave: 'texto_foto.3.foto', accion: 'anterior' });
      foto('texto_foto.3.foto').click();
      respuesta('quitar');
      expect(mensajes('foto').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'foto', clave: 'texto_foto.3.foto', accion: 'quitar' });
    });

    it('Describir la foto abre su descripción', () => {
      foto('texto_foto.3.foto').click();
      respuesta('describir');
      expect(flot().hidden).toBeFalse();
      expect(texto(q('.flot__titulo'))).toBe('describe la foto de «Nuestra sala»');
    });

    it('cerrada sin elegir: el foco vuelve a la foto solo si el panel lo pide', () => {
      foto('portada.foto').click();
      respuesta(null, false);
      expect(foto('portada.foto').getAttribute('aria-expanded')).toBe('false');
      expect(doc.activeElement).not.toBe(foto('portada.foto'));
      foto('portada.foto').click();
      respuesta(null, true);
      expect(doc.activeElement).toBe(foto('portada.foto'));
      expect(mensajes('foto')).toEqual([]);
    });

    it('una opción que la hoja no ofrecía (la portada no se quita) no hace nada', () => {
      foto('portada.foto').click();
      respuesta('quitar');
      expect(mensajes('foto')).toEqual([]);
    });

    it('B1: una respuesta con el id de un menú anterior no cierra ni ejecuta nada sobre el nuevo; la del id actual sí', () => {
      foto('texto_foto.3.foto').click();
      const viejo = idActual();
      foto('portada.foto').click();
      const nuevo = idActual();
      expect(nuevo).not.toBe(viejo);
      respuesta(null, false, viejo);
      respuesta('describir', false, viejo);
      expect(foto('portada.foto').getAttribute('aria-expanded')).toBe('true');
      expect(flot().hidden).toBeTrue();
      respuesta('describir', false, nuevo);
      expect(flot().hidden).toBeFalse();
      expect(texto(q('.flot__titulo'))).toBe('describe la foto de la portada');
    });

    it('B2: el segundo toque en la misma foto cierra el menú y avisa con menu e id', () => {
      foto('portada.foto').click();
      const id = idActual();
      foto('portada.foto').click();
      expect(foto('portada.foto').getAttribute('aria-expanded')).toBe('false');
      expect(mensajes('cerrar-hoja-menu')).toEqual([{ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-menu', menu: 'foto', id }]);
      expect(esMensajeDelEditor(mensajes('cerrar-hoja-menu')[0])).toBeTrue();
    });

    it('B2: abrir el menú de otra foto cierra el anterior avisando con el id viejo', () => {
      foto('texto_foto.3.foto').click();
      const viejo = idActual();
      foto('portada.foto').click();
      expect(foto('texto_foto.3.foto').getAttribute('aria-expanded')).toBe('false');
      expect(mensajes('cerrar-hoja-menu').map((c) => c['id'])).toEqual([viejo]);
    });

    it('B2: al cerrar por la respuesta del panel (sin elegir o eligiendo) no se emite cerrar-hoja-menu', () => {
      foto('portada.foto').click();
      respuesta(null, true);
      foto('portada.foto').click();
      respuesta('describir');
      foto('texto_foto.3.foto').click();
      respuesta('quitar');
      expect(mensajes('cerrar-hoja-menu')).toEqual([]);
    });

    it('con el panel ancho sigue el menú flotante y no hay hoja', () => {
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'estado-bloques', sinGuardar: [], quitables: [], estrecho: false });
      foto('portada.foto').click();
      expect(flot().hidden).toBeFalse();
      expect(mensajes('hoja-menu')).toEqual([]);
    });
  });
});
