import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import Swal, { SweetAlertResult } from 'sweetalert2';

import { ListadoConvocatoriasComponent } from './listado-convocatorias.component';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { itSinReloj } from '../../../testing/reloj-prueba';

/**
 * Decisión 235: al añadir un alumno a la convocatoria, «Por Recompensa» y «Por Antigüedad» son botones propios
 * (confirmar y denegar) y cerrar el diálogo (Esc, clic fuera, la X o «Cancelar») no asigna nada.
 */
describe('ListadoConvocatoriasComponent: tipo de precio al añadir un alumno', () => {
  let endpoints: jasmine.SpyObj<any>;
  let swal: jasmine.Spy;

  function crear(): ListadoConvocatoriasComponent {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [proveerCatalogoTesting(CATALOGO_TAEMOI), { provide: InformePdfService, useValue: {} }],
    });
    const component = TestBed.runInInjectionContext(
      () => new ListadoConvocatoriasComponent(endpoints, { detectChanges: () => undefined } as any, {} as any)
    );
    component.convocatoriaSeleccionada = { id: 7, deporte: 'TAEKWONDO' };
    component.alumnoSeleccionado = 10;
    component.alumnosFiltrados = [{ id: 10, grado: 'BLANCO', fechaNacimiento: '2015-01-01' }];
    spyOn(component, 'seleccionarConvocatoria').and.stub();
    return component;
  }

  /** Deja correr la promesa del diálogo y lo que encadena. */
  const esperar = () => new Promise<void>((resolve) => setTimeout(resolve));

  function responder(resultado: Partial<SweetAlertResult>): void {
    swal.and.returnValue(
      Promise.resolve({ isConfirmed: false, isDenied: false, isDismissed: false, ...resultado } as SweetAlertResult)
    );
  }

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['agregarAlumnoAConvocatoria']);
    endpoints.agregarAlumnoAConvocatoria.and.returnValue(of({}));
    swal = spyOn(Swal, 'fire');
  });

  itSinReloj('pregunta con «Por Recompensa», «Por Antigüedad» y «Cancelar» como botones distintos', async () => {
    responder({ isDismissed: true, dismiss: Swal.DismissReason.esc });
    crear().agregarAlumnoAConvocatoria();
    await esperar();

    expect(swal.calls.first().args[0]).toEqual(
      jasmine.objectContaining({
        text: '¿Asignar el precio por antigüedad o por recompensa?',
        confirmButtonText: 'Por Recompensa',
        showDenyButton: true,
        denyButtonText: 'Por Antigüedad',
        showCancelButton: true,
        cancelButtonText: 'Cancelar',
      })
    );
  });

  for (const motivo of ['esc', 'backdrop', 'close', 'cancel'] as const) {
    itSinReloj(`cerrar el diálogo (${motivo}) no llama al backend`, async () => {
      responder({ isDismissed: true, dismiss: Swal.DismissReason[motivo] });
      crear().agregarAlumnoAConvocatoria();
      await esperar();

      expect(endpoints.agregarAlumnoAConvocatoria).not.toHaveBeenCalled();
      expect(swal).toHaveBeenCalledTimes(1);
    });
  }

  itSinReloj('«Por Recompensa» asigna por recompensa', async () => {
    responder({ isConfirmed: true });
    crear().agregarAlumnoAConvocatoria();
    await esperar();

    expect(endpoints.agregarAlumnoAConvocatoria).toHaveBeenCalledWith(10, 7, true, false);
  });

  itSinReloj('«Por Antigüedad» asigna por antigüedad', async () => {
    responder({ isDenied: true });
    crear().agregarAlumnoAConvocatoria();
    await esperar();

    expect(endpoints.agregarAlumnoAConvocatoria).toHaveBeenCalledWith(10, 7, false, false);
  });
});
