import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { EditarAlumnoComponent } from './editar-alumno.component';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';

/** Pieza 6c-1: la ficha muestra el mensaje del 409 del backend al quitar de una convocatoria (decisión 186). */
describe('EditarAlumnoComponent: bloqueos del examen pagado', () => {
  const EXAMEN_PAGADO = 'El examen está pagado: haz la devolución o revierte el cobro antes';

  let endpoints: jasmine.SpyObj<any>;
  let swal: jasmine.Spy;

  function crear(): EditarAlumnoComponent {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    const component = TestBed.runInInjectionContext(
      () => new EditarAlumnoComponent(endpoints, {} as any, new FormBuilder(), {} as any, {} as any, {} as any, {} as any)
    );
    component.alumnoId = 10;
    return component;
  }

  const esperar = () => new Promise<void>((resolve) => setTimeout(resolve));
  const convocatoria = { id: 5, deporte: 'TAEKWONDO', fechaConvocatoria: '2026-06-01' };

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['eliminarAlumnoDeConvocatoria']);
    swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true } as any));
  });

  it('quitar de la convocatoria con examen pagado: aviso del backend', async () => {
    endpoints.eliminarAlumnoDeConvocatoria.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: EXAMEN_PAGADO } }))
    );
    const component = crear();

    component.eliminarDeConvocatoriaSeleccionada(convocatoria);
    await esperar();

    expect(endpoints.eliminarAlumnoDeConvocatoria).toHaveBeenCalledWith(10, 5);
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: EXAMEN_PAGADO }));
  });

  it('sin mensaje del backend, el texto de siempre', async () => {
    endpoints.eliminarAlumnoDeConvocatoria.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: null }))
    );
    const component = crear();

    component.eliminarDeConvocatoriaSeleccionada(convocatoria);
    await esperar();

    expect(swal).toHaveBeenCalledWith(
      jasmine.objectContaining({ icon: 'error', text: 'No se pudo eliminar al alumno de la convocatoria.' })
    );
  });
});
