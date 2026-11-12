import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
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
const PANEL = { origen: 'PANEL' as const, por: 'María Ruiz', en: '2026-11-03T18:42:10' };
const ALTA = { origen: 'ALTA' as const, por: null, en: '2026-10-01T09:00:00' };
const BLOQUES: BloqueContenido[] = [
  fila('portada.titular', 'Taekwondo en seis pueblos'),
  fila('portada.foto', 'Niños en fila', { imagenId: 7, imagenUrl: 'https://x/p.webp' }),
  fila('profesores', null, { orden: 2 }),
  fila('profesor.1.nombre', 'Antonio Ruiz', { permiso: PANEL }),
  fila('profesor.1.cargo', 'Cinturón negro 5.º dan'),
  fila('profesor.1.sedes', '3,4'),
  fila('profesor.3.nombre', 'Lucía Fernández', { permiso: ALTA }),
  fila('profesor.3.sedes', '5'),
];
const CONTENIDO: ContenidoWeb = { version: 5, bloques: BLOQUES };
const FALTA = 'Marca la casilla si tienes su permiso. Sin él no puede salir en la web.';

type Mensaje = { tipo: string } & Record<string, unknown>;
type Peticion = { clave: string; texto: string | null; permiso?: unknown };

/** W12f-2b: profesores en «Web · Contenido» (boceto `profeHtml`, `anadirProfe`, `quitarProfe`, `errores`, `cambios`). */
describe('WebContenidoComponent: profesores (W12f-2b)', () => {
  let fixture: ComponentFixture<WebContenidoComponent>;
  let http: HttpTestingController;
  let confirmar: jasmine.Spy;
  let enviados: jasmine.Spy;

  beforeEach(() => {
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
    spyOn(avisos, 'error').and.returnValue(2);
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
  const boton = (t: string) => Array.from(el().querySelectorAll<HTMLButtonElement>('.barra button')).find((b) => b.textContent!.trim() === t);

  function abrir(contenido = CONTENIDO, sedes: unknown[] = [{ nombre: 'Herrera', slug: 'herrera' }]): void {
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(PUBLICADA);
    http.expectOne(URL_CONTENIDO).flush(contenido);
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
    fixture.detectChanges();
    enviados = spyOn(iframe()!.contentWindow!, 'postMessage') as jasmine.Spy;
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
  }
  function llegar(data: unknown): void {
    window.dispatchEvent(new MessageEvent('message', { data, origin: location.origin, source: iframe()!.contentWindow }));
    fixture.detectChanges();
  }
  const profesor = (data: Record<string, unknown>) => llegar({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', ...data });
  const escribir = (clave: string, t: string) => llegar({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave, texto: t });
  const mensajes = (tipo: string) =>
    enviados.calls
      .allArgs()
      .map(([m]) => m as Mensaje)
      .filter((m) => m.tipo === tipo);
  const ultimo = (tipo: string) => mensajes(tipo).at(-1);
  const borrador = () => ultimo('borrador')!['bloques'] as { clave: string; texto: string | null }[];
  const profesores = () => ultimo('profesores')!['lista'] as { n: number; nombre: string; permiso: Record<string, unknown>; sedes: number[] }[];
  const resumen = () => Array.from(el().querySelectorAll('.resumen li')).map((li) => li.textContent!.trim());
  const guardar = (): TestRequest => {
    boton('Guardar')!.click();
    fixture.detectChanges();
    return http.expectOne((r) => r.method === 'PUT' && r.url === URL_CONTENIDO);
  };
  const bloquesPut = (put: TestRequest) => put.request.body.bloques as Peticion[];
  // d551: Descartar pide confirmación (aquí, aceptada) y descarta al resolverse.
  const descartar = async () => {
    boton('Descartar')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  it('a la capa: cada profesor con su constancia en texto (PANEL y ALTA); al tema, nunca la constancia', () => {
    abrir();
    expect(profesores()).toEqual([
      { n: 1, nombre: 'Antonio Ruiz', permiso: { tipo: 'constancia', texto: 'Permiso marcado por María Ruiz el 03/11/2026' }, sedes: [] },
      { n: 3, nombre: 'Lucía Fernández', permiso: { tipo: 'constancia', texto: 'Permiso dado en el alta el 01/10/2026' }, sedes: [] },
    ]);
    const vista = JSON.stringify(ultimo('borrador'));
    expect(vista).not.toContain('permiso');
    expect(vista).not.toContain('María');
  });

  it('añadir: número nuevo al final, foco a su nombre y voz; sin nombre ni permiso no se guarda', () => {
    abrir();
    profesor({ accion: 'anadir' });
    expect(borrador().find((b) => b.clave === 'profesor.4.nombre')).toEqual(jasmine.objectContaining({ texto: '' }));
    expect(ultimo('foco-profesor')!['foco']).toBe('editar:4');
    expect(anuncio()).toBe('Profesor añadido. Escribe su nombre y marca el permiso.');
    expect(profesores().at(-1)).toEqual({ n: 4, nombre: '', permiso: { tipo: 'casilla', marcada: false }, sedes: [] });
    expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');

    boton('Guardar')!.click();
    fixture.detectChanges();
    http.expectNone((r) => r.method === 'PUT');
    expect(resumen()).toEqual(['Profesor nuevo: nombre', 'Profesor nuevo: permiso para publicar']);
    expect(q('.resumen a[data-ir="profesor.4.permiso"]')).not.toBeNull();
    expect((ultimo('errores')!['errores'] as Record<string, string>)['profesor.4.permiso']).toBe(FALTA);
  });

  it('permiso marcado: voz; el PUT lleva `permiso: true` solo en ese nombre; guardado, llega la constancia', () => {
    abrir();
    profesor({ accion: 'anadir' });
    escribir('profesor.4.nombre', 'Rosa Gil Prieto');
    profesor({ accion: 'permiso', n: 4, marcada: true });
    expect(anuncio()).toBe('Permiso marcado. Se guarda con tu nombre y la fecha.');
    expect(profesores().at(-1)!.permiso).toEqual({ tipo: 'casilla', marcada: true });
    profesor({ accion: 'permiso', n: 4, marcada: false });
    expect(anuncio()).toBe('Permiso desmarcado.');
    profesor({ accion: 'permiso', n: 4, marcada: true });

    const put = guardar();
    const bloques = bloquesPut(put);
    expect(bloques.filter((b) => 'permiso' in b)).toEqual([jasmine.objectContaining({ clave: 'profesor.4.nombre', texto: 'Rosa Gil Prieto', permiso: true })]);
    expect(bloques.every((b) => b.permiso === undefined || b.permiso === true)).toBeTrue();
    expect(bloques.find((b) => b.clave === 'profesor.1.sedes')!.texto).toBe('3,4');
    expect(bloques.find((b) => b.clave === 'profesor.3.sedes')!.texto).toBe('5');
    put.flush({
      version: 6,
      bloques: [...BLOQUES, fila('profesor.4.nombre', 'Rosa Gil Prieto', { permiso: { origen: 'PANEL', por: 'Carlos Sánchez', en: '2026-11-12T19:00:00' } })],
    });
    fixture.detectChanges();
    expect(profesores().at(-1)).toEqual({ n: 4, nombre: 'Rosa Gil Prieto', permiso: { tipo: 'constancia', texto: 'Permiso marcado por Carlos Sánchez el 12/11/2026' }, sedes: [] });
    expect(q('.barra')).toBeNull();
  });

  it('nombre cambiado en uno guardado: vuelve la casilla y bloquea; marcada, `true`; volver al nombre la quita', () => {
    abrir();
    escribir('profesor.1.nombre', 'Antonio R.');
    expect(profesores()[0].permiso).toEqual({ tipo: 'casilla', marcada: false });
    boton('Guardar')!.click();
    fixture.detectChanges();
    http.expectNone((r) => r.method === 'PUT');
    expect(resumen()).toEqual(['Antonio R.: permiso para publicar']);

    profesor({ accion: 'permiso', n: 1, marcada: true });
    escribir('profesor.1.nombre', 'Antonio Ruiz');
    // Vuelve la constancia y la casilla deja de contar (ni marcada, ni en el PUT).
    expect(profesores()[0].permiso).toEqual({ tipo: 'constancia', texto: 'Permiso marcado por María Ruiz el 03/11/2026' });
    expect(q('.barra')).toBeNull();
    escribir('profesor.1.nombre', 'Antonio Ruiz Carmona');
    expect(profesores()[0].permiso).toEqual({ tipo: 'casilla', marcada: false });

    profesor({ accion: 'permiso', n: 1, marcada: true });
    const put = guardar();
    expect(bloquesPut(put).find((b) => b.clave === 'profesor.1.nombre')).toEqual(jasmine.objectContaining({ texto: 'Antonio Ruiz Carmona', permiso: true }));
    expect(bloquesPut(put).find((b) => b.clave === 'profesor.3.nombre')!.permiso).toBeUndefined();
    put.flush({ version: 6, bloques: BLOQUES });
  });

  it('guardado sin cambios en los profesores: el PUT no manda permiso ni la constancia', () => {
    abrir();
    escribir('profesor.1.cargo', 'Maestro');
    const put = guardar();
    const cuerpo = JSON.stringify(put.request.body);
    expect(cuerpo).not.toContain('permiso');
    expect(cuerpo).not.toContain('María');
    expect(cuerpo).not.toContain('ALTA');
    put.flush({ version: 6, bloques: BLOQUES });
  });

  it('quitar: confirmación del boceto; fuera todas sus filas (también sus sedes); foco al siguiente; voz', async () => {
    abrir();
    profesor({ accion: 'quitar', n: 1 });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(confirmar).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({
        titulo: '¿Quitar a Antonio Ruiz de la web?',
        mensaje: 'Deja de salir al guardar. Su ficha en el equipo no cambia.',
        accion: 'Quitar profesor',
        destructivo: true,
      }),
    );
    expect(borrador().some((b) => b.clave.startsWith('profesor.1.'))).toBeFalse();
    expect(borrador().some((b) => b.clave === 'profesor.3.sedes')).toBeTrue();
    expect(ultimo('foco-profesor')!['foco']).toBe('quitar:3');
    expect(anuncio()).toBe('Antonio Ruiz: quitado. Se quita de la web al guardar.');
    expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
    const put = guardar();
    expect(bloquesPut(put).some((b) => b.clave.startsWith('profesor.1.'))).toBeFalse();
    put.flush({ version: 6, bloques: BLOQUES.filter((b) => !b.clave.startsWith('profesor.1.')) });
  });

  it('quitar el último: foco a «Añadir profesor»; un profesor nuevo sin nombre: «el profesor nuevo»', async () => {
    abrir({ version: 5, bloques: BLOQUES.filter((b) => !b.clave.startsWith('profesor.1.') && !b.clave.startsWith('profesor.3.')) });
    profesor({ accion: 'anadir' });
    expect(profesores()).toEqual([{ n: 1, nombre: '', permiso: { tipo: 'casilla', marcada: false }, sedes: [] }]);
    profesor({ accion: 'quitar', n: 1 });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(confirmar).toHaveBeenCalledWith(jasmine.objectContaining({ titulo: '¿Quitar a el profesor nuevo de la web?' }));
    expect(ultimo('foco-profesor')!['foco']).toBe('anadir');
    expect(anuncio()).toBe('el profesor nuevo: quitado. Se quita de la web al guardar.');
    expect(q('.barra')).toBeNull();
  });

  it('quitar cancelado: nada cambia y el foco vuelve a su «Quitar»', async () => {
    confirmar.and.resolveTo({ confirmado: false } as never);
    abrir();
    profesor({ accion: 'quitar', n: 3 });
    await fixture.whenStable();
    expect(borrador().some((b) => b.clave === 'profesor.3.nombre')).toBeTrue();
    expect(ultimo('foco-profesor')!['foco']).toBe('quitar:3');
    expect(q('.barra')).toBeNull();
  });

  it('d531: quitar y volver a añadir da un número nuevo con la casilla sin marcar', async () => {
    abrir();
    profesor({ accion: 'anadir' });
    profesor({ accion: 'permiso', n: 4, marcada: true });
    profesor({ accion: 'quitar', n: 4 });
    await fixture.whenStable();
    profesor({ accion: 'anadir' });
    expect(borrador().some((b) => b.clave.startsWith('profesor.4.'))).toBeFalse();
    expect(profesores().at(-1)).toEqual({ n: 5, nombre: '', permiso: { tipo: 'casilla', marcada: false }, sedes: [] });
  });

  it('d531: quitar, guardar y añadir: el número del quitado (ya fuera de lo guardado) no vuelve', async () => {
    abrir();
    profesor({ accion: 'quitar', n: 3 });
    await fixture.whenStable();
    fixture.detectChanges();
    const put = guardar();
    put.flush({ version: 6, bloques: BLOQUES.filter((b) => !b.clave.startsWith('profesor.3.')) });
    fixture.detectChanges();
    profesor({ accion: 'anadir' });
    expect(profesores().map((p) => p.n)).toEqual([1, 4]);
  });

  it('quitar con su foto subiendo: la subida se corta y no deja filas', async () => {
    abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'subir-foto', clave: 'profesor.3.foto', archivo: new File(['x'], 'lucia.jpg', { type: 'image/jpeg' }) });
    const subida = http.expectOne((r) => r.method === 'POST' && r.url === URL_IMAGENES);
    profesor({ accion: 'quitar', n: 3 });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(subida.cancelled).toBeTrue();
    expect(ultimo('fotos')!['subidas']).toEqual({});
    expect(borrador().some((b) => b.clave.startsWith('profesor.3.'))).toBeFalse();
  });

  it('foto de un profesor: entra en el borrador; sin descripción, «Describe su foto.»', () => {
    abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'subir-foto', clave: 'profesor.1.foto', archivo: new File(['x'], 'antonio.jpg', { type: 'image/jpeg' }) });
    http.expectOne((r) => r.method === 'POST' && r.url === URL_IMAGENES).flush({ imagenId: 60, url: 'https://x/a.webp' }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
    expect(ultimo('describir')!['clave']).toBe('profesor.1.foto');
    expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
    boton('Guardar')!.click();
    fixture.detectChanges();
    http.expectNone((r) => r.method === 'PUT');
    expect(resumen()).toEqual(['Foto de Antonio Ruiz: falta describirla']);
    expect((ultimo('errores')!['errores'] as Record<string, string>)['profesor.1.foto.alt']).toBe('Describe su foto.');
  });

  it('400 con el permiso (PERMISO_NOMBRE_CAMBIADO): a la casilla de ese profesor, con enlace en el resumen', () => {
    abrir();
    escribir('profesor.1.cargo', 'Maestro');
    const mensaje = 'Has cambiado el nombre: marca la casilla si tienes el permiso de esta persona. Sin él no puede salir en la web.';
    guardar().flush({ mensaje: 'Hay datos que no se pueden guardar.', errores: { 'profesor.3.permiso': mensaje } }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(resumen()).toEqual(['Lucía Fernández: permiso para publicar']);
    expect(profesores().find((p) => p.n === 3)!.permiso).toEqual({ tipo: 'casilla', marcada: false });
    expect((ultimo('errores')!['errores'] as Record<string, string>)['profesor.3.permiso']).toBe(mensaje);
    profesor({ accion: 'permiso', n: 3, marcada: true });
    const put = guardar();
    expect(bloquesPut(put).find((b) => b.clave === 'profesor.3.nombre')!.permiso).toBeTrue();
    put.flush({ version: 6, bloques: BLOQUES });
  });

  it('Descartar: vuelven los profesores, sus filas y las casillas; los números ya dados no se repiten', async () => {
    abrir();
    profesor({ accion: 'anadir' });
    profesor({ accion: 'permiso', n: 4, marcada: true });
    escribir('profesor.1.nombre', 'Otro nombre');
    profesor({ accion: 'permiso', n: 1, marcada: true });
    await descartar();
    expect(q('.barra')).toBeNull();
    expect(borrador().some((b) => b.clave.startsWith('profesor.4.'))).toBeFalse();
    expect(profesores()[0].permiso['tipo']).toBe('constancia');
    profesor({ accion: 'anadir' });
    expect(profesores().at(-1)).toEqual({ n: 5, nombre: '', permiso: { tipo: 'casilla', marcada: false }, sedes: [] });
  });

  it('salir con un profesor añadido avisa de los cambios', async () => {
    abrir();
    profesor({ accion: 'anadir' });
    confirmar.and.resolveTo({ confirmado: false } as never);
    expect(await fixture.componentInstance.puedeSalir()).toBeFalse();
    expect(confirmar).toHaveBeenCalledWith(jasmine.objectContaining({ titulo: 'Tienes 1 cambio sin guardar' }));
  });

  describe('permiso ligado al nombre con que se marcó (decisión del propietario, 12/11)', () => {
    const permisoDe = (n: number) => profesores().find((p) => p.n === n)!.permiso;
    const SIN_MARCAR = { tipo: 'casilla', marcada: false };

    it('nuevo: marcar y cambiar el nombre la desmarca, lo dice una vez y no mueve el foco; volver al nombre no la recupera', () => {
      abrir();
      profesor({ accion: 'anadir' });
      escribir('profesor.4.nombre', 'Ana');
      profesor({ accion: 'permiso', n: 4, marcada: true });
      const voz = spyOn(fixture.componentInstance.anuncio, 'set').and.callThrough();
      const focos = mensajes('foco-profesor').length;
      // Solo espacios en los extremos: el mismo nombre para la API (`strip()`), la casilla sigue.
      escribir('profesor.4.nombre', 'Ana ');
      expect(permisoDe(4)).toEqual({ tipo: 'casilla', marcada: true });
      // Letra a letra: se desmarca con la primera y se dice una sola vez.
      escribir('profesor.4.nombre', 'Ana P');
      escribir('profesor.4.nombre', 'Ana Pe');
      escribir('profesor.4.nombre', 'Ana Pedro');
      expect(permisoDe(4)).toEqual(SIN_MARCAR);
      expect(voz.calls.allArgs().filter(([t]) => t === 'Permiso desmarcado.').length).toBe(1);
      expect(anuncio()).toBe('Permiso desmarcado.');
      expect(mensajes('foco-profesor').length).toBe(focos);
      escribir('profesor.4.nombre', 'Ana');
      expect(permisoDe(4)).toEqual(SIN_MARCAR);

      boton('Guardar')!.click();
      fixture.detectChanges();
      http.expectNone((r) => r.method === 'PUT');
      expect(resumen()).toEqual(['Ana: permiso para publicar']);

      // Marcada otra vez con este nombre: `true` con este nombre.
      profesor({ accion: 'permiso', n: 4, marcada: true });
      const put = guardar();
      expect(bloquesPut(put).filter((b) => 'permiso' in b)).toEqual([jasmine.objectContaining({ clave: 'profesor.4.nombre', texto: 'Ana', permiso: true })]);
      put.flush({ version: 6, bloques: BLOQUES });
    });

    it('guardado renombrado: marcar y volver a renombrar la desmarca; volver al nombre marcado no la recupera; sin PUT', () => {
      abrir();
      escribir('profesor.1.nombre', 'Antonio R.');
      profesor({ accion: 'permiso', n: 1, marcada: true });
      escribir('profesor.1.nombre', 'Antonio Ruiz Gil');
      expect(permisoDe(1)).toEqual(SIN_MARCAR);
      expect(anuncio()).toBe('Permiso desmarcado.');
      escribir('profesor.1.nombre', 'Antonio R.');
      expect(permisoDe(1)).toEqual(SIN_MARCAR);
      boton('Guardar')!.click();
      fixture.detectChanges();
      http.expectNone((r) => r.method === 'PUT');
      expect(resumen()).toEqual(['Antonio R.: permiso para publicar']);
    });

    it('marcado y vuelto al nombre guardado: vuelve la constancia; el PUT no manda `permiso`', () => {
      abrir();
      escribir('profesor.1.nombre', 'Antonio R.');
      profesor({ accion: 'permiso', n: 1, marcada: true });
      escribir('profesor.1.nombre', 'Antonio Ruiz');
      expect(permisoDe(1)['tipo']).toBe('constancia');
      escribir('profesor.1.cargo', 'Maestro');
      const put = guardar();
      expect(bloquesPut(put).some((b) => 'permiso' in b)).toBeFalse();
      put.flush({ version: 6, bloques: BLOQUES });
    });

    it('guardado con éxito y renombrado después: pide de nuevo la casilla, sin marcar', () => {
      abrir();
      profesor({ accion: 'anadir' });
      escribir('profesor.4.nombre', 'Rosa Gil');
      profesor({ accion: 'permiso', n: 4, marcada: true });
      guardar().flush({ version: 6, bloques: [...BLOQUES, fila('profesor.4.nombre', 'Rosa Gil', { permiso: { origen: 'PANEL', por: 'Carlos Sánchez', en: '2026-11-12T19:00:00' } })] });
      fixture.detectChanges();
      expect(permisoDe(4)['tipo']).toBe('constancia');
      escribir('profesor.4.nombre', 'Rosa Gil Prieto');
      expect(permisoDe(4)).toEqual(SIN_MARCAR);
      boton('Guardar')!.click();
      fixture.detectChanges();
      http.expectNone((r) => r.method === 'PUT');
      expect(resumen()).toEqual(['Rosa Gil Prieto: permiso para publicar']);
    });

    it('409 y recargar con casillas marcadas: marcas limpias; el número 4 ya dado no se repite (el nuevo es el 5)', () => {
      spyOn(TestBed.inject(AvisosService), 'info').and.returnValue(3);
      abrir();
      profesor({ accion: 'anadir' });
      escribir('profesor.4.nombre', 'Rosa Gil');
      profesor({ accion: 'permiso', n: 4, marcada: true });
      escribir('profesor.1.nombre', 'Antonio R.');
      profesor({ accion: 'permiso', n: 1, marcada: true });
      guardar().flush({ mensaje: 'Otra persona…' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();
      fixture.componentInstance.recargar();
      fixture.detectChanges();
      http.expectOne(URL).flush(PUBLICADA);
      http.expectOne(URL_CONTENIDO).flush(CONTENIDO);
      http.match((r) => r.url.startsWith(URL_PUBLICA)).forEach((r) => r.flush(r.request.url.endsWith('/eventos') ? { proximos: [], anteriores: [] } : { sedes: [] }));
      fixture.detectChanges();
      const ventana = iframe()!.contentWindow!;
      enviados = jasmine.isSpy(ventana.postMessage) ? (ventana.postMessage as jasmine.Spy) : spyOn(ventana, 'postMessage');
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });

      escribir('profesor.1.nombre', 'Antonio R.');
      expect(permisoDe(1)).toEqual(SIN_MARCAR);
      profesor({ accion: 'anadir' });
      expect(profesores().at(-1)).toEqual({ n: 5, nombre: '', permiso: SIN_MARCAR, sedes: [] });
      expect(profesores().some((p) => p.n === 4)).toBeFalse();
    });
  });

  it('d521: sin `listo` de la capa no sale la constancia hacia el iframe; con `listo`, sí', () => {
    const todos: unknown[] = [];
    const espiadas = new WeakSet<Window>();
    const real = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentWindow')!.get!;
    spyOnProperty(HTMLIFrameElement.prototype, 'contentWindow').and.callFake(function (this: HTMLIFrameElement) {
      const w = real.call(this) as Window | null;
      if (w && !espiadas.has(w)) {
        espiadas.add(w);
        w.postMessage = ((m: unknown) => todos.push(m)) as typeof w.postMessage;
      }
      return w;
    });
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(PUBLICADA);
    http.expectOne(URL_CONTENIDO).flush(CONTENIDO);
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes: [{ nombre: 'Herrera', slug: 'herrera' }] });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
    fixture.detectChanges();
    fixture.detectChanges();
    expect(iframe()).not.toBeNull();
    // Algo sí sale (errores, fotos), pero nada de quién marcó ni cuándo.
    expect(todos.length).toBeGreaterThan(0);
    const antes = JSON.stringify(todos);
    expect(todos.some((m) => (m as Mensaje).tipo === 'profesores')).toBeFalse();
    for (const prohibido of ['María Ruiz', 'Permiso marcado', 'Permiso dado', 'constancia', '03/11/2026', '01/10/2026']) {
      expect(antes).not.toContain(prohibido);
    }

    window.dispatchEvent(new MessageEvent('message', { data: { canal: CANAL_EDITOR_WEB, tipo: 'listo' }, origin: location.origin, source: iframe()!.contentWindow }));
    fixture.detectChanges();
    const lista = todos.filter((m) => (m as Mensaje).tipo === 'profesores');
    expect(lista.length).toBe(1);
    expect(JSON.stringify(lista[0])).toContain('Permiso marcado por María Ruiz el 03/11/2026');
  });

  it('quitar sin cabeceras guardadas: la sección no se mueve y cuenta un único cambio', async () => {
    abrir({
      version: 5,
      bloques: [
        fila('presentacion.texto', 'Hola', { orden: 2 }),
        fila('profesor.1.nombre', 'Antonio Ruiz', { orden: 1, permiso: PANEL }),
        fila('profesor.2.nombre', 'Lucía Fernández', { orden: 4, permiso: ALTA }),
        fila('galeria.1', 'Clase', { orden: 3, imagenId: 5, imagenUrl: 'https://x/g.webp' }),
      ],
    });
    profesor({ accion: 'quitar', n: 1 });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
  });

  it('foto de un profesor que sube mientras se le quita y se añade otro: la respuesta tardía no llega a ninguno', async () => {
    abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'subir-foto', clave: 'profesor.3.foto', archivo: new File(['x'], 'lucia.jpg', { type: 'image/jpeg' }) });
    const subida = http.expectOne((r) => r.method === 'POST' && r.url === URL_IMAGENES);
    profesor({ accion: 'quitar', n: 3 });
    await fixture.whenStable();
    fixture.detectChanges();
    profesor({ accion: 'anadir' });
    expect(profesores().at(-1)!.n).toBe(4);
    expect(subida.cancelled).toBeTrue();
    // La respuesta ya no tiene a quién llegar: la petición está cortada.
    expect(() => subida.flush({ imagenId: 61, url: 'https://x/tarde.webp' }, { status: 201, statusText: 'Created' })).toThrow();
    fixture.detectChanges();
    expect(borrador().some((b) => b.clave === 'profesor.3.foto' || b.clave === 'profesor.4.foto')).toBeFalse();
    expect(JSON.stringify(borrador())).not.toContain('tarde.webp');
    expect(ultimo('fotos')!['subidas']).toEqual({});
  });

  it('mensajes de profesor mal formados se ignoran (número fuera de 1-99, sin marcada)', () => {
    abrir();
    profesor({ accion: 'quitar', n: 100 });
    profesor({ accion: 'permiso', n: 1 });
    profesor({ accion: 'otra' });
    expect(confirmar).not.toHaveBeenCalled();
    expect(q('.barra')).toBeNull();
  });

  /** W12f-3: sedes de cada profesor (`profesor.<n>.sedes`). La interfaz es de otra pieza: aquí, el contrato y el guardado. */
  describe('sedes de cada profesor (W12f-3)', () => {
    /** Las activas de la vista previa, en el orden de la API. La 7 (inactiva) no está. */
    const SEDES_API = [
      { id: 6, nombre: 'Casariche', slug: 'casariche' },
      { id: 3, nombre: 'Estepa', slug: 'estepa' },
      { id: 4, nombre: 'Herrera', slug: 'herrera' },
      { id: 5, nombre: 'Lora de Estepa', slug: null },
    ];
    const ELEGIBLES = SEDES_API.map(({ id, nombre }) => ({ id, nombre }));
    const ELIGE = 'Elige sedes de tu escuela.';
    const sedes = (n: unknown, valor: unknown) => profesor({ accion: 'sedes', n, sedes: valor });
    const filaBorrador = (clave: string) => borrador().find((b) => b.clave === clave);
    const sedesDe = (n: number) => profesores().find((p) => p.n === n)!.sedes;
    const abrirConSedes = (contenido = CONTENIDO) => abrir(contenido, SEDES_API);
    const conFila = (clave: string, texto: string): ContenidoWeb => ({
      version: 5,
      bloques: BLOQUES.map((b) => (b.clave === clave ? { ...b, texto } : b)),
    });

    it('las sedes de la vista previa conservan su id; a la capa, las elegibles en orden y las marcadas de cada uno', () => {
      abrirConSedes();
      expect(fixture.componentInstance.listas()!.sedes.map((s) => s.id)).toEqual([6, 3, 4, 5]);
      expect(ultimo('profesores')!['sedes']).toEqual(ELEGIBLES);
      expect(sedesDe(1)).toEqual([3, 4]);
      expect(sedesDe(3)).toEqual([5]);
      // Lo de la vista previa (selector «Una sede») sigue igual.
      expect(fixture.componentInstance.listas()!.sedes[0]).toEqual({ id: 6, nombre: 'Casariche', slug: 'casariche' });
    });

    it('invariante c: cambiar solo las sedes cuenta (barra, aviso al salir); volver al conjunto guardado deja de contar', async () => {
      abrirConSedes();
      sedes(1, [5, 3]);
      expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3,5');
      expect(sedesDe(1)).toEqual([3, 5]);
      expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
      confirmar.and.resolveTo({ confirmado: false } as never);
      expect(await fixture.componentInstance.puedeSalir()).toBeFalse();
      expect(confirmar).toHaveBeenCalledWith(jasmine.objectContaining({ titulo: 'Tienes 1 cambio sin guardar' }));
      // De vuelta, en otro orden: la fila vuelve a ser la guardada.
      sedes(1, [4, 3]);
      expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3,4');
      expect(q('.barra')).toBeNull();
    });

    it('invariante c: guardada en otro orden («4,3»): el mismo conjunto no cuenta y la fila queda byte a byte', () => {
      abrirConSedes(conFila('profesor.1.sedes', '4,3'));
      sedes(1, [3, 4]);
      expect(q('.barra')).toBeNull();
      expect(filaBorrador('profesor.1.sedes')!.texto).toBe('4,3');
      sedes(1, [3]);
      sedes(1, [3, 4]);
      expect(filaBorrador('profesor.1.sedes')!.texto).toBe('4,3');
      expect(q('.barra')).toBeNull();
    });

    it('invariante a: la fila de quien no cambia viaja en el PUT byte a byte, también con sedes inactivas', () => {
      abrirConSedes(conFila('profesor.3.sedes', '7,5'));
      expect(sedesDe(3)).toEqual([5]);
      // Pedir el mismo conjunto elegible: nada cambia y la capa recibe el estado que vale.
      const antes = mensajes('profesores').length;
      sedes(3, [5]);
      expect(mensajes('profesores').length).toBe(antes + 1);
      expect(q('.barra')).toBeNull();
      sedes(1, [6]);
      const put = guardar();
      const bloques = bloquesPut(put);
      expect(bloques.find((b) => b.clave === 'profesor.3.sedes') as unknown).toEqual({ clave: 'profesor.3.sedes', texto: '7,5', imagenId: null, orden: 0, oculto: false });
      expect(bloques.find((b) => b.clave === 'profesor.1.sedes')!.texto).toBe('6');
      put.flush({ version: 6, bloques: conFila('profesor.3.sedes', '7,5').bloques.map((b) => (b.clave === 'profesor.1.sedes' ? { ...b, texto: '6' } : b)) });
    });

    it('invariante b: cambiar sedes no pide ni desmarca el permiso; el PUT no lleva `permiso` ni la constancia', () => {
      abrirConSedes();
      profesor({ accion: 'anadir' });
      escribir('profesor.4.nombre', 'Rosa Gil');
      profesor({ accion: 'permiso', n: 4, marcada: true });
      sedes(1, [6]);
      sedes(4, [3]);
      expect(profesores().find((p) => p.n === 1)!.permiso['tipo']).toBe('constancia');
      expect(profesores().find((p) => p.n === 4)!.permiso).toEqual({ tipo: 'casilla', marcada: true });
      const put = guardar();
      const bloques = bloquesPut(put);
      expect(bloques.filter((b) => 'permiso' in b)).toEqual([jasmine.objectContaining({ clave: 'profesor.4.nombre', permiso: true })]);
      expect(JSON.stringify(put.request.body)).not.toContain('María');
      put.flush({ version: 6, bloques: BLOQUES });
    });

    it('invariante f: guardado con éxito, borrador y guardado coinciden con lo que devuelve la API (CSV normalizado)', () => {
      abrirConSedes();
      sedes(1, [6, 5]);
      const put = guardar();
      expect(bloquesPut(put).find((b) => b.clave === 'profesor.1.sedes')!.texto).toBe('6,5');
      // La API conserva el orden recibido; aquí devuelve otro para comprobar que manda lo suyo.
      put.flush(conFila('profesor.1.sedes', '5,6'));
      fixture.detectChanges();
      expect(q('.barra')).toBeNull();
      expect(filaBorrador('profesor.1.sedes')!.texto).toBe('5,6');
      expect(sedesDe(1)).toEqual([6, 5]);
      // Y el mismo conjunto ya no cuenta.
      sedes(1, [5, 6]);
      expect(q('.barra')).toBeNull();
    });

    it('invariante d: Descartar restaura las sedes guardadas y la capa recibe el estado restaurado', async () => {
      abrirConSedes();
      sedes(1, []);
      sedes(3, [6, 4]);
      expect(sedesDe(1)).toEqual([]);
      await descartar();
      expect(q('.barra')).toBeNull();
      expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3,4');
      expect(filaBorrador('profesor.3.sedes')!.texto).toBe('5');
      expect(sedesDe(1)).toEqual([3, 4]);
      expect(sedesDe(3)).toEqual([5]);
    });

    it('invariante e: un profesor nuevo lleva sedes; al quitarlo se van; su número no vuelve', async () => {
      abrirConSedes();
      profesor({ accion: 'anadir' });
      sedes(4, [5, 6]);
      expect(filaBorrador('profesor.4.sedes')).toEqual(jasmine.objectContaining({ texto: '6,5', orden: 0, oculto: false }));
      expect(sedesDe(4)).toEqual([6, 5]);
      expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
      profesor({ accion: 'quitar', n: 4 });
      await fixture.whenStable();
      fixture.detectChanges();
      expect(borrador().some((b) => b.clave.startsWith('profesor.4.'))).toBeFalse();
      expect(q('.barra')).toBeNull();
      profesor({ accion: 'anadir' });
      expect(profesores().at(-1)!.n).toBe(5);
      expect(sedesDe(5)).toEqual([]);
    });

    it('invariante g: 400 en `profesor.<n>.sedes` va al resumen y a la capa; se limpia al cambiar sus sedes', () => {
      abrirConSedes(conFila('profesor.1.sedes', '3,99'));
      escribir('profesor.1.cargo', 'Maestro');
      guardar().flush({ mensaje: 'Hay datos que no se pueden guardar.', errores: { 'profesor.1.sedes': ELIGE } }, { status: 400, statusText: 'Bad Request' });
      fixture.detectChanges();
      // Como los demás errores de profesor: con su nombre y con enlace al botón de sus sedes.
      expect(resumen()).toEqual(['Antonio Ruiz: sedes donde da clase']);
      q<HTMLAnchorElement>('.resumen a[data-ir="profesor.1.sedes"]')!.click();
      fixture.detectChanges();
      expect(ultimo('ir-texto')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'profesor.1.sedes' });
      expect((ultimo('errores')!['errores'] as Record<string, string>)['profesor.1.sedes']).toBe(ELIGE);
      // Marcar una casilla (Herrera): la fila se reescribe con solo elegibles y el error se va.
      sedes(1, [3, 4]);
      expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3,4');
      expect(q('.resumen')).toBeNull();
      expect(ultimo('errores')!['errores']).toEqual({});
      const put = guardar();
      expect(bloquesPut(put).find((b) => b.clave === 'profesor.1.sedes')!.texto).toBe('3,4');
      put.flush({ version: 6, bloques: BLOQUES });
    });

    describe('fila de sedes rechazada por la API (M1: «3,99», 99 no es sede de la escuela)', () => {
      const rechazar = () => {
        abrirConSedes(conFila('profesor.1.sedes', '3,99'));
        escribir('profesor.1.cargo', 'Maestro');
        guardar().flush({ mensaje: 'Hay datos que no se pueden guardar.', errores: { 'profesor.1.sedes': ELIGE } }, { status: 400, statusText: 'Bad Request' });
        fixture.detectChanges();
      };

      it('tras el 400, marcar 4 y desmarcar 4 deja «3» (no vuelve a «3,99»), cuenta como cambio y el PUT manda «3»', () => {
        rechazar();
        expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
        sedes(1, [3, 4]);
        expect(q('.resumen')).toBeNull();
        sedes(1, [3]);
        expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3');
        expect(sedesDe(1)).toEqual([3]);
        expect(texto('.barra__texto strong')).toBe('2 cambios sin guardar');
        const put = guardar();
        expect(bloquesPut(put).find((b) => b.clave === 'profesor.1.sedes')!.texto).toBe('3');
        put.flush({ version: 6, bloques: conFila('profesor.1.sedes', '3').bloques });
      });

      it('descartar no lo olvida (vuelve a lo guardado que falló); guardar bien sí: luego, volver al conjunto guardado deja la fila', async () => {
        rechazar();
        await descartar();
        expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3,99');
        sedes(1, [3, 4]);
        sedes(1, [3]);
        expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3');
        expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
        // Guardado con éxito (aquí la API devuelve otra vez «3,99»): lo guardado es otro y la regla normal vuelve.
        guardar().flush({ version: 7, bloques: conFila('profesor.1.sedes', '3,99').bloques });
        fixture.detectChanges();
        sedes(1, [3, 4]);
        sedes(1, [3]);
        expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3,99');
        expect(q('.barra')).toBeNull();
      });
    });

    it('409 tras cambiar sedes: conflicto como hoy; el borrador no se toca', () => {
      abrirConSedes();
      sedes(1, [6]);
      guardar().flush({ mensaje: 'Otra persona…' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();
      expect(fixture.componentInstance.conflicto()).toBeTrue();
      expect(fixture.componentInstance.erroresServidor()).toEqual({});
      expect(filaBorrador('profesor.1.sedes')!.texto).toBe('6');
    });

    it('invariante h: peticiones que no valen no tocan el borrador (forma, profesor ausente, más ids que sedes, guardando)', () => {
      abrirConSedes();
      const antes = JSON.stringify(fixture.componentInstance.borrador());
      for (const [n, valor] of [
        [9, [3]],
        [1, [6, 3, 4, 5, 99]],
        [0, [3]],
        [1, [3, 3]],
        [1, ['3']],
        [1, '3,4'],
        [1, [-1]],
        [1, null],
      ] as [unknown, unknown][]) {
        sedes(n, valor);
      }
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'profesor', accion: 'sedes', n: 1 });
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista: [], sedes: [] });
      expect(JSON.stringify(fixture.componentInstance.borrador())).toBe(antes);
      expect(q('.barra')).toBeNull();

      // Mientras guarda, no se atiende (lo cambiado se perdería al llegar lo guardado) y la capa recibe el estado que vale.
      escribir('profesor.1.cargo', 'Maestro');
      const put = guardar();
      sedes(1, [6]);
      expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3,4');
      expect(sedesDe(1)).toEqual([3, 4]);
      put.flush({ version: 6, bloques: BLOQUES });
    });

    it('«Listo» en el diálogo de la capa (`anunciar-sedes`): voz con las sedes en orden de lista o «Sin sedes.»', () => {
      abrirConSedes();
      profesor({ accion: 'anunciar-sedes', n: 1 });
      expect(anuncio()).toBe('Sedes: Estepa, Herrera.');
      sedes(1, []);
      profesor({ accion: 'anunciar-sedes', n: 1 });
      expect(anuncio()).toBe('Sin sedes.');
      profesor({ accion: 'anunciar-sedes', n: 9 });
      expect(anuncio()).toBe('Sin sedes.');
    });

    describe('panel estrecho: la hoja de sedes (d541)', () => {
      const hojaSedes = (n: unknown, id: unknown) => llegar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja-sedes', n, id });
      const casillas = () => Array.from(el().querySelectorAll<HTMLInputElement>('.flot--sedes input[type="checkbox"]'));
      const etiquetas = () => Array.from(el().querySelectorAll('.flot--sedes label.casilla')).map((l) => l.textContent!.trim());
      const marcadas = () => casillas().map((c) => c.checked);
      const listo = () => q<HTMLButtonElement>('.flot--sedes .flot__acciones button')!;
      const abrirEstrecho = async () => {
        abrirConSedes();
        fixture.componentInstance.estrecho.set(true);
        fixture.detectChanges();
        hojaSedes(1, 4);
        await fixture.whenStable();
      };

      it('abre como diálogo con título, ayuda, leyenda oculta y una casilla por sede (marcadas las suyas); foco a la primera', async () => {
        await abrirEstrecho();
        const hoja = q('.flot--sedes')!;
        expect(hoja.classList).toContain('flot--capa');
        expect(hoja.getAttribute('role')).toBe('dialog');
        expect(hoja.getAttribute('aria-labelledby')).toBe('hoja-sedes-t');
        // Como el boceto: la ayuda describe el grupo de casillas, no el diálogo.
        expect(hoja.hasAttribute('aria-describedby')).toBeFalse();
        expect(q('.flot--sedes fieldset.casillas')!.getAttribute('aria-describedby')).toBe('hoja-sedes-a');
        expect(texto('#hoja-sedes-t')).toBe('Sedes de Antonio Ruiz');
        expect(texto('#hoja-sedes-a')).toBe('Salen en su tarjeta, sobre la línea. No cambia en qué grupos da clase.');
        expect(texto('.flot--sedes legend.oculto')).toBe('Sedes donde da clase');
        expect(etiquetas()).toEqual(['Casariche', 'Estepa', 'Herrera', 'Lora de Estepa']);
        expect(marcadas()).toEqual([false, true, true, false]);
        expect(texto('.flot--sedes .flot__acciones button')).toBe('Listo');
        expect(el().querySelectorAll('.flot--sedes button').length).toBe(1);
        expect(document.activeElement).toBe(casillas()[0]);
      });

      it('cada casilla va por el mismo camino que `sedes`: llega al borrador y a la capa; «Listo» cierra, foco y voz', async () => {
        await abrirEstrecho();
        casillas()[0].click();
        fixture.detectChanges();
        expect(filaBorrador('profesor.1.sedes')!.texto).toBe('6,3,4');
        expect(sedesDe(1)).toEqual([6, 3, 4]);
        expect(marcadas()).toEqual([true, true, true, false]);
        casillas()[2].click();
        fixture.detectChanges();
        expect(filaBorrador('profesor.1.sedes')!.texto).toBe('6,3');
        listo().click();
        fixture.detectChanges();
        expect(q('.flot--sedes')).toBeNull();
        expect(ultimo('respuesta-hoja-sedes')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id: 4, devolverFoco: true });
        expect(document.activeElement).toBe(iframe());
        expect(anuncio()).toBe('Sedes: Casariche, Estepa.');
      });

      it('Escape cierra devolviendo el foco, sin voz (boceto); pulsar fuera cierra sin foco ni voz', async () => {
        await abrirEstrecho();
        fixture.componentInstance.anuncio.set('');
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();
        expect(q('.flot--sedes')).toBeNull();
        expect(ultimo('respuesta-hoja-sedes')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id: 4, devolverFoco: true });
        expect(document.activeElement).toBe(iframe());
        expect(anuncio()).toBe('');

        hojaSedes(1, 5);
        await fixture.whenStable();
        document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
        fixture.detectChanges();
        expect(q('.flot--sedes')).toBeNull();
        expect(ultimo('respuesta-hoja-sedes')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id: 5, devolverFoco: false });
        expect(anuncio()).toBe('');
      });

      it('mientras guarda, la casilla vuelve a su valor (pinta lo que manda el panel)', async () => {
        await abrirEstrecho();
        escribir('profesor.1.cargo', 'Maestro');
        const put = guardar();
        casillas()[0].click();
        fixture.detectChanges();
        expect(casillas()[0].checked).toBeFalse();
        expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3,4');
        put.flush({ version: 6, bloques: BLOQUES });
      });

      it('la capa la cierra sola (`cerrar-hoja-sedes` con su id): se quita sin responder; con otro id, sigue', async () => {
        await abrirEstrecho();
        const antes = mensajes('respuesta-hoja-sedes').length;
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-sedes', id: 3 });
        expect(q('.flot--sedes')).not.toBeNull();
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'cerrar-hoja-sedes', id: 4 });
        expect(q('.flot--sedes')).toBeNull();
        expect(mensajes('respuesta-hoja-sedes').length).toBe(antes);
      });

      it('se cierra sin foco ni voz al abrir otra hoja: la de acciones de un bloque o la de un menú de la vista previa', async () => {
        await abrirEstrecho();
        fixture.componentInstance.anuncio.set('');
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'hoja', bloque: 'profesores' });
        expect(q('.flot--sedes')).toBeNull();
        expect(q('.hoja')).not.toBeNull();
        expect(ultimo('respuesta-hoja-sedes')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id: 4, devolverFoco: false });

        hojaSedes(1, 5);
        await fixture.whenStable();
        expect(q('.flot--sedes')).not.toBeNull();
        expect(q('.hoja')).toBeNull();
        llegar({
          canal: CANAL_EDITOR_WEB,
          tipo: 'hoja-menu',
          id: 7,
          menu: 'foto',
          titulo: 'Foto de «Taekwondo en seis pueblos»',
          opciones: [{ op: 'subir', texto: 'Cambiar foto', pie: '', icono: 'foto', desactivada: false, peligro: false }],
        });
        expect(q('.flot--sedes')).toBeNull();
        expect(q('.flot--capa [role="menuitem"]')).not.toBeNull();
        expect(ultimo('respuesta-hoja-sedes')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id: 5, devolverFoco: false });
        expect(anuncio()).toBe('');
      });

      it('se cierra al cambiar de página y no vuelve al volver a Inicio', async () => {
        await abrirEstrecho();
        const pagina = q<HTMLSelectElement>('#web-contenido-pagina')!;
        pagina.value = 'tarifas';
        pagina.dispatchEvent(new Event('change'));
        fixture.detectChanges();
        expect(q('.flot--sedes')).toBeNull();
        pagina.value = 'inicio';
        pagina.dispatchEvent(new Event('change'));
        fixture.detectChanges();
        expect(fixture.componentInstance.pagina()).toBe('inicio');
        expect(q('.flot--sedes')).toBeNull();
      });

      it('pasar el foco a la vista previa (`window:blur`) la cierra sin devolver el foco; sin el foco en ella, sigue', async () => {
        await abrirEstrecho();
        window.dispatchEvent(new Event('blur'));
        fixture.detectChanges();
        expect(q('.flot--sedes')).not.toBeNull();
        iframe()!.focus();
        window.dispatchEvent(new Event('blur'));
        fixture.detectChanges();
        expect(q('.flot--sedes')).toBeNull();
        expect(ultimo('respuesta-hoja-sedes')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id: 4, devolverFoco: false });
      });

      it('no se abre con el panel ancho, sin el profesor o sin sedes que elegir: se contesta al momento sin foco', () => {
        abrirConSedes();
        fixture.componentInstance.estrecho.set(false);
        hojaSedes(1, 1);
        expect(q('.flot--sedes')).toBeNull();
        expect(ultimo('respuesta-hoja-sedes')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id: 1, devolverFoco: false });
        fixture.componentInstance.estrecho.set(true);
        hojaSedes(9, 2);
        expect(q('.flot--sedes')).toBeNull();
        expect(ultimo('respuesta-hoja-sedes')!['id']).toBe(2);
        // Al ensancharse el panel, la hoja abierta se cierra sin mover el foco.
        hojaSedes(1, 3);
        expect(q('.flot--sedes')).not.toBeNull();
        fixture.componentInstance.estrecho.set(false);
        fixture.detectChanges();
        expect(q('.flot--sedes')).toBeNull();
        expect(ultimo('respuesta-hoja-sedes')).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id: 3, devolverFoco: false });
      });
    });

    it('sin la lista de sedes (falló la vista previa): no se puede cambiar ninguna; lo guardado sigue intacto', () => {
      fixture = TestBed.createComponent(WebContenidoComponent);
      fixture.detectChanges();
      http.expectOne(URL).flush(PUBLICADA);
      http.expectOne(URL_CONTENIDO).flush(CONTENIDO);
      http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
      http.expectOne((r) => r.url === URL_PUBLICA).flush('caída', { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      enviados = spyOn(iframe()!.contentWindow!, 'postMessage') as jasmine.Spy;
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
      expect(ultimo('profesores')!['sedes']).toEqual([]);
      expect(sedesDe(1)).toEqual([]);
      sedes(1, [3]);
      sedes(1, []);
      expect(filaBorrador('profesor.1.sedes')!.texto).toBe('3,4');
      expect(q('.barra')).toBeNull();
    });
  });
});
