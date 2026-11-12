import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../environments/environment';
import { CANAL_EDITOR_WEB } from '../../../web-plantilla/editor/puente-editor';
import { AvisosService } from '../../avisos/avisos.service';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { salirSinGuardarGuard } from '../../productos-alumno/salir-sin-guardar.guard';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import { BloqueContenido, ContenidoWeb, ORIGEN_EDITOR } from './web-contenido-vista';
import { WebContenidoComponent } from './web-contenido.component';

const URL = `${environment.apiUrl}/escuela/web`;
const URL_CONTENIDO = `${URL}/contenido`;
const URL_PUBLICA = `${environment.apiUrl}/publico/web`;
const ORIGEN = 'about:blank#';
const PUBLICADA: EstadoWebEscuela = { tema: 'linea-comarcal', publicada: true, publicadaEn: '2026-11-01T10:00:00Z', temasDisponibles: ['linea-comarcal'] };
const NUEVA: EstadoWebEscuela = { tema: null, publicada: false, publicadaEn: null, temasDisponibles: ['linea-comarcal'] };

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
const BLOQUES: BloqueContenido[] = [
  fila('portada.titular', 'Taekwondo en seis pueblos'),
  fila('portada.foto', 'Niños en fila', { imagenId: 7, imagenUrl: 'https://x/p.webp' }),
  fila('profesor.1.nombre', 'Ana Ruiz', { permiso: { origen: 'ALTA', por: null, en: '2026-10-01T10:00:00' } }),
];
const CONTENIDO: ContenidoWeb = { version: 5, bloques: BLOQUES };

/**
 * W12d: textos en el sitio y guardar (boceto `pintarBarra`, `pintarResumen`, `pintarConflicto`, guardar, descartar,
 * `salir()`; estados `contenido`, `errores`, `guardando`, `guardado`, `error` y `conflicto`).
 */
describe('WebContenidoComponent: guardar (W12d)', () => {
  let fixture: ComponentFixture<WebContenidoComponent>;
  let http: HttpTestingController;
  let confirmar: jasmine.Spy;
  let exito: jasmine.Spy;
  let error: jasmine.Spy;
  let info: jasmine.Spy;
  let cerrar: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: ORIGEN_EDITOR, useValue: ORIGEN }],
    });
    spyOnProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap').and.returnValue(convertToParamMap({}));
    http = TestBed.inject(HttpTestingController);
    spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    confirmar = spyOn(TestBed.inject(ConfirmacionService), 'confirmar').and.resolveTo({ confirmado: false } as never);
    const avisos = TestBed.inject(AvisosService);
    exito = spyOn(avisos, 'exito').and.returnValue(1);
    error = spyOn(avisos, 'error').and.returnValue(2);
    info = spyOn(avisos, 'info').and.returnValue(3);
    cerrar = spyOn(avisos, 'cerrar');
  });

  afterEach(() => {
    fixture?.destroy();
    http.verify();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement = HTMLElement>(selector: string) => el().querySelector<T>(selector);
  const texto = (selector: string) => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const iframe = () => q<HTMLIFrameElement>('iframe');
  const boton = (t: string) =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('.barra button')).find((b) => b.textContent!.trim() === t)!;

  function abrir(estado = PUBLICADA, contenido = CONTENIDO, sedes = [{ nombre: 'Herrera', slug: 'herrera' }, { nombre: 'Estepa', slug: 'estepa' }]): void {
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(estado);
    http.expectOne(URL_CONTENIDO).flush(contenido);
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
    fixture.detectChanges();
  }

  function llegar(data: unknown): void {
    window.dispatchEvent(new MessageEvent('message', { data, origin: location.origin, source: iframe()!.contentWindow }));
    fixture.detectChanges();
  }
  const cambiar = (clave: string, t: string) => llegar({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave, texto: t });
  const alIframe = (): jasmine.Spy => spyOn(iframe()!.contentWindow!, 'postMessage') as jasmine.Spy;

  it('sin cambios no hay barra; un texto cambiado la enseña con «· Al guardar se publican en tu web.»', () => {
    abrir();
    expect(q('.barra')).toBeNull();
    cambiar('menu.horarios', 'Horas');
    expect(texto('.barra__texto')).toBe('1 cambio sin guardar · Al guardar se publican en tu web.');
    expect(q('.barra')!.getAttribute('aria-label')).toBe('Cambios sin guardar');
    expect(texto('#web-contenido-pagina option[value="inicio"]')).toBe('Inicio, con cabecera y pie · 1 cambio');
    cambiar('cabecera.boton', 'Apúntate');
    expect(texto('.barra__texto strong')).toBe('2 cambios sin guardar');
    // Volver al texto del tema deja de ser un cambio.
    cambiar('menu.horarios', 'Horarios');
    expect(texto('.barra__texto strong')).toBe('1 cambio sin guardar');
  });

  it('web nueva (sin publicar): sin la segunda frase', () => {
    abrir(NUEVA);
    cambiar('menu.horarios', 'Horas');
    expect(texto('.barra__texto')).toBe('1 cambio sin guardar');
  });

  it('Guardar: PUT con todas las filas y la versión; «Guardando…»; luego el contenido nuevo y el aviso', () => {
    abrir();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'defectos', textos: { 'portada.frase': 'Grupos por edad.' } });
    cambiar('menu.horarios', 'Horas');
    cambiar('cabecera.boton', 'Preinscribirme');
    const enviados = alIframe();
    boton('Guardar').click();
    fixture.detectChanges();
    const peticion = http.expectOne((r) => r.method === 'PUT' && r.url === URL_CONTENIDO);
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.body).toEqual({
      version: 5,
      bloques: [
        { clave: 'portada.titular', texto: 'Taekwondo en seis pueblos', imagenId: null, orden: 0, oculto: false },
        { clave: 'portada.foto', texto: 'Niños en fila', imagenId: 7, orden: 0, oculto: false },
        { clave: 'profesor.1.nombre', texto: 'Ana Ruiz', imagenId: null, orden: 0, oculto: false },
        { clave: 'menu.horarios', texto: 'Horas', imagenId: null, orden: 0, oculto: false },
      ],
    });
    const guardar = q<HTMLButtonElement>('.barra .primario')!;
    expect(guardar.getAttribute('aria-busy')).toBe('true');
    expect(texto('.barra .primario')).toBe('Guardando…');
    expect(q('.barra .secundario')!.getAttribute('aria-disabled')).toBe('true');

    const nuevo = [...BLOQUES, fila('menu.horarios', 'Horas')];
    peticion.flush({ version: 6, bloques: nuevo });
    fixture.detectChanges();
    expect(q('.barra')).toBeNull();
    expect(exito).toHaveBeenCalledOnceWith('Cambios guardados. Ya se ven en tu web.');
    expect(enviados.calls.allArgs().map(([m]) => m)).toContain(
      jasmine.objectContaining({ tipo: 'borrador', bloques: nuevo.map(({ clave, texto: t, imagenUrl, orden, oculto }) => ({ clave, texto: t, imagenUrl, orden, oculto })) }),
    );
    expect(document.activeElement).toBe(q('h1'));
    // El siguiente guardado va con la versión nueva.
    cambiar('menu.horarios', 'Horario');
    boton('Guardar').click();
    expect(http.expectOne((r) => r.method === 'PUT').request.body.version).toBe(6);
  });

  it('Guardar no lleva la página arriba: el foco va al título sin desplazar la vista', () => {
    abrir();
    const relleno = document.createElement('div');
    relleno.style.height = '6000px';
    document.body.appendChild(relleno);
    try {
      cambiar('menu.horarios', 'Horas');
      const titulo = q('h1')!;
      window.scrollTo({ top: titulo.getBoundingClientRect().top + window.scrollY + 2000, behavior: 'auto' });
      const antes = window.scrollY;
      expect(antes).toBeGreaterThan(1000);
      // Como al tocarlo: el foco está en «Guardar» (si ya estuviera en el título, enfocarlo no desplazaría nada).
      boton('Guardar').focus({ preventScroll: true });
      boton('Guardar').click();
      http.expectOne((r) => r.method === 'PUT').flush({ version: 6, bloques: [...BLOQUES, fila('menu.horarios', 'Horas')] });
      fixture.detectChanges();
      expect(document.activeElement).toBe(titulo);
      // Al quitarse la barra la página encoge (su hueco inferior) y el navegador puede ajustar algo el scroll, pero el
      // título sigue lejos por encima de la vista: no se ha subido arriba del todo.
      expect(titulo.getBoundingClientRect().bottom).toBeLessThan(-1000);
      expect(Math.abs(window.scrollY - antes)).toBeLessThan(400);
    } finally {
      relleno.remove();
      window.scrollTo({ top: 0, behavior: 'auto' });
    }
  });

  it('Descartar no lleva la página arriba: el foco va al título sin desplazar la vista', () => {
    abrir();
    const relleno = document.createElement('div');
    relleno.style.height = '6000px';
    document.body.appendChild(relleno);
    try {
      cambiar('menu.horarios', 'Horas');
      const titulo = q('h1')!;
      window.scrollTo({ top: titulo.getBoundingClientRect().top + window.scrollY + 2000, behavior: 'auto' });
      const antes = window.scrollY;
      expect(antes).toBeGreaterThan(1000);
      boton('Descartar').focus({ preventScroll: true });
      boton('Descartar').click();
      fixture.detectChanges();
      expect(document.activeElement).toBe(titulo);
      expect(titulo.getBoundingClientRect().bottom).toBeLessThan(-1000);
      expect(Math.abs(window.scrollY - antes)).toBeLessThan(400);
    } finally {
      relleno.remove();
      window.scrollTo({ top: 0, behavior: 'auto' });
    }
  });

  it('web nueva: «Saldrán en tu web cuando la publiques.»', () => {
    abrir(NUEVA);
    cambiar('menu.horarios', 'Horas');
    boton('Guardar').click();
    http.expectOne((r) => r.method === 'PUT').flush({ version: 1, bloques: BLOQUES });
    expect(exito).toHaveBeenCalledOnceWith('Cambios guardados. Saldrán en tu web cuando la publiques.');
  });

  it('faltan datos: no llama a la API; resumen con enlaces, errores a la página y se va al corregir', () => {
    abrir();
    cambiar('portada.titular', '');
    cambiar('menu.horarios', '');
    const enviados = alIframe();
    boton('Guardar').click();
    fixture.detectChanges();
    fixture.detectChanges();
    http.expectNone((r) => r.method === 'PUT');
    expect(texto('.resumen h2')).toBe('Faltan 2 datos para guardar');
    expect(texto('.resumen p')).toBe('Tu web sigue como estaba. Corrige esto y vuelve a guardar:');
    expect(Array.from(el().querySelectorAll('.resumen a')).map((a) => a.textContent!.trim())).toEqual([
      'Portada: titular',
      'Cabecera: enlace del menú «Horarios»',
    ]);
    expect(q('.resumen')!.getAttribute('role')).toBe('alert');
    expect(document.activeElement).toBe(q('.resumen'));
    expect(enviados).toHaveBeenCalledWith(
      { canal: CANAL_EDITOR_WEB, tipo: 'errores', errores: { 'portada.titular': 'Escribe el titular.', 'menu.horarios': 'Escribe el texto.' } },
      location.origin,
    );
    q<HTMLAnchorElement>('.resumen a[data-ir="menu.horarios"]')!.click();
    expect(enviados).toHaveBeenCalledWith({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'menu.horarios' }, location.origin);

    cambiar('portada.titular', 'Taekwondo');
    expect(texto('.resumen h2')).toBe('Falta 1 dato para guardar');
    cambiar('menu.horarios', 'Horas');
    expect(q('.resumen')).toBeNull();
    expect(enviados).toHaveBeenCalledWith({ canal: CANAL_EDITOR_WEB, tipo: 'errores', errores: {} }, location.origin);
  });

  it('400 de la API: sus errores al mismo resumen (los que no son de esta pantalla, con su mensaje)', () => {
    abrir();
    cambiar('menu.horarios', 'Horas');
    boton('Guardar').click();
    http.expectOne((r) => r.method === 'PUT').flush(
      { mensaje: 'Hay datos que no se pueden guardar.', errores: { 'menu.horarios': 'Máximo 20 caracteres.', 'profesor.1.permiso': 'Marca la casilla si tienes su permiso.' } },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    fixture.detectChanges();
    expect(texto('.resumen h2')).toBe('Faltan 2 datos para guardar');
    expect(Array.from(el().querySelectorAll('.resumen li')).map((li) => li.textContent!.trim())).toEqual([
      'Cabecera: enlace del menú «Horarios»',
      'Marca la casilla si tienes su permiso.',
    ]);
    expect(q('.resumen a[data-ir="profesor.1.permiso"]')).toBeNull();
    expect(error).not.toHaveBeenCalled();
    expect(q('.barra')).not.toBeNull();
  });

  it('409: conflicto con «Recargar la página»; no se puede guardar; recargar vuelve a leer', () => {
    abrir();
    cambiar('menu.horarios', 'Horas');
    cambiar('cabecera.boton', 'Apúntate');
    boton('Guardar').click();
    http.expectOne((r) => r.method === 'PUT').flush({ mensaje: 'Otra persona…' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    fixture.detectChanges();
    expect(texto('.conflicto h2')).toBe('Otra persona ha guardado cambios en la web');
    expect(texto('.conflicto p')).toBe(
      'Los tuyos no se han guardado, para no borrar los suyos. Recarga la página para ver la web como está ahora y vuelve a hacer tus cambios. Al recargar se pierden los 2 cambios que tienes sin guardar.',
    );
    expect(document.activeElement).toBe(q('.conflicto'));
    expect(texto('.barra__texto')).toBe('No se pueden guardar tus 2 cambios. Otra persona cambió la web antes.');
    expect(boton('Guardar')).toBeUndefined();
    expect(error).not.toHaveBeenCalled();

    boton('Recargar la página').click();
    fixture.detectChanges();
    http.expectOne(URL).flush(PUBLICADA);
    http.expectOne(URL_CONTENIDO).flush({ version: 7, bloques: BLOQUES });
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes: [] });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
    fixture.detectChanges();
    expect(q('.conflicto')).toBeNull();
    expect(q('.barra')).toBeNull();
    expect(info).toHaveBeenCalledOnceWith('Página recargada. Ya ves la web como la dejó la otra persona.');
  });

  it('500: aviso de error con «Reintentar», que vuelve a guardar', () => {
    abrir();
    cambiar('menu.horarios', 'Horas');
    boton('Guardar').click();
    http.expectOne((r) => r.method === 'PUT').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(error).toHaveBeenCalledOnceWith('No se pudieron guardar los cambios. Tu web sigue como estaba.', jasmine.objectContaining({ texto: 'Reintentar' }));
    expect(q('.barra')).not.toBeNull();
    error.calls.mostRecent().args[1].alPulsar();
    http.expectOne((r) => r.method === 'PUT').flush({ version: 6, bloques: BLOQUES });
  });

  it('lo tecleado durante «Guardando…» se conserva al llegar el 200 y cuenta como cambio', () => {
    abrir();
    cambiar('menu.horarios', 'Horas');
    boton('Guardar').click();
    const peticion = http.expectOne((r) => r.method === 'PUT');
    cambiar('cabecera.boton', 'Apúntate');
    cambiar('menu.horarios', 'Horario');
    const enviados = alIframe();
    peticion.flush({ version: 6, bloques: [...BLOQUES, fila('menu.horarios', 'Horas')] });
    fixture.detectChanges();
    const c = fixture.componentInstance;
    expect(c.version()).toBe(6);
    expect(c.guardado().find((f) => f.clave === 'menu.horarios')!.texto).toBe('Horas');
    expect(c.borrador().find((f) => f.clave === 'menu.horarios')!.texto).toBe('Horario');
    expect(c.borrador().find((f) => f.clave === 'cabecera.boton')!.texto).toBe('Apúntate');
    expect(texto('.barra__texto strong')).toBe('2 cambios sin guardar');
    const borrador = enviados.calls.allArgs().map(([m]) => m as { tipo: string; bloques?: { clave: string; texto: string }[] }).filter((m) => m.tipo === 'borrador').at(-1)!;
    expect(borrador.bloques).toContain(jasmine.objectContaining({ clave: 'menu.horarios', texto: 'Horario' }));
  });

  it('doble clic en Guardar durante «Guardando…»: un solo PUT', () => {
    abrir();
    cambiar('menu.horarios', 'Horas');
    boton('Guardar').click();
    fixture.detectChanges();
    q<HTMLButtonElement>('.barra .primario')!.click();
    fixture.componentInstance.guardar();
    http.expectOne((r) => r.method === 'PUT').flush({ version: 6, bloques: BLOQUES });
    http.expectNone((r) => r.method === 'PUT');
  });

  it('«Reintentar» durante otro guardado en curso: un solo PUT', () => {
    abrir();
    cambiar('menu.horarios', 'Horas');
    boton('Guardar').click();
    http.expectOne((r) => r.method === 'PUT').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    boton('Guardar').click();
    const reintentar = error.calls.mostRecent().args[1].alPulsar as () => void;
    reintentar();
    reintentar();
    http.expectOne((r) => r.method === 'PUT').flush({ version: 6, bloques: BLOQUES });
    http.expectNone((r) => r.method === 'PUT');
  });

  it('el aviso de error con «Reintentar» se cierra al guardar bien', () => {
    abrir();
    cambiar('menu.horarios', 'Horas');
    boton('Guardar').click();
    http.expectOne((r) => r.method === 'PUT').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(cerrar).not.toHaveBeenCalled();
    boton('Guardar').click();
    http.expectOne((r) => r.method === 'PUT').flush({ version: 6, bloques: BLOQUES });
    expect(cerrar).toHaveBeenCalledOnceWith(2);
  });

  it('el aviso de error con «Reintentar» se cierra al descartar', () => {
    abrir();
    cambiar('menu.horarios', 'Horas');
    boton('Guardar').click();
    http.expectOne((r) => r.method === 'PUT').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    boton('Descartar').click();
    expect(cerrar).toHaveBeenCalledOnceWith(2);
  });

  it('400 en la foto de portada: el resumen da el mensaje de la API', () => {
    abrir();
    cambiar('menu.horarios', 'Horas');
    boton('Guardar').click();
    http.expectOne((r) => r.method === 'PUT').flush(
      { mensaje: 'Hay datos que no se pueden guardar.', errores: { 'portada.foto': 'La foto no es de la web de tu escuela.' } },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    fixture.detectChanges();
    expect(Array.from(el().querySelectorAll('.resumen li')).map((li) => li.textContent!.trim())).toEqual(['La foto no es de la web de tu escuela.']);
  });

  it('un texto del puente más largo que su máximo se recorta al máximo', () => {
    abrir();
    cambiar('menu.horarios', 'x'.repeat(30));
    expect(fixture.componentInstance.borrador().find((f) => f.clave === 'menu.horarios')!.texto).toBe('x'.repeat(20));
  });

  it('textos por defecto de la capa: se ignoran el «Escribe aquí…» y las claves que no dependen de los datos', () => {
    abrir();
    llegar({
      canal: CANAL_EDITOR_WEB,
      tipo: 'defectos',
      textos: { 'portada.frase': 'Escribe aquí una frase: para quién son las clases y qué hacer', 'texto_foto.2.rotulo': '02', 'horarios.apoyo': 'Toca tu pueblo' },
    });
    expect(fixture.componentInstance.defectos()).toEqual({ 'horarios.apoyo': 'Toca tu pueblo' });
  });

  it('Descartar: vuelve a lo guardado, manda el borrador a la vista previa y lo dice', () => {
    abrir();
    cambiar('menu.horarios', 'Horas');
    const enviados = alIframe();
    boton('Descartar').click();
    fixture.detectChanges();
    expect(q('.barra')).toBeNull();
    expect(enviados.calls.allArgs().map(([m]) => (m as { tipo: string }).tipo)).toContain('borrador');
    expect(texto('p.oculto[role="status"]')).toBe('Cambios descartados. Todo queda como en tu web.');
    expect(document.activeElement).toBe(q('h1'));
  });

  it('salir con cambios: «Tienes N cambios sin guardar»; sin cambios, sale sin preguntar', async () => {
    abrir();
    expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
    expect(confirmar).not.toHaveBeenCalled();
    cambiar('menu.horarios', 'Horas');
    expect(await fixture.componentInstance.puedeSalir()).toBeFalse();
    expect(confirmar).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({
        titulo: 'Tienes 1 cambio sin guardar',
        mensaje: 'Si sales ahora, se pierden. Tu web sigue como estaba.',
        accion: 'Salir sin guardar',
        cancelar: 'Seguir editando',
        destructivo: true,
      }),
    );
    confirmar.and.resolveTo({ confirmado: true } as never);
    expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
  });

  it('beforeunload solo con cambios', () => {
    // Sin lanzar el evento en la ventana: Karma lo toma por una recarga de la página.
    abrir();
    const sin = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
    fixture.componentInstance.antesDeDescargar(sin);
    expect(sin.defaultPrevented).toBeFalse();
    cambiar('menu.horarios', 'Horas');
    const con = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
    fixture.componentInstance.antesDeDescargar(con);
    expect(con.defaultPrevented).toBeTrue();
  });

  it('mensajes de texto con una clave que no es editable o de otro origen se ignoran', () => {
    abrir();
    cambiar('profesor.1.nombre', 'Otra');
    window.dispatchEvent(
      new MessageEvent('message', { data: { canal: CANAL_EDITOR_WEB, tipo: 'texto', clave: 'menu.horarios', texto: 'x' }, origin: 'https://otro.example', source: iframe()!.contentWindow }),
    );
    fixture.detectChanges();
    expect(q('.barra')).toBeNull();
  });

  describe('«Paso» con una sola sede (decisión del propietario, 11/11)', () => {
    it('como la web real: sin «Pueblo» y renumerado; empieza en «1 · Grupo»', () => {
      abrir(PUBLICADA, CONTENIDO, [{ nombre: 'Herrera', slug: 'herrera' }]);
      const pagina = q<HTMLSelectElement>('#web-contenido-pagina')!;
      pagina.value = 'preinscripcion';
      pagina.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      const opciones = Array.from(el().querySelectorAll<HTMLOptionElement>('#web-contenido-sub option'));
      expect(opciones.map((o) => [o.value, o.textContent!.trim()])).toEqual([
        ['2', '1 · Grupo'],
        ['3', '2 · Datos y firma'],
        ['4', 'Enviada'],
      ]);
      expect(q<HTMLSelectElement>('#web-contenido-sub')!.value).toBe('2');
      expect(texto('#marco-t strong')).toBe('Preinscripción · 1 · Grupo');
    });
  });
});

@Component({ template: '' })
class PantallaVacia {}

describe('WebContenidoComponent: salir desde un candado (W12d)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('con cambios, el candado pasa por el diálogo «Tienes N cambios sin guardar» y, si se sigue editando, no sale', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'contenido', component: WebContenidoComponent, canDeactivate: [salirSinGuardarGuard] },
          { path: 'sedes', component: PantallaVacia },
        ]),
        { provide: ORIGEN_EDITOR, useValue: ORIGEN },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const confirmar = spyOn(TestBed.inject(ConfirmacionService), 'confirmar').and.resolveTo({ confirmado: false } as never);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/contenido', WebContenidoComponent);
    http.expectOne(URL).flush(PUBLICADA);
    http.expectOne(URL_CONTENIDO).flush(CONTENIDO);
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes: [] });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
    harness.detectChanges();
    const fuente = (harness.routeNativeElement!.querySelector('iframe') as HTMLIFrameElement).contentWindow;
    const llegar = (data: unknown) => window.dispatchEvent(new MessageEvent('message', { data, origin: location.origin, source: fuente }));
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave: 'menu.horarios', texto: 'Horas' });
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: 'sedes' });
    await new Promise((r) => setTimeout(r));
    await harness.fixture.whenStable();
    expect(confirmar).toHaveBeenCalledOnceWith(jasmine.objectContaining({ titulo: 'Tienes 1 cambio sin guardar', accion: 'Salir sin guardar' }));
    expect(TestBed.inject(Router).url).toBe('/contenido');
  });
});
