import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { CANAL_EDITOR_WEB } from '../../../web-plantilla/editor/puente-editor';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import { ContenidoWeb, ORIGEN_EDITOR } from './web-contenido-vista';
import { WebContenidoComponent } from './web-contenido.component';

const URL = `${environment.apiUrl}/escuela/web`;
const URL_CONTENIDO = `${URL}/contenido`;
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

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: ORIGEN_EDITOR, useValue: ORIGEN }],
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

  function abrir(estado: EstadoWebEscuela = estadoWeb(), contenido: ContenidoWeb = CONTENIDO): void {
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(estado);
    http.expectOne(URL_CONTENIDO).flush(contenido);
    fixture.detectChanges();
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
      q<HTMLButtonElement>('.pn-bloque-error__reintentar')!.click();
      http.expectOne(URL).flush(estadoWeb());
      http.expectOne(URL_CONTENIDO).flush(CONTENIDO);
      fixture.detectChanges();
      expect(iframe()).not.toBeNull();
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

  describe('puente', () => {
    it('con la capa lista manda el borrador al iframe, al propio origen y sin constancias de permiso', () => {
      abrir();
      const enviar = spyOn(iframe()!.contentWindow!, 'postMessage');
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
      expect(enviar).toHaveBeenCalledTimes(1);
      const [mensaje, destino] = enviar.calls.mostRecent().args as unknown as [Record<string, unknown>, string];
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
