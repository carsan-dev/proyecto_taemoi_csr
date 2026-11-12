import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { CANAL_EDITOR_WEB } from '../../../web-plantilla/editor/puente-editor';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import { BloqueContenido, ContenidoWeb, ORIGEN_EDITOR, VISOR_VISUAL } from './web-contenido-vista';
import { WebContenidoComponent } from './web-contenido.component';

const URL = `${environment.apiUrl}/escuela/web`;
const URL_CONTENIDO = `${URL}/contenido`;
const URL_PUBLICA = `${environment.apiUrl}/publico/web`;
const ORIGEN = 'about:blank#';
const PREVIA = '?vista-previa=linea-comarcal&editor=1';
const ESTADO: EstadoWebEscuela = { tema: 'linea-comarcal', publicada: true, publicadaEn: '2026-11-01T10:00:00Z', temasDisponibles: ['linea-comarcal'] };

const fila = (clave: string, texto: string | null, extra: Partial<BloqueContenido> = {}): BloqueContenido => ({
  clave,
  texto,
  imagenId: null,
  imagenUrl: null,
  orden: 0,
  oculto: false,
  permiso: null,
  ...extra,
});
/** Lo guardado: filas de Inicio, fotos, bloques (W12e, W12f-1), un profesor con su constancia y textos de otras páginas. */
const BLOQUES: BloqueContenido[] = [
  fila('portada.titular', 'Taekwondo en seis pueblos'),
  fila('portada.foto', 'Niños en fila', { imagenId: 7, imagenUrl: 'https://x/p.webp' }),
  fila('texto_foto.3', null, { orden: 4 }),
  fila('texto_foto.3.titulo', 'Nuestra sala'),
  fila('texto_foto.3.foto', 'La sala', { imagenId: 9, imagenUrl: 'https://x/s.webp' }),
  fila('galeria', null, { orden: 5, oculto: true }),
  fila('profesor.1.nombre', 'Ana Ruiz', { permiso: { origen: 'ALTA', por: null, en: '2026-10-01T10:00:00' } }),
  fila('sede.anterior', 'Antes'),
  fila('legal.titulo', 'Lo legal'),
];
const CONTENIDO: ContenidoWeb = { version: 3, bloques: BLOQUES };

/**
 * W12d-bis: los textos de las otras seis páginas en el panel. El guardado sigue siendo un reemplazo completo; el
 * resumen «Faltan N datos» lleva a la página, al caso y al paso del dato; el selector «Página» cuenta los cambios de
 * cada una; y quitar la ficha de un dato pide confirmación con el diálogo del panel.
 */
describe('WebContenidoComponent: textos de las otras páginas (W12d-bis)', () => {
  let fixture: ComponentFixture<WebContenidoComponent>;
  let http: HttpTestingController;
  let confirmar: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: ORIGEN_EDITOR, useValue: ORIGEN }, { provide: VISOR_VISUAL, useValue: null }],
    });
    spyOnProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap').and.returnValue(convertToParamMap({}));
    http = TestBed.inject(HttpTestingController);
    spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    confirmar = spyOn(TestBed.inject(ConfirmacionService), 'confirmar').and.resolveTo({ confirmado: false } as never);
  });

  afterEach(() => {
    fixture?.destroy();
    http.verify();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement = HTMLElement>(selector: string) => el().querySelector<T>(selector);
  const texto = (selector: string) => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const iframe = () => q<HTMLIFrameElement>('iframe');
  const src = () => iframe()?.getAttribute('src') ?? null;
  const boton = (t: string) => Array.from(el().querySelectorAll<HTMLButtonElement>('.barra button')).find((b) => b.textContent!.trim() === t)!;

  function abrir(contenido: ContenidoWeb = CONTENIDO): void {
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(ESTADO);
    http.expectOne(URL_CONTENIDO).flush(contenido);
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes: [{ nombre: 'Herrera', slug: 'herrera' }, { nombre: 'Estepa', slug: 'estepa' }] });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
    fixture.detectChanges();
  }

  function elegir(valor: string): void {
    const select = q<HTMLSelectElement>('#web-contenido-pagina')!;
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function llegar(data: unknown): void {
    window.dispatchEvent(new MessageEvent('message', { data, origin: location.origin, source: iframe()!.contentWindow }));
    fixture.detectChanges();
  }
  const cambiar = (clave: string, t: string) => llegar({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave, texto: t });
  const alIframe = (): jasmine.Spy => spyOn(iframe()!.contentWindow!, 'postMessage') as jasmine.Spy;
  const enviados = (espia: jasmine.Spy) => espia.calls.allArgs().map(([m]) => m as { tipo: string });

  it('guardar desde otra página: PUT con todas las filas (fotos, bloques, otras páginas), sin la constancia del permiso', () => {
    abrir();
    elegir('preinscripcion');
    cambiar('pre.atras', 'Volver');
    cambiar('sede.anterior', 'Anterior');
    boton('Guardar').click();
    fixture.detectChanges();
    const peticion = http.expectOne((r) => r.method === 'PUT' && r.url === URL_CONTENIDO);
    expect(peticion.request.body).toEqual({
      version: 3,
      bloques: [
        { clave: 'portada.titular', texto: 'Taekwondo en seis pueblos', imagenId: null, orden: 0, oculto: false },
        { clave: 'portada.foto', texto: 'Niños en fila', imagenId: 7, orden: 0, oculto: false },
        { clave: 'texto_foto.3', texto: null, imagenId: null, orden: 4, oculto: false },
        { clave: 'texto_foto.3.titulo', texto: 'Nuestra sala', imagenId: null, orden: 0, oculto: false },
        { clave: 'texto_foto.3.foto', texto: 'La sala', imagenId: 9, orden: 0, oculto: false },
        { clave: 'galeria', texto: null, imagenId: null, orden: 5, oculto: true },
        { clave: 'profesor.1.nombre', texto: 'Ana Ruiz', imagenId: null, orden: 0, oculto: false },
        // «Anterior» es el texto del tema: no se manda (vuelve al de por defecto).
        { clave: 'legal.titulo', texto: 'Lo legal', imagenId: null, orden: 0, oculto: false },
        { clave: 'pre.atras', texto: 'Volver', imagenId: null, orden: 0, oculto: false },
      ],
    });
    expect(peticion.request.body.bloques.some((b: object) => 'permiso' in b)).toBeFalse();
    peticion.flush({ version: 4, bloques: BLOQUES });
  });

  it('guardar conserva intacta una fila guardada que el editor no ofrece (legal.priv.h1)', () => {
    const noOfrecida = fila('legal.priv.h1', 'Qué datos tratamos', { orden: 2, oculto: true });
    abrir({ version: 3, bloques: [...BLOQUES, noOfrecida] });
    cambiar('pre.atras', 'Volver');
    boton('Guardar').click();
    fixture.detectChanges();
    const peticion = http.expectOne((r) => r.method === 'PUT' && r.url === URL_CONTENIDO);
    expect(peticion.request.body.bloques).toContain({ clave: 'legal.priv.h1', texto: 'Qué datos tratamos', imagenId: null, orden: 2, oculto: true });
    peticion.flush({ version: 4, bloques: BLOQUES });
  });

  it('el selector «Página» cuenta los cambios de cada página (los de la cabecera y el pie, en Inicio)', () => {
    abrir();
    cambiar('sede.titulo_horarios', 'Clases');
    cambiar('sede.estas_aqui', 'Aquí');
    cambiar('pre.continuar', 'Seguir');
    cambiar('menu.horarios', 'Horas');
    const opcion = (v: string) => texto(`#web-contenido-pagina option[value="${v}"]`);
    expect(opcion('sede')).toBe('Una sede · 2 cambios');
    expect(opcion('preinscripcion')).toBe('Preinscripción · 1 cambio');
    expect(opcion('inicio')).toBe('Inicio, con cabecera y pie · 1 cambio');
    expect(opcion('tarifas')).toBe('Tarifas');
  });

  it('resumen: el dato de otra página lleva su nombre y la página; el enlace cambia de página y va al dato', () => {
    abrir();
    cambiar('sede.anterior', '');
    boton('Guardar').click();
    fixture.detectChanges();
    http.expectNone((r) => r.method === 'PUT');
    expect(Array.from(el().querySelectorAll('.resumen a')).map((a) => a.textContent!.trim())).toEqual([
      'Una sede · Cartel de la sede: rótulo de la parada anterior «Anterior»',
    ]);
    q<HTMLAnchorElement>('.resumen a[data-ir="sede.anterior"]')!.click();
    fixture.detectChanges();
    expect(src()).toBe(`${ORIGEN}/sedes/herrera${PREVIA}`);
    const espia = alIframe();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
    expect(enviados(espia).map((m) => m.tipo)).toContain('ir-texto');
    expect(enviados(espia).find((m) => m.tipo === 'ir-texto')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'sede.anterior' } as never);
    expect(enviados(espia).some((m) => m.tipo === 'caso')).toBeFalse();
  });

  it('resumen: un texto de un caso de la preinscripción va a su página, su caso y su paso (y luego al dato)', () => {
    abrir();
    cambiar('pre.fin_espera', '');
    boton('Guardar').click();
    fixture.detectChanges();
    expect(texto('.resumen a')).toBe('Preinscripción · Preinscripción: titular «Estás en la lista de espera»');
    q<HTMLAnchorElement>('.resumen a')!.click();
    fixture.detectChanges();
    expect(src()).toBe(`${ORIGEN}/preinscripcion${PREVIA}`);
    const espia = alIframe();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
    const tipos = enviados(espia);
    const iPaso = tipos.findIndex((m) => m.tipo === 'paso');
    const iCaso = tipos.findIndex((m) => m.tipo === 'caso');
    const iIr = tipos.findIndex((m) => m.tipo === 'ir-texto');
    expect(tipos[iPaso]).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 4 } as never);
    expect(tipos[iCaso]).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'fin-espera' } as never);
    expect(iPaso).toBeLessThan(iIr);
    expect(iCaso).toBeLessThan(iIr);
    expect(texto('#web-contenido-sub option:checked')).toBe('Enviada');
  });

  it('resumen en la misma página: un texto del paso 3 sin caso quita el caso que se ve y cambia de paso', () => {
    abrir();
    elegir('preinscripcion');
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
    // «En la lista de espera» (paso 4).
    q<HTMLButtonElement>('.ver-caso')!.click();
    fixture.detectChanges();
    Array.from(el().querySelectorAll<HTMLButtonElement>('.flot--casos [role="menuitemradio"]'))
      .find((b) => b.textContent!.includes('En la lista de espera'))!
      .click();
    fixture.detectChanges();
    cambiar('pre.enviar', '');
    boton('Guardar').click();
    fixture.detectChanges();
    const espia = alIframe();
    q<HTMLAnchorElement>('.resumen a[data-ir="pre.enviar"]')!.click();
    fixture.detectChanges();
    expect(enviados(espia)).toEqual([
      { canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: null },
      { canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 3 },
      { canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'pre.enviar' },
    ] as never);
  });

  it('quitar una ficha: el diálogo del panel con el texto aprobado; «Cancelar» y «Quitar dato» vuelven a la capa', fakeAsync(() => {
    abrir();
    elegir('tarifas');
    const espia = alIframe();
    llegar({
      canal: CANAL_EDITOR_WEB,
      tipo: 'pedir-quitar-dato',
      clave: 'tarifas.leyenda_cuadro',
      huecos: ['x'],
      antes: 'Mensualidades del curso 2026-2027',
      despues: 'Mensualidades del curso',
    });
    flushMicrotasks();
    expect(confirmar).toHaveBeenCalledOnceWith({
      titulo: '¿Quitar «curso» de este texto?',
      mensaje:
        'En tu web, la frase saldrá sin el curso. Hoy se ve así: «Mensualidades del curso 2026-2027» y pasará a verse así: «Mensualidades del curso»',
      accion: 'Quitar dato',
      cancelar: 'Cancelar',
      destructivo: true,
      nombre: 'quitar-dato',
    });
    expect(enviados(espia)).toEqual([{ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: false }] as never);
    expect(document.activeElement).toBe(iframe());

    confirmar.and.resolveTo({ confirmado: true } as never);
    llegar({
      canal: CANAL_EDITOR_WEB,
      tipo: 'pedir-quitar-dato',
      clave: 'pre.estado_plaza',
      huecos: ['grupo', 'sede'],
      antes: 'a',
      despues: 'b',
    });
    flushMicrotasks();
    expect(confirmar.calls.mostRecent().args[0].titulo).toBe('¿Quitar «grupo elegido» y «sede elegida» de este texto?');
    expect(enviados(espia)[1]).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: true } as never);
  }));

  it('un hueco que no es de ese texto no abre el diálogo (el puente lo descarta)', fakeAsync(() => {
    abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'pedir-quitar-dato', clave: 'tarifas.titulo', huecos: ['x'], antes: 'a', despues: 'b' });
    flushMicrotasks();
    expect(confirmar).not.toHaveBeenCalled();
  }));
});
