import { activarCapaEditor } from './capa-editor';
import { BloqueBorrador, CANAL_EDITOR_WEB } from './puente-editor';

/** Lo justo de Inicio de «La línea comarcal» para los textos en el sitio (W12d). */
const WEB = `
<div class="lc">
  <header class="cabecera"><div class="envoltura">
    <nav class="nav"><ul class="nav-lista"><li><a href="/?vista-previa=linea-comarcal#linea">Horarios</a></li></ul></nav>
    <a class="boton cabecera-cta" href="/preinscripcion">Preinscribirme</a>
  </div></header>
  <main id="principal" tabindex="-1">
    <section class="portada" id="portada">
      <div class="envoltura portada-rejilla"><div class="portada-texto">
        <h1 class="titular" id="portada-titulo">Taekwondo en seis pueblos</h1>
        <p class="entrada">Grupos por edad desde los 4 años.</p>
      </div></div>
    </section>
    <section class="presentacion" id="presentacion">
      <figure class="foto presentacion-foto"><div class="presentacion-velo"><div class="envoltura">
        <p class="rotulo">02 · El club</p><h2 class="titulo-seccion" id="presentacion-titulo">Clases cerca de casa</h2>
      </div></div></figure>
      <div class="presentacion-cuerpo"><div class="envoltura"><div class="cuerpo"><p>Uno.</p><p>Dos.</p></div></div></div>
    </section>
    <section class="seccion" id="fotos-sedes"><div class="envoltura"><ol class="fs-lista">
      <li class="fs-parada"><a class="enlace-flecha" href="/sedes/herrera">Ver la sede<span class="oculto">: Herrera</span></a></li>
      <li class="fs-parada"><a class="enlace-flecha" href="/sedes/estepa">Ver la sede<span class="oculto">: Estepa</span></a></li>
    </ol></div></section>
    <section class="seccion" id="texto-foto-3"><div class="envoltura tf"><div class="tf-texto">
      <div class="cabeza-seccion"><p class="rotulo">04</p><h2 class="titulo-seccion" id="texto-foto-3-titulo">Nuestra sala</h2></div>
      <div class="cuerpo"><p>Tatami nuevo.</p></div>
    </div></div></section>
    <section class="tarifas" id="tarifas"><div class="envoltura tarifas-rejilla">
      <h2 class="rotulo" id="tarifas-titulo">07 · Tarifas</h2>
      <div class="tarifa-lado"><ul class="tarifa-incluye"><li>Mensualidad por alumno</li><li>Licencia aparte</li><li>Punto tres</li></ul></div>
    </div></section>
  </main>
  <footer class="pie"><div class="envoltura"><div class="pie-fila"><p>© 2026</p></div></div></footer>
</div>`;

const fila = (clave: string, texto: string | null): BloqueBorrador => ({ clave, texto, imagenUrl: null, orden: 0, oculto: false });

describe('textos en el sitio (W12d, capa de edición)', () => {
  let marco: HTMLIFrameElement;
  let ventana: Window;
  let doc: Document;
  let enviados: jasmine.Spy;
  let borradores: BloqueBorrador[][];
  let desactivar: () => void;

  beforeEach(() => {
    marco = document.createElement('iframe');
    document.body.appendChild(marco);
    ventana = marco.contentWindow!;
    doc = marco.contentDocument!;
    doc.body.innerHTML = WEB;
    enviados = spyOn(window, 'postMessage');
    borradores = [];
    desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: (b) => borradores.push(b) });
  });

  afterEach(() => {
    desactivar();
    marco.remove();
  });

  const q = <T extends HTMLElement = HTMLElement>(selector: string) => doc.querySelector<T>(selector)!;
  const mensajes = (tipo: string) =>
    enviados.calls
      .allArgs()
      .map(([m]) => m as { tipo: string })
      .filter((m) => m.tipo === tipo);
  /** Un mensaje del panel a la capa. */
  const delPanel = (data: unknown) => ventana.dispatchEvent(new MessageEvent('message', { data, origin: ventana.origin, source: window }));
  const tecla = (el: Element, key: string) => {
    const e = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
    el.dispatchEvent(e);
    return e;
  };
  const campo = () => doc.querySelector<HTMLElement>('.ed-campo__texto');
  /** Escribe como el navegador: cambia el texto y lanza `input`. */
  const escribir = (texto: string) => {
    campo()!.textContent = texto;
    campo()!.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText' }));
  };

  it('marca cada texto como botón «Editar …» en el tabulador; la misma clave repetida, como repetida', () => {
    const titular = q('.portada .titular');
    expect(titular.classList).toContain('ed-texto');
    expect(titular.dataset['campo']).toBe('portada.titular');
    expect(titular.getAttribute('role')).toBe('button');
    expect(titular.getAttribute('aria-label')).toBe('Editar titular (obligatorio)');
    expect(titular.tabIndex).toBe(0);
    expect(q('.cabecera-cta').tabIndex).toBe(0);
    expect(q('.cabecera-cta').getAttribute('aria-label')).toBe('Editar botón (obligatorio)');
    expect(q('.portada .entrada').getAttribute('aria-label')).toBe('Editar frase de debajo');
    const verSede = Array.from(doc.querySelectorAll<HTMLElement>('.fs-parada .enlace-flecha'));
    expect(verSede.map((a) => a.dataset['campo'])).toEqual(['sedes.ver_sede', 'sedes.ver_sede']);
    expect(verSede.map((a) => a.dataset['rep'] ?? '')).toEqual(['', '1']);
    expect(Array.from(doc.querySelectorAll<HTMLElement>('.tarifa-incluye > li')).map((li) => li.dataset['campo'])).toEqual([
      'tarifas.punto1',
      'tarifas.punto2',
      undefined,
    ]);
  });

  it('manda al panel los textos por defecto que dependen de los datos (no los fijos del tema)', () => {
    const [defectos] = mensajes('defectos') as unknown as { textos: Record<string, string> }[];
    expect(defectos.textos).toEqual({
      'portada.titular': 'Taekwondo en seis pueblos',
      'portada.frase': 'Grupos por edad desde los 4 años.',
      'presentacion.texto': 'Uno.\n\nDos.',
    });
  });

  it('texto con foto: nunca toma de la página su texto por defecto (el rótulo sin fila pinta solo el número)', () => {
    const textos = (mensajes('defectos') as unknown as { textos: Record<string, string> }[]).map((m) => m.textos);
    expect(textos.flatMap((t) => Object.keys(t)).filter((k) => k.startsWith('texto_foto.'))).toEqual([]);
    tecla(q('#texto-foto-3 .rotulo'), 'Enter');
    expect(campo()!.textContent).toBe('');
    escribir('Nuestra sala');
    expect(mensajes('texto').at(-1)).toEqual(jasmine.objectContaining({ clave: 'texto_foto.3.rotulo', texto: 'Nuestra sala' }));
  });

  it('el valor es el texto real, sin el `text-transform` del tema (el titular va en mayúsculas en la web)', () => {
    tecla(q('.portada .titular'), 'Enter');
    campo()!.style.textTransform = 'uppercase';
    escribir('Taekwondo en seis pueblos');
    expect(campo()!.innerText).toBe('TAEKWONDO EN SEIS PUEBLOS');
    expect(mensajes('texto').at(-1)).toEqual(jasmine.objectContaining({ clave: 'portada.titular', texto: 'Taekwondo en seis pueblos' }));
    expect(borradores.at(-1)).toContain(fila('portada.titular', 'Taekwondo en seis pueblos'));
  });

  it('varias líneas: los saltos (`<br>` o bloques del navegador) llegan como `\\n`', () => {
    tecla(q('#presentacion .cuerpo'), 'Enter');
    campo()!.style.textTransform = 'uppercase';
    campo()!.innerHTML = 'Uno.<br>Dos.';
    campo()!.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertLineBreak' }));
    expect(mensajes('texto').at(-1)).toEqual(jasmine.objectContaining({ clave: 'presentacion.texto', texto: 'Uno.\nDos.' }));
    campo()!.innerHTML = 'Uno.<div>Dos.</div><div><br></div><div>Tres.</div>';
    campo()!.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertParagraph' }));
    expect(mensajes('texto').at(-1)).toEqual(jasmine.objectContaining({ texto: 'Uno.\nDos.\n\nTres.' }));
  });

  it('el texto por defecto leído de la página no sale en mayúsculas con `text-transform`', () => {
    desactivar();
    doc.body.innerHTML = WEB.replace('<h1 class="titular"', '<h1 style="text-transform: uppercase" class="titular"');
    enviados.calls.reset();
    desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: (b) => borradores.push(b) });
    const [defectos] = mensajes('defectos') as unknown as { textos: Record<string, string> }[];
    expect(defectos.textos['portada.titular']).toBe('Taekwondo en seis pueblos');
    tecla(q('.portada .titular'), 'Enter');
    expect(campo()!.textContent).toBe('Taekwondo en seis pueblos');
  });

  it('el «Escribe aquí…» de un vacío no entra nunca como texto por defecto', () => {
    desactivar();
    doc.body.innerHTML = WEB.replace('Grupos por edad desde los 4 años.', 'Escribe aquí una frase: para quién son las clases y qué hacer');
    enviados.calls.reset();
    desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: (b) => borradores.push(b) });
    const textos = (mensajes('defectos') as unknown as { textos: Record<string, string> }[]).map((m) => m.textos);
    expect(textos.flatMap((t) => Object.keys(t))).not.toContain('portada.frase');
    tecla(q('.portada .entrada'), 'Enter');
    expect(campo()!.textContent).toBe('');
  });

  it('Intro entra a editar en una copia con el texto (sin el número de la sección); Escape sale y devuelve el foco', () => {
    const rotulo = q('#presentacion .rotulo');
    rotulo.focus();
    expect(tecla(rotulo, 'Enter').defaultPrevented).toBeTrue();
    const copia = doc.querySelector<HTMLElement>('.ed-campo')!;
    expect(copia.tagName).toBe('P');
    expect(copia.classList).toContain('rotulo');
    expect(copia.textContent).toBe('02 · El club');
    expect(campo()!.textContent).toBe('El club');
    expect(campo()!.getAttribute('role')).toBe('textbox');
    expect(campo()!.getAttribute('aria-label')).toBe('Rótulo');
    expect(['plaintext-only', 'true']).toContain(campo()!.contentEditable);
    expect(rotulo.classList).toContain('ed-escondido');
    expect(doc.activeElement).toBe(campo());
    expect(q('.ed-chip').hidden).toBeFalse();
    expect(q('.ed-chip').textContent).toBe('Rótuloopcional7 / 24');

    tecla(campo()!, 'Escape');
    expect(doc.querySelector('.ed-campo')).toBeNull();
    expect(rotulo.classList).not.toContain('ed-escondido');
    expect(doc.activeElement).toBe(rotulo);
    expect(q('.ed-chip').hidden).toBeTrue();
  });

  it('F2 también entra; Intro termina en los de una línea y avisa «Texto cambiado.»', () => {
    const titular = q('.portada .titular');
    tecla(titular, 'F2');
    expect(campo()).not.toBeNull();
    escribir('Taekwondo en la comarca');
    tecla(campo()!, 'Enter');
    expect(campo()).toBeNull();
    expect(doc.activeElement).toBe(titular);
  });

  it('cada cambio va al panel y al tema (los demás sitios de la clave los repinta el tema)', () => {
    tecla(q('.fs-parada .enlace-flecha'), 'Enter');
    escribir('Ver su sede');
    expect(mensajes('texto')).toEqual([jasmine.objectContaining({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave: 'sedes.ver_sede', texto: 'Ver su sede' })]);
    expect(borradores.at(-1)).toContain(fila('sedes.ver_sede', 'Ver su sede'));
  });

  it('un texto vacío: al tema va su «Escribe aquí…» y la capa lo marca vacío', () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [fila('portada.frase', ''), fila('texto_foto.2', null)] });
    const tema = borradores.at(-1)!;
    expect(tema).toContain(fila('portada.frase', 'Escribe aquí una frase: para quién son las clases y qué hacer'));
    // Un texto con foto recibe sus tres textos para que se vea entero en el editor.
    expect(tema).toContain(fila('texto_foto.2.titulo', 'Escribe aquí el título'));
    expect(tema).toContain(fila('texto_foto.2.rotulo', 'Rótulo corto (opcional)'));
    expect(tema).toContain(fila('texto_foto.2.texto', 'Escribe aquí el texto'));
  });

  it('el máximo: no se escribe más y el chip dice «· máximo»', () => {
    tecla(q('.cabecera-cta'), 'Enter');
    escribir('x'.repeat(24));
    const e = new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertText', data: 'y' });
    campo()!.dispatchEvent(e);
    expect(e.defaultPrevented).toBeTrue();
    expect(q('.ed-chip').textContent).toContain('24 / 24 · máximo');
    expect(q('.ed-chip').classList).toContain('es-tope');
  });

  it('un salto de línea no entra en los de una línea; sí en los de varias', () => {
    tecla(q('.cabecera-cta'), 'Enter');
    const una = new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertParagraph' });
    campo()!.dispatchEvent(una);
    expect(una.defaultPrevented).toBeTrue();
    tecla(campo()!, 'Escape');
    tecla(q('#presentacion .cuerpo'), 'Enter');
    expect(campo()!.classList).toContain('ed-multi');
    expect(campo()!.getAttribute('aria-multiline')).toBe('true');
    expect(campo()!.textContent).toBe('Uno.\n\nDos.');
    const varias = new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertParagraph' });
    campo()!.dispatchEvent(varias);
    expect(varias.defaultPrevented).toBeFalse();
  });

  it('pegar: texto plano, en una línea y recortado al sitio que queda', () => {
    tecla(q('.cabecera-cta'), 'Enter');
    escribir('x'.repeat(20));
    const insertar = spyOn(doc, 'execCommand').and.returnValue(true);
    const datos = new DataTransfer();
    datos.setData('text/plain', 'ab\ncdef');
    const e = new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: datos });
    campo()!.dispatchEvent(e);
    expect(e.defaultPrevented).toBeTrue();
    expect(insertar).toHaveBeenCalledOnceWith('insertText', false, 'ab c');
    expect(q('.ed-chip').classList).toContain('es-tope');
  });

  it('errores del panel: contorno en todos los sitios, el mensaje solo tras el primero', () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'errores', errores: { 'sedes.ver_sede': 'Escribe el texto.' } });
    const [primero, segundo] = Array.from(doc.querySelectorAll<HTMLElement>('.fs-parada .enlace-flecha'));
    expect(primero.classList).toContain('es-error');
    expect(segundo.classList).toContain('es-error');
    expect(primero.getAttribute('aria-invalid')).toBe('true');
    const mensaje = primero.nextElementSibling as HTMLElement;
    expect(mensaje.classList).toContain('ed-error');
    expect(mensaje.textContent).toBe('Escribe el texto.');
    expect(primero.getAttribute('aria-describedby')).toBe(mensaje.id);
    expect(segundo.nextElementSibling?.classList.contains('ed-error') ?? false).toBeFalse();
    expect(doc.querySelectorAll('.ed-error').length).toBe(1);

    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'errores', errores: {} });
    expect(doc.querySelectorAll('.ed-error').length).toBe(0);
    expect(primero.classList).not.toContain('es-error');
  });

  it('«ir-texto»: el foco al primer sitio del texto y avisa al panel de dónde está', () => {
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'sedes.ver_sede' });
    expect(doc.activeElement).toBe(q('.fs-parada .enlace-flecha'));
    expect(mensajes('mostrar').length).toBe(1);
  });

  it('salir del foco termina la edición; un borrador nuevo del panel también', () => {
    tecla(q('.portada .titular'), 'Enter');
    campo()!.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));
    expect(campo()).toBeNull();
    tecla(q('.portada .titular'), 'Enter');
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [] });
    expect(campo()).toBeNull();
    expect(q('.portada .titular').classList).not.toContain('ed-escondido');
  });
});
