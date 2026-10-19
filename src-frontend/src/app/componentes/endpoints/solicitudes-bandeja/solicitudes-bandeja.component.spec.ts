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
import { SolicitudesBandejaComponent } from './solicitudes-bandeja.component';

export function solicitudDePrueba(cambios: Partial<Solicitud> = {}): Solicitud {
  return {
    id: 7,
    tipo: 'REVERTIR_COBRO',
    estado: 'PENDIENTE',
    sedeId: 1,
    sedeNombre: 'Sede Centro',
    solicitadoPorUsuarioId: 5,
    solicitadoPorNombre: 'Gestor Prueba',
    solicitadoEn: new Date(2026, 9, 19, 9, 30).toISOString(),
    motivo: 'Se marcó por error',
    resueltoPorUsuarioId: null,
    resueltoPorNombre: null,
    resueltoEn: null,
    respuesta: null,
    cobroId: 45,
    entregaCajaId: null,
    movimientoCajaId: null,
    productoAlumnoId: 30,
    productoId: null,
    alumnoId: 9,
    alumnoNombre: 'Alumno',
    alumnoApellidos: 'De Prueba',
    concepto: 'CUOTA OCTUBRE',
    importe: 10.5,
    motivoDeshacer: null,
    encargadoEn: null,
    recibidoEn: null,
    entregadoEn: null,
    devolucionPendiente: false,
    propia: false,
    ...cambios,
  };
}

/** Pieza 6c-7: bandeja de solicitudes (segmentos, acciones del propietario y de quien pidió). */
describe('SolicitudesBandejaComponent', () => {
  let servicio: jasmine.SpyObj<SolicitudesService>;
  let contador: { refrescar: jasmine.Spy };
  let swal: jasmine.Spy;
  let toast: jasmine.Spy;
  let fixture: ComponentFixture<SolicitudesBandejaComponent>;

  const pendiente = solicitudDePrueba();
  const encargo = solicitudDePrueba({
    id: 8, tipo: 'ENCARGO', estado: 'PEDIDO', cobroId: null, concepto: 'DOBOK T-160', motivo: 'Talla 160', propia: false,
  });
  const resuelta = solicitudDePrueba({
    id: 6, estado: 'DENEGADA', respuesta: 'El cobro es correcto', resueltoPorNombre: 'Propietario',
    resueltoEn: new Date(2026, 9, 19, 12, 0).toISOString(),
  });

  async function crear(roles: string[], datos: Partial<Record<string, Solicitud[]>> = {}) {
    servicio = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', ['listar', 'aceptar', 'denegar', 'retirar']);
    servicio.listar.and.callFake((segmento) =>
      of(datos[segmento] ?? { PENDIENTES: [pendiente], ENCARGOS: [encargo], RESUELTAS: [resuelta] }[segmento])
    );
    contador = { refrescar: jasmine.createSpy('refrescar') };
    // Toast hereda fire de Swal: se espía primero (y una sola vez por prueba, aunque se monte dos veces).
    if (!jasmine.isSpy(Swal.fire)) {
      toast = spyOn(Toast, 'fire').and.returnValue(Promise.resolve({} as any));
      swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true, value: 'No procede' } as any));
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
    return fixture.nativeElement as HTMLElement;
  }

  const pestanas = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('[role="tab"]')).map((b) => b.textContent?.replace(/\s+/g, ' ').trim());
  const botones = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('[role="tabpanel"]:not([hidden]) button')).map((b) => b.textContent?.trim());

  it('al entrar pide Pendientes y Encargos y pinta los dos números; Resueltas al elegirla', async () => {
    const el = await crear(['ROLE_ADMIN']);

    expect(servicio.listar.calls.allArgs()).toEqual([['PENDIENTES'], ['ENCARGOS']]);
    expect(pestanas(el)).toEqual(['Pendientes (1)', 'Encargos en curso (1)', 'Resueltas']);

    fixture.componentInstance.elegirSegmento('RESUELTAS');
    fixture.detectChanges();
    expect(servicio.listar).toHaveBeenCalledWith('RESUELTAS');
    expect(el.textContent).toContain('Denegada');
    expect(el.textContent).toContain('El cobro es correcto');
  });

  it('la fila muestra tipo, quién, sede, alumno, concepto, importe y motivo', async () => {
    const el = await crear(['ROLE_ADMIN']);
    const fila = el.querySelector('[role="tabpanel"]:not([hidden]) .fila-solicitud')!;
    const texto = fila.textContent!.replace(/\s+/g, ' ');

    expect(fila.querySelector('.chip-tipo')?.textContent?.trim()).toBe('Revertir cobro');
    expect(texto).toContain('Gestor Prueba');
    expect(texto).toContain('19/10/2026');
    expect(texto).toContain('Sede Centro');
    expect(texto).toContain('Alumno De Prueba');
    expect(texto).toContain('CUOTA OCTUBRE');
    expect(texto).toContain('10,50');
    expect(texto).toContain('«Se marcó por error»');
  });

  it('el propietario ve Aceptar y Denegar con el verbo del tipo; la gestión no', async () => {
    let el = await crear(['ROLE_ADMIN'], {
      PENDIENTES: [
        pendiente,
        solicitudDePrueba({ id: 9, tipo: 'DESHACER_COBRO_ENTREGADO' }),
        solicitudDePrueba({ id: 10, tipo: 'ANULAR_ENTREGA', cobroId: null, entregaCajaId: 3 }),
      ],
    });
    expect(botones(el)).toEqual([
      'Aceptar y revertir', 'Denegar…', 'Aceptar y deshacer', 'Denegar…', 'Aceptar y anular', 'Denegar…',
    ]);

    el = await crear(['ROLE_MANAGER']);
    expect(botones(el)).toEqual([]);
  });

  it('quien pidió ve Retirar en las suyas pendientes y la retira', async () => {
    const el = await crear(['ROLE_MANAGER'], { PENDIENTES: [solicitudDePrueba({ propia: true })] });
    servicio.retirar.and.returnValue(of(solicitudDePrueba({ estado: 'RETIRADA' })));
    expect(botones(el)).toEqual(['Retirar']);

    await fixture.componentInstance.retirar(solicitudDePrueba({ propia: true }));

    expect(servicio.retirar).toHaveBeenCalledWith(7);
    expect(contador.refrescar).toHaveBeenCalled();
  });

  it('aceptar con NO_APLICABLE avisa con la razón de la API y recarga', async () => {
    await crear(['ROLE_ADMIN']);
    servicio.aceptar.and.returnValue(
      of(solicitudDePrueba({ estado: 'NO_APLICABLE', respuesta: 'El cobro ya va en una entrega de caja.' }))
    );
    servicio.listar.calls.reset();

    await fixture.componentInstance.aceptar(pendiente);

    expect(servicio.aceptar).toHaveBeenCalledWith(7);
    expect(swal).toHaveBeenCalledWith(
      jasmine.objectContaining({ icon: 'warning', text: 'El cobro ya va en una entrega de caja.' })
    );
    expect(servicio.listar).toHaveBeenCalledWith('PENDIENTES');
    expect(contador.refrescar).toHaveBeenCalled();
  });

  it('aceptar ACEPTADA confirma con un aviso breve', async () => {
    await crear(['ROLE_ADMIN']);
    servicio.aceptar.and.returnValue(of(solicitudDePrueba({ estado: 'ACEPTADA' })));

    await fixture.componentInstance.aceptar(pendiente);

    expect(toast).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'success', title: 'Cobro revertido' }));
  });

  it('un error al aceptar muestra el mensaje del servidor', async () => {
    await crear(['ROLE_ADMIN']);
    servicio.aceptar.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'La solicitud ya no está pendiente' } }))
    );

    await fixture.componentInstance.aceptar(pendiente);

    expect(toast).toHaveBeenCalledWith(
      jasmine.objectContaining({ icon: 'error', title: 'La solicitud ya no está pendiente' })
    );
  });

  it('denegar pide una respuesta obligatoria y la envía', async () => {
    await crear(['ROLE_ADMIN']);
    servicio.denegar.and.returnValue(of(solicitudDePrueba({ estado: 'DENEGADA' })));

    await fixture.componentInstance.denegar(pendiente);

    const opciones = swal.calls.mostRecent().args[0] as any;
    expect(opciones.input).toBe('textarea');
    expect(opciones.inputValidator('  ')).toBeTruthy();
    expect(opciones.inputValidator('No procede')).toBeNull();
    expect(servicio.denegar).toHaveBeenCalledWith(7, 'No procede');
    expect(contador.refrescar).toHaveBeenCalled();
  });

  it('los encargos solo se listan, sin acciones', async () => {
    const el = await crear(['ROLE_ADMIN']);
    fixture.componentInstance.elegirSegmento('ENCARGOS');
    fixture.detectChanges();

    expect(el.querySelector('[role="tabpanel"]:not([hidden]) .chip-tipo')?.textContent?.trim()).toBe('Encargo');
    expect(el.textContent).toContain('Pedido');
    expect(botones(el)).toEqual([]);
  });

  it('sin solicitudes, un estado vacío que lo dice', async () => {
    const el = await crear(['ROLE_ADMIN'], { PENDIENTES: [] });
    expect(el.querySelector('[role="tabpanel"]:not([hidden]) .sin-resultados')?.textContent).toContain(
      'No hay solicitudes pendientes'
    );
  });
});
