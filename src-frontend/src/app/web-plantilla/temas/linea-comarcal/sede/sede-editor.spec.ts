import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../../environments/environment';
import { CARGAR_CAPA_EDITOR } from '../../../comun/editor-web';
import { RELOJ_WEB } from '../../../comun/reloj';
import { sitioConSedes } from '../../../datos/datos-de-prueba';
import { SitioWeb } from '../../../datos/modelos';
import type { OpcionesCapaEditor } from '../../../editor/capa-editor';
import { simuladorDe } from '../../../editor/casos-vista';
import { CANAL_EDITOR_WEB } from '../../../editor/puente-editor';
import { WEB_PLANTILLA_ROUTES } from '../../../web-plantilla.routes';
import { CLAVE_SEDE_RECORDADA } from '../contenido';

/** Lunes 2 de noviembre de 2026, 16:00 (hora local). */
const AHORA = new Date(2026, 10, 2, 16, 0);

/**
 * W12c-ter: «Sin clases esta semana» de Una sede (boceto `CASOS.sede`, l.2168): con el editor activo, el tablero dice
 * «Sin clases esta semana» y las salidas siguen; sin el editor, la próxima clase de siempre. Los specs de
 * `sede.component.spec.ts` no cambian.
 */
describe('SedeLineaComarcalComponent con el editor de la web (W12c-ter)', () => {
  const url = `${environment.apiUrl}/publico/web`;
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let opciones: OpcionesCapaEditor | null;
  let cargar: jasmine.Spy;
  let tituloAnterior: string;

  beforeEach(async () => {
    tituloAnterior = document.title;
    opciones = null;
    localStorage.removeItem(CLAVE_SEDE_RECORDADA);
    cargar = jasmine.createSpy('cargar').and.callFake(() =>
      Promise.resolve({
        activarCapaEditor: (o: OpcionesCapaEditor) => {
          opciones = o;
          return () => undefined;
        },
      }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(WEB_PLANTILLA_ROUTES),
        { provide: RELOJ_WEB, useValue: () => AHORA },
        { provide: CARGAR_CAPA_EDITOR, useValue: cargar },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CLAVE_SEDE_RECORDADA);
    for (const elemento of Array.from(document.head.querySelectorAll('[data-web-plantilla]'))) {
      elemento.remove();
    }
    document.title = tituloAnterior;
  });

  async function estable(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function abrir(sitio: SitioWeb, ruta: string): Promise<void> {
    await harness.navigateByUrl(ruta);
    http.expectOne((r) => r.url === url).flush(sitio);
    await estable();
    await estable();
  }

  const raiz = () => harness.routeNativeElement!;
  const texto = (selector: string) => raiz().querySelector(selector)?.textContent!.replace(/\s+/g, ' ').trim() ?? null;
  const salidas = () => raiz().querySelectorAll('.salidas > li').length;

  it('sin el editor: la próxima clase, y un mensaje del puente no la quita', async () => {
    await abrir(sitioConSedes(6), '/sedes/estepa');
    expect(texto('.proxima .proxima-rotulo')).toBe('Próxima clase');
    window.dispatchEvent(
      new MessageEvent('message', { data: { canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'sin-clases' }, origin: location.origin, source: window.parent }),
    );
    await estable();
    expect(texto('.proxima .proxima-rotulo')).toBe('Próxima clase');
    expect(cargar).not.toHaveBeenCalled();
  });

  it('con el editor: «Sin clases esta semana» con las mismas salidas; al volver a los datos reales, la próxima clase', async () => {
    await abrir(sitioConSedes(6, { vistaPrevia: true }), '/sedes/estepa?vista-previa=linea-comarcal&editor=1');
    const antes = salidas();
    expect(antes).toBeGreaterThan(0);
    expect(texto('.proxima .proxima-rotulo')).toBe('Próxima clase');
    expect(opciones).withContext('la capa de edición no se activó').not.toBeNull();

    opciones!.alCaso!(simuladorDe('sin-clases'));
    await estable();
    expect(texto('.proxima')).toBe('Sin clases esta semana');
    expect(raiz().querySelector('.proxima-cuando')).toBeNull();
    expect(salidas()).toBe(antes);

    opciones!.alCaso!(null);
    await estable();
    expect(texto('.proxima .proxima-rotulo')).toBe('Próxima clase');
    expect(salidas()).toBe(antes);
  });
});
