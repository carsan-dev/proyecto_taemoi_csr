import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { proveerCatalogoTesting } from '../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../features/catalogo/testing/catalogo.fixtures';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { LoadingService } from '../../servicios/generales/loading.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { ConvocatoriasComponent } from './convocatorias.component';
import { AlumnoConvocatoria, ConvocatoriaPanel } from './convocatorias-vista';

const API = environment.apiUrl;
const CLAVE_ESTADO = 'listadoConvocatoriasEstado';

const C1: ConvocatoriaPanel = { id: 11, fechaConvocatoria: '2026-11-14T00:00:00', deporte: 'TAEKWONDO' };
const C2: ConvocatoriaPanel = { id: 12, fechaConvocatoria: '2026-12-12T00:00:00', deporte: 'TAEKWONDO' };
const C3: ConvocatoriaPanel = { id: 21, fechaConvocatoria: '2026-11-21T00:00:00', deporte: 'KICKBOXING' };

function inscrito(id: number, cambios: Partial<AlumnoConvocatoria> = {}): AlumnoConvocatoria {
  return {
    id,
    alumnoId: 100 + id,
    nombre: `Ana${id}`,
    apellidos: 'Ficticia Ruiz',
    cuantiaExamen: 25,
    gradoActual: 'BLANCO',
    gradoSiguiente: 'AMARILLO',
    pagado: true,
    ...cambios,
  };
}

const PAGADO = inscrito(1);
const PENDIENTE = inscrito(2, { pagado: false, cuantiaExamen: 30, gradoActual: 'AMARILLO', gradoSiguiente: 'NARANJA' });
const APTO = { id: 501, nombre: 'Luis', apellidos: 'Inventado', grado: 'BLANCO', fechaNacimiento: '2015-03-01' };
const APTO_ROJO = { id: 502, nombre: 'Marta', apellidos: 'Supuesta', grado: 'AZUL', fechaNacimiento: '1990-01-01T12:00:00' };

describe('ConvocatoriasComponent (8b-3e, Convocatorias del panel nuevo)', () => {
  let fixture: ComponentFixture<ConvocatoriasComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let loading: jasmine.SpyObj<LoadingService>;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();

  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  async function esperar(): Promise<void> {
    for (let i = 0; i < 10; i++) {
      await Promise.resolve();
    }
    pintar();
  }

  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');

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

  const bloque = (id: number): HTMLElement => el().querySelector<HTMLElement>(`.convocatorias__bloque[data-convocatoria="${id}"]`)!;
  const cabecera = (id: number): HTMLButtonElement => bloque(id).querySelector<HTMLButtonElement>('.convocatorias__abrir')!;
  const filaAlumno = (id: number): HTMLElement => el().querySelector<HTMLElement>(`.convocatorias__alumno[data-inscrito="${id}"]`)!;
  const pestanas = (): HTMLButtonElement[] => Array.from(el().querySelectorAll<HTMLButtonElement>('[role="tab"]'));

  const vivas = (filtro: (r: TestRequest) => boolean): TestRequest[] => http.match(() => true).filter((r) => !r.cancelled && filtro(r));
  /** Lo que queda sin responder y no se comprueba en ese caso: se responde vacío para que `verify` no falle. */
  function pendientesDe(url: string): TestRequest[] {
    return http.match((r) => r.url === url).filter((r) => !r.cancelled);
  }

  function crear(): void {
    fixture = TestBed.createComponent(ConvocatoriasComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  /** Responde la lectura de convocatorias de cada disciplina con grados. */
  function responderListas(tkd: ConvocatoriaPanel[] = [C1, C2], kb: ConvocatoriaPanel[] = [C3]): void {
    const r = http.match((x) => x.url === `${API}/convocatorias`).filter((x) => !x.cancelled);
    expect(r.length).withContext('listas').toBe(2);
    r.find((x) => x.request.params.get('deporte') === 'TAEKWONDO')!.flush(tkd);
    r.find((x) => x.request.params.get('deporte') === 'KICKBOXING')!.flush(kb);
    fixture.detectChanges();
  }

  /** Responde las cifras de los bloques a la vista. */
  function responderCifras(por: Record<number, AlumnoConvocatoria[]> = {}): void {
    for (const r of vivas((x) => /\/convocatorias\/\d+\/alumnos$/.test(x.request.url))) {
      const id = Number(/convocatorias\/(\d+)\/alumnos/.exec(r.request.url)![1]);
      r.flush(por[id] ?? (id === 11 ? [PAGADO, PENDIENTE] : []));
    }
    fixture.detectChanges();
  }

  function cargar(tkd?: ConvocatoriaPanel[], kb?: ConvocatoriaPanel[]): void {
    crear();
    responderListas(tkd, kb);
    responderCifras();
  }

  /** Abre el bloque y responde sus alumnos y los aptos. */
  function abrir(id = 11, alumnos: AlumnoConvocatoria[] = [PAGADO, PENDIENTE], aptos: unknown[] = [APTO, APTO_ROJO]): void {
    cabecera(id).click();
    fixture.detectChanges();
    http.expectOne(`${API}/convocatorias/${id}/alumnos`).flush(alumnos);
    fixture.detectChanges();
    http.expectOne((r) => r.url === `${API}/alumnos/aptos/convocatoria`).flush(aptos);
    fixture.detectChanges();
  }

  /** La relectura del bloque abierto tras una escritura: alumnos y aptos. */
  function responderRelectura(id = 11, alumnos: AlumnoConvocatoria[] = [PAGADO, PENDIENTE], aptos: unknown[] = [APTO, APTO_ROJO]): void {
    http.expectOne(`${API}/convocatorias/${id}/alumnos`).flush(alumnos);
    fixture.detectChanges();
    http.expectOne((r) => r.url === `${API}/alumnos/aptos/convocatoria`).flush(aptos);
    fixture.detectChanges();
  }

  function elegirApto(id: number): void {
    const select = el().querySelector<HTMLSelectElement>('#convocatorias-apto')!;
    select.value = String(id);
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function radio(t: string): HTMLButtonElement {
    return Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('[role="radio"]')).find((b) => texto(b) === t)!;
  }

  beforeEach(async () => {
    // El componente actualiza los inscritos que recibe: cada prueba parte de los suyos.
    Object.assign(PAGADO, inscrito(1));
    Object.assign(PENDIENTE, inscrito(2, { pagado: false, cuantiaExamen: 30, gradoActual: 'AMARILLO', gradoSiguiente: 'NARANJA' }));
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 9, 22, 10, 0));
    sessionStorage.removeItem(CLAVE_ESTADO);
    quitarTokens = ponerTokensDePrueba();
    loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    await TestBed.configureTestingModule({
      imports: [ConvocatoriasComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        proveerCatalogoTesting(CATALOGO_TAEMOI),
        { provide: LoadingService, useValue: loading },
        { provide: PermisosSesionService, useValue: { obtener: () => of({ disenoNuevo: { activo: true, permitido: true } }) } },
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
    sessionStorage.removeItem(CLAVE_ESTADO);
    jasmine.clock().uninstall();
  });

  // ------------------------------------------------------------------ lecturas y cabecera

  it('carga: GET /convocatorias?deporte= de cada disciplina con grados, como hoy, con ERROR_HANDLED_LOCALLY y sin cargador global', () => {
    crear();
    const r = http.match((x) => x.url === `${API}/convocatorias`);
    expect(r.map((x) => x.request.params.get('deporte'))).toEqual(['TAEKWONDO', 'KICKBOXING']);
    for (const x of r) {
      expect(x.request.method).toBe('GET');
      expect(x.request.withCredentials).toBeTrue();
      expect(x.request.params.keys()).toEqual(['deporte']);
      expect(x.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    }
    r[0].flush([C1, C2]);
    r[1].flush([C3]);
    fixture.detectChanges();
    const cifras = vivas((x) => /\/convocatorias\/\d+\/alumnos$/.test(x.request.url));
    expect(cifras.map((x) => x.request.url)).toEqual([`${API}/convocatorias/11/alumnos`, `${API}/convocatorias/12/alumnos`]);
    for (const x of cifras) {
      expect(x.request.method).toBe('GET');
      expect(x.request.withCredentials).toBeTrue();
      expect(x.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    }
    responderCifras();
    expect(loading.show).not.toHaveBeenCalled();
  });

  it('cabecera: título «Convocatorias» con subtítulo y «Crear convocatoria» como botón principal', () => {
    cargar();
    expect(texto(el().querySelector('h1'))).toBe('Convocatorias');
    expect(texto(el().querySelector('.convocatorias__subtitulo'))).toBe(
      'Exámenes de grado por disciplina: alumnos inscritos, pagos y actualización de grados.'
    );
    const crearBoton = el().querySelector<HTMLButtonElement>('.convocatorias__cabecera .convocatorias__boton-primario')!;
    expect(texto(crearBoton)).toBe('Crear convocatoria');
  });

  // ------------------------------------------------------------------ pestañas

  it('pestañas: tablist con cada disciplina y su número; la primera elegida, con tabindex itinerante', () => {
    cargar();
    expect(el().querySelector('[role="tablist"]')!.getAttribute('aria-label')).toBe('Disciplina');
    expect(pestanas().map(texto)).toEqual(['Taekwondo 2', 'Kickboxing 1']);
    expect(pestanas().map((t) => t.getAttribute('aria-selected'))).toEqual(['true', 'false']);
    expect(pestanas().map((t) => t.tabIndex)).toEqual([0, -1]);
    expect(el().querySelector('[role="tabpanel"]')!.getAttribute('aria-labelledby')).toBe(pestanas()[0].id);
  });

  it('pestañas: elegir otra enseña sus convocatorias sin volver a pedir la lista y pide solo sus cifras', () => {
    cargar();
    pestanas()[1].click();
    fixture.detectChanges();
    expect(http.match((x) => x.url === `${API}/convocatorias`).length).toBe(0);
    expect(vivas(() => true).map((x) => x.request.url)).toEqual([`${API}/convocatorias/21/alumnos`]);
    responderCifras({ 21: [PENDIENTE] });
    expect(el().querySelectorAll('.convocatorias__bloque').length).toBe(1);
    expect(bloque(21)).not.toBeNull();
    // Volver: las cifras ya leídas no se vuelven a pedir.
    pestanas()[0].click();
    fixture.detectChanges();
    expect(vivas(() => true).length).toBe(0);
  });

  it('pestañas: flechas, Inicio y Fin cambian de pestaña y de foco', () => {
    cargar();
    const lista = el().querySelector<HTMLElement>('[role="tablist"]')!;
    pestanas()[0].focus();
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestanas()[1]);
    expect(pestanas()[1].getAttribute('aria-selected')).toBe('true');
    responderCifras();
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestanas()[0]);
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestanas()[1]);
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestanas()[0]);
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestanas()[1]);
  });

  // ------------------------------------------------------------------ bloques

  it('bloques: disciplina, fecha del examen y cifras (inscritos, pagados y pendientes con plural)', () => {
    cargar();
    const b = bloque(11);
    expect(texto(b.querySelector('.convocatorias__fecha'))).toBe('Sábado, 14 de noviembre de 2026');
    expect(texto(b.querySelector('.convocatorias__disciplina'))).toBe('Taekwondo');
    expect(Array.from(b.querySelectorAll('.convocatorias__cifra')).map(texto)).toEqual(['2 inscritos', '1 pagado', '1 pendiente']);
    expect(b.querySelector('.convocatorias__cifra--pendiente')).not.toBeNull();
    expect(Array.from(bloque(12).querySelectorAll('.convocatorias__cifra')).map(texto)).toEqual([
      '0 inscritos',
      '0 pagados',
      '0 pendientes',
    ]);
    expect(bloque(12).querySelector('.convocatorias__cifra--pendiente')).toBeNull();
  });

  it('bloques: un fallo de sus cifras lo dice en el bloque y «Reintentar» las vuelve a pedir', () => {
    crear();
    responderListas();
    http.expectOne(`${API}/convocatorias/11/alumnos`).flush({}, { status: 500, statusText: 'Error' });
    http.expectOne(`${API}/convocatorias/12/alumnos`).flush([]);
    fixture.detectChanges();
    const b = bloque(11);
    expect(texto(b.querySelector('.convocatorias__cifras-error'))).toContain('No se pudieron leer las cifras');
    botonEn(b, 'Reintentar las cifras').click();
    fixture.detectChanges();
    http.expectOne(`${API}/convocatorias/11/alumnos`).flush([PAGADO]);
    fixture.detectChanges();
    expect(Array.from(bloque(11).querySelectorAll('.convocatorias__cifra')).map(texto)).toEqual(['1 inscrito', '1 pagado', '0 pendientes']);
  });

  it('pestañas: si falla la lectura de una disciplina, la pantalla sigue y esa pestaña va sin número', () => {
    crear();
    const r = http.match((x) => x.url === `${API}/convocatorias`).filter((x) => !x.cancelled);
    r.find((x) => x.request.params.get('deporte') === 'TAEKWONDO')!.flush([C1, C2]);
    r.find((x) => x.request.params.get('deporte') === 'KICKBOXING')!.flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    responderCifras();
    expect(el().querySelector('.pn-bloque-error')).toBeNull();
    expect(el().querySelector('[role="tablist"]')).not.toBeNull();
    expect(pestanas().map(texto)).toEqual(['Taekwondo 2', 'Kickboxing']);
    expect(bloque(11)).not.toBeNull();
  });

  it('pestañas: la disciplina que no se pudo leer enseña un error con «Reintentar», nunca el vacío; al reintentar con éxito salen sus bloques y su número', () => {
    crear();
    const r = http.match((x) => x.url === `${API}/convocatorias`).filter((x) => !x.cancelled);
    r.find((x) => x.request.params.get('deporte') === 'TAEKWONDO')!.flush([C1, C2]);
    r.find((x) => x.request.params.get('deporte') === 'KICKBOXING')!.flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    responderCifras();
    pestanas()[1].click();
    fixture.detectChanges();
    const panel = el().querySelector<HTMLElement>('[role="tabpanel"]')!;
    expect(texto(panel.querySelector('pn-bloque-error'))).toContain('No se pudieron obtener las convocatorias');
    expect(panel.querySelector('pn-bloque-vacio')).toBeNull();
    expect(el().textContent).not.toContain('Crear primera convocatoria');
    expect(el().querySelector('.convocatorias__paginas')).toBeNull();
    botonEn(panel, 'Reintentar').click();
    fixture.detectChanges();
    const nueva = http.match((x) => x.url === `${API}/convocatorias`).filter((x) => !x.cancelled);
    expect(nueva.map((x) => x.request.params.get('deporte'))).toEqual(['KICKBOXING']);
    nueva[0].flush([C3]);
    fixture.detectChanges();
    http.expectOne(`${API}/convocatorias/21/alumnos`).flush([]);
    fixture.detectChanges();
    expect(el().querySelector('[role="tabpanel"] pn-bloque-error')).toBeNull();
    expect(bloque(21)).not.toBeNull();
    expect(pestanas().map(texto)).toEqual(['Taekwondo 2', 'Kickboxing 1']);
  });

  it('cifras: una respuesta tardía de la lectura de cifras no pisa la del bloque abierto, que es más nueva', () => {
    crear();
    responderListas();
    const cifras = http.expectOne(`${API}/convocatorias/12/alumnos`);
    cifras.flush([]);
    const tardia = http.expectOne(`${API}/convocatorias/11/alumnos`);
    cabecera(11).click();
    fixture.detectChanges();
    const lecturas = http.match(`${API}/convocatorias/11/alumnos`).filter((x) => !x.cancelled);
    expect(lecturas.length).toBe(1);
    // La del bloque abierto responde antes que la de las cifras, que llega tarde y con datos viejos.
    lecturas[0].flush([PAGADO, PENDIENTE]);
    fixture.detectChanges();
    http.expectOne((x) => x.url === `${API}/alumnos/aptos/convocatoria`).flush([]);
    expect(Array.from(bloque(11).querySelectorAll('.convocatorias__cifra')).map(texto)).toEqual(['2 inscritos', '1 pagado', '1 pendiente']);
    tardia.flush([]);
    fixture.detectChanges();
    expect(Array.from(bloque(11).querySelectorAll('.convocatorias__cifra')).map(texto)).toEqual(['2 inscritos', '1 pagado', '1 pendiente']);
  });

  it('cabecera de cada convocatoria: un botón (foco de teclado) con aria-expanded, aria-controls y foco visible', () => {
    cargar();
    const c = cabecera(11);
    expect(c.tagName).toBe('BUTTON');
    expect(c.getAttribute('aria-expanded')).toBe('false');
    expect(texto(c)).toContain('Ver alumnos');
    // Foco visible: la hoja de la pantalla declara un anillo para :focus-visible de la cabecera (hoy no lo tiene).
    const reglas = Array.from(document.styleSheets).flatMap((s) => {
      try {
        return Array.from(s.cssRules);
      } catch {
        return [];
      }
    });
    const foco = reglas.find(
      (r) => r instanceof CSSStyleRule && r.selectorText.includes('.convocatorias__abrir') && r.selectorText.includes(':focus-visible')
    ) as CSSStyleRule | undefined;
    expect(foco).withContext('regla :focus-visible de la cabecera').toBeDefined();
    expect(foco!.style.outlineStyle || foco!.style.outline).toContain('solid');
  });

  it('«Ver alumnos» abre la lista del bloque (alumnos y luego aptos de su disciplina, como hoy); otra la cierra', () => {
    cargar();
    cabecera(11).click();
    fixture.detectChanges();
    const alumnos = http.expectOne(`${API}/convocatorias/11/alumnos`);
    expect(alumnos.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    alumnos.flush([PAGADO, PENDIENTE]);
    fixture.detectChanges();
    const aptos = http.expectOne((r) => r.url === `${API}/alumnos/aptos/convocatoria`);
    expect(aptos.request.params.get('deporte')).toBe('TAEKWONDO');
    expect(aptos.request.withCredentials).toBeTrue();
    expect(aptos.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    aptos.flush([APTO, APTO_ROJO]);
    fixture.detectChanges();
    const c = cabecera(11);
    expect(c.getAttribute('aria-expanded')).toBe('true');
    expect(texto(c)).toContain('Ocultar alumnos');
    const cuerpo = el().querySelector(`#${c.getAttribute('aria-controls')}`)!;
    expect(cuerpo.querySelectorAll('.convocatorias__alumno').length).toBe(2);
    cabecera(12).click();
    fixture.detectChanges();
    expect(cabecera(11).getAttribute('aria-expanded')).toBe('false');
    expect(cabecera(12).getAttribute('aria-expanded')).toBe('true');
    http.expectOne(`${API}/convocatorias/12/alumnos`).flush([]);
    fixture.detectChanges();
    http.expectOne((r) => r.url === `${API}/alumnos/aptos/convocatoria`).flush([]);
    cabecera(12).click();
    fixture.detectChanges();
    expect(cabecera(12).getAttribute('aria-expanded')).toBe('false');
  });

  it('lista: iniciales, nombre, grado actual y siguiente con su color, cuantía, conmutador y cruz', () => {
    cargar();
    abrir();
    const f = filaAlumno(2);
    expect(texto(f.querySelector('.convocatorias__avatar'))).toBe('AF');
    expect(texto(f.querySelector('.convocatorias__nombre'))).toBe('Ana2 Ficticia Ruiz');
    const grados = Array.from(f.querySelectorAll('.convocatorias__grado'));
    expect(grados.map(texto)).toEqual(['amarillo', 'naranja']);
    const cintos = Array.from(f.querySelectorAll<HTMLElement>('.convocatorias__cinto'));
    expect(cintos.length).toBe(2);
    expect(cintos[0].style.background).toContain('rgb(255, 255, 0)');
    expect(cintos[1].style.background).toContain('rgb(255, 165, 0)');
    expect(texto(f.querySelector('.convocatorias__paso'))).toContain('pasa a');
    expect(f.querySelector<HTMLInputElement>('.convocatorias__cuantia input')!.value).toBe('30,00');
    const conmutador = f.querySelector('[role="group"]')!;
    expect(conmutador.getAttribute('aria-label')).toBe('Pago de Ana2 Ficticia Ruiz');
    expect(Array.from(conmutador.querySelectorAll('button')).map((b) => [texto(b), b.getAttribute('aria-pressed')])).toEqual([
      ['Pagado', 'false'],
      ['Pendiente', 'true'],
    ]);
    expect(botonEn(f, 'Quitar a Ana2 Ficticia Ruiz de la convocatoria')).toBeTruthy();
  });

  // ------------------------------------------------------------------ escrituras de la lista

  it('conmutador: la MISMA llamada que hoy (PUT /convocatorias/alumno/{id} con cuantía y pagado), sin doble envío; aviso y relectura con el bloque abierto', () => {
    cargar();
    abrir();
    botonEn(filaAlumno(1), 'Pendiente').click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/convocatorias/alumno/1`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ cuantiaExamen: 25, pagado: false });
    expect(r.request.withCredentials).toBeTrue();
    // Con la escritura en vuelo, los controles de esa fila no envían otra.
    const pagado = botonEn(filaAlumno(1), 'Pagado');
    expect(pagado.disabled).toBeTrue();
    pagado.click();
    http.expectNone(`${API}/convocatorias/alumno/1`);
    r.flush(null);
    fixture.detectChanges();
    pintar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Datos del alumno actualizados correctamente.' }]);
    responderRelectura(11, [inscrito(1, { pagado: false }), PENDIENTE]);
    expect(cabecera(11).getAttribute('aria-expanded')).toBe('true');
    expect(Array.from(bloque(11).querySelectorAll('.convocatorias__cifra')).map(texto)).toEqual(['2 inscritos', '0 pagados', '2 pendientes']);
  });

  it('conmutador: pulsar el estado que ya tiene no envía nada', () => {
    cargar();
    abrir();
    botonEn(filaAlumno(1), 'Pagado').click();
    fixture.detectChanges();
    http.expectNone(`${API}/convocatorias/alumno/1`);
  });

  it('conmutador: un error avisa con el mensaje del servidor (o el de hoy) y vuelve a lo guardado', () => {
    cargar();
    abrir();
    botonEn(filaAlumno(2), 'Pagado').click();
    http.expectOne(`${API}/convocatorias/alumno/2`).flush({ mensaje: 'La convocatoria ya está cerrada' }, { status: 409, statusText: 'Conflict' });
    pintar();
    expect(avisos()[0].tipo).toBe('error');
    responderRelectura();
    expect(botonEn(filaAlumno(2), 'Pendiente').getAttribute('aria-pressed')).toBe('true');
  });

  it('cuantía: al cambiar el campo, el PUT de hoy con la cuantía (coma decimal) y el pagado; sin cambio, nada', () => {
    cargar();
    abrir();
    const campo = filaAlumno(1).querySelector<HTMLInputElement>('.convocatorias__cuantia input')!;
    expect(campo.getAttribute('inputmode')).toBe('decimal');
    expect(texto(el().querySelector(`label[for="${campo.id}"]`))).toContain('Cuantía');
    campo.value = '25,00';
    campo.dispatchEvent(new Event('change'));
    http.expectNone(`${API}/convocatorias/alumno/1`);
    campo.value = '27,5';
    campo.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const r = http.expectOne(`${API}/convocatorias/alumno/1`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ cuantiaExamen: 27.5, pagado: true });
    r.flush(null);
    fixture.detectChanges();
    responderRelectura();
  });

  describe('escrituras seguidas con la relectura sin responder (el cuerpo parte de lo ya guardado)', () => {
    function campoCuantia(id: number): HTMLInputElement {
      return filaAlumno(id).querySelector<HTMLInputElement>('.convocatorias__cuantia input')!;
    }
    function cambiarCuantia(id: number, valor: string): void {
      const campo = campoCuantia(id);
      campo.value = valor;
      campo.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }
    function guardar(id: number, esperado: unknown): void {
      const r = http.expectOne(`${API}/convocatorias/alumno/${id}`);
      expect(r.request.body).toEqual(esperado as object);
      r.flush(null);
      fixture.detectChanges();
    }

    it('Pagado guardado y la relectura sin llegar: cambiar la cuantía envía la nueva con pagado: true', () => {
      cargar();
      abrir();
      botonEn(filaAlumno(2), 'Pagado').click();
      fixture.detectChanges();
      guardar(2, { cuantiaExamen: 30, pagado: true });
      cambiarCuantia(2, '40');
      guardar(2, { cuantiaExamen: 40, pagado: true });
    });

    it('cuantía guardada y la relectura sin llegar: marcar Pagado envía la cuantía nueva con pagado: true', () => {
      cargar();
      abrir();
      cambiarCuantia(2, '40');
      guardar(2, { cuantiaExamen: 40, pagado: false });
      botonEn(filaAlumno(2), 'Pagado').click();
      fixture.detectChanges();
      guardar(2, { cuantiaExamen: 40, pagado: true });
    });

    it('Pagado guardado y la relectura sin llegar: Pendiente justo después sí se envía (pagado: false)', () => {
      cargar();
      abrir();
      botonEn(filaAlumno(2), 'Pagado').click();
      fixture.detectChanges();
      guardar(2, { cuantiaExamen: 30, pagado: true });
      botonEn(filaAlumno(2), 'Pendiente').click();
      fixture.detectChanges();
      guardar(2, { cuantiaExamen: 30, pagado: false });
    });

    it('cuantía vacía: envía cuantiaExamen: null', () => {
      cargar();
      abrir();
      cambiarCuantia(1, '');
      guardar(1, { cuantiaExamen: null, pagado: true });
    });

    it('un error al guardar no cambia lo guardado: el siguiente envío parte de lo anterior', () => {
      cargar();
      abrir();
      botonEn(filaAlumno(2), 'Pagado').click();
      http.expectOne(`${API}/convocatorias/alumno/2`).flush({}, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      cambiarCuantia(2, '40');
      guardar(2, { cuantiaExamen: 40, pagado: false });
    });
  });

  it('cuantía: un texto que no es un número no se envía y el campo vuelve a lo guardado', () => {
    cargar();
    abrir();
    const campo = filaAlumno(1).querySelector<HTMLInputElement>('.convocatorias__cuantia input')!;
    campo.value = 'veinte';
    campo.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    http.expectNone(`${API}/convocatorias/alumno/1`);
    expect(campo.value).toBe('25,00');
  });

  it('quitar: confirmación destructiva con el foco en «Cancelar» y el DELETE de hoy; cancelar no envía', async () => {
    cargar();
    abrir();
    botonEn(filaAlumno(2), 'Quitar a Ana2 Ficticia Ruiz de la convocatoria').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Quitar alumno');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('Vas a eliminar al alumno Ana2 de la convocatoria.');
    expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
    expect(texto(document.activeElement)).toBe('Cancelar');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'DELETE');

    botonEn(filaAlumno(2), 'Quitar a Ana2 Ficticia Ruiz de la convocatoria').click();
    pintar();
    botonEn(dialogo()!, 'Sí, eliminar').click();
    await esperar();
    const r = http.expectOne(`${API}/alumnos/11/alumno/102`);
    expect(r.request.method).toBe('DELETE');
    r.flush(null);
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'El alumno ha sido eliminado de la convocatoria.' }]);
    responderRelectura(11, [PAGADO]);
  });

  it('agregar: «Agregar alumno» deshabilitado sin elegir; el desplegable con los aptos y su grado', () => {
    cargar();
    abrir();
    const agregar = botonEn(bloque(11), 'Agregar alumno');
    expect(agregar.disabled).toBeTrue();
    const opciones = Array.from(el().querySelectorAll<HTMLOptionElement>('#convocatorias-apto option')).map(texto);
    expect(opciones).toEqual(['Selecciona un alumno apto para examen', 'Luis Inventado - BLANCO', 'Marta Supuesta - AZUL']);
    expect(texto(el().querySelector('.convocatorias__etiqueta'))).toBe('Alumno apto para examen');
    elegirApto(501);
    expect(botonEn(bloque(11), 'Agregar alumno').disabled).toBeFalse();
  });

  it('«Selecciona el tipo de producto»: elección explícita (nada elegido al abrir, «Agregar» deshabilitado) y el POST de hoy', async () => {
    cargar();
    abrir();
    elegirApto(501);
    botonEn(bloque(11), 'Agregar alumno').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Selecciona el tipo de producto');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('¿Asignar el precio por antigüedad o por recompensa?');
    expect(Array.from(dialogo()!.querySelectorAll('[role="radio"]')).map((b) => [texto(b), b.getAttribute('aria-checked')])).toEqual([
      ['Por recompensa', 'false'],
      ['Por antigüedad', 'false'],
    ]);
    expect(botonEn(dialogo()!, 'Agregar').disabled).toBeTrue();
    expect(texto(document.activeElement)).toBe('Cancelar');
    radio('Por antigüedad').click();
    pintar();
    botonEn(dialogo()!, 'Agregar').click();
    await esperar();
    const r = http.expectOne(`${API}/alumnos/11/alumno/501`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({ porRecompensa: false, rojoBordado: false });
    r.flush({});
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'El alumno ha sido asignado correctamente.' }]);
    responderRelectura();
  });

  it('«Selecciona el tipo de producto»: «Cancelar», Esc y la X no deciden nada ni envían', async () => {
    cargar();
    abrir();
    elegirApto(501);
    for (const cerrar of ['Cancelar', 'Escape', 'Cerrar']) {
      botonEn(bloque(11), 'Agregar alumno').click();
      pintar();
      if (cerrar === 'Escape') {
        dialogo()!.dispatchEvent(new Event('cancel', { cancelable: true }));
      } else {
        botonEn(dialogo()!, cerrar).click();
      }
      await esperar();
      expect(dialogo()).toBeNull();
    }
    http.expectNone((r) => r.method === 'POST');
  });

  it('grado rojo: tras el tipo, la elección de hoy («Rojo (sin bordar)» por defecto); «Rojo bordado» envía rojoBordado: true', async () => {
    cargar();
    abrir();
    elegirApto(502);
    botonEn(bloque(11), 'Agregar alumno').click();
    pintar();
    radio('Por recompensa').click();
    pintar();
    botonEn(dialogo()!, 'Agregar').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Selecciona el grado rojo');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe(
      'El alumno pasará a rojo en Taekwondo. Selecciona el tipo de grado rojo.'
    );
    expect(radio('Rojo (sin bordar)').getAttribute('aria-checked')).toBe('true');
    radio('Rojo bordado').click();
    pintar();
    botonEn(dialogo()!, 'Continuar').click();
    await esperar();
    const r = http.expectOne(`${API}/alumnos/11/alumno/502`);
    expect(r.request.body).toEqual({ porRecompensa: true, rojoBordado: true });
    r.flush({});
    await esperar();
    responderRelectura();
  });

  it('grado rojo: cancelar la segunda pregunta no envía nada', async () => {
    cargar();
    abrir();
    elegirApto(502);
    botonEn(bloque(11), 'Agregar alumno').click();
    pintar();
    radio('Por antigüedad').click();
    pintar();
    botonEn(dialogo()!, 'Agregar').click();
    await esperar();
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'POST');
  });

  it('«Cargar más alumnos»: la petición de hoy (página y tamaño 10) y los aptos; desaparece al llegar a la última', () => {
    cargar();
    abrir();
    botonEn(bloque(11), 'Cargar más alumnos').click();
    fixture.detectChanges();
    const r = http.expectOne((x) => x.url === `${API}/alumnos`);
    expect(r.request.urlWithParams).toBe(`${API}/alumnos?page=1&size=10&incluirInactivos=false`);
    expect(r.request.headers.get('X-Skip-Loading')).toBe('true');
    r.flush({ content: [], totalPages: 3 });
    fixture.detectChanges();
    http.expectOne((x) => x.url === `${API}/alumnos/aptos/convocatoria`).flush([APTO]);
    fixture.detectChanges();
    botonEn(bloque(11), 'Cargar más alumnos').click();
    const r2 = http.expectOne((x) => x.url === `${API}/alumnos`);
    expect(r2.request.params.get('page')).toBe('2');
    r2.flush({ content: [], totalPages: 3 });
    fixture.detectChanges();
    http.expectOne((x) => x.url === `${API}/alumnos/aptos/convocatoria`).flush([APTO]);
    fixture.detectChanges();
    expect(Array.from(bloque(11).querySelectorAll('button')).some((b) => texto(b) === 'Cargar más alumnos')).toBeFalse();
  });

  it('vacío del bloque: «No hay alumnos inscritos en esta convocatoria»; PDF y «Actualizar grados de todos» deshabilitados', () => {
    cargar();
    abrir(12, []);
    const vacio = bloque(12).querySelector('pn-bloque-vacio')!;
    expect(texto(vacio)).toContain('No hay alumnos inscritos en esta convocatoria');
    expect(botonEn(bloque(12), 'PDF').disabled).toBeTrue();
    expect(botonEn(bloque(12), 'Actualizar grados de todos').disabled).toBeTrue();
  });

  // ------------------------------------------------------------------ PDF

  describe('PDF', () => {
    let descargas: string[];

    beforeEach(() => {
      descargas = [];
      spyOn(URL, 'createObjectURL').and.returnValue('blob:prueba');
      spyOn(URL, 'revokeObjectURL');
      spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
        descargas.push(this.download);
      });
    });

    function abrirMenu(): HTMLElement {
      botonEn(bloque(11), 'PDF').click();
      fixture.detectChanges();
      return bloque(11).querySelector<HTMLElement>('[role="menu"]')!;
    }

    it('menú «PDF» (botón de menú) con «Ver PDF» y «Descargar PDF» y teclado de menú', () => {
      cargar();
      abrir();
      const boton = botonEn(bloque(11), 'PDF');
      expect(boton.getAttribute('aria-haspopup')).toBe('menu');
      expect(boton.getAttribute('aria-expanded')).toBe('false');
      const menu = abrirMenu();
      expect(boton.getAttribute('aria-expanded')).toBe('true');
      expect(boton.getAttribute('aria-controls')).toBe(menu.id);
      expect(Array.from(menu.querySelectorAll('[role="menuitem"]')).map(texto)).toEqual(['Ver PDF', 'Descargar PDF 14/11/2026']);
      expect(document.activeElement).toBe(menu.querySelector('[role="menuitem"]'));
      (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
      expect(texto(document.activeElement)).toBe('Descargar PDF 14/11/2026');
      (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      fixture.detectChanges();
      expect(bloque(11).querySelector('[role="menu"]')).toBeNull();
      expect(document.activeElement).toBe(boton);
    });

    it('«Descargar PDF»: la petición de hoy y el nombre con la fecha', async () => {
      cargar();
      abrir();
      const menu = abrirMenu();
      Array.from(menu.querySelectorAll<HTMLButtonElement>('[role="menuitem"]'))[1].click();
      pintar();
      const r = http.expectOne(`${API}/convocatorias/11/informe-pdf`);
      expect(r.request.method).toBe('GET');
      expect(r.request.responseType).toBe('blob');
      expect(r.request.withCredentials).toBeTrue();
      r.flush(new Blob(['%PDF']));
      await esperar();
      expect(descargas).toEqual(['informe_convocatoria_TAEKWONDO_14_11_2026.pdf']);
      expect(loading.show).not.toHaveBeenCalled();
    });

    it('«Ver PDF» abre el visor del panel nuevo; un fallo avisa con el texto de hoy, sin SweetAlert2', async () => {
      cargar();
      abrir();
      Array.from(abrirMenu().querySelectorAll<HTMLButtonElement>('[role="menuitem"]'))[0].click();
      pintar();
      http.expectOne(`${API}/convocatorias/11/informe-pdf`).flush(new Blob(['%PDF']));
      await esperar();
      expect(el().querySelector('pn-visor-pdf')).not.toBeNull();
      expect(descargas).toEqual([]);
      fixture.componentInstance.informes.cerrar();
      fixture.detectChanges();
      Array.from(abrirMenu().querySelectorAll<HTMLButtonElement>('[role="menuitem"]'))[1].click();
      pintar();
      http.expectOne(`${API}/convocatorias/11/informe-pdf`).flush(new Blob([]), { status: 500, statusText: 'Error' });
      await esperar();
      expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo generar el informe.' }]);
      expect(Swal.isVisible()).toBeFalse();
    });
  });

  // ------------------------------------------------------------------ actualizar grados

  it('«Actualizar grados de todos»: botón principal al pie; confirmación con el aviso de impagos de hoy y el PUT de hoy', async () => {
    cargar();
    abrir();
    const boton = botonEn(bloque(11), 'Actualizar grados de todos');
    expect(boton.classList).toContain('convocatorias__boton-primario');
    boton.click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Actualizar grados');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe(
      'Hay alumnos que no han pagado la convocatoria. ¿Deseas continuar?'
    );
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone(`${API}/convocatorias/11/actualizar-grados`);

    botonEn(bloque(11), 'Actualizar grados de todos').click();
    pintar();
    botonEn(dialogo()!, 'Sí, actualizar').click();
    pintar();
    const r = http.expectOne(`${API}/convocatorias/11/actualizar-grados`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({});
    // Sin doble envío: el diálogo sigue abierto y ocupado.
    expect(dialogo()).not.toBeNull();
    botonEn(dialogo()!, 'Actualizando grados…').click();
    http.expectNone(`${API}/convocatorias/11/actualizar-grados`);
    r.flush(null);
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Grados actualizados correctamente.' }]);
    responderRelectura();
    expect(loading.show).not.toHaveBeenCalled();
  });

  it('«Actualizar grados de todos» con todos pagados: confirma igualmente, con el número de alumnos', async () => {
    cargar();
    abrir(11, [PAGADO]);
    botonEn(bloque(11), 'Actualizar grados de todos').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('Se actualizará el grado de 1 alumno inscrito.');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone(`${API}/convocatorias/11/actualizar-grados`);
  });

  it('«Actualizar grados de todos»: un fallo avisa con el texto de hoy', async () => {
    cargar();
    abrir();
    botonEn(bloque(11), 'Actualizar grados de todos').click();
    pintar();
    botonEn(dialogo()!, 'Sí, actualizar').click();
    pintar();
    http.expectOne(`${API}/convocatorias/11/actualizar-grados`).flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudieron actualizar los grados.' }]);
  });

  // ------------------------------------------------------------------ eliminar y crear convocatoria

  it('papelera: confirmación destructiva con el foco en «Cancelar» y el DELETE de hoy; el bloque y su número se van', async () => {
    cargar();
    const papelera = botonEn(bloque(11), 'Eliminar la convocatoria del 14/11/2026');
    papelera.click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Eliminar convocatoria');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe(
      'Vas a eliminar la convocatoria del 14/11/2026. Esta acción no se puede deshacer.'
    );
    expect(texto(document.activeElement)).toBe('Cancelar');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'DELETE');

    botonEn(bloque(11), 'Eliminar la convocatoria del 14/11/2026').click();
    pintar();
    botonEn(dialogo()!, 'Sí, eliminar').click();
    await esperar();
    const r = http.expectOne(`${API}/convocatorias/11`);
    expect(r.request.method).toBe('DELETE');
    r.flush(null);
    await esperar();
    expect(bloque(11)).toBeNull();
    expect(pestanas().map(texto)).toEqual(['Taekwondo 1', 'Kickboxing 1']);
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'La convocatoria ha sido eliminada.' }]);
  });

  it('«Crear convocatoria»: disciplina (la pestaña elegida) y fecha (hoy), confirmación y el POST de hoy; el bloque nuevo aparece', async () => {
    cargar();
    botonEn(el().querySelector('.convocatorias__cabecera')!, 'Crear convocatoria').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Crear convocatoria');
    const deporte = dialogo()!.querySelector<HTMLSelectElement>('#convocatoria-deporte')!;
    expect(Array.from(deporte.options).map((o) => [o.value, texto(o)])).toEqual([
      ['TAEKWONDO', 'Taekwondo'],
      ['KICKBOXING', 'Kickboxing'],
    ]);
    expect(deporte.value).toBe('TAEKWONDO');
    const campoFecha = dialogo()!.querySelector<HTMLInputElement>('#convocatoria-fecha')!;
    expect(campoFecha.value).toBe('2026-10-22');
    campoFecha.value = '';
    campoFecha.dispatchEvent(new Event('input'));
    botonEn(dialogo()!, 'Crear').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__error'))).toBe('Por favor, selecciona una fecha');
    campoFecha.value = '2026-11-28';
    campoFecha.dispatchEvent(new Event('input'));
    botonEn(dialogo()!, 'Crear').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('Vas a crear una convocatoria de Taekwondo para 28/11/2026.');
    botonEn(dialogo()!, 'Sí, crear').click();
    await esperar();
    const r = http.expectOne(`${API}/convocatorias`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body.deporte).toBe('TAEKWONDO');
    expect(r.request.body.fechaConvocatoria).toEqual(new Date('2026-11-28T00:00:00'));
    expect(Object.keys(r.request.body)).toEqual(['fechaConvocatoria', 'deporte']);
    r.flush({ id: 13, fechaConvocatoria: '2026-11-28T00:00:00', deporte: 'TAEKWONDO' });
    await esperar();
    expect(bloque(13)).not.toBeNull();
    expect(Array.from(bloque(13).querySelectorAll('.convocatorias__cifra')).map(texto)).toEqual([
      '0 inscritos',
      '0 pagados',
      '0 pendientes',
    ]);
    expect(pestanas().map(texto)).toEqual(['Taekwondo 3', 'Kickboxing 1']);
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'La convocatoria ha sido creada correctamente.' }]);
    http.expectNone(`${API}/convocatorias/13/alumnos`);
  });

  it('«Crear convocatoria»: cancelar en cualquiera de los dos pasos no envía nada', async () => {
    cargar();
    botonEn(el().querySelector('.convocatorias__cabecera')!, 'Crear convocatoria').click();
    pintar();
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    botonEn(el().querySelector('.convocatorias__cabecera')!, 'Crear convocatoria').click();
    pintar();
    botonEn(dialogo()!, 'Crear').click();
    await esperar();
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'POST');
  });

  // ------------------------------------------------------------------ paginación y estado

  it('paginación de convocatorias bajo los bloques: 5 por página; la página 2 pide solo las cifras de sus bloques', () => {
    const muchas = Array.from({ length: 7 }, (_, i) => ({ id: 30 + i, fechaConvocatoria: `2026-11-${10 + i}T00:00:00`, deporte: 'TAEKWONDO' }));
    crear();
    responderListas(muchas, []);
    expect(vivas(() => true).length).toBe(5);
    responderCifras();
    expect(el().querySelectorAll('.convocatorias__bloque').length).toBe(5);
    const nav = el().querySelector('nav.convocatorias__paginacion')!;
    expect(nav.getAttribute('aria-label')).toBe('Páginas de convocatorias');
    botonEn(nav, 'Página 2').click();
    fixture.detectChanges();
    expect(vivas(() => true).map((r) => r.request.url)).toEqual([`${API}/convocatorias/35/alumnos`, `${API}/convocatorias/36/alumnos`]);
    responderCifras();
    expect(el().querySelectorAll('.convocatorias__bloque').length).toBe(2);
    expect(JSON.parse(sessionStorage.getItem(CLAVE_ESTADO)!)).toEqual({
      paginaActualConvocatorias: 2,
      tamanoPaginaConvocatorias: 5,
      deporteSeleccionado: 'TAEKWONDO',
    });
  });

  it('recuerda la disciplina y la página de la sesión, con la clave de hoy', () => {
    sessionStorage.setItem(CLAVE_ESTADO, JSON.stringify({ paginaActualConvocatorias: 1, tamanoPaginaConvocatorias: 5, deporteSeleccionado: 'KICKBOXING' }));
    crear();
    responderListas();
    expect(pestanas()[1].getAttribute('aria-selected')).toBe('true');
    expect(vivas(() => true).map((r) => r.request.url)).toEqual([`${API}/convocatorias/21/alumnos`]);
    responderCifras();
  });

  it('esqueleto con forma de bloques a los 300 ms, y nada antes', () => {
    crear();
    jasmine.clock().tick(299);
    fixture.detectChanges();
    expect(el().querySelector('.pn-esqueleto__forma')).toBeNull();
    jasmine.clock().tick(1);
    fixture.detectChanges();
    expect(el().querySelectorAll('.convocatorias__esqueleto-bloque').length).toBeGreaterThanOrEqual(3);
    expect(el().querySelector('pn-esqueleto [role="status"]')!.textContent).toContain('Cargando convocatorias');
    responderListas();
    responderCifras();
    expect(el().querySelector('pn-esqueleto')).toBeNull();
  });

  it('error de carga (ninguna disciplina se pudo leer): bloque de error con «Reintentar» (sin SweetAlert2) que vuelve a pedir las listas', () => {
    crear();
    const r = http.match((x) => x.url === `${API}/convocatorias`);
    r[0].flush({}, { status: 500, statusText: 'Error' });
    r[1].flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    const error = el().querySelector('pn-bloque-error')!;
    expect(texto(error)).toContain('No se pudieron obtener las convocatorias');
    expect(document.querySelector('.swal2-container')).toBeNull();
    botonEn(error, 'Reintentar').click();
    fixture.detectChanges();
    responderListas();
    responderCifras();
    expect(bloque(11)).not.toBeNull();
  });

  it('error al abrir: bloque de error en la lista con «Reintentar»', () => {
    cargar();
    cabecera(11).click();
    fixture.detectChanges();
    http.expectOne(`${API}/convocatorias/11/alumnos`).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    const error = bloque(11).querySelector('pn-bloque-error')!;
    expect(texto(error)).toContain('No se pudieron obtener los alumnos de la convocatoria');
    botonEn(error, 'Reintentar').click();
    fixture.detectChanges();
    responderRelectura();
    expect(filaAlumno(1)).not.toBeNull();
  });

  it('vacío: «No hay convocatorias disponibles» con «Crear primera convocatoria», que abre el diálogo de crear', () => {
    cargar([], []);
    const vacio = el().querySelector('pn-bloque-vacio')!;
    expect(texto(vacio)).toContain('No hay convocatorias disponibles');
    expect(texto(vacio)).toContain('No hay convocatorias registradas para Taekwondo.');
    botonEn(vacio, 'Crear primera convocatoria').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Crear convocatoria');
    expect(el().querySelector('nav.convocatorias__paginacion')).toBeNull();
  });

  it('al destruir la pantalla antes de la respuesta no hay aviso ni relectura', () => {
    cargar();
    abrir();
    botonEn(filaAlumno(1), 'Pendiente').click();
    const r = http.expectOne(`${API}/convocatorias/alumno/1`);
    fixture.destroy();
    expect(r.cancelled).toBeTrue();
    pintar();
    expect(avisos()).toEqual([]);
  });

  it('nunca abre SweetAlert2 ni el cargador global en el flujo normal', () => {
    const fire = spyOn(Swal, 'fire').and.callThrough();
    cargar();
    abrir();
    expect(fire).not.toHaveBeenCalled();
    expect(loading.show).not.toHaveBeenCalled();
    expect(pendientesDe(`${API}/convocatorias`).length).toBe(0);
  });
});
