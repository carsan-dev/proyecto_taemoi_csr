import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { ListadoConvocatoriasComponent } from './listado-convocatorias.component';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';

/** Pieza 6c-1: la convocatoria muestra el mensaje del 409 del backend (decisiones 186 y 194). */
describe('ListadoConvocatoriasComponent: bloqueos del examen pagado', () => {
  const EXAMEN_PAGADO = 'El examen está pagado: revierte el cobro antes';
  const CUANTIA_EXAMEN_PAGADO = 'El examen está pagado: no se puede cambiar la cuantía. Revierte el cobro antes.';

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
    return component;
  }

  const conflicto = (mensaje: string) =>
    throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje } }));

  /** Deja correr la promesa de la confirmación y lo que encadena. */
  const esperar = () => new Promise<void>((resolve) => setTimeout(resolve));

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['eliminarAlumnoDeConvocatoria', 'actualizarAlumnoConvocatoria']);
    swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true } as any));
  });

  it('quitar con el examen pagado muestra el aviso del backend', async () => {
    endpoints.eliminarAlumnoDeConvocatoria.and.returnValue(conflicto(EXAMEN_PAGADO));
    const component = crear();

    component.eliminarAlumnoDeConvocatoria({ alumnoId: 10, nombre: 'Alumno' });
    await esperar();

    expect(endpoints.eliminarAlumnoDeConvocatoria).toHaveBeenCalledWith(10, 7);
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: EXAMEN_PAGADO }));
  });

  it('sin mensaje del backend, el texto de siempre', async () => {
    endpoints.eliminarAlumnoDeConvocatoria.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: null }))
    );
    const component = crear();

    component.eliminarAlumnoDeConvocatoria({ alumnoId: 10, nombre: 'Alumno' });
    await esperar();

    expect(swal).toHaveBeenCalledWith(
      jasmine.objectContaining({ icon: 'error', text: 'No se pudo eliminar al alumno.' })
    );
  });

  it('cambiar la cuantía de un examen pagado muestra el aviso y recarga', () => {
    endpoints.actualizarAlumnoConvocatoria.and.returnValue(conflicto(CUANTIA_EXAMEN_PAGADO));
    const component = crear();
    const recargar = spyOn(component, 'seleccionarConvocatoria').and.stub();

    component.actualizarAlumnoConvocatoria({ id: 3, cuantiaExamen: 25, pagado: true });

    expect(endpoints.actualizarAlumnoConvocatoria).toHaveBeenCalledWith(3, { cuantiaExamen: 25, pagado: true });
    expect(swal).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', text: CUANTIA_EXAMEN_PAGADO }));
    expect(recargar).toHaveBeenCalledWith(component.convocatoriaSeleccionada);
  });

  it('al actualizar, sin mensaje del backend, el texto de siempre', () => {
    endpoints.actualizarAlumnoConvocatoria.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: null }))
    );
    const component = crear();
    spyOn(component, 'seleccionarConvocatoria').and.stub();

    component.actualizarAlumnoConvocatoria({ id: 3, cuantiaExamen: 25, pagado: true });

    expect(swal).toHaveBeenCalledWith(
      jasmine.objectContaining({ icon: 'error', text: 'No se pudo actualizar el alumno.' })
    );
  });
});
