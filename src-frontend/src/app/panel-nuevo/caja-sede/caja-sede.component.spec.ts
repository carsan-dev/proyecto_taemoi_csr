import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { environment } from '../../../environments/environment';
import { EntregaCaja, MovimientoCaja, PendienteCobrador, TipoMovimientoCaja } from '../../interfaces/caja';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { valorDeHoy } from '../../testing/oraculo-hoy';
import { HOY } from './caja-sede.component.hoy';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { CLAVE_ULTIMA_SEDE, TEXTO_ENTREGA_CAMBIADA } from './caja-sede-vista';
import { cobroVista, entregaVista, movimientoVista, profesorVista } from './caja-sede-vista.spec';
import { CajaSedePanelComponent } from './caja-sede.component';

const API = environment.apiUrl;
const SEDES = `${API}/tesoreria/resumen`;
const caja = (sede: number, que: string) => `${API}/caja/sedes/${sede}/${que}`;
const SOLICITUDES = `${API}/solicitudes`;

const PROFESOR = profesorVista();
const ENTREGA = entregaVista();
const ANULADA = entregaVista({
  id: 8, estado: 'ANULADA', motivoAnulacion: 'contada dos veces', diferencia: 0, importeContado: 30, nota: null,
  movimientosDeCobro: [{ id: 40, tipo: 'ERROR', importe: 12, cobroId: 9, estado: 'ACTIVO' }],
});
const FALTANTE = movimientoVista({ id: 4, tipo: 'FALTANTE', importe: -1, entregaCajaId: 7, nota: 'faltaba 1' });
const SUELTO = movimientoVista();
const RESUMEN = { sedeId: 5, desde: '2026-10-01', hasta: '2026-10-31', pendienteDeEntregar: 45, recibidoEnPeriodo: 19, diferenciasEnPeriodo: -1, saldoCaja: 1240 };

describe('CajaSedePanelComponent (8b-3j, Caja de sede del panel nuevo, P2 pestañas)', () => {
  let fixture: ComponentFixture<CajaSedePanelComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let esAdmin: boolean;
  let guardarEnStorage: jasmine.Spy;
  let alumnos: jasmine.Spy;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/[\s  ]+/g, ' ').trim();
  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  const comp = (): CajaSedePanelComponent => fixture.componentInstance;
  const plano = (t: string | null): string => (t ?? '').replace(/[  ]/g, ' ');

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

  function avisos(): { tipo: string; texto: string }[] {
    return Array.from(document.querySelectorAll<HTMLElement>('.pn-aviso')).map((a) => ({
      tipo: a.classList.contains('pn-aviso--error') ? 'error' : a.classList.contains('pn-aviso--info') ? 'info' : 'exito',
      texto: texto(a.querySelector('.pn-aviso__texto')),
    }));
  }

  const peticiones = (url: string, metodo = 'GET'): TestRequest[] => http.match((r) => r.method === metodo && r.url === url);

  function crear(): void {
    fixture = TestBed.createComponent(CajaSedePanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  /** Las cuatro lecturas de una sede (resumen y movimientos del periodo, pendientes y entregas). */
  function responderSede(
    sede: number,
    datos: { resumen?: object; pendientes?: PendienteCobrador[]; entregas?: EntregaCaja[]; total?: number; paginas?: number; movimientos?: MovimientoCaja[] } = {}
  ): void {
    peticiones(caja(sede, 'resumen')).forEach((r) => r.flush({ ...RESUMEN, sedeId: sede, ...(datos.resumen ?? {}) }));
    peticiones(caja(sede, 'movimientos')).forEach((r) => r.flush(datos.movimientos ?? [FALTANTE, SUELTO]));
    peticiones(caja(sede, 'pendiente-entrega')).forEach((r) => r.flush(datos.pendientes ?? [PROFESOR]));
    const entregas = datos.entregas ?? [ENTREGA];
    peticiones(caja(sede, 'entregas')).forEach((r) =>
      r.flush({ content: entregas, totalPages: datos.paginas ?? 1, totalElements: datos.total ?? entregas.length, number: 0, size: 25 })
    );
    fixture.detectChanges();
  }

  function cargar(sedes = [{ sedeId: 5, nombre: 'Sede Prueba' }], datos: Parameters<typeof responderSede>[1] = {}): void {
    crear();
    peticiones(SEDES).forEach((r) => r.flush({ porSede: sedes }));
    fixture.detectChanges();
    const primera = comp().sedeId;
    if (primera !== null) {
      responderSede(primera, datos);
    }
  }

  function boton(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => (x.getAttribute('aria-label') ?? '').trim() === t || texto(x) === t
    );
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  const pestana = (p: string): HTMLButtonElement => el().querySelector<HTMLButtonElement>(`#caja-pestana-${p}`)!;
  const panel = (): HTMLElement => el().querySelector<HTMLElement>('[role="tabpanel"]:not([hidden])')!;
  const profesor = (id = 9): HTMLElement => el().querySelector<HTMLElement>(`.caja__profesor[data-usuario="${id}"]`)!;
  const formEntrega = (): HTMLElement | null => el().querySelector<HTMLElement>('.caja__entrega');
  const formMovimiento = (): HTMLElement | null => el().querySelector<HTMLElement>('.caja__mov-form');
  const filaEntrega = (id: number): HTMLElement => el().querySelector<HTMLElement>(`.caja__entregas tr[data-id="${id}"]`)!;
  const filaMovimiento = (id: number): HTMLElement => el().querySelector<HTMLElement>(`.caja__movimientos tr[data-id="${id}"]`)!;

  function elegir(p: string): void {
    pestana(p).click();
    fixture.detectChanges();
  }

  function escribirEn(control: HTMLInputElement | HTMLTextAreaElement, valor: string): void {
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function abrirRecibir(id = 9): HTMLElement {
    boton(profesor(id), 'Recibir entrega').click();
    fixture.detectChanges();
    return formEntrega()!;
  }

  async function confirmarDialogo(d: HTMLDialogElement): Promise<void> {
    (d.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
    await esperar();
  }

  function escribirDialogo(d: HTMLDialogElement, valor: string): void {
    const t = d.querySelector<HTMLTextAreaElement>('textarea')!;
    t.value = valor;
    t.dispatchEvent(new Event('input'));
  }

  beforeEach(async () => {
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    localStorage.removeItem(CLAVE_ULTIMA_SEDE);
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 9, 23, 12, 0));
    quitarTokens = ponerTokensDePrueba();
    esAdmin = true;
    guardarEnStorage = spyOn(Storage.prototype, 'setItem').and.callThrough();
    alumnos = jasmine.createSpy('obtenerTodosLosAlumnosSinPaginar').and.returnValue(
      of([{ id: 21, nombre: 'Ana', apellidos: 'Prueba' }, { id: 22, nombre: 'Leo', apellidos: 'Prueba' }])
    );
    await TestBed.configureTestingModule({
      imports: [CajaSedePanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthenticationService,
          useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => esAdmin, obtenerRoles: () => of([]) },
        },
        { provide: EndpointsService, useValue: { obtenerTodosLosAlumnosSinPaginar: alumnos } },
        { provide: PermisosSesionService, useValue: { obtener: () => of({}) } },
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
    jasmine.clock().uninstall();
    localStorage.removeItem(CLAVE_ULTIMA_SEDE);
  });

  // ------------------------------------------------------------------ lectura, cabecera, resumen y pestañas

  it('al entrar pide las sedes y, con la primera, resumen y movimientos del mes, pendientes y entregas (con ERROR_HANDLED_LOCALLY)', () => {
    crear();
    const [s] = peticiones(SEDES);
    expect(s.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    s.flush({ porSede: [{ sedeId: 5, nombre: 'Sede Prueba' }, { sedeId: null, nombre: 'Sin sede' }] });
    const [r] = peticiones(caja(5, 'resumen'));
    expect(r.request.urlWithParams).toBe(`${caja(5, 'resumen')}?desde=2026-10-01&hasta=2026-10-31`);
    expect(r.request.withCredentials).toBeTrue();
    expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(peticiones(caja(5, 'movimientos'))[0].request.urlWithParams).toBe(`${caja(5, 'movimientos')}?desde=2026-10-01&hasta=2026-10-31`);
    expect(peticiones(caja(5, 'pendiente-entrega')).length).toBe(1);
    expect(peticiones(caja(5, 'entregas'))[0].request.urlWithParams).toBe(`${caja(5, 'entregas')}?page=1&size=25`);
    responderSede(5);
    http.verify();
  });

  it('cabecera de hoy, sede y periodo bajo el título; resumen con las cuatro cifras es-ES; tres pestañas con su número', () => {
    cargar();
    expect(texto(el().querySelector('h1'))).toBe('Caja de sede');
    expect(texto(el().querySelector('.caja__subtitulo'))).toBe(
      'El efectivo de la sede: lo que hay en caja, lo que traen los profesores y lo que se ha apuntado.'
    );
    // Con una sola sede no hay desplegable: se nombra la sede.
    expect(el().querySelector('#caja-sede')).toBeNull();
    expect(texto(el().querySelector('.caja__sede-unica'))).toContain('Sede Prueba');
    const periodo = el().querySelector<HTMLSelectElement>('#caja-periodo')!;
    expect(periodo.options.length).toBe(24);
    expect(texto(periodo.selectedOptions[0])).toBe('Octubre 2026');
    const cifras = Array.from(el().querySelectorAll('.caja__cifra')).map((c) =>
      ['.caja__cifra-titulo', '.caja__cifra-valor', '.caja__cifra-detalle'].map((k) => texto(c.querySelector(k))).join(' | ')
    );
    expect(cifras).toEqual([
      'Saldo de caja | 1.240,00 € | Efectivo que debería haber en la sede',
      'Pendiente de entregar | 45,00 € | En mano de los profesores',
      'Recibido este periodo | 19,00 € | Octubre 2026',
      'Diferencias | −1,00 € | Sobrantes y faltantes de Octubre 2026',
    ]);
    expect(el().querySelector('[data-clave="pendiente"] .caja__cifra-valor')!.getAttribute('data-tono')).toBe('atencion');
    expect(el().querySelector('[data-clave="diferencias"] .caja__cifra-valor')!.getAttribute('data-tono')).toBe('peligro');
    const tabs = Array.from(el().querySelectorAll('[role="tab"]')).map((t) => [texto(t), t.getAttribute('aria-selected')]);
    expect(tabs).toEqual([['Por recibir 1', 'true'], ['Entregas 1', 'false'], ['Movimientos 2', 'false']]);
  });

  it('las pestañas se mueven con las flechas, Inicio y Fin, y el foco sigue a la elegida', () => {
    cargar();
    document.body.appendChild(el());
    const lista = el().querySelector<HTMLElement>('[role="tablist"]')!;
    pestana('recibir').focus();
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(pestana('entregas').getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(pestana('entregas'));
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestana('movimientos'));
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestana('recibir'));
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();
    expect(pestana('movimientos').getAttribute('aria-selected')).toBe('true');
    expect(pestana('recibir').getAttribute('tabindex')).toBe('-1');
    el().remove();
  });

  it('cambiar el periodo relee solo resumen y movimientos de ese mes; si un mes anterior contesta tarde, se descarta', () => {
    cargar();
    const periodo = el().querySelector<HTMLSelectElement>('#caja-periodo')!;
    periodo.value = '2026-09';
    periodo.dispatchEvent(new Event('change'));
    periodo.value = '2026-08';
    periodo.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const resumenes = peticiones(caja(5, 'resumen'));
    expect(resumenes.map((r) => r.request.params.get('desde'))).toEqual(['2026-09-01', '2026-08-01']);
    expect(resumenes[0].cancelled).toBeTrue();
    expect(peticiones(caja(5, 'pendiente-entrega')).length).toBe(0);
    expect(peticiones(caja(5, 'entregas')).length).toBe(0);
    resumenes[1].flush({ ...RESUMEN, saldoCaja: 200 });
    peticiones(caja(5, 'movimientos')).filter((r) => !r.cancelled).forEach((r) => r.flush([]));
    fixture.detectChanges();
    expect(texto(el().querySelector('.caja__cifra'))).toContain('200,00 €');
    expect(texto(pestana('movimientos'))).toBe('Movimientos 0');
    expect(texto(el().querySelectorAll('.caja__cifra')[2])).toContain('Agosto 2026');
  });

  // ------------------------------------------------------------------ sedes

  describe('con varias sedes', () => {
    const DOS = [{ sedeId: 8, nombre: 'Sur' }, { sedeId: 3, nombre: 'Norte' }];

    it('desplegable de sedes ordenadas; sin preferencia abre la primera; con preferencia válida, esa', () => {
      cargar(DOS);
      const sede = el().querySelector<HTMLSelectElement>('#caja-sede')!;
      expect(Array.from(sede.options).map((o) => texto(o))).toEqual(['Norte', 'Sur']);
      expect(comp().sedeId).toBe(3);
      fixture.destroy();
      localStorage.setItem(CLAVE_ULTIMA_SEDE, '8');
      cargar(DOS);
      expect(comp().sedeId).toBe(8);
      fixture.destroy();
      localStorage.setItem(CLAVE_ULTIMA_SEDE, '99');
      cargar(DOS);
      expect(comp().sedeId).toBe(3);
    });

    it('cambiar de sede la guarda (solo su id), vacía lo de la anterior y relee todo de la nueva', () => {
      cargar(DOS);
      const sede = el().querySelector<HTMLSelectElement>('#caja-sede')!;
      sede.value = sede.options[1].value;
      sede.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(localStorage.getItem(CLAVE_ULTIMA_SEDE)).toBe('8');
      expect(guardarEnStorage.calls.allArgs()).toEqual([[CLAVE_ULTIMA_SEDE, '8']]);
      // Lo de Norte ya no se ve mientras llega lo de Sur.
      expect(el().querySelector('.caja__profesor')).toBeNull();
      expect(texto(pestana('recibir'))).toBe('Por recibir');
      expect(texto(el().querySelector('.caja__cifra'))).toContain('—');
      const [resumen, movs, pendientes, entregas] = ['resumen', 'movimientos', 'pendiente-entrega', 'entregas'].map((q) => peticiones(caja(8, q)));
      [resumen, movs, pendientes, entregas].forEach((l) => expect(l.length).toBe(1));
      expect(peticiones(caja(3, 'pendiente-entrega')).length).toBe(0);
      resumen[0].flush({ ...RESUMEN, sedeId: 8 });
      movs[0].flush([]);
      pendientes[0].flush([profesorVista({ usuarioId: 12, nombre: 'Profe Sur' })]);
      entregas[0].flush({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 25 });
      fixture.detectChanges();
      expect(texto(profesor(12))).toContain('Profe Sur');
      expect(texto(pestana('recibir'))).toBe('Por recibir 1');
    });

    it('cambiar de sede con «Recibir entrega» abierto lo cierra sin enviar; con el envío en vuelo, sede y periodo no se pueden cambiar', () => {
      cargar(DOS);
      abrirRecibir();
      const sede = el().querySelector<HTMLSelectElement>('#caja-sede')!;
      sede.value = sede.options[1].value;
      sede.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(formEntrega()).toBeNull();
      expect(peticiones(caja(3, 'entregas'), 'POST').length).toBe(0);
      expect(peticiones(caja(8, 'entregas'), 'POST').length).toBe(0);
      responderSede(8);
      const form = abrirRecibir();
      boton(form, 'Recibir 45,00 €').click();
      fixture.detectChanges();
      expect(peticiones(caja(8, 'entregas'), 'POST').length).toBe(1);
      expect(el().querySelector<HTMLSelectElement>('#caja-sede')!.disabled).toBeTrue();
      expect(el().querySelector<HTMLSelectElement>('#caja-periodo')!.disabled).toBeTrue();
    });
  });

  it('sin sedes: el texto de hoy, sin lecturas de caja', () => {
    cargar([]);
    expect(texto(el().querySelector('.pn-bloque-vacio'))).toContain('No hay ninguna sede cuya caja puedas llevar.');
    expect(http.match((r) => r.url.includes('/caja/')).length).toBe(0);
  });

  it('error al leer las sedes: el mensaje del servidor y «Reintentar», que vuelve a pedirlas', () => {
    crear();
    peticiones(SEDES)[0].flush({ mensaje: 'Sin permiso para la caja' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    const error = el().querySelector('.pn-bloque-error')!;
    expect(texto(error)).toContain('Sin permiso para la caja');
    boton(error, 'Reintentar').click();
    expect(peticiones(SEDES).length).toBe(1);
  });

  it('error al leer los datos: el mensaje del servidor y «Reintentar», que relee todo', () => {
    crear();
    peticiones(SEDES)[0].flush({ porSede: [{ sedeId: 5, nombre: 'Sede Prueba' }] });
    peticiones(caja(5, 'pendiente-entrega'))[0].flush({ mensaje: 'La sede no es de tu escuela' }, { status: 400, statusText: 'Bad' });
    responderSede(5);
    const error = el().querySelector('.caja__error-datos')!;
    expect(texto(error)).toContain('La sede no es de tu escuela');
    boton(error, 'Reintentar').click();
    fixture.detectChanges();
    ['resumen', 'movimientos', 'pendiente-entrega', 'entregas'].forEach((q) => expect(peticiones(caja(5, q)).length).withContext(q).toBe(1));
  });

  it('si fallan los pendientes y las entregas, solo el error con «Reintentar», sin esqueleto debajo; al reintentar vuelve mientras carga', () => {
    crear();
    peticiones(SEDES)[0].flush({ porSede: [{ sedeId: 5, nombre: 'Sede Prueba' }] });
    peticiones(caja(5, 'pendiente-entrega'))[0].flush({ mensaje: 'Error' }, { status: 500, statusText: 'Error' });
    peticiones(caja(5, 'entregas'))[0].flush({ mensaje: 'Error' }, { status: 500, statusText: 'Error' });
    responderSede(5);
    jasmine.clock().tick(300);
    fixture.detectChanges();
    expect(el().querySelector('.caja__error-datos')).not.toBeNull();
    expect(panel().querySelector('pn-esqueleto')).toBeNull();
    elegir('entregas');
    expect(panel().querySelector('pn-esqueleto')).toBeNull();
    boton(el().querySelector('.caja__error-datos')!, 'Reintentar').click();
    fixture.detectChanges();
    expect(el().querySelector('.caja__error-datos')).toBeNull();
    expect(panel().querySelector('pn-esqueleto')).not.toBeNull();
    elegir('recibir');
    expect(panel().querySelector('pn-esqueleto')).not.toBeNull();
    responderSede(5);
    expect(panel().querySelector('pn-esqueleto')).toBeNull();
    expect(profesor()).not.toBeNull();
  });

  it('tras cambiar de sede, las respuestas en vuelo de la anterior (pendientes, entregas y periodo) no se pintan ni tocan la nueva', () => {
    crear();
    peticiones(SEDES)[0].flush({ porSede: [{ sedeId: 8, nombre: 'Sur' }, { sedeId: 3, nombre: 'Norte' }] });
    fixture.detectChanges();
    const viejas = ['resumen', 'movimientos', 'pendiente-entrega', 'entregas'].map((q) => peticiones(caja(3, q)));
    viejas.forEach((l) => expect(l.length).toBe(1));
    const sede = el().querySelector<HTMLSelectElement>('#caja-sede')!;
    sede.value = '8';
    sede.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    // Lo de Norte ya no puede llegar: se cancela al elegir Sur.
    viejas.forEach(([r]) => expect(r.cancelled).withContext(r.request.url).toBeTrue());
    expect(comp().pendientes).toBeNull();
    expect(comp().entregas).toBeNull();
    expect(comp().resumen).toBeNull();
    expect(comp().movimientos).toBeNull();
    expect(comp().errorDatos).toBeNull();
    expect(comp().cargandoPendientes).toBeTrue();
    responderSede(8, { pendientes: [profesorVista({ usuarioId: 12, nombre: 'Profe Sur' })], entregas: [], resumen: { saldoCaja: 7 } });
    expect(el().querySelectorAll('.caja__profesor').length).toBe(1);
    expect(texto(profesor(12))).toContain('Profe Sur');
    expect(texto(el().querySelector('.caja__cifra'))).toContain('7,00 €');
    expect(texto(pestana('entregas'))).toBe('Entregas 0');
    expect(comp().cargandoPendientes).toBeFalse();
    expect(comp().cargandoEntregas).toBeFalse();
    expect(comp().cargandoResumen).toBeFalse();
  });

  it('esqueleto a los 300 ms mientras cargan las sedes y mientras llega cada pestaña', () => {
    crear();
    jasmine.clock().tick(300);
    fixture.detectChanges();
    expect(el().querySelector('pn-esqueleto [role="status"]')).not.toBeNull();
    peticiones(SEDES)[0].flush({ porSede: [{ sedeId: 5, nombre: 'Sede Prueba' }] });
    fixture.detectChanges();
    jasmine.clock().tick(300);
    fixture.detectChanges();
    expect(panel().querySelector('pn-esqueleto')).not.toBeNull();
    responderSede(5);
    expect(panel().querySelector('pn-esqueleto')).toBeNull();
  });

  // ------------------------------------------------------------------ Por recibir

  it('Por recibir: profesor, cobros, importe y desde cuándo, con el aviso de más de 7 días', () => {
    cargar(undefined, { pendientes: [PROFESOR, profesorVista({ usuarioId: 10, nombre: 'Profe Nueva', cobroMasAntiguo: new Date(2026, 9, 16).toISOString(), numeroCobros: 1, importe: 1240 })] });
    expect(texto(profesor(9))).toContain('Profe Prueba');
    expect(texto(profesor(9))).toContain('2 cobros');
    expect(texto(profesor(9))).toContain('45,00 €');
    expect(texto(profesor(9).querySelector('.caja__desde'))).toBe('Desde hace 9 días');
    expect(profesor(9).querySelector('.caja__desde')!.getAttribute('data-aviso')).toBe('true');
    expect(texto(profesor(10))).toContain('1 cobro');
    expect(texto(profesor(10))).toContain('1.240,00 €');
    expect(profesor(10).querySelector('.caja__desde')!.getAttribute('data-aviso')).toBeNull();
  });

  it('Por recibir vacío: el texto de hoy', () => {
    cargar(undefined, { pendientes: [] });
    expect(texto(panel().querySelector('.pn-bloque-vacio'))).toContain('Ningún profesor tiene efectivo pendiente de entregar.');
  });

  describe('«Recibir entrega»', () => {
    it('se abre bajo el profesor con todos los cobros marcados, «debería entregar» y lo contado prefijado; el foco entra', () => {
      cargar();
      document.body.appendChild(el());
      const disparador = boton(profesor(), 'Recibir entrega');
      disparador.click();
      fixture.detectChanges();
      const form = formEntrega()!;
      expect(profesor().contains(form)).toBeTrue();
      expect(disparador.getAttribute('aria-expanded')).toBe('true');
      expect(disparador.getAttribute('aria-controls')).toBe(form.id);
      const casillas = Array.from(form.querySelectorAll<HTMLInputElement>('.caja__cobro input[type="checkbox"]'));
      expect(casillas.map((c) => c.checked)).toEqual([true, true]);
      expect(texto(form.querySelector('.caja__cobro'))).toContain('MENSUALIDAD');
      expect(texto(form.querySelector('.caja__cobro'))).toContain('Ana Prueba');
      expect(texto(form.querySelector('.caja__deberia'))).toBe('Debería entregar 45,00 €');
      expect(form.querySelector<HTMLInputElement>('.caja__contado')!.value).toBe('45,00');
      expect(form.contains(document.activeElement)).toBeTrue();
      expect(boton(form, 'Recibir 45,00 €').disabled).toBeFalse();
      el().remove();
    });

    it('falta: el aviso de hoy y la nota obligatoria; desmarcar recalcula y lo contado sigue a lo declarado si no se tocó', () => {
      cargar();
      let form = abrirRecibir();
      const todos = form.querySelector<HTMLInputElement>('input[aria-label="Seleccionar todos los cobros"]')!;
      const segundo = form.querySelectorAll<HTMLInputElement>('.caja__cobro input[type="checkbox"]')[1];
      segundo.click();
      fixture.detectChanges();
      expect(todos.indeterminate).toBeTrue();
      expect(texto(form.querySelector('.caja__deberia'))).toBe('Debería entregar 30,00 €');
      expect(form.querySelector<HTMLInputElement>('.caja__contado')!.value).toBe('30,00');
      segundo.click();
      fixture.detectChanges();
      escribirEn(form.querySelector<HTMLInputElement>('.caja__contado')!, '44');
      form = formEntrega()!;
      expect(texto(form.querySelector('.caja__aviso-cuadre'))).toBe('Faltan 1,00 €. Se apuntará un faltante en la caja.');
      expect(texto(form.querySelector('label[for="' + form.querySelector('textarea')!.id + '"]'))).toContain('obligatoria');
      expect(form.querySelector('textarea')!.required).toBeTrue();
      expect(boton(form, 'Recibir 44,00 €').disabled).toBeTrue();
      escribirEn(form.querySelector('textarea')!, '  faltaba un euro ');
      expect(boton(form, 'Recibir 44,00 €').disabled).toBeFalse();
      // Con lo contado a mano, desmarcar todo no lo pisa.
      todos.click();
      fixture.detectChanges();
      expect(form.querySelector<HTMLInputElement>('.caja__contado')!.value).toBe('44');
      expect(boton(form, 'Recibir 44,00 €').disabled).toBeTrue();
    });

    it('importe no válido: el aviso de hoy y sin enviar', () => {
      cargar();
      const form = abrirRecibir();
      escribirEn(form.querySelector<HTMLInputElement>('.caja__contado')!, '12,345');
      expect(texto(form.querySelector('.caja__aviso-cuadre'))).toBe('Escribe un importe válido, por ejemplo 45,50.');
      expect(form.querySelector('.caja__contado')!.getAttribute('aria-invalid')).toBe('true');
      expect(boton(form, 'Recibir 0,00 €').disabled).toBeTrue();
    });

    it('POST /caja/sedes/5/entregas con los cobros marcados, lo contado y la nota recortada; ocupado sin doble envío; luego aviso y relee todo', () => {
      cargar();
      const form = abrirRecibir();
      escribirEn(form.querySelector<HTMLInputElement>('.caja__contado')!, '46,5');
      escribirEn(form.querySelector('textarea')!, ' sobraba ');
      const enviar = boton(form, 'Recibir 46,50 €');
      enviar.click();
      fixture.detectChanges();
      enviar.click();
      form.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      const posts = peticiones(caja(5, 'entregas'), 'POST');
      expect(posts.length).toBe(1);
      const [post] = posts;
      expect(post.request.body).toEqual({ entregadoPorUsuarioId: 9, cobroIds: [1, 2], importeContado: 46.5, nota: 'sobraba' });
      expect(post.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      expect(enviar.getAttribute('aria-busy')).toBe('true');
      expect(texto(enviar)).toBe('Recibiendo…');
      post.flush(ENTREGA);
      pintar();
      expect(formEntrega()).toBeNull();
      expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Entrega de Profe Prueba recibida' }]);
      ['resumen', 'movimientos', 'pendiente-entrega', 'entregas'].forEach((q) => expect(peticiones(caja(5, q)).length).withContext(q).toBe(1));
    });

    it('el cuerpo y la nota obligatoria coinciden con los de hoy en cada caso', () => {
      const casos: Array<{ quitar: number[]; contado: string | null; nota: string }> = [
        { quitar: [], contado: null, nota: '' },
        { quitar: [2], contado: null, nota: '   ' },
        { quitar: [], contado: '45', nota: '' },
        { quitar: [], contado: '44,99', nota: 'x' },
        { quitar: [], contado: '44,99', nota: '' },
        { quitar: [1], contado: '1.015,00', nota: ' mucho ' },
        { quitar: [], contado: '0', nota: 'devuelve' },
        { quitar: [1, 2], contado: '5', nota: 'nada' },
        { quitar: [], contado: '45.5', nota: 'punto' },
      ];
      cargar(undefined, { pendientes: [profesorVista({ cobros: [cobroVista(), cobroVista({ cobroId: 2, importe: 15.005 })] })] });
      for (const caso of casos) {
        const form = abrirRecibir();
        caso.quitar.forEach((id) => {
          form.querySelector<HTMLInputElement>(`.caja__cobro input[data-cobro="${id}"]`)!.click();
          fixture.detectChanges();
        });
        if (caso.contado !== null) {
          escribirEn(form.querySelector<HTMLInputElement>('.caja__contado')!, caso.contado);
        }
        escribirEn(form.querySelector('textarea')!, caso.nota);
        expect(comp().puedeRecibir()).withContext(JSON.stringify(caso)).toBe(valorDeHoy(HOY));
        expect(comp().calculoEntrega?.notaObligatoria).withContext(JSON.stringify(caso)).toBe(valorDeHoy(HOY));
        comp().recibirEntrega();
        fixture.detectChanges();
        const posts = peticiones(caja(5, 'entregas'), 'POST');
        const cuerpoHoy = valorDeHoy(HOY);
        if (cuerpoHoy !== null) {
          expect(posts.length).withContext(JSON.stringify(caso)).toBe(1);
          expect(posts[0].request.body).withContext(JSON.stringify(caso)).toEqual(cuerpoHoy);
          posts[0].flush({ mensaje: 'x' }, { status: 400, statusText: 'Bad' });
          fixture.detectChanges();
        } else {
          expect(posts.length).withContext(JSON.stringify(caso)).toBe(0);
        }
        comp().cerrarEntrega();
        fixture.detectChanges();
      }
    });

    it('un 400 enseña el `mensaje` en el formulario, que sigue abierto', () => {
      cargar();
      const form = abrirRecibir();
      boton(form, 'Recibir 45,00 €').click();
      peticiones(caja(5, 'entregas'), 'POST')[0].flush({ mensaje: 'Algún cobro no es de esta sede.' }, { status: 400, statusText: 'Bad' });
      fixture.detectChanges();
      expect(texto(formEntrega()!.querySelector('[role="alert"]'))).toBe('Algún cobro no es de esta sede.');
      expect(boton(formEntrega()!, 'Recibir 45,00 €').getAttribute('aria-busy')).toBeNull();
    });

    it('un 409 cierra, relee la caja y avisa con el `mensaje` del servidor y lo que hay que hacer', () => {
      cargar();
      const form = abrirRecibir();
      boton(form, 'Recibir 45,00 €').click();
      peticiones(caja(5, 'entregas'), 'POST')[0].flush(
        { mensaje: 'Algún cobro ya está incluido en una entrega de caja.' }, { status: 409, statusText: 'Conflict' }
      );
      pintar();
      expect(formEntrega()).toBeNull();
      ['resumen', 'movimientos', 'pendiente-entrega', 'entregas'].forEach((q) => expect(peticiones(caja(5, q)).length).withContext(q).toBe(1));
      expect(avisos()).toEqual([
        { tipo: 'error', texto: `La entrega ha cambiado. Algún cobro ya está incluido en una entrega de caja. ${TEXTO_ENTREGA_CAMBIADA}` },
      ]);
    });

    it('«Cancelar» y Esc cierran sin enviar y el foco vuelve a «Recibir entrega»; abrir otro profesor cierra el anterior', () => {
      cargar(undefined, { pendientes: [PROFESOR, profesorVista({ usuarioId: 10, nombre: 'Otra' })] });
      document.body.appendChild(el());
      let form = abrirRecibir();
      boton(form, 'Cancelar').click();
      fixture.detectChanges();
      jasmine.clock().tick(1);
      expect(formEntrega()).toBeNull();
      expect(document.activeElement).toBe(boton(profesor(), 'Recibir entrega'));
      form = abrirRecibir();
      form.querySelector<HTMLInputElement>('.caja__contado')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      fixture.detectChanges();
      jasmine.clock().tick(1);
      expect(formEntrega()).toBeNull();
      expect(document.activeElement).toBe(boton(profesor(), 'Recibir entrega'));
      abrirRecibir(9);
      abrirRecibir(10);
      expect(el().querySelectorAll('.caja__entrega').length).toBe(1);
      expect(profesor(10).contains(formEntrega())).toBeTrue();
      expect(peticiones(caja(5, 'entregas'), 'POST').length).toBe(0);
      el().remove();
    });

    describe('abierto mientras se releen los pendientes', () => {
      const AVISO_CAMBIO = 'Los cobros pendientes de Profe Prueba han cambiado. Vuelve a abrir «Recibir entrega».';
      const CON_UNO_MAS = profesorVista({
        numeroCobros: 3, importe: 65,
        cobros: [...profesorVista().cobros, cobroVista({ cobroId: 3, cargoId: 13, concepto: 'EXAMEN', importe: 20 })],
      });
      const infos = (): string[] => avisos().filter((a) => a.tipo === 'info').map((a) => a.texto);
      const enviar = (): HTMLButtonElement => formEntrega()!.querySelector<HTMLButtonElement>('.caja__recibir-enviar')!;

      it('el propietario anula una entrega de X con su formulario abierto: sus cobros cambian, se cierra sin enviar y avisa', async () => {
        cargar();
        abrirRecibir();
        elegir('entregas');
        filaEntrega(7).querySelector<HTMLButtonElement>('button')!.click();
        await esperar();
        escribirDialogo(dialogo()!, 'contada dos veces');
        await confirmarDialogo(dialogo()!);
        peticiones(`${API}/caja/entregas/7/anular`, 'POST')[0].flush({ ...ENTREGA, estado: 'ANULADA' });
        await esperar();
        responderSede(5, { pendientes: [CON_UNO_MAS], entregas: [{ ...ENTREGA, estado: 'ANULADA' }] });
        pintar();
        expect(comp().formEntrega).toBeNull();
        expect(formEntrega()).toBeNull();
        expect(infos()).toEqual([AVISO_CAMBIO]);
        expect(peticiones(caja(5, 'entregas'), 'POST').length).toBe(0);
        // Al volver a abrirlo, los tres cobros entran en el cálculo y en el envío.
        elegir('recibir');
        const form = abrirRecibir();
        expect(Array.from(form.querySelectorAll<HTMLInputElement>('.caja__cobro input[type="checkbox"]')).map((c) => c.checked)).toEqual([true, true, true]);
        expect(texto(form.querySelector('.caja__deberia'))).toBe('Debería entregar 65,00 €');
        boton(form, 'Recibir 65,00 €').click();
        expect(peticiones(caja(5, 'entregas'), 'POST')[0].request.body).toEqual({
          entregadoPorUsuarioId: 9, cobroIds: [1, 2, 3], importeContado: 65, nota: null,
        });
      });

      it('si cambia el importe de un cobro o X ya no tiene pendientes, también se cierra sin enviar y avisa', () => {
        cargar();
        abrirRecibir();
        comp().recargarTodo();
        responderSede(5, { pendientes: [profesorVista({ cobros: [cobroVista(), cobroVista({ cobroId: 2, importe: 16 })] })] });
        pintar();
        expect(formEntrega()).toBeNull();
        expect(infos()).toEqual([AVISO_CAMBIO]);
        TestBed.inject(AvisosService).cerrarTodos();
        abrirRecibir();
        comp().recargarTodo();
        responderSede(5, { pendientes: [profesorVista({ usuarioId: 10, nombre: 'Otra' })] });
        pintar();
        expect(formEntrega()).toBeNull();
        expect(infos()).toEqual([AVISO_CAMBIO]);
        expect(peticiones(caja(5, 'entregas'), 'POST').length).toBe(0);
      });

      it('al cerrarse, el foco vuelve a «Recibir entrega» de X o, si X ya no está, a la pestaña', () => {
        cargar();
        document.body.appendChild(el());
        abrirRecibir();
        expect(formEntrega()!.contains(document.activeElement)).toBeTrue();
        comp().recargarTodo();
        responderSede(5, { pendientes: [CON_UNO_MAS] });
        pintar();
        jasmine.clock().tick(1);
        expect(document.activeElement).toBe(boton(profesor(), 'Recibir entrega'));
        abrirRecibir();
        comp().recargarTodo();
        responderSede(5, { pendientes: [] });
        pintar();
        jasmine.clock().tick(1);
        expect(document.activeElement).toBe(pestana('recibir'));
        el().remove();
      });

      it('si los cobros de X no cambian, sigue abierto tal cual y envía los mismos cobros y lo contado; durante la recarga no se envía', () => {
        cargar();
        const form = abrirRecibir();
        form.querySelector<HTMLInputElement>('.caja__cobro input[data-cobro="2"]')!.click();
        fixture.detectChanges();
        escribirEn(form.querySelector<HTMLInputElement>('.caja__contado')!, '29');
        escribirEn(form.querySelector('textarea')!, 'falta 1');
        expect(enviar().disabled).toBeFalse();
        comp().recargarTodo();
        fixture.detectChanges();
        expect(enviar().disabled).toBeTrue();
        expect(boton(profesor(), 'Recibir entrega').disabled).toBeTrue();
        comp().recibirEntrega();
        expect(peticiones(caja(5, 'entregas'), 'POST').length).toBe(0);
        // Los mismos ids e importes, en otro orden y en objetos nuevos.
        const [uno, dos] = profesorVista().cobros;
        responderSede(5, { pendientes: [profesorVista({ cobros: [dos, uno] })] });
        pintar();
        expect(infos()).toEqual([]);
        expect(formEntrega()).not.toBeNull();
        expect(formEntrega()!.querySelector<HTMLInputElement>('.caja__contado')!.value).toBe('29');
        expect(Array.from(formEntrega()!.querySelectorAll<HTMLInputElement>('.caja__cobro input[type="checkbox"]')).map((c) => c.checked)).toEqual([true, false]);
        expect(texto(formEntrega()!.querySelector('.caja__deberia'))).toBe('Debería entregar 30,00 €');
        expect(enviar().disabled).toBeFalse();
        enviar().click();
        const posts = peticiones(caja(5, 'entregas'), 'POST');
        expect(posts.length).toBe(1);
        expect(posts[0].request.body).toEqual({ entregadoPorUsuarioId: 9, cobroIds: [1], importeContado: 29, nota: 'falta 1' });
      });

      it('la lista, las casillas y «Seleccionar todos» del formulario salen de sus propios cobros, los del cálculo y el envío', () => {
        cargar();
        abrirRecibir();
        comp().recargarTodo();
        // Mismos ids e importes, otro concepto y un cobro de más en la fila: el formulario sigue con los suyos.
        responderSede(5, { pendientes: [profesorVista({ cobros: [cobroVista({ concepto: 'OTRO CONCEPTO' }), profesorVista().cobros[1]] })] });
        pintar();
        const form = formEntrega()!;
        expect(texto(form.querySelector('.caja__cobro'))).toContain('MENSUALIDAD');
        expect(texto(form.querySelector('.caja__cobro'))).not.toContain('OTRO CONCEPTO');
        comp().formEntrega!.profesor = profesorVista({ cobros: [] });
        comp().formEntrega!.seleccionados = new Set();
        fixture.detectChanges();
        expect(formEntrega()!.querySelectorAll('.caja__cobro').length).toBe(0);
        expect(formEntrega()!.querySelector<HTMLInputElement>('input[aria-label="Seleccionar todos los cobros"]')!.disabled).toBeTrue();
      });
    });
  });

  // ------------------------------------------------------------------ Entregas

  it('Entregas: fecha, de, recibió, contado, diferencia y nota; anuladas tachadas con motivo y correcciones; pie y páginas', () => {
    cargar(undefined, { entregas: [ENTREGA, ANULADA, entregaVista({ id: 9, autoentrega: true, nota: null })], total: 51, paginas: 3 });
    elegir('entregas');
    expect(texto(pestana('entregas'))).toBe('Entregas 51');
    const f = filaEntrega(7);
    expect(texto(f)).toContain('12/10/2026');
    expect(texto(f)).toContain('Profe Prueba');
    expect(texto(f)).toContain('Gestora Prueba');
    expect(texto(f)).toContain('19,00 €');
    expect(texto(f)).toContain('falta 1,00 €');
    expect(texto(f)).toContain('faltaba 1');
    const anulada = filaEntrega(8);
    expect(anulada.getAttribute('data-anulada')).toBe('true');
    expect(texto(anulada)).toContain('Anulada');
    expect(texto(anulada)).toContain('«contada dos veces»');
    expect(texto(anulada.querySelector('[aria-label="Correcciones de la entrega"]'))).toBe('Cobro deshecho +12,00 €');
    expect(getComputedStyle(anulada.querySelector('.caja__tachable')!).textDecorationLine).toContain('line-through');
    expect(texto(filaEntrega(9))).toContain('autoentrega');
    expect(texto(el().querySelector('.caja__mostrando'))).toBe('Mostrando 3 de 51 entregas');
    boton(el().querySelector('.caja__paginacion')!, 'Página 2').click();
    fixture.detectChanges();
    expect(peticiones(caja(5, 'entregas'))[0].request.params.get('page')).toBe('2');
  });

  it('Entregas vacías: el texto de hoy', () => {
    cargar(undefined, { entregas: [] });
    elegir('entregas');
    expect(texto(panel().querySelector('.pn-bloque-vacio'))).toContain('Todavía no se ha recibido ninguna entrega en esta sede.');
  });

  describe('anular (propietario)', () => {
    it('solo el propietario ve «Anular» en la entrega activa y en el movimiento suelto; ni en la anulada ni en el automático', () => {
      cargar(undefined, { entregas: [ENTREGA, ANULADA] });
      elegir('entregas');
      expect(filaEntrega(7).querySelector('button')!.getAttribute('aria-label')).toBe('Anular la entrega de Profe Prueba del 12/10/2026');
      expect(filaEntrega(8).querySelector('button')).toBeNull();
      expect(el().querySelectorAll('button[aria-label^="Pedir al propietario"]').length).toBe(0);
      elegir('movimientos');
      expect(filaMovimiento(4).querySelector('.caja__fila-accion button')).toBeNull();
      expect(texto(filaMovimiento(5).querySelector('.caja__fila-accion button'))).toBe('Anular');
    });

    it('la entrega: el diálogo de hoy con motivo obligatorio (500), destructivo; POST /caja/entregas/7/anular {motivo}; aviso y relee todo', async () => {
      cargar();
      elegir('entregas');
      filaEntrega(7).querySelector<HTMLButtonElement>('button')!.click();
      await esperar();
      const d = dialogo()!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Anular entrega');
      expect(texto(d)).toContain('Entrega de Profe Prueba por 19,00 €. Sus cobros vuelven a estar pendientes de entregar.');
      expect(d.querySelector('textarea')!.getAttribute('maxlength')).toBe('500');
      expect(texto(d.querySelector('.pn-dialogo__cancelar'))).toBe('Volver');
      await confirmarDialogo(d);
      expect(texto(d.querySelector('.pn-dialogo__error'))).toBe('Indica el motivo de la anulación');
      expect(peticiones(`${API}/caja/entregas/7/anular`, 'POST').length).toBe(0);
      escribirDialogo(d, '  contada dos veces ');
      await confirmarDialogo(d);
      const [post] = peticiones(`${API}/caja/entregas/7/anular`, 'POST');
      expect(post.request.body).toEqual({ motivo: 'contada dos veces' });
      expect(d.querySelector('.pn-dialogo__accion')!.getAttribute('aria-busy')).toBe('true');
      post.flush({ ...ENTREGA, estado: 'ANULADA' });
      await esperar();
      expect(dialogo()).toBeNull();
      expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Entrega anulada' }]);
      ['resumen', 'movimientos', 'pendiente-entrega', 'entregas'].forEach((q) => expect(peticiones(caja(5, q)).length).withContext(q).toBe(1));
    });

    it('«Volver» no llama; un 409 enseña el `mensaje` y relee todo', async () => {
      cargar();
      elegir('entregas');
      filaEntrega(7).querySelector<HTMLButtonElement>('button')!.click();
      await esperar();
      (dialogo()!.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement).click();
      await esperar();
      expect(http.match((r) => r.method === 'POST').length).toBe(0);
      filaEntrega(7).querySelector<HTMLButtonElement>('button')!.click();
      await esperar();
      escribirDialogo(dialogo()!, 'x');
      await confirmarDialogo(dialogo()!);
      peticiones(`${API}/caja/entregas/7/anular`, 'POST')[0].flush({ mensaje: 'La entrega ya está anulada.' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(avisos()).toEqual([{ tipo: 'error', texto: 'La entrega ya está anulada.' }]);
      expect(peticiones(caja(5, 'pendiente-entrega')).length).toBe(1);
    });

    it('el movimiento: POST /caja/movimientos/5/anular {motivo}; aviso y relee solo el periodo', async () => {
      cargar();
      elegir('movimientos');
      filaMovimiento(5).querySelector<HTMLButtonElement>('.caja__fila-accion button')!.click();
      await esperar();
      const d = dialogo()!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Anular movimiento');
      expect(texto(d)).toContain('Otro de 10,00 €. Deja de contar en la caja.');
      escribirDialogo(d, 'repetido');
      await confirmarDialogo(d);
      const [post] = peticiones(`${API}/caja/movimientos/5/anular`, 'POST');
      expect(post.request.body).toEqual({ motivo: 'repetido' });
      post.flush({ ...SUELTO, estado: 'ANULADO' });
      await esperar();
      expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Movimiento anulado' }]);
      expect(peticiones(caja(5, 'resumen')).length).toBe(1);
      expect(peticiones(caja(5, 'movimientos')).length).toBe(1);
      expect(peticiones(caja(5, 'pendiente-entrega')).length).toBe(0);
    });
  });

  describe('pedir anulación (gestión)', () => {
    beforeEach(() => (esAdmin = false));

    it('la gestión ve «Pedir anulación» en la entrega activa y en el movimiento suelto, no «Anular»', () => {
      cargar(undefined, { entregas: [ENTREGA, ANULADA] });
      elegir('entregas');
      const b = filaEntrega(7).querySelector('button')!;
      expect(texto(b)).toBe('Pedir anulación');
      expect(b.getAttribute('aria-label')).toBe('Pedir al propietario que anule la entrega de Profe Prueba del 12/10/2026');
      expect(filaEntrega(8).querySelector('button')).toBeNull();
      elegir('movimientos');
      expect(filaMovimiento(4).querySelector('.caja__fila-accion button')).toBeNull();
      expect(plano(filaMovimiento(5).querySelector('.caja__fila-accion button')!.getAttribute('aria-label'))).toBe(
        'Pedir al propietario que anule Otro de 10,00 €'
      );
      expect(Array.from(el().querySelectorAll('button')).some((x) => texto(x) === 'Anular')).toBeFalse();
    });

    it('la entrega: el diálogo de hoy con sus datos y motivo obligatorio; POST /solicitudes {tipo, entregaId, motivo}', async () => {
      cargar();
      elegir('entregas');
      filaEntrega(7).querySelector<HTMLButtonElement>('button')!.click();
      await esperar();
      const d = dialogo()!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Pedir al propietario que anule esta entrega');
      expect(texto(d)).toContain('Entregó: Profe Prueba');
      expect(texto(d)).toContain('Contado: 19,00 €');
      expect(texto(d)).toContain('Mientras tanto la entrega sigue contando en la caja.');
      expect(d.querySelector('textarea')!.getAttribute('maxlength')).toBe('500');
      await confirmarDialogo(d);
      expect(texto(d.querySelector('.pn-dialogo__error'))).toBe('Escribe el motivo: el propietario lo verá al decidir');
      escribirDialogo(d, ' contada dos veces ');
      await confirmarDialogo(d);
      const [post] = peticiones(SOLICITUDES, 'POST');
      expect(post.request.body).toEqual({ tipo: 'ANULAR_ENTREGA', entregaId: 7, motivo: 'contada dos veces' });
      post.flush({ id: 1, tipo: 'ANULAR_ENTREGA', estado: 'PENDIENTE' });
      await esperar();
      expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Solicitud enviada al propietario' }]);
    });

    it('el movimiento: POST /solicitudes {tipo: ANULAR_MOVIMIENTO, movimientoId, motivo}; un 409 enseña el `mensaje`', async () => {
      cargar();
      elegir('movimientos');
      filaMovimiento(5).querySelector<HTMLButtonElement>('.caja__fila-accion button')!.click();
      await esperar();
      const d = dialogo()!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Pedir al propietario que anule este movimiento');
      expect(texto(d)).toContain('Importe: +10,00 €');
      expect(texto(d)).toContain('Nota: cambio');
      escribirDialogo(d, 'repetido');
      await confirmarDialogo(d);
      const [post] = peticiones(SOLICITUDES, 'POST');
      expect(post.request.body).toEqual({ tipo: 'ANULAR_MOVIMIENTO', movimientoId: 5, motivo: 'repetido' });
      post.flush({ mensaje: 'Ya hay una solicitud pendiente sobre este movimiento.' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(avisos()).toEqual([{ tipo: 'error', texto: 'Ya hay una solicitud pendiente sobre este movimiento.' }]);
    });
  });

  // ------------------------------------------------------------------ Movimientos

  it('Movimientos: fecha, tipo, alumno, importe con signo y nota; anulados tachados con su motivo; vacío de hoy', () => {
    cargar(undefined, {
      movimientos: [
        FALTANTE, movimientoVista({ id: 6, tipo: 'DEVOLUCION', importe: -15, alumnoId: 21, estado: 'ANULADO', motivoAnulacion: 'mal apuntado', nota: 'devuelto' }),
      ],
    });
    elegir('movimientos');
    expect(texto(filaMovimiento(4))).toContain('Faltante');
    expect(texto(filaMovimiento(4))).toContain('−1,00 €');
    const anulado = filaMovimiento(6);
    expect(anulado.getAttribute('data-anulada')).toBe('true');
    expect(texto(anulado)).toContain('Anulado');
    expect(texto(anulado)).toContain('Ana Prueba');
    expect(texto(anulado)).toContain('Anulado: «mal apuntado»');
    expect(alumnos).toHaveBeenCalledWith(true);
    fixture.destroy();
    cargar(undefined, { movimientos: [] });
    elegir('movimientos');
    expect(texto(panel().querySelector('.pn-bloque-vacio'))).toContain('No hay movimientos en Octubre 2026.');
  });

  describe('«Apuntar movimiento»', () => {
    function abrirMovimiento(): HTMLElement {
      elegir('movimientos');
      boton(panel(), 'Apuntar movimiento').click();
      fixture.detectChanges();
      return formMovimiento()!;
    }

    it('tipo, importe, alumno y nota como hoy; devolución exige alumno y sale con signo negativo', () => {
      cargar();
      const form = abrirMovimiento();
      const tipo = form.querySelector<HTMLSelectElement>('#caja-mov-tipo')!;
      expect(Array.from(tipo.options).map((o) => texto(o))).toEqual(['Devolución', 'Corrección de error', 'Sobrante', 'Faltante', 'Otro']);
      expect(texto(tipo.selectedOptions[0])).toBe('Devolución');
      expect(texto(form.querySelector('.caja__mov-sede'))).toBe('Sede Prueba');
      escribirEn(form.querySelector<HTMLInputElement>('#caja-mov-importe')!, '--15');
      expect(texto(form.querySelector('#caja-mov-importe-ayuda'))).toBe(
        'Escribe un importe distinto de cero con un solo signo delante, por ejemplo 12,50 o -12,50.'
      );
      escribirEn(form.querySelector<HTMLInputElement>('#caja-mov-importe')!, '15');
      expect(texto(form.querySelector('#caja-mov-importe-ayuda'))).toBe('Sale de la caja.');
      escribirEn(form.querySelector<HTMLTextAreaElement>('#caja-mov-nota')!, ' devuelto ');
      const enviar = form.querySelector<HTMLButtonElement>('.caja__mov-enviar')!;
      expect(enviar.disabled).toBeTrue();
      const alumno = form.querySelector<HTMLSelectElement>('#caja-mov-alumno')!;
      alumno.selectedIndex = 1;
      alumno.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(enviar.disabled).toBeFalse();
      enviar.click();
      fixture.detectChanges();
      enviar.click();
      fixture.detectChanges();
      const posts = peticiones(caja(5, 'movimientos'), 'POST');
      expect(posts.length).toBe(1);
      expect(posts[0].request.body).toEqual({ tipo: 'DEVOLUCION', importe: -15, alumnoId: 21, nota: 'devuelto' });
      posts[0].flush(movimientoVista({ id: 9 }));
      pintar();
      expect(formMovimiento()).toBeNull();
      expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Movimiento apuntado' }]);
      expect(peticiones(caja(5, 'resumen')).length).toBe(1);
      expect(peticiones(caja(5, 'movimientos')).length).toBe(1);
      expect(peticiones(caja(5, 'pendiente-entrega')).length).toBe(0);
    });

    it('el cuerpo coincide con el de hoy para cada tipo e importe', () => {
      const casos: Array<{ tipo: TipoMovimientoCaja; importe: string; alumno: number | null; nota: string }> = [
        { tipo: 'DEVOLUCION', importe: '15', alumno: 21, nota: 'a' },
        { tipo: 'DEVOLUCION', importe: '-15', alumno: 22, nota: 'b' },
        { tipo: 'DEVOLUCION', importe: '15', alumno: null, nota: 'sin alumno' },
        { tipo: 'FALTANTE', importe: '+2,5', alumno: 21, nota: 'c' },
        { tipo: 'SOBRANTE', importe: '−3', alumno: null, nota: 'd' },
        { tipo: 'ERROR', importe: '-1.240,00', alumno: 21, nota: 'e' },
        { tipo: 'ERROR', importe: '0', alumno: null, nota: 'cero' },
        { tipo: 'OTRO', importe: '7,05', alumno: null, nota: '  ' },
        { tipo: 'OTRO', importe: '7,05', alumno: null, nota: ' f ' },
      ];
      cargar();
      elegir('movimientos');
      for (const caso of casos) {
        comp().abrirMovimiento();
        fixture.detectChanges();
        const form = formMovimiento()!;
        const tipo = form.querySelector<HTMLSelectElement>('#caja-mov-tipo')!;
        tipo.selectedIndex = ['DEVOLUCION', 'ERROR', 'SOBRANTE', 'FALTANTE', 'OTRO'].indexOf(caso.tipo);
        tipo.dispatchEvent(new Event('change'));
        fixture.detectChanges();
        comp().formMovimiento!.alumnoId = caso.alumno;
        escribirEn(form.querySelector<HTMLInputElement>('#caja-mov-importe')!, caso.importe);
        escribirEn(form.querySelector<HTMLTextAreaElement>('#caja-mov-nota')!, caso.nota);
        expect(comp().puedeApuntar()).withContext(JSON.stringify(caso)).toBe(valorDeHoy(HOY));
        comp().apuntarMovimiento();
        fixture.detectChanges();
        const posts = peticiones(caja(5, 'movimientos'), 'POST');
        const cuerpoHoy = valorDeHoy(HOY);
        if (cuerpoHoy !== null) {
          expect(posts.length).withContext(JSON.stringify(caso)).toBe(1);
          expect(posts[0].request.body).withContext(JSON.stringify(caso)).toEqual(cuerpoHoy);
          posts[0].flush({ mensaje: 'x' }, { status: 400, statusText: 'Bad' });
          fixture.detectChanges();
        } else {
          expect(posts.length).withContext(JSON.stringify(caso)).toBe(0);
        }
        comp().cerrarMovimiento();
        fixture.detectChanges();
      }
    });

    it('sobrante y faltante no piden alumno; un error enseña el `mensaje` en el formulario', () => {
      cargar();
      const form = abrirMovimiento();
      const tipo = form.querySelector<HTMLSelectElement>('#caja-mov-tipo')!;
      tipo.selectedIndex = 3;
      tipo.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(form.querySelector('#caja-mov-alumno')).toBeNull();
      escribirEn(form.querySelector<HTMLInputElement>('#caja-mov-importe')!, '2');
      escribirEn(form.querySelector<HTMLTextAreaElement>('#caja-mov-nota')!, 'faltan 2');
      form.querySelector<HTMLButtonElement>('.caja__mov-enviar')!.click();
      const [post] = peticiones(caja(5, 'movimientos'), 'POST');
      expect(post.request.body).toEqual({ tipo: 'FALTANTE', importe: -2, alumnoId: null, nota: 'faltan 2' });
      post.flush({ mensaje: 'La nota es obligatoria.' }, { status: 400, statusText: 'Bad' });
      fixture.detectChanges();
      expect(texto(formMovimiento()!.querySelector('[role="alert"]'))).toBe('La nota es obligatoria.');
    });
  });

  // ------------------------------------------------------------------ privacidad y móvil

  it('ningún dato en la URL; en el almacenamiento, solo el id de la última sede (como hoy)', async () => {
    const router = TestBed.inject(Router);
    const navegar = spyOn(router, 'navigate').and.callThrough();
    const navegarUrl = spyOn(router, 'navigateByUrl').and.callThrough();
    cargar();
    elegir('entregas');
    elegir('movimientos');
    abrirRecibirSiHay();
    expect(navegar).not.toHaveBeenCalled();
    expect(navegarUrl).not.toHaveBeenCalled();
    expect(guardarEnStorage).not.toHaveBeenCalled();

    function abrirRecibirSiHay(): void {
      elegir('recibir');
      abrirRecibir();
    }
  });

  it('a 375 px sin desplazamiento horizontal: profesores, formulario de entrega y tablas se apilan', () => {
    cargar(undefined, { entregas: [ENTREGA, ANULADA] });
    const host = el();
    host.style.display = 'block';
    host.style.width = '375px';
    document.body.appendChild(host);
    fixture.detectChanges();
    const pagina = host.querySelector<HTMLElement>('.caja__pagina')!;
    /** Lo que se sale por la derecha de la página (vacío si nada). */
    const desbordan = (): string[] => {
      const borde = pagina.getBoundingClientRect().right;
      return Array.from(pagina.querySelectorAll<HTMLElement>('*'))
        .filter((e) => e.getClientRects().length && e.getBoundingClientRect().right > borde + 0.5)
        .map((e) => `${e.tagName.toLowerCase()}.${e.className}`);
    };
    abrirRecibir();
    expect(desbordan()).toEqual([]);
    expect(pagina.scrollWidth).toBeLessThanOrEqual(pagina.clientWidth);
    elegir('entregas');
    expect(desbordan()).toEqual([]);
    expect(pagina.scrollWidth).toBeLessThanOrEqual(pagina.clientWidth);
    expect(getComputedStyle(filaEntrega(7)).display).toBe('grid');
    elegir('movimientos');
    boton(panel(), 'Apuntar movimiento').click();
    fixture.detectChanges();
    expect(desbordan()).toEqual([]);
    expect(pagina.scrollWidth).toBeLessThanOrEqual(pagina.clientWidth);
    host.remove();
  });
});
