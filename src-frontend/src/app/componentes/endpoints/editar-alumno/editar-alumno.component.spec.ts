import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { PageScrollSnapshot } from '../../../servicios/generales/page-scroll.service';
import { EditarAlumnoComponent } from './editar-alumno.component';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';

describe('EditarAlumnoComponent scroll continuity', () => {
  afterEach(() => Swal.close());

  it('reloads a saved student in the background and restores the active section', () => {
    const endpoints = {
      actualizarAlumno: jasmine.createSpy('actualizarAlumno').and.returnValue(of({})),
    };
    const snapshot: PageScrollSnapshot = {
      position: [0, 1450],
      anchor: { key: 'alumno-informacion-personal', viewportOffset: 90 },
    };
    const pageScroll = jasmine.createSpyObj('PageScrollService', [
      'captureSnapshot',
      'restoreSnapshotAfterNextRender',
    ]);
    pageScroll.captureSnapshot.and.returnValue(snapshot);
    pageScroll.restoreSnapshotAfterNextRender.and.returnValue(Promise.resolve(true));
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    const component = TestBed.runInInjectionContext(
      () =>
        new EditarAlumnoComponent(
          endpoints as any,
          {} as any,
          new FormBuilder(),
          {} as any,
          {} as any,
          {} as any,
          pageScroll
        )
    );
    component.alumnoId = 7;
    component.alumno = {
      id: 7,
      nombre: 'Ana',
      apellidos: 'Díaz',
      direccion: 'Calle Uno',
      fechaNacimiento: '2000-01-01',
      nif: '12345678Z',
      email: 'ana@example.test',
      telefono: 600000000,
      telefono2: null,
      responsableLegalNombre: null,
      responsableLegalNif: null,
      tieneDiscapacidad: false,
      autorizacionWeb: true,
      fechaBaja: null,
      observaciones: '',
      tipoTarifa: 'GENERAL',
      fechaAlta: '2024-01-01',
      cuantiaTarifa: 30,
      rolFamiliar: null,
      grupoFamiliar: null,
    };
    component.pendingBasicInfoChanges = { nombre: 'Ana María' };
    component.editingBasicInfo = true;
    spyOn(component, 'cargarAlumno').and.callFake((_id, afterLoad) => afterLoad?.());

    component.applyBasicInfoChanges();

    expect(pageScroll.captureSnapshot).toHaveBeenCalledOnceWith('alumno-informacion-personal');
    expect(component.cargarAlumno).toHaveBeenCalled();
    expect(pageScroll.restoreSnapshotAfterNextRender).toHaveBeenCalledOnceWith(snapshot, 'auto');
    expect(component.editingBasicInfo).toBeFalse();
  });
});

describe('EditarAlumnoComponent navegación entre alumnos', () => {
  let endpoints: any;
  let router: any;
  let params: BehaviorSubject<any>;

  function crear(): EditarAlumnoComponent {
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    const component = TestBed.runInInjectionContext(
      () =>
        new EditarAlumnoComponent(
          endpoints,
          {} as any,
          new FormBuilder(),
          router,
          { params } as any,
          {} as any,
          {} as any
        )
    );
    // Cargas secundarias de la ficha: fuera del alcance de estas pruebas.
    for (const metodo of [
      'cargarCatalogoGruposYTurnos',
      'cargarDocumentosAlumno',
      'obtenerProductosAlumno',
      'cargarConvocatoriasDelAlumno',
      'cargarDeportesDelAlumno',
      'cargarGruposYTurnos',
    ]) {
      spyOn<any>(component, metodo);
    }
    return component;
  }

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['obtenerIdsAlumnos', 'obtenerAlumnoPorId']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    params = new BehaviorSubject<any>({ id: '7' });
    spyOn(Swal, 'fire').and.resolveTo({} as any);
  });

  it('pide la ficha sin esperar a la lista de ids y la muestra con su posición', () => {
    const ids = new Subject<number[]>();
    endpoints.obtenerIdsAlumnos.and.returnValue(ids);
    endpoints.obtenerAlumnoPorId.and.returnValue(of({ id: 7, activo: true }));
    const component = crear();

    component.ngOnInit();

    expect(endpoints.obtenerIdsAlumnos).toHaveBeenCalledOnceWith(false);
    expect(endpoints.obtenerAlumnoPorId).toHaveBeenCalledOnceWith(7);
    expect(component.cargando).toBeTrue();

    ids.next([3, 7, 9]);
    ids.complete();

    expect(component.alumno).toEqual({ id: 7, activo: true });
    expect(component.paginaActual).toBe(2);
    expect(component.totalPaginas).toBe(3);
    expect(component.cargando).toBeFalse();
    expect(router.navigate).not.toHaveBeenCalled();
    expect(Swal.fire).not.toHaveBeenCalled();
  });

  it('alumno inactivo fuera de la lista: pasa a mostrar inactivos y lo sitúa sin volver a pedirlo', () => {
    endpoints.obtenerIdsAlumnos.and.callFake((inactivos: boolean) => of(inactivos ? [3, 7, 9] : [3, 9]));
    endpoints.obtenerAlumnoPorId.and.returnValue(of({ id: 7, activo: false }));
    const component = crear();

    component.ngOnInit();

    expect(component.mostrarInactivos).toBeTrue();
    expect(endpoints.obtenerIdsAlumnos.calls.allArgs()).toEqual([[false], [true]]);
    expect(endpoints.obtenerAlumnoPorId).toHaveBeenCalledOnceWith(7);
    expect(router.navigate).toHaveBeenCalledOnceWith(['/alumnosEditar', 7], { replaceUrl: true });
    expect(component.paginaActual).toBe(2);
    expect(component.totalPaginas).toBe(3);
    expect(component.alumno).toEqual({ id: 7, activo: false });
    expect(component.cargando).toBeFalse();
  });

  it('alumno inexistente: avisa y lleva al primero de la lista', () => {
    endpoints.obtenerIdsAlumnos.and.returnValue(of([3, 9]));
    endpoints.obtenerAlumnoPorId.and.returnValue(throwError(() => new Error('404')));
    const component = crear();

    component.ngOnInit();

    expect(Swal.fire).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ title: 'Alumno no encontrado', text: 'El alumno solicitado no existe.' })
    );
    expect(router.navigate).toHaveBeenCalledOnceWith(['/alumnosEditar', 3], { replaceUrl: true });
    expect(component.cargando).toBeFalse();
    expect(component.alumno).toBeNull();
  });

  it('alumno de la lista que no se puede cargar: mismo mensaje de error que antes', () => {
    endpoints.obtenerIdsAlumnos.and.returnValue(of([3, 7, 9]));
    endpoints.obtenerAlumnoPorId.and.returnValue(throwError(() => new Error('500')));
    const component = crear();

    component.ngOnInit();

    expect(Swal.fire).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ title: 'Error', text: 'No se pudo cargar el alumno con ID 7' })
    );
    expect(component.paginaActual).toBe(2);
    expect(component.cargando).toBeFalse();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('sin id en la ruta: lleva al primer alumno de la lista', () => {
    params.next({});
    endpoints.obtenerIdsAlumnos.and.returnValue(of([3, 9]));
    const component = crear();

    component.ngOnInit();

    expect(endpoints.obtenerAlumnoPorId).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledOnceWith(['/alumnosEditar', 3], { replaceUrl: true });
  });

  it('si falla la lista de ids: mismo aviso que antes y no se muestra ficha', () => {
    endpoints.obtenerIdsAlumnos.and.returnValue(throwError(() => new Error('500')));
    endpoints.obtenerAlumnoPorId.and.returnValue(of({ id: 7, activo: true }));
    const component = crear();

    component.ngOnInit();

    expect(Swal.fire).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ title: 'Error', text: 'No se pudo obtener la lista de alumnos.' })
    );
    expect(component.alumno).toBeNull();
    expect(component.cargando).toBeFalse();
  });

  it('cambiar de página navega al id de esa posición', () => {
    endpoints.obtenerIdsAlumnos.and.returnValue(of([3, 7, 9]));
    endpoints.obtenerAlumnoPorId.and.returnValue(of({ id: 7, activo: true }));
    const component = crear();
    component.ngOnInit();

    component.cambiarPagina(3);

    expect(router.navigate).toHaveBeenCalledOnceWith(['/alumnosEditar', 9]);
  });
});
