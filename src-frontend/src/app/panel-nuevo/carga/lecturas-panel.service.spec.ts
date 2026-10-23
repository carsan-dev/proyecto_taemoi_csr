import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';

import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { PreinscripcionService } from '../../features/preinscripcion/preinscripcion.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { LecturasPanelService } from './lecturas-panel.service';

/** Lo que se compara de una petición: método, ruta con parámetros, cabeceras y credenciales. */
function forma(r: TestRequest) {
  const cabeceras = r.request.headers.keys().map((k) => [k, r.request.headers.get(k)]);
  return { metodo: r.request.method, url: r.request.urlWithParams, cabeceras, credenciales: r.request.withCredentials };
}

/**
 * Las lecturas de bloque de las pantallas nuevas (8b-D2a): la misma petición que su método de `EndpointsService`, con
 * `ERROR_HANDLED_LOCALLY` (el de hoy, sin él).
 */
describe('LecturasPanelService (8b-D2a)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function pedir(fuente: Observable<unknown>): TestRequest {
    fuente.subscribe({ error: () => undefined });
    const [r] = http.match(() => true);
    return r;
  }

  it('alumnos: la misma petición que obtenerAlumnos, con y sin filtros, y con ERROR_HANDLED_LOCALLY', () => {
    const hoy = TestBed.inject(EndpointsService);
    const panel = TestBed.inject(LecturasPanelService);
    const casos: [number, number, string, boolean, boolean, string | null][] = [
      [1, 25, '', false, false, null],
      [3, 25, 'Ana María', true, true, 'TAEKWONDO'],
    ];
    for (const caso of casos) {
      const deHoy = pedir(hoy.obtenerAlumnos(...caso));
      const delPanel = pedir(panel.alumnos(...caso));
      expect(forma(delPanel)).toEqual(forma(deHoy));
      expect(deHoy.request.context.get(ERROR_HANDLED_LOCALLY)).toBeFalse();
      expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      deHoy.flush({});
      delPanel.flush({});
    }
  });

  it('límite por turno: la misma petición que obtenerLimiteTurno, con ERROR_HANDLED_LOCALLY', () => {
    const deHoy = pedir(TestBed.inject(EndpointsService).obtenerLimiteTurno());
    const delPanel = pedir(TestBed.inject(LecturasPanelService).limiteTurno());
    expect(forma(delPanel)).toEqual(forma(deHoy));
    expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    deHoy.flush(36);
    delPanel.flush(36);
  });

  it('Grupos (8b-3c): resumen de grupos y de turnos, conteo por tipo y alumnos por tipo, como hoy y con ERROR_HANDLED_LOCALLY', () => {
    const hoy = TestBed.inject(EndpointsService);
    const panel = TestBed.inject(LecturasPanelService);
    const pares: [Observable<unknown>, Observable<unknown>][] = [
      [hoy.obtenerResumenGrupos(), panel.resumenGrupos()],
      [hoy.obtenerResumenTurnos(), panel.resumenTurnos()],
      [hoy.obtenerAlumnosPorTipo('Taekwondo Competición'), panel.alumnosPorTipo('Taekwondo Competición')],
    ];
    for (const [deHoy$, delPanel$] of pares) {
      const deHoy = pedir(deHoy$);
      const delPanel = pedir(delPanel$);
      expect(forma(delPanel)).toEqual(forma(deHoy));
      expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      deHoy.flush([]);
      delPanel.flush([]);
    }
    hoy.obtenerConteoAlumnosPorGrupo();
    const [conteoHoy] = http.match(() => true);
    const conteoPanel = pedir(panel.conteoAlumnosPorGrupo());
    expect(forma(conteoPanel)).toEqual(forma(conteoHoy));
    expect(conteoPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(conteoPanel.request.url).toMatch(/\/grupos\/conteo-alumnos$/);
    conteoHoy.flush({});
    conteoPanel.flush({});
  });
  it('Tesorería (8b-3d): resumen, movimientos, años y alumnos sin paginar, como hoy y con ERROR_HANDLED_LOCALLY', () => {
    const hoy = TestBed.inject(EndpointsService);
    const panel = TestBed.inject(LecturasPanelService);
    const pares: [Observable<unknown>, Observable<unknown>][] = [
      [hoy.obtenerTesoreriaResumen(10, 2026, 'TODOS', true), panel.tesoreriaResumen(10, 2026, 'TODOS', true)],
      [hoy.obtenerTesoreriaResumen(null, null, 'KICKBOXING', false), panel.tesoreriaResumen(null, null, 'KICKBOXING', false)],
      [hoy.obtenerTesoreriaMovimientos(10, 2026, 'TODOS', false, '', true, 1, 25),
        panel.tesoreriaMovimientos(10, 2026, 'TODOS', false, '', true, 1, 25)],
      [hoy.obtenerTesoreriaMovimientos(null, 2025, 'PILATES', true, '  Ana  ', false, 3, 25),
        panel.tesoreriaMovimientos(null, 2025, 'PILATES', true, '  Ana  ', false, 3, 25)],
      [hoy.obtenerTesoreriaMovimientos(4, 2026, 'TODOS', undefined, 'x', true, 2, 25),
        panel.tesoreriaMovimientos(4, 2026, 'TODOS', undefined, 'x', true, 2, 25)],
      [hoy.obtenerTesoreriaAniosDisponibles(), panel.tesoreriaAnios()],
      [hoy.obtenerTodosLosAlumnosSinPaginar(false), panel.alumnosSinPaginar(false)],
    ];
    for (const [deHoy$, delPanel$] of pares) {
      const deHoy = pedir(deHoy$);
      const delPanel = pedir(delPanel$);
      expect(forma(delPanel)).toEqual(forma(deHoy));
      expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      deHoy.flush([]);
      delPanel.flush([]);
    }
  });
  it('Convocatorias (8b-3e): listas por disciplina, alumnos de una convocatoria y aptos, como hoy y con ERROR_HANDLED_LOCALLY', () => {
    const hoy = TestBed.inject(EndpointsService);
    const panel = TestBed.inject(LecturasPanelService);
    const pares: [Observable<unknown>, Observable<unknown>][] = [
      [hoy.obtenerConvocatorias('TAEKWONDO'), panel.convocatorias('TAEKWONDO')],
      [hoy.obtenerAlumnosDeConvocatoria(11), panel.alumnosDeConvocatoria(11)],
      [hoy.obtenerAlumnosElegiblesParaConvocatoria('KICKBOXING'), panel.aptosParaConvocatoria('KICKBOXING')],
    ];
    for (const [deHoy$, delPanel$] of pares) {
      const deHoy = pedir(deHoy$);
      const delPanel = pedir(delPanel$);
      expect(forma(delPanel)).toEqual(forma(deHoy));
      expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      deHoy.flush([]);
      delPanel.flush([]);
    }
  });
  it('Eventos (8b-3f): todos los eventos (admin) y uno por id, como hoy y con ERROR_HANDLED_LOCALLY', () => {
    const hoy = TestBed.inject(EndpointsService);
    const panel = TestBed.inject(LecturasPanelService);
    // `obtenerTodosLosEventos` no devuelve el observable: se suscribe solo.
    hoy.obtenerTodosLosEventos();
    const deHoyTodos = http.match(() => true)[0];
    const delPanelTodos = pedir(panel.eventosAdmin());
    expect(forma(delPanelTodos)).toEqual(forma(deHoyTodos));
    expect(delPanelTodos.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    deHoyTodos.flush([]);
    delPanelTodos.flush([]);

    const deHoy = pedir(hoy.obtenerEventoPorId(7));
    const delPanel = pedir(panel.evento(7));
    expect(forma(delPanel)).toEqual(forma(deHoy));
    expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    deHoy.flush({});
    delPanel.flush({});
  });

  it('Productos (8b-3g): el listado con sus filtros y uno por id, como hoy y con ERROR_HANDLED_LOCALLY', () => {
    const hoy = TestBed.inject(EndpointsService);
    const panel = TestBed.inject(LecturasPanelService);
    const casos: [string, number, number, string, string, string | null, boolean | null][] = [
      ['', 1, 200, 'concepto', 'asc', 'MENSUALIDAD', null],
      ['camis eta', 2, 200, 'concepto', 'asc', 'OTRO', false],
      ['x', 1, 10, 'precio', 'desc', null, true],
    ];
    for (const caso of casos) {
      const deHoy = pedir(hoy.obtenerTodosLosProductosPaginado(...caso));
      const delPanel = pedir(panel.productos(...caso));
      expect(forma(delPanel)).toEqual(forma(deHoy));
      expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      deHoy.flush({});
      delPanel.flush({});
    }

    const deHoy = pedir(hoy.obtenerProductoPorId(7));
    const delPanel = pedir(panel.producto(7));
    expect(forma(delPanel)).toEqual(forma(deHoy));
    expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    deHoy.flush({});
    delPanel.flush({});
  });

  it('preinscripciones (8b-3h): la misma petición que PreinscripcionService.listar, con y sin filtros, y con ERROR_HANDLED_LOCALLY', () => {
    const hoy = TestBed.inject(PreinscripcionService);
    const panel = TestBed.inject(LecturasPanelService);
    const casos: [Record<string, string>, number, number][] = [
      [{ temporada: '', deporte: '', estado: '', q: '' }, 0, 25],
      [{ temporada: '2025-2026', deporte: 'TAEKWONDO', estado: 'EN_LISTA_ESPERA', q: ' Ana ' }, 2, 25],
      [{ temporada: '', deporte: '', estado: 'CANCELADA', q: '12345678Z' }, 1, 25],
    ];
    for (const [filtros, page, size] of casos) {
      const deHoy = pedir(hoy.listar(filtros, page, size));
      deHoy.flush({});
      const delPanel = pedir(panel.preinscripciones(filtros, page, size));
      expect(forma(delPanel)).toEqual(forma(deHoy));
      expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      delPanel.flush({});
    }
  });

  it('preinscripcion y firmaPreinscripcion (8b-3h): las mismas que detalle y firma (en blob), con ERROR_HANDLED_LOCALLY', () => {
    const hoy = TestBed.inject(PreinscripcionService);
    const panel = TestBed.inject(LecturasPanelService);
    const pares: [Observable<unknown>, Observable<unknown>][] = [
      [hoy.detalle('PRE-1'), panel.preinscripcion('PRE-1')],
      [hoy.firma('PRE-1'), panel.firmaPreinscripcion('PRE-1')],
    ];
    for (const [deHoyFuente, delPanelFuente] of pares) {
      const deHoy = pedir(deHoyFuente);
      const tipo = deHoy.request.responseType;
      deHoy.flush(tipo === 'blob' ? new Blob() : {});
      const delPanel = pedir(delPanelFuente);
      expect(forma(delPanel)).toEqual(forma(deHoy));
      expect(delPanel.request.responseType).toBe(tipo);
      expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      delPanel.flush(tipo === 'blob' ? new Blob() : {});
    }
  });

  it('Configuración y Registro de acciones (8b-3k): usuarios, eventos con y sin filtros, detalle y módulos, como hoy y con ERROR_HANDLED_LOCALLY', () => {
    const hoy = TestBed.inject(EndpointsService);
    const panel = TestBed.inject(LecturasPanelService);
    const vacio = { desde: null, hasta: null, resultado: 'EXITO', accion: null, modulo: null, usuario: null, texto: null };
    const lleno = { desde: '2026-10-01', hasta: '2026-10-23', resultado: 'EXITO', accion: 'READ', modulo: 'salud', usuario: ' ana ', texto: ' cinto ' };
    const pares: [Observable<unknown>, Observable<unknown>][] = [
      [hoy.obtenerUsuarios(), panel.usuarios()],
      [hoy.obtenerAuditoriaEventos(vacio, 1, 25), panel.auditoriaEventos(vacio, 1, 25)],
      [hoy.obtenerAuditoriaEventos(lleno, 3, 25), panel.auditoriaEventos(lleno, 3, 25)],
      [hoy.obtenerAuditoriaEventoDetalle(9), panel.auditoriaEventoDetalle(9)],
      [hoy.obtenerAuditoriaModulos(), panel.auditoriaModulos()],
    ];
    for (const [deHoy$, delPanel$] of pares) {
      const deHoy = pedir(deHoy$);
      deHoy.flush([]);
      const delPanel = pedir(delPanel$);
      expect(forma(delPanel)).toEqual(forma(deHoy));
      expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      delPanel.flush([]);
    }
  });
});
