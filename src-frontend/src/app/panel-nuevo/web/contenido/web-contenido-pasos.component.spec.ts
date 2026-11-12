import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { CANAL_EDITOR_WEB } from '../../../web-plantilla/editor/puente-editor';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import { ContenidoWeb, ORIGEN_EDITOR } from './web-contenido-vista';
import { WebContenidoComponent } from './web-contenido.component';

const URL = `${environment.apiUrl}/escuela/web`;
const URL_PUBLICA = `${environment.apiUrl}/publico/web`;
const ORIGEN = 'about:blank#';
const PREVIA = '?vista-previa=linea-comarcal&editor=1';
const ESTADO: EstadoWebEscuela = { tema: 'linea-comarcal', publicada: true, publicadaEn: '2026-11-01T10:00:00Z', temasDisponibles: ['linea-comarcal'] };
const CONTENIDO: ContenidoWeb = { version: 1, bloques: [] };

/**
 * W12c-ter: el selector «Paso» de la preinscripción (boceto `SUBS.preinscripcion`, l.2401) y sus tres casos
 * (`CASOS.preinscripcion`, l.1687), y «Sin clases esta semana» en Una sede (l.1682). El paso va por el puente como un
 * número de la lista cerrada; ni el paso ni el caso llegan a la API.
 */
describe('WebContenidoComponent: selector «Paso» y casos de Preinscripción (W12c-ter)', () => {
  let fixture: ComponentFixture<WebContenidoComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: ORIGEN_EDITOR, useValue: ORIGEN }],
    });
    spyOnProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap').and.returnValue(convertToParamMap({}));
    http = TestBed.inject(HttpTestingController);
    spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
  });

  afterEach(() => {
    fixture?.destroy();
    http.verify();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement = HTMLElement>(selector: string) => el().querySelector<T>(selector);
  const todos = <T extends HTMLElement = HTMLElement>(selector: string) => Array.from(el().querySelectorAll<T>(selector));
  const texto = (selector: string) => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const iframe = () => q<HTMLIFrameElement>('iframe');
  const src = () => iframe()?.getAttribute('src') ?? null;
  const pagina = () => q<HTMLSelectElement>('#web-contenido-pagina')!;
  const sub = () => q<HTMLSelectElement>('#web-contenido-sub');
  const anuncio = () => texto('p.oculto[role="status"]');

  function abrir(): void {
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(ESTADO);
    http.expectOne(`${URL}/contenido`).flush(CONTENIDO);
    // Dos sedes: con una sola, el paso «Pueblo» no sale (web-contenido-guardar.component.spec.ts).
    http.expectOne((r) => r.url === URL_PUBLICA).flush({ sedes: [{ nombre: 'Herrera', slug: 'herrera' }, { nombre: 'Estepa', slug: 'estepa' }] });
    http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`).flush({ proximos: [], anteriores: [] });
    fixture.detectChanges();
  }

  function elegir(select: HTMLSelectElement, valor: string): void {
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function llegar(data: unknown): void {
    window.dispatchEvent(new MessageEvent('message', { data, origin: location.origin, source: iframe()!.contentWindow }));
    fixture.detectChanges();
  }

  function abrirMenu(): void {
    q<HTMLButtonElement>('.ver-caso')!.click();
    fixture.detectChanges();
  }

  const casos = () =>
    todos<HTMLButtonElement>('.flot--casos [role="menuitemradio"]')
      .slice(1)
      .map((o) => o.textContent!.replace(/\s+/g, ' ').trim());
  const mensajes = (espia: jasmine.Spy) => espia.calls.allArgs().map(([m, origen]) => [m, origen]);

  it('«Paso» solo en Preinscripción, con los cuatro pasos del boceto; empieza en el 1 y el rótulo lo dice', () => {
    abrir();
    expect(sub()).toBeNull();
    elegir(pagina(), 'preinscripcion');
    expect(texto('label[for="web-contenido-sub"]')).toBe('Paso');
    expect(todos<HTMLOptionElement>('#web-contenido-sub option').map((o) => [o.value, o.textContent!.trim(), o.disabled])).toEqual([
      ['1', '1 · Pueblo', false],
      ['2', '2 · Grupo', false],
      ['3', '3 · Datos y firma', false],
      ['4', 'Enviada', false],
    ]);
    expect(sub()!.value).toBe('1');
    expect(texto('#marco-t')).toBe('Vista previa de tu web · Preinscripción · 1 · Pueblo');
    expect(src()).toBe(`${ORIGEN}/preinscripcion${PREVIA}`);
    for (const otra of ['inicio', 'sede', 'tarifas', 'eventos', 'legales']) {
      elegir(pagina(), otra);
      expect(sub()?.parentElement?.textContent?.includes('Paso') ?? false).withContext(otra).toBeFalse();
    }
  });

  it('cambiar de paso lo manda por el puente (propio origen, solo el número), sin recargar el iframe ni llamar a la API', () => {
    abrir();
    elegir(pagina(), 'preinscripcion');
    const antes = src();
    const enviar = spyOn(iframe()!.contentWindow!, 'postMessage');
    elegir(sub()!, '3');
    expect(mensajes(enviar)).toEqual([[{ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 3 }, location.origin]]);
    expect(src()).toBe(antes);
    expect(texto('#marco-t')).toBe('Vista previa de tu web · Preinscripción · 3 · Datos y firma');
    elegir(sub()!, '4');
    expect(texto('#marco-t')).toBe('Vista previa de tu web · Preinscripción · Enviada');
    // Un valor que no es de la lista no se manda.
    enviar.calls.reset();
    elegir(sub()!, '9');
    expect(enviar).not.toHaveBeenCalled();
    http.expectNone(() => true);
  });

  it('al estar lista la capa le llega el borrador, el paso y, si hay, el caso; en otra página, el paso no', () => {
    abrir();
    const enviar = spyOn(iframe()!.contentWindow!, 'postMessage');
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
    expect(enviar.calls.allArgs().map(([m]) => (m as { tipo: string }).tipo).filter((t) => t !== 'estado-bloques' && t !== 'profesores' && t !== 'zona-visible')).toEqual(['borrador']);
    elegir(pagina(), 'preinscripcion');
    elegir(sub()!, '2');
    abrirMenu();
    q<HTMLButtonElement>('[data-caso="sin-grupo"]')!.click();
    fixture.detectChanges();
    enviar.calls.reset();
    llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
    const tipos = enviar.calls.allArgs().map(([m]) => m as { tipo: string; paso?: number; caso?: string }).filter((m) => m.tipo !== 'estado-bloques' && m.tipo !== 'profesores');
    expect(tipos.map((m) => m.tipo)).toEqual(['borrador', 'paso', 'caso']);
    expect(tipos[1].paso).toBe(2);
    expect(tipos[2].caso).toBe('sin-grupo');
  });

  it('los tres casos de Preinscripción del boceto; elegir uno pone su paso', () => {
    abrir();
    elegir(pagina(), 'preinscripcion');
    abrirMenu();
    expect(texto('.flot__ayuda--casos')).toBe('Textos que solo salen en casos poco frecuentes. Elige uno para verlo y cambiar sus textos.');
    expect(casos()).toEqual([
      'Sin grupo para su edadPaso 2: su sede no tiene grupo para esa edad',
      'Grupo completoPaso 3: se apunta a la lista de espera',
      'En la lista de esperaPaso 4: enviada a la lista de espera',
    ]);
    const enviar = spyOn(iframe()!.contentWindow!, 'postMessage');
    q<HTMLButtonElement>('[data-caso="fin-espera"]')!.click();
    fixture.detectChanges();
    expect(mensajes(enviar)).toEqual([
      [{ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 4 }, location.origin],
      [{ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'fin-espera' }, location.origin],
    ]);
    expect(sub()!.value).toBe('4');
    expect(texto('#marco-t')).toBe('Vista previa de tu web · Preinscripción · Enviada');
    expect(texto('.caso-activo__texto')).toBe(
      'Viendo: En la lista de esperaPaso 4: enviada a la lista de espera. Solo para ver y cambiar sus textos: no es un cambio y no se guarda.',
    );
    expect(anuncio()).toBe('Viendo: En la lista de espera. No es un cambio y no se guarda.');
    http.expectNone(() => true);
  });

  it('cambiar a otro paso quita el caso del paso anterior (como el boceto); al mismo, no', () => {
    abrir();
    elegir(pagina(), 'preinscripcion');
    abrirMenu();
    q<HTMLButtonElement>('[data-caso="espera"]')!.click();
    fixture.detectChanges();
    expect(sub()!.value).toBe('3');
    const enviar = spyOn(iframe()!.contentWindow!, 'postMessage');
    elegir(sub()!, '3');
    expect(enviar).not.toHaveBeenCalled();
    expect(q('.caso-activo')).not.toBeNull();
    elegir(sub()!, '2');
    expect(q('.caso-activo')).toBeNull();
    expect(fixture.componentInstance.caso()).toBeNull();
    expect(mensajes(enviar)).toEqual([
      [{ canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 2 }, location.origin],
      [{ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: null }, location.origin],
    ]);
  });

  it('el paso se queda al ir a otra página y volver', () => {
    abrir();
    elegir(pagina(), 'preinscripcion');
    elegir(sub()!, '3');
    elegir(pagina(), 'tarifas');
    elegir(pagina(), 'preinscripcion');
    expect(sub()!.value).toBe('3');
    expect(texto('#marco-t')).toBe('Vista previa de tu web · Preinscripción · 3 · Datos y firma');
  });

  it('Una sede: «Sin clases esta semana» con su descripción del boceto', () => {
    abrir();
    elegir(pagina(), 'sede');
    abrirMenu();
    expect(casos()).toEqual([
      'Grupo llenoUn grupo sin plazas libres',
      'Lista de esperaUn grupo que ya tiene lista de espera',
      'Sin clases esta semanaVacaciones o festivos: no hay próxima clase',
    ]);
  });
});
