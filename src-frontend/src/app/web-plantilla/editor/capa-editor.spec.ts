import { activarCapaEditor } from './capa-editor';
import { CANAL_EDITOR_WEB } from './puente-editor';

/** Lo justo del DOM de «La línea comarcal» (Inicio) para la capa. */
const WEB = `
<div class="lc">
  <a class="salto" href="#principal">Saltar al contenido</a>
  <header class="cabecera"><div class="envoltura">
    <a class="marca" href="/"><span class="logo">TS</span><span class="marca-nombre"><span>Taekwondo</span><span>Sierra Sur</span></span></a>
    <a class="boton cabecera-cta" href="/preinscripcion">Preinscribirme</a>
  </div></header>
  <main id="principal" tabindex="-1">
    <section class="portada" id="portada">
      <div class="envoltura portada-rejilla"><div class="portada-texto">
        <p class="rotulo"><span class="rotulo-disciplina">Escuela de taekwondo</span> · <span class="rotulo-sedes">6 sedes</span></p>
        <h1 class="titular">Taekwondo en seis pueblos</h1>
      </div></div>
      <div class="envoltura"><app-lc-linea-sedes>
        <div class="red">
          <h3 class="parada"><button type="button" class="parada-boton"><span class="parada-nombre">Herrera</span><span class="parada-proxima"><span class="parada-cuando">Hoy <span class="hora">17:00</span></span></span></button></h3>
          <div class="horario abierto"><p class="tablero-sede">Herrera</p><p class="tablero-lugar">Pabellón</p></div>
        </div>
      </app-lc-linea-sedes></div>
    </section>
    <section class="presentacion" id="presentacion" aria-labelledby="presentacion-titulo">
      <h2 id="presentacion-titulo">Clases cerca de casa</h2>
      <ul class="datos"><li class="dato" data-dato="sedes">6 sedes</li><li class="dato" data-dato="mensualidad">25 € al mes</li></ul>
    </section>
    <section class="seccion" id="texto-foto-1"><h2 class="titulo-seccion">Primer día</h2></section>
    <section class="tarifas" id="tarifas"><p><span class="tarifa-importe">25 €</span></p></section>
  </main>
  <footer class="pie"><div class="envoltura"><p class="pie-marca">Taekwondo Sierra Sur</p><div class="pie-fila"><p>© 2026 Taekwondo Sierra Sur</p></div></div></footer>
</div>`;

describe('capa de edición de la vista previa (W12c)', () => {
  let marco: HTMLIFrameElement;
  let ventana: Window;
  let doc: Document;
  let enviados: jasmine.Spy;
  let borradores: unknown[];
  let desactivar: () => void;

  beforeEach(() => {
    marco = document.createElement('iframe');
    document.body.appendChild(marco);
    ventana = marco.contentWindow!;
    doc = marco.contentDocument!;
    doc.body.innerHTML = WEB;
    // Como el tema: la raíz ocupa al menos una pantalla (`linea-comarcal.css`).
    const tema = doc.createElement('style');
    tema.textContent = '.lc{min-height:100vh;min-height:100svh}';
    doc.head.appendChild(tema);
    enviados = spyOn(window, 'postMessage');
    borradores = [];
    desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: (b) => borradores.push(b) });
  });

  afterEach(() => {
    desactivar();
    marco.remove();
  });

  const q = <T extends Element = HTMLElement>(selector: string) => doc.querySelector<T & HTMLElement>(selector)!;
  const mensajes = () => enviados.calls.allArgs();
  const pulsar = (el: Element) => {
    const evento = new MouseEvent('click', { bubbles: true, cancelable: true });
    el.dispatchEvent(evento);
    return evento;
  };

  it('avisa al panel de que está lista, siempre al propio origen', () => {
    expect(mensajes()).toContain([{ canal: CANAL_EDITOR_WEB, tipo: 'listo' }, location.origin]);
    expect(mensajes().every(([, origen]) => origen === location.origin)).toBeTrue();
  });

  it('envuelve las partes fijas con su nombre y su nota, y los bloques con su nombre', () => {
    const cabecera = q('.lc > .cabecera');
    expect(cabecera.classList).toContain('ed-fija');
    expect(cabecera.getAttribute('aria-label')).toBe('Parte Cabecera');
    expect(cabecera.querySelector('.ed-nombre')!.textContent).toBe('Cabecera');
    expect(cabecera.querySelector('.ed-nota')!.textContent).toBe('Su sitio lo pone el tema. Pulsa un texto para cambiarlo.');
    expect(q('app-lc-linea-sedes').querySelector('.ed-nombre')!.getAttribute('aria-label')).toBe('Horarios (la línea): elegir la parte');

    const presentacion = q('#presentacion');
    expect(presentacion.getAttribute('aria-label')).toBe('Bloque Presentación');
    expect(presentacion.hasAttribute('aria-labelledby')).toBeFalse();
    expect(presentacion.querySelector('.ed-nombre')!.getAttribute('aria-label')).toBe('Presentación: elegir el bloque');
    expect(presentacion.querySelector('.ed-nota')).toBeNull();

    const textoFoto = q('#texto-foto-1');
    expect(textoFoto.dataset['bloque']).toBe('texto-foto-1');
    expect(textoFoto.querySelector('.ed-nombre')!.textContent).toBe('Texto con foto');
    expect(textoFoto.getAttribute('aria-label')).toBe('Bloque Texto con foto «Primer día»');
  });

  it('marca los datos de otras pantallas con su candado (tabla d517)', () => {
    expect(q('.rotulo-disciplina').dataset['donde']).toBe('equipo');
    expect(q('.rotulo-sedes').dataset['que']).toBe('Número de sedes');
    expect(q('.rotulo-sedes').dataset['donde']).toBe('sedes');
    expect(q('[data-dato="mensualidad"]').dataset['donde']).toBe('productos');
    expect(q('.tarifa-importe').dataset['que']).toBe('Importe de la mensualidad');
    expect(q('.pie-fila > p').dataset['donde']).toBe('datos-escuela');
    expect(q('.logo').classList).toContain('ed-dato--caja');
  });

  it('un enlace oculto por tipo de dato, nunca dentro de un control de la web', () => {
    const enlaces = Array.from(doc.querySelectorAll<HTMLElement>('.ed-dato__ir'));
    const sede = enlaces.filter((a) => a.dataset['clave'] === 'Nombre de la sede|sedes');
    expect(sede.length).toBe(1);
    expect(sede[0].parentElement!.classList).toContain('tablero-sede');
    expect(sede[0].textContent).toBe('Nombre de la sede: se cambia en Sedes');
    expect(enlaces.some((a) => a.closest('button'))).toBeFalse();
    // Lo del equipo de Taemoi no lleva enlace.
    expect(enlaces.some((a) => a.dataset['clave']!.endsWith('|equipo'))).toBeFalse();
    expect(q('.rotulo-sedes .ed-dato__ir').textContent).toBe('Número de sedes: se cambia en Sedes');
    expect(q('[data-dato="mensualidad"] .ed-dato__ir').textContent).toBe('Mensualidad: se cambia en Tesorería · Productos');
  });

  it('al enfocar el enlace oculto enseña «X · Se cambia en Y» y su enlace lleva al panel por el puente', () => {
    q('.tablero-sede .ed-dato__ir').dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    const aviso = q('#ed-candado');
    expect(aviso.hidden).toBeFalse();
    expect(aviso.getAttribute('aria-hidden')).toBe('true');
    expect(aviso.textContent).toBe('Nombre de la sede · Se cambia enSedes');
    const ir = aviso.querySelector<HTMLAnchorElement>('a')!;
    expect(ir.tabIndex).toBe(-1);

    enviados.calls.reset();
    const evento = pulsar(ir);
    expect(evento.defaultPrevented).toBeTrue();
    expect(mensajes()).toEqual([[{ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: 'sedes' }, location.origin]]);
    expect(aviso.hidden).toBeTrue();
  });

  it('el enlace oculto también pide su destino al pulsarlo', () => {
    pulsar(q('[data-dato="mensualidad"] .ed-dato__ir'));
    expect(mensajes()).toContain([{ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: 'productos' }, location.origin]);
  });

  it('lo del equipo de Taemoi: «Lo cambia el equipo de Taemoi», sin enlace', () => {
    q('.logo').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    const aviso = q('#ed-candado');
    expect(aviso.textContent).toBe('Logo y color · Lo cambiael equipo de Taemoi');
    expect(aviso.querySelector('a')).toBeNull();
    expect(q('.logo').classList).toContain('es-activo');
  });

  it('los enlaces y botones de la web, fuera del tabulador y sin navegar', () => {
    expect(q('.salto').tabIndex).toBe(-1);
    expect(q('.cabecera-cta').tabIndex).toBe(-1);
    expect(q('.parada-boton').tabIndex).toBe(-1);
    expect(q('.ed-nombre').tabIndex).toBe(0);
    let llego = false;
    q('.cabecera-cta').addEventListener('click', () => (llego = true));
    expect(pulsar(q('.cabecera-cta')).defaultPrevented).toBeTrue();
    expect(llego).toBeFalse();
  });

  it('elige un bloque con su nombre y Escape lo suelta y devuelve el foco al nombre', () => {
    const nombre = q('#presentacion .ed-nombre');
    pulsar(nombre);
    expect(q('#presentacion').classList).toContain('es-sel');
    expect(nombre.getAttribute('aria-pressed')).toBe('true');
    doc.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(q('#presentacion').classList).not.toContain('es-sel');
    expect(nombre.getAttribute('aria-pressed')).toBe('false');
    expect(doc.activeElement).toBe(nombre);
  });

  it('vuelve a anotar lo que el tema pinta de nuevo', async () => {
    const nueva = doc.createElement('section');
    nueva.id = 'galeria';
    q('main').appendChild(nueva);
    await new Promise((r) => setTimeout(r));
    expect(nueva.querySelector('.ed-nombre')!.textContent).toBe('Galería');
    expect(nueva.querySelectorAll('.ed-barra').length).toBe(1);
  });

  describe('alto que manda al panel', () => {
    const altos = () =>
      mensajes()
        .map(([m]) => m as { tipo: string; alto?: number })
        .filter((m) => m.tipo === 'alto')
        .map((m) => m.alto!);
    const ultimo = () => altos()[altos().length - 1];
    /** Deja que el ResizeObserver de la ventana del iframe avise. */
    const cuadro = () => new Promise((r) => ventana.requestAnimationFrame(() => setTimeout(r, 0)));

    it('mide el contenido (la raíz del tema), no el documento: no depende del alto del iframe', async () => {
      marco.style.height = '4000px';
      const relleno = doc.createElement('div');
      relleno.style.height = '1200px';
      q('main').appendChild(relleno);
      await cuadro();
      await cuadro();
      expect(ultimo()).toBe(Math.ceil(q('.lc').offsetHeight));
      expect(ultimo()).toBeGreaterThan(1200);
      expect(ultimo()).toBeLessThan(4000);
    });

    it('baja cuando el contenido se acorta (de Móvil a Escritorio)', async () => {
      const relleno = doc.createElement('div');
      relleno.style.height = '3000px';
      q('main').appendChild(relleno);
      await cuadro();
      await cuadro();
      marco.style.height = `${ultimo()}px`;
      const largo = ultimo();
      expect(largo).toBeGreaterThan(3000);
      relleno.remove();
      await cuadro();
      await cuadro();
      expect(ultimo()).toBeLessThan(largo - 2500);
      expect(ultimo()).toBe(Math.ceil(q('.lc').offsetHeight));
    });

    it('la foto de portada usa una pantalla nominal de 800 px, no el alto del iframe', async () => {
      const tema = doc.createElement('style');
      tema.textContent =
        '.lc{--hueco:16px;--cabecera:72px}@media (min-width:900px){.lc .portada-foto{aspect-ratio:auto;height:max(360px, min(calc((50vw - var(--hueco) / 2) * 1.25), calc(100svh - var(--cabecera) - 220px)))}}';
      doc.head.insertBefore(tema, doc.head.firstChild);
      const foto = doc.createElement('div');
      foto.className = 'portada-foto';
      q('.portada-rejilla').appendChild(foto);
      marco.style.maxWidth = 'none';
      marco.style.width = '1200px';
      marco.style.height = '3000px';
      await cuadro();
      // max(360, min((600 - 8) × 1,25, 800 - 72 - 220)) = 508
      expect(foto.offsetHeight).toBe(508);
    });
  });

  it('«Próxima clase» (dentro del botón de la parada) lleva su enlace oculto al lado del botón, no dentro', () => {
    const ir = Array.from(doc.querySelectorAll<HTMLElement>('.ed-dato__ir')).filter((a) => a.dataset['clave'] === 'Próxima clase|grupos');
    expect(ir.length).toBe(1);
    expect(ir[0].closest('button')).toBeNull();
    expect(ir[0].previousElementSibling).toBe(q('.parada-boton'));
    expect(ir[0].textContent).toBe('Próxima clase: se cambia en Grupos');

    ir[0].dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    expect(q('#ed-candado').hidden).toBeFalse();
    expect(q('#ed-candado').textContent).toBe('Próxima clase · Se cambia enGrupos');
    expect(q('.parada-cuando').classList).toContain('es-activo');

    enviados.calls.reset();
    pulsar(ir[0]);
    expect(mensajes()).toContain([{ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: 'grupos' }, location.origin]);
  });

  it('un hover sobre un dato no vuelve a decorar la página', async () => {
    await new Promise((r) => setTimeout(r));
    const buscar = spyOn(doc, 'querySelectorAll').and.callThrough();
    q('.logo').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    q('.tarifa-importe').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    await new Promise((r) => setTimeout(r));
    expect(q('#ed-candado').hidden).toBeFalse();
    expect(buscar).not.toHaveBeenCalled();
  });

  it('entrega el borrador del panel y descarta el de otra ventana u otro origen', () => {
    const datos = { canal: CANAL_EDITOR_WEB, tipo: 'borrador', bloques: [{ clave: 'portada.titular', texto: 'Hola', imagenUrl: null, orden: 0 }] };
    ventana.dispatchEvent(new MessageEvent('message', { data: datos, origin: location.origin, source: ventana }));
    ventana.dispatchEvent(new MessageEvent('message', { data: datos, origin: 'https://otra.example', source: window }));
    ventana.dispatchEvent(new MessageEvent('message', { data: { ...datos, tipo: 'x' }, origin: location.origin, source: window }));
    expect(borradores).toEqual([]);
    ventana.dispatchEvent(new MessageEvent('message', { data: datos, origin: location.origin, source: window }));
    expect(borradores).toEqual([datos.bloques]);
  });
});
