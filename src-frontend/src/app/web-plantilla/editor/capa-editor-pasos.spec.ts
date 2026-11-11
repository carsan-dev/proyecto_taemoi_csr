import { sitioConSedes } from '../datos/datos-de-prueba';
import { activarCapaEditor } from './capa-editor';
import { PARTE_DEL_CASO, SimuladorCaso } from './casos-vista';
import { CANAL_EDITOR_WEB } from './puente-editor';

const AHORA = new Date(2026, 10, 7, 12, 0);

/** Lo justo del DOM de la preinscripción de «La línea comarcal» en sus pasos 2 a 4 (clases de su plantilla). */
const PREINSCRIPCION = `<div class="lc"><main id="principal"><div class="p-preinscripcion"><div class="flujo"><div class="envoltura flujo-rejilla">
  <div class="flujo-principal">
    <form novalidate><h1 class="pregunta-titulo">¿Qué grupo?</h1>
      <p class="contexto"><span>En <strong>Herrera</strong></span></p>
      <input class="control" id="dia" name="dia">
      <div class="vacio"><h2>En Herrera no hay grupo para 15 años</h2>
        <ul class="otras"><li class="otra"><a href="/preinscripcion?sede=estepa"><span class="otra-nombre">Estepa</span><span class="otra-dato">Jóvenes</span></a></li></ul>
        <p class="vacio-contacto">Llama a Herrera al <a class="cifras" href="tel:+34955">955 000 001</a> y te ayudan.</p></div>
      <canvas class="firma"></canvas>
      <button class="boton" type="submit">Continuar</button>
    </form>
    <section class="banda"><h1 class="pregunta-titulo" id="fin-titulo">Preinscripción enviada</h1><p class="banda-ref">Referencia <strong>TSS-2026-0142</strong></p></section>
  </div>
  <aside class="resumen"><dl>
    <div class="resumen-fila"><dt>Pueblo</dt><dd>Herrera</dd></div>
    <div class="resumen-fila"><dt>Edad</dt><dd>Este año cumple 5 años</dd></div>
    <div class="resumen-fila"><dt>Grupo</dt><dd data-dato="grupo">Peques, 4 a 6 años</dd><dd class="resumen-cambiar"><a href="#">Cambiar</a></dd></div>
    <div class="resumen-fila"><dt>Días</dt><dd data-dato="dias">Lunes y miércoles, 17:00–17:45</dd></div>
  </dl></aside>
</div></div></div></main></div>`;

/**
 * W12c-ter: la capa de edición en la preinscripción (boceto `preinscripcionHtml`): el paso que manda el panel (lista
 * cerrada), su nota y la de los avisos de error, los candados de los pasos 2 a 4 y el formulario inerte (no escribe, no
 * envía).
 */
describe('capa de edición: pasos de la preinscripción (W12c-ter)', () => {
  let iframe: HTMLIFrameElement;
  let ventana: Window;
  let doc: Document;
  let simuladores: (SimuladorCaso | null)[];
  let enviados: jasmine.Spy;
  let desactivar: () => void;

  beforeEach(() => {
    iframe = document.createElement('iframe');
    document.body.appendChild(iframe);
    ventana = iframe.contentWindow!;
    doc = iframe.contentDocument!;
    doc.body.innerHTML = PREINSCRIPCION;
    enviados = spyOn(window, 'postMessage');
    simuladores = [];
    desactivar = activarCapaEditor({ ventana, padre: window, alBorrador: () => undefined, alCaso: (s) => simuladores.push(s) });
  });

  afterEach(() => {
    desactivar();
    iframe.remove();
  });

  const q = (selector: string) => doc.querySelector<HTMLElement>(selector)!;
  const candado = (selector: string) => [q(selector).dataset['que'], q(selector).dataset['donde']];
  const llegar = (data: unknown, source: Window = window, origin = location.origin) =>
    ventana.dispatchEvent(new MessageEvent('message', { data, origin, source }));
  const pasoDe = (s: SimuladorCaso | null) => s?.preinscripcion(sitioConSedes(2), AHORA)?.paso ?? null;

  it('el paso del panel llega al simulador; uno fuera de la lista, de otra ventana o de otro origen se ignora', () => {
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 5 });
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: '3' });
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 3 }, ventana);
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 3 }, window, 'https://otra.example');
    expect(simuladores).toEqual([]);
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 3 });
    expect(simuladores.length).toBe(1);
    expect(pasoDe(simuladores[0])).toBe(3);
  });

  it('el caso y el paso van juntos: el caso cambia los datos y el paso sigue; sin caso, solo el paso', () => {
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 4 });
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'fin-espera' });
    const conCaso = simuladores.at(-1)!;
    expect(conCaso.preinscripcion(sitioConSedes(2), AHORA)!.respuesta!.plazaAsignada).toBeFalse();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: null });
    const sinCaso = simuladores.at(-1)!;
    expect(sinCaso).not.toBeNull();
    expect(sinCaso.preinscripcion(sitioConSedes(2), AHORA)!.respuesta!.plazaAsignada).toBeTrue();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'desconocido' });
    expect(simuladores.length).toBe(3);
  });

  it('la nota de la parte (boceto) y la de los avisos de error, una sola vez aunque el tema vuelva a pintar', async () => {
    expect(q('.p-preinscripcion > .flujo > .ed-barra .ed-nota').textContent).toBe('Su sitio lo pone el tema. Elige el paso arriba para ver sus textos.');
    const nota = () => Array.from(doc.querySelectorAll<HTMLElement>('.flujo-principal > .ed-nota-parte'));
    expect(nota().length).toBe(1);
    expect(nota()[0].textContent).toBe(
      'Los avisos de error del formulario («Escribe el nombre», «El DNI no es válido»…) son los comunes de todas las webs: no se cambian aquí.',
    );
    expect(nota()[0].classList).toContain('ed');
    expect(nota()[0].querySelector('svg')).not.toBeNull();
    expect(nota()[0].parentElement!.lastElementChild).toBe(nota()[0]);
    // El tema pinta otro paso: la nota sigue siendo una.
    q('.flujo-principal').insertBefore(doc.createElement('p'), q('.flujo-principal > form'));
    await new Promise((r) => setTimeout(r));
    expect(nota().length).toBe(1);
  });

  it('candados de los pasos 2 a 4 del boceto: otra sede y teléfono, referencia, grupo y días del resumen', () => {
    expect(candado('.vacio .otra > a')).toEqual(['Sede con grupo para su edad', 'grupos']);
    expect(candado('.vacio-contacto > a.cifras')).toEqual(['Teléfono de la sede', 'sedes']);
    expect(candado('.banda-ref > strong')).toEqual(['Referencia de la solicitud', 'web']);
    expect(candado('.resumen [data-dato="grupo"]')).toEqual(['Grupo elegido', 'grupos']);
    expect(candado('.resumen [data-dato="dias"]')).toEqual(['Días y hora del grupo', 'grupos']);
    expect(q('.resumen-fila:nth-child(2) > dd').classList).not.toContain('ed-dato');
    expect(q('.resumen-cambiar').classList).not.toContain('ed-dato');
  });

  it('el formulario no escribe ni envía: input, change, submit, beforeinput y la firma no llegan al tema', () => {
    const tema = jasmine.createSpy('tema');
    const control = q('#dia');
    const form = q('form');
    const lienzo = q('canvas.firma');
    for (const [el, tipo] of [[control, 'input'], [control, 'change'], [form, 'submit'], [lienzo, 'pointerdown']] as const) {
      el.addEventListener(tipo, tema);
    }
    control.dispatchEvent(new Event('input', { bubbles: true }));
    control.dispatchEvent(new Event('change', { bubbles: true }));
    const envio = new Event('submit', { bubbles: true, cancelable: true });
    form.dispatchEvent(envio);
    lienzo.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true }));
    const escribir = new InputEvent('beforeinput', { bubbles: true, cancelable: true, data: '1', inputType: 'insertText' });
    control.dispatchEvent(escribir);
    expect(tema).not.toHaveBeenCalled();
    expect(envio.defaultPrevented).toBeTrue();
    expect(escribir.defaultPrevented).toBeTrue();
  });

  it('tras «Grupo completo» o un caso de la preinscripción enseña la parte del flujo; «Sin clases esta semana», el tablero de la sede', async () => {
    expect(PARTE_DEL_CASO['sin-clases']).toEqual(['sp-horarios']);
    expect(PARTE_DEL_CASO['sin-grupo']).toEqual(['pr-flujo']);
    expect(PARTE_DEL_CASO['fin-espera']).toEqual(['pr-flujo']);
    enviados.calls.reset();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'espera' });
    await new Promise((r) => setTimeout(r));
    const mostrar = enviados.calls.allArgs().filter(([m]) => (m as { tipo: string }).tipo === 'mostrar');
    expect(mostrar.length).toBe(1);
    expect((mostrar[0][0] as { arriba: number }).arriba).toBe(
      Math.round(q('[data-bloque="pr-flujo"]').getBoundingClientRect().top + ventana.scrollY),
    );
  });
});
