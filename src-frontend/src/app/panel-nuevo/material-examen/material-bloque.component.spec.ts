import { LiveAnnouncer } from '@angular/cdk/a11y';
import { HttpClient, HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { MaterialBloqueComponent } from './material-bloque.component';
import { CLIENTE_SUBIDAS } from './material-examen-api.service';
import { DetalleBloque, DocumentoDetalle, VideoDetalle } from './material-examen.models';

const API = `${environment.apiUrl}/material-examen/TAEKWONDO/b01`;
const MB = 1024 * 1024;

function video(fichero: string, titulo: string, cambios: Partial<VideoDetalle> = {}): VideoDetalle {
  return { fichero, titulo, orden: 1, bytes: 58 * MB, fecha: '2026-09-12T10:00:00Z', tambienEnSuperiores: false, ...cambios };
}

function detalle(cambios: Partial<DetalleBloque> = {}): DetalleBloque {
  return {
    disciplina: 'TAEKWONDO',
    id: 'b01',
    nombre: 'Blanco a amarillo',
    grados: [
      { codigo: 'BLANCO', nombre: 'BLANCO' },
      { codigo: 'BLANCO_AMARILLO', nombre: 'BLANCO-AMARILLO' },
    ],
    temario: { nombre: 'temario.pdf', bytes: 248 * 1024, fecha: '2026-09-12T10:00:00Z' },
    videos: [
      video('a.mp4', 'Posiciones básicas'),
      video('b.mp4', 'Técnicas de brazo'),
      video('c.mp4', 'Taeguk Il Jang', { tambienEnSuperiores: true }),
    ],
    documentos: [{ fichero: 'normas.pdf', titulo: 'Normas del examen', mime: 'application/pdf', bytes: 1.2 * MB, fecha: '2026-09-12T10:00:00Z' }],
    ...cambios,
  };
}

const VACIO = detalle({ temario: null, videos: [], documentos: [] });

function ficheroVideo(nombre: string, bytes = 1000): File {
  const archivo = new File(['x'], nombre, { type: 'video/mp4' });
  Object.defineProperty(archivo, 'size', { value: bytes });
  return archivo;
}

describe('MaterialBloqueComponent (pieza R2, un bloque)', () => {
  let fixture: ComponentFixture<MaterialBloqueComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let confirmar: jasmine.Spy;
  let exito: jasmine.Spy;
  let error: jasmine.Spy;
  let anuncio: jasmine.Spy;
  let conSesion: boolean;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const comp = (): MaterialBloqueComponent => fixture.componentInstance;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const barra = (): HTMLElement | null => el().querySelector<HTMLElement>('.material__barra');
  const titulos = (): string[] => Array.from(el().querySelectorAll('.vid__titulo')).map((n) => texto(n));
  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.vid'));
  const panel = (): HTMLElement | null => el().querySelector<HTMLElement>('dialog.pn-panel');

  function boton(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find((x) => (x.getAttribute('aria-label') ?? '').trim() === t || texto(x) === t);
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  async function esperar(): Promise<void> {
    await fixture.whenStable();
    // Leer un Blob es asíncrono de verdad: unas vueltas de reloj real, no microtareas.
    for (let i = 0; i < 10; i++) {
      await new Promise((r) => setTimeout(r, 5));
    }
    pintar();
  }

  function cargar(d: DetalleBloque = detalle(), movil = false): void {
    fixture = TestBed.createComponent(MaterialBloqueComponent);
    http = TestBed.inject(HttpTestingController);
    comp().movil = movil;
    fixture.detectChanges();
    comp().movil = movil;
    http.expectOne(API).flush(d);
    pintar();
  }

  function abrirMenu(fila: HTMLElement): void {
    fila.querySelector<HTMLButtonElement>('.menu__boton')!.click();
    pintar();
  }

  function opcion(t: string): HTMLButtonElement {
    const o = Array.from(el().querySelectorAll<HTMLButtonElement>('[role="menuitem"]')).find((x) => texto(x) === t);
    if (!o) {
      throw new Error(`Sin opción «${t}»`);
    }
    return o;
  }

  function elegirFicheros(selector: string, archivos: File[]): void {
    const entrada = el().querySelector<HTMLInputElement>(selector)!;
    const lista = new DataTransfer();
    archivos.forEach((a) => lista.items.add(a));
    entrada.files = lista.files;
    entrada.dispatchEvent(new Event('change'));
  }

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    conSesion = true;
    TestBed.configureTestingModule({
      imports: [MaterialBloqueComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CLIENTE_SUBIDAS, useExisting: HttpClient },
        { provide: CatalogoService, useValue: { etiquetaDisciplina: () => 'Taekwondo' } },
        { provide: AuthenticationService, useValue: { comprobarLogueado: () => conSesion } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ disciplina: 'TAEKWONDO', bloque: 'b01' }), queryParamMap: convertToParamMap({}) } } },
      ],
    });
    confirmar = spyOn(TestBed.inject(ConfirmacionService), 'confirmar').and.resolveTo({ confirmado: true } as never);
    exito = spyOn(TestBed.inject(AvisosService), 'exito');
    error = spyOn(TestBed.inject(AvisosService), 'error');
    anuncio = spyOn(TestBed.inject(LiveAnnouncer), 'announce').and.resolveTo();
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    quitarTokens();
  });

  describe('lectura', () => {
    it('miga, título y a quién llega', () => {
      cargar();
      expect(texto(el().querySelector('.material__miga'))).toBe('Material de examen/Taekwondo');
      expect(texto(el().querySelector('h1'))).toBe('Blanco a amarillo');
      expect(texto(el().querySelector('.material__cabecera .material__sub'))).toBe('Lo ven las familias de alumnos de blanco a blanco-amarillo.');
    });

    it('vídeos: línea de resumen, datos y pastilla', () => {
      cargar();
      expect(texto(el().querySelector('.material__seccion--videos .material__sub'))).toBe('3 vídeos · 174 MB · Hasta 500 MB por vídeo');
      expect(texto(filas()[0].querySelector('.vid__dato'))).toBe('MP4 · 58 MB');
      expect(filas()[0].querySelector('.pas')).toBeNull();
      expect(texto(filas()[2].querySelector('.pas'))).toBe('También en grados superiores');
    });

    it('temario y documentación con sus datos', () => {
      cargar();
      expect(texto(el().querySelector('.material__seccion--temario .fich__nombre'))).toBe('temario.pdf');
      expect(texto(el().querySelector('.material__seccion--temario .fich__dato'))).toBe('248 KB · 12/09/2026');
      expect(texto(el().querySelector('.material__seccion--documentos .fich__dato'))).toBe('PDF · 1,2 MB');
    });

    it('sin material: textos y zonas vacías del boceto', () => {
      cargar(VACIO);
      expect(texto(el().querySelector('.material__cabecera .material__sub'))).toBe('Las familias aún no ven material en este bloque.');
      expect(texto(el().querySelector('.material__seccion--videos .material__sub'))).toBe('Hasta 500 MB por vídeo · MP4, WEBM, M4V o MOV');
      expect(texto(el().querySelector('.drop__fuerte'))).toBe('Arrastra aquí los vídeos');
      boton(el(), 'Elegir vídeos');
      expect(texto(el().querySelector('.material__seccion--temario .drop p'))).toBe('Un PDF con el temario de este bloque.');
      boton(el(), 'Elegir PDF');
      expect(texto(el().querySelector('.material__seccion--documentos .drop p'))).toBe('Normas, plantillas u otros documentos.');
      boton(el(), 'Añadir documento');
    });

    it('error con reintento', () => {
      fixture = TestBed.createComponent(MaterialBloqueComponent);
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();
      http.expectOne(API).flush({}, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      el().querySelector<HTMLButtonElement>('.pn-bloque-error__reintentar')!.click();
      http.expectOne(API).flush(detalle());
      fixture.detectChanges();
      expect(titulos().length).toBe(3);
    });
  });

  describe('orden', () => {
    it('en táctil, deslizar desde el asa arrastra sin esperar: el asa no desplaza la página', () => {
      cargar(detalle(), true);
      const asa = filas()[0].querySelector<HTMLElement>('.vid__asa')!;
      const { left, top } = asa.getBoundingClientRect();
      const tocar = (tipo: string, destino: EventTarget, y: number): void => {
        const toque = new Touch({ identifier: 1, target: asa, clientX: left, clientY: y, pageX: left, pageY: y });
        const activos = tipo === 'touchend' ? [] : [toque];
        destino.dispatchEvent(new TouchEvent(tipo, { bubbles: true, cancelable: true, touches: activos, targetTouches: activos, changedTouches: [toque] }));
      };

      tocar('touchstart', asa, top);
      tocar('touchmove', document, top + 20);
      const arrastrando = el().querySelector('.cdk-drag-preview') !== null;
      tocar('touchend', document, top + 20);

      expect(arrastrando).toBeTrue();
    });

    it('mover resalta la fila, lo anuncia y deja la barra', () => {
      cargar();
      expect(barra()).toBeNull();
      boton(filas()[2], 'Subir «Taeguk Il Jang»').click();
      pintar();
      expect(titulos()).toEqual(['Posiciones básicas', 'Taeguk Il Jang', 'Técnicas de brazo']);
      expect(filas()[1].classList).toContain('vid--movida');
      expect(texto(barra()?.querySelector('strong'))).toBe('Orden sin guardar');
      boton(barra()!, 'Descartar');
      boton(barra()!, 'Guardar orden');
      expect(anuncio).toHaveBeenCalledWith('«Taeguk Il Jang» movido a la posición 2 de 3.');
    });

    it('el foco sigue al mando de la fila movida y las flechas también mueven', () => {
      cargar();
      document.body.appendChild(fixture.nativeElement);
      const bajar = boton(filas()[0], 'Bajar «Posiciones básicas»');
      bajar.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
      pintar();
      expect(titulos()[1]).toBe('Posiciones básicas');
      expect(document.activeElement).toBe(boton(filas()[1], 'Bajar «Posiciones básicas»'));
      (fixture.nativeElement as HTMLElement).remove();
    });

    it('en los extremos los botones van deshabilitados', () => {
      cargar();
      expect(boton(filas()[0], 'Subir «Posiciones básicas»').disabled).toBeTrue();
      expect(boton(filas()[2], 'Bajar «Taeguk Il Jang»').disabled).toBeTrue();
    });

    it('volver al orden de partida quita la barra', () => {
      cargar();
      comp().mover(0, 1);
      comp().mover(1, -1);
      pintar();
      expect(barra()).toBeNull();
      expect(el().querySelector('.vid--movida')).toBeNull();
    });

    it('descartar vuelve al orden de la API', () => {
      cargar();
      comp().mover(0, 1);
      pintar();
      boton(barra()!, 'Descartar').click();
      pintar();
      expect(titulos()[0]).toBe('Posiciones básicas');
      expect(barra()).toBeNull();
    });

    it('guardar envía la lista completa y usa el detalle devuelto', () => {
      cargar();
      comp().mover(0, 1);
      pintar();
      boton(barra()!, 'Guardar orden').click();
      const r = http.expectOne(`${API}/videos/orden`);
      expect(r.request.method).toBe('PUT');
      expect(r.request.body).toEqual(['b.mp4', 'a.mp4', 'c.mp4']);
      r.flush(detalle({ videos: [video('b.mp4', 'Técnicas de brazo'), video('a.mp4', 'Posiciones básicas'), video('c.mp4', 'Taeguk Il Jang')] }));
      pintar();
      expect(barra()).toBeNull();
      expect(el().querySelector('.vid--movida')).toBeNull();
      expect(exito).toHaveBeenCalled();
    });

    it('un 409 enseña su mensaje y recarga la lista', () => {
      cargar();
      comp().mover(0, 1);
      pintar();
      boton(barra()!, 'Guardar orden').click();
      http.expectOne(`${API}/videos/orden`).flush({ mensaje: 'La lista de vídeos ha cambiado.' }, { status: 409, statusText: 'Conflict' });
      expect(error).toHaveBeenCalledWith('La lista de vídeos ha cambiado.');
      http.expectOne(API).flush(detalle({ videos: [video('d.mp4', 'Nuevo')] }));
      pintar();
      expect(titulos()).toEqual(['Nuevo']);
      expect(barra()).toBeNull();
    });

    it('otro fallo al guardar conserva el orden local', () => {
      cargar();
      comp().mover(0, 1);
      pintar();
      boton(barra()!, 'Guardar orden').click();
      http.expectOne(`${API}/videos/orden`).flush({}, { status: 500, statusText: 'Error' });
      pintar();
      expect(titulos()[0]).toBe('Técnicas de brazo');
      expect(barra()).not.toBeNull();
    });

    it('con un orden sin guardar, una escritura respeta el orden y añade lo nuevo al final', () => {
      cargar();
      comp().mover(0, 1);
      (comp() as unknown as { aplicar(d: DetalleBloque): void }).aplicar(detalle({ videos: [...detalle().videos, video('d.mp4', 'Nuevo')] }));
      pintar();
      expect(titulos()).toEqual(['Técnicas de brazo', 'Posiciones básicas', 'Taeguk Il Jang', 'Nuevo']);
      expect(barra()).not.toBeNull();
    });

    describe('detalles cruzados: uno más viejo nunca pisa a uno más nuevo', () => {
      const A = video('a.mp4', 'Posiciones básicas');
      const B = video('b.mp4', 'Técnicas de brazo');
      const C = video('c.mp4', 'Taeguk Il Jang');
      const D = video('d.mp4', 'Nuevo');
      const conD = (): DetalleBloque => detalle({ videos: [A, B, C, D] });

      async function subirD(): Promise<TestRequest> {
        elegirFicheros('input[multiple]', [ficheroVideo('d.mp4')]);
        await esperar();
        return http.expectOne(`${API}/videos?nombre=d.mp4`);
      }

      function guardar(): TestRequest {
        comp().mover(0, 1);
        pintar();
        boton(barra()!, 'Guardar orden').click();
        return http.expectOne(`${API}/videos/orden`);
      }

      it('la respuesta de guardar, llegada tras la subida, no borra el vídeo y se relee el bloque', async () => {
        cargar();
        const subida = await subirD();
        const orden = guardar();

        subida.flush({ fichero: 'd.mp4', bloque: conD() });
        orden.flush(detalle({ videos: [B, A, C] }));
        pintar();
        expect(titulos()).toEqual(['Técnicas de brazo', 'Posiciones básicas', 'Taeguk Il Jang', 'Nuevo']);
        expect(barra()).toBeNull();
        expect(exito).toHaveBeenCalledWith('Orden guardado.');

        http.expectOne(API).flush(detalle({ videos: [B, A, C, D] }));
        pintar();
        expect(titulos()).toEqual(['Técnicas de brazo', 'Posiciones básicas', 'Taeguk Il Jang', 'Nuevo']);
        expect(barra()).toBeNull();
      });

      it('la subida que acaba tras guardar no recoloca el orden: se relee', async () => {
        cargar();
        const subida = await subirD();
        const orden = guardar();

        orden.flush(detalle({ videos: [B, A, C] }));
        subida.flush({ fichero: 'd.mp4', bloque: conD() });
        http.expectOne(API).flush(detalle({ videos: [B, A, C, D] }));
        pintar();
        expect(titulos()).toEqual(['Técnicas de brazo', 'Posiciones básicas', 'Taeguk Il Jang', 'Nuevo']);
        expect(barra()).toBeNull();
      });

      it('dos escrituras cruzadas: la segunda respuesta no se aplica y se relee', async () => {
        cargar();
        comp().editando = 'a.mp4';
        comp().guardarTitulo('Otro');
        const titulo = http.expectOne(`${API}/videos/a.mp4`);
        comp().editando = null;
        pintar();
        abrirMenu(filas()[2]);
        opcion('Eliminar').click();
        await esperar();
        const borrado = http.expectOne(`${API}/videos/c.mp4`);

        borrado.flush(detalle({ videos: [A, B] }));
        titulo.flush(detalle({ videos: [video('a.mp4', 'Otro'), B, C] }));
        pintar();
        expect(titulos()).toEqual(['Posiciones básicas', 'Técnicas de brazo']);

        http.expectOne(API).flush(detalle({ videos: [video('a.mp4', 'Otro'), B] }));
        pintar();
        expect(titulos()).toEqual(['Otro', 'Técnicas de brazo']);
      });

      it('una relectura adelantada por una escritura se descarta y se repite', async () => {
        cargar();
        const subida = await subirD();
        guardar().flush({ mensaje: 'La lista de vídeos ha cambiado.' }, { status: 409, statusText: 'Conflict' });
        const lectura = http.expectOne(API);

        subida.flush({ fichero: 'd.mp4', bloque: conD() });
        lectura.flush(detalle());
        pintar();
        expect(titulos()).toContain('Nuevo');

        http.expectOne(API).flush(conD());
        pintar();
        expect(titulos()).toContain('Nuevo');
      });

      it('si el orden choca con un vídeo nuevo (409), se conserva, con el nuevo al final, y se avisa', () => {
        cargar();
        guardar().flush({ mensaje: 'La lista de vídeos ha cambiado.' }, { status: 409, statusText: 'Conflict' });
        expect(error).toHaveBeenCalledWith('La lista de vídeos ha cambiado.');

        http.expectOne(API).flush(conD());
        pintar();
        expect(titulos()).toEqual(['Técnicas de brazo', 'Posiciones básicas', 'Taeguk Il Jang', 'Nuevo']);
        expect(barra()).not.toBeNull();

        boton(barra()!, 'Guardar orden').click();
        const otra = http.expectOne(`${API}/videos/orden`);
        expect(otra.request.body).toEqual(['b.mp4', 'a.mp4', 'c.mp4', 'd.mp4']);
        otra.flush(detalle({ videos: [B, A, C, D] }));
        pintar();
        expect(barra()).toBeNull();
      });

      it('una relectura por escrituras cruzadas conserva el orden sin guardar', async () => {
        cargar();
        const subida = await subirD();
        comp().editando = 'a.mp4';
        comp().guardarTitulo('Otro');
        http.expectOne(`${API}/videos/a.mp4`).flush(detalle({ videos: [video('a.mp4', 'Otro'), B, C] }));
        comp().mover(0, 1);

        subida.flush({ fichero: 'd.mp4', bloque: conD() });
        http.expectOne(API).flush(detalle({ videos: [video('a.mp4', 'Otro'), B, C, D] }));
        pintar();
        expect(titulos()).toEqual(['Técnicas de brazo', 'Otro', 'Taeguk Il Jang', 'Nuevo']);
        expect(barra()).not.toBeNull();
      });

      it('tras destruir la pantalla no se relee', async () => {
        cargar();
        const subida = await subirD();
        const orden = guardar();
        subida.flush({ fichero: 'd.mp4', bloque: conD() });
        fixture.destroy();
        orden.flush(detalle({ videos: [B, A, C] }));
        http.expectNone(API);
      });

      it('sin cruces no se relee nada de más', async () => {
        cargar();
        const subida = await subirD();
        subida.flush({ fichero: 'd.mp4', bloque: conD() });
        pintar();
        guardar().flush(detalle({ videos: [B, A, C, D] }));
        pintar();
        expect(barra()).toBeNull();
        http.expectNone(API);
      });
    });

    it('en móvil: subir y bajar van en el menú y el botón es «Guardar»', () => {
      cargar(detalle(), true);
      expect(el().querySelector('.vid__subir')).toBeNull();
      abrirMenu(filas()[0]);
      expect(opcion('Subir').disabled).toBeTrue();
      opcion('Bajar').click();
      pintar();
      expect(titulos()[1]).toBe('Posiciones básicas');
      boton(barra()!, 'Guardar');
    });
  });

  describe('subidas', () => {
    it('sube con progreso y al acabar usa el detalle y lo anuncia', async () => {
      cargar();
      elegirFicheros('input[multiple]', [ficheroVideo('Taeguk Sam Jang.mp4', 142 * MB)]);
      await esperar();
      const r: TestRequest = http.expectOne(`${API}/videos?nombre=Taeguk%20Sam%20Jang.mp4`);
      r.event({ type: HttpEventType.UploadProgress, loaded: 88 * MB, total: 142 * MB });
      pintar();
      expect(el().querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('62');
      expect(texto(el().querySelector('.subida__texto'))).toBe('62 % · 88 de 142 MB · No cierres esta pantalla hasta que termine.');

      r.flush({ fichero: 'd.mp4', bloque: detalle({ videos: [...detalle().videos, video('d.mp4', 'Taeguk Sam Jang')] }) });
      pintar();
      expect(titulos()[3]).toBe('Taeguk Sam Jang');
      expect(el().querySelector('[role="progressbar"]')).toBeNull();
      expect(anuncio).toHaveBeenCalledWith('Subida terminada: Taeguk Sam Jang.mp4.');
    });

    it('el rechazo previo sale como en Primera y solo admite Quitar', async () => {
      cargar(VACIO);
      elegirFicheros('input[multiple]', [ficheroVideo('Examen_completo.mov', 612 * MB)]);
      await esperar();
      const fallo = el().querySelector<HTMLElement>('.fallo')!;
      expect(fallo.getAttribute('role')).toBe('alert');
      expect(texto(fallo.querySelector('.fallo__motivo'))).toBe('Pesa 612 MB. El máximo es 500 MB por vídeo.');
      expect(fallo.querySelectorAll('button').length).toBe(1);
      boton(fallo, 'Quitar Examen_completo.mov').click();
      pintar();
      expect(el().querySelector('.fallo')).toBeNull();
    });

    it('corte de conexión: Reintentar y Quitar, y la cola sigue', async () => {
      cargar(VACIO);
      elegirFicheros('input[multiple]', [ficheroVideo('uno.mp4', 142 * MB), ficheroVideo('dos.mp4', 10)]);
      await esperar();
      const uno = http.expectOne(`${API}/videos?nombre=uno.mp4`);
      uno.event({ type: HttpEventType.UploadProgress, loaded: 88 * MB, total: 142 * MB });
      uno.error(new ProgressEvent('error'));
      pintar();
      const fallo = el().querySelector<HTMLElement>('.fallo')!;
      expect(texto(fallo.querySelector('.fallo__motivo'))).toBe('Se cortó la conexión a los 88 MB. Vuelve a subirlo.');
      boton(fallo, 'Quitar uno.mp4');
      const dos = http.expectOne(`${API}/videos?nombre=dos.mp4`);
      dos.flush({ fichero: 'dos.mp4', bloque: detalle() });
      pintar();

      boton(el().querySelector('.fallo')!, 'Reintentar uno.mp4').click();
      pintar();
      http.expectOne(`${API}/videos?nombre=uno.mp4`).flush({ fichero: 'uno.mp4', bloque: detalle() });
    });

    it('arrastrar ficheros sobre Vídeos los añade', async () => {
      cargar(VACIO);
      const datos = new DataTransfer();
      datos.items.add(ficheroVideo('a.mp4'));
      const seccion = el().querySelector<HTMLElement>('.material__seccion--videos')!;
      seccion.dispatchEvent(new DragEvent('dragover', { dataTransfer: datos, bubbles: true, cancelable: true }));
      pintar();
      expect(seccion.classList).toContain('material__seccion--encima');
      seccion.dispatchEvent(new DragEvent('drop', { dataTransfer: datos, bubbles: true, cancelable: true }));
      await esperar();
      expect(seccion.classList).not.toContain('material__seccion--encima');
      http.expectOne(`${API}/videos?nombre=a.mp4`).flush({ fichero: 'a.mp4', bloque: VACIO });
    });

    it('al destruir la pantalla se corta la subida', async () => {
      cargar();
      elegirFicheros('input[multiple]', [ficheroVideo('a.mp4')]);
      await esperar();
      const r = http.expectOne(`${API}/videos?nombre=a.mp4`);
      fixture.destroy();
      expect(r.cancelled).toBeTrue();
    });
  });

  describe('salir', () => {
    it('sin cambios sale sin preguntar', async () => {
      cargar();
      expect(await comp().puedeSalir()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
    });

    it('con orden sin guardar pregunta y respeta la respuesta', async () => {
      cargar();
      comp().mover(0, 1);
      confirmar.and.resolveTo({ confirmado: false });
      expect(await comp().puedeSalir()).toBeFalse();
      expect(confirmar.calls.mostRecent().args[0].titulo).toBe('¿Salir sin guardar el orden?');
    });

    it('con una subida en curso pregunta; un fallo solo no retiene', async () => {
      cargar();
      elegirFicheros('input[multiple]', [ficheroVideo('a.mp4')]);
      await esperar();
      const r = http.expectOne(`${API}/videos?nombre=a.mp4`);
      expect(await comp().puedeSalir()).toBeTrue();
      expect(confirmar.calls.mostRecent().args[0].titulo).toBe('¿Salir y cortar la subida?');

      confirmar.calls.reset();
      r.flush({ mensaje: 'No' }, { status: 400, statusText: 'Bad' });
      expect(await comp().puedeSalir()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
    });

    it('un vídeo que aún se está preparando ya retiene al salir', async () => {
      cargar();
      const archivo = ficheroVideo('a.mp4');
      let soltar = (): void => undefined;
      const lectura = new Promise<ArrayBuffer>((r) => (soltar = () => r(new ArrayBuffer(1))));
      spyOn(archivo, 'slice').and.returnValue({ arrayBuffer: () => lectura } as unknown as Blob);
      confirmar.and.resolveTo({ confirmado: false });

      elegirFicheros('input[multiple]', [archivo]);
      expect(await comp().puedeSalir()).toBeFalse();
      expect(confirmar.calls.mostRecent().args[0].titulo).toBe('¿Salir y cortar la subida?');

      soltar();
      await esperar();
      http.expectOne(`${API}/videos?nombre=a.mp4`).flush({ fichero: 'a.mp4', bloque: detalle() });
    });

    it('sin sesión no pregunta, ni con orden sin guardar', async () => {
      cargar();
      comp().mover(0, 1);
      conSesion = false;
      expect(await comp().puedeSalir()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
    });

    it('sin sesión no pregunta, ni con una subida en curso', async () => {
      cargar();
      elegirFicheros('input[multiple]', [ficheroVideo('a.mp4')]);
      await esperar();
      const r = http.expectOne(`${API}/videos?nombre=a.mp4`);
      conSesion = false;
      expect(await comp().puedeSalir()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
      r.flush({ mensaje: 'No' }, { status: 401, statusText: 'Unauthorized' });
    });

    it('beforeunload solo avisa con algo en vuelo o sin guardar', () => {
      cargar();
      const sinCambios = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
      comp().antesDeSalir(sinCambios);
      expect(sinCambios.defaultPrevented).toBeFalse();
      comp().mover(0, 1);
      const conCambios = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
      comp().antesDeSalir(conCambios);
      expect(conCambios.defaultPrevented).toBeTrue();
    });
  });

  describe('editar y eliminar', () => {
    function abrirEditar(i = 2): void {
      abrirMenu(filas()[i]);
      opcion('Editar título').click();
      pintar();
    }

    const entrada = (): HTMLInputElement => panel()!.querySelector<HTMLInputElement>('#material-titulo')!;

    function escribir(valor: string): void {
      entrada().value = valor;
      entrada().dispatchEvent(new Event('input'));
      pintar();
    }

    it('el panel de editar como en el boceto, con la ayuda en Taekwondo', () => {
      cargar();
      abrirEditar();
      expect(texto(panel()!.querySelector('.pn-panel__titulo'))).toBe('Editar vídeo');
      expect(texto(panel()!.querySelector('.pn-panel__descripcion'))).toBe('Blanco a amarillo · Taekwondo');
      expect(entrada().value).toBe('Taeguk Il Jang');
      expect(texto(panel()!.querySelector('#material-titulo-ayuda'))).toBe('Si lleva «Taeguk» o «Pumse», también lo ven los grados superiores.14 / 120');
      expect(texto(panel()!.querySelector('.editar__nombre'))).toBe('c.mp4');
      expect(texto(panel()!.querySelector('.editar__dato'))).toBe('MP4 · 58 MB · 12/09/2026');
      expect(texto(panel()!)).toContain('Sustituirlo mantiene el título y el orden.');
    });

    it('sin la ayuda fuera de Taekwondo', () => {
      cargar(detalle({ disciplina: 'KICKBOXING' }));
      abrirEditar();
      expect(texto(panel()!.querySelector('#material-titulo-ayuda'))).toBe('14 / 120');
    });

    it('guarda el título recortado y usa el detalle devuelto', () => {
      cargar();
      abrirEditar();
      escribir('  Taeguk 1  ');
      boton(panel()!, 'Guardar').click();
      const r = http.expectOne(`${API}/videos/c.mp4`);
      expect(r.request.method).toBe('PATCH');
      expect(r.request.body).toEqual({ titulo: 'Taeguk 1' });
      r.flush(detalle({ videos: [video('c.mp4', 'Taeguk 1')] }));
      pintar();
      expect(panel()).toBeNull();
      expect(titulos()).toEqual(['Taeguk 1']);
    });

    it('sin título no envía nada y lo dice', () => {
      cargar();
      abrirEditar();
      escribir('   ');
      boton(panel()!, 'Guardar').click();
      pintar();
      expect(texto(panel()!.querySelector('.editar__error'))).toBe('Escribe un título.');
      http.expectNone(`${API}/videos/c.mp4`);
    });

    it('sustituir desde el panel enseña el progreso dentro del panel', async () => {
      cargar();
      abrirEditar();
      const selector = panel()!.querySelector<HTMLInputElement>('input[type="file"]')!;
      const lista = new DataTransfer();
      lista.items.add(ficheroVideo('nuevo.mp4'));
      selector.files = lista.files;
      selector.dispatchEvent(new Event('change'));
      await esperar();
      const r = http.expectOne(`${API}/videos/c.mp4/contenido`);
      expect(r.request.method).toBe('PUT');
      expect(panel()!.querySelector('[role="progressbar"]')).not.toBeNull();
      r.flush(detalle());
    });

    it('eliminar un vídeo: confirmación del boceto y detalle devuelto', async () => {
      cargar();
      abrirMenu(filas()[2]);
      opcion('Eliminar').click();
      await esperar();
      expect(confirmar.calls.mostRecent().args[0]).toEqual(
        jasmine.objectContaining({
          titulo: '¿Eliminar «Taeguk Il Jang»?',
          mensaje: 'Las familias dejarán de verlo en todos los grados. No se puede deshacer.',
          accion: 'Sí, eliminarlo',
          cancelar: 'Cancelar',
          destructivo: true,
        })
      );
      const r = http.expectOne(`${API}/videos/c.mp4`);
      expect(r.request.method).toBe('DELETE');
      r.flush(detalle({ videos: [video('a.mp4', 'Posiciones básicas')] }));
      pintar();
      expect(titulos()).toEqual(['Posiciones básicas']);
    });

    it('cancelar la confirmación no borra nada', async () => {
      cargar();
      confirmar.and.resolveTo({ confirmado: false });
      abrirMenu(filas()[0]);
      opcion('Eliminar').click();
      await esperar();
      http.expectNone(`${API}/videos/a.mp4`);
      expect(titulos().length).toBe(3);
    });

    it('eliminar un documento: frase corta del boceto', async () => {
      cargar();
      abrirMenu(el().querySelector<HTMLElement>('.docs .fich')!);
      opcion('Eliminar').click();
      await esperar();
      expect(confirmar.calls.mostRecent().args[0].mensaje).toBe('Las familias dejarán de verlo. No se puede deshacer.');
      http.expectOne(`${API}/documentacion/normas.pdf`).flush(detalle({ documentos: [] }));
    });
  });

  describe('foco al eliminar un documento', () => {
    const doc = (fichero: string): DocumentoDetalle => ({ fichero, titulo: fichero, mime: 'application/pdf', bytes: 10, fecha: '2026-09-12T10:00:00Z' });
    const menus = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.docs .menu__boton'));

    async function eliminar(indice: number, restantes: DocumentoDetalle[], fichero: string): Promise<void> {
      document.body.appendChild(fixture.nativeElement);
      abrirMenu(el().querySelectorAll<HTMLElement>('.docs .fich')[indice]);
      opcion('Eliminar').click();
      await esperar();
      http.expectOne(`${API}/documentacion/${fichero}`).flush(detalle({ documentos: restantes }));
      pintar();
    }

    afterEach(() => (fixture.nativeElement as HTMLElement).remove());

    it('pasa al documento siguiente', async () => {
      cargar(detalle({ documentos: [doc('uno.pdf'), doc('dos.pdf'), doc('tres.pdf')] }));
      await eliminar(0, [doc('dos.pdf'), doc('tres.pdf')], 'uno.pdf');
      expect(document.activeElement).toBe(menus()[0]);
    });

    it('sin siguiente, pasa al que queda antes', async () => {
      cargar(detalle({ documentos: [doc('uno.pdf'), doc('dos.pdf')] }));
      await eliminar(1, [doc('uno.pdf')], 'dos.pdf');
      expect(document.activeElement).toBe(menus()[0]);
    });

    it('si no queda ninguno, pasa a «Añadir documento»', async () => {
      cargar();
      await eliminar(0, [], 'normas.pdf');
      expect(document.activeElement).toBe(boton(el(), 'Añadir documento'));
    });
  });

  describe('ver', () => {
    it('el vídeo va por <video src> con la ruta de gestión, sin descargarlo antes', () => {
      cargar();
      abrirMenu(filas()[0]);
      opcion('Ver vídeo').click();
      pintar();
      const reproductor = panel()!.querySelector<HTMLVideoElement>('video')!;
      expect(reproductor.getAttribute('src')).toBe(`${API}/videos/a.mp4`);
      expect(reproductor.controls).toBeTrue();
      expect(reproductor.preload).toBe('metadata');
      http.expectNone(`${API}/videos/a.mp4`);
    });

    it('el temario se abre en el visor de PDF', () => {
      cargar();
      boton(el(), 'Ver el temario').click();
      http.expectOne(`${API}/temario`).flush(new Blob(['%PDF']));
      pintar();
      expect(comp().documentosPdf.length).toBe(1);
      comp().documentosPdf = [];
      fixture.detectChanges();
    });

    it('un documento que no es PDF se descarga', () => {
      cargar(detalle({ documentos: [{ fichero: 'lista.xlsx', titulo: 'Lista', mime: null, bytes: 10, fecha: '2026-09-12T10:00:00Z' }] }));
      const crear = spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
      spyOn(URL, 'revokeObjectURL');
      spyOn(HTMLAnchorElement.prototype, 'click');
      abrirMenu(el().querySelector<HTMLElement>('.docs .fich')!);
      opcion('Ver').click();
      http.expectOne(`${API}/documentacion/lista.xlsx`).flush(new Blob(['x']));
      expect(crear).toHaveBeenCalled();
      expect(comp().documentosPdf.length).toBe(0);
    });
  });

  describe('temario y documentación', () => {
    it('el temario se prepara, va crudo y usa el detalle devuelto', async () => {
      cargar(VACIO);
      elegirFicheros('.material__seccion--temario input', [new File(['%PDF-1.7'], 'plan.pdf', { type: 'application/pdf' })]);
      await esperar();
      const r = http.expectOne(`${API}/temario`);
      expect(r.request.method).toBe('PUT');
      expect(r.request.body instanceof File).toBeTrue();
      r.flush(detalle());
      pintar();
      expect(texto(el().querySelector('.material__seccion--temario .fich__nombre'))).toBe('temario.pdf');
    });

    it('un temario que no es PDF no se envía', async () => {
      cargar(VACIO);
      elegirFicheros('.material__seccion--temario input', [new File(['x'], 'plan.docx')]);
      await esperar();
      http.expectNone(`${API}/temario`);
      expect(error).toHaveBeenCalledWith('El temario debe ser un PDF.');
    });

    it('sustituir un documento exige su mismo tipo', async () => {
      cargar();
      abrirMenu(el().querySelector<HTMLElement>('.docs .fich')!);
      const click = spyOn(HTMLInputElement.prototype, 'click');
      opcion('Sustituir').click();
      expect(click).toHaveBeenCalled();
      const selector = el().querySelectorAll<HTMLInputElement>('.material__seccion--documentos input')[1];
      const lista = new DataTransfer();
      lista.items.add(new File(['x'], 'otro.docx'));
      selector.files = lista.files;
      selector.dispatchEvent(new Event('change'));
      await esperar();
      expect(error).toHaveBeenCalledWith('Este documento solo se puede sustituir por otro PDF.');
    });

    describe('sustituir un documento con el fichero preparado', () => {
      function sustituir(archivo: File): void {
        spyOn(HTMLInputElement.prototype, 'click');
        comp().accionDocumento(detalle().documentos[0], 'sustituir');
        const selector = el().querySelectorAll<HTMLInputElement>('.material__seccion--documentos input')[1];
        const lista = new DataTransfer();
        lista.items.add(archivo);
        selector.files = lista.files;
        selector.dispatchEvent(new Event('change'));
      }

      it('acepta el PDF sin extensión y con MIME válido que entrega Android', async () => {
        cargar();
        sustituir(new File(['%PDF-1.7'], 'Documento', { type: 'application/pdf' }));
        await esperar();
        const r = http.expectOne(`${API}/documentacion/normas.pdf/contenido`);
        expect((r.request.body as File).name).toBe('Documento.pdf');
        r.flush(detalle());
        expect(exito).toHaveBeenCalledWith('Documento sustituido.');
      });

      it('acepta el PDF sin extensión ni MIME, reconocido por su firma', async () => {
        cargar();
        sustituir(new File(['%PDF-1.7'], 'Documento'));
        await esperar();
        http.expectOne(`${API}/documentacion/normas.pdf/contenido`).flush(detalle());
        expect(error).not.toHaveBeenCalled();
      });

      it('sigue rechazando otro tipo una vez preparado, y se puede volver a elegir', async () => {
        cargar();
        sustituir(new File(['x'], 'Documento', { type: 'image/png' }));
        await esperar();
        expect(error).toHaveBeenCalledWith('Este documento solo se puede sustituir por otro PDF.');
        expect(comp().enviandoDocumento).toBeFalse();
        http.expectNone(`${API}/documentacion/normas.pdf/contenido`);
      });
    });

    describe('bloqueo desde la selección', () => {
      /** Un fichero cuya preparación no acaba hasta que se llame a `soltar`. */
      function lento(nombre: string, contenido = '%PDF-1.7'): { archivo: File; soltar: () => void } {
        const archivo = new File([contenido], nombre, { type: 'application/pdf' });
        let soltar = (): void => undefined;
        const lectura = new Promise<ArrayBuffer>((r) => (soltar = () => r(new TextEncoder().encode(contenido).buffer)));
        spyOn(archivo, 'arrayBuffer').and.returnValue(lectura);
        return { archivo, soltar: () => soltar() };
      }

      it('una segunda selección de temario durante la preparación no lanza otra subida', async () => {
        cargar(VACIO);
        const primero = lento('uno.pdf');
        const segundo = lento('dos.pdf');
        elegirFicheros('.material__seccion--temario input', [primero.archivo]);
        expect(comp().enviandoTemario).toBeTrue();
        expect(comp().subiendo).toBeTrue();

        elegirFicheros('.material__seccion--temario input', [segundo.archivo]);
        primero.soltar();
        segundo.soltar();
        await esperar();

        const r = http.expectOne(`${API}/temario`);
        expect((r.request.body as File).name).toBe('uno.pdf');
        expect(comp().enviandoTemario).toBeTrue();
        r.flush(detalle());
        expect(comp().enviandoTemario).toBeFalse();
      });

      it('una segunda selección de documento durante la preparación no lanza otra subida', async () => {
        cargar(VACIO);
        const primero = lento('uno.pdf');
        const segundo = lento('dos.pdf');
        elegirFicheros('.material__seccion--documentos input', [primero.archivo]);
        expect(comp().enviandoDocumento).toBeTrue();

        elegirFicheros('.material__seccion--documentos input', [segundo.archivo]);
        primero.soltar();
        segundo.soltar();
        await esperar();

        const r = http.expectOne(`${API}/documentacion?nombre=uno.pdf`);
        expect(comp().enviandoDocumento).toBeTrue();
        r.flush({ fichero: 'uno.pdf', bloque: detalle() });
        expect(comp().enviandoDocumento).toBeFalse();
      });

      it('sustituir un documento durante la preparación de otro no lanza otra subida', async () => {
        cargar();
        const primero = lento('uno.pdf');
        const segundo = lento('dos.pdf');
        elegirFicheros('.material__seccion--documentos input', [primero.archivo]);

        spyOn(HTMLInputElement.prototype, 'click');
        comp().accionDocumento(detalle().documentos[0], 'sustituir');
        const selector = el().querySelectorAll<HTMLInputElement>('.material__seccion--documentos input')[1];
        const lista = new DataTransfer();
        lista.items.add(segundo.archivo);
        selector.files = lista.files;
        selector.dispatchEvent(new Event('change'));
        primero.soltar();
        segundo.soltar();
        await esperar();

        http.expectOne(`${API}/documentacion?nombre=uno.pdf`).flush({ fichero: 'uno.pdf', bloque: detalle() });
        http.expectNone(`${API}/documentacion/normas.pdf/contenido`);
      });

      it('si la preparación rechaza el fichero, se puede volver a elegir', async () => {
        cargar(VACIO);
        elegirFicheros('.material__seccion--temario input', [new File(['x'], 'plan.docx')]);
        await esperar();
        expect(comp().enviandoTemario).toBeFalse();

        elegirFicheros('.material__seccion--documentos input', [new File(['x'], 'plan.exe')]);
        await esperar();
        expect(comp().enviandoDocumento).toBeFalse();

        elegirFicheros('.material__seccion--temario input', [new File(['%PDF-1.7'], 'plan.pdf', { type: 'application/pdf' })]);
        await esperar();
        http.expectOne(`${API}/temario`).flush(detalle());
      });

      it('sin fichero elegido no se bloquea nada', async () => {
        cargar(VACIO);
        el().querySelector<HTMLInputElement>('.material__seccion--temario input')!.dispatchEvent(new Event('change'));
        el().querySelector<HTMLInputElement>('.material__seccion--documentos input')!.dispatchEvent(new Event('change'));
        await esperar();
        expect(comp().subiendo).toBeFalse();
      });
    });

    describe('al destruir la pantalla', () => {
      const pdf = (nombre: string): File => new File(['%PDF-1.7'], nombre, { type: 'application/pdf' });

      it('corta la subida del temario', async () => {
        cargar(VACIO);
        elegirFicheros('.material__seccion--temario input', [pdf('plan.pdf')]);
        await esperar();
        const r = http.expectOne(`${API}/temario`);
        fixture.destroy();
        expect(r.cancelled).toBeTrue();
      });

      it('corta la subida de un documento', async () => {
        cargar(VACIO);
        elegirFicheros('.material__seccion--documentos input', [pdf('normas.pdf')]);
        await esperar();
        const r = http.expectOne(`${API}/documentacion?nombre=normas.pdf`);
        fixture.destroy();
        expect(r.cancelled).toBeTrue();
      });

      it('corta la sustitución de un documento', async () => {
        cargar();
        spyOn(HTMLInputElement.prototype, 'click');
        comp().accionDocumento(detalle().documentos[0], 'sustituir');
        const selector = el().querySelectorAll<HTMLInputElement>('.material__seccion--documentos input')[1];
        const lista = new DataTransfer();
        lista.items.add(pdf('otro.pdf'));
        selector.files = lista.files;
        selector.dispatchEvent(new Event('change'));
        await esperar();
        const r = http.expectOne(`${API}/documentacion/normas.pdf/contenido`);
        fixture.destroy();
        expect(r.cancelled).toBeTrue();
      });

      it('no envía lo que aún se estaba preparando', async () => {
        cargar(VACIO);
        elegirFicheros('.material__seccion--temario input', [pdf('plan.pdf')]);
        fixture.destroy();
        await new Promise((r) => setTimeout(r, 50));
        http.expectNone(`${API}/temario`);
      });
    });

    it('un fallo de la API enseña su mensaje', async () => {
      cargar(VACIO);
      elegirFicheros('.material__seccion--documentos input', [new File(['%PDF'], 'normas.pdf', { type: 'application/pdf' })]);
      await esperar();
      http.expectOne(`${API}/documentacion?nombre=normas.pdf`).flush({ mensaje: 'Tipo de documento no permitido.' }, { status: 415, statusText: 'x' });
      expect(error).toHaveBeenCalledWith('Tipo de documento no permitido.');
    });
  });
});
