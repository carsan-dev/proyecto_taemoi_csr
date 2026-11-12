import { activarCapaEditor } from './capa-editor';
import { BloqueBorrador, CANAL_EDITOR_WEB } from './puente-editor';

const marco = (pagina: string) => `
<div class="lc">
  <header class="cabecera"><div class="envoltura">
    <nav class="nav"><ul class="nav-lista"><li><a href="/?vista-previa=linea-comarcal#linea">Horarios</a></li></ul></nav>
    <a class="boton cabecera-cta" href="/preinscripcion">Preinscribirme</a>
    <button class="menu-boton" type="button"><svg aria-hidden="true"></svg><span class="menu-texto">Menú</span></button>
  </div></header>
  <main id="principal" tabindex="-1">${pagina}</main>
  <footer class="pie"><div class="envoltura"><div class="pie-fila"><p>© 2026</p></div></div></footer>
</div>`;

/** Lo justo de la página de una sede del tema (`sede.component.html`). */
const SEDE = marco(`<div class="p-sede">
  <section class="sede-portada"><div class="envoltura sede-rejilla"><div class="sede-texto">
    <nav class="tramo">
      <a class="tramo-parada" href="/sedes/casariche"><span class="tramo-circulo"></span><span class="tramo-sentido"><svg aria-hidden="true"></svg>Anterior</span><span class="tramo-nombre">Casariche</span></a>
      <p class="tramo-parada tramo-parada--actual"><span class="tramo-circulo"></span><span class="tramo-nombre">Parada 2 de 6</span></p>
      <a class="tramo-parada" href="/sedes/roda"><span class="tramo-circulo"></span><span class="tramo-sentido">Siguiente<svg aria-hidden="true"></svg></span><span class="tramo-nombre">Roda</span></a>
    </nav>
    <h1 class="sede-titular">Herrera</h1>
    <div class="acciones"><a class="boton" href="/preinscripcion" aria-label="Preinscribirme en Herrera">Preinscribirme</a><a class="enlace-flecha" href="#horarios">Ver grupos y horas</a></div>
  </div></div></section>
  <section class="seccion seccion--anden" id="horarios"><div class="envoltura">
    <div class="cabeza-seccion"><p class="rotulo">Horarios · curso 2026-2027</p><h2 class="titulo-seccion" id="horarios-titulo">Grupos y horas</h2></div>
    <div class="tablero">
      <p class="proxima"><span class="proxima-rotulo">Próxima clase</span><span class="proxima-cuando">Lunes <span class="hora">17:00</span></span></p>
      <div class="salidas-cabeza" aria-hidden="true"><span>Hora</span><span>Grupo</span><span>Días y plazas</span><span></span></div>
      <ol class="salidas"><li class="salida">
        <p class="salida-hora"><span class="hora-ini">17:00</span></p>
        <div class="salida-detalle"><p class="plazas plazas--lleno"><svg aria-hidden="true"></svg><span class="cifras">Lleno</span></p></div>
        <div class="salida-accion"><a class="boton boton--secundario" href="/preinscripcion">Apuntarme a la lista de espera</a></div>
      </li></ol>
      <ul class="leyenda"><li><svg aria-hidden="true"></svg>Quedan plazas</li><li><svg aria-hidden="true"></svg>Lleno: puedes apuntarte a la lista de espera</li><li><svg aria-hidden="true"></svg>Ya hay lista de espera</li></ul>
    </div>
  </div></section>
  <section class="seccion" id="llegar"><div class="envoltura llegar"><div class="llegar-texto">
    <div class="cabeza-seccion"><p class="rotulo">Dirección y contacto</p><h2 class="titulo-seccion" id="llegar-titulo">Cómo llegar</h2></div>
    <dl class="ficha"><div class="ficha-fila"><dt>Lugar</dt><dd data-dato="lugar">Pabellón</dd></div><div class="ficha-fila"><dt>Teléfono</dt><dd data-dato="telefono"><a class="cifras" href="tel:600">600</a></dd></div></dl>
  </div></div></section>
  <section class="seccion" id="sedes"><div class="envoltura">
    <div class="cabeza-seccion"><p class="rotulo">La línea</p><h2 class="titulo-seccion" id="sedes-titulo">Todas las sedes</h2></div>
    <ol class="ruta"><li class="ruta-sede ruta-sede--actual"><span class="ruta-nombre">Herrera</span><span class="ruta-aqui">Estás aquí</span></li></ol>
  </div></section>
</div>`);

/** La página de tarifas (`tarifas.component.html`) con una tarifa familiar, una de hermanos y «Aparte». */
const TARIFAS = marco(`<div class="p-tarifas">
  <section class="tarifas" id="tarifas"><div class="envoltura tarifas-rejilla">
    <h1 class="rotulo" id="tarifas-titulo">Tarifas</h1>
    <p class="tarifa-cifra"><span class="tarifa-importe">25 €</span><span class="tarifa-mes">al mes</span></p>
    <div class="tarifa-lado"><p class="tarifa-igual">Una cuota al mes, igual en las seis sedes.</p>
      <ul class="tarifa-incluye"><li>Mensualidad por alumno</li><li>También tarifa familiar</li><li>Licencia y exámenes, aparte</li></ul>
      <div class="tarifas-acciones"><a class="boton boton--blanco" href="/preinscripcion">Preinscribirme</a><a class="enlace-flecha" href="/#linea">Ver horarios</a></div>
    </div>
  </div></section>
  <section class="seccion" id="cuadro"><div class="envoltura cuadro-rejilla">
    <div class="cabeza-seccion"><p class="rotulo">Cuadro de tarifas</p><h2 class="titulo-seccion" id="cuadro-titulo">Cuánto se paga</h2></div>
    <div class="cuadro-caja"><table class="cuadro">
      <caption><span class="rotulo">Mensualidades del curso 2026-2027</span></caption>
      <thead><tr><th scope="col">Tarifa</th><th scope="col">Quién</th><th scope="col">Importe</th></tr></thead>
      <tbody><tr><th scope="row">General</th><td class="quien" data-quien="alumno">Cada alumno</td><td class="importe cifras">25 €<small>al mes</small></td></tr></tbody>
      <tbody><tr><th scope="row">Hermanos</th><td class="quien" data-quien="hermanos">Desde el segundo hermano</td><td class="importe cifras">20 €<small>al mes</small></td></tr></tbody>
      <tbody class="grupo-tarifa"><tr><th scope="rowgroup" rowspan="2">Familiar</th><td class="quien" data-quien="adulto">Cada adulto</td><td class="importe cifras">30 €<small>al mes</small></td></tr>
        <tr><td class="quien" data-quien="menor">Cada menor</td><td class="importe cifras">15 €<small>al mes</small></td></tr></tbody>
    </table><p class="cuadro-pie">Se paga una mensualidad por alumno, la misma en todos los grupos.</p></div>
  </div></section>
  <section class="seccion" id="aparte"><div class="envoltura aparte-rejilla">
    <div class="cabeza-seccion"><p class="rotulo">Aparte</p><h2 class="titulo-seccion" id="aparte-titulo">Licencia y exámenes</h2></div>
    <div class="aparte-caja"><ul class="aparte">
      <li><span class="aparte-concepto">Licencia de taekwondo</span><span class="aparte-precio" data-dato="licencia">40 €</span></li>
      <li><span class="aparte-concepto">Derecho de examen</span><span class="aparte-precio" data-dato="examen">Según grado, desde 30 €</span></li>
    </ul></div>
  </div></section>
</div>`);

/** Legales (`legales.component.html`): los textos con clave y los fijos del tema. */
const LEGALES = marco(`<div class="p-legales">
  <div class="envoltura legal-cabeza"><h1 class="legal-titular">Información legal</h1><p class="legal-entrada">Quién está detrás.</p>
    <p class="actualizado">Última actualización: <time datetime="2026-11-01">1 de noviembre de 2026</time></p></div>
  <div class="envoltura legal-rejilla">
    <nav class="indice"><h2 class="rotulo indice-titulo" id="indice-titulo">En esta página</h2><ol class="indice-lista"><li><a href="/legales#aviso-legal">Aviso legal</a></li></ol></nav>
    <div class="legal-texto">
      <section class="legal-seccion" id="aviso-legal"><h2 class="legal-h2" id="t-aviso">Aviso legal</h2><p>En cumplimiento.</p>
        <dl class="ficha"><div class="ficha-fila"><dt>Titular</dt><dd data-dato="titular">Club</dd></div><div class="ficha-fila"><dt>CIF</dt><dd data-dato="cif">G1</dd></div>
          <div class="ficha-fila"><dt>Domicilio</dt><dd data-dato="domicilio">C/ Uno</dd></div><div class="ficha-fila"><dt>Correo</dt><dd data-dato="correo"><a href="mailto:a@b">a@b</a></dd></div></dl>
        <h3 class="legal-h3">Para qué sirve esta web</h3><p>Informa.</p>
        <h3 class="legal-h3">Propiedad intelectual</h3><p>Son de Club o de sus autores.</p>
        <h3 class="legal-h3">Responsabilidad</h3><p>Texto fijo largo.</p>
        <h3 class="legal-h3">Legislación</h3><p>Española.</p>
      </section>
      <section class="legal-seccion" id="privacidad"><h2 class="legal-h2" id="t-privacidad">Privacidad</h2><p>Tratamos tus datos.</p>
        <h3 class="legal-h3">Quién es el responsable</h3><p>Club.</p><h3 class="legal-h3">Tus derechos</h3><p>Puedes pedir.</p></section>
      <section class="legal-seccion" id="cookies"><h2 class="legal-h2" id="t-cookies">Cookies</h2><p>Solo técnicas.</p></section>
      <section class="legal-seccion" id="normas"><h2 class="legal-h2" id="t-normas">Normas del club</h2><p>Son las normas.</p></section>
    </div>
  </div>
</div>`);

/** Preinscripción, paso 2 sin grupo para su edad (caso «sin-grupo»). */
const PRE_SIN_GRUPO = marco(`<div class="p-preinscripcion"><div class="flujo"><div class="envoltura flujo-rejilla"><div class="flujo-principal">
  <a class="atras" href="/">Atrás</a>
  <div class="progreso"><p class="rotulo progreso-texto">Paso 2 de 3</p><ol class="progreso-linea" aria-hidden="true"><span class="progreso-hecho"></span>
    <li class="progreso-paso"><span class="progreso-circulo"></span>Pueblo</li><li class="progreso-paso"><span class="progreso-circulo"></span>Grupo</li><li class="progreso-paso"><span class="progreso-circulo"></span>Datos</li></ol></div>
  <form><h1 class="pregunta-titulo">¿Qué grupo?</h1><p class="contexto"><span>En <strong>Herrera</strong></span><a href="#">Cambiar de pueblo</a></p>
    <div id="campo-grupos"><div class="vacio"><h2>En Herrera no hay grupo para 15 años</h2>
      <p>Estas sedes sí tienen grupo para su edad.</p><ul class="otras"><li class="otra"><a href="#">Estepa</a></li></ul>
      <p class="vacio-contacto">¿Prefieres Herrera o tienes dudas? Llama a la sede al <a class="cifras" href="tel:600">600 000 000</a> y te orientamos.</p>
      <p class="vacio-contacto"><a href="#fecha-pista">Revisar la fecha de nacimiento</a></p></div></div></form>
</div></div></div></div>`);

/** Preinscripción, paso 4 en la lista de espera. */
const PRE_FIN_ESPERA = marco(`<div class="p-preinscripcion"><div class="flujo flujo--fin"><div class="envoltura flujo-rejilla"><div class="flujo-principal">
  <section class="banda"><h1 class="pregunta-titulo" id="fin-titulo">Estás en la lista de espera</h1><p class="banda-ref">Referencia<strong>TSS-1</strong></p>
    <p class="banda-estado">Todavía no tienes plaza. Estás en la posición 3 de la lista de espera de Peques en Herrera, para el lunes.</p></section>
  <div class="fin-rejilla"><section class="fin-pasos"><h2 id="fin-ahora">Qué pasa ahora</h2><ol class="ruta">
    <li class="ruta-sede"><span class="ruta-nombre">Te llega un correo</span><span class="ruta-dir">Con la solicitud y tu posición.</span></li>
    <li class="ruta-sede"><span class="ruta-nombre">Esperas tu turno</span><span class="ruta-dir">Fijo.</span></li></ol></section>
    <section class="fin-resumen"><h2 id="fin-res">Resumen</h2><dl class="ficha"><div class="ficha-fila"><dt>Estado</dt><dd>Lista de espera, sin plaza</dd></div></dl></section></div>
</div></div></div></div>`);

const fila = (clave: string, texto: string | null): BloqueBorrador => ({ clave, texto, imagenUrl: null, orden: 0, oculto: false });

describe('textos de las otras seis páginas (W12d-bis, capa de edición)', () => {
  let iframe: HTMLIFrameElement;
  let ventana: Window;
  let doc: Document;
  let enviados: jasmine.Spy;
  let desactivar: () => void;

  function montar(html: string): void {
    iframe = document.createElement('iframe');
    document.body.appendChild(iframe);
    ventana = iframe.contentWindow!;
    doc = iframe.contentDocument!;
    doc.body.innerHTML = html;
    enviados = spyOn(window, 'postMessage');
    desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: () => undefined });
  }

  afterEach(() => {
    desactivar();
    iframe.remove();
  });

  const q = <T extends HTMLElement = HTMLElement>(selector: string) => doc.querySelector<T>(selector)!;
  const campos = () => Array.from(doc.querySelectorAll<HTMLElement>('.ed-texto')).map((el) => el.dataset['campo']);
  const delPanel = (data: unknown) => ventana.dispatchEvent(new MessageEvent('message', { data, origin: ventana.origin, source: window }));
  const mensajes = (tipo: string) =>
    enviados.calls
      .allArgs()
      .map(([m]) => m as { tipo: string } & Record<string, unknown>)
      .filter((m) => m.tipo === tipo);
  const tecla = (el: Element, key: string) => {
    const e = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
    el.dispatchEvent(e);
    return e;
  };
  const campo = () => doc.querySelector<HTMLElement>('.ed-campo__texto')!;
  const ficha = () => campo().querySelector<HTMLElement>('.ed-ficha')!;
  function cursor(nodo: Node, pos: number): void {
    const r = doc.createRange();
    r.setStart(nodo, pos);
    r.collapse(true);
    ventana.getSelection()!.removeAllRanges();
    ventana.getSelection()!.addRange(r);
  }
  function elegir(desde: [Node, number], hasta: [Node, number]): void {
    const r = doc.createRange();
    r.setStart(...desde);
    r.setEnd(...hasta);
    ventana.getSelection()!.removeAllRanges();
    ventana.getSelection()!.addRange(r);
  }
  const antesDeEscribir = (inputType: string, data: string | null = null) => {
    const e = new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType, data });
    campo().dispatchEvent(e);
    return e;
  };
  /** El texto con tokens, como lo lee la capa (texto suelto y el token de cada ficha). */
  const leido = () =>
    Array.from(campo().childNodes)
      .map((n) => ((n as HTMLElement).dataset?.['token'] ?? n.textContent))
      .join('');

  describe('Una sede', () => {
    beforeEach(() => montar(SEDE));

    it('marca sus textos con el chip del boceto; el aria-label del botón no se ofrece (decisión 12/11)', () => {
      expect(campos()).toEqual([
        'menu.horarios',
        'cabecera.boton',
        'cabecera.menu',
        'sede.anterior',
        'sede.siguiente',
        'sede.ver_grupos',
        'sede.rotulo_horarios',
        'sede.titulo_horarios',
        'sedes.proxima_rotulo',
        'sede.cabeza_hora',
        'sede.cabeza_grupo',
        'sede.cabeza_dias',
        'horarios.lleno',
        'horarios.boton_espera',
        'sede.leyenda_quedan',
        'sede.leyenda_lleno',
        'sede.leyenda_espera',
        'sede.rotulo_llegar',
        'sede.titulo_llegar',
        'sede.ficha_lugar',
        'sede.ficha_telefono',
        'sede.rotulo_ruta',
        'sede.titulo_ruta',
        'sede.estas_aqui',
      ]);
      expect(q('.sede-portada .acciones .boton').classList).not.toContain('ed-texto');
      expect(q('.tramo-sentido').getAttribute('aria-label')).toBe('Editar rótulo de la parada anterior (obligatorio)');
      // Las cabeceras de la tabla ya no se esconden a los lectores mientras se editan.
      expect(q('.salidas-cabeza').hasAttribute('aria-hidden')).toBeFalse();
      desactivar();
      expect(q('.salidas-cabeza').getAttribute('aria-hidden')).toBe('true');
      desactivar = () => undefined;
    });

    it('la ficha del curso: se pinta con el dato de la página, el lector oye su nombre y al leer vuelve el token', () => {
      tecla(q('#horarios .rotulo'), 'Enter');
      expect(campo().textContent).toContain('Horarios · curso ');
      expect(ficha().contentEditable).toBe('false');
      expect(ficha().dataset['token']).toBe('{x}');
      expect(ficha().querySelector(':scope > span[aria-hidden="true"]')!.textContent).toBe('2026-2027');
      expect(ficha().querySelector('.ed-oculto')!.textContent).toBe('2026-2027 (Curso: no se edita aquí)');
      // Más visible que el boceto (decisión del propietario): un candado delante del dato, sin texto ni lector.
      const candado = ficha().firstElementChild!;
      expect(candado.localName).toBe('svg');
      expect(candado.getAttribute('aria-hidden')).toBe('true');
      expect(candado.textContent).toBe('');
      expect(leido()).toBe('Horarios · curso {x}');
      // Contador como el boceto: el texto con su token (lo que guarda la API).
      expect(q('.ed-chip .ed-cont').textContent).toBe('20 / 40');
      campo().insertBefore(doc.createTextNode('Grupos · '), campo().firstChild);
      campo().dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText' }));
      expect(mensajes('texto').pop()).toEqual(
        jasmine.objectContaining({ clave: 'sede.rotulo_horarios', texto: 'Grupos · Horarios · curso {x}' }),
      );
    });

    it('la leyenda «Lleno: …» lleva enlazada la palabra de «Lleno» (también la del borrador)', () => {
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [fila('horarios.lleno', 'Completo')] });
      tecla(q('.leyenda > li:nth-child(2)'), 'Enter');
      expect(ficha().dataset['token']).toBe('{x}');
      expect(ficha().dataset['valor']).toBe('Completo');
      expect(ficha().dataset['que']).toBe('Etiqueta de grupo lleno');
      expect(leido()).toBe('{x}: puedes apuntarte a la lista de espera');
      // Candado aprobado por el propietario (12/11), sin enlace a otra pantalla.
      ficha().dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
      expect(q('#ed-candado').textContent).toBe('Etiqueta de grupo lleno · Se cambia más arriba, en la etiqueta “Lleno” de un grupo');
      expect(q('#ed-candado').querySelector('[data-accion="ir"]')).toBeNull();
    });

    it('al pasar por la ficha sale su candado', () => {
      tecla(q('#horarios .rotulo'), 'Enter');
      ficha().dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
      expect(q('#ed-candado').hidden).toBeFalse();
      // El curso lo pone la web con la fecha (no se cambia en Grupos ni en Productos).
      expect(q('#ed-candado').textContent).toBe('Curso · Lo pone la web');
    });
  });

  describe('fichas con el dedo (el cursor junto a una ficha del final o del principio)', () => {
    beforeEach(() => {
      montar(SEDE);
      iframe.style.cssText = 'width:360px;height:640px;border:0';
      const tema = doc.createElement('style');
      tema.textContent = 'body{margin:0;font:16px/1.4 sans-serif}.envoltura{padding:0 16px}';
      doc.head.appendChild(tema);
      // «Horarios · curso [ficha]»: la ficha es lo último del texto.
      tecla(q('#horarios .rotulo'), 'Enter');
    });

    // Como el navegador: el `pointerdown` (con el tipo de puntero) va antes que su `mousedown`.
    const bajar = (el: Element, x: number, y: number, tipo: 'touch' | 'pen' | 'mouse' = 'touch') => {
      el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, pointerType: tipo, clientX: x, clientY: y }));
      const e = new MouseEvent('mousedown', { bubbles: true, cancelable: true, button: 0, clientX: x, clientY: y });
      el.dispatchEvent(e);
      return e;
    };
    const sitio = () => {
      const r = ventana.getSelection()!.getRangeAt(0);
      return [r.startContainer, r.startOffset, r.collapsed];
    };
    const indice = (n: Node) => Array.from(campo().childNodes).indexOf(n as ChildNode);

    it('tocar la mitad derecha de la ficha final: cursor detrás de ella, sin perder el foco ni cambiar el texto', () => {
      const f = ficha();
      const r = f.getBoundingClientRect();
      const e = bajar(f.lastElementChild!, r.right - 2, r.top + r.height / 2);
      expect(e.defaultPrevented).toBeTrue();
      expect(doc.activeElement).toBe(campo());
      expect(sitio()).toEqual([campo(), indice(f) + 1, true]);
      expect(leido()).toBe('Horarios · curso {x}');
      expect(mensajes('texto')).toEqual([]);
      // Retroceso desde ahí pide confirmación, una sola vez.
      expect(antesDeEscribir('deleteContentBackward').defaultPrevented).toBeTrue();
      expect(mensajes('pedir-quitar-dato').length).toBe(1);
    });

    it('tocar la mitad izquierda de la ficha: cursor delante de ella', () => {
      const f = ficha();
      const r = f.getBoundingClientRect();
      expect(bajar(f, r.left + 2, r.top + r.height / 2).defaultPrevented).toBeTrue();
      expect(doc.activeElement).toBe(campo());
      expect(sitio()).toEqual([campo(), indice(f), true]);
    });

    it('tocar a la derecha del final del texto (o debajo): cursor al final, tras la ficha; escribir va detrás', () => {
      const copia = campo().closest('.ed-campo')!;
      const r = ficha().getBoundingClientRect();
      expect(copia.getBoundingClientRect().right).toBeGreaterThan(r.right + 20);
      expect(bajar(copia, r.right + 15, r.top + r.height / 2).defaultPrevented).toBeTrue();
      expect(doc.activeElement).toBe(campo());
      expect(sitio()).toEqual([campo(), campo().childNodes.length, true]);
      expect(leido()).toBe('Horarios · curso {x}');
      doc.execCommand('insertText', false, ' bis');
      expect(mensajes('texto').pop()).toEqual(jasmine.objectContaining({ clave: 'sede.rotulo_horarios', texto: 'Horarios · curso {x} bis' }));
      expect(mensajes('pedir-quitar-dato')).toEqual([]);
    });

    it('con el ratón no se toca nada (la selección arrastrando sigue siendo del navegador), sobre la ficha o tras el final', () => {
      const f = ficha();
      const r = f.getBoundingClientRect();
      const copia = campo().closest('.ed-campo')!;
      cursor(campo(), 0);
      const antes = sitio();
      expect(bajar(f.lastElementChild!, r.right - 2, r.top + r.height / 2, 'mouse').defaultPrevented).toBeFalse();
      expect(bajar(f, r.left + 2, r.top + r.height / 2, 'mouse').defaultPrevented).toBeFalse();
      expect(bajar(copia, r.right + 15, r.top + r.height / 2, 'mouse').defaultPrevented).toBeFalse();
      expect(sitio()).toEqual(antes);
    });

    it('un lápiz cuenta como el dedo', () => {
      const f = ficha();
      const r = f.getBoundingClientRect();
      expect(bajar(f, r.left + 2, r.top + r.height / 2, 'pen').defaultPrevented).toBeTrue();
      expect(sitio()).toEqual([campo(), indice(f), true]);
    });

    it('tocar el texto normal no se toca: lo hace el navegador', () => {
      const texto = campo().firstChild!;
      const rango = doc.createRange();
      rango.setStart(texto, 2);
      rango.setEnd(texto, 3);
      const r = rango.getBoundingClientRect();
      expect(bajar(campo(), r.left + 1, r.top + r.height / 2).defaultPrevented).toBeFalse();
    });

    it('ficha al principio del texto: tocar su mitad izquierda deja el cursor delante', () => {
      tecla(campo(), 'Escape');
      tecla(q('.leyenda > li:nth-child(2)'), 'Enter');
      const f = ficha();
      expect(indice(f)).toBe(0);
      const r = f.getBoundingClientRect();
      expect(bajar(f, r.left + 1, r.top + r.height / 2).defaultPrevented).toBeTrue();
      expect(doc.activeElement).toBe(campo());
      expect(sitio()).toEqual([campo(), 0, true]);
      expect(leido()).toBe('{x}: puedes apuntarte a la lista de espera');
    });
  });

  describe('tocar tras el final con el dedo: la última línea', () => {
    const bajar = (el: Element, x: number, y: number) => {
      el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, pointerType: 'touch', clientX: x, clientY: y }));
      const e = new MouseEvent('mousedown', { bubbles: true, cancelable: true, button: 0, clientX: x, clientY: y });
      el.dispatchEvent(e);
      return e;
    };
    const alFinal = () => {
      const r = ventana.getSelection()!.rangeCount ? ventana.getSelection()!.getRangeAt(0) : null;
      return !!r && r.collapsed && r.startContainer === campo() && r.startOffset === campo().childNodes.length;
    };
    const lineas = (nodo: Node) => {
      const r = doc.createRange();
      r.selectNodeContents(nodo);
      // Una caja por línea: sin las de espacios o saltos finales (ancho 0) y con la más ancha de cada fila.
      const porFila = new Map<number, DOMRect>();
      Array.from(r.getClientRects())
        .filter((c) => c.width > 0 && c.height > 0)
        .forEach((c) => {
          const antes = porFila.get(Math.round(c.top));
          if (!antes || c.right > antes.right) {
            porFila.set(Math.round(c.top), c);
          }
        });
      return Array.from(porFila.values());
    };

    it('un texto de un span en dos líneas: el blanco a la derecha de la primera (parte baja) no lleva al final; el de la última, sí', () => {
      montar(SEDE);
      iframe.style.cssText = 'width:360px;height:640px;border:0';
      const tema = doc.createElement('style');
      tema.textContent = 'body{margin:0;font:16px/1 sans-serif}.envoltura{padding:0 16px}';
      doc.head.appendChild(tema);
      tecla(q('#horarios .rotulo'), 'Enter');
      campo().textContent = 'palabra palabra palabra palabra palabra palabra z';
      const cajas = lineas(campo().firstChild!);
      const primera = cajas[0];
      const ultima = cajas[cajas.length - 1];
      expect(cajas.length).withContext('el texto ocupa dos líneas').toBe(2);
      expect(ultima.right + 20).withContext('la primera línea es la más larga').toBeLessThan(primera.right);
      const copia = campo().closest('.ed-campo')!;
      expect(copia.getBoundingClientRect().right).toBeGreaterThan(primera.right + 10);
      cursor(campo().firstChild!, 0);
      // Parte baja de la primera línea, a la derecha de su final: el punto también cae sobre la caja de la última línea.
      const y1 = primera.bottom - 1;
      expect(y1).toBeGreaterThanOrEqual(ultima.top);
      expect(y1).toBeLessThan(ultima.top + ultima.height / 2);
      expect(bajar(copia, primera.right + 8, y1).defaultPrevented).toBeFalse();
      expect(alFinal()).toBeFalse();
      // A la derecha de la última línea: al final del texto.
      expect(bajar(copia, ultima.right + 8, ultima.top + ultima.height / 2).defaultPrevented).toBeTrue();
      expect(alFinal()).toBeTrue();
    });

    it('un campo de varias líneas (display:block): a la derecha de la última línea y debajo de ella, cursor al final', () => {
      montar(LEGALES);
      iframe.style.cssText = 'width:360px;height:640px;border:0';
      const tema = doc.createElement('style');
      tema.textContent = 'body{margin:0;font:16px/1.4 sans-serif}.envoltura{padding:0 16px}.ed-campo{padding-bottom:24px}';
      doc.head.appendChild(tema);
      tecla(q('#aviso-legal > p'), 'Enter');
      expect(campo().classList).toContain('ed-multi');
      campo().textContent = 'Uno.\n\nDos.';
      const cajas = lineas(campo().firstChild!);
      const ultima = cajas[cajas.length - 1];
      expect(campo().getClientRects().length).withContext('un block da una sola caja').toBe(1);
      expect(campo().getBoundingClientRect().right).toBeGreaterThan(ultima.right + 40);
      const copia = campo().closest('.ed-campo')!;
      cursor(campo().firstChild!, 0);
      // En la primera línea, a la derecha: es del navegador.
      expect(bajar(campo(), cajas[0].right + 8, cajas[0].top + cajas[0].height / 2).defaultPrevented).toBeFalse();
      expect(bajar(campo(), ultima.right + 8, ultima.top + ultima.height / 2).defaultPrevented).toBeTrue();
      expect(alFinal()).toBeTrue();
      cursor(campo().firstChild!, 0);
      expect(bajar(copia, ultima.left + 2, ultima.bottom + 6).defaultPrevented).toBeTrue();
      expect(alFinal()).toBeTrue();
    });
  });

  describe('fichas: flechas y borrar', () => {
    beforeEach(() => {
      montar(TARIFAS);
      tecla(q('.cuadro caption .rotulo'), 'Enter');
    });

    it('las flechas saltan la ficha como un carácter', () => {
      const texto = campo().firstChild!;
      cursor(campo(), 2);
      const izquierda = tecla(campo(), 'ArrowLeft');
      expect(izquierda.defaultPrevented).toBeTrue();
      const r = ventana.getSelection()!.getRangeAt(0);
      expect([r.startContainer, r.startOffset]).toEqual([campo(), 1]);
      cursor(texto, (texto.nodeValue ?? '').length);
      expect(tecla(campo(), 'ArrowRight').defaultPrevented).toBeTrue();
      const d = ventana.getSelection()!.getRangeAt(0);
      expect([d.startContainer, d.startOffset]).toEqual([campo(), 2]);
      // Lejos de la ficha, la flecha es la del navegador.
      cursor(texto, 3);
      expect(tecla(campo(), 'ArrowRight').defaultPrevented).toBeFalse();
    });

    it('Retroceso junto a la ficha no la borra: pregunta al panel con la frase de hoy y la de sin el dato', () => {
      cursor(campo(), 2);
      const e = antesDeEscribir('deleteContentBackward');
      expect(e.defaultPrevented).toBeTrue();
      expect(ficha()).not.toBeNull();
      expect(mensajes('pedir-quitar-dato')).toEqual([
        {
          canal: CANAL_EDITOR_WEB,
          tipo: 'pedir-quitar-dato',
          clave: 'tarifas.leyenda_cuadro',
          huecos: ['x'],
          antes: 'Mensualidades del curso 2026-2027',
          despues: 'Mensualidades del curso',
        },
      ]);
      // El foco se va al diálogo del panel: la edición sigue abierta.
      campo().dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));
      expect(campo()).not.toBeNull();
    });

    it('«Cancelar»: todo como estaba y el foco, en el texto', () => {
      cursor(campo(), 2);
      antesDeEscribir('deleteContentBackward');
      campo().blur();
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: false });
      expect(leido()).toBe('Mensualidades del curso {x}');
      expect(doc.activeElement).toBe(campo());
      const r = ventana.getSelection()!.getRangeAt(0);
      expect([r.startContainer, r.startOffset, r.collapsed]).toEqual([campo(), 2, true]);
      expect(mensajes('texto')).toEqual([]);
    });

    it('«Quitar dato»: se quita con un comando de edición y el texto queda limpio como el tema', () => {
      cursor(campo(), 2);
      antesDeEscribir('deleteContentBackward');
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: true });
      expect(campo().querySelector('.ed-ficha')).toBeNull();
      expect(mensajes('texto').pop()).toEqual(jasmine.objectContaining({ clave: 'tarifas.leyenda_cuadro', texto: 'Mensualidades del curso' }));
      expect(doc.activeElement).toBe(campo());
    });

    it('Suprimir delante de la ficha también pregunta', () => {
      cursor(campo().firstChild!, (campo().firstChild!.nodeValue ?? '').length);
      expect(antesDeEscribir('deleteContentForward').defaultPrevented).toBeTrue();
      expect(mensajes('pedir-quitar-dato').length).toBe(1);
    });

    it('escribir sobre una selección con la ficha pregunta; si se confirma, se escribe encima', () => {
      elegir([campo().firstChild!, 15], [campo(), 2]);
      expect(antesDeEscribir('insertText', 'Z').defaultPrevented).toBeTrue();
      expect(mensajes('pedir-quitar-dato').length).toBe(1);
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: true });
      expect(leido()).toBe('Mensualidades d' + 'Z');
    });

    it('pegar sobre la ficha y cortarla también preguntan; sin la ficha, pegar y escribir siguen igual', () => {
      elegir([campo(), 0], [campo(), 2]);
      const datos = new DataTransfer();
      datos.setData('text/plain', 'Cuotas');
      campo().dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: datos }));
      expect(mensajes('pedir-quitar-dato').length).toBe(1);
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: false });
      elegir([campo(), 1], [campo(), 2]);
      const cortar = new ClipboardEvent('cut', { bubbles: true, cancelable: true, clipboardData: new DataTransfer() });
      campo().dispatchEvent(cortar);
      expect(cortar.defaultPrevented).toBeTrue();
      expect(cortar.clipboardData!.getData('text/plain')).toBe('2026-2027');
      expect(mensajes('pedir-quitar-dato').length).toBe(2);
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: false });
      cursor(campo().firstChild!, 3);
      expect(antesDeEscribir('insertText', 'y').defaultPrevented).toBeFalse();
      expect(antesDeEscribir('deleteContentBackward').defaultPrevented).toBeFalse();
      expect(mensajes('pedir-quitar-dato').length).toBe(2);
    });

    /** Lo que haría el navegador en un cambio que no se puede parar: quitar la ficha (y escribir algo). */
    function quitarAMano(escrito: string): void {
      ficha().replaceWith(doc.createTextNode(escrito));
    }

    for (const inputType of ['historyRedo', 'insertFromDrop', 'insertReplacementText'] as const) {
      it(`red de seguridad: ${inputType} que quita la ficha la devuelve y pregunta`, () => {
        elegir([campo(), 1], [campo(), 2]);
        // Sin `beforeinput` que parar (o uno que el navegador no da): el cambio ya ha quitado la ficha.
        quitarAMano('á');
        campo().dispatchEvent(new InputEvent('input', { bubbles: true, inputType, data: 'á' }));
        expect(leido()).toBe('Mensualidades del curso {x}');
        expect(ficha().querySelector(':scope > span[aria-hidden="true"]')!.textContent).toBe('2026-2027');
        expect(mensajes('texto')).toEqual([]);
        expect(mensajes('pedir-quitar-dato')).toEqual([
          jasmine.objectContaining({ clave: 'tarifas.leyenda_cuadro', huecos: ['x'], despues: 'Mensualidades del curso' }),
        ]);
        // El cursor, justo tras la ficha devuelta.
        const r = ventana.getSelection()!.getRangeAt(0);
        expect([r.startContainer, r.startOffset, r.collapsed]).toEqual([campo(), 2, true]);
        // Y el diálogo sigue el flujo de siempre.
        delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: true });
        expect(campo().querySelector('.ed-ficha')).toBeNull();
        expect(mensajes('texto').pop()).toEqual(jasmine.objectContaining({ texto: 'Mensualidades del curso' }));
      });
    }

    it('red de seguridad: tras quitar una ficha confirmada, el cambio siguiente no la pide de vuelta', () => {
      cursor(campo(), 2);
      antesDeEscribir('deleteContentBackward');
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: true });
      campo().append(doc.createTextNode('!'));
      campo().dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: '!' }));
      expect(mensajes('pedir-quitar-dato').length).toBe(1);
      expect(mensajes('texto').pop()).toEqual(jasmine.objectContaining({ texto: 'Mensualidades del curso!' }));
    });

    it('red de seguridad en una composición: no repinta a mitad; al acabarla devuelve la ficha y pregunta', async () => {
      elegir([campo(), 1], [campo(), 2]);
      campo().dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true, data: '' }));
      // La composición no se puede cancelar: el `beforeinput` pasa y el navegador cambia el texto igual.
      const antes = new InputEvent('beforeinput', { bubbles: true, cancelable: false, inputType: 'insertCompositionText', data: 'á', isComposing: true });
      campo().dispatchEvent(antes);
      quitarAMano('á');
      campo().dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertCompositionText', data: 'á', isComposing: true }));
      // A mitad de la composición, el texto se deja como lo tiene el navegador.
      expect(campo().querySelector('.ed-ficha')).toBeNull();
      expect(mensajes('pedir-quitar-dato')).toEqual([]);
      expect(mensajes('texto')).toEqual([]);
      campo().dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: 'á' }));
      await new Promise((listo) => setTimeout(listo));
      expect(leido()).toBe('Mensualidades del curso {x}');
      expect(mensajes('texto')).toEqual([]);
      expect(mensajes('pedir-quitar-dato')).toEqual([
        jasmine.objectContaining({ clave: 'tarifas.leyenda_cuadro', huecos: ['x'], despues: 'Mensualidades del curso' }),
      ]);
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: true });
      expect(campo().querySelector('.ed-ficha')).toBeNull();
      expect(mensajes('texto').pop()).toEqual(jasmine.objectContaining({ texto: 'Mensualidades del curso' }));
    });

    it('con el diálogo ya abierto, un cambio que quita la ficha solo la devuelve (sin otro diálogo) y el diálogo sigue valiendo', () => {
      cursor(campo(), 2);
      antesDeEscribir('deleteContentBackward');
      expect(mensajes('pedir-quitar-dato').length).toBe(1);
      quitarAMano('');
      campo().dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'historyRedo' }));
      expect(leido()).toBe('Mensualidades del curso {x}');
      expect(mensajes('pedir-quitar-dato').length).toBe(1);
      expect(mensajes('texto')).toEqual([]);
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: true });
      expect(campo().querySelector('.ed-ficha')).toBeNull();
      expect(mensajes('texto').pop()).toEqual(jasmine.objectContaining({ texto: 'Mensualidades del curso' }));
    });

    it('una composición que empieza con la ficha elegida no la pisa: la selección se recoge al final', () => {
      elegir([campo().firstChild!, 15], [campo(), 2]);
      campo().dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true, data: '' }));
      const r = ventana.getSelection()!.getRangeAt(0);
      expect([r.startContainer, r.startOffset, r.collapsed]).toEqual([campo(), 2, true]);
      expect(ficha()).not.toBeNull();
      // Sin ficha en la selección, la composición sigue como el navegador quiera.
      elegir([campo().firstChild!, 3], [campo().firstChild!, 6]);
      campo().dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true, data: '' }));
      expect(ventana.getSelection()!.getRangeAt(0).collapsed).toBeFalse();
    });

    it('quitar la ficha solo limpia lo que la toca: los espacios dobles del usuario en otra parte se quedan', () => {
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [fila('tarifas.leyenda_cuadro', 'Cuotas  del curso ({x})')] });
      tecla(q('.cuadro caption .rotulo'), 'Enter');
      expect(leido()).toBe('Cuotas  del curso ({x})');
      cursor(campo(), 2);
      antesDeEscribir('deleteContentBackward');
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: true });
      expect(mensajes('texto').pop()).toEqual(jasmine.objectContaining({ clave: 'tarifas.leyenda_cuadro', texto: 'Cuotas  del curso' }));
    });

    it('copiar da el texto como se ve (sin la frase oculta del lector)', () => {
      elegir([campo().firstChild!, 15], [campo(), 2]);
      const copiar = new ClipboardEvent('copy', { bubbles: true, cancelable: true, clipboardData: new DataTransfer() });
      campo().dispatchEvent(copiar);
      expect(copiar.clipboardData!.getData('text/plain')).toBe('el curso 2026-2027');
    });
  });

  describe('Tarifas', () => {
    beforeEach(() => montar(TARIFAS));

    it('los puntos, la fila de hermanos y la de cada alumno; «Cada adulto» y «Cada menor» con candado', () => {
      const li = Array.from(doc.querySelectorAll<HTMLElement>('.tarifa-incluye > li')).map((x) => x.dataset['campo']);
      expect(li).toEqual(['tarifas.punto1', 'tarifas.tambien', 'tarifas.incluye_aparte']);
      expect(Array.from(doc.querySelectorAll<HTMLElement>('td.quien')).map((td) => td.dataset['campo'] ?? td.dataset['que'])).toEqual([
        'tarifas.cada_alumno',
        'tarifas.hermanos',
        'Quién paga',
        'Quién paga',
      ]);
      expect(q('.grupo-tarifa td.quien').classList).toContain('ed-dato');
      expect(q('.tarifa-igual').dataset['campo']).toBe('tarifas.cuota_igual');
      expect(q('[data-dato="examen"]').dataset['campo']).toBe('tarifas.examen_desde');
      expect(q('.aparte li:first-child .aparte-concepto').classList).not.toContain('ed-texto');
      expect(Array.from(doc.querySelectorAll<HTMLElement>('td.importe > small')).every((s) => s.dataset['campo'] === 'tarifas.al_mes')).toBeTrue();
    });

    it('hermanos y cada alumno con el mismo texto: la fila se sabe por su `data-quien` (lo pone el tema)', () => {
      const temas: BloqueBorrador[][] = [];
      desactivar();
      desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: (b) => temas.push(b) });
      const bloques = [fila('tarifas.cada_alumno', 'Cada alumno'), fila('tarifas.hermanos', 'Cada alumno')];
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques });
      // El tema recibe los textos tal cual (sin marcas).
      expect(temas.at(-1)).toEqual(bloques);
      // Lo que pintaría el tema con ese borrador: las dos filas dicen lo mismo.
      const [general, hermanos] = Array.from(doc.querySelectorAll<HTMLElement>('tbody:not(.grupo-tarifa) td.quien'));
      general.textContent = 'Cada alumno';
      hermanos.textContent = 'Cada alumno';
      desactivar();
      desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: () => undefined });
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques });
      const [g, h] = Array.from(doc.querySelectorAll<HTMLElement>('tbody:not(.grupo-tarifa) td.quien'));
      expect([g.dataset['campo'], h.dataset['campo']]).toEqual(['tarifas.cada_alumno', 'tarifas.hermanos']);
      tecla(h, 'Enter');
      expect(leido()).toBe('Cada alumno');
    });

    it('sin fila de hermanos en el borrador, el tema no recibe nada de más', () => {
      const temas: BloqueBorrador[][] = [];
      desactivar();
      desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: (b) => temas.push(b) });
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [] });
      expect(temas.at(-1)).toEqual([]);
    });
  });

  describe('Legales', () => {
    beforeEach(() => montar(LEGALES));

    it('solo los 20 textos que lee el tema; el texto legal fijo no se ofrece', () => {
      const legales = [...new Set(campos().filter((c) => c!.startsWith('legal.')))];
      expect(legales.sort()).toEqual(
        [
          'legal.titulo',
          'legal.entrada',
          'legal.actualizada',
          'legal.en_esta_pagina',
          'legal.aviso',
          'legal.aviso.p0',
          'legal.aviso.h1',
          'legal.aviso.p1',
          'legal.aviso.h2',
          'legal.aviso.p2',
          'legal.aviso.h3',
          'legal.privacidad',
          'legal.priv.p0',
          'legal.priv.h2',
          'legal.cookies',
          'legal.normas',
          'legal.ficha_titular',
          'legal.ficha_cif',
          'legal.ficha_domicilio',
          'legal.ficha_correo',
        ].sort(),
      );
      for (const fijo of ['Texto fijo largo.', 'Española.', 'Club.', 'Puedes pedir.', 'Solo técnicas.', 'Son las normas.']) {
        const p = Array.from(doc.querySelectorAll('.legal-texto p')).find((x) => x.textContent === fijo)!;
        expect(p.classList).withContext(fijo).not.toContain('ed-texto');
      }
      expect(Array.from(doc.querySelectorAll('h3')).filter((h) => h.classList.contains('ed-texto')).map((h) => h.textContent)).toEqual([
        'Para qué sirve esta web',
        'Propiedad intelectual',
        'Responsabilidad',
        'Tus derechos',
      ]);
      // «Última actualización:» va envuelto; la fecha sigue con su candado.
      expect(q('.actualizado > .ed-envuelto').dataset['campo']).toBe('legal.actualizada');
      expect(q('.actualizado time').classList).toContain('ed-dato');
    });

    it('el párrafo de la propiedad intelectual lleva el nombre legal como ficha', () => {
      tecla(q('#aviso-legal h3:nth-of-type(2) + p'), 'Enter');
      expect(leido()).toBe(
        'Los textos, fotos, logotipos y diseño de esta web son de {x} o de sus autores. No se pueden copiar ni usar con otros fines sin permiso.',
      );
      expect(ficha().dataset['que']).toBe('Nombre legal');
    });
  });

  describe('Preinscripción', () => {
    it('sin grupo: la frase del teléfono y su final, envueltas a cada lado del teléfono (con candado)', () => {
      montar(PRE_SIN_GRUPO);
      const [antes, despues] = Array.from(doc.querySelectorAll<HTMLElement>('.vacio-contacto > .ed-envuelto'));
      expect([antes.dataset['campo'], despues.dataset['campo']]).toEqual(['pre.vacio_llamar', 'pre.vacio_llamar_fin']);
      expect(q('.vacio-contacto > a.cifras').classList).toContain('ed-dato');
      expect(q('.progreso-linea').hasAttribute('aria-hidden')).toBeFalse();
      expect(Array.from(doc.querySelectorAll<HTMLElement>('.progreso-linea > li')).map((li) => li.dataset['campo'])).toEqual([
        'pre.paso_pueblo',
        'pre.paso_grupo',
        'pre.paso_datos',
      ]);
      tecla(q('.vacio > h2'), 'Enter');
      expect(leido()).toBe('En {x} no hay grupo para {n} años');
      expect(Array.from(campo().querySelectorAll<HTMLElement>('.ed-ficha')).map((f) => f.dataset['valor'])).toEqual(['Herrera', '15']);
    });

    it('red de seguridad con varias fichas: pregunta por la que falta; si faltan varias, las devuelve y pregunta por la primera', () => {
      montar(PRE_SIN_GRUPO);
      tecla(q('.vacio > h2'), 'Enter');
      const fichas = () => Array.from(campo().querySelectorAll<HTMLElement>('.ed-ficha'));
      fichas()[1].remove();
      campo().dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertReplacementText' }));
      expect(leido()).toBe('En {x} no hay grupo para {n} años');
      expect(mensajes('pedir-quitar-dato').pop()).toEqual(jasmine.objectContaining({ clave: 'pre.vacio_titulo', huecos: ['n'] }));
      delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: false });
      fichas().forEach((f) => f.remove());
      campo().dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'historyRedo' }));
      expect(fichas().map((f) => f.dataset['valor'])).toEqual(['Herrera', '15']);
      expect(mensajes('pedir-quitar-dato').pop()).toEqual(jasmine.objectContaining({ huecos: ['x'] }));
      expect(mensajes('texto')).toEqual([]);
    });

    it('enviada a la lista de espera: los textos de la espera, la referencia envuelta y los pasos fijos fuera', () => {
      montar(PRE_FIN_ESPERA);
      expect(campos().filter((c) => c!.startsWith('pre.'))).toEqual([
        'pre.fin_espera',
        'pre.referencia',
        'pre.estado_espera',
        'pre.que_ahora',
        'pre.paso_correo',
        'pre.paso_correo_espera',
      ]);
      tecla(q('.banda-estado'), 'Enter');
      expect(Array.from(campo().querySelectorAll<HTMLElement>('.ed-ficha')).map((f) => [f.dataset['token'], f.dataset['valor']])).toEqual([
        ['{n}', '3'],
        ['{grupo}', 'Peques'],
        ['{sede}', 'Herrera'],
        ['{dias}', 'el lunes'],
      ]);
    });
  });

  it('ir a un texto que aún no se ve (un caso que se está pintando): va cuando el tema lo pinta', () => {
    montar(marco('<div class="p-eventos"><div class="envoltura pagina-cabeza"><h1 class="pagina-titular">Eventos</h1></div><section class="proximos" id="proximos"><div class="envoltura"></div></section></div>'));
    delPanel({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'eventos.sin_proximos_titular' });
    expect(mensajes('mostrar')).toEqual([]);
    return new Promise<void>((listo) => {
      q('#proximos .envoltura').innerHTML = '<div class="ev-vacio"><h3>No hay eventos próximos</h3></div>';
      setTimeout(() => {
        expect(mensajes('mostrar').length).toBe(1);
        expect(doc.activeElement).toBe(q('.ev-vacio > h3'));
        listo();
      }, 20);
    });
  });
});
