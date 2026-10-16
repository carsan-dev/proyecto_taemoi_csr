import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ListadoAlumnosComponent } from './listado-alumnos.component';
import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';

describe('ListadoAlumnosComponent deportes de la página', () => {
  const kickboxing = {
    id: 21, deporte: 'KICKBOXING', activo: true, principal: false,
    fechaAlta: '2024-01-01', fechaAltaInicial: '2022-01-01',
  };
  const taekwondo = {
    id: 20, deporte: 'TAEKWONDO', activo: true, principal: true,
    fechaAlta: '2023-09-01', fechaAltaInicial: '2023-09-01',
  };
  const pilatesInactivo = {
    id: 22, deporte: 'PILATES', activo: false, principal: false,
    fechaAlta: '2021-01-01', fechaAltaInicial: '2021-01-01',
  };

  let endpoints: any;
  let alumnoService: any;
  let component: ListadoAlumnosComponent;

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['obtenerAlumnos']);
    alumnoService = jasmine.createSpyObj('AlumnoService', ['obtenerDeportesDelAlumno']);
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI), { provide: InformePdfService, useValue: {} }] });
    component = TestBed.runInInjectionContext(
      () => new ListadoAlumnosComponent(endpoints, alumnoService, {} as any, {} as any, {} as any)
    );
    spyOn(sessionStorage, 'setItem');
  });

  it('usa los deportes que trae cada alumno de la página, sin una petición por alumno', () => {
    endpoints.obtenerAlumnos.and.returnValue(of({
      content: [
        { id: 1, activo: true, deportes: [kickboxing, taekwondo, pilatesInactivo] },
        { id: 2, activo: false, deportes: [pilatesInactivo] },
        { id: 3, activo: true },
      ],
      totalPages: 4,
    }));

    component.obtenerAlumnos();

    expect(alumnoService.obtenerDeportesDelAlumno).not.toHaveBeenCalled();
    expect(component.totalPaginas).toBe(4);
    // Solo activos y el principal primero, como con la petición individual.
    expect(component.getDeportesDeAlumno(1).map((d) => d.deporte)).toEqual(['TAEKWONDO', 'KICKBOXING']);
    expect(component.getDeportesDetalle({ id: 2, activo: false }).map((d) => d.deporte)).toEqual(['PILATES']);
    expect(component.getDeportesDeAlumno(3)).toEqual([]);
    expect(component.tieneDeportesCargados(3)).toBeTrue();
  });

  it('al cambiar de página no conserva los deportes de la anterior', () => {
    endpoints.obtenerAlumnos.and.returnValues(
      of({ content: [{ id: 1, activo: true, deportes: [taekwondo] }], totalPages: 2 }),
      of({ content: [{ id: 5, activo: true, deportes: [kickboxing] }], totalPages: 2 })
    );

    component.obtenerAlumnos();
    component.obtenerAlumnos();

    expect(component.tieneDeportesCargados(1)).toBeFalse();
    expect(component.getDeportesDeAlumno(5).map((d) => d.deporte)).toEqual(['KICKBOXING']);
  });
});
