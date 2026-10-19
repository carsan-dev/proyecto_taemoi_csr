import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { CajaSedeComponent, construirPeriodos } from './caja-sede.component';
import { CajaService } from '../../../servicios/endpoints/caja.service';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { EntregaCaja, MovimientoCaja, PendienteCobrador } from '../../../interfaces/caja';

const profesor: PendienteCobrador = {
  usuarioId: 9,
  nombre: 'Profe Prueba',
  numeroCobros: 2,
  importe: 45,
  cobroMasAntiguo: new Date(Date.now() - 9 * 86_400_000).toISOString(),
  cobros: [
    { cobroId: 1, cargoId: 11, alumnoId: 21, alumnoNombre: 'Ana', alumnoApellidos: 'Prueba', concepto: 'MENSUALIDAD', importe: 30, fechaCobro: '2026-10-10T08:00:00.000+00:00', sedeId: 5 },
    { cobroId: 2, cargoId: 12, alumnoId: 22, alumnoNombre: 'Leo', alumnoApellidos: 'Prueba', concepto: 'LICENCIA', importe: 15, fechaCobro: '2026-10-11T08:00:00.000+00:00', sedeId: 5 },
  ],
};

const entrega: EntregaCaja = {
  id: 7, sedeId: 5, entregadoPorUsuarioId: 9, entregadoPorNombre: 'Profe Prueba', recibidoPorUsuarioId: 3,
  recibidoPorNombre: 'Gestora Prueba', importeDeclarado: 20, importeContado: 19, diferencia: -1,
  fecha: '2026-10-12T18:00:00', nota: 'faltaba 1', estado: 'ACTIVA', cobroIds: [5], movimientoId: 4,
  anuladaPorUsuarioId: null, anuladaEn: null, motivoAnulacion: null, autoentrega: false,
};

const movimientos: MovimientoCaja[] = [
  { id: 4, sedeId: 5, tipo: 'FALTANTE', importe: -1, alumnoId: null, entregaCajaId: 7, nota: 'faltaba 1', creadoPorUsuarioId: 3, fecha: '2026-10-12T18:00:00', estado: 'ACTIVO', anuladoPorUsuarioId: null, anuladoEn: null, motivoAnulacion: null },
  { id: 5, sedeId: 5, tipo: 'OTRO', importe: 10, alumnoId: null, entregaCajaId: null, nota: 'cambio', creadoPorUsuarioId: 3, fecha: '2026-10-13T10:00:00', estado: 'ACTIVO', anuladoPorUsuarioId: null, anuladoEn: null, motivoAnulacion: null },
];

describe('CajaSedeComponent', () => {
  let fixture: ComponentFixture<CajaSedeComponent>;
  let component: CajaSedeComponent;
  let caja: jasmine.SpyObj<CajaService>;

  async function montar(admin: boolean): Promise<void> {
    caja = jasmine.createSpyObj<CajaService>('CajaService', [
      'sedesGestionables', 'resumen', 'pendienteEntrega', 'entregas', 'movimientos', 'registrarEntrega',
      'crearMovimiento', 'anularEntrega', 'anularMovimiento',
    ]);
    caja.sedesGestionables.and.returnValue(of([{ id: 5, nombre: 'Sede Prueba' }]));
    caja.resumen.and.returnValue(of({ sedeId: 5, desde: '2026-10-01', hasta: '2026-10-31', pendienteDeEntregar: 45, recibidoEnPeriodo: 19, diferenciasEnPeriodo: -1, saldoCaja: 28 }));
    caja.pendienteEntrega.and.returnValue(of([profesor]));
    caja.entregas.and.returnValue(of({ content: [entrega], totalPages: 1, totalElements: 1, number: 0, size: 25 }));
    caja.movimientos.and.returnValue(of(movimientos));
    const auth = { rolesEstanCargados: () => true, tieneRolAdmin: () => admin, obtenerRoles: () => of([]) };
    const endpoints = jasmine.createSpyObj<EndpointsService>('EndpointsService', ['obtenerTodosLosAlumnosSinPaginar']);
    endpoints.obtenerTodosLosAlumnosSinPaginar.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [CajaSedeComponent],
      providers: [
        { provide: CajaService, useValue: caja },
        { provide: AuthenticationService, useValue: auth },
        { provide: EndpointsService, useValue: endpoints },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CajaSedeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => fixture?.destroy());

  const texto = () => (fixture.nativeElement as HTMLElement).textContent?.replace(/ | /g, ' ') ?? '';

  it('carga la primera sede y pinta métricas, profesores, entregas y movimientos', async () => {
    await montar(false);
    expect(caja.resumen).toHaveBeenCalledWith(5, jasmine.objectContaining({ desde: jasmine.any(String), hasta: jasmine.any(String) }));
    expect(texto()).toContain('Saldo de caja');
    expect(texto()).toContain('28,00 €');
    expect(texto()).toContain('Profe Prueba');
    expect(texto()).toContain('hace 9 días');
    expect((fixture.nativeElement as HTMLElement).querySelector('.chip-aviso')).not.toBeNull();
    expect(texto()).toContain('falta 1,00 €');
    expect(texto()).toContain('Faltante');
  });

  it('el gestor no ve «Anular»; el propietario sí, salvo en el faltante automático de una entrega', async () => {
    await montar(false);
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.btn-anular').length).toBe(0);
    fixture.destroy();
    TestBed.resetTestingModule();
    await montar(true);
    const botones = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.btn-anular'));
    expect(botones.length).toBe(2); // la entrega y el movimiento suelto
    expect(component.puedeAnularMovimiento(movimientos[0])).toBeFalse();
  });

  it('diálogo de entrega: todo marcado, contado prefijado, faltante con nota obligatoria', async () => {
    await montar(false);
    component.abrirEntrega(profesor);
    fixture.detectChanges();
    expect(component.calculoEntrega?.declarado).toBe(45);
    expect(component.dialogoEntrega?.contado).toBe('45,00');
    expect(component.puedeRecibir()).toBeTrue();

    component.onContadoInput('44');
    fixture.detectChanges();
    expect(component.calculoEntrega?.estado).toBe('falta');
    expect(texto()).toContain('Faltan 1,00 €. Se apuntará un faltante en la caja.');
    expect(component.puedeRecibir()).toBeFalse();
    component.dialogoEntrega!.nota = 'faltaba un euro';
    expect(component.puedeRecibir()).toBeTrue();
    expect(texto()).toContain('Recibir 44,00 €');
  });

  it('desmarcar un cobro actualiza lo declarado y el contado si no se ha tocado', async () => {
    await montar(false);
    component.abrirEntrega(profesor);
    component.alternarCobro(2, false);
    expect(component.calculoEntrega?.declarado).toBe(30);
    expect(component.dialogoEntrega?.contado).toBe('30,00');
  });

  it('seleccionar todos: tri-estado, alterna todo y recalcula como las casillas sueltas', async () => {
    await montar(false);
    component.abrirEntrega(profesor);
    fixture.detectChanges();
    const casilla = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[aria-label="Seleccionar todos los cobros"]')!;
    expect(component.estadoSeleccionCobros).toBe('todos');
    expect(casilla().checked).toBeTrue();

    component.alternarCobro(1, false);
    fixture.detectChanges();
    expect(component.estadoSeleccionCobros).toBe('algunos');
    expect(casilla().indeterminate).toBeTrue();

    component.alternarTodos(false);
    fixture.detectChanges();
    expect(component.estadoSeleccionCobros).toBe('ninguno');
    expect(casilla().checked).toBeFalse();
    expect(casilla().indeterminate).toBeFalse();
    expect(component.calculoEntrega?.declarado).toBe(0);
    expect(component.dialogoEntrega?.contado).toBe('0,00');

    casilla().click();
    fixture.detectChanges();
    expect(component.estadoSeleccionCobros).toBe('todos');
    expect(component.calculoEntrega?.declarado).toBe(45);
    expect(component.dialogoEntrega?.contado).toBe('45,00');

    // Con lo contado escrito a mano, desmarcar todo no lo pisa y aparece la diferencia.
    component.onContadoInput('45');
    component.alternarTodos(false);
    expect(component.dialogoEntrega?.contado).toBe('45');
    expect(component.calculoEntrega?.estado).toBe('sobra');
  });

  it('envía la entrega con los cobros marcados y lo contado', async () => {
    await montar(false);
    caja.registrarEntrega.and.returnValue(of(entrega));
    component.abrirEntrega(profesor);
    component.onContadoInput('46,5');
    component.dialogoEntrega!.nota = 'sobraba';
    component.recibirEntrega();
    expect(caja.registrarEntrega).toHaveBeenCalledWith(5, { entregadoPorUsuarioId: 9, cobroIds: [1, 2], importeContado: 46.5, nota: 'sobraba' });
    expect(component.dialogoEntrega).toBeNull();
  });

  it('409: cierra, recarga y avisa', async () => {
    await montar(false);
    const fire = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({} as any));
    caja.registrarEntrega.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Algún cobro ya está incluido en una entrega de caja.' } })));
    component.abrirEntrega(profesor);
    const llamadasAntes = caja.pendienteEntrega.calls.count();
    component.recibirEntrega();
    expect(component.dialogoEntrega).toBeNull();
    expect(caja.pendienteEntrega.calls.count()).toBe(llamadasAntes + 1);
    expect((fire.calls.mostRecent().args[0] as any).title).toBe('La entrega ha cambiado');
  });

  it('400: muestra el mensaje de la API en el diálogo', async () => {
    await montar(false);
    caja.registrarEntrega.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'Algún cobro no es de esta sede.' } })));
    component.abrirEntrega(profesor);
    component.recibirEntrega();
    expect(component.dialogoEntrega?.error).toBe('Algún cobro no es de esta sede.');
  });

  it('movimiento: devolución exige alumno y sale con signo negativo', async () => {
    await montar(false);
    caja.crearMovimiento.and.returnValue(of(movimientos[1]));
    component.abrirMovimiento();
    const d = component.dialogoMovimiento!;
    d.tipo = 'DEVOLUCION';
    d.importe = '--15';
    d.nota = 'x';
    d.alumnoId = 21;
    expect(component.importeMovimientoNoValido).toBeTrue();
    expect(component.puedeApuntar()).toBeFalse();
    d.alumnoId = null;
    d.importe = '15';
    d.nota = 'devuelto';
    expect(component.puedeApuntar()).toBeFalse();
    d.alumnoId = 21;
    expect(component.puedeApuntar()).toBeTrue();
    component.apuntarMovimiento();
    expect(caja.crearMovimiento).toHaveBeenCalledWith(5, { tipo: 'DEVOLUCION', importe: -15, alumnoId: 21, nota: 'devuelto' });
  });

  it('cambio rápido de mes: si el mes anterior contesta tarde, se queda el último', async () => {
    await montar(false);
    const respuestas = new Map<string, Subject<any>>();
    const resumen = (desde: string, saldo: number) => ({ sedeId: 5, desde, hasta: desde, pendienteDeEntregar: 0, recibidoEnPeriodo: 0, diferenciasEnPeriodo: 0, saldoCaja: saldo });
    caja.resumen.and.callFake((_sede: number, rango: { desde: string }) => {
      const s = new Subject<any>();
      respuestas.set(rango.desde, s);
      return s.asObservable();
    });
    caja.movimientos.and.callFake((_sede: number, rango: { desde: string }) =>
      of([{ ...movimientos[1], id: rango.desde.length + Number(rango.desde.slice(5, 7)), nota: 'mes ' + rango.desde }]));

    const [, anterior, ultimo] = component.periodos;
    component.periodo = anterior.valor;
    component.onPeriodoChange();
    component.periodo = ultimo.valor;
    component.onPeriodoChange();
    expect(component.cargandoResumen).toBeTrue();

    const desdeAnterior = Array.from(respuestas.keys())[0];
    const desdeUltimo = Array.from(respuestas.keys())[1];
    respuestas.get(desdeUltimo)!.next(resumen(desdeUltimo, 200));
    respuestas.get(desdeUltimo)!.complete();
    expect(component.cargandoResumen).toBeFalse();
    expect(component.resumen?.saldoCaja).toBe(200);

    // El mes anterior contesta después: se descarta.
    respuestas.get(desdeAnterior)!.next(resumen(desdeAnterior, 100));
    respuestas.get(desdeAnterior)!.complete();
    expect(component.resumen?.saldoCaja).toBe(200);
    expect(component.movimientos[0].nota).toBe('mes ' + desdeUltimo);
    expect(component.cargandoResumen).toBeFalse();
    expect(component.cargandoMovimientos).toBeFalse();
  });

  it('paginación de entregas: si la página 1 contesta después de la 2, se queda la 2', async () => {
    await montar(false);
    const respuestas = new Map<number, Subject<any>>();
    caja.entregas.and.callFake((_sede: number, pagina: number) => {
      const s = new Subject<any>();
      respuestas.set(pagina, s);
      return s.asObservable();
    });
    const pagina = (n: number) => ({ content: [{ ...entrega, id: 100 + n }], totalPages: 3, totalElements: 51, number: n - 1, size: 25 });
    component.totalPaginasEntregas = 3;
    component.paginaEntregas = 2;
    component.cambiarPaginaEntregas(1);
    component.cambiarPaginaEntregas(2);
    expect(component.cargandoEntregas).toBeTrue();

    respuestas.get(2)!.next(pagina(2));
    respuestas.get(2)!.complete();
    expect(component.entregas[0].id).toBe(102);
    expect(component.cargandoEntregas).toBeFalse();

    respuestas.get(1)!.next(pagina(1));
    respuestas.get(1)!.complete();
    expect(component.entregas[0].id).toBe(102);
    expect(component.paginaEntregas).toBe(2);
    expect(component.cargandoEntregas).toBeFalse();
  });

  it('periodos: del mes actual hacia atrás', () => {
    const periodos = construirPeriodos(new Date(2026, 0, 15), 3);
    expect(periodos.map((p) => p.etiqueta)).toEqual(['Enero 2026', 'Diciembre 2025', 'Noviembre 2025']);
  });
});
