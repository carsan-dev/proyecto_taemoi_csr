import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { AvisosService } from '../../avisos/avisos.service';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { OpcionesConfirmacion, ResultadoConfirmacion } from '../../dialogo/dialogo.component';
import { MarcaEscuelaPanelService } from '../../marca-escuela-panel.service';
import { EstadoWebEscuela } from './web-tema-vista';
import { ORIGEN_VISTA_PREVIA, WebTemaComponent } from './web-tema.component';

const API = environment.apiUrl;
const URL = `${API}/escuela/web`;
const URL_PUBLICAR = `${API}/escuela/web/publicacion`;
const URL_SITIO = `${API}/publico/web`;
/** El iframe de las pruebas no carga la página de Karma: un documento vacío (siempre asíncrono) con la ruta en el fragmento. */
const ORIGEN = 'data:text/html,vista#';

function estadoWeb(parcial: Partial<EstadoWebEscuela> = {}): EstadoWebEscuela {
  return {
    tema: 'linea-comarcal',
    publicada: true,
    publicadaEn: '2026-11-01T10:00:00Z',
    temasDisponibles: ['linea-comarcal', 'chandal', 'sede-por-pantalla', 'estandar'],
    ...parcial,
  };
}

const SIN_PUBLICAR = estadoWeb({ tema: null, publicada: false, publicadaEn: null });

describe('WebTemaComponent (W7)', () => {
  let fixture: ComponentFixture<WebTemaComponent>;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;
  let confirmar: jasmine.Spy<(o: OpcionesConfirmacion<EstadoWebEscuela>) => Promise<ResultadoConfirmacion<EstadoWebEscuela>>>;

  /** Confirma y ejecuta `ejecutar`, como el diálogo real: resuelve con su respuesta o su error. */
  const confirmarYEjecutar = (o: OpcionesConfirmacion<EstadoWebEscuela>) =>
    new Promise<ResultadoConfirmacion<EstadoWebEscuela>>((resolver) =>
      o.ejecutar!(null).subscribe({
        next: (respuesta) => resolver({ confirmado: true, valor: null, respuesta }),
        error: (error: unknown) => resolver({ confirmado: true, valor: null, error }),
      })
    );

  beforeEach(() => {
    avisos = jasmine.createSpyObj('Avisos', ['exito', 'error', 'info']);
    confirmar = jasmine.createSpy('confirmar');
    confirmar.and.resolveTo({ confirmado: false, valor: null });
    const marca = {
      nombre: 'Club de Ejemplo',
      logo: 'NINGUNO',
      logoVersion: null,
      tema: { primario: '#0b5cad', sobrePrimario: '#fff', sobrePrimario2: '#c4d8eb', primarioTinta: '#0b5cad', primarioSuave: '#e7eff7', ajustado: false },
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AvisosService, useValue: avisos },
        { provide: ConfirmacionService, useValue: { confirmar } },
        { provide: MarcaEscuelaPanelService, useValue: { marca: () => of(marca), urlLogo: () => null } },
        { provide: ORIGEN_VISTA_PREVIA, useValue: ORIGEN },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement = HTMLElement>(selector: string) => el().querySelector<T>(selector);
  const texto = (selector: string) => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const tarjeta = (tema: string) => q(`[data-tema="${tema}"]`)!;
  const iframe = () => q<HTMLIFrameElement>('iframe');
  const barra = () => q('.barra');
  const anuncio = () => texto('.vista [role="status"]');
  const tecla = (destino: HTMLElement, key: string) => {
    destino.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    fixture.detectChanges();
  };

  /** Abre la pantalla con el estado dado y la respuesta de la vista previa pública (sus sedes). */
  function abrir(estado: EstadoWebEscuela = estadoWeb(), sedes: { slug: string | null }[] | null = [{ slug: 'casariche' }]): void {
    fixture = TestBed.createComponent(WebTemaComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(estado);
    fixture.detectChanges();
    const sitio = http.expectOne((r) => r.url === URL_SITIO);
    if (sedes === null) {
      sitio.flush(null, { status: 500, statusText: 'Error' });
    } else {
      sitio.flush({ sedes });
    }
    fixture.detectChanges();
  }

  function cambiarPagina(valor: string): void {
    const selector = q<HTMLSelectElement>('#web-tema-pagina')!;
    selector.value = valor;
    selector.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  const src = () => iframe()!.getAttribute('src')!.replace(ORIGEN, '');

  describe('carga', () => {
    it('publicada: migas, título, estado con el tema, una tarjeta elegida con «Publicado» y sin barra', () => {
      abrir();
      expect(texto('.migas')).toBe('Web/');
      expect(texto('h1')).toBe('Tema');
      expect(texto('.estado-web')).toBe('Tu web está publicada con “La línea comarcal”.');
      expect(q('.estado-web')!.classList).toContain('es-publicada');
      expect(el().querySelectorAll('[role="radio"][data-tema]').length).toBe(1);
      const t = tarjeta('linea-comarcal');
      expect(t.getAttribute('aria-checked')).toBe('true');
      expect(t.getAttribute('tabindex')).toBe('0');
      expect(t.getAttribute('aria-describedby')).toBe('d-linea-comarcal p-linea-comarcal');
      expect(texto('.tema__nombre')).toBe('La línea comarcal');
      expect(texto('.tema__desc')).toBe('Las sedes como paradas de una línea, con sus horarios.');
      expect(texto('.publicado')).toBe('Publicado');
      expect(q('.mini.mini--linea')?.getAttribute('aria-hidden')).toBe('true');
      expect(barra()).toBeNull();
      expect(texto('#t-vista')).toBe('Así se verá tu web con tus sedes, horarios, logo y color');
      expect(texto('.marca-ro')).toBe('Logo y color: los cambia el equipo de Taemoi');
    });

    it('error al leer: bloque de error con reintentar, que vuelve a pedir', () => {
      fixture = TestBed.createComponent(WebTemaComponent);
      fixture.detectChanges();
      http.expectOne(URL).flush(null, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      expect(texto('pn-bloque-error')).toContain('No se ha podido leer el estado de tu web.');
      expect(q('.rejilla')).toBeNull();
      q<HTMLButtonElement>('pn-bloque-error button')!.click();
      fixture.detectChanges();
      http.expectOne(URL).flush(estadoWeb());
      fixture.detectChanges();
      http.expectOne((r) => r.url === URL_SITIO).flush({ sedes: [] });
      fixture.detectChanges();
      expect(q('pn-bloque-error')).toBeNull();
      expect(tarjeta('linea-comarcal')).not.toBeNull();
    });

    it('primera vez: elegido el primero construido, barra sin «Descartar» y con «Publicar la web con este tema»', () => {
      abrir(SIN_PUBLICAR);
      expect(texto('.estado-web')).toBe('Tu web aún no está publicada. Elige un tema, revísalo y publícalo.');
      expect(tarjeta('linea-comarcal').getAttribute('aria-checked')).toBe('true');
      expect(q('.publicado')).toBeNull();
      expect(barra()!.getAttribute('role')).toBe('region');
      expect(barra()!.getAttribute('aria-label')).toBe('Publicar la web');
      expect(texto('.barra__texto')).toBe('Tu web aún no está publicada. Has elegido “La línea comarcal”.');
      expect(Array.from(barra()!.querySelectorAll('button')).map((b) => b.textContent!.trim())).toEqual(['Publicar la web con este tema']);
    });
  });

  describe('elegir tema', () => {
    it('con un tema publicado no construido se ven los dos; flechas, Inicio y Fin mueven la elección y el foco', () => {
      abrir(estadoWeb({ tema: 'estandar' }));
      expect(Array.from(el().querySelectorAll('[data-tema]')).map((t) => t.getAttribute('data-tema'))).toEqual(['linea-comarcal', 'estandar']);
      const estandar = tarjeta('estandar');
      expect(estandar.getAttribute('aria-checked')).toBe('true');
      estandar.focus();
      tecla(estandar, 'ArrowDown');
      expect(tarjeta('linea-comarcal').getAttribute('aria-checked')).toBe('true');
      expect(tarjeta('linea-comarcal').getAttribute('tabindex')).toBe('0');
      expect(estandar.getAttribute('aria-checked')).toBe('false');
      expect(estandar.getAttribute('tabindex')).toBe('-1');
      expect(document.activeElement).toBe(tarjeta('linea-comarcal'));
      tecla(tarjeta('linea-comarcal'), 'End');
      expect(document.activeElement).toBe(estandar);
      tecla(estandar, 'Home');
      expect(document.activeElement).toBe(tarjeta('linea-comarcal'));
      tecla(tarjeta('linea-comarcal'), 'ArrowUp');
      expect(estandar.getAttribute('aria-checked')).toBe('true');
    });

    it('cambios: la barra dice qué se eligió y con qué sigue la web; «Descartar» vuelve al publicado', () => {
      abrir(estadoWeb({ tema: 'estandar' }));
      tarjeta('linea-comarcal').click();
      fixture.detectChanges();
      expect(texto('.barra__texto')).toBe('Has elegido “La línea comarcal”. Tu web sigue con “El estándar del sector” hasta que publiques.');
      const botones = Array.from(barra()!.querySelectorAll('button')).map((b) => b.textContent!.trim());
      expect(botones).toEqual(['Descartar', 'Publicar con este tema']);
      barra()!.querySelector<HTMLButtonElement>('.secundario')!.click();
      fixture.detectChanges();
      expect(barra()).toBeNull();
      expect(tarjeta('estandar').getAttribute('aria-checked')).toBe('true');
      expect(document.activeElement).toBe(tarjeta('estandar'));
    });
  });

  describe('vista previa', () => {
    it('src y title del iframe al cambiar tema, página y dispositivo; sin sandbox', () => {
      abrir(estadoWeb({ tema: 'estandar' }));
      expect(src()).toBe('/?vista-previa=estandar');
      expect(iframe()!.hasAttribute('sandbox')).toBeFalse();
      q<HTMLButtonElement>('[data-dispositivo="movil"]')!.click();
      fixture.detectChanges();
      expect(iframe()!.title).toBe('Vista previa de tu web: El estándar del sector, portada, móvil');
      expect(q('.marco')!.classList).toContain('marco--movil');
      cambiarPagina('tarifas');
      expect(src()).toBe('/tarifas?vista-previa=estandar');
      cambiarPagina('sede');
      expect(src()).toBe('/sedes/casariche?vista-previa=estandar');
      tarjeta('linea-comarcal').click();
      fixture.detectChanges();
      expect(src()).toBe('/sedes/casariche?vista-previa=linea-comarcal');
      const escritorio = q<HTMLButtonElement>('[data-dispositivo="escritorio"]')!;
      escritorio.click();
      fixture.detectChanges();
      expect(escritorio.getAttribute('aria-checked')).toBe('true');
      expect(q('.marco')!.classList).toContain('marco--escritorio');
      expect(iframe()!.title).toBe('Vista previa de tu web: La línea comarcal, una sede, escritorio');
      tecla(escritorio, 'ArrowLeft');
      expect(q('[data-dispositivo="movil"]')!.getAttribute('aria-checked')).toBe('true');
      expect(document.activeElement).toBe(q('[data-dispositivo="movil"]'));
    });

    it('las seis páginas del selector; sin sedes (o si falla), «Una sede» deshabilitada', () => {
      abrir(estadoWeb(), null);
      const opciones = Array.from(el().querySelectorAll<HTMLOptionElement>('#web-tema-pagina option'));
      expect(opciones.map((o) => o.textContent!.trim())).toEqual(['Portada', 'Una sede', 'Preinscripción', 'Tarifas', 'Eventos', 'Legales']);
      expect(opciones[1].disabled).toBeTrue();
      cambiarPagina('legales');
      expect(src()).toBe('/legales?vista-previa=linea-comarcal');
    });

    it('cargando: esqueleto solo pasados 300 ms; «load» la deja lista; sin «load» en 15 s, error con reintentar', fakeAsync(() => {
      abrir();
      expect(q('.marco')!.classList).not.toContain('es-cargando');
      tick(299);
      fixture.detectChanges();
      expect(q('.marco')!.classList).not.toContain('es-cargando');
      expect(anuncio()).toBe('');
      tick(1);
      fixture.detectChanges();
      expect(q('.marco')!.classList).toContain('es-cargando');
      expect(anuncio()).toBe('Cargando la vista previa…');

      iframe()!.dispatchEvent(new Event('load'));
      fixture.detectChanges();
      expect(q('.marco')!.classList).not.toContain('es-cargando');
      expect(anuncio()).toBe('Vista previa: La línea comarcal, portada.');

      cambiarPagina('eventos');
      tick(15000);
      fixture.detectChanges();
      expect(iframe()).toBeNull();
      const fallo = q('.fallo')!;
      expect(fallo.getAttribute('role')).toBe('alert');
      expect(texto('.fallo h3')).toBe('No se pudo cargar la vista previa');
      expect(texto('.fallo p')).toBe('Tu web publicada no cambia. Vuelve a intentarlo en unos segundos.');

      fallo.querySelector<HTMLButtonElement>('button')!.click();
      fixture.detectChanges();
      expect(q('.fallo')).toBeNull();
      expect(src()).toBe('/eventos?vista-previa=linea-comarcal');
      expect(document.activeElement).toBe(q('.escenario'));
      iframe()!.dispatchEvent(new Event('load'));
      fixture.detectChanges();
      expect(anuncio()).toBe('Vista previa: La línea comarcal, eventos.');
      tick(15000);
      fixture.detectChanges();
      expect(q('.fallo')).toBeNull();
      flush();
    }));
  });

  describe('publicar', () => {
    it('primera vez: diálogo «¿Publicar tu web?», PUT con {tema}, aviso de éxito, sin barra y foco en la tarjeta', async () => {
      confirmar.and.callFake(confirmarYEjecutar);
      abrir(SIN_PUBLICAR);
      barra()!.querySelector<HTMLButtonElement>('.primario')!.click();
      const opciones = confirmar.calls.mostRecent().args[0];
      expect(opciones.titulo).toBe('¿Publicar tu web?');
      expect(opciones.mensaje).toEqual(['Las familias verán tu web con el tema ', { fuerte: '“La línea comarcal”' }, ' desde ahora.']);
      expect(opciones.bloques).toEqual([{ tipo: 'parrafo', partes: ['Hasta ahora tu web no estaba publicada.'] }]);
      expect(opciones.accion).toBe('Publicar');
      expect(opciones.accionOcupada).toBe('Publicando…');
      expect(opciones.cancelar).toBe('Cancelar');
      expect(opciones.destructivo).toBeFalsy();
      const peticion = http.expectOne(URL_PUBLICAR);
      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.body).toEqual({ tema: 'linea-comarcal' });
      peticion.flush(estadoWeb());
      await fixture.whenStable();
      fixture.detectChanges();
      expect(avisos.exito).toHaveBeenCalledWith('Tu web ya se ve con “La línea comarcal”.');
      expect(barra()).toBeNull();
      expect(texto('.estado-web')).toBe('Tu web está publicada con “La línea comarcal”.');
      expect(texto('.publicado')).toBe('Publicado');
      expect(document.activeElement).toBe(tarjeta('linea-comarcal'));
    });

    it('con la web publicada: el diálogo nombra el tema nuevo y el actual', async () => {
      confirmar.and.callFake(confirmarYEjecutar);
      abrir(estadoWeb({ tema: 'estandar' }));
      tarjeta('linea-comarcal').click();
      fixture.detectChanges();
      barra()!.querySelector<HTMLButtonElement>('.primario')!.click();
      const opciones = confirmar.calls.mostRecent().args[0];
      expect(opciones.titulo).toBe('¿Publicar tu web con “La línea comarcal”?');
      expect(opciones.mensaje).toEqual([
        'Las familias verán tu web con el tema ',
        { fuerte: '“La línea comarcal”' },
        ' desde ahora, en lugar de “El estándar del sector”.',
      ]);
      expect(opciones.bloques).toEqual([{ tipo: 'parrafo', partes: ['Tus sedes, horarios, logo y color no cambian.'] }]);
      http.expectOne(URL_PUBLICAR).flush(estadoWeb());
      await fixture.whenStable();
      fixture.detectChanges();
      expect(barra()).toBeNull();
      // El publicado ya no es el estándar (no construido): solo queda la línea comarcal.
      expect(Array.from(el().querySelectorAll('[data-tema]')).map((t) => t.getAttribute('data-tema'))).toEqual(['linea-comarcal']);
    });

    it('error del PUT: aviso con el mensaje de la API y todo sigue igual', async () => {
      confirmar.and.callFake(confirmarYEjecutar);
      abrir(SIN_PUBLICAR);
      barra()!.querySelector<HTMLButtonElement>('.primario')!.click();
      http.expectOne(URL_PUBLICAR).flush({ mensaje: 'Otra persona acaba de publicar la web.' }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();
      http.expectOne(URL).flush(SIN_PUBLICAR); // el 409 vuelve a leer el estado; aquí sigue sin publicar
      fixture.detectChanges();
      expect(avisos.error).toHaveBeenCalledWith('No se ha podido publicar la web. Otra persona acaba de publicar la web.');
      expect(avisos.exito).not.toHaveBeenCalled();
      expect(texto('.barra__texto')).toBe('Tu web aún no está publicada. Has elegido “La línea comarcal”.');
    });

    it('409 (publicación simultánea): tras el aviso vuelve a leer el estado y la pantalla refleja lo publicado', async () => {
      confirmar.and.callFake(confirmarYEjecutar);
      abrir(SIN_PUBLICAR);
      barra()!.querySelector<HTMLButtonElement>('.primario')!.click();
      http.expectOne(URL_PUBLICAR).flush({ mensaje: 'Otra persona acaba de publicar la web.' }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();
      expect(avisos.error).toHaveBeenCalledTimes(1);
      http.expectOne(URL).flush(estadoWeb({ tema: 'estandar' }));
      fixture.detectChanges();
      expect(texto('.estado-web')).toBe('Tu web está publicada con “El estándar del sector”.');
    });

    it('error del PUT distinto de 409: no vuelve a leer el estado', async () => {
      confirmar.and.callFake(confirmarYEjecutar);
      abrir(SIN_PUBLICAR);
      barra()!.querySelector<HTMLButtonElement>('.primario')!.click();
      http.expectOne(URL_PUBLICAR).flush({ mensaje: 'Fallo' }, { status: 500, statusText: 'Error' });
      await fixture.whenStable();
      http.expectNone(URL);
    });

    it('cancelar el diálogo no publica', async () => {
      abrir(SIN_PUBLICAR);
      barra()!.querySelector<HTMLButtonElement>('.primario')!.click();
      await fixture.whenStable();
      http.expectNone(URL_PUBLICAR);
      expect(confirmar).toHaveBeenCalledTimes(1);
      expect(barra()).not.toBeNull();
    });
  });

  describe('salir sin publicar', () => {
    it('solo pregunta con la web publicada y otro tema elegido', async () => {
      abrir(estadoWeb({ tema: 'estandar' }));
      expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
      tarjeta('linea-comarcal').click();
      fixture.detectChanges();
      expect(await fixture.componentInstance.puedeSalir()).toBeFalse();
      expect(confirmar.calls.mostRecent().args[0].titulo).toBe('¿Salir sin guardar?');
    });

    it('la primera vez no avisa', async () => {
      abrir(SIN_PUBLICAR);
      expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
    });
  });
});
