import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { MiCajaComponent } from './mi-caja.component';
import { estadoCobroEncargo, indicePasoEncargo, requiereAccion } from './mi-caja-pedidos';
import { CajaProfesorService } from '../../../servicios/endpoints/caja-profesor.service';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { CargoPendienteProfesor, CobroEntregadoReciente } from '../../../interfaces/caja';
import { Solicitud } from '../../../interfaces/solicitud';

function solicitud(id: number, extra: Partial<Solicitud> = {}): Solicitud {
  return {
    id, tipo: 'ENCARGO', estado: 'PEDIDO', sedeId: 1, sedeNombre: 'Sede Norte', solicitadoPorUsuarioId: 8,
    solicitadoPorNombre: 'Profe', solicitadoEn: '2026-10-18T15:30:00.000Z', motivo: 'Talla 140',
    resueltoPorUsuarioId: null, resueltoPorNombre: null, resueltoEn: null, respuesta: null, cobroId: null,
    entregaCajaId: null, movimientoCajaId: null, productoAlumnoId: 100 + id, productoId: 4, alumnoId: 3,
    alumnoNombre: 'Alumna', alumnoApellidos: 'Uno', concepto: 'Dobok', importe: 25, motivoDeshacer: null,
    encargadoEn: null, recibidoEn: null, entregadoEn: null, devolucionPendiente: false, propia: true,
    movimientoDevolucionId: null, devolucionEnSede: null, estadoCobro: 'PENDIENTE', ...extra,
  };
}

function cargo(cargoId: number): CargoPendienteProfesor {
  return {
    cargoId, alumnoId: 3, alumnoNombre: 'Alumna', alumnoApellidos: 'Uno', concepto: 'Dobok', importe: 25,
    fechaAsignacion: '2026-10-18T15:30:00.000Z', sedeId: 1, sedeNombre: 'Sede Norte',
  };
}

function entregado(cobroId: number, extra: Partial<CobroEntregadoReciente> = {}): CobroEntregadoReciente {
  return {
    cobroId, cargoId: 7, alumnoId: 3, alumnoNombre: 'Alumna', alumnoApellidos: 'Uno', concepto: 'Camiseta',
    importe: 12.5, fechaCobro: '2026-10-10T10:00:00.000Z', entregaId: 5, fechaEntrega: '2026-10-15T17:00:00.000Z',
    sedeId: 1, sedeNombre: 'Sede Norte', venta: false, solicitudPendienteId: null, noSePuedePedir: null, ...extra,
  };
}

const error = (status: number, mensaje: string) => new HttpErrorResponse({ status, error: { mensaje } });

const SOLICITUDES: Solicitud[] = [
  solicitud(1), // PEDIDO, cargo pendiente
  solicitud(2, { estado: 'RECIBIDO', estadoCobro: 'COBRADO' }), // requiere su acción
  solicitud(3, {
    estado: 'RECHAZADO', respuesta: 'Sin existencias', devolucionPendiente: true, devolucionEnSede: false,
    estadoCobro: 'COBRADO',
  }),
  solicitud(4, { estado: 'RETIRADA', devolucionPendiente: true, devolucionEnSede: true, estadoCobro: 'COBRADO' }),
  solicitud(5, { estado: 'RETIRADA', movimientoDevolucionId: 70, estadoCobro: 'DEVUELTO' }),
  solicitud(6, {
    tipo: 'DESHACER_COBRO_ENTREGADO', estado: 'PENDIENTE', motivo: 'Lo cobré dos veces', cobroId: 50,
    concepto: 'Camiseta', importe: 12.5,
  }),
  solicitud(7, {
    tipo: 'DESHACER_COBRO_ENTREGADO', estado: 'DENEGADA', motivo: 'Error', respuesta: 'Ya lo comprobé', cobroId: 51,
  }),
];

describe('MiCajaComponent (pedidos, 6c-8)', () => {
  let fixture: ComponentFixture<MiCajaComponent>;
  let componente: MiCajaComponent;
  let caja: jasmine.SpyObj<CajaProfesorService>;

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (selector: string) => el().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
  const todos = (selector: string) => Array.from(el().querySelectorAll<HTMLElement>(selector));
  const fila = (id: number) => el().querySelector<HTMLElement>(`[data-solicitud="${id}"]`)!;

  function crear(solicitudes = SOLICITUDES): void {
    caja = jasmine.createSpyObj<CajaProfesorService>('CajaProfesorService', [
      'cargosPendientes', 'cobrarCargo', 'deshacerCobro', 'productos', 'venderProducto', 'cobrosPendientesEntrega',
      'alumnos', 'productosEncargables', 'pedirEncargo', 'misSolicitudes', 'retirarSolicitud', 'marcarEntregado',
      'registrarDevolucion', 'cobrosEntregadosRecientes', 'pedirDeshacer',
    ]);
    caja.cargosPendientes.and.returnValue(of([cargo(101)]));
    caja.cobrosPendientesEntrega.and.returnValue(of({ numeroCobros: 0, importe: 0, cobros: [] }));
    caja.alumnos.and.returnValue(of([{ alumnoId: 3, nombre: 'Alumna', apellidos: 'Uno', grupos: ['Infantil'] }]));
    caja.productosEncargables.and.returnValue(of([{ id: 4, concepto: 'Dobok', precio: 25 }]));
    caja.misSolicitudes.and.returnValue(of(solicitudes));
    caja.cobrosEntregadosRecientes.and.returnValue(of([]));

    TestBed.configureTestingModule({
      imports: [MiCajaComponent],
      providers: [
        { provide: CajaProfesorService, useValue: caja },
        { provide: AuthenticationService, useValue: { getRolesActuales: () => ['ROLE_PROFESOR'], forzarCierreSesion: () => Promise.resolve() } },
        { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) },
      ],
    });
    fixture = TestBed.createComponent(MiCajaComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  }

  function irAPedidos(): void {
    componente.elegirPestana('pedidos');
    fixture.detectChanges();
  }

  describe('reglas', () => {
    it('línea de estados: el paso de cada estado; un encargo caído no tiene paso', () => {
      expect(indicePasoEncargo(solicitud(1))).toBe(0);
      expect(indicePasoEncargo(solicitud(1, { estado: 'ENCARGADO' }))).toBe(1);
      expect(indicePasoEncargo(solicitud(1, { estado: 'RECIBIDO' }))).toBe(2);
      expect(indicePasoEncargo(solicitud(1, { estado: 'ENTREGADO' }))).toBe(3);
      expect(indicePasoEncargo(solicitud(1, { estado: 'RECHAZADO' }))).toBe(-1);
    });

    it('estado del cobro: el que da la API, solo en un encargo', () => {
      expect(estadoCobroEncargo(solicitud(1))).toBe('PENDIENTE');
      expect(estadoCobroEncargo(SOLICITUDES[4])).toBe('DEVUELTO');
      expect(estadoCobroEncargo(solicitud(8, { estado: 'RETIRADA', estadoCobro: 'CANCELADO' }))).toBe('CANCELADO');
      expect(estadoCobroEncargo(solicitud(9, { estadoCobro: null }))).toBeNull();
      expect(estadoCobroEncargo(solicitud(10, { tipo: 'DESHACER_COBRO_ENTREGADO', estadoCobro: 'COBRADO' }))).toBeNull();
    });

    it('requiere su acción: recibido, o devolución pendiente que lleva él encima', () => {
      expect(SOLICITUDES.filter(requiereAccion).map((s) => s.id)).toEqual([2, 3]);
    });
  });

  it('4.ª pestaña «Pedidos» con el contador de lo que requiere su acción', () => {
    crear();
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(1);
    expect(texto('#pestana-pedidos')).toBe('Pedidos 2');
    expect(el().querySelector('#panel-pedidos[role="tabpanel"]')).not.toBeNull();
    expect(el().querySelector<HTMLElement>('#panel-pedidos')!.hidden).toBeTrue();
    irAPedidos();
    expect(el().querySelector<HTMLElement>('#panel-pedidos')!.hidden).toBeFalse();
  });

  it('pinta los encargos con su línea de estados, el cobro y las acciones de cada estado', () => {
    crear();
    irAPedidos();
    const pedido = fila(1);
    expect(pedido.querySelector('[aria-current="step"]')?.textContent?.trim()).toBe('Pedido');
    expect(pedido.querySelector('.chip-cobro')?.textContent?.trim()).toBe('Sin cobrar');
    expect(pedido.querySelector('.btn-retirar')).not.toBeNull();
    expect(pedido.querySelector('.btn-entregado')).toBeNull();

    const recibido = fila(2);
    expect(recibido.querySelector('[aria-current="step"]')?.textContent?.trim()).toBe('Recibido');
    expect(recibido.querySelector('.chip-cobro')?.textContent?.trim()).toBe('Cobrado');
    expect(recibido.querySelector('.btn-entregado')?.textContent?.trim()).toBe('Marcar entregado al alumno');
    expect(recibido.querySelector('.btn-retirar')).toBeNull();

    const rechazado = fila(3);
    expect(rechazado.querySelector('.pasos')).toBeNull();
    expect(rechazado.querySelector('.caida')?.textContent).toContain('Sin existencias');
    expect(rechazado.querySelector('.btn-devolucion')?.textContent?.trim()).toBe('Registrar devolución');

    const enSede = fila(4);
    expect(enSede.querySelector('.btn-devolucion')).toBeNull();
    expect(enSede.textContent).toContain('Devolución pendiente en la sede');

    expect(fila(5).querySelector('.chip-cobro')?.textContent?.trim()).toBe('Devuelto');
    expect(fila(6).querySelector('.chip-cobro')).toBeNull();
  });

  it('solicitudes de deshacer: la pendiente con «Retirar» y la resuelta con la respuesta', () => {
    crear();
    irAPedidos();
    expect(fila(6).textContent).toContain('Esperando al propietario');
    expect(fila(6).querySelector('.btn-retirar')).not.toBeNull();
    expect(fila(7).textContent).toContain('Denegada');
    expect(fila(7).textContent).toContain('Ya lo comprobé');
    expect(fila(7).querySelector('.btn-retirar')).toBeNull();
  });

  it('sin pedidos: estado vacío con «Encargar un producto»', () => {
    crear([]);
    irAPedidos();
    expect(texto('#pestana-pedidos')).toBe('Pedidos 0');
    expect(texto('#panel-pedidos .estado')).toContain('Aún no has encargado nada');
    expect(el().querySelector('.btn-encargar')).not.toBeNull();
  });

  it('error al cargar los pedidos: mensaje del servidor y reintentar', () => {
    crear();
    caja.misSolicitudes.and.returnValue(throwError(() => error(500, 'Servicio no disponible')));
    componente.cargarSolicitudes();
    irAPedidos();
    expect(texto('#panel-pedidos .estado-error p')).toBe('Servicio no disponible');
    caja.misSolicitudes.and.returnValue(of(SOLICITUDES));
    (el().querySelector('#panel-pedidos .estado-error button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fila(1)).not.toBeNull();
  });

  it('encargar sin sede: valida, envía alumno, producto y nota, y recarga pedidos y «Por cobrar»', () => {
    crear();
    irAPedidos();
    (el().querySelector('.btn-encargar') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(texto('.hoja h2')).toBe('Encargar un producto');
    expect(caja.productosEncargables).toHaveBeenCalledTimes(1);

    componente.confirmarEncargo();
    fixture.detectChanges();
    expect(caja.pedirEncargo).not.toHaveBeenCalled();
    expect(texto('.hoja .campo-error')).toBe('Elige el alumno.');

    componente.elegirAlumnoEncargo(componente.alumnos[0]);
    componente.hojaEncargo!.productoId = 4;
    componente.hojaEncargo!.nota = '   ';
    componente.confirmarEncargo();
    expect(caja.pedirEncargo).not.toHaveBeenCalled();
    expect(componente.errorEncargo).toBe('Escribe la nota (talla, color…).');

    caja.pedirEncargo.and.returnValue(of(solicitud(20)));
    caja.misSolicitudes.calls.reset();
    caja.cargosPendientes.calls.reset();
    componente.hojaEncargo!.nota = ' Talla 140 ';
    componente.confirmarEncargo();
    fixture.detectChanges();
    expect(caja.pedirEncargo).toHaveBeenCalledOnceWith(3, { productoId: 4, nota: 'Talla 140' });
    expect(el().querySelector('.hoja')).toBeNull();
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(1);
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(1);
    expect(texto('.aviso-texto')).toBe('Encargo enviado');
  });

  it('encargar con dos sedes: el selector sale al elegir el alumno, solo con sus sedes, y se envía la elegida', () => {
    crear();
    caja.alumnos.and.returnValue(
      of([{
        alumnoId: 3, nombre: 'Alumna', apellidos: 'Uno', grupos: ['A', 'B'],
        sedes: [{ id: 2, nombre: 'Sede Sur' }, { id: 1, nombre: 'Sede Norte' }],
      }])
    );
    irAPedidos();
    componente.abrirEncargo();
    componente.elegirAlumnoEncargo(componente.alumnos[0]);
    fixture.detectChanges();
    expect(componente.hojaEncargo!.pedirSede).toBeTrue();
    const opciones = Array.from(el().querySelectorAll<HTMLOptionElement>('#sedeEncargo option')).map((o) => o.textContent?.trim());
    expect(opciones).toEqual(['Elige la sede', 'Sede Norte', 'Sede Sur']);

    componente.hojaEncargo!.productoId = 4;
    componente.hojaEncargo!.nota = 'Talla 140';
    componente.confirmarEncargo();
    expect(caja.pedirEncargo).not.toHaveBeenCalled();
    expect(componente.errorEncargo).toBe('Elige la sede.');

    caja.pedirEncargo.and.returnValue(of(solicitud(21)));
    componente.hojaEncargo!.sedeId = 2;
    componente.confirmarEncargo();
    expect(caja.pedirEncargo).toHaveBeenCalledOnceWith(3, { productoId: 4, nota: 'Talla 140', sedeId: 2 });
  });

  it('encargar con una sola sede: sin selector y sin sede en la petición', () => {
    crear();
    caja.alumnos.and.returnValue(of([{ alumnoId: 3, nombre: 'Alumna', apellidos: 'Uno', grupos: ['A'], sedes: [{ id: 1, nombre: 'Sede Norte' }] }]));
    componente.abrirEncargo();
    componente.elegirAlumnoEncargo(componente.alumnos[0]);
    fixture.detectChanges();
    expect(componente.hojaEncargo!.pedirSede).toBeFalse();
    expect(el().querySelector('#sedeEncargo')).toBeNull();
  });

  it('si aun así la API pide la sede y el alumno no trae dos, lo explica sin selector ni error repetido', () => {
    crear();
    irAPedidos();
    componente.abrirEncargo();
    componente.elegirAlumnoEncargo(componente.alumnos[0]);
    componente.hojaEncargo!.productoId = 4;
    componente.hojaEncargo!.nota = 'Talla 140';
    caja.pedirEncargo.and.returnValue(
      throwError(() => error(400, 'El alumno está en grupos suyos de varias sedes: indica la sede del encargo.'))
    );
    componente.confirmarEncargo();
    fixture.detectChanges();
    expect(componente.hojaEncargo!.pedirSede).toBeTrue();
    expect(el().querySelector('#sedeEncargo')).toBeNull();
    expect(texto('#ayudaSedeEncargo')).toContain('pide este encargo a la gestión');
    expect(el().querySelector('.hoja .hoja-error')).toBeNull();
  });

  it('retirar un encargo: confirma, llama una vez y recarga; con 409 enseña el mensaje', fakeAsync(() => {
    crear();
    irAPedidos();
    const respuesta = new Subject<Solicitud>();
    caja.retirarSolicitud.and.returnValue(respuesta);
    (fila(1).querySelector('.btn-retirar') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(texto('.hoja h2')).toBe('Retirar el encargo');
    componente.confirmarAccion();
    componente.confirmarAccion();
    expect(caja.retirarSolicitud).toHaveBeenCalledTimes(1);
    caja.misSolicitudes.calls.reset();
    respuesta.next(solicitud(1, { estado: 'RETIRADA' }));
    respuesta.complete();
    fixture.detectChanges();
    expect(el().querySelector('.hoja')).toBeNull();
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(1);
    tick(6000);

    caja.retirarSolicitud.and.returnValue(throwError(() => error(409, 'La solicitud ya no está pendiente.')));
    (fila(1).querySelector('.btn-retirar') as HTMLButtonElement).click();
    componente.confirmarAccion();
    fixture.detectChanges();
    expect(texto('.hoja .hoja-error')).toBe('La solicitud ya no está pendiente.');
  }));

  it('6c-9: si el botón que abrió la hoja ya no existe, el foco va a «Encargar un producto»', fakeAsync(() => {
    crear();
    irAPedidos();
    caja.retirarSolicitud.and.returnValue(of(solicitud(1, { estado: 'RETIRADA' })));
    caja.misSolicitudes.and.returnValue(of(SOLICITUDES.filter((s) => s.id !== 1)));
    (fila(1).querySelector('.btn-retirar') as HTMLButtonElement).click();
    fixture.detectChanges();
    componente.confirmarAccion();
    fixture.detectChanges();
    tick();
    expect(el().querySelector('[data-solicitud="1"]')).toBeNull();
    expect(document.activeElement).toBe(el().querySelector('#panel-pedidos .btn-encargar'));
    tick(6000);
  }));

  it('6c-9: si sigue, el foco vuelve al botón que abrió la hoja', fakeAsync(() => {
    crear();
    irAPedidos();
    const boton = fila(2).querySelector('.btn-entregado') as HTMLButtonElement;
    boton.click();
    fixture.detectChanges();
    componente.cerrarAccion();
    fixture.detectChanges();
    tick();
    expect(document.activeElement).toBe(boton);
  }));

  it('marcar entregado al alumno: confirma y llama a su ruta', fakeAsync(() => {
    crear();
    irAPedidos();
    caja.marcarEntregado.and.returnValue(of(solicitud(2, { estado: 'ENTREGADO' })));
    (fila(2).querySelector('.btn-entregado') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(texto('.hoja h2')).toBe('Marcar entregado al alumno');
    componente.confirmarAccion();
    expect(caja.marcarEntregado).toHaveBeenCalledOnceWith(2);
    tick(6000);
  }));

  it('registrar devolución: confirma alumno, concepto e importe, nota opcional, y recarga lo de encima', fakeAsync(() => {
    crear();
    irAPedidos();
    (fila(3).querySelector('.btn-devolucion') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(texto('.hoja h2')).toBe('Registrar devolución');
    expect(texto('.hoja .hoja-importe')).toBe('25,00 €');
    expect(texto('.hoja .hoja-detalle')).toBe('Alumna Uno · Dobok');
    expect(el().querySelector('.hoja input[type="number"]')).toBeNull();

    caja.registrarDevolucion.and.returnValue(of(solicitud(3, { estado: 'RECHAZADO', movimientoDevolucionId: 71 })));
    caja.cobrosPendientesEntrega.calls.reset();
    componente.confirmarDevolucion();
    expect(caja.registrarDevolucion).toHaveBeenCalledOnceWith(3, null);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(1);
    tick(6000);

    (fila(3).querySelector('.btn-devolucion') as HTMLButtonElement)?.click();
    componente.hojaDevolucion!.nota = '  En mano a la familia ';
    caja.registrarDevolucion.and.returnValue(
      throwError(() => error(409, 'No llevas encima el dinero de este encargo: la devolución la registra la sede.'))
    );
    componente.confirmarDevolucion();
    fixture.detectChanges();
    expect(caja.registrarDevolucion).toHaveBeenCalledWith(3, 'En mano a la familia');
    expect(texto('.hoja .hoja-error')).toContain('la registra la sede');
  }));

  describe('Entregados recientes', () => {
    function abrir(filas: CobroEntregadoReciente[]): void {
      caja.cobrosEntregadosRecientes.and.returnValue(of(filas));
      componente.elegirPestana('encima');
      fixture.detectChanges();
      const boton = el().querySelector<HTMLButtonElement>('.btn-plegable')!;
      expect(boton.getAttribute('aria-expanded')).toBe('false');
      boton.click();
      fixture.detectChanges();
      expect(boton.getAttribute('aria-expanded')).toBe('true');
    }

    it('se cargan al desplegar, una vez, y cada fila dice si se puede pedir', () => {
      crear();
      expect(caja.cobrosEntregadosRecientes).not.toHaveBeenCalled();
      abrir([
        entregado(50),
        entregado(51, { solicitudPendienteId: 6 }),
        entregado(52, { noSePuedePedir: 'El cobro tiene una devolución registrada.' }),
      ]);
      expect(caja.cobrosEntregadosRecientes).toHaveBeenCalledTimes(1);
      const filas = todos('.fila-entregado');
      expect(filas.length).toBe(3);
      expect(filas[0].querySelector('.btn-pedir-deshacer')).not.toBeNull();
      expect(filas[1].textContent).toContain('Esperando al propietario');
      expect(filas[1].querySelector('.btn-pedir-deshacer')).toBeNull();
      expect(filas[2].textContent).toContain('El cobro tiene una devolución registrada.');
      expect(filas[2].querySelector('.btn-pedir-deshacer')).toBeNull();
      // 6c-9: la nota de lo ya entregado solo sale con datos.
      expect(texto('#panel-encima .nota')).toContain('Lo ya entregado está en la caja de la sede');

      componente.alternarEntregados();
      componente.alternarEntregados();
      expect(caja.cobrosEntregadosRecientes).toHaveBeenCalledTimes(1);
    });

    it('pedir deshacer: motivo obligatorio y «no hubo venta» solo en una venta', () => {
      crear();
      abrir([entregado(50), entregado(53, { venta: true })]);
      (todos('.fila-entregado')[0].querySelector('.btn-pedir-deshacer') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(texto('.hoja h2')).toBe('Pedir al propietario que deshaga este cobro');
      expect(el().querySelector('.hoja input[value="NO_HUBO_VENTA"]')).toBeNull();

      componente.confirmarPedirDeshacer();
      fixture.detectChanges();
      expect(caja.pedirDeshacer).not.toHaveBeenCalled();
      expect(texto('.hoja .campo-error')).toBe('Escribe el motivo.');

      caja.pedirDeshacer.and.returnValue(of(solicitud(30, { tipo: 'DESHACER_COBRO_ENTREGADO', estado: 'PENDIENTE' })));
      caja.cobrosEntregadosRecientes.calls.reset();
      caja.misSolicitudes.calls.reset();
      componente.hojaPedirDeshacer!.motivo = ' Lo cobré dos veces ';
      componente.confirmarPedirDeshacer();
      fixture.detectChanges();
      expect(caja.pedirDeshacer).toHaveBeenCalledOnceWith(50, { motivo: 'Lo cobré dos veces', motivoDeshacer: 'AUN_NO_PAGADO' });
      expect(caja.cobrosEntregadosRecientes).toHaveBeenCalledTimes(1);
      expect(caja.misSolicitudes).toHaveBeenCalledTimes(1);
      expect(el().querySelector('.hoja')).toBeNull();

      (todos('.fila-entregado')[1].querySelector('.btn-pedir-deshacer') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(el().querySelector('.hoja input[value="NO_HUBO_VENTA"]')).not.toBeNull();
    });

    it('pedir deshacer con 409: enseña el mensaje y no cierra', () => {
      crear();
      abrir([entregado(50)]);
      componente.abrirPedirDeshacer(entregado(50));
      componente.hojaPedirDeshacer!.motivo = 'Error';
      caja.pedirDeshacer.and.returnValue(throwError(() => error(409, 'Ya hay una solicitud pendiente sobre este cobro.')));
      componente.confirmarPedirDeshacer();
      fixture.detectChanges();
      expect(texto('.hoja .hoja-error')).toBe('Ya hay una solicitud pendiente sobre este cobro.');
      expect(componente.hojaPedirDeshacer!.enviando).toBeFalse();
    });

    it('vacío y error al cargar', () => {
      crear();
      abrir([]);
      expect(texto('.entregados .estado')).toContain('Nada entregado en los últimos 30 días');
      expect(el().querySelector('#panel-encima .nota')).toBeNull();

      caja.cobrosEntregadosRecientes.and.returnValue(throwError(() => error(403, 'Acceso denegado')));
      componente.cargarEntregados();
      fixture.detectChanges();
      expect(texto('.entregados .estado-error p')).toBe('Acceso denegado');
    });
  });
});
