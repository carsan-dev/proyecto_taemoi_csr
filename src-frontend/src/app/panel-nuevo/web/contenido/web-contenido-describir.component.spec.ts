import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { CANAL_EDITOR_WEB } from '../../../web-plantilla/editor/puente-editor';
import { AvisosService } from '../../avisos/avisos.service';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { SALIDA_HOJA_MS, deslizarAsa } from '../../hoja/asa-hoja-prueba';
import { CLIENTE_SUBIDAS } from '../../material-examen/material-examen-api.service';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import { BloqueContenido, ContenidoWeb, ORIGEN_EDITOR, VISOR_VISUAL } from './web-contenido-vista';
import { WebContenidoComponent } from './web-contenido.component';

const URL = `${environment.apiUrl}/escuela/web`;
const URL_CONTENIDO = `${URL}/contenido`;
const URL_PUBLICA = `${environment.apiUrl}/publico/web`;
const PUBLICADA: EstadoWebEscuela = { tema: 'linea-comarcal', publicada: true, publicadaEn: '2026-11-01T10:00:00Z', temasDisponibles: ['linea-comarcal'] };

const fila = (clave: string, texto: string | null = null, extra: Partial<BloqueContenido> = {}): BloqueContenido => ({
  clave,
  texto,
  imagenId: null,
  imagenUrl: null,
  orden: 0,
  oculto: false,
  permiso: null,
  ...extra,
});
const BLOQUES: BloqueContenido[] = [
  fila('portada.titular', 'Taekwondo en seis pueblos'),
  fila('portada.foto', 'Niños en fila', { imagenId: 7, imagenUrl: 'https://x/p.webp' }),
  fila('presentacion', null, { orden: 0 }),
  fila('texto_foto.2', null, { orden: 1 }),
  fila('texto_foto.2.titulo', 'Qué traer'),
];
const CONTENIDO: ContenidoWeb = { version: 5, bloques: BLOQUES };

/** «Describir la foto» como hoja inferior del panel (panel < 768 px; boceto `abrirDescribir` y `.flot` en estrecho). */
describe('WebContenidoComponent: «Describir la foto» como hoja', () => {
  let fixture: ComponentFixture<WebContenidoComponent>;
  let http: HttpTestingController;
  let estrecho = true;
  let alCambiarAncho: ((e: { matches: boolean }) => void) | null = null;
  let visor: VisualViewport | null = null;

  /** Lo justo de `VisualViewport`: posición, alto y sus eventos (como en las pruebas de d551). */
  class VisorFalso extends EventTarget {
    offsetTop = 0;
    height = window.innerHeight;
    width = window.innerWidth;
    scale?: number;
  }

  beforeEach(() => {
    estrecho = true;
    alCambiarAncho = null;
    visor = null;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ORIGEN_EDITOR, useValue: 'about:blank#' },
        { provide: VISOR_VISUAL, useFactory: () => visor },
        { provide: CLIENTE_SUBIDAS, useExisting: HttpClient },
      ],
    });
    spyOnProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap').and.returnValue(convertToParamMap({}));
    http = TestBed.inject(HttpTestingController);
    spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    spyOn(TestBed.inject(ConfirmacionService), 'confirmar').and.resolveTo({ confirmado: true } as never);
    const avisos = TestBed.inject(AvisosService);
    spyOn(avisos, 'exito').and.returnValue(1);
    spyOn(avisos, 'info').and.returnValue(3);
    const original = window.matchMedia.bind(window);
    spyOn(window, 'matchMedia').and.callFake((q: string) =>
      q === '(max-width: 767.98px)'
        ? ({ matches: estrecho, addEventListener: (_: string, f: (e: { matches: boolean }) => void) => (alCambiarAncho = f), removeEventListener: () => undefined } as unknown as MediaQueryList)
        : original(q),
    );
  });

  afterEach(() => {
    fixture?.destroy();
    http.verify();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement = HTMLElement>(selector: string) => el().querySelector<T>(selector);
  const texto = (selector: string) => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const iframe = () => q<HTMLIFrameElement>('iframe');
  const hoja = () => q('.flot--describir');
  const area = () => q<HTMLTextAreaElement>('.flot--describir textarea')!;

  function abrir(): jasmine.Spy {
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(PUBLICADA);
    http.expectOne(URL_CONTENIDO).flush(CONTENIDO);
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes: [{ nombre: 'Herrera', slug: 'herrera' }] });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
    fixture.detectChanges();
    const enviados = spyOn(iframe()!.contentWindow!, 'postMessage') as jasmine.Spy;
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
    return enviados;
  }

  function llegar(data: unknown, origin = location.origin, source: MessageEventSource | null = iframe()!.contentWindow): void {
    window.dispatchEvent(new MessageEvent('message', { data, origin, source }));
    fixture.detectChanges();
  }
  const mensajes = (enviados: jasmine.Spy, tipo: string) =>
    enviados.calls
      .allArgs()
      .map(([m]) => m as { tipo: string } & Record<string, unknown>)
      .filter((m) => m.tipo === tipo);
  const HOJA = {
    canal: CANAL_EDITOR_WEB,
    tipo: 'hoja-describir',
    id: 4,
    clave: 'portada.foto',
    titulo: 'describe la foto de la portada',
    texto: 'Niños en fila',
  };
  const respuesta = (devolverFoco: boolean, id = 4) => ({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-describir', id, devolverFoco });
  const respuestas = (enviados: jasmine.Spy) => mensajes(enviados, 'respuesta-hoja-describir');
  function escribir(valor: string): void {
    area().value = valor;
    area().dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
  }

  it('sale como hoja: diálogo con título, «Descripción» obligatoria, 150 como máximo, ayuda, contador, «Listo» y foco al texto', async () => {
    abrir();
    llegar(HOJA);
    await fixture.whenStable();
    const h = hoja()!;
    expect(h.classList).toContain('flot--capa');
    expect(h.getAttribute('role')).toBe('dialog');
    const titulo = h.querySelector('h2')!;
    expect(h.getAttribute('aria-labelledby')).toBe(titulo.id);
    expect(texto('.flot--describir h2')).toBe('describe la foto de la portada');
    expect(texto('.flot--describir label')).toBe('Descripciónobligatorio');
    expect(q('.flot--describir label .marca')).not.toBeNull();
    expect(q<HTMLLabelElement>('.flot--describir label')!.htmlFor).toBe(area().id);
    expect(area().rows).toBe(3);
    expect(area().maxLength).toBe(150);
    expect(area().value).toBe('Niños en fila');
    const [ayuda, contador] = area().getAttribute('aria-describedby')!.split(' ');
    expect(document.getElementById(ayuda)!.textContent!.trim()).toBe('Qué se ve, para quien no puede verla. Sin nombres de alumnos.');
    expect(document.getElementById(contador)!.textContent!.trim()).toBe('13 / 150');
    expect(texto('.flot--describir .flot__acciones button')).toBe('Listo');
    expect(q('.flot--describir .flot__asa-m')).not.toBeNull();
    expect(document.activeElement).toBe(area());
  });

  it('tras subir: «Foto subida: describe la foto…» con el texto vacío', () => {
    abrir();
    llegar({ ...HOJA, titulo: 'Foto subida: describe la foto de la portada', texto: '' });
    expect(texto('.flot--describir h2')).toBe('Foto subida: describe la foto de la portada');
    expect(area().value).toBe('');
    expect(texto('.flot--describir .contador')).toBe('0 / 150');
  });

  it('cada pulsación va a la capa con el id; el contador avisa del tope', () => {
    const enviados = abrir();
    llegar(HOJA);
    escribir('Niños saludando');
    expect(mensajes(enviados, 'texto-hoja-describir').at(-1)).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'texto-hoja-describir', id: 4, texto: 'Niños saludando' });
    expect(texto('.flot--describir .contador')).toBe('15 / 150');
    expect(q('.flot--describir .contador')!.classList).not.toContain('es-tope');
    escribir('x'.repeat(150));
    expect(texto('.flot--describir .contador')).toBe('150 / 150 · máximo');
    expect(q('.flot--describir .contador')!.classList).toContain('es-tope');
  });

  it('la `descripcion` que devuelve la capa cambia el borrador una vez (un cambio) y el texto escrito se queda', () => {
    abrir();
    llegar(HOJA);
    escribir('Niños saludando');
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'descripcion', clave: 'portada.foto', texto: 'Niños saludando' });
    expect(fixture.componentInstance.borrador().find((f) => f.clave === 'portada.foto')!.texto).toBe('Niños saludando');
    expect(fixture.componentInstance.nCambios()).toBe(1);
    expect(area().value).toBe('Niños saludando');
  });

  it('«Listo» la cierra y pide el foco para la foto, en la vista previa', () => {
    const enviados = abrir();
    llegar(HOJA);
    q<HTMLButtonElement>('.flot--describir .flot__acciones button')!.click();
    fixture.detectChanges();
    expect(hoja()).toBeNull();
    expect(respuestas(enviados)).toEqual([respuesta(true)]);
    expect(document.activeElement).toBe(iframe());
  });

  it('Escape (también desde el texto) la cierra devolviendo el foco', async () => {
    const enviados = abrir();
    llegar(HOJA);
    await fixture.whenStable();
    area().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    fixture.detectChanges();
    expect(hoja()).toBeNull();
    expect(respuestas(enviados)).toEqual([respuesta(true)]);
  });

  it('d546: deslizar el asa la cierra devolviendo el foco a la foto', async () => {
    const enviados = abrir();
    llegar(HOJA);
    await fixture.whenStable();
    deslizarAsa(q('.flot--describir .flot__asa-m')!);
    await new Promise((resuelve) => setTimeout(resuelve, SALIDA_HOJA_MS));
    fixture.detectChanges();
    expect(hoja()).toBeNull();
    expect(respuestas(enviados)).toEqual([respuesta(true)]);
  });

  it('pulsar fuera la cierra sin mover el foco; pulsar dentro, no', () => {
    const enviados = abrir();
    llegar(HOJA);
    area().dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    expect(hoja()).not.toBeNull();
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    fixture.detectChanges();
    expect(hoja()).toBeNull();
    expect(respuestas(enviados)).toEqual([respuesta(false)]);
  });

  it('pasar el foco a la vista previa la cierra sin devolver el foco', () => {
    const enviados = abrir();
    llegar(HOJA);
    iframe()!.focus();
    window.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(hoja()).toBeNull();
    expect(respuestas(enviados)).toEqual([respuesta(false)]);
  });

  it('cerrar-hoja-describir de la capa la quita solo si es la misma (id), sin responder', () => {
    const enviados = abrir();
    llegar(HOJA);
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-describir', id: 3 });
    expect(hoja()).not.toBeNull();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-describir', id: 4 });
    expect(hoja()).toBeNull();
    expect(respuestas(enviados)).toEqual([]);
  });

  it('otra hoja de describir (id nuevo): la anterior se responde con su id y las pulsaciones llevan el nuevo', () => {
    const enviados = abrir();
    llegar(HOJA);
    llegar({ ...HOJA, id: 6, texto: '' });
    expect(respuestas(enviados)).toEqual([respuesta(false, 4)]);
    expect(area().value).toBe('');
    escribir('Sala');
    expect(mensajes(enviados, 'texto-hoja-describir').at(-1)!['id']).toBe(6);
  });

  it('el menú de la foto como hoja cierra la de describir avisando a la capa', () => {
    const enviados = abrir();
    llegar(HOJA);
    llegar({
      canal: CANAL_EDITOR_WEB,
      tipo: 'hoja-menu',
      id: 5,
      menu: 'foto',
      titulo: 'Foto de la portada',
      opciones: [{ op: 'subir', texto: 'Cambiar foto', pie: '', icono: 'foto', desactivada: false, peligro: false }],
    });
    expect(hoja()).toBeNull();
    expect(respuestas(enviados)).toEqual([respuesta(false)]);
  });

  it('la foto deja de estar en el borrador: la hoja se cierra y se lo dice a la capa', () => {
    const enviados = abrir();
    llegar(HOJA);
    fixture.componentInstance.borrador.update((filas) => filas.map((f) => (f.clave === 'portada.foto' ? { ...f, imagenId: null, imagenUrl: null } : f)));
    fixture.detectChanges();
    expect(hoja()).toBeNull();
    expect(respuestas(enviados)).toEqual([respuesta(false)]);
  });

  it('una foto que no está en el borrador no abre la hoja y se contesta ya', () => {
    const enviados = abrir();
    llegar({ ...HOJA, clave: 'presentacion.foto' });
    expect(hoja()).toBeNull();
    expect(respuestas(enviados)).toEqual([respuesta(false)]);
  });

  it('si el panel se ensancha con la hoja abierta, se cierra como la del menú (sin foco)', () => {
    const enviados = abrir();
    llegar(HOJA);
    alCambiarAncho!({ matches: false });
    fixture.detectChanges();
    expect(hoja()).toBeNull();
    expect(respuestas(enviados)).toEqual([respuesta(false)]);
  });

  it('d551: con el teclado abierto, la hoja va pegada a él y cabe en lo que se ve', () => {
    const falso = new VisorFalso();
    visor = falso as unknown as VisualViewport;
    abrir();
    llegar(HOJA);
    falso.offsetTop = 40;
    falso.height = window.innerHeight - 40 - 200;
    falso.dispatchEvent(new Event('resize'));
    fixture.detectChanges();
    const h = hoja()!;
    expect(h.classList).toContain('flot--teclado');
    expect(getComputedStyle(h).bottom).toBe('200px');
    expect(parseFloat(getComputedStyle(h).maxHeight)).toBeLessThanOrEqual(window.innerHeight - 240 + 1);
  });

  it('mensajes mal formados o de otro origen no abren nada', () => {
    abrir();
    llegar({ ...HOJA, texto: 'x'.repeat(151) });
    llegar({ ...HOJA, clave: 'portada.titular' });
    llegar(HOJA, 'https://otro.example');
    expect(hoja()).toBeNull();
  });

  describe('panel de 768 px o más', () => {
    beforeEach(() => (estrecho = false));

    it('no abre la hoja y contesta ya (la capa sigue con su flotante)', () => {
      const enviados = abrir();
      llegar(HOJA);
      expect(hoja()).toBeNull();
      expect(respuestas(enviados)).toEqual([respuesta(false)]);
    });
  });
});
