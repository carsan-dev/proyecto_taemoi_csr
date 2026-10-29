import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import {
  AlumnoDeMisGrupos,
  CargoPendienteProfesor,
  CobroCaja,
  CobroEntregadoReciente,
  MisCobrosPendientes,
  ProductoVendible,
} from '../../interfaces/caja';
import { Solicitud } from '../../interfaces/solicitud';
import { CajaProfesorService } from '../../servicios/endpoints/caja-profesor.service';
import { AccionAviso, AvisosService } from '../avisos/avisos.service';
import { ErrorCampo, OpcionesConfirmacion, ResultadoConfirmacion } from '../dialogo/dialogo.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { MiCajaPanelComponent } from './mi-caja.component';

function cargo(cargoId: number, alumnoId: number, nombre: string, apellidos: string, concepto: string, importe: number): CargoPendienteProfesor {
  return {
    cargoId, alumnoId, alumnoNombre: nombre, alumnoApellidos: apellidos, concepto, importe,
    fechaAsignacion: '2026-10-01T10:00:00.000Z', sedeId: 1, sedeNombre: 'Centro',
  };
}

function cobro(cobroId: number, venta: boolean, importe = 35): CobroCaja {
  return {
    cobroId, cargoId: 1, alumnoId: 3, alumnoNombre: 'Lucía', alumnoApellidos: 'Prado', concepto: 'Mensualidad',
    importe, fechaCobro: new Date().toISOString(), sedeId: 1, venta,
  };
}

function solicitud(parcial: Partial<Solicitud>): Solicitud {
  return {
    id: 1, tipo: 'ENCARGO', estado: 'PEDIDO', sedeId: 1, sedeNombre: 'Centro', solicitadoPorUsuarioId: 9,
    solicitadoPorNombre: 'Sergio', solicitadoEn: '2026-10-14T10:00:00.000Z', motivo: 'Talla 140', resueltoPorUsuarioId: null,
    resueltoPorNombre: null, resueltoEn: null, respuesta: null, cobroId: null, entregaCajaId: null, movimientoCajaId: null,
    productoAlumnoId: 5, productoId: 4, alumnoId: 3, alumnoNombre: 'Hugo', alumnoApellidos: 'Martín', concepto: 'Dobok infantil',
    importe: 28, motivoDeshacer: null, encargadoEn: null, recibidoEn: null, entregadoEn: null, devolucionPendiente: false,
    propia: true, devolucionEnSede: null, estadoCobro: 'PENDIENTE',
    ...parcial,
  };
}

const CARGOS = [
  cargo(1, 3, 'Lucía', 'Prado', 'Mensualidad octubre', 35),
  cargo(2, 5, 'Andrés', 'Gil', 'Mensualidad octubre', 30),
  cargo(3, 3, 'Lucía', 'Prado', 'Camiseta', 12.5),
];

const ENCIMA: MisCobrosPendientes = { numeroCobros: 2, importe: 47.5, cobros: [cobro(10, false), cobro(11, true, 12.5)] };

/** Texto sin espacios duros ni dobles, para comparar importes («35,00 €»). */
const limpio = (t: string | null | undefined) => (t ?? '').replace(/ /g, ' ').replace(/\s+/g, ' ').trim();

describe('MiCajaPanelComponent', () => {
  let fixture: ComponentFixture<MiCajaPanelComponent>;
  let componente: MiCajaPanelComponent;
  let caja: jasmine.SpyObj<CajaProfesorService>;
  let confirmacion: jasmine.SpyObj<ConfirmacionService>;
  let avisos: jasmine.SpyObj<AvisosService>;

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = () => limpio(el().textContent);

  function crear(opciones: { cargos?: unknown; encima?: unknown; solicitudes?: unknown } = {}): void {
    caja = jasmine.createSpyObj<CajaProfesorService>('CajaProfesorService', [
      'cargosPendientes', 'cobrarCargo', 'deshacerCobro', 'productos', 'venderProducto', 'cobrosPendientesEntrega', 'alumnos',
      'misSolicitudes', 'cobrosEntregadosRecientes', 'productosEncargables', 'pedirEncargo', 'retirarSolicitud',
      'marcarEntregado', 'registrarDevolucion', 'pedirDeshacer',
    ]);
    caja.cargosPendientes.and.returnValue((opciones.cargos as never) ?? of(CARGOS));
    caja.cobrosPendientesEntrega.and.returnValue((opciones.encima as never) ?? of(ENCIMA));
    caja.misSolicitudes.and.returnValue((opciones.solicitudes as never) ?? of([]));
    caja.deshacerCobro.and.returnValue(of(undefined));
    caja.alumnos.and.returnValue(of([]));
    caja.productos.and.returnValue(of([]));
    caja.productosEncargables.and.returnValue(of([]));
    caja.cobrosEntregadosRecientes.and.returnValue(of([]));
    confirmacion = jasmine.createSpyObj<ConfirmacionService>('ConfirmacionService', ['confirmar']);
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error', 'info']);

    TestBed.configureTestingModule({
      imports: [MiCajaPanelComponent],
      providers: [
        provideRouter([]),
        { provide: CajaProfesorService, useValue: caja },
        { provide: ConfirmacionService, useValue: confirmacion },
        { provide: AvisosService, useValue: avisos },
      ],
    });
    fixture = TestBed.createComponent(MiCajaPanelComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => fixture?.destroy());

  // ------------------------------------------------------------------ carga

  it('al entrar pide cargos, lo que lleva encima y sus solicitudes; lo demás, a demanda', () => {
    crear();
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(1);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(1);
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(1);
    expect(caja.productos).not.toHaveBeenCalled();
    expect(caja.alumnos).not.toHaveBeenCalled();
    expect(caja.cobrosEntregadosRecientes).not.toHaveBeenCalled();
    expect(caja.productosEncargables).not.toHaveBeenCalled();
  });

  it('lleva el título y no pinta la navegación del profesor ni «Cerrar sesión» propios', () => {
    crear();
    expect(limpio(el().querySelector('h1')?.textContent)).toBe('Mi caja');
    expect(el().querySelector('app-navegacion-profesor')).toBeNull();
    expect(texto()).not.toContain('Cerrar sesión');
    expect(el().querySelector('.pn')).not.toBeNull();
  });

  it('«Llevas encima»: la cifra del servidor y los cobros sin entregar', () => {
    crear();
    const bloque = el().querySelector('.mi-caja__encima')!;
    expect(limpio(bloque.querySelector('.mi-caja__cifra')?.textContent)).toBe('47,50 €');
    expect(limpio(bloque.textContent)).toContain('2 cobros sin entregar');
  });

  it('«Llevas encima» en singular con un cobro', () => {
    crear({ encima: of({ numeroCobros: 1, importe: 35, cobros: [cobro(10, false)] }) });
    expect(limpio(el().querySelector('.mi-caja__encima')?.textContent)).toContain('1 cobro sin entregar');
  });

  it('mientras carga enseña los esqueletos de «Llevas encima» y de la lista', fakeAsync(() => {
    crear({ cargos: new Subject(), encima: new Subject() });
    tick(300);
    fixture.detectChanges();
    expect(el().querySelector('.mi-caja__encima pn-esqueleto')).not.toBeNull();
    expect(el().querySelector('#mi-caja-panel-cobrar pn-esqueleto')).not.toBeNull();
    flush();
  }));

  it('el error de «Llevas encima» deja reintentar solo ese bloque', () => {
    crear({ encima: throwError(() => new HttpErrorResponse({ status: 500 })) });
    const error = el().querySelector('.mi-caja__encima pn-bloque-error');
    expect(error).not.toBeNull();
    caja.cobrosPendientesEntrega.and.returnValue(of(ENCIMA));
    error!.querySelector<HTMLButtonElement>('button')!.click();
    fixture.detectChanges();
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(2);
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(1);
    expect(limpio(el().querySelector('.mi-caja__cifra')?.textContent)).toBe('47,50 €');
  });

  it('el error de los cargos se ve con «Reintentar» y vuelve a pedirlos', () => {
    crear({ cargos: throwError(() => new HttpErrorResponse({ status: 500 })) });
    const error = el().querySelector('#mi-caja-panel-cobrar pn-bloque-error');
    expect(limpio(error?.textContent)).toContain('No se pudieron cargar los cobros pendientes.');
    caja.cargosPendientes.and.returnValue(of(CARGOS));
    error!.querySelector<HTMLButtonElement>('button')!.click();
    fixture.detectChanges();
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(2);
    expect(el().querySelector('#mi-caja-panel-cobrar pn-bloque-error')).toBeNull();
  });

  it('sin cargos dice que no hay nada pendiente', () => {
    crear({ cargos: of([]) });
    expect(limpio(el().querySelector('#mi-caja-panel-cobrar pn-bloque-vacio')?.textContent)).toContain('Nada pendiente');
  });

  // ------------------------------------------------------------------ por cobrar

  it('agrupa por alumno con iniciales, concepto, fecha, importe y «Cobrar»', () => {
    crear();
    const alumnos = Array.from(el().querySelectorAll('.mi-caja__alumno'));
    expect(alumnos.map((a) => limpio(a.querySelector('.mi-caja__nombre')?.textContent))).toEqual(['Andrés Gil', 'Lucía Prado']);
    expect(limpio(alumnos[1].querySelector('.mi-caja__avatar')?.textContent)).toBe('LP');
    const filas = alumnos[1].querySelectorAll('.mi-caja__cargo');
    expect(filas.length).toBe(2);
    expect(limpio(filas[0].textContent)).toContain('Mensualidad octubre');
    expect(limpio(filas[0].textContent)).toContain('desde 01/10');
    expect(limpio(filas[0].querySelector('.mi-caja__importe')?.textContent)).toBe('35,00 €');
    expect(filas[0].querySelector('button')?.getAttribute('aria-label')).toBe('Cobrar Mensualidad octubre a Lucía Prado');
    expect(texto()).toContain('Solo alumnos de tus grupos. No cambias importes.');
    expect(texto()).not.toContain('vencido');
  });

  it('formatea los euros en español con miles', () => {
    crear({ cargos: of([cargo(9, 3, 'Lucía', 'Prado', 'Equipación completa', 1010)]) });
    expect(limpio(el().querySelector('.mi-caja__importe')?.textContent)).toBe('1.010,00 €');
  });

  it('el buscador filtra sin tildes, cuenta lo que se ve y avisa si no hay coincidencias', () => {
    crear();
    expect(limpio(el().querySelector('.mi-caja__contador')?.textContent)).toBe('Mostrando 3 de 3');
    const buscador = el().querySelector<HTMLInputElement>('#mi-caja-buscar')!;
    buscador.value = 'lucia';
    buscador.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(el().querySelectorAll('.mi-caja__alumno').length).toBe(1);
    expect(limpio(el().querySelector('.mi-caja__contador')?.textContent)).toBe('Mostrando 2 de 3');
    buscador.value = 'zzz';
    buscador.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(limpio(el().querySelector('#mi-caja-panel-cobrar pn-bloque-vacio')?.textContent)).toContain(
      'Ningún alumno con cargos pendientes se llama «zzz».'
    );
  });

  // ------------------------------------------------------------------ cobrar

  it('«Cobrar» abre el diálogo de cobro y hace la misma llamada que hoy', async () => {
    crear();
    const cobrado: CobroCaja = { ...cobro(77, false, 35), cargoId: 1 };
    caja.cobrarCargo.and.returnValue(of(cobrado));
    let opciones!: OpcionesConfirmacion;
    confirmacion.confirmar.and.callFake(((o: OpcionesConfirmacion) => {
      opciones = o;
      return new Promise<ResultadoConfirmacion>((resolver) =>
        o.ejecutar!(null).subscribe((respuesta) => resolver({ confirmado: true, valor: null, respuesta }))
      );
    }) as never);

    await componente.abrirCobro(CARGOS[0]);

    expect(opciones.titulo).toBe('Cobrar a Lucía Prado');
    expect(limpio(opciones.accion)).toBe('Cobrar 35,00 €');
    expect(opciones.accionOcupada).toBe('Cobrando…');
    expect(opciones.destacado?.etiqueta).toBe('Mensualidad octubre');
    expect(limpio(opciones.destacado?.valor)).toBe('35,00 €');
    expect(opciones.mensaje).toBe('Queda en tu efectivo hasta que lo entregues en la sede.');
    expect(caja.cobrarCargo).toHaveBeenCalledOnceWith(1);
    expect(avisos.exito).toHaveBeenCalledTimes(1);
    const [textoAviso, accion] = avisos.exito.calls.mostRecent().args;
    expect(limpio(textoAviso)).toBe('Cobrado 35,00 €');
    expect(accion?.texto).toBe('Deshacer');
    // Tras cobrar, lo pendiente y lo que lleva encima se vuelven a leer.
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(2);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(2);
  });

  it('«Deshacer» del aviso hace la misma llamada que hoy y vuelve a leer los contadores', async () => {
    crear();
    caja.cobrarCargo.and.returnValue(of(cobro(77, false, 35)));
    confirmacion.confirmar.and.callFake(((o: OpcionesConfirmacion) =>
      new Promise<ResultadoConfirmacion>((resolver) =>
        o.ejecutar!(null).subscribe((respuesta) => resolver({ confirmado: true, valor: null, respuesta }))
      )) as never);
    await componente.abrirCobro(CARGOS[0]);
    const accion = avisos.exito.calls.mostRecent().args[1] as AccionAviso;

    accion.alPulsar();
    accion.alPulsar();

    expect(caja.deshacerCobro).toHaveBeenCalledOnceWith(77, 'AUN_NO_PAGADO');
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(3);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(3);
  });

  it('si deshacer falla, lo avisa y vuelve a leer', async () => {
    crear();
    caja.cobrarCargo.and.returnValue(of(cobro(77, false, 35)));
    caja.deshacerCobro.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya entregado' } })));
    confirmacion.confirmar.and.callFake(((o: OpcionesConfirmacion) =>
      new Promise<ResultadoConfirmacion>((resolver) =>
        o.ejecutar!(null).subscribe((respuesta) => resolver({ confirmado: true, valor: null, respuesta }))
      )) as never);
    await componente.abrirCobro(CARGOS[0]);
    (avisos.exito.calls.mostRecent().args[1] as AccionAviso).alPulsar();
    expect(avisos.error).toHaveBeenCalledOnceWith('Ya entregado');
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(3);
  });

  it('«Deshacer» del aviso sobrevive a la pantalla: hace la misma llamada y no recarga nada', async () => {
    crear();
    caja.cobrarCargo.and.returnValue(of(cobro(77, false, 35)));
    dialogoQueConfirma();
    await componente.abrirCobro(CARGOS[0]);
    const accion = avisos.exito.calls.mostRecent().args[1] as AccionAviso;
    const lecturas = caja.cargosPendientes.calls.count();

    fixture.destroy();
    accion.alPulsar();

    expect(caja.deshacerCobro).toHaveBeenCalledOnceWith(77, 'AUN_NO_PAGADO');
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(lecturas);
  });

  it('si la recarga de cargos y de «Encima» fallan tras cobrar, el foco no queda pendiente y una carga posterior no lo mueve', fakeAsync(() => {
    crear();
    caja.cobrarCargo.and.returnValue(of(cobro(77, false, 35)));
    caja.cargosPendientes.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    caja.cobrosPendientesEntrega.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    dialogoQueConfirma();
    componente.abrirCobro(CARGOS[0]);
    flush();
    expect(componente['focoPorRecolocar']).toBeFalse();

    caja.cargosPendientes.and.returnValue(of(CARGOS));
    caja.cobrosPendientesEntrega.and.returnValue(of(ENCIMA));
    (document.activeElement as HTMLElement | null)?.blur();
    componente.cargarCargos();
    flush();
    expect(document.activeElement).toBe(document.body);
  }));

  it('cancelar el diálogo no cobra nada', async () => {
    crear();
    confirmacion.confirmar.and.resolveTo({ confirmado: false, valor: null });
    await componente.abrirCobro(CARGOS[0]);
    expect(caja.cobrarCargo).not.toHaveBeenCalled();
    expect(avisos.exito).not.toHaveBeenCalled();
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(1);
  });

  it('si el cobro falla, lo avisa con el mensaje del servidor y vuelve a leer', async () => {
    crear();
    caja.cobrarCargo.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya cobrado' } })));
    confirmacion.confirmar.and.callFake(((o: OpcionesConfirmacion) =>
      new Promise<ResultadoConfirmacion>((resolver) =>
        o.ejecutar!(null).subscribe({ error: (error) => resolver({ confirmado: true, valor: null, error }) })
      )) as never);
    await componente.abrirCobro(CARGOS[0]);
    expect(avisos.error).toHaveBeenCalledOnceWith('Ya cobrado');
    expect(avisos.exito).not.toHaveBeenCalled();
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(2);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(2);
  });

  it('no cobra dos veces el mismo cargo mientras el cobro está en vuelo', async () => {
    crear();
    const respuesta = new Subject<CobroCaja>();
    caja.cobrarCargo.and.returnValue(respuesta);
    let resolver!: (r: ResultadoConfirmacion) => void;
    confirmacion.confirmar.and.callFake(((o: OpcionesConfirmacion) =>
      new Promise<ResultadoConfirmacion>((r) => {
        resolver = r;
        o.ejecutar!(null).subscribe((c) => r({ confirmado: true, valor: null, respuesta: c }));
      })) as never);
    const primero = componente.abrirCobro(CARGOS[0]);
    fixture.detectChanges();
    const boton = el().querySelector<HTMLButtonElement>('button[aria-label="Cobrar Mensualidad octubre a Lucía Prado"]')!;
    expect(boton.getAttribute('aria-busy')).toBe('true');
    expect(el().querySelector('button[aria-label="Cobrar Mensualidad octubre a Andrés Gil"]')?.getAttribute('aria-busy')).toBeNull();
    await componente.abrirCobro(CARGOS[0]);
    expect(confirmacion.confirmar).toHaveBeenCalledTimes(1);
    respuesta.next(cobro(77, false));
    await primero;
    expect(resolver).toBeDefined();
    expect(caja.cobrarCargo).toHaveBeenCalledTimes(1);
  });

  // ------------------------------------------------------------------ pestañas

  it('las pestañas llevan sus contadores y se recorren con las flechas', fakeAsync(() => {
    crear();
    const pestanas = () => Array.from(el().querySelectorAll<HTMLButtonElement>('[role="tab"]'));
    expect(pestanas().map((p) => limpio(p.textContent))).toEqual(['Por cobrar 3', 'Vender', 'Encima 2', 'Pedidos 0']);
    expect(pestanas()[0].getAttribute('aria-selected')).toBe('true');
    expect(pestanas()[0].tabIndex).toBe(0);
    expect(pestanas()[1].tabIndex).toBe(-1);

    const lista = el().querySelector('[role="tablist"]')!;
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    tick();
    expect(componente.pestana).toBe('vender');
    expect(document.activeElement).toBe(pestanas()[1]);

    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();
    tick();
    expect(componente.pestana).toBe('pedidos');
    expect(document.activeElement).toBe(pestanas()[3]);
    expect(el().querySelector('#mi-caja-panel-pedidos')?.hasAttribute('hidden')).toBeFalse();
    expect(el().querySelector('#mi-caja-panel-cobrar')?.hasAttribute('hidden')).toBeTrue();
  }));

  it('el contador de «Pedidos» va en ámbar si algo espera su acción', () => {
    crear({ solicitudes: of([solicitud({ id: 1, estado: 'RECIBIDO' }), solicitud({ id: 2, estado: 'PEDIDO' })]) });
    const contador = el().querySelector('#mi-caja-pestana-pedidos .mi-caja__n');
    expect(limpio(contador?.textContent)).toBe('1');
    expect(contador?.classList).toContain('mi-caja__n--aviso');
  });


  // ------------------------------------------------------------------ encima

  it('«Encima» lista lo que lleva sin entregar y despliega los entregados a demanda', () => {
    crear();
    const entregados: CobroEntregadoReciente[] = [
      { cobroId: 20, cargoId: 2, alumnoId: 3, alumnoNombre: 'Pablo', alumnoApellidos: 'Navarro', concepto: 'Mensualidad septiembre',
        importe: 35, fechaCobro: '2026-09-30', entregaId: 4, fechaEntrega: '2026-10-03T10:00:00', sedeId: 1, sedeNombre: null,
        venta: false, solicitudPendienteId: null, noSePuedePedir: null },
      { cobroId: 21, cargoId: 3, alumnoId: 4, alumnoNombre: 'Sofía', alumnoApellidos: 'Molina', concepto: 'Mensualidad septiembre',
        importe: 30, fechaCobro: '2026-09-30', entregaId: 4, fechaEntrega: '2026-10-03T10:00:00', sedeId: 1, sedeNombre: null,
        venta: false, solicitudPendienteId: 8, noSePuedePedir: null },
    ];
    caja.cobrosEntregadosRecientes.and.returnValue(of(entregados));
    componente.elegirPestana('encima');
    fixture.detectChanges();
    const panel = el().querySelector('#mi-caja-panel-encima')!;
    expect(limpio(panel.querySelector('.mi-caja__sin-entregar h2')?.textContent)).toBe('Sin entregar');
    expect(limpio(panel.querySelector('.mi-caja__sin-entregar .mi-caja__importe')?.textContent)).toBe('47,50 €');
    const filas = panel.querySelectorAll('.mi-caja__encima-lista .mi-caja__fila');
    expect(filas.length).toBe(2);
    expect(limpio(filas[1].textContent)).toContain('Mensualidad · venta ·');
    expect(caja.cobrosEntregadosRecientes).not.toHaveBeenCalled();

    const plegable = panel.querySelector<HTMLButtonElement>('.mi-caja__plegable')!;
    expect(plegable.getAttribute('aria-expanded')).toBe('false');
    plegable.click();
    fixture.detectChanges();
    expect(caja.cobrosEntregadosRecientes).toHaveBeenCalledTimes(1);
    expect(plegable.getAttribute('aria-expanded')).toBe('true');
    const recientes = panel.querySelectorAll('.mi-caja__entregados .mi-caja__fila');
    expect(limpio(recientes[0].textContent)).toContain('Pedir deshacer');
    expect(limpio(recientes[1].textContent)).toContain('Esperando al propietario');
    expect(limpio(panel.textContent)).toContain('Lo ya entregado está en la caja de la sede: deshacerlo se pide al propietario.');
  });

  it('«Encima» vacío', () => {
    crear({ encima: of({ numeroCobros: 0, importe: 0, cobros: [] }) });
    componente.elegirPestana('encima');
    fixture.detectChanges();
    expect(limpio(el().querySelector('#mi-caja-panel-encima pn-bloque-vacio')?.textContent)).toContain('No llevas nada encima');
  });

  // ------------------------------------------------------------------ pedidos

  it('«Pedidos» separa en curso, terminados y deshacer, con hitos, caída y devolución', () => {
    crear({
      solicitudes: of([
        solicitud({ id: 1, estado: 'RECIBIDO', solicitadoEn: '2026-10-14T10:00:00.000Z' }),
        solicitud({ id: 2, estado: 'RECHAZADO', respuesta: 'Sin existencias', estadoCobro: 'CANCELADO' }),
        solicitud({ id: 3, estado: 'RETIRADA', devolucionPendiente: true, devolucionEnSede: false, estadoCobro: 'COBRADO' }),
        solicitud({ id: 4, tipo: 'DESHACER_COBRO_ENTREGADO', estado: 'DENEGADA', motivo: 'Me equivoqué', respuesta: 'Ya cuadrado',
          concepto: 'Mensualidad septiembre', importe: 35 }),
      ]),
    });
    componente.elegirPestana('pedidos');
    fixture.detectChanges();
    const panel = el().querySelector('#mi-caja-panel-pedidos')!;
    const titulos = Array.from(panel.querySelectorAll('h2')).map((h) => limpio(h.textContent));
    expect(titulos).toEqual(['En curso', 'Terminados', 'Deshacer cobros entregados']);

    const recibido = panel.querySelector('[data-solicitud="1"]')!;
    expect(limpio(recibido.querySelector('[aria-current="step"]')?.textContent)).toBe('Recibido');
    expect(limpio(recibido.textContent)).toContain('Marcar entregado al alumno');
    expect(limpio(recibido.textContent)).toContain('Sin cobrar');
    expect(limpio(recibido.textContent)).toContain('Dobok infantil · pedido el 14/10');

    const devolucion = panel.querySelector('[data-solicitud="3"]')!;
    expect(limpio(devolucion.textContent)).toContain('Registrar devolución');
    expect(limpio(devolucion.textContent)).toContain('Retirado');

    const rechazado = panel.querySelector('[data-solicitud="2"]')!;
    expect(limpio(rechazado.querySelector('.mi-caja__caida')?.textContent)).toBe('Rechazado: Sin existencias');
    expect(rechazado.querySelector('.mi-caja__hitos')).toBeNull();

    const deshacer = panel.querySelector('[data-solicitud="4"]')!;
    expect(limpio(deshacer.textContent)).toContain('Denegada');
    expect(limpio(deshacer.textContent)).toContain('Respuesta: Ya cuadrado');
    expect(limpio(panel.textContent)).toContain('Encargar un producto');
  });

  it('«Pedidos» vacío y con error', () => {
    crear({ solicitudes: throwError(() => new HttpErrorResponse({ status: 500 })) });
    componente.elegirPestana('pedidos');
    fixture.detectChanges();
    expect(el().querySelector('#mi-caja-panel-pedidos pn-bloque-error')).not.toBeNull();
    caja.misSolicitudes.and.returnValue(of([]));
    el().querySelector<HTMLButtonElement>('#mi-caja-panel-pedidos pn-bloque-error button')!.click();
    fixture.detectChanges();
    expect(limpio(el().querySelector('#mi-caja-panel-pedidos pn-bloque-vacio')?.textContent)).toContain('Aún no has encargado nada');
  });
  // ------------------------------------------------------------------ ronda 2: utilidades

  const ALUMNOS: AlumnoDeMisGrupos[] = [
    { alumnoId: 5, nombre: 'Lucía', apellidos: 'Prado', grupos: [], sedes: [{ id: 2, nombre: 'Umbrete' }, { id: 1, nombre: 'Centro' }] },
    { alumnoId: 3, nombre: 'Hugo', apellidos: 'Martín', grupos: ['Taekwondo infantil'] },
  ];
  const PRODUCTOS: ProductoVendible[] = [
    { id: 4, concepto: 'Camiseta del club', precio: 12.5 },
    { id: 6, concepto: 'Botella', precio: 8 },
  ];
  const ENTREGADO: CobroEntregadoReciente = {
    cobroId: 20, cargoId: 2, alumnoId: 3, alumnoNombre: 'Pablo', alumnoApellidos: 'Navarro', concepto: 'Mensualidad septiembre',
    importe: 35, fechaCobro: '2026-09-30', entregaId: 4, fechaEntrega: '2026-10-03T10:00:00', sedeId: 1, sedeNombre: 'Umbrete',
    venta: false, solicitudPendienteId: null, noSePuedePedir: null,
  };

  /**
   * El diálogo del panel simulado: guarda las opciones, pasa `comprobar` con los valores de sus campos (o los dados) y,
   * si no hay error, ejecuta como el de verdad (cierra con la respuesta o con el error).
   */
  function dialogoQueConfirma(valores: Record<string, string> = {}): OpcionesConfirmacion[] {
    const vistas: OpcionesConfirmacion[] = [];
    confirmacion.confirmar.and.callFake(((o: OpcionesConfirmacion) => {
      vistas.push(o);
      const todos: Record<string, string> = {
        ...Object.fromEntries((o.campos ?? []).map((c) => [c.id, c.valor ?? ''])),
        ...valores,
      };
      const error = o.comprobar ? (o.comprobar(todos) as ErrorCampo | null) : null;
      if (error) {
        return Promise.resolve({ confirmado: false, valor: null, comprobacion: error });
      }
      return new Promise<ResultadoConfirmacion>((resolver) => {
        if (!o.ejecutar) {
          resolver({ confirmado: true, valor: null, valores: todos });
          return;
        }
        o.ejecutar(null).subscribe({
          next: (respuesta) => resolver({ confirmado: true, valor: null, respuesta }),
          error: (e) => resolver({ confirmado: true, valor: null, error: e }),
        });
      });
    }) as never);
    return vistas;
  }

  const partesTexto = (m: OpcionesConfirmacion['mensaje']) =>
    limpio((typeof m === 'string' ? [m] : m ?? []).map((p) => (typeof p === 'string' ? p : p.fuerte)).join(''));

  function escribir(selector: string, valor: string): void {
    const control = el().querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function abrirVender(): void {
    caja.alumnos.and.returnValue(of(ALUMNOS));
    caja.productos.and.returnValue(of(PRODUCTOS));
    componente.elegirPestana('vender');
    fixture.detectChanges();
  }

  // ------------------------------------------------------------------ vender

  it('«Vender» pide alumnos y catálogo a demanda, una sola vez, como hoy', () => {
    crear();
    expect(caja.alumnos).not.toHaveBeenCalled();
    abrirVender();
    componente.elegirPestana('cobrar');
    componente.elegirPestana('vender');
    fixture.detectChanges();
    expect(caja.alumnos).toHaveBeenCalledTimes(1);
    expect(caja.productos).toHaveBeenCalledTimes(1);
    const panel = el().querySelector('#mi-caja-panel-vender')!;
    expect(Array.from(panel.querySelectorAll('h2')).map((h) => limpio(h.textContent))).toEqual(['Alumno de tus grupos', 'Catálogo (precio fijo)']);
    // Alumnos por nombre, con el contador y la pista; «Vender» desactivado sin alumno.
    expect(Array.from(panel.querySelectorAll('.mi-caja__elegible .mi-caja__fila-nombre')).map((n) => limpio(n.textContent))).toEqual([
      'Hugo Martín',
      'Lucía Prado',
    ]);
    expect(limpio(panel.querySelector('.mi-caja__contador')?.textContent)).toBe('Mostrando 2 de 2');
    expect(limpio(panel.textContent)).toContain('Elige primero el alumno.');
    const vender = panel.querySelectorAll<HTMLButtonElement>('.mi-caja__catalogo button');
    expect(vender.length).toBe(2);
    expect(vender[0].disabled).toBeTrue();
    expect(limpio(panel.querySelector('.mi-caja__catalogo .mi-caja__importe')?.textContent)).toBe('12,50 €');
    expect(limpio(panel.textContent)).toContain('Sin mensualidades, licencias, exámenes, reservas ni productos de grado.');
  });

  it('el error de «Vender» deja reintentar y vuelve a pedir las dos cosas', () => {
    crear();
    caja.alumnos.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    caja.productos.and.returnValue(of(PRODUCTOS));
    componente.elegirPestana('vender');
    fixture.detectChanges();
    const error = el().querySelector('#mi-caja-panel-vender pn-bloque-error');
    expect(limpio(error?.textContent)).toContain('No se pudo cargar el catálogo ni tus alumnos.');
    caja.alumnos.and.returnValue(of(ALUMNOS));
    error!.querySelector<HTMLButtonElement>('button')!.click();
    fixture.detectChanges();
    expect(caja.alumnos).toHaveBeenCalledTimes(2);
    expect(caja.productos).toHaveBeenCalledTimes(2);
    expect(el().querySelector('#mi-caja-panel-vender pn-bloque-error')).toBeNull();
  });

  it('«Vender» sin productos ni alumnos enseña sus vacíos de hoy', () => {
    crear();
    caja.alumnos.and.returnValue(of([]));
    caja.productos.and.returnValue(of([]));
    componente.elegirPestana('vender');
    fixture.detectChanges();
    const vacios = Array.from(el().querySelectorAll('#mi-caja-panel-vender pn-bloque-vacio')).map((v) => limpio(v.textContent));
    expect(vacios[0]).toContain('No tienes alumnos activos en tus grupos.');
    expect(vacios[1]).toContain('Aún no hay nada que vender');
  });

  it('el buscador de «Vender» filtra sin tildes y avisa si no hay nadie', () => {
    crear();
    abrirVender();
    escribir('#mi-caja-buscar-alumno', 'lucia');
    expect(el().querySelectorAll('#mi-caja-panel-vender .mi-caja__elegible').length).toBe(1);
    expect(limpio(el().querySelector('#mi-caja-panel-vender .mi-caja__contador')?.textContent)).toBe('Mostrando 1 de 2');
    escribir('#mi-caja-buscar-alumno', 'zzz');
    expect(limpio(el().querySelector('#mi-caja-panel-vender pn-bloque-vacio')?.textContent)).toContain(
      'Ningún alumno de tus grupos se llama «zzz».'
    );
  });

  it('«Vender» hace la misma llamada que hoy, avisa «Vendido» con «Deshacer» (no hubo venta) y recarga', async () => {
    crear();
    abrirVender();
    el().querySelector<HTMLButtonElement>('#mi-caja-panel-vender .mi-caja__elegible')!.click();
    fixture.detectChanges();
    expect(limpio(el().querySelector('#mi-caja-panel-vender .mi-caja__fila--alumno')?.textContent)).toContain('Hugo Martín');
    expect(limpio(el().querySelector('#mi-caja-cambiar-alumno')?.textContent)).toBe('Cambiar');
    expect(el().querySelector<HTMLButtonElement>('.mi-caja__catalogo button')!.disabled).toBeFalse();
    expect(limpio(el().querySelector('#mi-caja-panel-vender')?.textContent)).not.toContain('Elige primero el alumno.');

    caja.venderProducto.and.returnValue(of(cobro(88, true, 12.5)));
    const vistas = dialogoQueConfirma();
    await componente.abrirVenta(PRODUCTOS[0]);

    expect(caja.venderProducto).toHaveBeenCalledOnceWith(3, 4);
    const o = vistas[0];
    expect(o.titulo).toBe('Vender y cobrar en efectivo');
    expect(limpio(o.accion)).toBe('Vender 12,50 €');
    expect(o.accionOcupada).toBe('Vendiendo…');
    expect(o.destacado?.etiqueta).toBe('Hugo Martín · Camiseta del club');
    expect(limpio(o.destacado?.valor)).toBe('12,50 €');
    expect(o.mensaje).toBe('Queda en tu efectivo hasta que lo entregues en la sede.');
    const [textoAviso, accion] = avisos.exito.calls.mostRecent().args;
    expect(limpio(textoAviso)).toBe('Vendido 12,50 €');
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(2);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(2);
    // Como hoy: el alumno sigue elegido para otra venta.
    expect(componente.alumnoVenta?.alumnoId).toBe(3);

    (accion as AccionAviso).alPulsar();
    expect(caja.deshacerCobro).toHaveBeenCalledOnceWith(88, 'NO_HUBO_VENTA');
  });

  it('«Deshacer» de una venta sobrevive a la pantalla: «No hubo venta» y no recarga nada', async () => {
    crear();
    abrirVender();
    componente.elegirAlumno(ALUMNOS[1]);
    caja.venderProducto.and.returnValue(of(cobro(88, true, 12.5)));
    dialogoQueConfirma();
    await componente.abrirVenta(PRODUCTOS[0]);
    const accion = avisos.exito.calls.mostRecent().args[1] as AccionAviso;
    const lecturas = caja.cargosPendientes.calls.count();

    fixture.destroy();
    accion.alPulsar();

    expect(caja.deshacerCobro).toHaveBeenCalledOnceWith(88, 'NO_HUBO_VENTA');
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(lecturas);
  });

  it('si la venta falla, avisa con el mensaje del servidor y recarga', async () => {
    crear();
    abrirVender();
    componente.elegirAlumno(ALUMNOS[1]);
    caja.venderProducto.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Producto retirado' } })));
    dialogoQueConfirma();
    await componente.abrirVenta(PRODUCTOS[1]);
    expect(caja.venderProducto).toHaveBeenCalledOnceWith(3, 6);
    expect(avisos.error).toHaveBeenCalledOnceWith('Producto retirado');
    expect(avisos.exito).not.toHaveBeenCalled();
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(2);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(2);
  });

  it('sin alumno no se vende; «Cambiar» vuelve al buscador', fakeAsync(() => {
    crear();
    abrirVender();
    componente.abrirVenta(PRODUCTOS[0]);
    expect(confirmacion.confirmar).not.toHaveBeenCalled();
    componente.elegirAlumno(ALUMNOS[0]);
    fixture.detectChanges();
    tick();
    expect(document.activeElement?.id).toBe('mi-caja-cambiar-alumno');
    el().querySelector<HTMLButtonElement>('#mi-caja-cambiar-alumno')!.click();
    fixture.detectChanges();
    tick();
    expect(componente.alumnoVenta).toBeNull();
    expect(document.activeElement?.id).toBe('mi-caja-buscar-alumno');
  }));

  // ------------------------------------------------------------------ deshacer desde «Encima»

  it('«Deshacer» un cobro: diálogo de peligro sin «No hubo venta» y la misma llamada que hoy', async () => {
    crear();
    const vistas = dialogoQueConfirma();
    await componente.abrirDeshacer(ENCIMA.cobros[0]);
    const o = vistas[0];
    expect(o.titulo).toBe('Deshacer el cobro');
    expect(o.destructivo).toBeTrue();
    expect(o.accion).toBe('Deshacer cobro');
    expect(partesTexto(o.mensaje)).toBe('Lucía Prado · Mensualidad · 35,00 €');
    expect(o.campos?.[0].etiqueta).toBe('Qué ha pasado (obligatorio)');
    expect(o.campos?.[0].opciones?.map((p) => p.texto)).toEqual(['Aún no ha pagado (queda pendiente)']);
    expect(o.campos?.[0].valor).toBe('AUN_NO_PAGADO');
    expect(caja.deshacerCobro).toHaveBeenCalledOnceWith(10, 'AUN_NO_PAGADO');
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(2);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(2);
  });

  it('«No hubo venta» solo en una venta, y solo vale en una venta', async () => {
    crear();
    let vistas = dialogoQueConfirma({ 'mi-caja-que-ha-pasado': 'NO_HUBO_VENTA' });
    await componente.abrirDeshacer(ENCIMA.cobros[1]);
    expect(vistas[0].campos?.[0].opciones?.map((p) => p.texto)).toEqual([
      'Aún no ha pagado (queda pendiente)',
      'No hubo venta (se cancela el cargo)',
    ]);
    expect(caja.deshacerCobro).toHaveBeenCalledOnceWith(11, 'NO_HUBO_VENTA');

    caja.deshacerCobro.calls.reset();
    vistas = dialogoQueConfirma({ 'mi-caja-que-ha-pasado': 'NO_HUBO_VENTA' });
    await componente.abrirDeshacer(ENCIMA.cobros[0]);
    expect(caja.deshacerCobro).toHaveBeenCalledOnceWith(10, 'AUN_NO_PAGADO');
  });

  it('si deshacer desde «Encima» falla, lo avisa y vuelve a leer lo que lleva encima', async () => {
    crear();
    caja.deshacerCobro.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya entregado' } })));
    dialogoQueConfirma();
    await componente.abrirDeshacer(ENCIMA.cobros[0]);
    expect(avisos.error).toHaveBeenCalledOnceWith('Ya entregado');
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(2);
  });

  it('si deshacer desde «Encima» falla, no relee los cargos ni los pedidos', async () => {
    crear();
    caja.deshacerCobro.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya entregado' } })));
    dialogoQueConfirma();
    await componente.abrirDeshacer(ENCIMA.cobros[0]);
    expect(avisos.exito).not.toHaveBeenCalled();
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(1);
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(1);
  });

  // ------------------------------------------------------------------ pedir deshacer

  it('«Pedir deshacer»: panel con el resumen, motivo obligatorio y la misma llamada que hoy', () => {
    crear();
    caja.cobrosEntregadosRecientes.and.returnValue(of([ENTREGADO]));
    caja.pedirDeshacer.and.returnValue(of(solicitud({ tipo: 'DESHACER_COBRO_ENTREGADO' })));
    componente.elegirPestana('encima');
    componente.abrirPedirDeshacer(ENTREGADO);
    fixture.detectChanges();

    const panel = el().querySelector('pn-panel-lateral')!;
    expect(limpio(panel.querySelector('h2')?.textContent)).toBe('Pedir al propietario que deshaga este cobro');
    const datos = Array.from(panel.querySelectorAll('.mi-caja__datos div')).map(
      (d) => `${limpio(d.querySelector('dt')?.textContent)} ${limpio(d.querySelector('dd')?.textContent)}`
    );
    expect(datos).toEqual(['Alumno Pablo Navarro', 'Concepto Mensualidad septiembre', 'Importe 35,00 €', 'Entregado el 03/10 en Umbrete']);
    expect(panel.querySelectorAll('input[type="radio"]').length).toBe(1);
    expect(limpio(panel.textContent)).toContain('Qué ha pasado obligatorio');
    expect(limpio(panel.textContent)).toContain('Mientras tanto el cobro sigue entregado en la sede.');
    expect(limpio(panel.querySelector('#mi-caja-motivo-pedir-contador')?.textContent)).toBe('0/500');

    // Sin motivo no se envía.
    componente.confirmarPedirDeshacer();
    fixture.detectChanges();
    expect(caja.pedirDeshacer).not.toHaveBeenCalled();
    expect(limpio(panel.querySelector('#mi-caja-motivo-pedir-error')?.textContent)).toBe('Escribe el motivo.');
    escribir('#mi-caja-motivo-pedir', '   ');
    componente.confirmarPedirDeshacer();
    expect(caja.pedirDeshacer).not.toHaveBeenCalled();

    // Por encima del máximo de hoy, tampoco.
    componente.hojaPedirDeshacer!.motivo = 'x'.repeat(501);
    fixture.detectChanges();
    componente.confirmarPedirDeshacer();
    fixture.detectChanges();
    expect(caja.pedirDeshacer).not.toHaveBeenCalled();
    expect(limpio(el().querySelector('#mi-caja-motivo-pedir-error')?.textContent)).toBe('El motivo admite como máximo 500 caracteres.');

    escribir('#mi-caja-motivo-pedir', '  El padre paga el lunes  ');
    expect(limpio(el().querySelector('#mi-caja-motivo-pedir-contador')?.textContent)).toBe('26/500');
    componente.hojaPedirDeshacer!.motivoDeshacer = 'NO_HUBO_VENTA'; // no es una venta: no vale
    componente.confirmarPedirDeshacer();
    fixture.detectChanges();
    expect(caja.pedirDeshacer).toHaveBeenCalledOnceWith(20, { motivo: 'El padre paga el lunes', motivoDeshacer: 'AUN_NO_PAGADO' });
    expect(componente.hojaPedirDeshacer).toBeNull();
    expect(el().querySelector('pn-panel-lateral')).toBeNull();
    expect(avisos.exito).toHaveBeenCalledOnceWith('Solicitud enviada al propietario');
    expect(caja.cobrosEntregadosRecientes).toHaveBeenCalledTimes(1);
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(2);
  });

  it('«Pedir deshacer» de una venta ofrece «No hubo venta» y lo envía', () => {
    crear();
    caja.cobrosEntregadosRecientes.and.returnValue(of([]));
    caja.pedirDeshacer.and.returnValue(of(solicitud({})));
    componente.abrirPedirDeshacer({ ...ENTREGADO, venta: true });
    fixture.detectChanges();
    const radios = el().querySelectorAll<HTMLInputElement>('pn-panel-lateral input[type="radio"]');
    expect(radios.length).toBe(2);
    radios[1].click();
    fixture.detectChanges();
    escribir('#mi-caja-motivo-pedir', 'Se equivocó');
    componente.confirmarPedirDeshacer();
    expect(caja.pedirDeshacer).toHaveBeenCalledOnceWith(20, { motivo: 'Se equivocó', motivoDeshacer: 'NO_HUBO_VENTA' });
  });

  it('si «Pedir deshacer» falla, el panel sigue con el error y se releen los entregados; sin doble envío', () => {
    crear();
    caja.cobrosEntregadosRecientes.and.returnValue(of([]));
    const respuesta = new Subject<Solicitud>();
    caja.pedirDeshacer.and.returnValue(respuesta);
    componente.abrirPedirDeshacer(ENTREGADO);
    fixture.detectChanges();
    escribir('#mi-caja-motivo-pedir', 'Motivo');
    componente.confirmarPedirDeshacer();
    componente.confirmarPedirDeshacer();
    fixture.detectChanges();
    expect(caja.pedirDeshacer).toHaveBeenCalledTimes(1);
    expect(el().querySelector('pn-panel-lateral button[type="submit"]')?.getAttribute('aria-busy')).toBe('true');
    // Con el envío en vuelo no se cierra.
    componente.cerrarPedirDeshacer();
    expect(componente.hojaPedirDeshacer).not.toBeNull();

    respuesta.error(new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya hay una solicitud' } }));
    fixture.detectChanges();
    expect(limpio(el().querySelector('pn-panel-lateral [role="alert"]')?.textContent)).toBe('Ya hay una solicitud');
    expect(caja.cobrosEntregadosRecientes).toHaveBeenCalledTimes(1);
    expect(componente.hojaPedirDeshacer?.enviando).toBeFalse();
  });

  // ------------------------------------------------------------------ encargar

  function abrirEncargo(): void {
    caja.alumnos.and.returnValue(of(ALUMNOS));
    caja.productosEncargables.and.returnValue(of(PRODUCTOS));
    componente.elegirPestana('pedidos');
    componente.abrirEncargo();
    fixture.detectChanges();
  }

  it('«Encargar» pide alumnos y productos encargables una vez y enseña los campos con «obligatorio»', () => {
    crear();
    abrirEncargo();
    componente.cerrarEncargo();
    componente.abrirEncargo();
    fixture.detectChanges();
    expect(caja.alumnos).toHaveBeenCalledTimes(1);
    expect(caja.productosEncargables).toHaveBeenCalledTimes(1);
    const panel = el().querySelector('pn-panel-lateral')!;
    expect(limpio(panel.querySelector('h2')?.textContent)).toBe('Encargar un producto');
    const etiquetas = Array.from(panel.querySelectorAll('.mi-caja__etiqueta')).map((e) => limpio(e.textContent));
    expect(etiquetas).toEqual(['Alumno obligatorio', 'Producto obligatorio', 'Nota obligatorio']);
    expect(panel.querySelectorAll('.mi-caja__radio-fila').length).toBe(2);
    const fila = panel.querySelector('.mi-caja__radio-fila')!;
    expect(limpio(fila.querySelector('.mi-caja__fila-texto')?.textContent)).toBe('Camiseta del club');
    expect(limpio(fila.querySelector('.mi-caja__importe')?.textContent)).toBe('12,50 €');
    expect(limpio(panel.querySelector('#mi-caja-encargo-nota-ayuda')?.textContent)).toBe('Va con el encargo y en el cargo del alumno · 0/255');
    expect(limpio(panel.textContent)).toContain('Se apunta al alumno al precio del catálogo. Lo cobras desde «Por cobrar».');
  });

  it('«Encargar» sin datos: resumen «Revisa 3 campos» con enlaces que llevan el foco y errores en línea', fakeAsync(() => {
    crear();
    abrirEncargo();
    tick();
    componente.confirmarEncargo();
    fixture.detectChanges();
    tick();
    expect(caja.pedirEncargo).not.toHaveBeenCalled();
    const resumen = el().querySelector<HTMLElement>('.mi-caja__resumen-errores')!;
    expect(limpio(resumen.querySelector('h3')?.textContent)).toBe('Revisa 3 campos');
    expect(Array.from(resumen.querySelectorAll('a')).map((a) => limpio(a.textContent))).toEqual(['Alumno', 'Producto', 'Nota']);
    expect(document.activeElement).toBe(resumen);
    expect(limpio(el().querySelector('#mi-caja-encargo-alumno-error')?.textContent)).toBe('Elige el alumno.');
    expect(limpio(el().querySelector('#mi-caja-encargo-producto-error')?.textContent)).toBe('Elige el producto.');
    expect(limpio(el().querySelector('#mi-caja-encargo-nota-error')?.textContent)).toBe('Escribe la nota (talla, color…).');

    resumen.querySelectorAll<HTMLAnchorElement>('a')[2].click();
    tick();
    expect(document.activeElement?.id).toBe('mi-caja-encargo-nota');
    resumen.querySelectorAll<HTMLAnchorElement>('a')[0].click();
    tick();
    expect(document.activeElement?.id).toBe('mi-caja-encargo-buscar');
    resumen.querySelectorAll<HTMLAnchorElement>('a')[1].click();
    tick();
    expect(document.activeElement?.getAttribute('name')).toBe('mi-caja-producto-encargo');
    flush();
  }));

  it('«Encargar»: la nota es obligatoria y con el máximo de hoy', () => {
    crear();
    abrirEncargo();
    componente.elegirAlumnoEncargo(ALUMNOS[1]);
    componente.elegirProductoEncargo(4);
    componente.hojaEncargo!.nota = '   ';
    componente.confirmarEncargo();
    fixture.detectChanges();
    expect(caja.pedirEncargo).not.toHaveBeenCalled();
    expect(limpio(el().querySelector('.mi-caja__resumen-errores h3')?.textContent)).toBe('Revisa 1 campo');
    expect(limpio(el().querySelector('#mi-caja-encargo-nota-error')?.textContent)).toBe('Escribe la nota (talla, color…).');
    componente.hojaEncargo!.nota = 'x'.repeat(256);
    componente.confirmarEncargo();
    fixture.detectChanges();
    expect(caja.pedirEncargo).not.toHaveBeenCalled();
    expect(limpio(el().querySelector('#mi-caja-encargo-nota-error')?.textContent)).toBe('La nota admite como máximo 255 caracteres.');
    expect(el().querySelector('#mi-caja-encargo-nota')?.getAttribute('maxlength')).toBe('255');
  });

  it('«Encargar» hace la misma llamada y el mismo cuerpo que hoy, avisa y recarga', () => {
    crear();
    caja.pedirEncargo.and.returnValue(of(solicitud({})));
    abrirEncargo();
    el().querySelectorAll<HTMLButtonElement>('pn-panel-lateral .mi-caja__elegible')[0].click(); // Hugo Martín
    fixture.detectChanges();
    el().querySelectorAll<HTMLInputElement>('pn-panel-lateral input[type="radio"]')[0].click();
    escribir('#mi-caja-encargo-nota', '  Talla 8, blanco  ');
    expect(limpio(el().querySelector('pn-panel-lateral')?.textContent)).toContain('Se apunta al alumno al precio del catálogo (12,50 €).');
    expect(el().querySelector('#mi-caja-encargo-sede')).toBeNull();
    el().querySelector<HTMLButtonElement>('pn-panel-lateral button[type="submit"]')!.click();
    fixture.detectChanges();
    expect(caja.pedirEncargo).toHaveBeenCalledOnceWith(3, { productoId: 4, nota: 'Talla 8, blanco' });
    expect(el().querySelector('pn-panel-lateral')).toBeNull();
    expect(avisos.exito).toHaveBeenCalledOnceWith('Encargo enviado');
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(2);
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(2);
  });

  it('«Encargar» con varias sedes pide la sede (obligatoria) y la envía', () => {
    crear();
    caja.pedirEncargo.and.returnValue(of(solicitud({})));
    abrirEncargo();
    componente.elegirAlumnoEncargo(ALUMNOS[0]); // Lucía, en dos sedes
    componente.elegirProductoEncargo(6);
    componente.hojaEncargo!.nota = 'Azul';
    fixture.detectChanges();
    const sede = el().querySelector<HTMLSelectElement>('#mi-caja-encargo-sede')!;
    expect(Array.from(sede.options).map((o) => limpio(o.textContent))).toEqual(['Elige la sede', 'Centro', 'Umbrete']);
    expect(limpio(el().querySelector('#mi-caja-encargo-sede-ayuda')?.textContent)).toBe(
      'El alumno está en grupos tuyos de varias sedes: lo atiende la que elijas.'
    );
    componente.confirmarEncargo();
    fixture.detectChanges();
    expect(caja.pedirEncargo).not.toHaveBeenCalled();
    expect(limpio(el().querySelector('#mi-caja-encargo-sede-error')?.textContent)).toBe('Elige la sede.');
    sede.value = sede.options[2].value;
    sede.dispatchEvent(new Event('change'));
    componente.confirmarEncargo();
    expect(caja.pedirEncargo).toHaveBeenCalledOnceWith(5, { productoId: 6, nota: 'Azul', sedeId: 2 });
  });

  it('si la API pide la sede, «Encargar» la pide sin repetir el error; otro error se queda en el panel', () => {
    crear();
    caja.pedirEncargo.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'El alumno está en grupos de varias sedes: indica la sede.' } }))
    );
    abrirEncargo();
    componente.elegirAlumnoEncargo(ALUMNOS[1]);
    componente.elegirProductoEncargo(4);
    componente.hojaEncargo!.nota = 'Talla 8';
    componente.confirmarEncargo();
    fixture.detectChanges();
    expect(componente.hojaEncargo?.pedirSede).toBeTrue();
    expect(componente.hojaEncargo?.error).toBeNull();
    expect(limpio(el().querySelector('pn-panel-lateral')?.textContent)).toContain(
      'No se puede elegir la sede desde aquí: pide este encargo a la gestión.'
    );

    caja.pedirEncargo.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Producto retirado' } })));
    componente.hojaEncargo!.pedirSede = false;
    componente.confirmarEncargo();
    fixture.detectChanges();
    expect(limpio(el().querySelector('pn-panel-lateral [role="alert"]')?.textContent)).toBe('Producto retirado');
    expect(componente.hojaEncargo).not.toBeNull();
  });

  it('el error al cargar «Encargar» deja reintentar', () => {
    crear();
    caja.alumnos.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    caja.productosEncargables.and.returnValue(of(PRODUCTOS));
    componente.abrirEncargo();
    fixture.detectChanges();
    const error = el().querySelector('pn-panel-lateral pn-bloque-error');
    expect(limpio(error?.textContent)).toContain('No se pudieron cargar tus alumnos ni los productos.');
    expect(el().querySelector('pn-panel-lateral button[type="submit"]')).toBeNull();
    caja.alumnos.and.returnValue(of(ALUMNOS));
    error!.querySelector<HTMLButtonElement>('button')!.click();
    fixture.detectChanges();
    expect(caja.alumnos).toHaveBeenCalledTimes(2);
    expect(caja.productosEncargables).toHaveBeenCalledTimes(1);
    expect(el().querySelector('pn-panel-lateral button[type="submit"]')).not.toBeNull();
  });

  // ------------------------------------------------------------------ retirar, marcar entregado y devolución

  it('«Retirar» un encargo: diálogo de peligro con los textos de hoy y la misma llamada', async () => {
    crear();
    caja.retirarSolicitud.and.returnValue(of(solicitud({ estado: 'RETIRADA' })));
    const vistas = dialogoQueConfirma();
    await componente.abrirRetirar(solicitud({ id: 7 }));
    const o = vistas[0];
    expect(o.titulo).toBe('Retirar el encargo');
    expect(o.destructivo).toBeTrue();
    expect(o.accion).toBe('Retirar encargo');
    expect(partesTexto(o.mensaje)).toBe('Hugo Martín · Dobok infantil');
    expect(o.bloques).toEqual([
      { tipo: 'parrafo', partes: ['Si el alumno aún no lo ha pagado, se cancela su cargo; si ya lo pagó, quedará la devolución pendiente.'] },
    ]);
    expect(caja.retirarSolicitud).toHaveBeenCalledOnceWith(7);
    expect(avisos.exito).toHaveBeenCalledOnceWith('Encargo retirado');
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(2);
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(2);
  });

  it('«Retirar» una solicitud de deshacer lleva sus textos; si falla, avisa y relee los pedidos', async () => {
    crear();
    caja.retirarSolicitud.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya resuelta' } })));
    const vistas = dialogoQueConfirma();
    await componente.abrirRetirar(solicitud({ id: 8, tipo: 'DESHACER_COBRO_ENTREGADO', estado: 'PENDIENTE' }));
    expect(vistas[0].titulo).toBe('Retirar la solicitud');
    expect(vistas[0].accion).toBe('Retirar solicitud');
    expect(vistas[0].bloques?.[0]).toEqual({ tipo: 'parrafo', partes: ['El cobro sigue como está y puedes volver a pedirlo.'] });
    expect(caja.retirarSolicitud).toHaveBeenCalledOnceWith(8);
    expect(avisos.error).toHaveBeenCalledOnceWith('Ya resuelta');
    expect(avisos.exito).not.toHaveBeenCalled();
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(2);
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(1);
  });

  it('«Marcar entregado»: diálogo normal con los textos de hoy y la misma llamada', async () => {
    crear();
    caja.marcarEntregado.and.returnValue(of(solicitud({ estado: 'ENTREGADO' })));
    let vistas = dialogoQueConfirma();
    await componente.abrirEntregado(solicitud({ id: 9, estado: 'RECIBIDO', estadoCobro: 'PENDIENTE' }));
    expect(vistas[0].titulo).toBe('Marcar entregado al alumno');
    expect(vistas[0].destructivo).toBeFalse();
    expect(vistas[0].accion).toBe('Marcar entregado');
    expect(vistas[0].bloques?.[0]).toEqual({
      tipo: 'parrafo',
      partes: ['Confirma que el alumno ya lo tiene. El cargo sigue en «Por cobrar» hasta que lo pague.'],
    });
    expect(caja.marcarEntregado).toHaveBeenCalledOnceWith(9);
    expect(avisos.exito).toHaveBeenCalledOnceWith('Encargo entregado');

    vistas = dialogoQueConfirma();
    await componente.abrirEntregado(solicitud({ id: 9, estado: 'RECIBIDO', estadoCobro: 'COBRADO' }));
    expect(vistas[0].bloques?.[0]).toEqual({ tipo: 'parrafo', partes: ['Confirma que el alumno ya lo tiene. No se puede deshacer.'] });
  });

  it('«Registrar devolución»: peligro, cifra, nota opcional recortada y la misma llamada que hoy', async () => {
    crear();
    caja.registrarDevolucion.and.returnValue(of(solicitud({})));
    const s = solicitud({ id: 3, estado: 'RETIRADA', devolucionPendiente: true, devolucionEnSede: false, estadoCobro: 'COBRADO' });
    let vistas = dialogoQueConfirma();
    await componente.abrirDevolucion(s);
    const o = vistas[0];
    expect(o.titulo).toBe('Registrar devolución');
    expect(o.destructivo).toBeTrue();
    expect(limpio(o.accion)).toBe('Devolver 28,00 €');
    expect(limpio(o.destacado?.valor)).toBe('28,00 €');
    expect(o.nombre).toBe('mi-caja-devolucion');
    expect(partesTexto(o.mensaje)).toBe('Hugo Martín · Dobok infantil');
    expect(o.bloques?.[0]).toEqual({
      tipo: 'parrafo',
      partes: ['Se devuelve el cobro entero y sale de lo que llevas encima. Da el dinero a la familia.'],
    });
    expect(o.campos?.[0].etiqueta).toBe('Nota (opcional)');
    expect(o.campos?.[0].atributos?.['maxlength']).toBe('500');
    expect(caja.registrarDevolucion).toHaveBeenCalledOnceWith(3, null);
    expect(limpio(avisos.exito.calls.mostRecent().args[0])).toBe('Devolución registrada 28,00 €');
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(2);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(2);

    caja.registrarDevolucion.calls.reset();
    vistas = dialogoQueConfirma({ 'mi-caja-nota-devolucion': '  A su madre  ' });
    await componente.abrirDevolucion(s);
    expect(caja.registrarDevolucion).toHaveBeenCalledOnceWith(3, 'A su madre');
  });

  it('si «Registrar devolución» falla, avisa y relee solo los pedidos', async () => {
    crear();
    caja.registrarDevolucion.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya devuelto' } })));
    dialogoQueConfirma();
    await componente.abrirDevolucion(solicitud({ id: 3, estado: 'RETIRADA', devolucionPendiente: true, devolucionEnSede: false, estadoCobro: 'COBRADO' }));
    expect(caja.registrarDevolucion).toHaveBeenCalledOnceWith(3, null);
    expect(avisos.error).toHaveBeenCalledOnceWith('Ya devuelto');
    expect(avisos.exito).not.toHaveBeenCalled();
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(2);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(1);
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(1);
  });

  it('si «Marcar entregado» falla, avisa y relee solo los pedidos', async () => {
    crear();
    caja.marcarEntregado.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya entregado' } })));
    dialogoQueConfirma();
    await componente.abrirEntregado(solicitud({ id: 9, estado: 'RECIBIDO', estadoCobro: 'PENDIENTE' }));
    expect(caja.marcarEntregado).toHaveBeenCalledOnceWith(9);
    expect(avisos.error).toHaveBeenCalledOnceWith('Ya entregado');
    expect(avisos.exito).not.toHaveBeenCalled();
    expect(caja.misSolicitudes).toHaveBeenCalledTimes(2);
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(1);
  });

  it('«Registrar devolución» no admite una nota por encima del máximo de hoy', () => {
    crear();
    confirmacion.confirmar.and.returnValue(new Promise(() => undefined));
    componente.abrirDevolucion(solicitud({ id: 3 }));
    const o = confirmacion.confirmar.calls.mostRecent().args[0] as OpcionesConfirmacion;
    expect(o.comprobar!({ 'mi-caja-nota-devolucion': 'x'.repeat(501) })).toEqual({
      campo: 'mi-caja-nota-devolucion',
      mensaje: 'La nota admite como máximo 500 caracteres.',
    });
    expect(o.comprobar!({ 'mi-caja-nota-devolucion': 'x'.repeat(500) })).toBeNull();
  });

  // ------------------------------------------------------------------ un solo diálogo o panel a la vez

  it('con un diálogo o un panel abierto no se abre otro (sin doble envío)', async () => {
    crear();
    confirmacion.confirmar.and.returnValue(new Promise(() => undefined));
    componente.abrirRetirar(solicitud({ id: 1 }));
    await componente.abrirDevolucion(solicitud({ id: 1 }));
    await componente.abrirDeshacer(ENCIMA.cobros[0]);
    await componente.abrirCobro(CARGOS[0]);
    componente.abrirEncargo();
    componente.abrirPedirDeshacer(ENTREGADO);
    expect(confirmacion.confirmar).toHaveBeenCalledTimes(1);
    expect(componente.hojaEncargo).toBeNull();
    expect(componente.hojaPedirDeshacer).toBeNull();
  });

  it('con un panel abierto no se abre ningún diálogo', async () => {
    crear();
    caja.cobrosEntregadosRecientes.and.returnValue(of([]));
    componente.abrirPedirDeshacer(ENTREGADO);
    await componente.abrirCobro(CARGOS[0]);
    await componente.abrirDeshacer(ENCIMA.cobros[0]);
    componente.abrirEncargo();
    expect(confirmacion.confirmar).not.toHaveBeenCalled();
    expect(componente.hojaEncargo).toBeNull();
    expect(caja.alumnos).not.toHaveBeenCalled();
  });
});
