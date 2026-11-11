import { sitioConSedes } from '../datos/datos-de-prueba';
import { EventoDetalleWeb, EventosWeb, SitioWeb } from '../datos/modelos';
import { paginaDelDocumento } from './candados-linea-comarcal';
import { activarCapaEditor } from './capa-editor';
import { SimuladorCaso, simuladorDe } from './casos-vista';
import { CANAL_EDITOR_WEB } from './puente-editor';

/** Cabecera y pie del marco, comunes a todas las páginas. */
const marco = (pagina: string) => `
<div class="lc">
  <header class="cabecera"><div class="envoltura"><a class="marca" href="/"><span class="logo">TS</span><span class="marca-nombre">Taekwondo Sierra Sur</span></a></div></header>
  <main id="principal">${pagina}</main>
  <footer class="pie"><div class="envoltura"><p class="pie-marca">Taekwondo Sierra Sur</p><div class="pie-fila"><p>© 2026 Club</p></div></div></footer>
</div>`;

/** Lo justo del DOM de cada página interior de «La línea comarcal» (clases de sus plantillas). */
const PAGINAS: Record<string, string> = {
  sede: `<div class="p-sede">
    <section class="sede-portada"><div class="envoltura sede-rejilla"><div class="sede-texto">
      <nav class="tramo"><a class="tramo-parada" href="/sedes/estepa"><span class="tramo-nombre">Estepa</span></a>
        <p class="tramo-parada tramo-parada--actual"><span class="tramo-nombre">Parada 2 de 6</span></p></nav>
      <h1 class="sede-titular">Herrera</h1><p class="sede-lugar"><strong>Pabellón</strong><span>Avda. Constitución</span></p>
      <div class="acciones"><a class="boton" href="/preinscripcion">Preinscribirme en Herrera</a></div></div>
      <figure class="foto sede-foto"><img alt=""></figure></div></section>
    <section class="seccion" id="horarios"><div class="tablero"><p class="proxima"><span class="proxima-rotulo">Próxima clase</span><span class="proxima-cuando">Lunes <span class="hora">17:00</span></span></p>
      <ol class="salidas"><li class="salida"><p class="salida-hora">17:00</p><div class="salida-grupo"><p class="grupo-nombre">Peques</p></div>
      <div class="salida-detalle"><p class="dias-p">L X</p><p class="plazas plazas--quedan">Quedan 3</p></div></li></ol></div></section>
    <section class="seccion" id="llegar"><dl class="ficha"><div class="ficha-fila"><dt>Lugar</dt><dd data-dato="lugar">Pabellón</dd></div>
      <div class="ficha-fila"><dt>Entrada</dt><dd data-dato="acceso">Puerta lateral</dd></div>
      <div class="ficha-fila"><dt>Teléfono</dt><dd data-dato="telefono"><a class="cifras" href="tel:955">955</a></dd></div></dl></section>
    <section class="tarifas" id="tarifas"><p class="tarifa-cifra"><span class="tarifa-importe">25 €</span></p></section>
    <section class="seccion" id="sedes"><ol class="ruta"><li class="ruta-sede"><a class="ruta-nombre" href="/sedes/estepa">Estepa</a><span class="ruta-dir">C/ Deporte</span></li>
      <li class="ruta-sede ruta-sede--actual"><span class="ruta-nombre">Herrera</span></li></ol></section>
  </div>`,
  tarifas: `<div class="p-tarifas">
    <section class="tarifas" id="tarifas"><h1 class="rotulo">Tarifas</h1><p class="tarifa-cifra"><span class="tarifa-importe">25 €</span></p></section>
    <section class="seccion" id="cuadro"><table class="cuadro"><tbody><tr><th scope="row">General</th><td class="quien">Cada alumno</td><td class="importe cifras">25 €<small>al mes</small></td></tr></tbody></table></section>
    <section class="seccion" id="por-sede"><ol class="linea-tarifa"><li class="lt-parada"><div class="lt-cabeza"><a class="lt-nombre" href="/sedes/herrera">Herrera</a><p class="lt-importe">25 €</p></div></li></ol></section>
    <section class="seccion" id="aparte"><ul class="aparte"><li><span class="aparte-concepto">Licencia</span><span class="aparte-precio" data-dato="licencia">40 €</span></li>
      <li><span class="aparte-concepto">Examen</span><span class="aparte-precio" data-dato="examen">desde 30 €</span></li></ul></section>
  </div>`,
  vacia: `<div class="p-tarifas"><section class="tarifas" id="tarifas"><p class="tarifa-vacio">Pregunta la cuota en tu sede</p>
    <dl class="tarifa-contacto"><div><dt>Correo</dt><dd><a href="mailto:a@b.es">a@b.es</a></dd></div><div><dt>Teléfono</dt><dd><a class="cifras" href="tel:600">600</a></dd></div></dl></section></div>`,
  eventos: `<div class="p-eventos"><div class="envoltura pagina-cabeza"><h1 class="pagina-titular">Eventos</h1></div>
    <section class="proximos" id="proximos"><article class="destacado"><figure class="foto destacado-foto"><img alt=""></figure>
      <div class="destacado-texto"><time class="fecha">14 nov</time><div class="destacado-cuerpo"><h3 class="destacado-titulo">Examen</h3><p class="ev-donde">Estepa</p>
      <p class="destacado-desc">Paso de grado</p><a class="boton" href="/eventos/1">Ver el evento</a></div></div></article>
      <ol class="ev-filas"><li class="ev-fila"><time class="fecha">29 nov</time><div class="ev-texto"><h3 class="ev-titulo">Torneo</h3></div></li></ol></section>
    <section class="seccion" id="anteriores"><ul class="ev-rejilla"><li class="ev-mini"><a href="/eventos/2"><h4 class="ev-mini-titulo">Exhibición</h4></a></li></ul></section>
  </div>`,
  evento: `<article class="p-evento"><div class="envoltura evento-cabeza"><a class="atras" href="/eventos">Volver</a>
      <div class="evento-rejilla-cabeza"><div class="evento-rotulo"><time class="fecha">14 nov</time></div><h1 class="evento-titular">Examen</h1></div></div>
    <figure class="foto evento-foto"><img alt=""></figure>
    <div class="envoltura evento-cuerpo"><p class="evento-desc">Paso de grado</p><section class="evento-docs"><ul class="docs"><li><a href="/doc">Circular</a></li></ul></section></div>
    <section class="seccion" id="galeria"><ol class="rejilla-fotos"><li><button class="ampliar" type="button"><img alt=""></button></li></ol></section>
  </article>`,
  legales: `<div class="p-legales"><div class="envoltura legal-cabeza"><h1 class="legal-titular">Legales</h1><p class="actualizado">Última actualización: <time>11 de noviembre de 2026</time></p></div>
    <div class="envoltura legal-rejilla"><div class="legal-texto"><section class="legal-seccion"><dl class="ficha">
      <div class="ficha-fila"><dt>Titular</dt><dd data-dato="titular">Club</dd></div><div class="ficha-fila"><dt>CIF</dt><dd data-dato="cif"><span class="cifras">G0</span></dd></div>
      <div class="ficha-fila"><dt>Domicilio</dt><dd data-dato="domicilio">C/ Deporte</dd></div><div class="ficha-fila"><dt>Correo</dt><dd data-dato="correo"><a href="mailto:a@b.es">a@b.es</a></dd></div></dl></section>
      <section class="legal-seccion" id="normas"><ol class="normas-lista"><li><span>Respeto</span></li></ol></section></div></div>
  </div>`,
  preinscripcion: `<div class="p-preinscripcion"><div class="flujo"><div class="envoltura flujo-rejilla"><div class="flujo-principal">
    <ol class="selector-linea"><li class="sel"><label><input type="radio" name="sede"><span class="sel-nombre">Herrera</span><span class="sel-dato">Pabellón · 3 grupos</span></label></li></ol></div>
    <aside class="resumen"><dl><div class="resumen-fila"><dt>Pueblo</dt><dd class="resumen-vacio">Sin elegir</dd></div></dl></aside></div></div></div>`,
};

describe('capa de edición en las otras seis páginas (W12c-bis)', () => {
  let iframe: HTMLIFrameElement;
  let ventana: Window;
  let doc: Document;
  let enviados: jasmine.Spy;
  let simuladores: (SimuladorCaso | null)[];
  let desactivar: () => void;

  function abrir(pagina: string): void {
    iframe = document.createElement('iframe');
    document.body.appendChild(iframe);
    ventana = iframe.contentWindow!;
    doc = iframe.contentDocument!;
    doc.body.innerHTML = marco(PAGINAS[pagina]);
    enviados = spyOn(window, 'postMessage');
    simuladores = [];
    desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: () => undefined, alCaso: (s) => simuladores.push(s) });
  }

  afterEach(() => {
    desactivar();
    iframe.remove();
  });

  const q = (selector: string) => doc.querySelector<HTMLElement>(selector)!;
  const candado = (selector: string) => [q(selector).dataset['que'], q(selector).dataset['donde']];
  const fija = (selector: string) => [q(selector).dataset['bloque'], q(selector).querySelector('.ed-nombre')!.textContent];
  const mensajes = () => enviados.calls.allArgs();

  it('reconoce la página por la raíz del tema; sin ninguna, Inicio', () => {
    abrir('sede');
    expect(paginaDelDocumento(doc)).toBe('sede');
    doc.body.innerHTML = '<div class="lc"><section class="portada"></section></div>';
    expect(paginaDelDocumento(doc)).toBe('inicio');
  });

  it('Una sede: partes fijas con su nombre y candados de Sedes, Grupos y Productos', () => {
    abrir('sede');
    expect(fija('.p-sede > .sede-portada')).toEqual(['sp-cartel', 'Cartel de la sede']);
    expect(fija('.p-sede > #horarios')).toEqual(['sp-horarios', 'Grupos y horas']);
    expect(fija('.p-sede > #llegar')).toEqual(['sp-llegar', 'Cómo llegar']);
    expect(fija('.p-sede > #tarifas')).toEqual(['sp-tarifa', 'Tarifa']);
    expect(fija('.p-sede > #sedes')).toEqual(['sp-ruta', 'Todas las sedes']);
    expect(q('.p-sede > #horarios .ed-nota').textContent).toBe('Su sitio lo pone el tema. Pulsa un texto para cambiarlo.');
    expect(candado('.sede-titular')).toEqual(['Nombre de la sede', 'sedes']);
    expect(candado('.tramo-parada:not(.tramo-parada--actual) .tramo-nombre')).toEqual(['Nombre de la sede', 'sedes']);
    expect(q('.tramo-parada--actual .tramo-nombre').classList).not.toContain('ed-dato');
    expect(candado('.sede-lugar')).toEqual(['Lugar y dirección', 'sedes']);
    expect(candado('.sede-foto')).toEqual(['Foto de la sede', 'sedes']);
    expect(candado('.proxima-cuando')).toEqual(['Próxima clase', 'grupos']);
    expect(candado('.salida-hora')).toEqual(['Horario del grupo', 'grupos']);
    expect(candado('.plazas--quedan')).toEqual(['Plazas libres', 'grupos']);
    expect(candado('[data-dato="acceso"]')).toEqual(['Indicaciones de acceso', 'sedes']);
    expect(candado('[data-dato="telefono"]')).toEqual(['Teléfono de la sede', 'sedes']);
    expect(candado('#tarifas .tarifa-importe')).toEqual(['Importe de la mensualidad', 'productos']);
    expect(candado('.ruta-dir')).toEqual(['Lugar y dirección', 'sedes']);
    // Inicio no se cuela: la ruta de la sede no es «Sede: nombre y dirección».
    expect(q('.ruta-sede').classList).not.toContain('ed-dato');
    // Cabecera y pie, en todas las páginas.
    expect(candado('.logo')).toEqual(['Logo y color', 'equipo']);
    expect(candado('.pie-fila > p')).toEqual(['Nombre legal', 'datos-escuela']);
    expect(fija('.lc > .cabecera')).toEqual(['cabecera', 'Cabecera']);
    // Un enlace oculto por tipo, nunca dentro de un enlace del tema.
    const sede = Array.from(doc.querySelectorAll<HTMLElement>('.ed-dato__ir')).filter((a) => a.dataset['clave'] === 'Nombre de la sede|sedes');
    expect(sede.length).toBe(1);
    expect(sede[0].closest('a.tramo-parada, a.ruta-nombre')).toBeNull();
  });

  it('Tarifas: Productos y, sin tarifas, el correo y el teléfono de la escuela; el examen no (es de W12d)', () => {
    abrir('tarifas');
    expect(fija('.p-tarifas > #tarifas')).toEqual(['tp-banda', 'Cuota']);
    expect(fija('.p-tarifas > #cuadro')).toEqual(['tp-cuadro', 'Cuadro de tarifas']);
    expect(fija('.p-tarifas > #por-sede')).toEqual(['tp-sede', 'Por sede']);
    expect(fija('.p-tarifas > #aparte')).toEqual(['tp-aparte', 'Licencia y exámenes']);
    expect(candado('.tarifa-importe')).toEqual(['Importe de la mensualidad', 'productos']);
    expect(candado('.cuadro th')).toEqual(['Nombre de la tarifa', 'productos']);
    expect(candado('.cuadro td.importe')).toEqual(['Importe de la tarifa', 'productos']);
    expect(candado('.lt-nombre')).toEqual(['Nombre de la sede', 'sedes']);
    expect(candado('.lt-importe')).toEqual(['Mensualidad en la sede', 'productos']);
    expect(candado('[data-dato="licencia"]')).toEqual(['Precio de la licencia', 'productos']);
    expect(q('[data-dato="examen"]').classList).not.toContain('ed-dato');
    desactivar();
    iframe.remove();
    abrir2('vacia');
    expect(candado('.tarifa-contacto dd:has(> a[href^="mailto:"])')).toEqual(['Correo de contacto', 'datos-escuela']);
    expect(candado('.tarifa-contacto dd:has(> a.cifras)')).toEqual(['Teléfono de contacto', 'datos-escuela']);
  });

  /** Segunda página en la misma prueba (el espía de postMessage ya está puesto). */
  function abrir2(pagina: string): void {
    iframe = document.createElement('iframe');
    document.body.appendChild(iframe);
    ventana = iframe.contentWindow!;
    doc = iframe.contentDocument!;
    doc.body.innerHTML = marco(PAGINAS[pagina]);
    desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: () => undefined });
  }

  it('Eventos: fecha, foto, nombre y descripción y anteriores, a Eventos', () => {
    abrir('eventos');
    expect(fija('.p-eventos > .pagina-cabeza')).toEqual(['ep-cabeza', 'Cabeza de la página']);
    expect(fija('.p-eventos > #proximos')).toEqual(['ep-proximos', 'Próximos']);
    expect(fija('.p-eventos > #anteriores')).toEqual(['ep-anteriores', 'Anteriores']);
    expect(candado('.destacado .fecha')).toEqual(['Fecha del evento', 'eventos']);
    expect(candado('.ev-fila .fecha')).toEqual(['Fecha del evento', 'eventos']);
    expect(candado('.destacado-foto')).toEqual(['Foto del evento', 'eventos']);
    expect(candado('.destacado-titulo')).toEqual(['Evento: nombre y descripción', 'eventos']);
    expect(candado('.destacado-desc')).toEqual(['Evento: nombre y descripción', 'eventos']);
    expect(candado('.ev-texto')).toEqual(['Evento: nombre y descripción', 'eventos']);
    expect(candado('.ev-mini')).toEqual(['Evento anterior', 'eventos']);
  });

  it('Un evento: cabeza, descripción, documentos y fotos, a Eventos', () => {
    abrir('evento');
    expect(fija('.p-evento > .evento-cabeza')).toEqual(['ev-cabeza', 'Evento']);
    expect(fija('.p-evento > .evento-cuerpo')).toEqual(['ev-cuerpo', 'Descripción y documentos']);
    expect(fija('.p-evento > #galeria')).toEqual(['ev-galeria', 'Galería del evento']);
    expect(q('.p-evento > #galeria').querySelector('.ed-nombre')!.textContent).not.toBe('Galería');
    expect(candado('.evento-rotulo')).toEqual(['Fecha, hora y lugar del evento', 'eventos']);
    expect(candado('.evento-titular')).toEqual(['Nombre del evento', 'eventos']);
    expect(candado('.evento-foto')).toEqual(['Foto del evento', 'eventos']);
    expect(candado('.evento-desc')).toEqual(['Descripción del evento', 'eventos']);
    expect(candado('.docs')).toEqual(['Documentos del evento', 'eventos']);
    expect(candado('.rejilla-fotos')).toEqual(['Fotos del evento', 'eventos']);
  });

  it('Legales: datos de la escuela, la fecha la pone la web y las normas van a Preinscripciones · Plantillas', () => {
    abrir('legales');
    expect(fija('.p-legales > .legal-cabeza')).toEqual(['lg-cabeza', 'Cabeza de la página']);
    expect(fija('.p-legales > .legal-rejilla')).toEqual(['lg-texto', 'Texto legal']);
    expect(q('.p-legales > .legal-rejilla .ed-nota').textContent).toBe('Su sitio lo pone el tema. El índice repite los títulos de las secciones.');
    expect(candado('.actualizado time')).toEqual(['Fecha de la última publicación', 'web']);
    expect(candado('[data-dato="titular"]')).toEqual(['Nombre legal', 'datos-escuela']);
    expect(candado('[data-dato="cif"]')).toEqual(['CIF', 'datos-escuela']);
    expect(candado('[data-dato="domicilio"]')).toEqual(['Domicilio', 'datos-escuela']);
    expect(candado('[data-dato="correo"]')).toEqual(['Correo de contacto', 'datos-escuela']);
    expect(candado('.normas-lista')).toEqual(['Normas del club', 'plantillas']);
    // «Lo pone la web»: sin enlace oculto.
    expect(q('.actualizado time .ed-dato__ir')).toBeNull();

    const ir = q('.normas-lista .ed-dato__ir');
    expect(ir.textContent).toBe('Normas del club: se cambia en Preinscripciones · Plantillas');
    ir.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    expect(q('#ed-candado').textContent).toBe('Normas del club · Se cambia enPreinscripciones · Plantillas');
    enviados.calls.reset();
    ir.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    expect(mensajes()).toContain([{ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: 'plantillas' }, location.origin]);

    q('.actualizado time').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    expect(q('#ed-candado').textContent).toBe('Fecha de la última publicación · Lo pone la web');
  });

  it('Preinscripción: la parte del flujo y la sede, a Sedes; el resumen sin elegir no lleva candado', () => {
    abrir('preinscripcion');
    expect(fija('.p-preinscripcion > .flujo')).toEqual(['pr-flujo', 'Preinscripción']);
    expect(candado('.sel-nombre')).toEqual(['Sede, grupos y próxima clase', 'sedes']);
    expect(candado('.sel-dato')).toEqual(['Sede, grupos y próxima clase', 'sedes']);
    expect(q('.resumen-vacio').classList).not.toContain('ed-dato');
  });

  describe('«Ver el caso» por el puente', () => {
    const llegar = (data: unknown, source: Window = window, origin = location.origin) =>
      ventana.dispatchEvent(new MessageEvent('message', { data, origin, source }));

    it('entrega el simulador del caso y, al volver, null; ignora un caso desconocido o de otra ventana', () => {
      abrir('eventos');
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'desconocido' });
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'sin-eventos' }, ventana);
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'sin-eventos' }, window, 'https://otra.example');
      expect(simuladores).toEqual([]);
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'sin-eventos' });
      expect(simuladores.length).toBe(1);
      expect(simuladores[0]!.eventos({ proximos: [{} as never], anteriores: [{} as never] })).toEqual({ proximos: [], anteriores: [] });
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: null });
      expect(simuladores[1]).toBeNull();
    });

    it('tras el caso, dice al panel dónde empieza su parte (solo un número)', async () => {
      abrir('eventos');
      enviados.calls.reset();
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'sin-anteriores' });
      await new Promise((r) => setTimeout(r));
      const mostrar = mensajes().filter(([m]) => (m as { tipo: string }).tipo === 'mostrar');
      expect(mostrar.length).toBe(1);
      const [m, origen] = mostrar[0] as [{ arriba: number }, string];
      expect(origen).toBe(location.origin);
      expect(Object.keys(m).sort()).toEqual(['arriba', 'canal', 'tipo']);
      expect(m.arriba).toBe(Math.round(q('[data-bloque="ep-anteriores"]').getBoundingClientRect().top + ventana.scrollY));
    });
  });
});

describe('simulador de los casos (W12c-bis)', () => {
  const eventos = (): EventosWeb => ({
    proximos: [{ id: 1 }, { id: 2 }, { id: 3 }] as EventosWeb['proximos'],
    anteriores: [{ id: 9 }] as EventosWeb['anteriores'],
  });

  it('«Grupo lleno» y «Lista de espera»: todos los grupos y turnos con ese estado, sin tocar el original', () => {
    const sitio: SitioWeb = sitioConSedes(2);
    const antes = JSON.stringify(sitio);
    const lleno = simuladorDe('lleno').sitio(sitio);
    const grupos = lleno.sedes.flatMap((s) => s.grupos);
    expect(grupos.length).toBeGreaterThan(0);
    for (const g of grupos) {
      if (g.plazas) {
        expect(g.plazas).toEqual({ estado: 'LLENO', quedan: null });
      }
      for (const t of g.turnos) {
        expect(t.plazas).toEqual({ estado: 'LLENO', quedan: null });
      }
    }
    const espera = simuladorDe('espera').sitio(sitio);
    expect(espera.sedes.flatMap((s) => s.grupos).flatMap((g) => g.turnos).every((t) => t.plazas.estado === 'LISTA_ESPERA')).toBeTrue();
    expect(JSON.stringify(sitio)).toBe(antes);
  });

  it('«Sin tarifas publicadas»: sin tarifas ni tarifas por sede', () => {
    const sitio = simuladorDe('sin-tarifas').sitio(sitioConSedes(2));
    expect(sitio.tarifas).toEqual([]);
    expect(sitio.tarifasSede).toEqual([]);
  });

  it('casos de Eventos y de Un evento', () => {
    expect(simuladorDe('sin-proximos').eventos(eventos())).toEqual({ proximos: [], anteriores: eventos().anteriores });
    expect(simuladorDe('un-evento').eventos(eventos()).proximos.map((e) => e.id)).toEqual([1]);
    expect(simuladorDe('sin-anteriores').eventos(eventos()).anteriores).toEqual([]);
    const evento = { id: 1, fotos: [{ id: 1 }, { id: 2 }] } as EventoDetalleWeb;
    expect(simuladorDe('una-foto').evento(evento).fotos).toEqual([{ id: 1 }]);
    // Lo que no es de su página no cambia.
    expect(simuladorDe('una-foto').eventos(eventos())).toEqual(eventos());
    expect(simuladorDe('sin-eventos').evento(evento)).toBe(evento);
  });
});
