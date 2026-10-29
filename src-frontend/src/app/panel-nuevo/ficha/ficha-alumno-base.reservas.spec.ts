import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { of } from 'rxjs';
import Swal, { SweetAlertResult } from 'sweetalert2';

import { FichaAlumnoBase } from './ficha-alumno-base';
import { proveerCatalogoTesting } from '../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../features/catalogo/testing/catalogo.fixtures';
import { itSinReloj } from '../../testing/reloj-prueba';

/**
 * Decisión 248: en «¿Ha sido abonada…?», «Sí» y «No» son botones propios (confirmar y denegar) y cerrar el diálogo
 * (Esc, clic fuera, la X o «Cancelar») no crea ninguna reserva.
 */
describe('FichaAlumnoBase: ¿abonada? al reservar plaza', () => {
  let endpoints: jasmine.SpyObj<any>;
  let swal: jasmine.Spy;

  function crear(): any {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    const component = TestBed.runInInjectionContext(
      () => new FichaAlumnoBase(endpoints, {} as any, new FormBuilder(), {} as any, {} as any, {} as any, {} as any)
    );
    component.alumnoId = 10;
    spyOn(component as any, 'obtenerProductosAlumno').and.stub();
    return component;
  }

  const esperar = () => new Promise<void>((resolve) => setTimeout(resolve));

  function responder(resultado: Partial<SweetAlertResult>): void {
    swal.and.returnValue(
      Promise.resolve({ isConfirmed: false, isDenied: false, isDismissed: false, ...resultado } as SweetAlertResult)
    );
  }

  const deportes = [{ deporte: 'TAEKWONDO' }, { deporte: 'KICKBOXING' }];
  const casos: Array<{ nombre: string; abrir: (c: any) => void; titulo: string; confirmar: string }> = [
    {
      nombre: 'un deporte',
      abrir: (c) => c.confirmarPagoReservaDeporte(10, 'TAEKWONDO'),
      titulo: '¿Ha sido abonada la reserva?',
      confirmar: 'Sí, pagada',
    },
    {
      nombre: 'todos los deportes',
      abrir: (c) => c.confirmarPagoReservaTodosDeportes(10, deportes),
      titulo: '¿Han sido abonadas las reservas?',
      confirmar: 'Sí, pagadas',
    },
  ];

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['reservarPlazaPorDeporte']);
    endpoints.reservarPlazaPorDeporte.and.returnValue(of({}));
    swal = spyOn(Swal, 'fire');
  });

  for (const caso of casos) {
    describe(caso.nombre, () => {
      itSinReloj('pregunta con «Sí», «No» y «Cancelar» como botones distintos', async () => {
        responder({ isDismissed: true, dismiss: Swal.DismissReason.esc });
        caso.abrir(crear());
        await esperar();

        expect(swal.calls.first().args[0]).toEqual(
          jasmine.objectContaining({
            title: caso.titulo,
            confirmButtonText: caso.confirmar,
            showDenyButton: true,
            denyButtonText: 'No',
            showCancelButton: true,
            cancelButtonText: 'Cancelar',
          })
        );
      });

      for (const motivo of ['esc', 'backdrop', 'close', 'cancel'] as const) {
        itSinReloj(`cerrar el diálogo (${motivo}) no crea ninguna reserva`, async () => {
          responder({ isDismissed: true, dismiss: Swal.DismissReason[motivo] });
          const component = crear();
          caso.abrir(component);
          await esperar();

          expect(endpoints.reservarPlazaPorDeporte).not.toHaveBeenCalled();
          expect(component.obtenerProductosAlumno).not.toHaveBeenCalled();
          expect(swal).toHaveBeenCalledTimes(1);
        });
      }

      itSinReloj('«Sí» reserva como pagada', async () => {
        responder({ isConfirmed: true });
        caso.abrir(crear());
        await esperar();

        expect(endpoints.reservarPlazaPorDeporte).toHaveBeenCalled();
        for (const args of endpoints.reservarPlazaPorDeporte.calls.allArgs()) {
          expect(args[2]).toBeTrue();
        }
      });

      itSinReloj('«No» reserva como no pagada', async () => {
        responder({ isDenied: true });
        caso.abrir(crear());
        await esperar();

        expect(endpoints.reservarPlazaPorDeporte).toHaveBeenCalled();
        for (const args of endpoints.reservarPlazaPorDeporte.calls.allArgs()) {
          expect(args[2]).toBeFalse();
        }
      });
    });
  }
});
