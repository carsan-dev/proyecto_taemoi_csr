import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { Solicitud } from '../../../interfaces/solicitud';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { SolicitudesContadorService } from '../../../servicios/endpoints/solicitudes-contador.service';
import { SolicitudesService } from '../../../servicios/endpoints/solicitudes.service';
import { Toast } from '../../../utils/toast.util';
import { DEVOLUCION_OTRA_SEDE, SolicitudesBandejaComponent } from './solicitudes-bandeja.component';
import { solicitudDePrueba } from './solicitudes-bandeja.component.spec';

/** Pieza 6c-9: acciones de los encargos en la bandeja (marcar, rechazar y registrar la devolución). */
describe('SolicitudesBandejaComponent: encargos', () => {
  let servicio: jasmine.SpyObj<SolicitudesService>;
  let contador: { refrescar: jasmine.Spy };
  let swal: jasmine.Spy;
  let toast: jasmine.Spy;
  let fixture: ComponentFixture<SolicitudesBandejaComponent>;

  const encargo = (cambios: Partial<Solicitud>) =>
    solicitudDePrueba({
      id: 20, tipo: 'ENCARGO', estado: 'PEDIDO', cobroId: null, productoAlumnoId: 31, concepto: 'DOBOK T-160',
      motivo: 'Talla 160', importe: 25, estadoCobro: 'PENDIENTE', solicitadoPorNombre: 'Profesor Prueba', ...cambios,
    });
  const pedido = encargo({});
  const encargado = encargo({ id: 21, estado: 'ENCARGADO', estadoCobro: 'COBRADO' });
  const recibido = encargo({ id: 22, estado: 'RECIBIDO' });
  const devolucionSede = encargo({
    id: 23, estado: 'RECHAZADO', respuesta: 'No hay talla', devolucionPendiente: true, devolucionEnSede: true,
    estadoCobro: 'COBRADO',
  });
  const devolucionProfesor = encargo({
    id: 24, estado: 'RETIRADA', devolucionPendiente: true, devolucionEnSede: false, estadoCobro: 'COBRADO',
  });

  async function crear(roles: string[], encargos: Solicitud[]) {
    servicio = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', [
      'listar', 'marcarEncargado', 'marcarRecibido', 'rechazar', 'registrarDevolucion',
    ]);
    servicio.listar.and.callFake((segmento) => of(segmento === 'ENCARGOS' ? encargos : []));
    contador = { refrescar: jasmine.createSpy('refrescar') };
    if (!jasmine.isSpy(Swal.fire)) {
      toast = spyOn(Toast, 'fire').and.returnValue(Promise.resolve({} as any));
      swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true, value: '' } as any));
    }
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [SolicitudesBandejaComponent],
      providers: [
        provideRouter([]),
        { provide: SolicitudesService, useValue: servicio },
        { provide: SolicitudesContadorService, useValue: contador },
        {
          provide: AuthenticationService,
          useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => roles.includes('ROLE_ADMIN') },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SolicitudesBandejaComponent);
    fixture.detectChanges();
    fixture.componentInstance.elegirSegmento('ENCARGOS');
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const filas = (el: HTMLElement) =>
    Array.from(el.querySelectorAll<HTMLElement>('[role="tabpanel"]:not([hidden]) .fila-solicitud'));
  const botonesDe = (fila: HTMLElement) =>
    Array.from(fila.querySelectorAll('.col-acciones button')).map((b) => b.textContent?.trim());

  it('cada estado tiene sus botones; propietario y gestión por igual', async () => {
    for (const roles of [['ROLE_ADMIN'], ['ROLE_MANAGER']]) {
      const el = await crear(roles, [pedido, encargado, recibido, devolucionSede, devolucionProfesor]);
      const [f1, f2, f3, f4, f5] = filas(el);
      expect(botonesDe(f1)).toEqual(['Marcar encargado', 'Rechazar…']);
      expect(botonesDe(f2)).toEqual(['Marcar recibido', 'Rechazar…']);
      expect(botonesDe(f3)).toEqual(['Rechazar…']);
      expect(botonesDe(f4)).toEqual(['Registrar devolución']);
      expect(botonesDe(f5)).toEqual([]);
      expect(f5.textContent).toContain('Devolución pendiente: la registra el profesor');
    }
  });

  it('pinta la línea de estados con el paso actual y el estado del cobro', async () => {
    const el = await crear(['ROLE_ADMIN'], [encargado, devolucionSede]);
    const [enCurso, caido] = filas(el);

    const pasos = Array.from(enCurso.querySelectorAll('.pasos .paso')).map((p) => p.textContent?.trim());
    expect(pasos).toEqual(['Pedido', 'Encargado', 'Recibido', 'Entregado']);
    expect(enCurso.querySelector('[aria-current="step"]')?.textContent?.trim()).toBe('Encargado');
    expect(enCurso.querySelectorAll('.paso.hecho').length).toBe(1);
    expect(enCurso.querySelector('.chip-cobro')?.textContent?.trim()).toBe('Cobrado');

    expect(caido.querySelector('.pasos')).toBeNull();
    expect(caido.textContent).toContain('Motivo del rechazo:');
    expect(caido.textContent).toContain('No hay talla');
  });

  it('marcar encargado y recibido confirman, llaman una vez y recargan con el contador', async () => {
    await crear(['ROLE_MANAGER'], [pedido, encargado]);
    servicio.marcarEncargado.and.returnValue(of(encargo({ estado: 'ENCARGADO' })));
    servicio.marcarRecibido.and.returnValue(of(encargo({ id: 21, estado: 'RECIBIDO' })));
    servicio.listar.calls.reset();

    await fixture.componentInstance.marcarEncargado(pedido);
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ confirmButtonText: 'Marcar encargado' }));
    expect(servicio.marcarEncargado).toHaveBeenCalledOnceWith(20);
    expect(servicio.listar).toHaveBeenCalledWith('ENCARGOS');
    expect(contador.refrescar).toHaveBeenCalled();

    await fixture.componentInstance.marcarRecibido(encargado);
    expect(servicio.marcarRecibido).toHaveBeenCalledOnceWith(21);
    expect(toast).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'success', title: 'Encargo marcado como recibido' }));
  });

  it('al cancelar la confirmación no llama', async () => {
    await crear(['ROLE_ADMIN'], [pedido]);
    swal.and.returnValue(Promise.resolve({ isConfirmed: false } as any));

    await fixture.componentInstance.marcarEncargado(pedido);
    await fixture.componentInstance.rechazar(pedido);

    expect(servicio.marcarEncargado).not.toHaveBeenCalled();
    expect(servicio.rechazar).not.toHaveBeenCalled();
  });

  it('rechazar pide un motivo obligatorio y avisa de lo que pasa con el cargo', async () => {
    await crear(['ROLE_ADMIN'], [pedido, encargado]);
    swal.and.returnValue(Promise.resolve({ isConfirmed: true, value: '  No hay talla  ' } as any));
    servicio.rechazar.and.returnValue(of(encargo({ estado: 'RECHAZADO' })));

    await fixture.componentInstance.rechazar(pedido);
    const opciones = swal.calls.mostRecent().args[0] as any;
    expect(opciones.input).toBe('textarea');
    expect(opciones.inputAttributes.maxlength).toBe('500');
    expect(opciones.inputValidator('  ')).toBeTruthy();
    expect(opciones.inputValidator('No hay talla')).toBeNull();
    expect(opciones.text).toContain('se cancelará');
    expect(servicio.rechazar).toHaveBeenCalledWith(20, 'No hay talla');
    expect(contador.refrescar).toHaveBeenCalled();

    await fixture.componentInstance.rechazar(encargado);
    expect((swal.calls.mostRecent().args[0] as any).text).toContain('quedará una devolución pendiente');
  });

  it('registrar devolución confirma alumno, concepto e importe, sin pedir importe; nota opcional', async () => {
    await crear(['ROLE_ADMIN'], [devolucionSede]);
    servicio.registrarDevolucion.and.returnValue(of(encargo({ id: 23, estado: 'RECHAZADO', estadoCobro: 'DEVUELTO' })));

    await fixture.componentInstance.registrarDevolucion(devolucionSede);

    const opciones = swal.calls.mostRecent().args[0] as any;
    expect(opciones.html).toContain('Alumno De Prueba');
    expect(opciones.html).toContain('DOBOK T-160');
    expect(opciones.html).toContain('25,00');
    expect(opciones.input).toBe('textarea');
    expect(opciones.inputValidator).toBeUndefined();
    expect(servicio.registrarDevolucion).toHaveBeenCalledOnceWith(23, null);
    expect(toast).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'success', title: 'Devolución registrada' }));
    expect(contador.refrescar).toHaveBeenCalled();
  });

  it('con nota, la envía recortada', async () => {
    await crear(['ROLE_ADMIN'], [devolucionSede]);
    swal.and.returnValue(Promise.resolve({ isConfirmed: true, value: ' En mano a la madre ' } as any));
    servicio.registrarDevolucion.and.returnValue(of(devolucionSede));

    await fixture.componentInstance.registrarDevolucion(devolucionSede);

    expect(servicio.registrarDevolucion).toHaveBeenCalledWith(23, 'En mano a la madre');
  });

  it('un 403 al registrar explica que el cobro es de otra sede, no un error genérico', async () => {
    await crear(['ROLE_ADMIN'], [devolucionSede]);
    servicio.registrarDevolucion.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 403, error: { mensaje: 'Acceso denegado' } }))
    );

    await fixture.componentInstance.registrarDevolucion(devolucionSede);

    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'info', text: DEVOLUCION_OTRA_SEDE }));
    expect(toast).not.toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error' }));
    expect(contador.refrescar).toHaveBeenCalled();
  });

  it('un 409 muestra el mensaje del servidor', async () => {
    await crear(['ROLE_ADMIN'], [devolucionSede, pedido]);
    servicio.registrarDevolucion.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'El encargo no tiene una devolución pendiente.' } }))
    );
    servicio.marcarEncargado.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'La solicitud ya no está pendiente' } }))
    );

    await fixture.componentInstance.registrarDevolucion(devolucionSede);
    expect(toast).toHaveBeenCalledWith(
      jasmine.objectContaining({ icon: 'error', title: 'El encargo no tiene una devolución pendiente.' })
    );

    await fixture.componentInstance.marcarEncargado(pedido);
    expect(toast).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', title: 'La solicitud ya no está pendiente' }));
  });

  it('con el dinero en el profesor no deja registrar desde la bandeja', async () => {
    await crear(['ROLE_ADMIN'], [devolucionProfesor]);

    await fixture.componentInstance.registrarDevolucion(devolucionProfesor);

    expect(swal).not.toHaveBeenCalled();
    expect(servicio.registrarDevolucion).not.toHaveBeenCalled();
  });
});
