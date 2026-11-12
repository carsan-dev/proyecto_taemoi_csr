import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { CANAL_EDITOR_WEB } from '../../../web-plantilla/editor/puente-editor';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import { ContenidoWeb, ORIGEN_EDITOR, VISOR_VISUAL } from './web-contenido-vista';
import { WebContenidoComponent } from './web-contenido.component';

const URL = `${environment.apiUrl}/escuela/web`;
const URL_CONTENIDO = `${URL}/contenido`;
const URL_PUBLICA = `${environment.apiUrl}/publico/web`;
/** El iframe de las pruebas no carga la página de Karma: un documento vacío del mismo origen. */
const ORIGEN = 'about:blank#';

const estadoWeb = (parcial: Partial<EstadoWebEscuela> = {}): EstadoWebEscuela => ({
  tema: 'linea-comarcal',
  publicada: true,
  publicadaEn: '2026-11-01T10:00:00Z',
  temasDisponibles: ['linea-comarcal'],
  ...parcial,
});
const SIN_PUBLICAR = estadoWeb({ tema: null, publicada: false, publicadaEn: null });

const CONTENIDO: ContenidoWeb = {
  version: 3,
  bloques: [
    { clave: 'portada.titular', texto: 'Taekwondo en seis pueblos', imagenId: null, imagenUrl: null, orden: 0, oculto: false, permiso: null },
    {
      clave: 'profesor.1.nombre',
      texto: 'Ana',
      imagenId: null,
      imagenUrl: null,
      orden: 0,
      oculto: false,
      permiso: { origen: 'ALTA', por: null, en: '2026-10-01T10:00:00' },
    },
  ],
};

describe('WebContenidoComponent (W12c)', () => {
  let fixture: ComponentFixture<WebContenidoComponent>;
  let http: HttpTestingController;
  let navegar: jasmine.Spy;
  /** El `visualViewport` que ve la pantalla: el del navegador o uno simulado (d551, teclado del móvil). */
  let visor: VisualViewport | null;

  beforeEach(() => {
    visor = window.visualViewport;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ORIGEN_EDITOR, useValue: ORIGEN },
        { provide: VISOR_VISUAL, useFactory: () => visor },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navegar = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
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

  function abrir(estado: EstadoWebEscuela = estadoWeb(), contenido: ContenidoWeb = CONTENIDO): void {
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(estado);
    http.expectOne(URL_CONTENIDO).flush(contenido);
    listas();
    fixture.detectChanges();
  }

  /** Sedes y eventos de la vista previa de gestión (W12c-bis), para «Una sede» y «Un evento». */
  function listas(): void {
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes: [] });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
  }

  /** Un mensaje que llega a la ventana del panel. */
  function llegar(data: unknown, origin = location.origin, source: Window | null = iframe()!.contentWindow): void {
    window.dispatchEvent(new MessageEvent('message', { data, origin, source }));
    fixture.detectChanges();
  }

  describe('estados', () => {
    it('cargando: título, sin estado ni vista previa', () => {
      fixture = TestBed.createComponent(WebContenidoComponent);
      fixture.detectChanges();
      expect(texto('h1')).toBe('Contenido');
      expect(Array.from(q('.migas')!.children).map((c) => c.textContent)).toEqual(['Web', '/']);
      expect(q('pn-esqueleto')).not.toBeNull();
      expect(q('.estado-web')).toBeNull();
      expect(q('.ver-web')).toBeNull();
      expect(iframe()).toBeNull();
      http.expectOne(URL).flush(estadoWeb());
      http.expectOne(URL_CONTENIDO).flush(CONTENIDO);
      listas();
    });

    it('error: el bloque de error con sus textos y «Reintentar» vuelve a leer', () => {
      fixture = TestBed.createComponent(WebContenidoComponent);
      fixture.detectChanges();
      const estado = http.expectOne(URL);
      http.expectOne(URL_CONTENIDO).flush(CONTENIDO);
      estado.flush(null, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      expect(texto('.pn-bloque-error__titulo')).toBe('No se pudo cargar el contenido de tu web');
      expect(texto('.pn-bloque-error__texto')).toBe('Tu web publicada no cambia. Vuelve a intentarlo en unos segundos.');
      expect(iframe()).toBeNull();
      expect(anuncio()).toBe('');
      q<HTMLButtonElement>('.pn-bloque-error__reintentar')!.click();
      http.expectOne(URL).flush(estadoWeb());
      http.expectOne(URL_CONTENIDO).flush(CONTENIDO);
      listas();
      fixture.detectChanges();
      expect(iframe()).not.toBeNull();
      expect(anuncio()).toBe('Contenido cargado.');
      expect(document.activeElement).toBe(q('h1'));
    });

    it('la primera carga no anuncia «Contenido cargado.»', () => {
      abrir();
      expect(anuncio()).toBe('');
    });

    it('publicada: «Publicada con «tema»», la ayuda, «Ver en la web» y el iframe en modo editor', () => {
      abrir();
      expect(texto('.estado-web')).toBe('Publicada con «La línea comarcal».');
      expect(q('.estado-web')!.classList).toContain('es-publicada');
      expect(texto('.ayuda-editor')).toBe(
        'Pulsa un texto o una foto para cambiarlo. Lo que tiene candado se cambia en otra pantalla. Lo que guardes se publica al momento.',
      );
      const ver = q<HTMLAnchorElement>('.ver-web')!;
      expect(ver.getAttribute('href')).toBe('/');
      expect(ver.target).toBe('_blank');
      expect(ver.rel).toBe('noopener');
      expect(texto('.ver-web')).toBe('Ver en la web (se abre en otra pestaña)');
      expect(texto('.marco__cab #marco-t')).toBe('Vista previa de tu web · Inicio');
      expect(iframe()!.getAttribute('src')).toBe(`${ORIGEN}/?vista-previa=linea-comarcal&editor=1`);
      expect(iframe()!.title).toBe('Vista previa editable de tu web');
    });

    it('nueva: «Tu web aún no está publicada…» con el enlace a Tema', () => {
      abrir(SIN_PUBLICAR);
      expect(texto('.estado-web')).toBe('Tu web aún no está publicada. Lo que guardes aquí saldrá cuando la publiques en Tema.');
      expect(q('.estado-web')!.classList).not.toContain('es-publicada');
      expect(q<HTMLAnchorElement>('.estado-web a')!.getAttribute('href')).toBe('/web-tema');
      expect(texto('.ayuda-editor')).toContain('Lo que guardes saldrá al publicar tu web.');
      expect(iframe()!.getAttribute('src')).toBe(`${ORIGEN}/?vista-previa=linea-comarcal&editor=1`);
    });

    it('el foco va al título al entrar', async () => {
      abrir();
      await fixture.whenStable();
      expect(document.activeElement).toBe(q('h1'));
    });
  });

  describe('Móvil | Escritorio y escala', () => {
    it('390 o 1200 px, escala si no cabe y el rótulo «… · al N %»', () => {
      abrir();
      fixture.componentInstance.cambiarVista('escritorio');
      fixture.componentInstance.disponible.set(744);
      fixture.detectChanges();
      expect(texto('.marco__info')).toBe('Escritorio, 1200 px · al 62 %');
      expect(iframe()!.style.width).toBe('1200px');
      expect(iframe()!.style.transform).toBe('scale(0.62)');
      expect(q('.lienzo')!.style.width).toBe('744px');
      expect(q('[data-vista="escritorio"]')!.getAttribute('aria-pressed')).toBe('true');

      q<HTMLButtonElement>('[data-vista="movil"]')!.click();
      fixture.detectChanges();
      expect(texto('.marco__info')).toBe('Móvil, 390 px');
      expect(iframe()!.style.width).toBe('390px');
      expect(iframe()!.style.transform).toBe('');
      expect(q('[data-vista="movil"]')!.getAttribute('aria-pressed')).toBe('true');
    });

    it('el alto del marco sigue al que manda la capa de edición', () => {
      abrir();
      fixture.componentInstance.cambiarVista('escritorio');
      fixture.componentInstance.disponible.set(600);
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'alto', alto: 3000 });
      expect(iframe()!.style.height).toBe('3000px');
      expect(q('.lienzo')!.style.height).toBe('1500px');
    });
  });

  describe('zona visible de la vista previa (los flotantes de la capa, dentro de lo que se ve)', () => {
    const LISTO = { canal: CANAL_EDITOR_WEB, tipo: 'listo' };
    let cabecera: HTMLElement | null = null;
    let enviados: jasmine.Spy;

    afterEach(() => {
      cabecera?.remove();
      cabecera = null;
      window.scrollTo(0, 0);
    });

    const fotograma = () => new Promise<void>((r) => requestAnimationFrame(() => r()));
    const zonas = () =>
      enviados.calls
        .allArgs()
        .map(([m]) => m as { tipo: string; top: number; alto: number })
        .filter((m) => m.tipo === 'zona-visible');
    /** Lo que se espera, en píxeles del documento del iframe: de `arriba` a `abajo` de la ventana, con la escala del marco. */
    function esperada(arriba: number, abajo: number): { top: number; alto: number } {
      const marco = iframe()!.getBoundingClientRect();
      const escala = marco.height / iframe()!.offsetHeight;
      return { top: (arriba - marco.top) / escala, alto: (abajo - arriba) / escala };
    }
    function comoEsperada(z: { top: number; alto: number }, e: { top: number; alto: number }): void {
      expect(Math.abs(z.top - e.top)).withContext(`top ${z.top} ≈ ${e.top}`).toBeLessThanOrEqual(1);
      expect(Math.abs(z.alto - e.alto)).withContext(`alto ${z.alto} ≈ ${e.alto}`).toBeLessThanOrEqual(1);
    }

    /** Escritorio a media escala, documento muy alto, la cabecera fija del panel y el marco empezando 500 px más arriba. */
    function preparar(): void {
      abrir();
      fixture.componentInstance.cambiarVista('escritorio');
      fixture.componentInstance.disponible.set(600);
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'alto', alto: 8000 });
      cabecera = document.createElement('div');
      cabecera.className = 'pn-cab';
      cabecera.style.cssText = 'position:fixed;top:0;left:0;right:0;height:64px;z-index:1';
      document.body.appendChild(cabecera);
      window.scrollTo(0, iframe()!.getBoundingClientRect().top + window.scrollY + 500);
      enviados = spyOn(iframe()!.contentWindow!, 'postMessage');
    }

    it('al «listo»: la parte del marco bajo la cabecera fija y hasta el borde de la ventana; con cambios, hasta la barra', async () => {
      preparar();
      llegar(LISTO);
      expect(zonas().length).toBe(1);
      expect(iframe()!.getBoundingClientRect().top).toBeLessThan(0);
      comoEsperada(zonas()[0], esperada(64, window.innerHeight));

      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave: 'portada.titular', texto: 'Taekwondo en siete pueblos' });
      const barra = q('.barra')!;
      expect(barra).not.toBeNull();
      await fotograma();
      expect(zonas().length).toBe(2);
      comoEsperada(zonas()[1], esperada(64, barra.getBoundingClientRect().top));
    });

    it('al desplazar o redimensionar: un envío por fotograma y ninguno si no cambia', async () => {
      preparar();
      llegar(LISTO);
      const antes = zonas()[0];
      window.scrollBy(0, 100);
      window.dispatchEvent(new Event('scroll'));
      window.dispatchEvent(new Event('scroll'));
      window.dispatchEvent(new Event('resize'));
      await fotograma();
      await fotograma();
      expect(zonas().length).toBe(2);
      comoEsperada(zonas()[1], esperada(64, window.innerHeight));
      expect(zonas()[1].top).toBeGreaterThan(antes.top);

      window.dispatchEvent(new Event('resize'));
      window.dispatchEvent(new Event('scroll'));
      await fotograma();
      expect(zonas().length).toBe(2);
    });

    it('al salir de la pantalla deja de escuchar el desplazamiento y el tamaño', () => {
      preparar();
      llegar(LISTO);
      fixture.destroy();
      const pedir = spyOn(window, 'requestAnimationFrame').and.callThrough();
      window.dispatchEvent(new Event('scroll'));
      window.dispatchEvent(new Event('resize'));
      expect(pedir).not.toHaveBeenCalled();
    });
  });

  describe('d551: teclado del móvil (visualViewport)', () => {
    const LISTO = { canal: CANAL_EDITOR_WEB, tipo: 'listo' };
    /** Lo justo de `VisualViewport`: posición, alto y sus eventos. */
    class VisorFalso extends EventTarget {
      offsetTop = 0;
      height = window.innerHeight;
      width = window.innerWidth;
      /** Sin definir, como un visor sin zoom. */
      scale?: number;
    }
    let falso: VisorFalso;
    let cabecera: HTMLElement | null = null;
    let enviados: jasmine.Spy;

    beforeEach(() => {
      falso = new VisorFalso();
      visor = falso as unknown as VisualViewport;
    });

    afterEach(() => {
      cabecera?.remove();
      cabecera = null;
      window.scrollTo(0, 0);
    });

    const fotograma = () => new Promise<void>((r) => requestAnimationFrame(() => r()));
    const esperar = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
    const zonas = () =>
      enviados.calls
        .allArgs()
        .map(([m]) => m as { tipo: string; top: number; alto: number; campo?: true })
        .filter((m) => m.tipo === 'zona-visible');
    function esperada(arriba: number, abajo: number): { top: number; alto: number } {
      const marco = iframe()!.getBoundingClientRect();
      const escala = marco.height / iframe()!.offsetHeight;
      return { top: (arriba - marco.top) / escala, alto: (abajo - arriba) / escala };
    }
    function comoEsperada(z: { top: number; alto: number }, e: { top: number; alto: number }): void {
      expect(Math.abs(z.top - e.top)).withContext(`top ${z.top} ≈ ${e.top}`).toBeLessThanOrEqual(1);
      expect(Math.abs(z.alto - e.alto)).withContext(`alto ${z.alto} ≈ ${e.alto}`).toBeLessThanOrEqual(1);
    }

    /** Como en la zona visible, pero con el panel estrecho (móvil). */
    function preparar(): void {
      abrir();
      fixture.componentInstance.estrecho.set(true);
      fixture.componentInstance.cambiarVista('escritorio');
      fixture.componentInstance.disponible.set(600);
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'alto', alto: 8000 });
      cabecera = document.createElement('div');
      cabecera.className = 'pn-cab';
      cabecera.style.cssText = 'position:fixed;top:0;left:0;right:0;height:64px;z-index:1';
      document.body.appendChild(cabecera);
      window.scrollTo(0, iframe()!.getBoundingClientRect().top + window.scrollY + 500);
      enviados = spyOn(iframe()!.contentWindow!, 'postMessage');
    }

    /** El teclado tapa `alto` px por abajo; `arriba`: el visor desplazado (offsetTop). */
    function teclado(alto: number, arriba = 0): void {
      falso.offsetTop = arriba;
      falso.height = window.innerHeight - arriba - alto;
      falso.dispatchEvent(new Event('resize'));
      fixture.detectChanges();
    }
    const cambiar = () => llegar({ canal: CANAL_EDITOR_WEB, tipo: 'texto', clave: 'portada.titular', texto: 'Taekwondo en siete pueblos' });

    it('teclado abierto sin barra: la zona acaba en el borde del teclado; con el visor movido, empieza en su offsetTop', async () => {
      preparar();
      llegar(LISTO);
      teclado(300);
      await fotograma();
      comoEsperada(zonas().at(-1)!, esperada(64, window.innerHeight - 300));
      teclado(300, 100);
      await fotograma();
      comoEsperada(zonas().at(-1)!, esperada(100, window.innerHeight - 300));
    });

    it('con cambios: la barra, en una línea pegada al teclado (Descartar, «1 cambio», Guardar) y la zona acaba en ella', async () => {
      preparar();
      llegar(LISTO);
      cambiar();
      teclado(300);
      await fotograma();
      const barra = q('.barra')!;
      expect(barra.classList).toContain('barra--teclado');
      expect(barra.classList).toContain('barra--compacta');
      expect(getComputedStyle(barra).bottom).toBe('300px');
      expect(Math.abs(barra.getBoundingClientRect().bottom - (window.innerHeight - 300))).toBeLessThanOrEqual(1);
      expect(barra.getBoundingClientRect().height).toBeLessThanOrEqual(56);
      expect(texto('.barra__texto strong')).toBe('1 cambio');
      // Para el lector de pantalla, el texto entero; sin «· Al guardar se publican en tu web.».
      expect(texto('.barra__texto')).toBe('1 cambio sin guardar');
      expect(barra.getAttribute('aria-label')).toBe('Cambios sin guardar');
      // Como la barra de escritorio: el contador a la izquierda, luego Descartar y Guardar, en la misma línea.
      const [descartar, guardar] = Array.from(barra.querySelectorAll('button')).map((b) => b.getBoundingClientRect());
      const frase = q('.barra__texto')!.getBoundingClientRect();
      expect(frase.right).toBeLessThanOrEqual(descartar.left);
      expect(descartar.right).toBeLessThanOrEqual(guardar.left);
      expect(Math.abs(descartar.top - guardar.top)).toBeLessThanOrEqual(1);
      expect(Math.abs(frase.top + frase.height / 2 - (descartar.top + descartar.height / 2))).toBeLessThanOrEqual(1);
      comoEsperada(zonas().at(-1)!, esperada(64, barra.getBoundingClientRect().top));

      // Teclado cerrado: la barra de siempre.
      teclado(0);
      expect(barra.classList).not.toContain('barra--teclado');
      expect(barra.classList).not.toContain('barra--compacta');
      expect(barra.style.getPropertyValue('--alto-teclado')).toBe('');
      expect(texto('.barra__texto')).toBe('1 cambio sin guardar · Al guardar se publican en tu web.');
    });

    it('a 360 px con «12 cambios»: la barra compacta no desborda y los botones conservan su ancho', () => {
      preparar();
      cambiar();
      teclado(300);
      const barra = q('.barra')!;
      const botones = Array.from(barra.querySelectorAll('button'));
      const naturales = botones.map((b) => b.getBoundingClientRect().width);
      barra.style.right = 'auto';
      barra.style.width = '360px';
      // El contador más largo que cabe en la práctica (el número lo da `textoCambiosCorto`).
      q('.barra__frase strong')!.textContent = '12 cambios';
      expect(barra.getBoundingClientRect().width).toBe(360);
      expect(barra.scrollWidth).toBeLessThanOrEqual(barra.clientWidth);
      expect(barra.getBoundingClientRect().height).toBeLessThanOrEqual(56);
      botones.forEach((b, i) => {
        expect(Math.abs(b.getBoundingClientRect().width - naturales[i])).toBeLessThanOrEqual(0.5);
        expect(b.getBoundingClientRect().right).toBeLessThanOrEqual(barra.getBoundingClientRect().right);
      });
      const frase = q('.barra__frase')!;
      expect(frase.getBoundingClientRect().right).toBeLessThanOrEqual(botones[0].getBoundingClientRect().left);
      // Si no cabe, se recorta el texto (nunca los botones).
      q('.barra__frase strong')!.textContent = '12 cambios y un texto mucho más largo que no cabe';
      expect(barra.scrollWidth).toBeLessThanOrEqual(barra.clientWidth);
      expect(frase.scrollWidth).toBeGreaterThan(frase.clientWidth);
      botones.forEach((b, i) => expect(Math.abs(b.getBoundingClientRect().width - naturales[i])).toBeLessThanOrEqual(0.5));
    });

    it('panel ancho (tableta o escritorio): aunque el visor encoja, la barra no cambia', () => {
      preparar();
      fixture.componentInstance.estrecho.set(false);
      cambiar();
      teclado(300);
      expect(q('.barra')!.classList).not.toContain('barra--teclado');
      expect(fixture.componentInstance.teclado()).toBeFalse();
    });

    it('un visor que encoge poco (barra de direcciones) no es el teclado', () => {
      preparar();
      cambiar();
      teclado(100);
      expect(fixture.componentInstance.teclado()).toBeFalse();
      expect(q('.barra')!.classList).not.toContain('barra--compacta');
    });

    it('conflicto (409): sube sobre el teclado con su forma de siempre', () => {
      preparar();
      cambiar();
      fixture.componentInstance.conflicto.set(true);
      teclado(300);
      const barra = q('.barra')!;
      expect(barra.classList).toContain('barra--teclado');
      expect(barra.classList).not.toContain('barra--compacta');
      expect(getComputedStyle(barra).bottom).toBe('300px');
      expect(getComputedStyle(barra).zIndex).toBe('1002');
      expect(texto('.barra__texto')).toContain('Otra persona cambió la web antes.');
    });

    it('al abrirse el teclado pide a la capa el texto en edición; con su respuesta, lo lleva bajo la cabecera', async () => {
      preparar();
      llegar(LISTO);
      expect(zonas()[0].campo).toBeUndefined();
      teclado(300);
      // Se pide cuando el teclado se asienta (sin cambios de alto un rato).
      await esperar(200);
      await fotograma();
      expect(zonas().at(-1)!.campo).toBeTrue();
      // Desplazar la ventana no lo vuelve a pedir: no se pelea con quien se desplaza.
      window.scrollBy(0, 100);
      window.dispatchEvent(new Event('scroll'));
      await fotograma();
      expect(zonas().at(-1)!.campo).toBeUndefined();

      const ir = spyOn(window, 'scrollTo');
      const escala = fixture.componentInstance.escala();
      const y = iframe()!.getBoundingClientRect().top + window.scrollY + 3000 * escala;
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: 3000, campo: true });
      expect(ir).toHaveBeenCalledTimes(1);
      const destino = (ir.calls.mostRecent().args[0] as ScrollToOptions).top!;
      expect(Math.abs(destino - (y - 64 - 16))).toBeLessThanOrEqual(1);

      // Con el teclado ya cerrado, una respuesta tardía no mueve nada.
      teclado(0);
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: 3000, campo: true });
      expect(ir).toHaveBeenCalledTimes(1);
    });

    it('un «mostrar» sin pedir (p. ej. al añadir un profesor) sigue sin mover la ventana', () => {
      preparar();
      llegar(LISTO);
      teclado(300);
      const ir = spyOn(window, 'scrollTo');
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: 3000 });
      expect(ir).not.toHaveBeenCalled();
    });

    it('zoom de pellizco (scale > 1): el visor no cuenta; ni teclado ni campo, y la zona como sin visor', async () => {
      preparar();
      llegar(LISTO);
      cambiar();
      // Con zoom ×2 el visor mide la mitad de la ventana y está desplazado: no es el teclado.
      falso.scale = 2;
      falso.offsetTop = 200;
      falso.height = window.innerHeight / 2;
      falso.dispatchEvent(new Event('resize'));
      fixture.detectChanges();
      await fotograma();
      await esperar(200);
      await fotograma();
      expect(fixture.componentInstance.tapadoAbajo()).toBe(0);
      expect(fixture.componentInstance.teclado()).toBeFalse();
      const barra = q('.barra')!;
      expect(barra.classList).not.toContain('barra--teclado');
      expect(barra.classList).not.toContain('barra--compacta');
      expect(barra.style.getPropertyValue('--alto-teclado')).toBe('');
      expect(texto('.barra__texto')).toBe('1 cambio sin guardar · Al guardar se publican en tu web.');
      // Como en develop: de la cabecera a la barra, en la ventana entera (ni offsetTop ni alto del visor).
      comoEsperada(zonas().at(-1)!, esperada(64, Math.min(window.innerHeight, barra.getBoundingClientRect().top)));
      expect(zonas().some((z) => z.campo)).toBeFalse();
    });

    it('con el visor movido (offsetTop 100, teclado 300), la barra se pega igual al teclado', () => {
      preparar();
      cambiar();
      teclado(300, 100);
      expect(fixture.componentInstance.tapadoAbajo()).toBe(300);
      expect(getComputedStyle(q('.barra')!).bottom).toBe('300px');
    });

    it('teclado abierto con el visor desplazado (tapado 0 o 44): sigue siendo el teclado y la barra, pegada a él y sobre la navegación', () => {
      preparar();
      cambiar();
      const sinTeclado = getComputedStyle(q('.barra')!);
      const deSiempre = { zIndex: sinTeclado.zIndex, bottom: sinTeclado.bottom };
      // Teclado de 300 px; Chrome mueve el visor 300 px para enseñar el campo: no tapa nada de la ventana.
      falso.offsetTop = 300;
      falso.height = window.innerHeight - 300;
      falso.dispatchEvent(new Event('resize'));
      fixture.detectChanges();
      expect(fixture.componentInstance.tapadoAbajo()).toBe(0);
      expect(fixture.componentInstance.altoTeclado()).toBe(300);
      expect(fixture.componentInstance.teclado()).toBeTrue();
      const barra = q('.barra')!;
      expect(barra.classList).toContain('barra--teclado');
      expect(barra.classList).toContain('barra--compacta');
      expect(getComputedStyle(barra).bottom).toBe('0px');
      // Por encima de la navegación inferior (`.pn-barra`, 1001) y por debajo de las hojas inferiores (1020).
      const capa = Number(getComputedStyle(barra).zIndex);
      expect(capa).toBeGreaterThan(1001);
      expect(capa).toBeLessThan(1020);
      // Visor casi abajo: tapa 44 px.
      falso.offsetTop = 256;
      falso.dispatchEvent(new Event('scroll'));
      fixture.detectChanges();
      expect(fixture.componentInstance.tapadoAbajo()).toBe(44);
      expect(getComputedStyle(barra).bottom).toBe('44px');
      // Teclado cerrado: z-index y bottom de siempre.
      teclado(0);
      expect(barra.classList).not.toContain('barra--teclado');
      expect(getComputedStyle(barra).zIndex).toBe(deSiempre.zIndex);
      expect(getComputedStyle(barra).bottom).toBe(deSiempre.bottom);
      expect(Number(deSiempre.zIndex)).toBeLessThan(1001);
    });

    it('el visor se desplaza (offsetTop 0 → 300) con el mismo teclado: sigue compacta y no se vuelve a pedir el campo', fakeAsync(() => {
      preparar();
      llegar(LISTO);
      cambiar();
      teclado(300);
      tick(500);
      const conCampo = () => zonas().filter((z) => z.campo).length;
      expect(conCampo()).toBe(1);
      const barra = q('.barra')!;
      expect(getComputedStyle(barra).bottom).toBe('300px');
      falso.offsetTop = 300;
      falso.dispatchEvent(new Event('scroll'));
      falso.dispatchEvent(new Event('resize'));
      fixture.detectChanges();
      tick(500);
      expect(fixture.componentInstance.teclado()).toBeTrue();
      expect(barra.classList).toContain('barra--compacta');
      expect(getComputedStyle(barra).bottom).toBe('0px');
      expect(conCampo()).toBe(1);
      flush();
    }));

    it('campo: una vez al asentarse el teclado y otra si cambia de alto; nunca solo por mover el visor o desplazar', fakeAsync(() => {
      preparar();
      llegar(LISTO);
      const conCampo = () => zonas().filter((z) => z.campo).length;
      teclado(300);
      tick(16);
      // La zona normal va al momento; el campo, aún no.
      expect(zonas().length).toBeGreaterThan(0);
      expect(conCampo()).toBe(0);
      tick(100);
      // Sigue abriéndose: la espera vuelve a empezar.
      teclado(320);
      tick(140);
      expect(conCampo()).toBe(0);
      tick(50);
      expect(conCampo()).toBe(1);

      // Mover el visor (mismo alto, otro offsetTop) o desplazar la ventana no lo pide.
      falso.offsetTop = 80;
      falso.dispatchEvent(new Event('scroll'));
      falso.dispatchEvent(new Event('resize'));
      fixture.detectChanges();
      tick(500);
      expect(fixture.componentInstance.teclado()).toBeTrue();
      window.scrollBy(0, 100);
      window.dispatchEvent(new Event('scroll'));
      tick(500);
      expect(conCampo()).toBe(1);

      // Cambia el alto con el teclado abierto: otra vez (la ventana de Karma mide poco: alturas que dejan zona).
      teclado(250);
      tick(500);
      expect(conCampo()).toBe(2);

      // Varios cambios seguidos: un solo envío al acabar.
      teclado(230);
      tick(50);
      teclado(240);
      tick(50);
      teclado(260);
      tick(500);
      expect(conCampo()).toBe(3);

      // Si se cierra antes de asentarse, nada.
      teclado(0);
      tick(500);
      teclado(300);
      tick(50);
      teclado(0);
      tick(500);
      expect(conCampo()).toBe(3);

      // Tras salir, nada.
      teclado(300);
      tick(50);
      fixture.destroy();
      tick(500);
      expect(conCampo()).toBe(3);
      flush();
    }));

    for (const [reducir, comportamiento] of [
      [true, 'auto'],
      [false, 'smooth'],
    ] as const) {
      it(`«mostrar» con campo: scrollTo '${comportamiento}' ${reducir ? 'con' : 'sin'} reduced-motion, aunque ya esté dentro del límite`, () => {
        preparar();
        llegar(LISTO);
        teclado(200);
        const original = window.matchMedia.bind(window);
        spyOn(window, 'matchMedia').and.callFake((c: string) =>
          c.includes('prefers-reduced-motion') ? ({ matches: reducir } as MediaQueryList) : original(c),
        );
        const ir = spyOn(window, 'scrollTo');
        const escala = fixture.componentInstance.escala();
        const marcoTop = iframe()!.getBoundingClientRect().top;
        // A 100 px de arriba de la ventana: bajo la cabecera (64 + 16) y por encima del límite (teclado + margen).
        expect(100).toBeLessThan(window.innerHeight - 200 - 56);
        const arriba = Math.round((100 - marcoTop) / escala);
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba, campo: true });
        expect(ir).toHaveBeenCalledTimes(1);
        const opciones = ir.calls.mostRecent().args[0] as ScrollToOptions;
        expect(opciones.behavior).toBe(comportamiento);
        expect(Math.abs(opciones.top! - (window.scrollY + marcoTop + arriba * escala - 64 - 16))).toBeLessThanOrEqual(1);
        // Con el teclado cerrado, se ignora.
        teclado(0);
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba, campo: true });
        expect(ir).toHaveBeenCalledTimes(1);
      });
    }

    it('subiendo una foto con el teclado: barra compacta y la frase en una línea con puntos suspensivos', () => {
      preparar();
      fixture.componentInstance.subidas.set({ 'portada.foto': { estado: 'subiendo', pct: 10, nombre: 'foto.jpg' } } as never);
      teclado(300);
      const barra = q('.barra')!;
      expect(barra.classList).toContain('barra--compacta');
      expect(getComputedStyle(barra).bottom).toBe('300px');
      expect(texto('.barra__frase')).toBe('Se está subiendo una foto. Podrás guardar cuando termine.');
      const estilo = getComputedStyle(q('.barra__frase')!);
      expect(estilo.whiteSpace).toBe('nowrap');
      expect(estilo.textOverflow).toBe('ellipsis');
      expect(estilo.overflow).toBe('hidden');
    });

    it('al salir deja de escuchar el visor y cancela el fotograma pendiente', () => {
      preparar();
      llegar(LISTO);
      const quitar = spyOn(falso, 'removeEventListener').and.callThrough();
      const cancelar = spyOn(window, 'cancelAnimationFrame').and.callThrough();
      // Un cambio del visor deja un fotograma pedido.
      falso.height -= 300;
      falso.dispatchEvent(new Event('resize'));
      fixture.destroy();
      expect(quitar.calls.allArgs().map(([tipo]) => tipo).sort()).toEqual(['resize', 'scroll']);
      expect(cancelar).toHaveBeenCalled();
      const pedir = spyOn(window, 'requestAnimationFrame').and.callThrough();
      falso.dispatchEvent(new Event('resize'));
      falso.dispatchEvent(new Event('scroll'));
      expect(pedir).not.toHaveBeenCalled();
    });
  });

  describe('carga de la vista previa', () => {
    const LISTO = { canal: CANAL_EDITOR_WEB, tipo: 'listo' };

    it('esqueleto solo pasados 300 ms; el «listo» de la capa la deja lista y ya no cae en error', fakeAsync(() => {
      abrir();
      expect(q('.lienzo')!.classList).not.toContain('es-cargando');
      tick(299);
      fixture.detectChanges();
      expect(q('.lienzo')!.classList).not.toContain('es-cargando');
      expect(anuncio()).toBe('');
      tick(1);
      fixture.detectChanges();
      expect(q('.lienzo')!.classList).toContain('es-cargando');
      expect(anuncio()).toBe('Cargando la vista previa…');

      llegar(LISTO);
      expect(q('.lienzo')!.classList).not.toContain('es-cargando');
      expect(anuncio()).toBe('');
      tick(15000);
      fixture.detectChanges();
      expect(q('.fallo')).toBeNull();
      expect(iframe()).not.toBeNull();
    }));

    it('sin el «listo» de la capa (la API no confirma la vista previa), aunque el iframe cargue: error por tiempo', fakeAsync(() => {
      abrir();
      iframe()!.dispatchEvent(new Event('load'));
      fixture.detectChanges();
      tick(14999);
      fixture.detectChanges();
      expect(q('.fallo')).toBeNull();
      tick(1);
      fixture.detectChanges();
      expect(iframe()).toBeNull();
      const fallo = q('.fallo')!;
      expect(fallo.getAttribute('role')).toBe('alert');
      expect(texto('.fallo h2')).toBe('No se pudo cargar la vista previa');
      expect(texto('.fallo p')).toBe('Tu web publicada no cambia. Vuelve a intentarlo en unos segundos.');
      expect(texto('.fallo button')).toBe('Reintentar');
      expect(anuncio()).toBe('');
    }));

    it('«Reintentar» de la vista previa: vuelve a cargar el iframe al alto inicial y lleva el foco al marco', fakeAsync(() => {
      abrir();
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'alto', alto: 3000 });
      tick(15000);
      fixture.detectChanges();
      q<HTMLButtonElement>('.fallo button')!.click();
      fixture.detectChanges();
      expect(q('.fallo')).toBeNull();
      expect(iframe()).not.toBeNull();
      expect(iframe()!.style.height).toBe('800px');
      expect(document.activeElement).toBe(q('.marco__vista'));
      tick(300);
      fixture.detectChanges();
      expect(q('.lienzo')!.classList).toContain('es-cargando');
      llegar(LISTO);
      expect(q('.lienzo')!.classList).not.toContain('es-cargando');
      tick(15000);
      fixture.detectChanges();
      expect(q('.fallo')).toBeNull();
      flush();
    }));
  });

  describe('puente', () => {
    it('con la capa lista manda el borrador al iframe, al propio origen y sin constancias de permiso', () => {
      abrir();
      const enviar = spyOn(iframe()!.contentWindow!, 'postMessage');
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
      // W12e: con el panel estrecho la capa nueva recibe también `estado-bloques`; W12f-2b, `profesores`; y la parte que se
      // ve (`zona-visible`): aquí no cuentan.
      const llamadas = enviar.calls
        .allArgs()
        .filter(([m]) => !['estado-bloques', 'profesores', 'zona-visible'].includes((m as { tipo: string }).tipo));
      expect(llamadas.length).toBe(1);
      const [mensaje, destino] = llamadas[0] as unknown as [Record<string, unknown>, string];
      expect(destino).toBe(location.origin);
      expect(destino).not.toBe('*');
      expect(mensaje['canal']).toBe(CANAL_EDITOR_WEB);
      expect(mensaje['tipo']).toBe('borrador');
      expect(mensaje['bloques']).toEqual([
        { clave: 'portada.titular', texto: 'Taekwondo en seis pueblos', imagenUrl: null, orden: 0, oculto: false },
        { clave: 'profesor.1.nombre', texto: 'Ana', imagenUrl: null, orden: 0, oculto: false },
      ]);
    });

    it('ignora mensajes de otro origen, de otra ventana o con forma desconocida', () => {
      abrir();
      const enviar = spyOn(iframe()!.contentWindow!, 'postMessage');
      const listo = { canal: CANAL_EDITOR_WEB, tipo: 'listo' };
      const ir = { canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: 'sedes' };
      llegar(listo, 'https://otra.example');
      llegar(ir, 'https://otra.example');
      llegar(listo, location.origin, window);
      llegar(ir, location.origin, window);
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: '/plataformaErrores' });
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'abrir', url: 'https://otra.example' });
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'alto', alto: 'mucho' });
      llegar('listo');
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'lleno' });
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: 'arriba' });
      expect(enviar).not.toHaveBeenCalled();
      expect(navegar).not.toHaveBeenCalled();
      expect(iframe()!.style.height).toBe('800px');
    });

    for (const [destino, ruta] of [
      ['sedes', '/sedes'],
      ['grupos', '/gruposListar'],
      ['eventos', '/eventosListar'],
      ['productos', '/productosListar'],
      ['datos-escuela', '/datos-escuela'],
      ['plantillas', '/preinscripciones?tab=plantillas'],
    ] as const) {
      it(`un candado de ${destino} lleva a ${ruta}`, () => {
        abrir();
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'ir', destino });
        expect(navegar).toHaveBeenCalledOnceWith(ruta);
      });
    }

    it('deja de escuchar al salir de la pantalla', () => {
      abrir();
      const contenido = iframe()!.contentWindow!;
      fixture.destroy();
      window.dispatchEvent(new MessageEvent('message', { data: { canal: CANAL_EDITOR_WEB, tipo: 'ir', destino: 'sedes' }, origin: location.origin, source: contenido }));
      expect(navegar).not.toHaveBeenCalled();
    });
  });
});
