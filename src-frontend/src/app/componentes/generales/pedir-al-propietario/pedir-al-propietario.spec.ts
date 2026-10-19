import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { SolicitudesService } from '../../../servicios/endpoints/solicitudes.service';
import { Toast } from '../../../utils/toast.util';
import { pedirAlPropietario } from './pedir-al-propietario';

/** Pieza 6c-7: diálogo «Pedir al propietario» (decisiones 190 y 191, D6c-8). */
describe('pedirAlPropietario', () => {
  let servicio: jasmine.SpyObj<SolicitudesService>;
  let swal: jasmine.Spy;
  let toast: jasmine.Spy;

  const opciones = () => ({
    titulo: 'Pedir al propietario que revierta este cobro',
    datos: [
      { etiqueta: 'Alumno', valor: 'Alumno <b>De Prueba</b>' },
      { etiqueta: 'Concepto', valor: 'CUOTA OCTUBRE' },
      { etiqueta: 'Importe', valor: '10,50 €' },
    ],
    nota: 'Mientras tanto el pago sigue marcado.',
    peticion: { tipo: 'REVERTIR_COBRO' as const, productoAlumnoId: 30 },
  });

  beforeEach(() => {
    servicio = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', ['crear']);
    toast = spyOn(Toast, 'fire').and.returnValue(Promise.resolve({} as any));
    swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true, value: '  Se marcó por error ' } as any));
  });

  it('muestra los datos escapados, la nota y exige motivo', async () => {
    servicio.crear.and.returnValue(of({ id: 1 } as any));

    await pedirAlPropietario(servicio, opciones());

    const config = swal.calls.mostRecent().args[0] as any;
    expect(config.title).toBe('Pedir al propietario que revierta este cobro');
    expect(config.html).toContain('CUOTA OCTUBRE');
    expect(config.html).toContain('Alumno &lt;b&gt;De Prueba&lt;/b&gt;');
    expect(config.html).toContain('Mientras tanto el pago sigue marcado.');
    expect(config.input).toBe('textarea');
    expect(config.inputAttributes.maxlength).toBe('500');
    expect(config.inputValidator('   ')).toBeTruthy();
    expect(config.inputValidator('motivo')).toBeNull();
    expect(config.confirmButtonText).toBe('Enviar solicitud');
    expect(config.cancelButtonText).toBe('Cancelar');
  });

  it('envía la petición con el motivo y confirma', async () => {
    servicio.crear.and.returnValue(of({ id: 1 } as any));

    const resultado = await pedirAlPropietario(servicio, opciones());

    expect(servicio.crear).toHaveBeenCalledWith({
      tipo: 'REVERTIR_COBRO',
      productoAlumnoId: 30,
      motivo: 'Se marcó por error',
    });
    expect(resultado).toEqual({ id: 1 } as any);
    expect(toast).toHaveBeenCalledWith(
      jasmine.objectContaining({ icon: 'success', title: 'Solicitud enviada al propietario' })
    );
  });

  it('cancelar no envía nada', async () => {
    swal.and.returnValue(Promise.resolve({ isConfirmed: false } as any));

    const resultado = await pedirAlPropietario(servicio, opciones());

    expect(resultado).toBeNull();
    expect(servicio.crear).not.toHaveBeenCalled();
  });

  it('con una pendiente sobre el recurso (409) muestra el mensaje del servidor', async () => {
    servicio.crear.and.returnValue(
      throwError(
        () => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya hay una solicitud pendiente sobre este recurso.' } })
      )
    );

    const resultado = await pedirAlPropietario(servicio, opciones());

    expect(resultado).toBeNull();
    expect(toast).toHaveBeenCalledWith(
      jasmine.objectContaining({ icon: 'error', title: 'Ya hay una solicitud pendiente sobre este recurso.' })
    );
  });
});
