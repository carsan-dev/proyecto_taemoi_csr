import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { valorDeHoy } from '../../testing/oraculo-hoy';
import { HOY } from './solicitudes.component.hoy';
import { environment } from '../../../environments/environment';
import { EstadoSolicitud, Solicitud, TipoSolicitud } from '../../interfaces/solicitud';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { DEVOLUCION_OTRA_SEDE, accionesDe, devolucionDelProfesor, verboAceptar } from './solicitudes-vista';
import { solicitudVista } from './solicitudes-vista.spec';
import { SolicitudesPanelComponent } from './solicitudes.component';

const BASE = `${environment.apiUrl}/solicitudes`;
const CONTADOR = `${BASE}/contador`;

const encargo = (cambios: Partial<Solicitud> = {}): Solicitud =>
  solicitudVista({
    id: 20, tipo: 'ENCARGO', estado: 'PEDIDO', cobroId: null, productoAlumnoId: 31, concepto: 'DOBOK T-160', motivo: 'Talla 160',
    importe: 25, estadoCobro: 'PENDIENTE', solicitadoPorNombre: 'Profesor Prueba', ...cambios,
  });

const PENDIENTE = solicitudVista();
const RESUELTA = solicitudVista({
  id: 6, estado: 'DENEGADA', respuesta: 'El cobro es correcto', resueltoPorNombre: 'Propietario',
  resueltoEn: new Date(2026, 9, 19, 12, 0).toISOString(),
});
const PEDIDO = encargo();
const ENCARGADO = encargo({ id: 21, estado: 'ENCARGADO', estadoCobro: 'COBRADO' });
const RECIBIDO = encargo({ id: 22, estado: 'RECIBIDO' });
const DEVOLUCION_SEDE = encargo({
  id: 23, estado: 'RECHAZADO', respuesta: 'No hay talla', devolucionPendiente: true, devolucionEnSede: true, estadoCobro: 'COBRADO',
});
const DEVOLUCION_PROFESOR = encargo({
  id: 24, estado: 'RETIRADA', devolucionPendiente: true, devolucionEnSede: false, estadoCobro: 'COBRADO',
});

/** La API manda las fechas como `LocalDateTime` en array; aquí basta con la cadena ISO (la normaliza el servicio). */
const api = (s: Solicitud): any => ({ ...s });

describe('SolicitudesPanelComponent (8b-3i, Solicitudes del panel nuevo, P1 acciones en la fila)', () => {
  let fixture: ComponentFixture<SolicitudesPanelComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let esAdmin: boolean;
  let guardarEnStorage: jasmine.Spy;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');

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
      texto: a.querySelector('.pn-aviso__texto')!.textContent!.replace(/\s+/g, ' ').trim(),
    }));
  }

  const lecturas = (segmento: string): TestRequest[] =>
    http.match((r) => r.method === 'GET' && r.url === BASE && r.params.get('segmento') === segmento);
  const contador = (): TestRequest[] => http.match((r) => r.method === 'GET' && r.url === CONTADOR);

  function responder(segmento: string, lista: Solicitud[]): void {
    lecturas(segmento).forEach((r) => r.flush(lista.map(api)));
    fixture.detectChanges();
  }

  function crear(): void {
    fixture = TestBed.createComponent(SolicitudesPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function cargar(pendientes: Solicitud[] = [PENDIENTE], encargos: Solicitud[] = [PEDIDO]): void {
    crear();
    responder('PENDIENTES', pendientes);
    responder('ENCARGOS', encargos);
  }

  const panel = (): HTMLElement => el().querySelector<HTMLElement>('[role="tabpanel"]:not([hidden])')!;
  const filas = (raiz: ParentNode = panel()): HTMLElement[] => Array.from(raiz.querySelectorAll<HTMLElement>('.sol__fila'));
  const fila = (id: number): HTMLElement => el().querySelector<HTMLElement>(`.sol__fila[data-id="${id}"]`)!;
  const botones = (raiz: ParentNode): string[] =>
    Array.from(raiz.querySelectorAll<HTMLButtonElement>('.sol__acciones button')).map((b) => texto(b));
  const pendientesDe = (raiz: ParentNode = panel()): HTMLElement =>
    raiz.querySelector<HTMLElement>('.sol__lista--principal')!;

  function boton(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => (x.getAttribute('aria-label') ?? '').trim() === t || texto(x) === t
    );
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  function pestana(segmento: string): HTMLButtonElement {
    return el().querySelector<HTMLButtonElement>(`#sol-pestana-${segmento}`)!;
  }

  function elegir(segmento: string): void {
    pestana(segmento).click();
    fixture.detectChanges();
  }

  async function pulsar(raiz: ParentNode, t: string): Promise<HTMLDialogElement | null> {
    boton(raiz, t).click();
    await esperar();
    return dialogo();
  }

  async function confirmarDialogo(d: HTMLDialogElement): Promise<void> {
    (d.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
    await esperar();
  }

  function escribir(d: HTMLDialogElement, valor: string): void {
    const t = d.querySelector<HTMLTextAreaElement>('textarea')!;
    t.value = valor;
    t.dispatchEvent(new Event('input'));
  }

  /** Lo que se relee tras una escritura: los segmentos cargados y el contador de la cabecera. */
  function releer(pendientes: Solicitud[] = [], encargos: Solicitud[] = []): void {
    responder('PENDIENTES', pendientes);
    responder('ENCARGOS', encargos);
  }

  beforeEach(async () => {
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 9, 23, 12, 0));
    quitarTokens = ponerTokensDePrueba();
    esAdmin = true;
    guardarEnStorage = spyOn(Storage.prototype, 'setItem').and.callThrough();
    await TestBed.configureTestingModule({
      imports: [SolicitudesPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthenticationService,
          useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => esAdmin, obtenerRoles: () => of([]) },
        },
        { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => of({}) } },
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
  });

  // ------------------------------------------------------------------ lectura, cabecera y pestañas

  it('al entrar pide Pendientes y Encargos (GET /solicitudes?segmento=…, con ERROR_HANDLED_LOCALLY); Resueltas al elegirla', () => {
    crear();
    const [p] = lecturas('PENDIENTES');
    const [e] = lecturas('ENCARGOS');
    expect(p.request.urlWithParams).toBe(`${BASE}?segmento=PENDIENTES`);
    expect(e.request.urlWithParams).toBe(`${BASE}?segmento=ENCARGOS`);
    expect(p.request.withCredentials).toBeTrue();
    expect(p.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(lecturas('RESUELTAS').length).toBe(0);
    p.flush([api(PENDIENTE)]);
    e.flush([api(PEDIDO)]);
    fixture.detectChanges();

    elegir('RESUELTAS');
    const [r] = lecturas('RESUELTAS');
    expect(r.request.urlWithParams).toBe(`${BASE}?segmento=RESUELTAS`);
    r.flush([api(RESUELTA)]);
    fixture.detectChanges();
    expect(texto(panel())).toContain('Denegada');
    expect(texto(panel())).toContain('El cobro es correcto');
    // Elegirla otra vez no la vuelve a pedir.
    elegir('PENDIENTES');
    elegir('RESUELTAS');
    expect(lecturas('RESUELTAS').length).toBe(0);
    http.verify();
  });

  it('cabecera: título, subtítulo por rol y «Actualizar»; pestañas con su número (Resueltas sin número, como hoy)', () => {
    cargar([PENDIENTE], [PEDIDO, ENCARGADO]);
    expect(texto(el().querySelector('h1'))).toBe('Solicitudes');
    expect(texto(el().querySelector('.sol__subtitulo'))).toBe('Lo que la gestión te pide revertir o anular, y los encargos de material.');
    expect(boton(el(), 'Actualizar')).toBeTruthy();
    const lista = el().querySelector('[role="tablist"]')!;
    const pestanas = Array.from(lista.querySelectorAll('[role="tab"]')).map((p) => [texto(p), p.getAttribute('aria-selected')]);
    expect(pestanas).toEqual([
      ['Pendientes 1', 'true'],
      ['Encargos en curso 2', 'false'],
      ['Resueltas', 'false'],
    ]);
    expect(pestana('PENDIENTES').getAttribute('aria-controls')).toBe('sol-panel-PENDIENTES');
    expect(panel().getAttribute('aria-labelledby')).toBe('sol-pestana-PENDIENTES');

    fixture.destroy();
    esAdmin = false;
    cargar();
    expect(texto(el().querySelector('.sol__subtitulo'))).toBe('Lo que has pedido al propietario y los encargos de material.');
  });

  it('las pestañas se mueven con las flechas, Inicio y Fin, y el foco sigue a la elegida', async () => {
    cargar();
    pestana('PENDIENTES').focus();
    const tecla = (key: string) => {
      document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
      fixture.detectChanges();
      jasmine.clock().tick(1);
      fixture.detectChanges();
    };
    tecla('ArrowRight');
    expect(pestana('ENCARGOS').getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(pestana('ENCARGOS'));
    expect(pestana('ENCARGOS').tabIndex).toBe(0);
    expect(pestana('PENDIENTES').tabIndex).toBe(-1);
    tecla('ArrowLeft');
    expect(document.activeElement).toBe(pestana('PENDIENTES'));
    tecla('ArrowLeft');
    expect(document.activeElement).toBe(pestana('RESUELTAS'));
    responder('RESUELTAS', []);
    tecla('Home');
    expect(document.activeElement).toBe(pestana('PENDIENTES'));
    tecla('End');
    expect(document.activeElement).toBe(pestana('RESUELTAS'));
  });

  it('la fila: a la izquierda quién, cuándo y sede; en el centro tipo, sobre qué, importe es-ES y el motivo como cita', () => {
    cargar();
    const f = fila(7);
    expect(texto(f.querySelector('.sol__quien'))).toContain('Gestor Prueba');
    expect(texto(f.querySelector('.sol__quien time'))).toBe('19/10/2026 09:30');
    expect(f.querySelector('.sol__quien time')!.getAttribute('datetime')).toBe(PENDIENTE.solicitadoEn);
    expect(texto(f.querySelector('.sol__sede'))).toBe('Sede Centro');
    const que = f.querySelector('.sol__que')!;
    expect(Array.from(que.querySelectorAll('.sol__pastilla')).map((p) => texto(p))).toEqual(['Revertir cobro']);
    expect(texto(que.querySelector('.sol__sobre'))).toBe('Alumno De Prueba');
    expect(texto(que.querySelector('.sol__concepto'))).toBe('CUOTA OCTUBRE');
    expect(texto(que.querySelector('.sol__importe'))).toBe('10,50 €');
    expect(texto(que.querySelector('blockquote.sol__cita'))).toBe('«Se marcó por error»');
  });

  it('sin nombre ni sede, los textos de hoy («Cuenta borrada», «Sin sede»)', () => {
    cargar([solicitudVista({ solicitadoPorNombre: null, sedeNombre: null })], []);
    expect(texto(fila(7).querySelector('.sol__nombre'))).toBe('Cuenta borrada');
    expect(texto(fila(7).querySelector('.sol__sede'))).toBe('Sin sede');
  });

  // ------------------------------------------------------------------ acciones por rol

  describe('acciones por rol', () => {
    it('el propietario ve «Aceptar» con el verbo del tipo (primario de la fila) y «Denegar…»', () => {
      cargar(
        [
          PENDIENTE,
          solicitudVista({ id: 9, tipo: 'DESHACER_COBRO_ENTREGADO' }),
          solicitudVista({ id: 10, tipo: 'ANULAR_ENTREGA', cobroId: null, entregaCajaId: 3 }),
          solicitudVista({ id: 11, tipo: 'ANULAR_MOVIMIENTO', cobroId: null, movimientoCajaId: 4 }),
        ],
        []
      );
      expect(botones(fila(7))).toEqual(['Aceptar y revertir', 'Denegar…']);
      expect(botones(fila(9))).toEqual(['Aceptar y deshacer', 'Denegar…']);
      expect(botones(fila(10))).toEqual(['Aceptar y anular', 'Denegar…']);
      expect(botones(fila(11))).toEqual(['Aceptar y anular', 'Denegar…']);
      expect(boton(fila(7), 'Aceptar y revertir').classList).toContain('sol__accion--principal');
    });

    it('la gestión no ve aceptar ni denegar; sí «Retirar» en las suyas pendientes', () => {
      esAdmin = false;
      cargar([PENDIENTE, solicitudVista({ id: 8, propia: true })], []);
      expect(botones(fila(7))).toEqual([]);
      expect(botones(fila(8))).toEqual(['Retirar']);
    });

    it('el propietario también retira las suyas, y no las de otros', () => {
      cargar([PENDIENTE, solicitudVista({ id: 8, propia: true })], []);
      expect(botones(fila(7))).toEqual(['Aceptar y revertir', 'Denegar…']);
      expect(botones(fila(8))).toEqual(['Aceptar y revertir', 'Denegar…', 'Retirar']);
    });

    it('con los roles por cargar, si fallan, no hay propietario (falla cerrado, como hoy)', () => {
      TestBed.overrideProvider(AuthenticationService, {
        useValue: {
          rolesEstanCargados: () => false,
          tieneRolAdmin: () => true,
          obtenerRoles: () => throwError(() => new Error('sin roles')),
        },
      });
      cargar([PENDIENTE], []);
      expect(botones(fila(7))).toEqual([]);
    });

    it('con los roles por cargar, al llegar decide con tieneRolAdmin', () => {
      TestBed.overrideProvider(AuthenticationService, {
        useValue: { rolesEstanCargados: () => false, tieneRolAdmin: () => true, obtenerRoles: () => of(['ROLE_ADMIN']) },
      });
      cargar([PENDIENTE], []);
      expect(botones(fila(7))).toEqual(['Aceptar y revertir', 'Denegar…']);
    });

    it('encargos: los mismos botones para propietario y gestión según su estado', () => {
      for (const admin of [true, false]) {
        esAdmin = admin;
        cargar([], [PEDIDO, ENCARGADO, RECIBIDO, DEVOLUCION_SEDE, DEVOLUCION_PROFESOR]);
        elegir('ENCARGOS');
        expect(botones(fila(20))).toEqual(['Marcar encargado', 'Rechazar…']);
        expect(botones(fila(21))).toEqual(['Marcar recibido', 'Rechazar…']);
        expect(botones(fila(22))).toEqual(['Rechazar…']);
        expect(botones(fila(23))).toEqual(['Registrar devolución']);
        expect(botones(fila(24))).toEqual([]);
        expect(texto(fila(24))).toContain('Devolución pendiente: la registra el profesor');
        fixture.destroy();
      }
    });

    it('la tabla acción × rol coincide con la bandeja de hoy en todos los casos', () => {
      const tipos: TipoSolicitud[] = ['REVERTIR_COBRO', 'DESHACER_COBRO_ENTREGADO', 'ANULAR_ENTREGA', 'ANULAR_MOVIMIENTO', 'ENCARGO'];
      const estados: EstadoSolicitud[] = [
        'PENDIENTE', 'ACEPTADA', 'DENEGADA', 'RETIRADA', 'NO_APLICABLE', 'PEDIDO', 'ENCARGADO', 'RECIBIDO', 'ENTREGADO', 'RECHAZADO',
      ];
      // La bandeja de hoy (retirada en 8b-Z), grabada: cada caso es un índice (base 36) en `distintos`, que guarda
      // [acciones, devolucionDelProfesor, verboAceptar] en el mismo orden de los bucles.
      const { distintos, indices } = valorDeHoy<{ distintos: string[]; indices: string }>(HOY);
      const deHoy = (n: number) => JSON.parse(distintos[parseInt(indices[n], 36)]).map((v: unknown) => (v === '__undef' ? undefined : v));
      let casos = 0;
      for (const propietario of [true, false]) {
        for (const tipo of tipos) {
          for (const estado of estados) {
            for (const propia of [true, false]) {
              for (const devolucionPendiente of [true, false]) {
                for (const devolucionEnSede of [true, false, null]) {
                  const s = solicitudVista({ tipo, estado, propia, devolucionPendiente, devolucionEnSede });
                  const [acciones, devolucion, verbo] = deHoy(casos);
                  const contexto = JSON.stringify({ propietario, tipo, estado, propia, devolucionPendiente, devolucionEnSede });
                  expect(accionesDe(s, propietario)).withContext(contexto).toEqual(acciones ? acciones.split(',') : []);
                  expect(devolucionDelProfesor(s)).withContext(contexto).toBe(devolucion);
                  expect(verboAceptar(tipo)).withContext(contexto).toBe(verbo);
                  casos++;
                }
              }
            }
          }
        }
      }
      expect(casos).toBe(2 * 5 * 10 * 2 * 2 * 3);
      expect(indices.length).toBe(casos);
      expect(DEVOLUCION_OTRA_SEDE).toBe(valorDeHoy(HOY));
    });
  });

  // ------------------------------------------------------------------ encargos bajo las pendientes

  it('debajo de las pendientes, los encargos en curso con su barra de pasos y el estado del cobro', () => {
    cargar([PENDIENTE], [ENCARGADO]);
    const seccion = panel().querySelector<HTMLElement>('.sol__encargos')!;
    expect(texto(seccion.querySelector('h2'))).toBe('Encargos en curso');
    const f = filas(seccion)[0];
    expect(f.getAttribute('data-id')).toBe('21');
    const pasos = Array.from(f.querySelectorAll('.sol__paso')).map((p) => [texto(p), p.getAttribute('data-estado')]);
    expect(pasos).toEqual([
      ['Pedido', 'hecho'],
      ['Encargado', 'actual'],
      ['Recibido', 'pendiente'],
      ['Entregado', 'pendiente'],
    ]);
    expect(texto(f.querySelector('[aria-current="step"]'))).toBe('Encargado');
    expect(f.querySelector('.sol__pasos')!.getAttribute('aria-label')).toBe('Recorrido del encargo');
    expect(Array.from(f.querySelectorAll('.sol__que .sol__pastilla')).map((p) => texto(p))).toEqual(['Encargo', 'Encargado', 'Cobrado']);
    expect(botones(f)).toEqual(['Marcar recibido', 'Rechazar…']);
    // Las pendientes van antes, en su lista.
    expect(filas(pendientesDe()).map((x) => x.getAttribute('data-id'))).toEqual(['7']);
  });

  it('sin encargos en curso no hay sección debajo; con pendientes vacías, el vacío de hoy y los encargos debajo', () => {
    cargar([PENDIENTE], []);
    expect(panel().querySelector('.sol__encargos')).toBeNull();
    fixture.destroy();
    cargar([], [PEDIDO]);
    expect(texto(panel().querySelector('.pn-bloque-vacio'))).toContain('No hay solicitudes pendientes.');
    expect(panel().querySelector('.sol__encargos .sol__fila[data-id="20"]')).not.toBeNull();
  });

  it('un encargo caído no lleva barra de pasos y enseña «Motivo del rechazo:» con la respuesta', () => {
    cargar([], [DEVOLUCION_SEDE]);
    elegir('ENCARGOS');
    const f = fila(23);
    expect(f.querySelector('.sol__pasos')).toBeNull();
    expect(texto(f.querySelector('.sol__respuesta'))).toBe('Motivo del rechazo: No hay talla');
    expect(Array.from(f.querySelectorAll('.sol__pastilla')).map((p) => [texto(p), p.getAttribute('data-tono')])).toEqual([
      ['Encargo', 'tipo'],
      ['Rechazado', 'peligro'],
      ['Devolución pendiente', 'atencion'],
      ['Cobrado', 'bien'],
    ]);
  });

  it('Resueltas: resuelta por y cuándo; respuesta, «Por qué no se aplicó:» y «Retirada»', () => {
    cargar();
    elegir('RESUELTAS');
    responder('RESUELTAS', [
      RESUELTA,
      solicitudVista({ id: 5, estado: 'NO_APLICABLE', respuesta: 'Ya iba en una entrega.', resueltoEn: RESUELTA.resueltoEn }),
      solicitudVista({ id: 4, estado: 'RETIRADA', resueltoPorNombre: 'Gestor Prueba', resueltoEn: RESUELTA.resueltoEn }),
    ]);
    expect(texto(fila(6).querySelector('.sol__resolucion'))).toBe('Resuelta por Propietario el 19/10/2026 12:00');
    expect(texto(fila(6).querySelector('.sol__respuesta'))).toBe('Respuesta: El cobro es correcto');
    expect(texto(fila(5).querySelector('.sol__respuesta'))).toBe('Por qué no se aplicó: Ya iba en una entrega.');
    expect(texto(fila(4).querySelector('.sol__resolucion'))).toBe('Retirada por Gestor Prueba el 19/10/2026 12:00');
    expect(botones(fila(6))).toEqual([]);
    // Fuera de Resueltas no se pinta la resolución.
    elegir('PENDIENTES');
    expect(panel().querySelector('.sol__resolucion')).toBeNull();
  });

  // ------------------------------------------------------------------ carga, error y vacíos

  it('esqueleto a los 300 ms mientras carga; error con el mensaje y «Reintentar», que vuelve a pedir solo ese segmento', () => {
    crear();
    expect(panel().querySelector('.pn-esqueleto__forma')).toBeNull();
    jasmine.clock().tick(300);
    fixture.detectChanges();
    expect(panel().querySelector('.pn-esqueleto__forma')).not.toBeNull();
    expect(panel().getAttribute('aria-busy')).toBe('true');
    lecturas('PENDIENTES')[0].flush({ mensaje: 'Sin permiso para ver solicitudes.' }, { status: 403, statusText: 'Forbidden' });
    responder('ENCARGOS', []);
    const error = panel().querySelector('.pn-bloque-error')!;
    expect(texto(error)).toContain('No se han podido cargar las solicitudes');
    expect(texto(error)).toContain('Sin permiso para ver solicitudes.');
    boton(error, 'Reintentar').click();
    fixture.detectChanges();
    expect(lecturas('PENDIENTES').length).toBe(1);
    expect(lecturas('ENCARGOS').length).toBe(0);
  });

  it('un error sin mensaje enseña el texto por defecto', () => {
    crear();
    lecturas('PENDIENTES')[0].flush(null, { status: 500, statusText: 'Error' });
    responder('ENCARGOS', []);
    expect(texto(panel().querySelector('.pn-bloque-error'))).toContain('No se han podido cargar las solicitudes');
  });

  it('vacíos de cada segmento con el texto de hoy, por rol', () => {
    const vacioDe = () =>
      Array.from(panel().querySelectorAll('.pn-bloque-vacio__titulo, .pn-bloque-vacio__texto')).map((p) => texto(p));
    cargar([], []);
    expect(vacioDe()).toEqual([
      'No hay solicitudes pendientes.',
      'Cuando la gestión pida revertir un cobro o anular algo de la caja, aparecerá aquí.',
    ]);
    elegir('ENCARGOS');
    expect(vacioDe()).toEqual(['No hay encargos en curso.']);
    elegir('RESUELTAS');
    responder('RESUELTAS', []);
    expect(vacioDe()).toEqual(['Todavía no hay solicitudes resueltas.']);
    fixture.destroy();
    esAdmin = false;
    cargar([], []);
    expect(texto(panel().querySelector('.pn-bloque-vacio'))).toContain(
      'Lo que pidas al propietario desde la tesorería, la ficha o la caja de sede aparecerá aquí.'
    );
  });

  it('«Actualizar» relee los segmentos cargados y el contador de la cabecera', () => {
    cargar();
    contador().forEach((r) => r.flush({ total: 1 }));
    boton(el(), 'Actualizar').click();
    fixture.detectChanges();
    expect(lecturas('PENDIENTES').length).toBe(1);
    expect(lecturas('ENCARGOS').length).toBe(1);
    expect(lecturas('RESUELTAS').length).toBe(0);
    expect(contador().length).toBe(1);
  });

  // ------------------------------------------------------------------ aceptar

  describe('aceptar', () => {
    it('el diálogo de hoy: título, qué pasa, el verbo y «Volver», que no llama', async () => {
      cargar();
      // Como un clic de verdad: el botón tiene el foco al abrir.
      boton(fila(7), 'Aceptar y revertir').focus();
      const d = (await pulsar(fila(7), 'Aceptar y revertir'))!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Aceptar y revertir el cobro');
      expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe(
        'Alumno De Prueba, CUOTA OCTUBRE, 10,50 €. El cargo pasará a pendiente, como al revertirlo desde la tesorería.'
      );
      expect(texto(d.querySelector('.pn-dialogo__accion'))).toBe('Aceptar y revertir');
      expect(texto(d.querySelector('.pn-dialogo__cancelar'))).toBe('Volver');
      (d.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement).click();
      await esperar();
      http.expectNone(`${BASE}/7/aceptar`);
      // El foco vuelve al botón de la fila, que sigue pulsable.
      expect(document.activeElement).toBe(boton(fila(7), 'Aceptar y revertir'));
      expect(boton(fila(7), 'Aceptar y revertir').disabled).toBeFalse();
    });

    it('cada tipo con su título y su efecto, como hoy', async () => {
      const casos: Array<[Solicitud, string, string]> = [
        [solicitudVista({ id: 9, tipo: 'DESHACER_COBRO_ENTREGADO' }), 'Aceptar y deshacer el cobro', 'El cobro se anula y su entrega apunta la corrección; la entrega sigue en pie.'],
        [solicitudVista({ id: 10, tipo: 'ANULAR_ENTREGA', alumnoNombre: null, alumnoApellidos: null, concepto: null, importe: null }), 'Aceptar y anular la entrega', 'Sus cobros vuelven a estar pendientes de entregar.'],
        [solicitudVista({ id: 11, tipo: 'ANULAR_MOVIMIENTO', alumnoNombre: null, alumnoApellidos: null, concepto: null, importe: 12 }), 'Aceptar y anular el movimiento', 'Deja de contar en la caja.'],
      ];
      cargar(casos.map((c) => c[0]), []);
      for (const [s, titulo, efecto] of casos) {
        const d = (await pulsar(fila(s.id), s.tipo === 'DESHACER_COBRO_ENTREGADO' ? 'Aceptar y deshacer' : 'Aceptar y anular'))!;
        expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe(titulo);
        expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toContain(efecto);
        (d.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement).click();
        await esperar();
      }
    });

    it('POST /solicitudes/7/aceptar sin cuerpo, con el diálogo ocupado; luego «Cobro revertido», relee y refresca el contador', async () => {
      cargar();
      contador().forEach((r) => r.flush({ total: 1 }));
      const d = (await pulsar(fila(7), 'Aceptar y revertir'))!;
      await confirmarDialogo(d);
      const r = http.expectOne(`${BASE}/7/aceptar`);
      expect(r.request.method).toBe('POST');
      expect(r.request.body).toBeNull();
      expect(r.request.withCredentials).toBeTrue();
      expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      const accion = d.querySelector('.pn-dialogo__accion') as HTMLButtonElement;
      expect(accion.getAttribute('aria-busy')).toBe('true');
      accion.click();
      await esperar();
      http.expectNone(`${BASE}/7/aceptar`);
      r.flush(api(solicitudVista({ estado: 'ACEPTADA' })));
      await esperar();
      expect(avisos()).toContain({ tipo: 'exito', texto: 'Cobro revertido' });
      expect(lecturas('PENDIENTES').length).toBe(1);
      expect(lecturas('ENCARGOS').length).toBe(1);
      expect(contador().length).toBe(1);
    });

    it('NO_APLICABLE avisa con la razón de la API (o la de hoy por defecto) y relee', async () => {
      cargar([PENDIENTE, solicitudVista({ id: 9 })], []);
      let d = (await pulsar(fila(7), 'Aceptar y revertir'))!;
      await confirmarDialogo(d);
      http.expectOne(`${BASE}/7/aceptar`).flush(api(solicitudVista({ estado: 'NO_APLICABLE', respuesta: 'El cobro ya va en una entrega de caja.' })));
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'No se ha aplicado. El cobro ya va en una entrega de caja.' });
      releer([PENDIENTE, solicitudVista({ id: 9 })]);
      d = (await pulsar(fila(9), 'Aceptar y revertir'))!;
      await confirmarDialogo(d);
      http.expectOne(`${BASE}/9/aceptar`).flush(api(solicitudVista({ id: 9, estado: 'NO_APLICABLE', respuesta: '  ' })));
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'No se ha aplicado. El estado cambió desde que se pidió.' });
      expect(contador().length).toBeGreaterThan(0);
    });

    it('un 409 enseña el `mensaje` del servidor; relee y refresca el contador', async () => {
      cargar();
      contador().forEach((r) => r.flush({ total: 1 }));
      const d = (await pulsar(fila(7), 'Aceptar y revertir'))!;
      await confirmarDialogo(d);
      http.expectOne(`${BASE}/7/aceptar`).flush({ mensaje: 'La solicitud ya no está pendiente' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'La solicitud ya no está pendiente' });
      expect(lecturas('PENDIENTES').length).toBe(1);
      expect(contador().length).toBe(1);
    });

    it('un error sin mensaje, el texto por defecto de hoy', async () => {
      cargar();
      const d = (await pulsar(fila(7), 'Aceptar y revertir'))!;
      await confirmarDialogo(d);
      http.expectOne(`${BASE}/7/aceptar`).flush(null, { status: 500, statusText: 'Error' });
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'No se ha podido aceptar la solicitud.' });
    });
  });

  // ------------------------------------------------------------------ denegar

  describe('denegar', () => {
    it('el diálogo de hoy: respuesta obligatoria (500), destructivo; envía {respuesta} recortada', async () => {
      cargar();
      contador().forEach((r) => r.flush({ total: 1 }));
      const d = (await pulsar(fila(7), 'Denegar…'))!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Denegar la solicitud');
      expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe('Alumno De Prueba, CUOTA OCTUBRE, 10,50 €');
      const campo = d.querySelector<HTMLTextAreaElement>('textarea')!;
      expect(texto(d.querySelector(`label[for="${campo.id}"]`))).toBe('Respuesta');
      expect(campo.placeholder).toBe('Por qué no se hace; quien lo pidió lo verá');
      expect(campo.maxLength).toBe(500);
      expect(texto(d.querySelector('.pn-dialogo__accion'))).toBe('Denegar');
      expect(d.classList).toContain('pn-dialogo--peligro');
      expect(texto(d.querySelector('.pn-dialogo__cancelar'))).toBe('Volver');

      escribir(d, '   ');
      await confirmarDialogo(d);
      expect(texto(d)).toContain('Escribe la respuesta para quien lo pidió');
      http.expectNone(`${BASE}/7/denegar`);

      escribir(d, '  No procede  ');
      await confirmarDialogo(d);
      const r = http.expectOne(`${BASE}/7/denegar`);
      expect(r.request.method).toBe('POST');
      expect(r.request.body).toEqual({ respuesta: 'No procede' });
      r.flush(api(solicitudVista({ estado: 'DENEGADA' })));
      await esperar();
      expect(avisos()).toContain({ tipo: 'exito', texto: 'Solicitud denegada' });
      expect(contador().length).toBe(1);
    });

    it('un 409 enseña su mensaje', async () => {
      cargar();
      const d = (await pulsar(fila(7), 'Denegar…'))!;
      escribir(d, 'No');
      await confirmarDialogo(d);
      http.expectOne(`${BASE}/7/denegar`).flush({ mensaje: 'Ya resuelta.' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'Ya resuelta.' });
    });
  });

  // ------------------------------------------------------------------ retirar

  describe('retirar', () => {
    it('sin confirmación (como hoy): POST /retirar sin cuerpo, botón ocupado sin doble envío; aviso, relee y contador', async () => {
      esAdmin = false;
      cargar([solicitudVista({ propia: true })], []);
      contador().forEach((r) => r.flush({ total: 1 }));
      const b = boton(fila(7), 'Retirar');
      b.click();
      await esperar();
      expect(dialogo()).toBeNull();
      const r = http.expectOne(`${BASE}/7/retirar`);
      expect(r.request.method).toBe('POST');
      expect(r.request.body).toBeNull();
      expect(b.getAttribute('aria-busy')).toBe('true');
      b.click();
      await esperar();
      http.expectNone(`${BASE}/7/retirar`);
      r.flush(api(solicitudVista({ estado: 'RETIRADA', propia: true })));
      await esperar();
      expect(avisos()).toContain({ tipo: 'exito', texto: 'Solicitud retirada' });
      expect(lecturas('PENDIENTES').length).toBe(1);
      expect(contador().length).toBe(1);
    });

    it('un 409 enseña su mensaje y refresca el contador', async () => {
      esAdmin = false;
      cargar([solicitudVista({ propia: true })], []);
      contador().forEach((r) => r.flush({ total: 1 }));
      boton(fila(7), 'Retirar').click();
      await esperar();
      http.expectOne(`${BASE}/7/retirar`).flush({ mensaje: 'Ya no está pendiente.' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'Ya no está pendiente.' });
      expect(contador().length).toBe(1);
    });
  });

  // ------------------------------------------------------------------ encargos

  describe('encargos', () => {
    it('marcar encargado y recibido: el diálogo de hoy, POST sin cuerpo y su aviso', async () => {
      esAdmin = false;
      cargar([], [PEDIDO, ENCARGADO]);
      elegir('ENCARGOS');
      let d = (await pulsar(fila(20), 'Marcar encargado'))!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Marcar como encargado');
      expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe('Alumno De Prueba, DOBOK T-160, 25,00 €. Ya se ha pedido al proveedor.');
      expect(texto(d.querySelector('.pn-dialogo__accion'))).toBe('Marcar encargado');
      expect(texto(d.querySelector('.pn-dialogo__cancelar'))).toBe('Volver');
      await confirmarDialogo(d);
      let r = http.expectOne(`${BASE}/20/encargado`);
      expect(r.request.method).toBe('POST');
      expect(r.request.body).toBeNull();
      r.flush(api(encargo({ estado: 'ENCARGADO' })));
      await esperar();
      expect(avisos()).toContain({ tipo: 'exito', texto: 'Encargo marcado como encargado' });
      releer([], [PEDIDO, ENCARGADO]);

      d = (await pulsar(fila(21), 'Marcar recibido'))!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Marcar como recibido');
      expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toContain(
        'Ha llegado y se le da al profesor, que lo marcará entregado al dárselo al alumno.'
      );
      await confirmarDialogo(d);
      r = http.expectOne(`${BASE}/21/recibido`);
      expect(r.request.body).toBeNull();
      r.flush(api(encargo({ id: 21, estado: 'RECIBIDO' })));
      await esperar();
      expect(avisos()).toContain({ tipo: 'exito', texto: 'Encargo marcado como recibido' });
      expect(contador().length).toBeGreaterThan(0);
    });

    it('al volver de la confirmación no llama', async () => {
      cargar([], [PEDIDO]);
      elegir('ENCARGOS');
      const d = (await pulsar(fila(20), 'Marcar encargado'))!;
      (d.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement).click();
      await esperar();
      http.expectNone(`${BASE}/20/encargado`);
    });

    it('rechazar: motivo obligatorio (500), destructivo, y avisa de lo que pasa con el cargo; envía {motivo} recortado', async () => {
      cargar([], [PEDIDO, ENCARGADO]);
      elegir('ENCARGOS');
      let d = (await pulsar(fila(20), 'Rechazar…'))!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Rechazar el encargo');
      expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe('Alumno De Prueba, DOBOK T-160, 25,00 €. El cargo del alumno se cancelará.');
      const campo = d.querySelector<HTMLTextAreaElement>('textarea')!;
      expect(texto(d.querySelector(`label[for="${campo.id}"]`))).toBe('Motivo');
      expect(campo.placeholder).toBe('Por qué no se puede atender; el profesor lo verá');
      expect(campo.maxLength).toBe(500);
      expect(texto(d.querySelector('.pn-dialogo__accion'))).toBe('Rechazar');
      expect(d.classList).toContain('pn-dialogo--peligro');
      await confirmarDialogo(d);
      expect(texto(d)).toContain('Escribe el motivo para el profesor');
      http.expectNone(`${BASE}/20/rechazar`);
      escribir(d, '  No hay talla  ');
      await confirmarDialogo(d);
      const r = http.expectOne(`${BASE}/20/rechazar`);
      expect(r.request.body).toEqual({ motivo: 'No hay talla' });
      r.flush(api(encargo({ estado: 'RECHAZADO' })));
      await esperar();
      expect(avisos()).toContain({ tipo: 'exito', texto: 'Encargo rechazado' });
      releer([], [PEDIDO, ENCARGADO]);

      d = (await pulsar(fila(21), 'Rechazar…'))!;
      expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toContain('El cargo ya está cobrado: quedará una devolución pendiente.');
    });

    it('registrar devolución: confirma alumno, concepto e importe (sin pedirlo) con nota opcional; {nota: null} vacía', async () => {
      cargar([], [DEVOLUCION_SEDE]);
      contador().forEach((r) => r.flush({ total: 1 }));
      elegir('ENCARGOS');
      const d = (await pulsar(fila(23), 'Registrar devolución'))!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Registrar devolución');
      const cuerpo = texto(d);
      expect(cuerpo).toContain('Alumno: Alumno De Prueba');
      expect(cuerpo).toContain('Concepto: DOBOK T-160');
      expect(cuerpo).toContain('Importe: 25,00 €');
      expect(cuerpo).toContain('Se devuelve el cobro entero y sale de la caja de la sede.');
      expect(d.querySelectorAll('input:not([type="hidden"])').length).toBe(0);
      const campo = d.querySelector<HTMLTextAreaElement>('textarea')!;
      expect(texto(d.querySelector(`label[for="${campo.id}"]`))).toBe('Nota (opcional)');
      expect(campo.placeholder).toBe('A quién y cómo se ha devuelto');
      expect(campo.maxLength).toBe(500);
      expect(texto(d.querySelector('.pn-dialogo__accion'))).toBe('Registrar devolución');
      await confirmarDialogo(d);
      const r = http.expectOne(`${BASE}/23/devolucion`);
      expect(r.request.method).toBe('POST');
      expect(r.request.body).toEqual({ nota: null });
      r.flush(api(encargo({ id: 23, estado: 'RECHAZADO', estadoCobro: 'DEVUELTO' })));
      await esperar();
      expect(avisos()).toContain({ tipo: 'exito', texto: 'Devolución registrada' });
      expect(contador().length).toBe(1);
    });

    it('con nota, la envía recortada', async () => {
      cargar([], [DEVOLUCION_SEDE]);
      elegir('ENCARGOS');
      const d = (await pulsar(fila(23), 'Registrar devolución'))!;
      escribir(d, ' En mano a la madre ');
      await confirmarDialogo(d);
      expect(http.expectOne(`${BASE}/23/devolucion`).request.body).toEqual({ nota: 'En mano a la madre' });
    });

    it('un 403 explica que el cobro es de otra sede, sin error genérico; un 409, su mensaje', async () => {
      cargar([], [DEVOLUCION_SEDE]);
      elegir('ENCARGOS');
      let d = (await pulsar(fila(23), 'Registrar devolución'))!;
      await confirmarDialogo(d);
      http.expectOne(`${BASE}/23/devolucion`).flush({ mensaje: 'Acceso denegado' }, { status: 403, statusText: 'Forbidden' });
      await esperar();
      expect(avisos()).toEqual([{ tipo: 'error', texto: `No puedes registrarla. ${DEVOLUCION_OTRA_SEDE}` }]);
      releer([], [DEVOLUCION_SEDE]);
      elegir('ENCARGOS');

      d = (await pulsar(fila(23), 'Registrar devolución'))!;
      await confirmarDialogo(d);
      http
        .expectOne(`${BASE}/23/devolucion`)
        .flush({ mensaje: 'El encargo no tiene una devolución pendiente.' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'El encargo no tiene una devolución pendiente.' });
    });

    it('un 409 al marcar enseña su mensaje', async () => {
      cargar([], [PEDIDO]);
      elegir('ENCARGOS');
      const d = (await pulsar(fila(20), 'Marcar encargado'))!;
      await confirmarDialogo(d);
      http.expectOne(`${BASE}/20/encargado`).flush({ mensaje: 'La solicitud ya no está pendiente' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'La solicitud ya no está pendiente' });
    });
  });

  // ------------------------------------------------------------------ privacidad, salida y móvil

  it('ningún dato en la URL ni en el almacenamiento del navegador', async () => {
    const router = TestBed.inject(Router);
    const navegar = spyOn(router, 'navigate').and.callThrough();
    const navegarUrl = spyOn(router, 'navigateByUrl').and.callThrough();
    cargar([PENDIENTE], [PEDIDO]);
    elegir('ENCARGOS');
    elegir('RESUELTAS');
    responder('RESUELTAS', [RESUELTA]);
    const d = (await pulsar(el(), 'Pendientes 1'));
    expect(d).toBeNull();
    expect(navegar).not.toHaveBeenCalled();
    expect(navegarUrl).not.toHaveBeenCalled();
    expect(guardarEnStorage).not.toHaveBeenCalled();
  });

  it('una escritura no se cancela al salir y, tras salir, no relee la bandeja (el contador sí)', async () => {
    esAdmin = false;
    cargar([solicitudVista({ propia: true })], []);
    contador().forEach((r) => r.flush({ total: 1 }));
    boton(fila(7), 'Retirar').click();
    await esperar();
    const r = http.expectOne(`${BASE}/7/retirar`);
    fixture.destroy();
    expect(r.cancelled).toBeFalse();
    r.flush(api(solicitudVista({ estado: 'RETIRADA' })));
    await esperar();
    expect(lecturas('PENDIENTES').length).toBe(0);
    expect(contador().length).toBe(1);
  });

  it('a 375 px la fila se apila (quién, qué, acciones) sin desplazamiento horizontal', () => {
    cargar([solicitudVista({ propia: true, motivo: 'Un motivo largo que tiene que partirse en varias líneas sin salirse de la pantalla' })], [ENCARGADO]);
    const host = el();
    host.style.display = 'block';
    host.style.width = '375px';
    document.body.appendChild(host);
    fixture.detectChanges();
    const f = fila(7);
    const [quien, que, acciones] = ['.sol__quien', '.sol__que', '.sol__acciones'].map((c) => f.querySelector<HTMLElement>(c)!.getBoundingClientRect());
    expect(quien.bottom).toBeLessThanOrEqual(que.top + 1);
    expect(que.bottom).toBeLessThanOrEqual(acciones.top + 1);
    const pagina = host.querySelector<HTMLElement>('.sol__pagina')!;
    expect(pagina.scrollWidth).toBeLessThanOrEqual(pagina.clientWidth);
    host.remove();
  });
});
