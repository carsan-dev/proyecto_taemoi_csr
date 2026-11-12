import { activarCapaEditor } from './capa-editor';
import { Caja, cortan, colocarEvitando, encajarEnVista, PeticionColocar } from './colocar-flotantes';
import { CANAL_EDITOR_WEB } from './puente-editor';

const caja = (left: number, top: number, ancho: number, alto: number): Caja => ({ left, top, right: left + ancho, bottom: top + alto });

describe('colocar evitando cajas (flotantes de la capa)', () => {
  const base: PeticionColocar = {
    ancho: 200,
    alto: 30,
    ancla: caja(40, 300, 100, 20),
    separacion: 8,
    izquierda: 40,
    arriba: true,
    vista: { ancho: 360, alto: 640 },
    evitar: [],
  };

  it('sin nada que evitar deja el sitio de siempre, sobre el ancla o bajo ella', () => {
    expect(colocarEvitando(base)).toEqual({ top: 300 - 30 - 8, left: 40 });
    expect(colocarEvitando({ ...base, arriba: false })).toEqual({ top: 320 + 8, left: 40 });
  });

  it('mete la caja en el ancho visible por los dos lados', () => {
    expect(colocarEvitando({ ...base, izquierda: -50 }).left).toBe(8);
    expect(colocarEvitando({ ...base, izquierda: 340 }).left).toBe(360 - 200 - 8);
  });

  it('si el sitio de siempre corta una caja, pasa al otro lado del ancla', () => {
    const etiqueta = caja(20, 250, 300, 30);
    const sitio = colocarEvitando({ ...base, evitar: [etiqueta] });
    expect(sitio.top).toBe(320 + 8);
    expect(cortan(caja(sitio.left, sitio.top, 200, 30), etiqueta)).toBeFalse();
  });

  it('si el otro lado tampoco cabe en la vista, se desplaza lo justo y sin tapar el ancla', () => {
    // Ancla casi arriba del todo: sobre ella no cabe; bajo ella hay otra caja.
    const ancla = caja(40, 20, 100, 20);
    const etiqueta = caja(20, 0, 300, 18);
    const debajo = caja(20, 48, 300, 40);
    const sitio = colocarEvitando({ ...base, ancla, evitar: [etiqueta, debajo] });
    const mia = caja(sitio.left, sitio.top, 200, 30);
    expect(cortan(mia, etiqueta)).toBeFalse();
    expect(cortan(mia, debajo)).toBeFalse();
    expect(cortan(mia, ancla)).toBeFalse();
    expect(sitio.top).toBeGreaterThanOrEqual(4);
    expect(sitio.top + 30).toBeLessThanOrEqual(640 - 4);
  });

  it('encajarEnVista deja lo que cabe, sube lo que se sale por abajo y enseña el principio de lo más alto que la vista', () => {
    expect(encajarEnVista(100, 200, 640)).toBe(100);
    expect(encajarEnVista(500, 200, 640)).toBe(436);
    expect(encajarEnVista(-30, 200, 640)).toBe(4);
    expect(encajarEnVista(300, 900, 640)).toBe(4);
  });

  it('con la zona del panel (vista que empieza más abajo): encajarEnVista y colocarEvitando no se salen de ella', () => {
    // Zona de 200 a 600 en la vista.
    expect(encajarEnVista(100, 150, 400, 200)).toBe(204);
    expect(encajarEnVista(500, 150, 400, 200)).toBe(446);
    expect(encajarEnVista(300, 150, 400, 200)).toBe(300);
    // Sobre el ancla quedaría por encima de la zona: pasa a bajo él.
    const sitio = colocarEvitando({ ...base, ancla: caja(40, 210, 100, 20), vista: { ancho: 360, alto: 400, arriba: 200 }, evitar: [caja(0, 150, 360, 50)] });
    expect(sitio.top).toBe(230 + 8);
  });
});

/** La página «Una sede» con lo justo para que el campo del rótulo y el de la leyenda tengan maquetación real. */
const SEDE = `
<div class="lc">
  <header class="cabecera"><div class="envoltura">
    <nav class="nav"><ul class="nav-lista"><li><a href="/?vista-previa=linea-comarcal#linea">Horarios</a></li></ul></nav>
    <a class="boton cabecera-cta" href="/preinscripcion">Preinscribirme</a>
    <button class="menu-boton" type="button"><svg aria-hidden="true"></svg><span class="menu-texto">Menú</span></button>
  </div></header>
  <main id="principal" tabindex="-1"><div class="p-sede">
  <section class="sede-portada"><div class="envoltura sede-rejilla"><div class="sede-texto">
    <h1 class="sede-titular">Herrera</h1>
    <div class="acciones"><a class="boton" href="/preinscripcion" aria-label="Preinscribirme en Herrera">Preinscribirme</a><a class="enlace-flecha" href="#horarios">Ver grupos y horas</a></div>
  </div></div></section>
  <section class="seccion seccion--anden" id="horarios"><div class="envoltura">
    <div class="cabeza-seccion"><p class="rotulo">Horarios · curso 2026-2027</p><h2 class="titulo-seccion" id="horarios-titulo">Grupos y horas</h2></div>
    <div class="tablero">
      <ul class="leyenda"><li><svg aria-hidden="true"></svg>Quedan plazas</li><li><svg aria-hidden="true"></svg>Lleno: puedes apuntarte a la lista de espera</li><li><svg aria-hidden="true"></svg>Ya hay lista de espera</li></ul>
    </div>
  </div></section>
  <section class="seccion" id="llegar"><div class="envoltura llegar"><div class="llegar-texto">
    <div class="cabeza-seccion"><p class="rotulo">Dirección y contacto</p><h2 class="titulo-seccion" id="llegar-titulo">Cómo llegar</h2></div>
  </div></div></section>
  </div></main>
</div>`;

describe('flotantes de la capa sin pisarse (maquetación real)', () => {
  let iframe: HTMLIFrameElement;
  let ventana: Window;
  let doc: Document;
  let desactivar: () => void;

  function montar(ancho: number, estilo = ''): void {
    iframe = document.createElement('iframe');
    iframe.style.cssText = `width:${ancho}px;max-width:none;height:640px;border:0;position:fixed;left:0;top:0`;
    document.body.appendChild(iframe);
    ventana = iframe.contentWindow!;
    doc = iframe.contentDocument!;
    const tema = doc.createElement('style');
    tema.textContent =
      'body{margin:0;font:16px/1.4 sans-serif;padding-bottom:1600px}.envoltura{padding:0 16px}.seccion{padding:40px 0}' +
      '.cabeza-seccion{margin-bottom:16px}.rotulo,.titulo-seccion{margin:0 0 6px}.leyenda{margin:0;padding:0;list-style:none}' +
      '.cabecera{padding:12px 0}.leyenda li{margin:6px 0}' +
      estilo;
    doc.head.appendChild(tema);
    doc.body.innerHTML = SEDE;
    spyOn(window, 'postMessage');
    desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: () => undefined });
    // El panel marca el documento como estrecho por debajo de 768 px: el nombre del bloque siempre a la vista, sin barra de acciones.
    doc.documentElement.classList.toggle('estrecho', ancho < 768);
  }

  afterEach(() => {
    desactivar();
    iframe.remove();
  });

  const q = (selector: string) => doc.querySelector<HTMLElement>(selector)!;
  const rect = (el: Element): Caja => {
    const r = el.getBoundingClientRect();
    return { left: r.left, top: r.top, right: r.right, bottom: r.bottom };
  };
  const editar = (el: HTMLElement) => el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
  const ficha = () => q('.ed-campo__texto .ed-ficha');
  const mostrarCandado = () => ficha().dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
  /** Pone el elemento a `y` px del borde superior de la vista. */
  function ponerEn(el: HTMLElement, y: number): void {
    ventana.scrollTo(0, Math.max(0, el.getBoundingClientRect().top + ventana.scrollY - y));
  }
  function dentroDelAncho(c: Caja, ancho: number): void {
    expect(c.left).toBeGreaterThanOrEqual(8 - 0.5);
    expect(c.right).toBeLessThanOrEqual(ancho - 8 + 0.5);
  }
  function sinPisarse(ancho: number): void {
    const chip = rect(q('.ed-chip'));
    const candado = rect(q('#ed-candado'));
    expect(cortan(chip, candado)).withContext('etiqueta del campo y aviso del candado').toBeFalse();
    doc.querySelectorAll<HTMLElement>('.ed-nombre').forEach((n) => {
      const c = rect(n);
      if (c.right > c.left && ventana.getComputedStyle(n).opacity !== '0') {
        expect(cortan(chip, c)).withContext('etiqueta del campo y nombre del bloque').toBeFalse();
        expect(cortan(candado, c)).withContext('aviso del candado y nombre del bloque').toBeFalse();
      }
    });
    dentroDelAncho(chip, ancho);
    dentroDelAncho(candado, ancho);
  }

  describe('a 360 px', () => {
    it('el caso del propietario: etiqueta del campo y aviso del candado de su ficha no se cortan', () => {
      montar(360);
      const rotulo = q('#horarios .rotulo');
      ponerEn(rotulo, 300);
      editar(rotulo);
      mostrarCandado();
      expect(q('#ed-candado').hidden).toBeFalse();
      expect(q('.ed-chip').hidden).toBeFalse();
      sinPisarse(360);
    });

    it('ficha al principio del texto (borde izquierdo)', () => {
      montar(360);
      const li = q('.leyenda li:nth-child(2)');
      ponerEn(li, 300);
      editar(li);
      expect(ficha().dataset['token']).toBe('{x}');
      mostrarCandado();
      // Su aviso lleva un texto largo que no cabe en 360 px de una línea: se parte en varias y queda dentro de [8, 352].
      sinPisarse(360);
      expect(Math.round(rect(q('#ed-candado')).left)).toBe(8);
      const candado = q('#ed-candado');
      expect(candado.offsetHeight).toBeGreaterThan(40);
      // El candado no se encoge ni se separa del texto.
      const icono = candado.querySelector('svg')!.getBoundingClientRect();
      expect(Math.round(icono.width)).toBe(Math.round(parseFloat(ventana.getComputedStyle(candado.querySelector('svg')!).width)));
      expect(icono.width).toBeGreaterThan(0);
    });

    it('el aviso largo se mide ya con su ancho máximo, aunque antes estuviera más a la derecha', () => {
      montar(360, '.cabeza-seccion{text-align:right}');
      const rotulo = q('#horarios .rotulo');
      ponerEn(rotulo, 300);
      editar(rotulo);
      mostrarCandado();
      const corto = rect(q('#ed-candado'));
      expect(corto.left).toBeGreaterThan(100);
      // Del aviso corto (a la derecha) al largo: no se queda estrecho por medirse donde estaba el anterior.
      const li = q('.leyenda li:nth-child(2)');
      editar(li);
      mostrarCandado();
      const largo = rect(q('#ed-candado'));
      dentroDelAncho(largo, 360);
      expect(largo.right - largo.left).toBeGreaterThan(300);
    });

    it('ficha al final del texto, con el texto pegado a la derecha (borde derecho)', () => {
      montar(360, '.cabeza-seccion{text-align:right}');
      const rotulo = q('#horarios .rotulo');
      ponerEn(rotulo, 300);
      editar(rotulo);
      mostrarCandado();
      sinPisarse(360);
      expect(rect(q('#ed-candado')).right).toBeLessThanOrEqual(360 - 8 + 0.5);
    });

    it('campo pegado al borde superior de la vista: no cabe arriba y no se pisan ni se salen', () => {
      montar(360);
      const rotulo = q('#horarios .rotulo');
      ponerEn(rotulo, 6);
      editar(rotulo);
      mostrarCandado();
      sinPisarse(360);
      const chip = rect(q('.ed-chip'));
      const candado = rect(q('#ed-candado'));
      expect(candado.bottom).toBeLessThanOrEqual(640);
      expect(Math.min(chip.top, candado.top)).toBeGreaterThanOrEqual(-60);
    });

    it('un campo lejos del nombre del bloque: la etiqueta sigue sobre el campo', () => {
      montar(360);
      const li = q('.leyenda li:nth-child(1)');
      ponerEn(li, 300);
      editar(li);
      const campo = rect(q('.ed-campo__texto'));
      const chip = rect(q('.ed-chip'));
      expect(chip.bottom).toBeLessThanOrEqual(campo.top);
      dentroDelAncho(chip, 360);
    });
  });

  describe('con el aviso de error del bloque', () => {
    it('la etiqueta del campo, bajo el campo, no pisa el .ed-error del bloque', () => {
      // El botón de la cabecera, arriba del todo: arriba no cabe en la página y la etiqueta va bajo el campo.
      montar(1200, '.cabecera-cta{position:absolute;top:8px;left:40px}');
      editar(q('.cabecera-cta'));
      const campo = q('.ed-campo__texto');
      const bloque = campo.closest('.ed-bloque') as HTMLElement | null;
      expect(bloque).not.toBeNull();
      const chip = q('.ed-chip');
      const sola = rect(chip);
      expect(sola.top).withContext('bajo el campo').toBeGreaterThanOrEqual(rect(campo).bottom);
      // Un aviso de error justo donde estaba la etiqueta.
      const error = doc.createElement('p');
      error.className = 'ed ed-error';
      error.textContent = 'Es obligatorio.';
      error.style.cssText = `position:absolute;margin:0;left:${sola.left + ventana.scrollX}px;top:${sola.top + ventana.scrollY}px;width:${sola.right - sola.left}px;height:${sola.bottom - sola.top}px`;
      bloque!.appendChild(error);
      expect(cortan(rect(error), sola)).toBeTrue();
      campo.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText' }));
      expect(chip.hidden).toBeFalse();
      expect(cortan(rect(chip), rect(error))).withContext('etiqueta y aviso de error').toBeFalse();
    });
  });

  describe('a 1200 px', () => {
    it('la colocación de siempre: etiqueta a 14 px sobre el campo y a su derecha', () => {
      // El campo lejos de la barra del bloque: nada que evitar, todo donde siempre.
      montar(1200, '.tablero{padding-top:200px}');
      const li = q('.leyenda li:nth-child(2)');
      ponerEn(li, 300);
      editar(li);
      const campo = rect(q('.ed-campo__texto'));
      const chip = rect(q('.ed-chip'));
      // Con tolerancia de 1 px: las medidas con decimales redondean distinto según las fuentes del sistema (CI en Linux).
      expect(Math.abs(campo.top - chip.bottom - 14)).toBeLessThan(1);
      expect(Math.abs(chip.right - (campo.right + 4))).toBeLessThan(1);
    });

    it('la colocación de siempre: aviso a 8 px sobre la ficha y a su izquierda cuando no hay etiqueta que pise', () => {
      montar(1200, '.tablero{padding-top:200px}');
      const li = q('.leyenda li:nth-child(2)');
      ponerEn(li, 300);
      editar(li);
      q('.ed-chip').hidden = true;
      mostrarCandado();
      const f = rect(ficha());
      const candado = rect(q('#ed-candado'));
      expect(Math.abs(f.top - candado.bottom - 8)).toBeLessThan(1);
      expect(Math.abs(candado.left - f.left)).toBeLessThan(1);
    });

    it('el mismo caso del propietario, con la etiqueta a la vista: tampoco se pisan', () => {
      montar(1200, '.tablero{padding-top:200px}');
      const li = q('.leyenda li:nth-child(2)');
      ponerEn(li, 300);
      editar(li);
      mostrarCandado();
      expect(cortan(rect(q('.ed-chip')), rect(q('#ed-candado')))).toBeFalse();
      // En ancho, el aviso largo sigue en una línea, como siempre.
      expect(q('#ed-candado').offsetHeight).toBeLessThan(40);
    });

    it('con la zona del panel empezando justo sobre el campo: etiqueta y aviso bajan dentro de ella, sin pisarse', () => {
      montar(1200, '.tablero{padding-top:200px}');
      const li = q('.leyenda li:nth-child(2)');
      ponerEn(li, 300);
      // Lo que tapa el panel (cabecera, franja) acaba 10 px sobre el campo: arriba no cabe ni la etiqueta ni el aviso.
      const arriba = Math.round(li.getBoundingClientRect().top) - 10;
      ventana.dispatchEvent(
        new MessageEvent('message', {
          data: { canal: CANAL_EDITOR_WEB, tipo: 'zona-visible', top: arriba + ventana.scrollY, alto: 300 },
          origin: ventana.origin,
          source: window,
        }),
      );
      editar(li);
      mostrarCandado();
      const chip = rect(q('.ed-chip'));
      const candado = rect(q('#ed-candado'));
      for (const [nombre, c] of [['etiqueta', chip], ['aviso', candado]] as const) {
        expect(c.top).withContext(nombre).toBeGreaterThanOrEqual(arriba + 4 - 1);
        expect(c.bottom).withContext(nombre).toBeLessThanOrEqual(arriba + 300 - 4 + 1);
      }
      expect(cortan(chip, candado)).toBeFalse();
    });
  });
});
