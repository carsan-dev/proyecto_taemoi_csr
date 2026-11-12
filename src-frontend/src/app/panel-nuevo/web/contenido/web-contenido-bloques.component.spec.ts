import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { CANAL_EDITOR_WEB } from '../../../web-plantilla/editor/puente-editor';
import { AvisosService } from '../../avisos/avisos.service';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { CLIENTE_SUBIDAS } from '../../material-examen/material-examen-api.service';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import { BloqueContenido, ContenidoWeb, ORIGEN_EDITOR } from './web-contenido-vista';
import { WebContenidoComponent } from './web-contenido.component';

const URL = `${environment.apiUrl}/escuela/web`;
const URL_CONTENIDO = `${URL}/contenido`;
const URL_IMAGENES = `${URL}/imagenes`;
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
  fila('disciplinas', null, { orden: 1 }),
  fila('galeria', null, { orden: 2 }),
  fila('texto_foto.2', null, { orden: 3 }),
  fila('texto_foto.2.titulo', 'Qué traer'),
  fila('profesores', null, { orden: 4 }),
  fila('eventos', null, { orden: 5 }),
  fila('tarifas', null, { orden: 6 }),
];
const CONTENIDO: ContenidoWeb = { version: 5, bloques: BLOQUES };
const PAGINA = ['presentacion', 'disciplinas', 'galeria', 'texto_foto.2', 'profesores', 'eventos', 'tarifas'];
const peticion = (f: BloqueContenido) => ({ clave: f.clave, texto: f.texto, imagenId: f.imagenId, orden: f.orden, oculto: f.oculto });

/** W12e: bloques de Inicio (boceto `mover`, `alternarOculto`, `quitarBloque`, `anadirBloque`, `abrirHoja`, `cambios`). */
describe('WebContenidoComponent: bloques (W12e)', () => {
  let fixture: ComponentFixture<WebContenidoComponent>;
  let http: HttpTestingController;
  let confirmar: jasmine.Spy;
  let estrecho = false;
  let alCambiarAncho: ((e: { matches: boolean }) => void) | null = null;

  beforeEach(() => {
    estrecho = false;
    alCambiarAncho = null;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ORIGEN_EDITOR, useValue: 'about:blank#' },
        { provide: CLIENTE_SUBIDAS, useExisting: HttpClient },
      ],
    });
    spyOnProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap').and.returnValue(convertToParamMap({}));
    http = TestBed.inject(HttpTestingController);
    spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    confirmar = spyOn(TestBed.inject(ConfirmacionService), 'confirmar').and.resolveTo({ confirmado: true } as never);
    const avisos = TestBed.inject(AvisosService);
    spyOn(avisos, 'exito').and.returnValue(1);
    spyOn(avisos, 'info').and.returnValue(3);
    const original = window.matchMedia.bind(window);
    spyOn(window, 'matchMedia').and.callFake((q: string) =>
      q === '(max-width: 767.98px)' ? ({ matches: estrecho, addEventListener: (_: string, f: (e: { matches: boolean }) => void) => (alCambiarAncho = f), removeEventListener: () => undefined } as unknown as MediaQueryList) : original(q),
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
  const anuncio = () => texto('p.oculto[role="status"]');

  function abrir(contenido = CONTENIDO): jasmine.Spy {
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(PUBLICADA);
    http.expectOne(URL_CONTENIDO).flush(contenido);
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes: [{ nombre: 'Herrera', slug: 'herrera' }] });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
    fixture.detectChanges();
    const enviados = spyOn(iframe()!.contentWindow!, 'postMessage') as jasmine.Spy;
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'bloques', orden: PAGINA, vacios: ['galeria'] });
    return enviados;
  }

  function llegar(data: unknown, origin = location.origin, source: MessageEventSource | null = iframe()!.contentWindow): void {
    window.dispatchEvent(new MessageEvent('message', { data, origin, source }));
    fixture.detectChanges();
  }
  const accion = (a: string, bloque: string) => llegar({ canal: CANAL_EDITOR_WEB, tipo: 'bloque', accion: a, bloque });
  const mensajes = (enviados: jasmine.Spy, tipo: string) =>
    enviados.calls
      .allArgs()
      .map(([m]) => m as { tipo: string } & Record<string, unknown>)
      .filter((m) => m.tipo === tipo);
  const ultimo = (enviados: jasmine.Spy, tipo: string) => mensajes(enviados, tipo).pop();
  const ordenEnviado = (enviados: jasmine.Spy) =>
    (ultimo(enviados, 'borrador')!['bloques'] as { clave: string; orden: number }[])
      .filter((b) => /^(presentacion|disciplinas|galeria|profesores|eventos|tarifas|sedes|texto_foto\.\d+)$/.test(b.clave))
      .sort((a, b) => a.orden - b.orden)
      .map((b) => b.clave);
  const guardar = () => {
    Array.from(el().querySelectorAll<HTMLButtonElement>('.barra button')).find((b) => b.textContent!.trim() === 'Guardar')!.click();
    fixture.detectChanges();
    return http.expectOne((r) => r.method === 'PUT' && r.url === URL_CONTENIDO);
  };

  it('Bajar sobre una fija: la vista previa al momento, un cambio, voz y foco; el PUT lleva el orden nuevo', () => {
    const enviados = abrir();
    accion('bajar', 'presentacion');
    expect(ordenEnviado(enviados)).toEqual(['disciplinas', 'presentacion', 'galeria', 'texto_foto.2', 'profesores', 'eventos', 'tarifas']);
    expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
    expect(anuncio()).toBe('Presentación pasa al puesto 2 de 7.');
    expect(ultimo(enviados, 'foco-bloque')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'foco-bloque', foco: 'bajar:presentacion' });
    expect(ultimo(enviados, 'estado-bloques')).toEqual(
      jasmine.objectContaining({ sinGuardar: ['presentacion'], quitables: ['texto_foto.2'], estrecho: false }),
    );
    const put = guardar();
    expect(put.request.body.version).toBe(5);
    expect(put.request.body.bloques).toContain({ clave: 'presentacion', texto: null, imagenId: null, orden: 1, oculto: false });
    expect(put.request.body.bloques).toContain({ clave: 'disciplinas', texto: null, imagenId: null, orden: 0, oculto: false });
    expect(put.request.body.bloques.some((b: Record<string, unknown>) => 'permiso' in b || 'imagenUrl' in b)).toBeFalse();
    put.flush({ version: 6, bloques: BLOQUES });
  });

  it('al llegar al extremo, el foco pasa al otro botón; en el extremo no se mueve', () => {
    const enviados = abrir();
    accion('subir', 'galeria');
    accion('subir', 'galeria');
    expect(ultimo(enviados, 'foco-bloque')!['foco']).toBe('bajar:galeria');
    expect(anuncio()).toBe('Galería pasa al puesto 1 de 7.');
    const n = mensajes(enviados, 'borrador').length;
    accion('subir', 'galeria');
    expect(mensajes(enviados, 'borrador').length).toBe(n);
  });

  it('Ocultar y Mostrar: el `oculto` de la cabecera, cuenta y se dice; volver a mostrar deja de ser cambio', () => {
    const enviados = abrir();
    accion('ocultar', 'galeria');
    expect((ultimo(enviados, 'borrador')!['bloques'] as BloqueContenido[]).find((b) => b.clave === 'galeria')!.oculto).toBeTrue();
    expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
    expect(anuncio()).toBe('Galería: oculto. Al guardar deja de salir en tu web.');
    expect(ultimo(enviados, 'foco-bloque')!['foco']).toBe('vis:galeria');
    accion('mostrar', 'galeria');
    expect(anuncio()).toBe('Galería: visible. Al guardar vuelve a salir en tu web.');
    expect(q('.barra')).toBeNull();
  });

  it('Añadir «Texto con foto» en un puesto: número libre, dos cambios, foco al primer texto y PUT con su cabecera', () => {
    const enviados = abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'anadir-bloque', pos: 1, bloque: 'texto_foto' });
    expect(ordenEnviado(enviados)).toEqual(['presentacion', 'texto_foto.1', 'disciplinas', 'galeria', 'texto_foto.2', 'profesores', 'eventos', 'tarifas']);
    expect(texto('.barra__texto strong')).toBe('2 cambios sin guardar');
    expect(anuncio()).toBe('Texto con foto añadido en el puesto 2. Escribe su contenido.');
    expect(ultimo(enviados, 'foco-bloque')!['foco']).toBe('editar:texto_foto.1');
    // Lo que se escribe en el bloque nuevo no cuenta aparte (el boceto cuenta el bloque).
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave: 'texto_foto.1.titulo', texto: 'Horario de verano' });
    expect(texto('.barra__texto strong')).toBe('2 cambios sin guardar');
    expect(ultimo(enviados, 'estado-bloques')!['quitables']).toEqual(['texto_foto.1', 'texto_foto.2']);
    const put = guardar();
    expect(put.request.body.bloques).toContain({ clave: 'texto_foto.1', texto: null, imagenId: null, orden: 1, oculto: false });
    expect(put.request.body.bloques).toContain({ clave: 'texto_foto.1.titulo', texto: 'Horario de verano', imagenId: null, orden: 0, oculto: false });
    put.flush({ version: 6, bloques: BLOQUES });
  });

  it('un tipo único que ya está no se añade', () => {
    const enviados = abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'anadir-bloque', pos: 0, bloque: 'galeria' });
    expect(mensajes(enviados, 'borrador').length).toBe(1);
    expect(q('.barra')).toBeNull();
  });

  it('Quitar: confirmación del boceto; se van todas las filas del bloque; foco al hueco; PUT sin ellas', async () => {
    const enviados = abrir();
    accion('quitar', 'texto_foto.2');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(confirmar).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({
        titulo: '¿Quitar «Qué traer» de tu web?',
        mensaje: 'Se quita al guardar. Hasta entonces, «Descartar» lo recupera.',
        accion: 'Quitar bloque',
        cancelar: 'Cancelar',
        destructivo: true,
      }),
    );
    const bloques = ultimo(enviados, 'borrador')!['bloques'] as BloqueContenido[];
    expect(bloques.some((b) => b.clave.startsWith('texto_foto.2'))).toBeFalse();
    expect(texto('.barra__texto strong')).toBe('2 cambios sin guardar');
    expect(anuncio()).toBe('Texto con foto «Qué traer»: quitado. Se quita de la web al guardar.');
    expect(ultimo(enviados, 'foco-bloque')!['foco']).toBe('a:3');
    const put = guardar();
    expect(put.request.body.bloques.some((b: BloqueContenido) => b.clave.startsWith('texto_foto.2'))).toBeFalse();
    put.flush({ version: 6, bloques: BLOQUES });
  });

  it('Quitar cancelado: nada cambia y el foco vuelve a su botón; un bloque que no se puede quitar, ni pregunta', async () => {
    confirmar.and.resolveTo({ confirmado: false } as never);
    const enviados = abrir();
    accion('quitar', 'texto_foto.2');
    await fixture.whenStable();
    expect(q('.barra')).toBeNull();
    expect(ultimo(enviados, 'foco-bloque')!['foco']).toBe('quitar:texto_foto.2');
    accion('quitar', 'galeria');
    await fixture.whenStable();
    expect(confirmar).toHaveBeenCalledTimes(1);
  });

  it('Descartar devuelve el orden, lo oculto y lo quitado', async () => {
    const enviados = abrir();
    accion('bajar', 'presentacion');
    accion('ocultar', 'galeria');
    accion('quitar', 'texto_foto.2');
    await fixture.whenStable();
    fixture.detectChanges();
    // El orden cuenta una vez (boceto `cambios`): bajar + ocultar + quitar = 3.
    expect(texto('.barra__texto strong')).toBe('3 cambios sin guardar');
    Array.from(el().querySelectorAll<HTMLButtonElement>('.barra button')).find((b) => b.textContent!.trim() === 'Descartar')!.click();
    fixture.detectChanges();
    expect(q('.barra')).toBeNull();
    expect(ordenEnviado(enviados)).toEqual(PAGINA);
    expect(ultimo(enviados, 'estado-bloques')!['sinGuardar']).toEqual([]);
  });

  const descartar = () => {
    Array.from(el().querySelectorAll<HTMLButtonElement>('.barra button')).find((b) => b.textContent!.trim() === 'Descartar')!.click();
    fixture.detectChanges();
  };

  it('mover un bloque y volver a su sitio: 0 cambios', () => {
    const enviados = abrir();
    accion('bajar', 'presentacion');
    expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
    accion('subir', 'presentacion');
    expect(ordenEnviado(enviados)).toEqual(PAGINA);
    expect(q('.barra')).toBeNull();
    expect(ultimo(enviados, 'estado-bloques')!['sinGuardar']).toEqual([]);
  });

  it('añadir, editar su texto y quitar: 0 cambios', async () => {
    const enviados = abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'anadir-bloque', pos: 1, bloque: 'texto_foto' });
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave: 'texto_foto.1.titulo', texto: 'Horario de verano' });
    expect(texto('.barra__texto strong')).toBe('2 cambios sin guardar');
    accion('quitar', 'texto_foto.1');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(q('.barra')).toBeNull();
    const bloques = ultimo(enviados, 'borrador')!['bloques'] as BloqueContenido[];
    expect(bloques.some((b) => b.clave.startsWith('texto_foto.1'))).toBeFalse();
    expect(ordenEnviado(enviados)).toEqual(PAGINA);
  });

  it('Descartar tras añadir y quitar vuelve exactamente a lo guardado', async () => {
    const enviados = abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'anadir-bloque', pos: 0, bloque: 'texto_foto' });
    accion('quitar', 'texto_foto.2');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(texto('.barra__texto strong')).toBe('3 cambios sin guardar');
    descartar();
    expect(q('.barra')).toBeNull();
    const resumen = (filas: { clave: string; texto: string | null; orden: number; oculto: boolean }[]) =>
      filas.map((b) => `${b.clave}|${b.texto}|${b.orden}|${b.oculto}`).sort();
    expect(resumen(ultimo(enviados, 'borrador')!['bloques'] as BloqueContenido[])).toEqual(resumen(BLOQUES));
  });

  it('Quitar un bloque con una foto nueva subiéndose: la petición se corta y no queda fila huérfana ni cambio', async () => {
    const enviados = abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'anadir-bloque', pos: 1, bloque: 'texto_foto' });
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'subir-foto', clave: 'texto_foto.1.foto', archivo: new File(['x'], 'pabellon.jpg', { type: 'image/jpeg' }) });
    const subida = http.expectOne((r) => r.method === 'POST' && r.url === URL_IMAGENES);
    accion('quitar', 'texto_foto.1');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(subida.cancelled).toBeTrue();
    const bloques = ultimo(enviados, 'borrador')!['bloques'] as BloqueContenido[];
    expect(bloques.some((b) => b.clave.startsWith('texto_foto.1'))).toBeFalse();
    expect(ultimo(enviados, 'fotos')?.['subidas']).toEqual({});
    expect(q('.barra')).toBeNull();
  });

  it('durante el guardado, las acciones no hacen nada', () => {
    const enviados = abrir();
    accion('ocultar', 'galeria');
    const put = guardar();
    const n = mensajes(enviados, 'borrador').length;
    accion('bajar', 'presentacion');
    expect(mensajes(enviados, 'borrador').length).toBe(n);
    put.flush({ version: 6, bloques: BLOQUES });
  });

  it('mensajes de otro origen, de otra ventana o con un bloque no permitido: ignorados', () => {
    const enviados = abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'bloque', accion: 'bajar', bloque: 'presentacion' }, 'https://otro.example');
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'bloque', accion: 'bajar', bloque: 'presentacion' }, location.origin, window);
    accion('bajar', 'contacto');
    accion('subir', 'eventos');
    accion('borrar', 'galeria');
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'anadir-bloque', pos: 99999, bloque: 'texto_foto' });
    expect(mensajes(enviados, 'borrador').length).toBe(1);
    expect(q('.barra')).toBeNull();
  });

  describe('panel estrecho (< 768 px): hoja inferior', () => {
    beforeEach(() => (estrecho = true));

    it('la capa la pide; «Puesto i de n · …», botones del boceto y foco al primero que se puede pulsar', async () => {
      const enviados = abrir();
      expect(ultimo(enviados, 'estado-bloques')!['estrecho']).toBeTrue();
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja', bloque: 'presentacion' });
      await fixture.whenStable();
      const hoja = q('.hoja')!;
      expect(hoja.getAttribute('role')).toBe('dialog');
      expect(texto('#hoja-t')).toBe('Presentación');
      expect(texto('.hoja__cab p')).toBe('Puesto 1 de 7 · Se ve en la web');
      const botones = Array.from(hoja.querySelectorAll<HTMLButtonElement>('.hoja__acc button'));
      expect(botones.map((b) => b.textContent!.trim())).toEqual(['Subir', 'Bajar', 'Ocultar']);
      expect(botones[0].getAttribute('aria-disabled')).toBe('true');
      expect(botones[1].getAttribute('aria-label')).toBe('Bajar el bloque Presentación');
      expect(document.activeElement).toBe(botones[1]);
      expect(q('.web-contenido__pagina')!.classList).toContain('con-hoja');
    });

    it('sus botones hacen la acción y la hoja se repinta; Listo la cierra y devuelve el foco al bloque', async () => {
      const enviados = abrir();
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja', bloque: 'galeria' });
      expect(texto('.hoja__cab p')).toBe('Puesto 3 de 7 · Vacío: no sale en la web');
      q<HTMLButtonElement>('[data-h="h-vis"]')!.click();
      fixture.detectChanges();
      expect(texto('.hoja__cab p')).toBe('Puesto 3 de 7 · Oculto en la web');
      expect(texto('[data-h="h-vis"]')).toBe('Mostrar');
      q<HTMLButtonElement>('[data-h="h-subir"]')!.click();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(texto('.hoja__cab p')).toBe('Puesto 2 de 7 · Oculto en la web');
      expect(document.activeElement).toBe(q('[data-h="h-subir"]'));
      expect(mensajes(enviados, 'foco-bloque')).toEqual([]);
      q<HTMLButtonElement>('.hoja__listo')!.click();
      fixture.detectChanges();
      expect(q('.hoja')).toBeNull();
      expect(ultimo(enviados, 'foco-bloque')!['foco']).toBe('sel:galeria');
    });

    it('Quitar desde la hoja: confirma, cierra la hoja y el foco va al hueco', async () => {
      const enviados = abrir();
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja', bloque: 'texto_foto.2' });
      expect(texto('.hoja__quitar')).toBe('Quitar');
      q<HTMLButtonElement>('.hoja__quitar')!.click();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(q('.hoja')).toBeNull();
      expect(ultimo(enviados, 'foco-bloque')!['foco']).toBe('a:3');
    });

    it('Escape la cierra', () => {
      abrir();
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja', bloque: 'galeria' });
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();
      expect(q('.hoja')).toBeNull();
    });
  });

  it('con el panel ancho, la hoja no se abre', () => {
    abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja', bloque: 'galeria' });
    expect(q('.hoja')).toBeNull();
  });

  /** d541: los menús de la vista previa (foto, «Añadir bloque») como hoja inferior con el panel estrecho. */
  describe('menús de la capa como hoja (d541)', () => {
    const opcion = (op: string, texto: string, extra: Record<string, unknown> = {}) => ({
      op,
      texto,
      pie: '',
      icono: 'foto',
      desactivada: false,
      peligro: false,
      ...extra,
    });
    const MENU_FOTO = {
      canal: CANAL_EDITOR_WEB,
      tipo: 'hoja-menu',
      id: 7,
      menu: 'foto',
      titulo: 'Foto de «Nuestra sala»',
      opciones: [
        opcion('subir', 'Cambiar foto', { pie: 'JPG, PNG o WebP, hasta 50 MB' }),
        opcion('describir', 'Describir la foto', { icono: 'texto' }),
        opcion('quitar', 'Quitar foto', { icono: 'papelera', peligro: true }),
      ],
    };
    const MENU_ANADIR = {
      canal: CANAL_EDITOR_WEB,
      tipo: 'hoja-menu',
      id: 8,
      menu: 'anadir',
      titulo: 'Añadir bloque en el puesto 3',
      opciones: [
        opcion('presentacion', 'Presentación', { icono: 'texto', pie: 'Ya está en tu web. Si no lo quieres, ocúltalo.', desactivada: true }),
        opcion('sedes', 'Fotos de las sedes'),
        opcion('texto_foto', 'Texto con foto', { icono: 'texto' }),
      ],
    };
    const items = () => Array.from(el().querySelectorAll<HTMLButtonElement>('.flot--capa [role="menuitem"]'));
    const respuestas = (enviados: jasmine.Spy) => mensajes(enviados, 'respuesta-hoja-menu');
    const respuesta = (menu: string, op: string | null, devolverFoco: boolean, id = menu === 'foto' ? 7 : 8) => ({
      canal: CANAL_EDITOR_WEB,
      tipo: 'respuesta-hoja-menu',
      menu,
      id,
      op,
      devolverFoco,
    });

    describe('panel estrecho', () => {
      beforeEach(() => (estrecho = true));

      it('el menú de foto sale como hoja: título, opciones del boceto, quitar en peligro tras un separador y foco al primero', async () => {
        abrir();
        llegar(MENU_FOTO);
        await fixture.whenStable();
        const hoja = q('.flot--capa')!;
        expect(texto('#hoja-menu-t')).toBe('Foto de «Nuestra sala»');
        expect(hoja.querySelector('.flot__lista')!.getAttribute('role')).toBe('menu');
        expect(hoja.querySelector('.flot__lista')!.getAttribute('aria-labelledby')).toBe('hoja-menu-t');
        expect(items().map((b) => b.textContent!.replace(/\s+/g, ' ').trim())).toEqual([
          'Cambiar fotoJPG, PNG o WebP, hasta 50 MB',
          'Describir la foto',
          'Quitar foto',
        ]);
        expect(items()[2].classList).toContain('flot__op--peligro');
        expect(hoja.querySelectorAll('.flot__sep[role="separator"]').length).toBe(1);
        expect(items()[2].previousElementSibling!.classList).toContain('flot__sep');
        expect(hoja.querySelectorAll('svg').length).toBe(3);
        expect(document.activeElement).toBe(items()[0]);
        expect(q('.hoja')).toBeNull();
      });

      it('el menú «Añadir bloque» sale como hoja; las opciones que ya están, desactivadas y sin foco', async () => {
        abrir();
        llegar(MENU_ANADIR);
        await fixture.whenStable();
        expect(texto('#hoja-menu-t')).toBe('Añadir bloque en el puesto 3');
        expect(items().map((b) => b.getAttribute('aria-disabled'))).toEqual(['true', null, null]);
        expect(items()[0].textContent).toContain('Ya está en tu web. Si no lo quieres, ocúltalo.');
        expect(document.activeElement).toBe(items()[1]);
        expect(q('.flot--capa .flot__sep')).toBeNull();
      });

      it('elegir una opción la devuelve a la capa (sin pedir foco) y cierra la hoja', async () => {
        const enviados = abrir();
        llegar(MENU_FOTO);
        await fixture.whenStable();
        items()[2].click();
        fixture.detectChanges();
        expect(respuestas(enviados)).toEqual([respuesta('foto', 'quitar', false)]);
        expect(q('.flot--capa')).toBeNull();
        expect(document.activeElement).toBe(iframe());
      });

      it('una opción desactivada no hace nada', async () => {
        const enviados = abrir();
        llegar(MENU_ANADIR);
        await fixture.whenStable();
        items()[0].click();
        fixture.detectChanges();
        expect(respuestas(enviados)).toEqual([]);
        expect(q('.flot--capa')).not.toBeNull();
        items()[2].click();
        expect(respuestas(enviados)).toEqual([respuesta('anadir', 'texto_foto', false)]);
      });

      it('flechas, Inicio y Fin se mueven entre opciones', async () => {
        abrir();
        llegar(MENU_FOTO);
        await fixture.whenStable();
        const tecla = (key: string) =>
          (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        tecla('ArrowUp');
        expect(document.activeElement).toBe(items()[2]);
        tecla('ArrowDown');
        expect(document.activeElement).toBe(items()[0]);
        tecla('End');
        expect(document.activeElement).toBe(items()[2]);
        tecla('Home');
        expect(document.activeElement).toBe(items()[0]);
      });

      it('Escape en una opción la cierra y pide que el foco vuelva a la foto, en la vista previa', async () => {
        const enviados = abrir();
        llegar(MENU_FOTO);
        await fixture.whenStable();
        items()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
        fixture.detectChanges();
        expect(q('.flot--capa')).toBeNull();
        expect(respuestas(enviados)).toEqual([respuesta('foto', null, true)]);
        expect(document.activeElement).toBe(iframe());
      });

      it('Escape con el foco fuera de la hoja también la cierra; con un diálogo abierto encima, no', async () => {
        const enviados = abrir();
        llegar(MENU_ANADIR);
        await fixture.whenStable();
        const dialogo = document.createElement('dialog');
        document.body.appendChild(dialogo);
        dialogo.setAttribute('open', '');
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        expect(q('.flot--capa')).not.toBeNull();
        dialogo.remove();
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        fixture.detectChanges();
        expect(q('.flot--capa')).toBeNull();
        expect(respuestas(enviados)).toEqual([respuesta('anadir', null, true)]);
      });

      it('pulsar fuera de la hoja la cierra sin mover el foco; pulsar dentro, no', async () => {
        const enviados = abrir();
        llegar(MENU_FOTO);
        await fixture.whenStable();
        items()[0].dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
        expect(q('.flot--capa')).not.toBeNull();
        document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
        fixture.detectChanges();
        expect(q('.flot--capa')).toBeNull();
        expect(respuestas(enviados)).toEqual([respuesta('foto', null, false)]);
        expect(document.activeElement).not.toBe(iframe());
      });

      it('pasar el foco a la vista previa (pulsar en ella) la cierra sin devolver el foco', async () => {
        const enviados = abrir();
        llegar(MENU_FOTO);
        await fixture.whenStable();
        iframe()!.focus();
        window.dispatchEvent(new Event('blur'));
        fixture.detectChanges();
        expect(q('.flot--capa')).toBeNull();
        expect(respuestas(enviados)).toEqual([respuesta('foto', null, false)]);
      });

      it('abrir el menú cierra la hoja de acciones del bloque', () => {
        abrir();
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja', bloque: 'galeria' });
        expect(q('.hoja')).not.toBeNull();
        llegar(MENU_ANADIR);
        expect(q('.hoja')).toBeNull();
        expect(q('.flot--capa')).not.toBeNull();
      });

      it('la respuesta lleva el id de la hoja que pinta; si llega otra hoja, la anterior se responde con su id', async () => {
        const enviados = abrir();
        llegar(MENU_FOTO);
        llegar({ ...MENU_FOTO, id: 9 });
        fixture.detectChanges();
        expect(respuestas(enviados)).toEqual([respuesta('foto', null, false, 7)]);
        items()[0].click();
        expect(respuestas(enviados).at(-1)).toEqual(respuesta('foto', 'subir', false, 9));
      });

      it('cerrar-hoja-menu de la capa quita la hoja si es la misma (menú e id), sin responder ni mover el foco', () => {
        const enviados = abrir();
        llegar(MENU_FOTO);
        fixture.detectChanges();
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-menu', menu: 'foto', id: 6 });
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-menu', menu: 'anadir', id: 7 });
        fixture.detectChanges();
        expect(q('.flot--capa')).not.toBeNull();
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-menu', menu: 'foto', id: 7 });
        fixture.detectChanges();
        expect(q('.flot--capa')).toBeNull();
        expect(respuestas(enviados)).toEqual([]);
        expect(document.activeElement).not.toBe(iframe());
      });

      it('abrir la hoja de acciones de un bloque cierra antes la del menú y se lo dice a la capa', () => {
        const enviados = abrir();
        llegar(MENU_ANADIR);
        fixture.detectChanges();
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja', bloque: 'galeria' });
        fixture.detectChanges();
        expect(q('.flot--capa')).toBeNull();
        expect(q('.hoja')).not.toBeNull();
        expect(respuestas(enviados)).toEqual([respuesta('anadir', null, false, 8)]);
      });

      it('si el panel se ensancha con la hoja abierta, se cierra y la capa recibe la respuesta null', () => {
        const enviados = abrir();
        llegar(MENU_FOTO);
        fixture.detectChanges();
        expect(alCambiarAncho).not.toBeNull();
        alCambiarAncho!({ matches: false });
        fixture.detectChanges();
        expect(q('.flot--capa')).toBeNull();
        expect(respuestas(enviados)).toEqual([respuesta('foto', null, false, 7)]);
      });

      it('mensajes mal formados o de otro origen no abren nada', () => {
        abrir();
        llegar({ ...MENU_FOTO, menu: 'otro' });
        llegar({ ...MENU_FOTO, opciones: [] });
        llegar(MENU_FOTO, 'https://otro.example');
        expect(q('.flot--capa')).toBeNull();
      });
    });

    describe('panel de 768 px o más', () => {
      it('no abre la hoja y le dice a la capa que siga con su menú flotante', () => {
        const enviados = abrir();
        llegar(MENU_FOTO);
        expect(q('.flot--capa')).toBeNull();
        expect(respuestas(enviados)).toEqual([respuesta('foto', null, false)]);
      });
    });
  });
});
