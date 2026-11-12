import { HttpClient, HttpEventType, provideHttpClient } from '@angular/common/http';
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
const MB = 1024 * 1024;

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
  fila('presentacion.foto', 'El pabellón', { imagenId: 8, imagenUrl: 'https://x/pr.webp' }),
  fila('texto_foto.3', null, { orden: 5, oculto: true }),
  fila('texto_foto.3.titulo', 'Nuestra sala'),
  fila('galeria.1', 'Exhibición', { imagenId: 11, imagenUrl: 'https://x/g.webp', orden: 1 }),
];
const CONTENIDO: ContenidoWeb = { version: 5, bloques: BLOQUES };

const foto = (nombre = 'pabellon.jpg', tipo = 'image/jpeg', tamano?: number): File => {
  const f = new File(['x'], nombre, { type: tipo });
  if (tamano !== undefined) {
    Object.defineProperty(f, 'size', { value: tamano });
  }
  return f;
};

/**
 * W12f-1: fotos sueltas en «Web · Contenido» (boceto `fotoCapa`, `subidaHtml`, `abrirMenuFoto`, `abrirDescribir`,
 * `simularSubida`, `pintarBarra` con subida, `cambios`, descartar; `?subida=`).
 */
describe('WebContenidoComponent: fotos sueltas (W12f-1)', () => {
  let fixture: ComponentFixture<WebContenidoComponent>;
  let http: HttpTestingController;
  let confirmar: jasmine.Spy;

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
    confirmar = spyOn(TestBed.inject(ConfirmacionService), 'confirmar').and.resolveTo({ confirmado: false } as never);
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
  const boton = (t: string) =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('.barra button')).find((b) => b.textContent!.trim() === t)!;
  let enviados: jasmine.Spy;

  function abrir(contenido = CONTENIDO): void {
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(PUBLICADA);
    http.expectOne(URL_CONTENIDO).flush(contenido);
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes: [{ nombre: 'Herrera', slug: 'herrera' }, { nombre: 'Estepa', slug: 'estepa' }] });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
    fixture.detectChanges();
    enviados = spyOn(iframe()!.contentWindow!, 'postMessage') as jasmine.Spy;
  }

  function llegar(data: unknown): void {
    window.dispatchEvent(new MessageEvent('message', { data, origin: location.origin, source: iframe()!.contentWindow }));
    fixture.detectChanges();
  }
  const elegir = (clave: string, archivo: File) => llegar({ canal: CANAL_EDITOR_WEB, tipo: 'subir-foto', clave, archivo });
  const accion = (clave: string, a: string) => llegar({ canal: CANAL_EDITOR_WEB, tipo: 'foto', clave, accion: a });
  const describir = (clave: string, t: string) => llegar({ canal: CANAL_EDITOR_WEB, tipo: 'descripcion', clave, texto: t });
  const mensajes = (tipo: string) =>
    enviados.calls
      .allArgs()
      .map(([m]) => m as { tipo: string } & Record<string, unknown>)
      .filter((m) => m.tipo === tipo);
  const ultimoFotos = () => mensajes('fotos').at(-1)!;
  const subida = (): TestRequest => http.expectOne((r) => r.method === 'POST' && r.url === URL_IMAGENES);

  function subir(clave: string, imagenId = 50, url = 'https://x/n.webp'): void {
    elegir(clave, foto());
    subida().flush({ imagenId, url }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
  }

  it('subida: POST multipart `archivo` con progreso; la barra avisa y Guardar no se puede pulsar', () => {
    abrir();
    const archivo = foto('entrenamiento-mollina.jpg');
    elegir('portada.foto', archivo);
    const peticion = subida();
    expect(peticion.request.body instanceof FormData).toBeTrue();
    expect((peticion.request.body as FormData).get('archivo')).toEqual(archivo);
    expect(peticion.request.reportProgress).toBeTrue();
    expect(peticion.request.withCredentials).toBeTrue();
    expect(ultimoFotos()['subidas']).toEqual({ 'portada.foto': { estado: 'subiendo', pct: 0, nombre: 'entrenamiento-mollina.jpg' } });
    expect(texto('[role=status].oculto')).toBe('Subiendo la foto.');

    peticion.event({ type: HttpEventType.UploadProgress, loaded: 62, total: 100 });
    fixture.detectChanges();
    expect(ultimoFotos()['subidas']).toEqual({ 'portada.foto': { estado: 'subiendo', pct: 62, nombre: 'entrenamiento-mollina.jpg' } });
    // Boceto `pintarBarra` con subida: sin cambios todavía, la barra sale igual.
    expect(texto('.barra__texto')).toBe('Se está subiendo una foto. Podrás guardar cuando termine.');
    const guardar = boton('Guardar');
    expect(guardar.disabled).toBeTrue();
    guardar.click();
    http.expectNone((r) => r.method === 'PUT');

    peticion.flush({ imagenId: 50, url: 'https://x/n.webp' }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
    expect(ultimoFotos()).toEqual({
      canal: CANAL_EDITOR_WEB,
      tipo: 'fotos',
      cambiadas: ['portada.foto'],
      anteriores: ['portada.foto'],
      subidas: {},
    });
    // La vista previa enseña la foto nueva antes de guardar (por el puente) y la capa abre «describe la foto».
    const borrador = mensajes('borrador').at(-1)!['bloques'] as { clave: string; imagenUrl: string | null; texto: string | null }[];
    expect(borrador.find((b) => b.clave === 'portada.foto')).toEqual(jasmine.objectContaining({ imagenUrl: 'https://x/n.webp', texto: '' }));
    expect(borrador.every((b) => !('imagenId' in b) && !('permiso' in b))).toBeTrue();
    expect(enviados).toHaveBeenCalledWith({ canal: CANAL_EDITOR_WEB, tipo: 'describir', clave: 'portada.foto' }, location.origin);
    expect(texto('[role=status].oculto')).toBe('Foto subida. Describe qué se ve.');
    expect(texto('.barra__texto strong')).toBe('2 cambios sin guardar');
    expect(boton('Guardar').disabled).toBeFalse();
  });

  it('«Cancelar subida»: corta la petición; la foto de antes sigue', () => {
    abrir();
    elegir('presentacion.foto', foto());
    const peticion = subida();
    accion('presentacion.foto', 'cancelar-subida');
    expect(peticion.cancelled).toBeTrue();
    expect(ultimoFotos()['subidas']).toEqual({});
    expect(texto('[role=status].oculto')).toBe('Subida cancelada.');
    expect(q('.barra')).toBeNull();
  });

  it('«Demasiado grande» (más de 50 MB): no llama a la API', () => {
    abrir();
    elegir('portada.foto', foto('exhibicion-navidad.jpg', 'image/jpeg', 64 * MB));
    http.expectNone(URL_IMAGENES);
    expect(ultimoFotos()['subidas']).toEqual({
      'portada.foto': { estado: 'error', titulo: 'Demasiado grande', msg: '«exhibicion-navidad.jpg» pesa 64 MB. Usa una foto de menos de 50 MB.' },
    });
    expect(q('.barra')).toBeNull();
    // «Cerrar» quita el aviso.
    accion('portada.foto', 'cancelar-subida');
    expect(ultimoFotos()['subidas']).toEqual({});
  });

  it('«Formato no admitido» (HEIC): no llama a la API', () => {
    abrir();
    elegir('portada.foto', foto('grupo-estepa.heic', ''));
    http.expectNone(URL_IMAGENES);
    expect(ultimoFotos()['subidas']).toEqual({
      'portada.foto': {
        estado: 'error',
        titulo: 'Formato no admitido',
        msg: '«grupo-estepa.heic»: convierte la foto a JPG o desactiva HEIF en la cámara.',
      },
    });
  });

  it('fallos de la API: 413 «Demasiado grande», 400 de formato y error genérico', () => {
    abrir();
    elegir('portada.foto', foto('a.jpg', 'image/jpeg', 49 * MB));
    subida().flush(null, { status: 413, statusText: 'Payload Too Large' });
    fixture.detectChanges();
    expect(ultimoFotos()['subidas']).toEqual({
      'portada.foto': { estado: 'error', titulo: 'Demasiado grande', msg: '«a.jpg» pesa 49 MB. Usa una foto de menos de 50 MB.' },
    });
    elegir('portada.foto', foto('b.png', 'image/png'));
    subida().flush({ mensaje: 'La foto tiene que ser JPG, PNG o WebP.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect((ultimoFotos()['subidas'] as Record<string, { titulo: string }>)['portada.foto'].titulo).toBe('Formato no admitido');
    elegir('portada.foto', foto('c.jpg'));
    subida().flush({ mensaje: 'Error al guardar la foto.' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(ultimoFotos()['subidas']).toEqual({
      'portada.foto': { estado: 'error', titulo: 'No se pudo subir la foto', msg: '«c.jpg»: inténtalo de nuevo.' },
    });
    // La foto de antes sigue puesta: ningún cambio.
    expect(q('.barra')).toBeNull();
  });

  it('describir: la descripción va en el texto de la fila; PUT con imagenId, orden y oculto', () => {
    abrir();
    subir('texto_foto.3.foto', 60);
    describir('texto_foto.3.foto', 'Tatami nuevo de la sala');
    expect(texto('.barra__texto strong')).toBe('2 cambios sin guardar');
    boton('Guardar').click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === URL_CONTENIDO);
    expect(put.request.body.version).toBe(5);
    expect(put.request.body.bloques).toContain({ clave: 'texto_foto.3.foto', texto: 'Tatami nuevo de la sala', imagenId: 60, orden: 0, oculto: false });
    expect(put.request.body.bloques).toContain({ clave: 'texto_foto.3', texto: null, imagenId: null, orden: 5, oculto: true });
    expect(put.request.body.bloques).toContain({ clave: 'galeria.1', texto: 'Exhibición', imagenId: 11, orden: 1, oculto: false });
    expect(put.request.body.bloques.every((b: object) => !('imagenUrl' in b) && !('permiso' in b))).toBeTrue();
    put.flush({ version: 6, bloques: [...BLOQUES, fila('texto_foto.3.foto', 'Tatami nuevo de la sala', { imagenId: 60, imagenUrl: 'https://x/n.webp' })] });
    fixture.detectChanges();
    expect(q('.barra')).toBeNull();
  });

  it('cambiar una foto guardada conserva su orden y oculto', () => {
    abrir({ version: 5, bloques: [...BLOQUES, fila('texto_foto.3.foto', 'Vieja', { imagenId: 9, imagenUrl: 'https://x/v.webp', orden: 2 })] });
    subir('texto_foto.3.foto', 61);
    describir('texto_foto.3.foto', 'Nueva');
    boton('Guardar').click();
    const put = http.expectOne((r) => r.method === 'PUT');
    expect(put.request.body.bloques).toContain({ clave: 'texto_foto.3.foto', texto: 'Nueva', imagenId: 61, orden: 2, oculto: false });
    put.flush({ version: 6, bloques: BLOQUES });
  });

  it('falta describir la foto: resumen «Foto de la portada: falta describirla» y su enlace abre la descripción', () => {
    abrir();
    subir('portada.foto');
    boton('Guardar').click();
    fixture.detectChanges();
    fixture.detectChanges();
    http.expectNone((r) => r.method === 'PUT');
    expect(texto('.resumen h2')).toBe('Falta 1 dato para guardar');
    expect(texto('.resumen li')).toBe('Foto de la portada: falta describirla');
    expect(enviados).toHaveBeenCalledWith(
      { canal: CANAL_EDITOR_WEB, tipo: 'errores', errores: { 'portada.foto.alt': 'Describe la foto.' } },
      location.origin,
    );
    q<HTMLAnchorElement>('.resumen a[data-ir="portada.foto.alt"]')!.click();
    expect(enviados).toHaveBeenCalledWith({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'portada.foto.alt' }, location.origin);
    describir('portada.foto', 'Niños saludando');
    expect(q('.resumen')).toBeNull();
  });

  it('sin foto de portada: «Portada: falta la foto» lleva a la foto y se resuelve subiéndola', () => {
    abrir({ version: 5, bloques: BLOQUES.filter((f) => f.clave !== 'portada.foto') });
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave: 'menu.horarios', texto: 'Horas' });
    boton('Guardar').click();
    fixture.detectChanges();
    fixture.detectChanges();
    expect(texto('.resumen li')).toBe('Portada: falta la foto');
    q<HTMLAnchorElement>('.resumen a[data-ir="portada.foto"]')!.click();
    expect(enviados).toHaveBeenCalledWith({ canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave: 'portada.foto' }, location.origin);
    subir('portada.foto');
    expect(texto('.resumen li')).toBe('Foto de la portada: falta describirla');
    describir('portada.foto', 'Niños saludando');
    expect(q('.resumen')).toBeNull();
    boton('Guardar').click();
    http.expectOne((r) => r.method === 'PUT').flush({ version: 6, bloques: BLOQUES });
  });

  it('«Volver a la anterior»: vuelve la foto guardada y deja de ser un cambio', () => {
    abrir();
    subir('portada.foto');
    accion('portada.foto', 'anterior');
    expect(q('.barra')).toBeNull();
    expect(ultimoFotos()).toEqual(jasmine.objectContaining({ cambiadas: [], anteriores: [] }));
    expect(texto('[role=status].oculto')).toBe('Vuelve la foto anterior.');
  });

  it('«Quitar foto»: fuera del PUT; la portada (obligatoria) no se quita', () => {
    abrir();
    accion('portada.foto', 'quitar');
    expect(q('.barra')).toBeNull();
    accion('presentacion.foto', 'quitar');
    expect(texto('.barra__texto strong')).toBe('2 cambios sin guardar');
    expect(texto('[role=status].oculto')).toBe('Foto quitada. Se quita de la web al guardar.');
    boton('Guardar').click();
    const put = http.expectOne((r) => r.method === 'PUT');
    expect(put.request.body.bloques.some((b: { clave: string }) => b.clave === 'presentacion.foto')).toBeFalse();
    expect(put.request.body.bloques.some((b: { clave: string }) => b.clave === 'portada.foto')).toBeTrue();
    put.flush({ version: 6, bloques: BLOQUES.filter((f) => f.clave !== 'presentacion.foto') });
  });

  it('Descartar: vuelven las fotos guardadas y se corta la subida en curso', () => {
    abrir();
    subir('portada.foto');
    elegir('presentacion.foto', foto());
    const peticion = subida();
    boton('Descartar').click();
    fixture.detectChanges();
    expect(peticion.cancelled).toBeTrue();
    expect(q('.barra')).toBeNull();
    expect(ultimoFotos()).toEqual({ canal: CANAL_EDITOR_WEB, tipo: 'fotos', cambiadas: [], anteriores: [], subidas: {} });
    const borrador = mensajes('borrador').at(-1)!['bloques'] as { clave: string; imagenUrl: string | null }[];
    expect(borrador.find((b) => b.clave === 'portada.foto')!.imagenUrl).toBe('https://x/p.webp');
  });

  it('salir con una foto cambiada: «Tienes 2 cambios sin guardar»', async () => {
    abrir();
    subir('portada.foto');
    expect(await fixture.componentInstance.puedeSalir()).toBeFalse();
    expect(confirmar).toHaveBeenCalledWith(jasmine.objectContaining({ titulo: 'Tienes 2 cambios sin guardar' }));
  });

  it('el puente no acepta subir ni tocar fotos de la galería o de profesores', () => {
    abrir();
    elegir('galeria.1', foto());
    accion('profesor.1.foto', 'quitar');
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'descripcion', clave: 'galeria.1', texto: 'Otra' });
    http.expectNone(URL_IMAGENES);
    expect(q('.barra')).toBeNull();
  });
});
