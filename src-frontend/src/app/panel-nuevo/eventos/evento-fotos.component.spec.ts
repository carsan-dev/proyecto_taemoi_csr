import { LiveAnnouncer } from '@angular/cdk/a11y';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { itSinReloj, leerFicherosEnLaZona } from '../../testing/reloj-prueba';
import { MOTIVO_HEIC } from '../../utils/subida-archivos.util';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EventoFotosComponent, MAX_FOTOS, SUBIDAS_A_LA_VEZ } from './evento-fotos.component';

const API = environment.apiUrl;
const FOTOS = `${API}/eventos/1/fotos`;
const setTimeoutReal = window.setTimeout.bind(window);

const BYTES_JPG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4, 5, 6, 7, 8]);
const jpg = (nombre: string): File => new File([BYTES_JPG], nombre, { type: 'image/jpeg' });
const nombres = (n: number): string[] => Array.from({ length: n }, (_, i) => `f${i}.jpg`);
const GUARDADAS = [
  { id: 11, url: `${FOTOS}/11`, orden: 0 },
  { id: 12, url: `${FOTOS}/12`, orden: 1 },
  { id: 13, url: `${FOTOS}/13`, orden: 2 },
];

describe('EventoFotosComponent (E8c · F2, bloque «Fotos del evento» del formulario de eventos)', () => {
  let fixture: ComponentFixture<EventoFotosComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let anuncios: jasmine.Spy;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const celdas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.fotos__celda--foto'));
  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  const contador = (): string => texto(el().querySelector('.fotos__contador'));

  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  async function esperar(veces = 6): Promise<void> {
    for (let i = 0; i < veces; i++) {
      await new Promise((r) => setTimeoutReal(r, 5));
    }
    pintar();
  }

  function montar(eventoId: number | null, guardadas: object[] | null = []): void {
    fixture = TestBed.createComponent(EventoFotosComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('eventoId', eventoId);
    fixture.detectChanges();
    if (eventoId !== null && guardadas) {
      http.expectOne(FOTOS).flush(guardadas);
    }
    fixture.detectChanges();
  }

  function elegir(archivos: File[]): void {
    const entrada = el().querySelector<HTMLInputElement>('#evento-fotos')!;
    const dt = new DataTransfer();
    archivos.forEach((a) => dt.items.add(a));
    entrada.files = dt.files;
    entrada.dispatchEvent(new Event('change'));
  }

  function boton(raiz: ParentNode, etiqueta: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => (x.getAttribute('aria-label') ?? '').replace(/\s+/g, ' ').trim() === etiqueta || texto(x) === etiqueta
    );
    if (!b) {
      throw new Error(`Sin botón «${etiqueta}»`);
    }
    return b;
  }

  /** Las peticiones `POST .../fotos` que han salido desde la última vez. */
  function subidas(): TestRequest[] {
    return http.match((r) => r.method === 'POST' && r.url === FOTOS);
  }

  function respuesta(id: number): object {
    return { id, url: `${FOTOS}/${id}`, orden: id };
  }

  beforeEach(async () => {
    quitarTokens = ponerTokensDePrueba();
    const contenidos: Record<string, Uint8Array | string> = { 'foto.heic': 'x' };
    nombres(45).forEach((n) => (contenidos[n] = BYTES_JPG));
    leerFicherosEnLaZona(contenidos);
    await TestBed.configureTestingModule({
      imports: [EventoFotosComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    anuncios = spyOn(TestBed.inject(LiveAnnouncer), 'announce').and.returnValue(Promise.resolve());
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    quitarTokens();
  });

  // ------------------------------------------------------------------ estructura

  it('crear: cabecera, «opcional», contador «0 / 40», ayuda, aviso neutro, pie y selector múltiple; sin pedir nada', () => {
    montar(null);
    expect(texto(el().querySelector('h2'))).toBe('Fotos del evento');
    expect(texto(el().querySelector('.evento-form__marca'))).toBe('opcional');
    expect(contador()).toBe('0 / 40');
    expect(texto(el().querySelector('.fotos__ayuda'))).toBe('Se ven en la página del evento. Arrastra para cambiar el orden.');
    expect(texto(el().querySelector('.fotos__aviso'))).toBe('Sube solo fotos de personas con autorización web.');
    expect(texto(el().querySelector('.fotos__pie'))).toBe('JPG, PNG, WebP o GIF. Hasta 50 MB por foto.');
    const entrada = el().querySelector<HTMLInputElement>('#evento-fotos')!;
    expect(entrada.multiple).toBeTrue();
    expect(entrada.accept).toBe('image/*');
    expect(texto(el().querySelector('.fotos__anadir--casilla'))).toBe('Añadir');
    expect(texto(el().querySelector('.fotos__anadir--boton'))).toBe('Añadir fotos');
    expect(el().querySelectorAll('.fotos__celda--foto').length).toBe(0);
    http.expectNone(() => true);
  });

  it('editar: GET .../fotos (ERROR_HANDLED_LOCALLY); miniaturas con ?w=640 y su versión; contador «3 / 40»', () => {
    fixture = TestBed.createComponent(EventoFotosComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('eventoId', 1);
    fixture.detectChanges();
    const peticion = http.expectOne(FOTOS);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(contador()).toBe('– / 40');
    peticion.flush([GUARDADAS[2], GUARDADAS[0], GUARDADAS[1]]);
    fixture.detectChanges();
    expect(contador()).toBe('3 / 40');
    expect(celdas().map((c) => c.querySelector('img')!.getAttribute('src'))).toEqual([
      `${FOTOS}/11?w=640&v=11`,
      `${FOTOS}/12?w=640&v=12`,
      `${FOTOS}/13?w=640&v=13`,
    ]);
    expect(texto(celdas()[0].querySelector('.fotos__oculto-texto'))).toBe('Foto 1 de 3, guardada');
  });

  it('editar con error al cargar: el error es del bloque (pn-bloque-error), con «Reintentar» que repite solo la lectura', () => {
    fixture = TestBed.createComponent(EventoFotosComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('eventoId', 1);
    fixture.detectChanges();
    http.expectOne(FOTOS).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(el().querySelector('pn-bloque-error')).not.toBeNull();
    expect(el().querySelector('.fotos__anadir')).toBeNull();
    boton(el(), 'Reintentar').click();
    http.expectOne(FOTOS).flush(GUARDADAS);
    fixture.detectChanges();
    expect(el().querySelector('pn-bloque-error')).toBeNull();
    expect(celdas().length).toBe(3);
  });

  // ------------------------------------------------------------------ añadir y validar

  itSinReloj('tope: con 42 fotos entran 40, el contador lo dice y un mensaje en línea cuenta las 2 descartadas', async () => {
    montar(null);
    elegir(nombres(42).map(jpg));
    await esperar(60);
    expect(celdas().length).toBe(MAX_FOTOS);
    expect(contador()).toBe('40 / 40');
    expect(texto(el().querySelector('#evento-fotos-error'))).toBe('Se han descartado 2 fotos: el máximo es 40 por evento.');
    expect(el().querySelector('.fotos__anadir')).toBeNull();
    expect(texto(el().querySelector('.fotos__pie'))).toBe('Has llegado al máximo de 40 fotos.');
  });

  itSinReloj('validación foto a foto: HEIC rechazada con su motivo en línea y la buena entra pendiente', async () => {
    montar(null);
    elegir([jpg('f0.jpg'), new File(['x'], 'foto.heic', { type: 'image/heic' })]);
    await esperar();
    expect(celdas().length).toBe(1);
    expect(celdas()[0].dataset['estado']).toBe('pendiente');
    expect(texto(celdas()[0].querySelector('.fotos__estado'))).toBe('Pendiente');
    expect(celdas()[0].querySelector('img')!.src).toMatch(/^blob:/);
    expect(texto(el().querySelector('#evento-fotos-error'))).toBe(`No se pudo añadir una foto. «foto.heic»: ${MOTIVO_HEIC}`);
  });

  itSinReloj('soltar ficheros sobre el bloque los añade; un arrastre que no es de ficheros no hace nada', async () => {
    montar(null);
    const seccion = el().querySelector('section')!;
    const dt = new DataTransfer();
    dt.items.add(jpg('f0.jpg'));
    dt.items.add(jpg('f1.jpg'));
    const encima = new DragEvent('dragover', { dataTransfer: dt, cancelable: true });
    seccion.dispatchEvent(encima);
    fixture.detectChanges();
    expect(encima.defaultPrevented).toBeTrue();
    expect(seccion.classList).toContain('fotos--encima');
    seccion.dispatchEvent(new DragEvent('drop', { dataTransfer: dt, cancelable: true }));
    await esperar();
    expect(seccion.classList).not.toContain('fotos--encima');
    expect(celdas().length).toBe(2);
    const texto$ = new DataTransfer();
    texto$.setData('text/plain', 'hola');
    const ajeno = new DragEvent('drop', { dataTransfer: texto$, cancelable: true });
    seccion.dispatchEvent(ajeno);
    expect(ajeno.defaultPrevented).toBeFalse();
  });

  // ------------------------------------------------------------------ URL locales

  itSinReloj('revoca las URL locales al quitar una pendiente (al momento, sin diálogo) y al destruir el bloque', async () => {
    const revocar = spyOn(URL, 'revokeObjectURL').and.callThrough();
    montar(null);
    elegir([jpg('f0.jpg'), jpg('f1.jpg')]);
    await esperar();
    const urls = celdas().map((c) => c.querySelector('img')!.src);
    boton(celdas()[0], 'Quitar la foto 1').click();
    fixture.detectChanges();
    expect(dialogo()).toBeNull();
    expect(celdas().length).toBe(1);
    expect(revocar).toHaveBeenCalledOnceWith(urls[0]);
    fixture.destroy();
    expect(revocar).toHaveBeenCalledWith(urls[1]);
    expect(revocar).toHaveBeenCalledTimes(2);
  });

  // ------------------------------------------------------------------ subir

  itSinReloj('subida en paralelo limitada a 3; campo «archivo»; al acabar, PUT .../orden con la secuencia final', async () => {
    montar(1, []);
    elegir(nombres(5).map(jpg));
    await esperar(20);
    expect(contador()).toBe('5 / 40');
    expect(fixture.componentInstance.cambios).toBe(5);
    const resultado = fixture.componentInstance.sincronizar(1);
    pintar();
    const abiertas = subidas();
    expect(abiertas.length).toBe(SUBIDAS_A_LA_VEZ);
    expect(abiertas[0].request.body instanceof FormData).toBeTrue();
    expect((abiertas[0].request.body as FormData).get('archivo')).toEqual(jasmine.any(File));
    expect(abiertas[0].request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(celdas().map((c) => c.dataset['estado'])).toEqual(['subiendo', 'subiendo', 'subiendo', 'subiendo', 'subiendo']);

    abiertas[0].flush(respuesta(101));
    await esperar(2);
    const cuarta = subidas();
    expect(cuarta.length).toBe(1);
    abiertas[1].flush(respuesta(102));
    abiertas[2].flush(respuesta(103));
    await esperar(2);
    const resto = subidas();
    expect(resto.length).toBe(1);
    cuarta[0].flush(respuesta(104));
    resto[0].flush(respuesta(105));
    await esperar(2);
    const orden = http.expectOne(`${FOTOS}/orden`);
    expect(orden.request.method).toBe('PUT');
    expect(orden.request.body).toEqual([101, 102, 103, 104, 105]);
    orden.flush([]);
    expect(await resultado).toEqual({ fallos: 0, errorOrden: false, motivos: [] });
    pintar();
    expect(celdas().every((c) => c.dataset['estado'] === 'guardada')).toBeTrue();
    expect(fixture.componentInstance.cambios).toBe(0);
  });

  itSinReloj('fallo parcial: la fallida queda con «Reintentar» (503 con su mensaje), las demás se guardan y se ordenan; reintentar la coloca', async () => {
    montar(1, []);
    elegir(nombres(3).map(jpg));
    await esperar(12);
    const resultado = fixture.componentInstance.sincronizar(1);
    const abiertas = subidas();
    abiertas[0].flush(respuesta(101));
    abiertas[1].flush({ mensaje: 'El servidor está ocupado. Inténtalo de nuevo.' }, { status: 503, statusText: 'Service Unavailable' });
    abiertas[2].flush(respuesta(103));
    await esperar(2);
    const orden1 = http.expectOne(`${FOTOS}/orden`);
    expect(orden1.request.body).toEqual([101, 103]);
    orden1.flush([]);
    const r = await resultado;
    expect(r.fallos).toBe(1);
    expect(r.motivos).toEqual(['El servidor está ocupado. Inténtalo de nuevo.']);
    pintar();
    expect(celdas().map((c) => c.dataset['estado'])).toEqual(['guardada', 'error', 'guardada']);
    expect(texto(celdas()[1].querySelector('.fotos__reintentar'))).toBe('Reintentar');
    expect(fixture.componentInstance.cambios).toBe(1);

    boton(celdas()[1], 'Reintentar la subida de la foto 2. El servidor está ocupado. Inténtalo de nuevo.').click();
    const otra = subidas();
    expect(otra.length).toBe(1);
    otra[0].flush(respuesta(102));
    await esperar(3);
    const orden2 = http.expectOne(`${FOTOS}/orden`);
    expect(orden2.request.body).toEqual([101, 102, 103]);
    orden2.flush([]);
    await esperar(3);
    expect(celdas().map((c) => c.dataset['estado'])).toEqual(['guardada', 'guardada', 'guardada']);
    expect(fixture.componentInstance.cambios).toBe(0);
  });

  // ------------------------------------------------------------------ orden

  it('orden con teclado: flechas en «después», foco que se queda, anuncio, extremos desactivados y «1 cambio»', () => {
    montar(1, GUARDADAS);
    expect(fixture.componentInstance.cambios).toBe(0);
    expect(boton(celdas()[0], 'Mover la foto 1 antes').disabled).toBeTrue();
    expect(boton(celdas()[2], 'Mover la foto 3 después').disabled).toBeTrue();
    const despues = boton(celdas()[0], 'Mover la foto 1 después');
    despues.focus();
    despues.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true }));
    fixture.detectChanges();
    expect(celdas().map((c) => c.querySelector('img')!.getAttribute('src'))).toEqual([
      `${FOTOS}/12?w=640&v=12`,
      `${FOTOS}/11?w=640&v=11`,
      `${FOTOS}/13?w=640&v=13`,
    ]);
    expect(anuncios).toHaveBeenCalledWith('Foto movida a la posición 2 de 3.');
    expect(document.activeElement).toBe(boton(celdas()[1], 'Mover la foto 2 después'));
    expect(fixture.componentInstance.cambios).toBe(1);
    // Con un botón al final de la fila, ArrowLeft lo devuelve.
    boton(celdas()[1], 'Mover la foto 2 antes').click();
    fixture.detectChanges();
    expect(celdas()[0].querySelector('img')!.getAttribute('src')).toBe(`${FOTOS}/11?w=640&v=11`);
  });

  it('orden por arrastre: soltar mueve la miniatura y cuenta como cambio', () => {
    montar(1, GUARDADAS);
    fixture.componentInstance.soltar({ previousIndex: 0, currentIndex: 2 } as never);
    fixture.detectChanges();
    expect(celdas().map((c) => c.querySelector('img')!.getAttribute('src'))).toEqual([
      `${FOTOS}/12?w=640&v=12`,
      `${FOTOS}/13?w=640&v=13`,
      `${FOTOS}/11?w=640&v=11`,
    ]);
    expect(fixture.componentInstance.cambios).toBe(1);
  });

  itSinReloj('guardar un cambio de orden envía PUT .../orden con la secuencia final y deja 0 cambios', async () => {
    montar(1, GUARDADAS);
    fixture.componentInstance.mover(2, -2);
    fixture.detectChanges();
    const resultado = fixture.componentInstance.sincronizar(1);
    await esperar(2);
    const orden = http.expectOne(`${FOTOS}/orden`);
    expect(orden.request.body).toEqual([13, 11, 12]);
    orden.flush([]);
    expect((await resultado).errorOrden).toBeFalse();
    expect(fixture.componentInstance.cambios).toBe(0);
  });

  // ------------------------------------------------------------------ quitar una guardada

  itSinReloj('quitar una guardada: confirmación destructiva, cancelar no envía; confirmar, DELETE al momento', async () => {
    montar(1, GUARDADAS);
    boton(celdas()[1], 'Quitar la foto 2').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Eliminar foto');
    expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
    boton(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone(() => true);
    expect(celdas().length).toBe(3);

    boton(celdas()[1], 'Quitar la foto 2').click();
    await esperar();
    boton(dialogo()!, 'Sí, eliminar').click();
    await esperar();
    const borrado = http.expectOne(`${FOTOS}/12`);
    expect(borrado.request.method).toBe('DELETE');
    borrado.flush(null);
    fixture.detectChanges();
    expect(celdas().length).toBe(2);
    expect(contador()).toBe('2 / 40');
    expect(fixture.componentInstance.cambios).toBe(0);
  });

  // ------------------------------------------------------------------ barra

  itSinReloj('cambios sin guardar: cuenta pendientes y cambio de orden', async () => {
    montar(1, GUARDADAS);
    expect(fixture.componentInstance.cambios).toBe(0);
    elegir([jpg('f0.jpg'), jpg('f1.jpg')]);
    await esperar();
    expect(fixture.componentInstance.cambios).toBe(2);
    fixture.componentInstance.mover(0, 1);
    expect(fixture.componentInstance.cambios).toBe(3);
  });
});
