import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { LoadingService } from '../../servicios/generales/loading.service';
import { itSinReloj, leerFicherosEnLaZona } from '../../testing/reloj-prueba';
import { MOTIVO_HEIC, SUBIDA_DOCUMENTO, SUBIDA_IMAGEN } from '../../utils/subida-archivos.util';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { EventoFormularioComponent } from './evento-formulario.component';

const API = environment.apiUrl;
const setTimeoutReal = window.setTimeout.bind(window);

const EVENTO = {
  id: 1,
  titulo: 'Campeonato regional',
  descripcion: 'Competición oficial.',
  fechaEvento: [2026, 11, 14],
  visible: false,
  fotoEvento: { id: 901, nombre: 'f1.webp', url: 'https://img.test/f1.webp', tipo: 'image/webp', ruta: '' },
  documentos: [
    { id: 71, nombre: 'Circular.pdf', tipo: 'application/pdf', url: '', ruta: '' },
    { id: 72, nombre: 'Autorización', tipo: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', url: '', ruta: '' },
  ],
};

const BYTES_JPG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4, 5, 6, 7, 8]);
const JPG = (): File => new File([BYTES_JPG], 'foto.jpg', { type: 'image/jpeg' });
/** Los nombres de los campos del multipart, en orden (sin `FormData.keys`, que no está en la lib de TS del proyecto). */
function claves(datos: FormData): string[] {
  const lista: string[] = [];
  datos.forEach((_, clave) => lista.push(clave));
  return lista;
}

const PDF = (nombre = 'Horario.pdf'): File => new File(['%PDF-1.4 ficticio'], nombre, { type: 'application/pdf' });

describe('EventoFormularioComponent (8b-3f, crear y editar evento del panel nuevo)', () => {
  let fixture: ComponentFixture<EventoFormularioComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let loading: jasmine.SpyObj<LoadingService>;
  let parametros: BehaviorSubject<Record<string, string>>;
  let navegar: jasmine.Spy;
  let atras: jasmine.Spy;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');

  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  /** Deja correr las lecturas de archivo (servidas desde la zona, `leerFicherosEnLaZona`) y las promesas. */
  async function esperar(veces = 6): Promise<void> {
    for (let i = 0; i < veces; i++) {
      await new Promise((r) => setTimeoutReal(r, 5));
    }
    pintar();
  }

  function botonEn(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => (x.getAttribute('aria-label') ?? '').replace(/\s+/g, ' ').trim() === t || texto(x) === t
    );
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  function avisos(): { tipo: string; texto: string }[] {
    return Array.from(document.querySelectorAll<HTMLElement>('.pn-aviso')).map((a) => ({
      tipo: a.classList.contains('pn-aviso--error') ? 'error' : 'otro',
      texto: a.querySelector('.pn-aviso__texto')!.textContent!.replace(/\s+/g, ' ').trim(),
    }));
  }

  function escribir(id: string, valor: string): void {
    const campo = el().querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    campo.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  function elegir(id: string, archivos: File[]): void {
    const entrada = el().querySelector<HTMLInputElement>(`#${id}`)!;
    const dt = new DataTransfer();
    archivos.forEach((a) => dt.items.add(a));
    entrada.files = dt.files;
    entrada.dispatchEvent(new Event('change'));
  }

  const guardar = (): HTMLButtonElement => el().querySelector<HTMLButtonElement>('.evento-form__guardar-primario')!;
  const previa = (): HTMLElement => el().querySelector<HTMLElement>('.evento-form__previa')!;

  function crear(id?: number): void {
    parametros = new BehaviorSubject<Record<string, string>>(id ? { id: String(id) } : {});
    TestBed.overrideProvider(ActivatedRoute, { useValue: { params: parametros.asObservable() } });
    fixture = TestBed.createComponent(EventoFormularioComponent);
    http = TestBed.inject(HttpTestingController);
    navegar = spyOn(TestBed.inject(Router), 'navigate').and.returnValue(Promise.resolve(true));
    atras = spyOn(TestBed.inject(Location), 'back');
    fixture.detectChanges();
  }

  function editar(evento: object = EVENTO): void {
    crear(1);
    http.expectOne(`${API}/eventos/1`).flush(evento);
    fixture.detectChanges();
    // El bloque de fotos pide la suya al aparecer el formulario (E8c · F2).
    http.expectOne(`${API}/eventos/1/fotos`).flush([]);
    fixture.detectChanges();
  }

  function rellenar(): void {
    escribir('evento-titulo', 'Copa de otoño');
    escribir('evento-descripcion', 'Torneo amistoso.');
    escribir('evento-fecha', '2026-11-20');
  }

  beforeEach(async () => {
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    quitarTokens = ponerTokensDePrueba();
    const pdf = '%PDF-1.4 ficticio';
    leerFicherosEnLaZona({
      'foto.jpg': BYTES_JPG,
      'foto.heic': 'x',
      'foto.bmp': 'x',
      'virus.exe': 'MZ',
      'Horario.pdf': pdf,
      'Uno.pdf': pdf,
      'Dos.pdf': pdf,
      'Tres.pdf': pdf,
      'Bien.pdf': pdf,
    });
    loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    await TestBed.configureTestingModule({
      imports: [EventoFormularioComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { params: of({}) } },
        { provide: LoadingService, useValue: loading },
        { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => of({}) } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    quitarTokens();
  });

  // ------------------------------------------------------------------ crear

  it('crear: título y enlace «Eventos»; secciones Información, Imagen, Fotos del evento y Documentos; etiquetas obligatorio u opcional', () => {
    crear();
    expect(texto(el().querySelector('h1'))).toBe('Crear nuevo evento');
    expect(texto(el().querySelector('.evento-form__subtitulo'))).toBe('Crea un evento para mostrar en la página web pública');
    expect(el().querySelector('.evento-form__miga a')!.getAttribute('href')).toBe('/eventosListar');
    expect(Array.from(el().querySelectorAll('.evento-form__seccion h2')).map(texto)).toEqual(['Información', 'Imagen', 'Fotos del evento', 'Documentos']);
    const etiqueta = (id: string): string => texto(el().querySelector(`label[for="${id}"]`));
    expect(etiqueta('evento-titulo')).toBe('Título obligatorio');
    expect(etiqueta('evento-descripcion')).toBe('Descripción obligatorio');
    expect(etiqueta('evento-fecha')).toBe('Fecha del evento opcional');
    expect(el().querySelector<HTMLInputElement>('#evento-titulo')!.getAttribute('aria-required')).toBe('true');
    expect(el().querySelector('[role="switch"]')!.getAttribute('aria-checked')).toBe('true');
    expect(texto(el().querySelector('.evento-form__documentos-ayuda'))).toBe('Los documentos estarán disponibles públicamente para descarga');
    expect(http.match(() => true).length).toBe(0);
  });

  it('con errores: «Crear evento» desactivado; pulsarlo no envía y enseña el resumen arriba con enlaces a cada campo', () => {
    crear();
    const boton = guardar();
    expect(texto(boton)).toBe('Crear evento');
    expect(boton.getAttribute('aria-disabled')).toBe('true');
    expect(el().querySelector('.evento-form__resumen')).toBeNull();
    boton.click();
    fixture.detectChanges();
    http.expectNone(() => true);
    const resumen = el().querySelector<HTMLElement>('.evento-form__resumen')!;
    expect(resumen.getAttribute('role')).toBe('alert');
    expect(document.activeElement).toBe(resumen);
    expect(texto(resumen.querySelector('h2'))).toBe('Revisa 2 campos antes de guardar');
    const enlaces = Array.from(resumen.querySelectorAll<HTMLAnchorElement>('a'));
    expect(enlaces.map((a) => [a.getAttribute('href'), texto(a)])).toEqual([
      ['#evento-titulo', 'El título es obligatorio'],
      ['#evento-descripcion', 'La descripción es obligatoria'],
    ]);
    enlaces[1].click();
    fixture.detectChanges();
    expect(document.activeElement).toBe(el().querySelector('#evento-descripcion'));
    // Error debajo de cada campo, enlazado con aria-describedby.
    const titulo = el().querySelector<HTMLInputElement>('#evento-titulo')!;
    expect(titulo.getAttribute('aria-invalid')).toBe('true');
    expect(titulo.getAttribute('aria-describedby')).toContain('evento-titulo-error');
    expect(texto(el().querySelector('#evento-titulo-error'))).toBe('El título es obligatorio');
  });

  it('el máximo de caracteres de hoy (100 y 500) es un error del resumen', () => {
    crear();
    escribir('evento-titulo', 'x'.repeat(101));
    escribir('evento-descripcion', 'y'.repeat(501));
    guardar().click();
    fixture.detectChanges();
    expect(Array.from(el().querySelectorAll('.evento-form__resumen a')).map(texto)).toEqual(['Máximo 100 caracteres', 'Máximo 500 caracteres']);
    escribir('evento-titulo', 'x'.repeat(100));
    escribir('evento-descripcion', 'y'.repeat(500));
    expect(el().querySelector('.evento-form__resumen')).toBeNull();
    expect(guardar().getAttribute('aria-disabled')).toBeNull();
  });

  itSinReloj('crear: el MISMO POST /eventos/crear de hoy (multipart «nuevo» y «file» con su nombre), luego cada documento, y vuelve a Eventos', async () => {
    crear();
    rellenar();
    el().querySelector<HTMLButtonElement>('[role="switch"]')!.click();
    fixture.detectChanges();
    elegir('evento-imagen', [JPG()]);
    elegir('evento-documentos', [PDF('Uno.pdf'), PDF('Dos.pdf')]);
    await esperar();
    guardar().click();
    fixture.detectChanges();
    expect(texto(guardar())).toContain('Guardando…');
    expect(guardar().getAttribute('aria-busy')).toBe('true');
    // Sin doble envío.
    guardar().click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/eventos/crear`);
    expect(r.request.method).toBe('POST');
    expect(r.request.withCredentials).toBeTrue();
    const cuerpo = r.request.body as FormData;
    expect(claves(cuerpo)).toEqual(['nuevo', 'file']);
    expect(cuerpo.get('nuevo')).toBe('{"titulo":"Copa de otoño","descripcion":"Torneo amistoso.","fechaEvento":"2026-11-20","visible":false}');
    const file = cuerpo.get('file') as File;
    expect(file.name).toBe('foto.jpg');
    expect(file.size).toBe(12);
    http.expectNone((x) => x.url.includes('/documentos'));
    r.flush({ id: 33 });
    fixture.detectChanges();
    const docs = http.match(`${API}/eventos/33/documentos`);
    expect(docs.length).toBe(2);
    expect(docs.map((d) => d.request.method)).toEqual(['POST', 'POST']);
    expect(docs.map((d) => ((d.request.body as FormData).get('archivo') as File).name)).toEqual(['Uno.pdf', 'Dos.pdf']);
    expect(claves(docs[0].request.body as FormData)).toEqual(['archivo']);
    docs[0].flush({ id: 1 });
    expect(navegar).not.toHaveBeenCalled();
    docs[1].flush({ id: 2 });
    await esperar(1);
    expect(navegar).toHaveBeenCalledOnceWith(['/eventosListar']);
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Has creado un nuevo evento.' }]);
    expect(loading.show).not.toHaveBeenCalled();
  });

  itSinReloj('crear sin imagen ni documentos: «nuevo» solo, con la fecha vacía como null y visible por defecto', async () => {
    crear();
    escribir('evento-titulo', 'T');
    escribir('evento-descripcion', 'D');
    guardar().click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/eventos/crear`);
    expect(claves(r.request.body as FormData)).toEqual(['nuevo']);
    expect((r.request.body as FormData).get('nuevo')).toBe('{"titulo":"T","descripcion":"D","fechaEvento":null,"visible":true}');
    r.flush({ id: 34 });
    await esperar(1);
    expect(navegar).toHaveBeenCalledOnceWith(['/eventosListar']);
  });

  itSinReloj('crear: un documento que falla avisa (sin cerrarse solo) con los motivos del servidor, y vuelve a Eventos', async () => {
    crear();
    rellenar();
    elegir('evento-documentos', [PDF('Uno.pdf'), PDF('Dos.pdf')]);
    await esperar();
    guardar().click();
    fixture.detectChanges();
    http.expectOne(`${API}/eventos/crear`).flush({ id: 33 });
    const docs = http.match(`${API}/eventos/33/documentos`);
    docs[0].flush({ mensaje: 'Archivo dañado.' }, { status: 400, statusText: 'Bad' });
    docs[1].flush({ id: 2 });
    await esperar(1);
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'El evento se ha creado, pero 1 documento no se pudo subir. Archivo dañado.' }]);
    expect(navegar).toHaveBeenCalledOnceWith(['/eventosListar']);
  });

  itSinReloj('crear: un error avisa con el mensaje del servidor (o el de hoy) y deja el formulario como estaba', async () => {
    crear();
    rellenar();
    guardar().click();
    fixture.detectChanges();
    http.expectOne(`${API}/eventos/crear`).flush({}, { status: 500, statusText: 'Error' });
    await esperar(1);
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No hemos podido crear el evento.' }]);
    expect(navegar).not.toHaveBeenCalled();
    expect(el().querySelector<HTMLInputElement>('#evento-titulo')!.value).toBe('Copa de otoño');
    expect(guardar().getAttribute('aria-busy')).toBeNull();
  });

  it('cancelar al crear: no envía nada y vuelve a Eventos', () => {
    crear();
    rellenar();
    botonEn(el(), 'Cancelar').click();
    fixture.detectChanges();
    http.expectNone(() => true);
    expect(navegar).toHaveBeenCalledOnceWith(['/eventosListar']);
  });

  // ------------------------------------------------------------------ imagen

  itSinReloj('imagen: HEIC rechazada con el mensaje de hoy, en línea; no queda elegida', async () => {
    crear();
    elegir('evento-imagen', [new File(['x'], 'foto.heic', { type: 'image/heic' })]);
    await esperar();
    const error = el().querySelector('#evento-imagen-error')!;
    expect(texto(error)).toBe(`No se pudo usar la imagen. ${MOTIVO_HEIC}`);
    expect(el().querySelector('.evento-form__imagen-actual')).toBeNull();
    expect(Swal.isVisible()).toBeFalse();
  });

  itSinReloj('imagen: formato y tamaño con las validaciones de hoy', async () => {
    crear();
    elegir('evento-imagen', [new File(['x'], 'foto.bmp', { type: 'image/bmp' })]);
    await esperar();
    expect(texto(el().querySelector('#evento-imagen-error'))).toBe(`No se pudo usar la imagen. ${SUBIDA_IMAGEN.motivoFormato}`);
    const grande = JPG();
    Object.defineProperty(grande, 'size', { value: 50 * 1024 * 1024 + 1 });
    elegir('evento-imagen', [grande]);
    await esperar();
    expect(texto(el().querySelector('#evento-imagen-error'))).toBe(`No se pudo usar la imagen. ${SUBIDA_IMAGEN.motivoTamano}`);
    expect(el().querySelector<HTMLInputElement>('#evento-imagen')!.accept).toBe('image/*');
    expect(texto(el().querySelector('.evento-form__imagen-ayuda'))).toBe('Formatos: JPG, PNG, GIF, WebP (máx. 50 MB)');
  });

  itSinReloj('imagen al crear: se ve en el campo y en la vista previa; «Quitar» la deja sin imagen sin enviar nada', async () => {
    crear();
    elegir('evento-imagen', [JPG()]);
    await esperar();
    const actual = el().querySelector<HTMLImageElement>('.evento-form__imagen-actual img')!;
    expect(actual.src).toMatch(/^blob:/);
    expect(previa().querySelector<HTMLImageElement>('img')!.src).toBe(actual.src);
    botonEn(el(), 'Quitar').click();
    fixture.detectChanges();
    expect(el().querySelector('.evento-form__imagen-actual')).toBeNull();
    expect(previa().querySelector<HTMLImageElement>('img')!.getAttribute('src')).toBe('assets/media/default.webp');
    http.expectNone(() => true);
  });

  // ------------------------------------------------------------------ editar

  it('editar: GET /eventos/{id} de hoy con ERROR_HANDLED_LOCALLY; campos, imagen (w=920) y documentos', () => {
    crear(1);
    const r = http.expectOne(`${API}/eventos/1`);
    expect(r.request.method).toBe('GET');
    expect(r.request.withCredentials).toBeTrue();
    expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    r.flush(EVENTO);
    fixture.detectChanges();
    expect(texto(el().querySelector('h1'))).toBe('Editar evento');
    expect(texto(el().querySelector('.evento-form__subtitulo'))).toBe('Modifica la información del evento publicado en la web');
    expect(el().querySelector<HTMLInputElement>('#evento-titulo')!.value).toBe('Campeonato regional');
    expect(el().querySelector<HTMLInputElement>('#evento-fecha')!.value).toBe('2026-11-14');
    expect(el().querySelector('[role="switch"]')).toBeNull();
    expect(el().querySelector<HTMLImageElement>('.evento-form__imagen-actual img')!.getAttribute('src')).toBe(
      'https://img.test/f1.webp?w=920&v=901'
    );
    expect(Array.from(el().querySelectorAll('.evento-form__documento--guardado .evento-form__documento-nombre')).map(texto)).toEqual([
      'Circular.pdf',
      'Autorización',
    ]);
    expect(texto(guardar())).toBe('Guardar cambios');
  });

  itSinReloj('editar: el MISMO PUT /eventos/{id} de hoy («eventoEditado» y «file» = "null" sin imagen nueva), y vuelve a Eventos', async () => {
    editar();
    escribir('evento-titulo', 'Campeonato regional 2026');
    escribir('evento-fecha', '');
    guardar().click();
    guardar().click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/eventos/1`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.withCredentials).toBeTrue();
    const cuerpo = r.request.body as FormData;
    expect(claves(cuerpo)).toEqual(['eventoEditado', 'file']);
    expect(cuerpo.get('eventoEditado')).toBe(
      '{"titulo":"Campeonato regional 2026","descripcion":"Competición oficial.","fechaEvento":null,"fotoEvento":null}'
    );
    expect(cuerpo.get('file')).toBe('null');
    r.flush({});
    await esperar(1);
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'El evento ha sido actualizado correctamente.' }]);
    expect(navegar).toHaveBeenCalledOnceWith(['/eventosListar']);
  });

  itSinReloj('editar con imagen nueva: «file» lleva el archivo', async () => {
    editar();
    elegir('evento-imagen', [JPG()]);
    await esperar();
    guardar().click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/eventos/1`);
    expect(((r.request.body as FormData).get('file') as File).name).toBe('foto.jpg');
    r.flush({}, { status: 400, statusText: 'Bad', headers: {} });
    await esperar(1);
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se ha podido actualizar el evento' }]);
    expect(navegar).not.toHaveBeenCalled();
  });

  itSinReloj('editar: «Eliminar imagen» pide confirmación destructiva y manda el DELETE /eventos/{id}/imagen de hoy', async () => {
    editar();
    botonEn(el(), 'Eliminar imagen').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Eliminar imagen');
    expect(texto(document.activeElement)).toBe('Cancelar');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar(1);
    http.expectNone(() => true);
    botonEn(el(), 'Eliminar imagen').click();
    pintar();
    botonEn(dialogo()!, 'Sí, eliminar').click();
    await esperar(1);
    const r = http.expectOne(`${API}/eventos/1/imagen`);
    expect(r.request.method).toBe('DELETE');
    expect(r.request.withCredentials).toBeTrue();
    r.flush(null);
    await esperar(1);
    expect(el().querySelector('.evento-form__imagen-actual')).toBeNull();
    expect(previa().querySelector<HTMLImageElement>('img')!.getAttribute('src')).toBe('assets/media/default.webp');
  });

  it('cancelar al editar: no envía nada y vuelve atrás, como hoy', () => {
    editar();
    escribir('evento-titulo', 'Otro');
    botonEn(el(), 'Cancelar').click();
    http.expectNone(() => true);
    expect(atras).toHaveBeenCalledTimes(1);
  });

  // ------------------------------------------------------------------ documentos

  itSinReloj('documentos al editar: nombre y tamaño, quitar, «Subir N documentos» con un POST por documento como hoy', async () => {
    editar();
    elegir('evento-documentos', [PDF('Uno.pdf'), PDF('Dos.pdf'), PDF('Tres.pdf')]);
    await esperar();
    const nuevos = (): string[] => Array.from(el().querySelectorAll('.evento-form__documento--nuevo .evento-form__documento-nombre')).map(texto);
    expect(nuevos()).toEqual(['Uno.pdf', 'Dos.pdf', 'Tres.pdf']);
    expect(texto(el().querySelector('.evento-form__documento--nuevo .evento-form__documento-tamano'))).toBe('17 bytes');
    botonEn(el(), 'Quitar Dos.pdf').click();
    fixture.detectChanges();
    expect(nuevos()).toEqual(['Uno.pdf', 'Tres.pdf']);
    http.expectNone(() => true);
    const subir = botonEn(el(), 'Subir 2 documentos');
    subir.click();
    subir.click();
    fixture.detectChanges();
    const r = http.match(`${API}/eventos/1/documentos`);
    expect(r.length).toBe(2);
    expect(r.map((x) => ((x.request.body as FormData).get('archivo') as File).name)).toEqual(['Uno.pdf', 'Tres.pdf']);
    r[0].flush({ id: 81, nombre: 'Uno.pdf', tipo: 'application/pdf' });
    r[1].flush({ id: 82, nombre: 'Tres.pdf', tipo: 'application/pdf' });
    await esperar(1);
    expect(nuevos()).toEqual([]);
    expect(Array.from(el().querySelectorAll('.evento-form__documento--guardado .evento-form__documento-nombre')).map(texto)).toEqual([
      'Circular.pdf',
      'Autorización',
      'Uno.pdf',
      'Tres.pdf',
    ]);
    expect(avisos()).toEqual([{ tipo: 'otro', texto: '2 documentos subidos correctamente.' }]);
  });

  itSinReloj('documentos: los de un tipo no admitido no se añaden y se dice por qué, en línea', async () => {
    crear();
    elegir('evento-documentos', [new File(['MZ'], 'virus.exe', { type: 'application/x-msdownload' }), PDF('Bien.pdf')]);
    await esperar();
    expect(Array.from(el().querySelectorAll('.evento-form__documento--nuevo .evento-form__documento-nombre')).map(texto)).toEqual(['Bien.pdf']);
    expect(texto(el().querySelector('#evento-documentos-error'))).toBe(`No se pudo añadir el documento. «virus.exe»: ${SUBIDA_DOCUMENTO.motivoFormato}`);
    expect(el().querySelector<HTMLInputElement>('#evento-documentos')!.accept).toBe('.pdf,.txt,.csv,.jpg,.jpeg,.png,.webp,.gif,.doc,.docx,.xls,.xlsx');
    expect(el().querySelector<HTMLInputElement>('#evento-documentos')!.multiple).toBeTrue();
  });

  it('descargar: la petición de hoy (?download=true, blob) y el nombre de hoy', () => {
    editar();
    const descargas: string[] = [];
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
      descargas.push(this.download);
    });
    botonEn(el(), 'Descargar Autorización').click();
    const r = http.expectOne(`${API}/eventos/1/documentos/72/descargar?download=true`);
    expect(r.request.method).toBe('GET');
    expect(r.request.responseType).toBe('blob');
    expect(r.request.withCredentials).toBeTrue();
    r.flush(new Blob(['x']));
    expect(descargas).toEqual(['Autorización.docx']);
  });

  itSinReloj('eliminar documento: destructivo con el foco en «Cancelar»; cancelar no envía; confirmar, el DELETE de hoy', async () => {
    editar();
    botonEn(el(), 'Eliminar Circular.pdf').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Eliminar documento');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('¿Estás seguro de eliminar «Circular.pdf»?');
    expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
    expect(texto(document.activeElement)).toBe('Cancelar');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar(1);
    http.expectNone(() => true);
    botonEn(el(), 'Eliminar Circular.pdf').click();
    pintar();
    botonEn(dialogo()!, 'Sí, eliminar').click();
    await esperar(1);
    const r = http.expectOne(`${API}/eventos/1/documentos/71`);
    expect(r.request.method).toBe('DELETE');
    r.flush(null);
    await esperar(1);
    expect(Array.from(el().querySelectorAll('.evento-form__documento--guardado .evento-form__documento-nombre')).map(texto)).toEqual([
      'Autorización',
    ]);
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'El documento ha sido eliminado.' }]);
  });

  itSinReloj('eliminar documento: un error avisa y el documento sigue', async () => {
    editar();
    botonEn(el(), 'Eliminar Circular.pdf').click();
    pintar();
    botonEn(dialogo()!, 'Sí, eliminar').click();
    await esperar(1);
    http.expectOne(`${API}/eventos/1/documentos/71`).flush({}, { status: 500, statusText: 'Error' });
    await esperar(1);
    expect(Array.from(el().querySelectorAll('.evento-form__documento--guardado .evento-form__documento-nombre')).map(texto)).toEqual([
      'Circular.pdf',
      'Autorización',
    ]);
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo eliminar el documento.' }]);
  });

  itSinReloj('eliminar imagen: un error avisa y la imagen sigue', async () => {
    editar();
    botonEn(el(), 'Eliminar imagen').click();
    pintar();
    botonEn(dialogo()!, 'Sí, eliminar').click();
    await esperar(1);
    http.expectOne(`${API}/eventos/1/imagen`).flush({}, { status: 500, statusText: 'Error' });
    await esperar(1);
    expect(el().querySelector('.evento-form__imagen-actual')).not.toBeNull();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se ha podido eliminar la imagen.' }]);
  });

  itSinReloj('descargar: un error avisa y no descarga nada', async () => {
    editar();
    const clic = spyOn(HTMLAnchorElement.prototype, 'click');
    botonEn(el(), 'Descargar Autorización').click();
    http.expectOne(`${API}/eventos/1/documentos/72/descargar?download=true`).flush(new Blob(['x']), { status: 500, statusText: 'Error' });
    await esperar(1);
    expect(clic).not.toHaveBeenCalled();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo descargar el documento.' }]);
  });

  itSinReloj('documentos al editar: si uno falla, avisa de la subida parcial y los que subieron quedan guardados', async () => {
    editar();
    elegir('evento-documentos', [PDF('Uno.pdf'), PDF('Dos.pdf')]);
    await esperar();
    botonEn(el(), 'Subir 2 documentos').click();
    fixture.detectChanges();
    const r = http.match(`${API}/eventos/1/documentos`);
    expect(r.length).toBe(2);
    r[0].flush({ id: 81, nombre: 'Uno.pdf', tipo: 'application/pdf' });
    r[1].flush({ mensaje: 'Archivo dañado.' }, { status: 400, statusText: 'Bad' });
    await esperar(1);
    expect(Array.from(el().querySelectorAll('.evento-form__documento--nuevo')).length).toBe(0);
    expect(Array.from(el().querySelectorAll('.evento-form__documento--guardado .evento-form__documento-nombre')).map(texto)).toEqual([
      'Circular.pdf',
      'Autorización',
      'Uno.pdf',
    ]);
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'Subida parcial: 1 documento subido, 1 error. Archivo dañado.' }]);
  });

  // ------------------------------------------------------------------ salir a mitad de un guardado

  itSinReloj('crear: salir con el guardado en vuelo no cancela la petición, sube los documentos y no navega', async () => {
    crear();
    rellenar();
    elegir('evento-documentos', [PDF('Uno.pdf')]);
    await esperar();
    guardar().click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/eventos/crear`);
    fixture.destroy();
    expect(r.cancelled).toBeFalse();
    r.flush({ id: 33 });
    const doc = http.expectOne(`${API}/eventos/33/documentos`);
    expect(doc.cancelled).toBeFalse();
    doc.flush({ id: 1 });
    expect(navegar).not.toHaveBeenCalled();
  });

  it('editar: salir con el guardado en vuelo no cancela el PUT', () => {
    editar();
    escribir('evento-titulo', 'Otro');
    guardar().click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/eventos/1`);
    fixture.destroy();
    expect(r.cancelled).toBeFalse();
    r.flush({});
    expect(navegar).not.toHaveBeenCalled();
  });

  itSinReloj('eliminar imagen y documento: salir con la petición en vuelo no la cancela', async () => {
    editar();
    botonEn(el(), 'Eliminar imagen').click();
    pintar();
    botonEn(dialogo()!, 'Sí, eliminar').click();
    await esperar(1);
    const img = http.expectOne(`${API}/eventos/1/imagen`);
    botonEn(el(), 'Eliminar Circular.pdf').click();
    pintar();
    botonEn(dialogo()!, 'Sí, eliminar').click();
    await esperar(1);
    const doc = http.expectOne(`${API}/eventos/1/documentos/71`);
    fixture.destroy();
    expect(img.cancelled).toBeFalse();
    expect(doc.cancelled).toBeFalse();
    expect(() => {
      img.flush(null);
      doc.flush(null);
    }).not.toThrow();
  });

  // ------------------------------------------------------------------ vista previa

  it('vista previa: cambia al escribir y dice si el evento está visible u oculto', () => {
    crear();
    expect(texto(previa().querySelector('.evento-form__previa-titulo'))).toBe('Título del evento');
    expect(texto(previa().querySelector('.evento-form__previa-estado'))).toBe('Visible en la web');
    escribir('evento-titulo', 'Copa de otoño');
    escribir('evento-descripcion', 'Torneo amistoso.');
    escribir('evento-fecha', '2026-11-20');
    expect(texto(previa().querySelector('.evento-form__previa-titulo'))).toBe('Copa de otoño');
    expect(texto(previa().querySelector('.evento-form__previa-descripcion'))).toBe('Torneo amistoso.');
    expect(texto(previa().querySelector('.evento-form__previa-fecha'))).toMatch(/· 20 nov 2026$/);
    el().querySelector<HTMLButtonElement>('[role="switch"]')!.click();
    fixture.detectChanges();
    expect(texto(previa().querySelector('.evento-form__previa-estado'))).toBe('Oculto: no se verá en la web');
  });

  it('vista previa al editar: el estado guardado del evento', () => {
    editar();
    expect(texto(previa().querySelector('.evento-form__previa-estado'))).toBe('Oculto: no se verá en la web');
    expect(texto(previa().querySelector('.evento-form__previa-titulo'))).toBe('Campeonato regional');
  });

  // ------------------------------------------------------------------ fotos del evento (E8c · F2)

  const cambios = (): HTMLElement | null => el().querySelector<HTMLElement>('.evento-form__cambios');
  const miniaturas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.fotos__celda--foto'));

  itSinReloj('fotos: la barra dice «N cambios sin guardar» (singular con 1) y desaparece con 0', async () => {
    editar();
    expect(cambios()).toBeNull();
    elegir('evento-fotos', [JPG(), JPG()]);
    await esperar();
    expect(texto(cambios())).toBe('2 cambios sin guardar');
    expect(cambios()!.getAttribute('role')).toBe('status');
    botonEn(miniaturas()[0], 'Quitar la foto 1').click();
    fixture.detectChanges();
    expect(texto(cambios())).toBe('1 cambio sin guardar');
    botonEn(miniaturas()[0], 'Quitar la foto 1').click();
    fixture.detectChanges();
    expect(cambios()).toBeNull();
    expect(texto(el().querySelector('.evento-form__barra'))).toBe('CancelarGuardar cambios');
  });

  itSinReloj('fotos: las secciones van en el orden Imagen, Fotos del evento, Documentos', async () => {
    crear();
    expect(Array.from(el().querySelectorAll('.evento-form__seccion h2')).map(texto)).toEqual([
      'Información',
      'Imagen',
      'Fotos del evento',
      'Documentos',
    ]);
    http.expectNone(() => true);
  });

  itSinReloj('fotos al editar: guarda el evento como hoy, luego sube las pendientes y navega con el aviso de siempre', async () => {
    editar();
    elegir('evento-fotos', [JPG()]);
    await esperar();
    guardar().click();
    fixture.detectChanges();
    const put = http.expectOne(`${API}/eventos/1`);
    expect(put.request.method).toBe('PUT');
    put.flush({});
    await esperar(1);
    expect(navegar).not.toHaveBeenCalled();
    const foto = http.expectOne(`${API}/eventos/1/fotos`);
    expect(foto.request.method).toBe('POST');
    foto.flush({ id: 501, url: `${API}/eventos/1/fotos/501`, orden: 0 });
    await esperar(3);
    expect(navegar).toHaveBeenCalledOnceWith(['/eventosListar']);
    expect(avisos().map((a) => a.texto)).toEqual(['El evento ha sido actualizado correctamente.']);
  });

  itSinReloj('fotos al editar: si una falla, se queda en el formulario con aviso y «Reintentar»; no navega', async () => {
    editar();
    elegir('evento-fotos', [JPG(), JPG()]);
    await esperar();
    guardar().click();
    fixture.detectChanges();
    http.expectOne(`${API}/eventos/1`).flush({});
    await esperar(1);
    const subidas = http.match(`${API}/eventos/1/fotos`);
    expect(subidas.length).toBe(2);
    subidas[0].flush({ id: 501, url: `${API}/eventos/1/fotos/501`, orden: 0 });
    subidas[1].flush({ mensaje: 'El servidor está ocupado. Inténtalo de nuevo.' }, { status: 503, statusText: 'Unavailable' });
    await esperar(3);
    expect(navegar).not.toHaveBeenCalled();
    expect(miniaturas().map((m) => m.dataset['estado'])).toEqual(['guardada', 'error']);
    expect(texto(miniaturas()[1].querySelector('.fotos__reintentar'))).toBe('Reintentar');
    expect(texto(cambios())).toBe('1 cambio sin guardar');
    expect(avisos()).toEqual([
      {
        tipo: 'error',
        texto:
          'El evento se ha guardado, pero 1 foto no se pudo subir. El servidor está ocupado. Inténtalo de nuevo. Reintenta cada foto o pulsa «Guardar cambios».',
      },
    ]);
    expect(guardar().getAttribute('aria-disabled')).toBeNull();
  });

  itSinReloj('fotos al crear con un fallo: el evento ya existe, pasa a Editar y reintentar sube solo la fallida (sin crear otro)', async () => {
    crear();
    rellenar();
    const reemplazar = spyOn(TestBed.inject(Location), 'replaceState');
    elegir('evento-fotos', [JPG(), JPG()]);
    await esperar();
    guardar().click();
    fixture.detectChanges();
    http.expectOne(`${API}/eventos/crear`).flush({ id: 33, titulo: 'Copa de otoño', descripcion: 'Torneo amistoso.', visible: true });
    const subidas = http.match(`${API}/eventos/33/fotos`);
    expect(subidas.length).toBe(2);
    subidas[0].flush({ id: 601, url: `${API}/eventos/33/fotos/601`, orden: 0 });
    subidas[1].flush({}, { status: 500, statusText: 'Error' });
    await esperar(3);
    expect(navegar).not.toHaveBeenCalled();
    expect(texto(el().querySelector('h1'))).toBe('Editar evento');
    expect(texto(guardar())).toBe('Guardar cambios');
    expect(reemplazar).toHaveBeenCalledOnceWith('/eventosEditar/33');
    expect(miniaturas().map((m) => m.dataset['estado'])).toEqual(['guardada', 'error']);
    expect(avisos()).toEqual([
      { tipo: 'error', texto: 'El evento se ha creado, pero 1 foto no se pudo subir. Reintenta cada foto o pulsa «Guardar cambios».' },
    ]);

    botonEn(miniaturas()[1], 'Reintentar la subida de la foto 2. No se pudo subir la foto.').click();
    const otra = http.match(`${API}/eventos/33/fotos`);
    expect(otra.length).toBe(1);
    otra[0].flush({ id: 602, url: `${API}/eventos/33/fotos/602`, orden: 1 });
    await esperar(3);
    expect(http.match(`${API}/eventos/33/fotos/orden`).length).toBe(1);
    expect(miniaturas().map((m) => m.dataset['estado'])).toEqual(['guardada', 'guardada']);
    http.expectNone(`${API}/eventos/crear`);
  });

  itSinReloj('fotos al crear sin fallos: sigue como hoy (aviso «Has creado un nuevo evento.» y a Eventos)', async () => {
    crear();
    rellenar();
    elegir('evento-fotos', [JPG()]);
    await esperar();
    guardar().click();
    fixture.detectChanges();
    http.expectOne(`${API}/eventos/crear`).flush({ id: 33 });
    http.expectOne(`${API}/eventos/33/fotos`).flush({ id: 601, url: `${API}/eventos/33/fotos/601`, orden: 0 });
    await esperar(3);
    expect(navegar).toHaveBeenCalledOnceWith(['/eventosListar']);
    expect(avisos().map((a) => a.texto)).toEqual(['Has creado un nuevo evento.']);
  });

  itSinReloj('fotos: un fallo al leerlas en Editar no bloquea el resto del formulario', async () => {
    crear(1);
    http.expectOne(`${API}/eventos/1`).flush(EVENTO);
    fixture.detectChanges();
    http.expectOne(`${API}/eventos/1/fotos`).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(el().querySelector('.evento-form__seccion pn-bloque-error, pn-fotos-evento pn-bloque-error')).not.toBeNull();
    expect(el().querySelector<HTMLInputElement>('#evento-titulo')!.value).toBe('Campeonato regional');
    expect(texto(el().querySelector('h1'))).toBe('Editar evento');
    expect(guardar().getAttribute('aria-disabled')).toBeNull();
  });

  // ------------------------------------------------------------------ estados

  it('editar: esqueleto a los 300 ms; error con «Reintentar» (sin SweetAlert2)', () => {
    jasmine.clock().install();
    try {
      crear(1);
      jasmine.clock().tick(299);
      fixture.detectChanges();
      expect(el().querySelector('.pn-esqueleto__forma')).toBeNull();
      jasmine.clock().tick(1);
      fixture.detectChanges();
      expect(el().querySelector('pn-esqueleto [role="status"]')!.textContent).toContain('Cargando el evento');
      http.expectOne(`${API}/eventos/1`).flush({}, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      expect(texto(el().querySelector('pn-bloque-error'))).toContain('No se pudo cargar el evento');
      expect(Swal.isVisible()).toBeFalse();
      botonEn(el(), 'Reintentar').click();
      fixture.detectChanges();
      http.expectOne(`${API}/eventos/1`).flush(EVENTO);
      fixture.detectChanges();
      expect(el().querySelector<HTMLInputElement>('#evento-titulo')!.value).toBe('Campeonato regional');
    } finally {
      jasmine.clock().uninstall();
    }
  });
});
