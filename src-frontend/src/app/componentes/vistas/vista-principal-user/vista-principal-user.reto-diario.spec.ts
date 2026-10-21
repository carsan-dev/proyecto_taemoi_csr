import Swal, { SweetAlertResult } from 'sweetalert2';

import { VistaPrincipalUserComponent } from './vista-principal-user.component';

/**
 * Decisión 248: con la checklist sin confirmar, «Revisar checklist» y «Marcar de todos modos» son botones propios
 * (confirmar y denegar) y cerrar el diálogo (Esc, clic fuera, la X o «Cancelar») no marca el reto.
 */
describe('VistaPrincipalUserComponent: checklist del reto diario sin confirmar', () => {
  let swal: jasmine.Spy;
  let endpoints: jasmine.SpyObj<any>;

  /** Instancia sin constructor: solo se usan completarRetoDiario y lo que encadena. */
  function crear(): any {
    const c = Object.create(VistaPrincipalUserComponent.prototype);
    c.retoCompletadoHoy = false;
    c.checklistRetoDiarioRevisada = false;
    c.endpointsService = endpoints;
    c.obtenerAlumnoSeleccionadoId = () => 10;
    spyOn(c, 'mostrarComoHacerRetoDiario').and.stub();
    spyOn(c, 'registrarCompletadoRetoDiario').and.callThrough();
    return c;
  }

  const esperar = () => new Promise<void>((resolve) => setTimeout(resolve));

  function responder(resultado: Partial<SweetAlertResult>): void {
    swal.and.returnValue(
      Promise.resolve({ isConfirmed: false, isDenied: false, isDismissed: false, ...resultado } as SweetAlertResult)
    );
  }

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['completarRetoDiario']);
    endpoints.completarRetoDiario.and.returnValue({ subscribe: () => undefined });
    swal = spyOn(Swal, 'fire');
  });

  it('pregunta con «Revisar checklist», «Marcar de todos modos» y «Cancelar» como botones distintos', async () => {
    responder({ isDismissed: true, dismiss: Swal.DismissReason.esc });
    crear().completarRetoDiario();
    await esperar();

    expect(swal.calls.first().args[0]).toEqual(
      jasmine.objectContaining({
        text: 'No has confirmado la checklist. ¿Quieres revisarla antes de marcar el reto?',
        confirmButtonText: 'Revisar checklist',
        showDenyButton: true,
        denyButtonText: 'Marcar de todos modos',
        showCancelButton: true,
        cancelButtonText: 'Cancelar',
      })
    );
  });

  for (const motivo of ['esc', 'backdrop', 'close', 'cancel'] as const) {
    it(`cerrar el diálogo (${motivo}) no marca el reto ni cambia el estado`, async () => {
      responder({ isDismissed: true, dismiss: Swal.DismissReason[motivo] });
      const c = crear();
      c.completarRetoDiario();
      await esperar();

      expect(endpoints.completarRetoDiario).not.toHaveBeenCalled();
      expect(c.registrarCompletadoRetoDiario).not.toHaveBeenCalled();
      expect(c.mostrarComoHacerRetoDiario).not.toHaveBeenCalled();
      expect(c.retoCompletadoHoy).toBeFalse();
      expect(c.checklistRetoDiarioRevisada).toBeFalse();
    });
  }

  it('«Revisar checklist» abre la explicación y no marca el reto', async () => {
    responder({ isConfirmed: true });
    const c = crear();
    c.completarRetoDiario();
    await esperar();

    expect(c.mostrarComoHacerRetoDiario).toHaveBeenCalledTimes(1);
    expect(endpoints.completarRetoDiario).not.toHaveBeenCalled();
  });

  it('«Marcar de todos modos» marca el reto', async () => {
    responder({ isDenied: true });
    const c = crear();
    c.completarRetoDiario();
    await esperar();

    expect(endpoints.completarRetoDiario).toHaveBeenCalledWith(10);
    expect(c.mostrarComoHacerRetoDiario).not.toHaveBeenCalled();
  });
});
