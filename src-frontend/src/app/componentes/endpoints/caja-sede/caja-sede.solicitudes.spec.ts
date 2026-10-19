import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { EntregaCaja, MovimientoCaja } from '../../../interfaces/caja';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { CajaService } from '../../../servicios/endpoints/caja.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { SolicitudesService } from '../../../servicios/endpoints/solicitudes.service';
import { Toast } from '../../../utils/toast.util';
import { CajaSedeComponent } from './caja-sede.component';

const entrega: EntregaCaja = {
  id: 7, sedeId: 5, entregadoPorUsuarioId: 9, entregadoPorNombre: 'Profe Prueba', recibidoPorUsuarioId: 3,
  recibidoPorNombre: 'Gestora Prueba', importeDeclarado: 20, importeContado: 19, diferencia: -1,
  fecha: '2026-10-12T18:00:00', nota: null, estado: 'ACTIVA', cobroIds: [5], movimientoId: 4,
  anuladaPorUsuarioId: null, anuladaEn: null, motivoAnulacion: null, autoentrega: false,
} as EntregaCaja;

const movimientos: MovimientoCaja[] = [
  { id: 4, sedeId: 5, tipo: 'FALTANTE', importe: -1, alumnoId: null, entregaCajaId: 7, nota: null, creadoPorUsuarioId: 3, fecha: '2026-10-12T18:00:00', estado: 'ACTIVO', anuladoPorUsuarioId: null, anuladoEn: null, motivoAnulacion: null },
  { id: 5, sedeId: 5, tipo: 'OTRO', importe: 10, alumnoId: null, entregaCajaId: null, nota: 'cambio', creadoPorUsuarioId: 3, fecha: '2026-10-13T10:00:00', estado: 'ACTIVO', anuladoPorUsuarioId: null, anuladoEn: null, motivoAnulacion: null },
] as MovimientoCaja[];

/** Pieza 6c-7 (D6c-8): en la caja de sede, la gestión pide anular entregas y movimientos sueltos. */
describe('CajaSedeComponent: «Pedir anulación»', () => {
  let fixture: ComponentFixture<CajaSedeComponent>;
  let solicitudes: jasmine.SpyObj<SolicitudesService>;

  async function montar(admin: boolean): Promise<HTMLElement> {
    const caja = jasmine.createSpyObj<CajaService>('CajaService', [
      'sedesGestionables', 'resumen', 'pendienteEntrega', 'entregas', 'movimientos',
    ]);
    caja.sedesGestionables.and.returnValue(of([{ id: 5, nombre: 'Sede Prueba' }]));
    caja.resumen.and.returnValue(of({ sedeId: 5, desde: '2026-10-01', hasta: '2026-10-31', pendienteDeEntregar: 0, recibidoEnPeriodo: 19, diferenciasEnPeriodo: -1, saldoCaja: 28 }));
    caja.pendienteEntrega.and.returnValue(of([]));
    caja.entregas.and.returnValue(of({ content: [entrega], totalPages: 1, totalElements: 1, number: 0, size: 25 }));
    caja.movimientos.and.returnValue(of(movimientos));
    const endpoints = jasmine.createSpyObj<EndpointsService>('EndpointsService', ['obtenerTodosLosAlumnosSinPaginar']);
    endpoints.obtenerTodosLosAlumnosSinPaginar.and.returnValue(of([]));
    solicitudes = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', ['crear']);
    solicitudes.crear.and.returnValue(of({ id: 1 } as any));

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [CajaSedeComponent],
      providers: [
        { provide: CajaService, useValue: caja },
        { provide: AuthenticationService, useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => admin, obtenerRoles: () => of([]) } },
        { provide: EndpointsService, useValue: endpoints },
        { provide: SolicitudesService, useValue: solicitudes },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CajaSedeComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => fixture?.destroy());

  it('la gestión ve «Pedir anulación» en la entrega y en el movimiento suelto, no en el automático', async () => {
    const el = await montar(false);
    const botones = Array.from(el.querySelectorAll<HTMLButtonElement>('.btn-pedir'));

    expect(botones.map((b) => b.textContent?.trim())).toEqual(['Pedir anulación', 'Pedir anulación']);
    expect(botones[0].getAttribute('aria-label')).toContain('entrega de Profe Prueba');
    expect(botones[1].getAttribute('aria-label')).toContain('Otro');
    expect(el.querySelectorAll('.btn-anular').length).toBe(0);
  });

  it('el propietario no ve «Pedir anulación»', async () => {
    const el = await montar(true);
    expect(el.querySelectorAll('.btn-pedir').length).toBe(0);
  });

  it('pide anular la entrega y el movimiento con su tipo y motivo', async () => {
    await montar(false);
    spyOn(Toast, 'fire').and.returnValue(Promise.resolve({} as any));
    const swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true, value: 'contada dos veces' } as any));

    await fixture.componentInstance.pedirAnularEntrega(entrega);
    expect((swal.calls.mostRecent().args[0] as any).title).toBe('Pedir al propietario que anule esta entrega');
    expect(solicitudes.crear).toHaveBeenCalledWith({ tipo: 'ANULAR_ENTREGA', entregaId: 7, motivo: 'contada dos veces' });

    await fixture.componentInstance.pedirAnularMovimiento(movimientos[1]);
    expect((swal.calls.mostRecent().args[0] as any).title).toBe('Pedir al propietario que anule este movimiento');
    expect(solicitudes.crear).toHaveBeenCalledWith({ tipo: 'ANULAR_MOVIMIENTO', movimientoId: 5, motivo: 'contada dos veces' });
  });
});
