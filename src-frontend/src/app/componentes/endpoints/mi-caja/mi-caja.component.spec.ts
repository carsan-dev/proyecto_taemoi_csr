import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { DURACION_AVISO_MS, MiCajaComponent, agruparPorAlumno } from './mi-caja.component';
import { CajaProfesorService } from '../../../servicios/endpoints/caja-profesor.service';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { CargoPendienteProfesor, CobroCaja, MisCobrosPendientes } from '../../../interfaces/caja';

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

const CARGOS = [
  cargo(1, 3, 'Lucía', 'Prado', 'Mensualidad octubre', 35),
  cargo(2, 5, 'Andrés', 'Gil', 'Mensualidad octubre', 30),
  cargo(3, 3, 'Lucía', 'Prado', 'Camiseta', 12.5),
];

const ENCIMA: MisCobrosPendientes = { numeroCobros: 2, importe: 47.5, cobros: [cobro(10, false), cobro(11, true, 12.5)] };

describe('MiCajaComponent', () => {
  let fixture: ComponentFixture<MiCajaComponent>;
  let componente: MiCajaComponent;
  let caja: jasmine.SpyObj<CajaProfesorService>;
  let roles: string[];

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (selector: string) => el().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();

  function crear(cargos = CARGOS, encima = ENCIMA): void {
    caja = jasmine.createSpyObj<CajaProfesorService>('CajaProfesorService', [
      'cargosPendientes', 'cobrarCargo', 'deshacerCobro', 'productos', 'venderProducto', 'cobrosPendientesEntrega', 'alumnos',
      'misSolicitudes',
    ]);
    caja.misSolicitudes.and.returnValue(of([]));
    caja.cargosPendientes.and.returnValue(of(cargos));
    caja.cobrosPendientesEntrega.and.returnValue(of(encima));
    caja.productos.and.returnValue(of([{ id: 4, concepto: 'Camiseta', precio: 12.5 }]));
    caja.alumnos.and.returnValue(of([{ alumnoId: 3, nombre: 'Lucía', apellidos: 'Prado', grupos: ['Infantil'] }]));
    caja.deshacerCobro.and.returnValue(of(undefined));

    TestBed.configureTestingModule({
      imports: [MiCajaComponent],
      providers: [
        { provide: CajaProfesorService, useValue: caja },
        {
          provide: AuthenticationService,
          useValue: { getRolesActuales: () => roles, forzarCierreSesion: () => Promise.resolve() },
        },
        { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) },
      ],
    });

    fixture = TestBed.createComponent(MiCajaComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => (roles = ['ROLE_PROFESOR']));

  it('agrupa los cargos por alumno, por nombre, y filtra sin tildes', () => {
    const grupos = agruparPorAlumno(CARGOS);
    expect(grupos.map((g) => g.nombre)).toEqual(['Andrés Gil', 'Lucía Prado']);
    expect(grupos[1].cargos.map((c) => c.cargoId)).toEqual([1, 3]);

    expect(agruparPorAlumno(CARGOS, 'lucia').map((g) => g.nombre)).toEqual(['Lucía Prado']);
  });

  it('muestra lo que llevas encima, contadores y los alumnos en negrita', async () => {
    crear();
    expect(texto('.encima-importe')).toBe('47,50 €');
    expect(texto('.encima-detalle')).toBe('2 cobros sin entregar');
    expect(texto('#pestana-cobrar')).toBe('Por cobrar 3');
    expect(texto('#pestana-encima')).toBe('Encima 2');
    const nombres = Array.from(el().querySelectorAll('.alumno-nombre')).map((h) => h.textContent?.trim());
    expect(nombres).toEqual(['Andrés Gil', 'Lucía Prado']);
    expect(texto('.fila .meta')).toBe('desde 01/10');
  });

  it('filtra al buscar alumno', async () => {
    crear();
    componente.busquedaCargos = 'gil';
    componente.filtrarCargos();
    fixture.detectChanges();
    expect(el().querySelectorAll('.alumno').length).toBe(1);
    expect(texto('.alumno-nombre')).toBe('Andrés Gil');
  });

  it('solo profesor ve «Cerrar sesión»; una cuenta mixta no', async () => {
    crear();
    expect(el().querySelector('.btn-salir')).not.toBeNull();

    TestBed.resetTestingModule();
    roles = ['ROLE_USER', 'ROLE_PROFESOR'];
    crear();
    expect(el().querySelector('.btn-salir')).toBeNull();
  });

  it('cobrar abre la hoja, llama al servicio una sola vez aunque se pulse dos veces y avisa con deshacer', fakeAsync(() => {
    crear();
    const respuesta = new Subject<CobroCaja>();
    caja.cobrarCargo.and.returnValue(respuesta);

    (el().querySelector('.btn-cobrar') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(texto('.hoja h2')).toBe('Cobrar en efectivo');
    expect(texto('.hoja-importe')).toBe('30,00 €');
    expect(texto('.hoja-detalle')).toBe('Andrés Gil · Mensualidad octubre');
    expect(texto('.btn-principal-grande')).toBe('Cobrar 30,00 €');

    componente.confirmarHoja();
    componente.confirmarHoja();
    fixture.detectChanges();
    expect(caja.cobrarCargo).toHaveBeenCalledTimes(1);
    expect(caja.cobrarCargo).toHaveBeenCalledWith(2);
    expect((el().querySelector('.btn-principal-grande') as HTMLButtonElement).disabled).toBeTrue();

    caja.cargosPendientes.calls.reset();
    caja.cobrosPendientesEntrega.calls.reset();
    respuesta.next(cobro(77, false, 30));
    respuesta.complete();
    fixture.detectChanges();
    expect(el().querySelector('.hoja')).toBeNull();
    expect(texto('.aviso-texto')).toBe('Cobrado 30,00 €');
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(1);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(1);

    (el().querySelector('.aviso-deshacer') as HTMLButtonElement).click();
    expect(caja.deshacerCobro).toHaveBeenCalledWith(77, 'AUN_NO_PAGADO');
    fixture.detectChanges();
    expect(el().querySelector('.aviso')).toBeNull();
    tick(DURACION_AVISO_MS);
  }));

  it('deshacer el aviso anterior no borra el aviso de un cobro posterior', fakeAsync(() => {
    crear();
    const deshacer = new Subject<void>();
    caja.deshacerCobro.and.returnValue(deshacer);
    caja.cobrarCargo.and.returnValues(of(cobro(80, false, 30)), of(cobro(81, false, 35)));

    componente.abrirCobro(CARGOS[1]);
    componente.confirmarHoja();
    componente.deshacerDesdeAviso();
    expect(caja.deshacerCobro).toHaveBeenCalledWith(80, 'AUN_NO_PAGADO');

    // Mientras se deshace el primero, llega otro cobro con su aviso.
    componente.abrirCobro(CARGOS[0]);
    componente.confirmarHoja();
    expect(componente.aviso?.cobroId).toBe(81);

    deshacer.next();
    deshacer.complete();
    fixture.detectChanges();
    expect(componente.aviso?.cobroId).toBe(81);
    expect(texto('.aviso-texto')).toBe('Cobrado 35,00 €');
    tick(DURACION_AVISO_MS);
  }));

  it('pestañas: los tres paneles existen siempre y solo el activo es visible', () => {
    crear();
    for (const id of ['panel-cobrar', 'panel-vender', 'panel-encima']) {
      expect(el().querySelector(`#${id}[role="tabpanel"]`)).withContext(id).not.toBeNull();
    }
    expect(el().querySelector<HTMLElement>('#panel-cobrar')!.hidden).toBeFalse();
    expect(el().querySelector<HTMLElement>('#panel-encima')!.hidden).toBeTrue();
    componente.elegirPestana('encima');
    fixture.detectChanges();
    expect(el().querySelector<HTMLElement>('#panel-cobrar')!.hidden).toBeTrue();
    expect(el().querySelector<HTMLElement>('#panel-encima')!.hidden).toBeFalse();
  });

  it('el aviso se cierra solo tras unos segundos', fakeAsync(() => {
    crear();
    caja.cobrarCargo.and.returnValue(of(cobro(78, false)));
    componente.abrirCobro(CARGOS[0]);
    componente.confirmarHoja();
    fixture.detectChanges();
    expect(el().querySelector('.aviso')).not.toBeNull();
    tick(DURACION_AVISO_MS);
    fixture.detectChanges();
    expect(el().querySelector('.aviso')).toBeNull();
  }));

  it('vender: elegir alumno, la hoja «Vender y cobrar» y deshacer con NO_HUBO_VENTA', fakeAsync(() => {
    crear();
    caja.venderProducto.and.returnValue(of(cobro(90, true, 12.5)));
    componente.elegirPestana('vender');
    fixture.detectChanges();
    expect(caja.productos).toHaveBeenCalledTimes(1);
    expect((el().querySelector('.filas-catalogo .btn-cobrar') as HTMLButtonElement).disabled).toBeTrue();

    (el().querySelector('.eleccion') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(texto('.alumno-elegido strong')).toBe('Lucía Prado');

    (el().querySelector('.filas-catalogo .btn-cobrar') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(texto('.hoja h2')).toBe('Vender y cobrar en efectivo');
    componente.confirmarHoja();
    fixture.detectChanges();
    expect(caja.venderProducto).toHaveBeenCalledWith(3, 4);
    expect(texto('.aviso-texto')).toBe('Vendido 12,50 €');

    componente.deshacerDesdeAviso();
    expect(caja.deshacerCobro).toHaveBeenCalledWith(90, 'NO_HUBO_VENTA');
    tick(DURACION_AVISO_MS);
  }));

  it('catálogo vacío: explica que la gestión aún no ha marcado productos', async () => {
    crear();
    caja.productos.and.returnValue(of([]));
    componente.elegirPestana('vender');
    fixture.detectChanges();
    expect(texto('#panel-vender .estado')).toContain('todavía no ha marcado productos');
  });

  it('encima: «No hubo venta» solo para las ventas y deshace con el motivo elegido', async () => {
    crear();
    componente.elegirPestana('encima');
    fixture.detectChanges();
    const metas = Array.from(el().querySelectorAll('#panel-encima .fila .meta')).map((m) => m.textContent?.replace(/\s+/g, ' ').trim());
    expect(metas[0]).toBe('Mensualidad · hoy');
    expect(metas[1]).toBe('Mensualidad · venta · hoy');

    const botones = el().querySelectorAll<HTMLButtonElement>('#panel-encima .btn-deshacer');
    botones[0].click();
    fixture.detectChanges();
    expect(el().querySelectorAll('.motivo').length).toBe(1);
    expect(texto('.motivo')).toContain('Aún no ha pagado');
    componente.cerrarDeshacer();
    fixture.detectChanges();

    botones[1].click();
    fixture.detectChanges();
    expect(el().querySelectorAll('.motivo').length).toBe(2);
    componente.hojaDeshacer!.motivo = 'NO_HUBO_VENTA';
    componente.confirmarDeshacer();
    componente.confirmarDeshacer();
    expect(caja.deshacerCobro).toHaveBeenCalledOnceWith(11, 'NO_HUBO_VENTA');
  });

  it('un cobro que no es venta nunca se deshace con NO_HUBO_VENTA', async () => {
    crear();
    componente.abrirDeshacer(ENCIMA.cobros[0]);
    componente.hojaDeshacer!.motivo = 'NO_HUBO_VENTA';
    componente.confirmarDeshacer();
    expect(caja.deshacerCobro).toHaveBeenCalledWith(10, 'AUN_NO_PAGADO');
  });

  it('estados vacíos útiles', async () => {
    crear([], { numeroCobros: 0, importe: 0, cobros: [] });
    expect(texto('#panel-cobrar .estado')).toContain('Nada pendiente');
    expect(texto('.encima-detalle')).toBe('0 cobros sin entregar');
    componente.elegirPestana('encima');
    fixture.detectChanges();
    expect(texto('#panel-encima .estado')).toContain('No llevas nada encima');
  });

  it('error de carga con el mensaje del servidor y reintento', async () => {
    crear();
    caja.cargosPendientes.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 403, error: { mensaje: 'No eres profesor de ningún grupo.' } }))
    );
    componente.cargarCargos();
    fixture.detectChanges();
    expect(texto('#panel-cobrar .estado-error p')).toBe('No eres profesor de ningún grupo.');
  });

  it('error al cobrar: el mensaje del servidor en la hoja, que sigue abierta', async () => {
    crear();
    caja.cobrarCargo.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'Ese cargo ya está pagado.' } }))
    );
    caja.cargosPendientes.calls.reset();
    caja.cobrosPendientesEntrega.calls.reset();
    componente.abrirCobro(CARGOS[0]);
    componente.confirmarHoja();
    fixture.detectChanges();
    expect(texto('.hoja-error')).toBe('Ese cargo ya está pagado.');
    // Por si el servidor sí registró algo: cargos, «Encima» y «Llevas encima» se recargan.
    expect(caja.cargosPendientes).toHaveBeenCalledTimes(1);
    expect(caja.cobrosPendientesEntrega).toHaveBeenCalledTimes(1);
    expect((el().querySelector('.btn-principal-grande') as HTMLButtonElement).disabled).toBeFalse();
  });

  it('Escape cierra la hoja', async () => {
    crear();
    componente.abrirCobro(CARGOS[0]);
    fixture.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(el().querySelector('.hoja')).toBeNull();
  });
});
