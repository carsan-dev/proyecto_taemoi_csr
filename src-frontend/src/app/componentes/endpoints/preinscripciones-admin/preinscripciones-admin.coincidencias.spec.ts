// R2-P7a: el listado ya no trae las fichas compatibles (`alumnosCoincidentes`). Al abrir una fila se pide el detalle
// (GET /api/preinscripciones/{ref}) y el panel de finalizar usa sus coincidencias; mientras llegan, o si falla, no se
// puede abrir la finalización.
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { PreinscripcionService } from '../../../features/preinscripcion/preinscripcion.service';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { Toast } from '../../../utils/toast.util';
import { PreinscripcionesAdminComponent } from './preinscripciones-admin.component';

/** Fila tal como llega ahora del listado: sin `alumnosCoincidentes`. */
function fila(extra: Record<string, unknown> = {}) {
  return {
    referencia: 'PRE-1', nombre: 'Ana', apellidos: 'García López', dni: '12345678Z', deporte: 'TAEKWONDO',
    estado: 'PENDIENTE', email: 'ana@example.com', telefono: '612345678', fechaNacimiento: [2014, 4, 12],
    direccion: 'Calle Mayor 1', tieneDiscapacidad: false, grupo: 'Taekwondo · Lunes 18:00', turnosSolicitados: [],
    ...extra,
  };
}

const CANDIDATA = {
  id: 3, nif: '12345678Z', nombre: 'Ana', apellidos: 'García López', fechaNacimiento: [2014, 4, 12],
  direccion: 'Calle Mayor 1', telefono: '612345678', email: 'ana@example.com', tieneDiscapacidad: false,
  activo: true, requiereDatosDeporte: false, tipoTarifa: 'INFANTIL', cuantiaTarifa: 30,
};

describe('PreinscripcionesAdminComponent · fichas compatibles del detalle', () => {
  let api: any;
  let fixture: ComponentFixture<PreinscripcionesAdminComponent>;
  let component: PreinscripcionesAdminComponent;

  beforeEach(() => {
    api = {
      listar: jasmine.createSpy('listar').and.returnValue(of({ content: [fila()], page: 0, totalElements: 1, totalPages: 1 })),
      firma: jasmine.createSpy('firma').and.returnValue(of(new Blob())),
      detalle: jasmine.createSpy('detalle').and.returnValue(of({ ...fila(), alumnosCoincidentes: [CANDIDATA] })),
    };
    TestBed.configureTestingModule({
      imports: [PreinscripcionesAdminComponent],
      providers: [
        proveerCatalogoTesting(CATALOGO_TAEMOI),
        { provide: PreinscripcionService, useValue: api },
        { provide: AuthenticationService, useValue: { tieneRolAdmin: () => true } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
      ],
    });
    fixture = TestBed.createComponent(PreinscripcionesAdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  const panel = () => fixture.nativeElement.querySelector('.detail-panel') as HTMLElement;
  const botonFinalizar = () =>
    Array.from(panel().querySelectorAll('button')).find((b) => b.textContent?.includes('Revisar y finalizar alta')) as HTMLButtonElement;

  it('al abrir una fila pide el detalle y el panel de finalizar usa sus coincidencias (DNI exacto: se vincula)', () => {
    const item = component.items[0];
    component.seleccionar(item);
    fixture.detectChanges();

    expect(api.detalle).toHaveBeenCalledOnceWith('PRE-1');
    expect(component.seleccion.alumnosCoincidentes).toEqual([CANDIDATA]);
    expect(item.alumnosCoincidentes).toBeUndefined();
    expect(panel().querySelector('.student-match')?.textContent).toContain('1 ficha compatible');
    expect(botonFinalizar().disabled).toBeFalse();

    component.abrirFinalizacion();
    fixture.detectChanges();

    expect(component.mostrandoFinalizacion).toBeTrue();
    expect(component.accionAlumno).toBe('VINCULAR_EXISTENTE');
    expect(component.alumnoSeleccionado).toEqual(CANDIDATA);
    expect(component.tieneCoincidenciaDniExacta).toBeTrue();
    expect(fixture.nativeElement.querySelector('.admission-modal')?.textContent).toContain('Usar Ana García López');
  });

  it('sin coincidencias en el detalle propone crear una ficha nueva, como antes', () => {
    api.detalle.and.returnValue(of({ ...fila(), alumnosCoincidentes: [] }));
    component.seleccionar(component.items[0]);
    fixture.detectChanges();

    expect(panel().querySelector('.no-match')?.textContent).toContain('No se ha encontrado una ficha compatible.');
    component.abrirFinalizacion();
    expect(component.accionAlumno).toBe('CREAR_NUEVO');
  });

  it('mientras llega el detalle muestra que busca fichas y no deja abrir la finalización', () => {
    const respuesta = new Subject<any>();
    api.detalle.and.returnValue(respuesta);
    component.seleccionar(component.items[0]);
    fixture.detectChanges();

    expect(component.cargandoCoincidencias).toBeTrue();
    expect(panel().querySelector('.student-match')?.textContent).toContain('Buscando fichas compatibles…');
    expect(botonFinalizar().disabled).toBeTrue();
    component.abrirFinalizacion();
    expect(component.mostrandoFinalizacion).toBeFalse();

    respuesta.next({ ...fila(), alumnosCoincidentes: [CANDIDATA] });
    fixture.detectChanges();

    expect(component.cargandoCoincidencias).toBeFalse();
    expect(botonFinalizar().disabled).toBeFalse();
  });

  it('si el detalle falla lo dice, no deja finalizar y al volver a abrir la fila lo reintenta', () => {
    const toast = spyOn(Toast, 'fire');
    api.detalle.and.returnValue(throwError(() => new Error('500')));
    component.seleccionar(component.items[0]);
    fixture.detectChanges();

    expect(component.errorCoincidencias).toBeTrue();
    expect(toast).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', title: 'No se pudieron cargar las fichas compatibles.' }));
    expect(panel().querySelector('.no-match')?.textContent).toContain('No se pudieron cargar las fichas compatibles.');
    expect(botonFinalizar().disabled).toBeTrue();
    component.abrirFinalizacion();
    expect(component.mostrandoFinalizacion).toBeFalse();

    api.detalle.and.returnValue(of({ ...fila(), alumnosCoincidentes: [CANDIDATA] }));
    component.seleccionar(component.items[0]);
    fixture.detectChanges();

    expect(api.detalle).toHaveBeenCalledTimes(2);
    expect(component.errorCoincidencias).toBeFalse();
    expect(botonFinalizar().disabled).toBeFalse();
  });

  it('una fila que no está PENDIENTE no pide el detalle ni muestra carga o error de coincidencias', () => {
    const toast = spyOn(Toast, 'fire');
    api.detalle.and.returnValue(throwError(() => new Error('500')));
    for (const estado of ['EN_LISTA_ESPERA', 'FINALIZADA', 'CANCELADA']) {
      component.seleccionar(fila({ referencia: 'PRE-' + estado, estado }));
      fixture.detectChanges();

      expect(component.cargandoCoincidencias).withContext(estado).toBeFalse();
      expect(component.errorCoincidencias).withContext(estado).toBeFalse();
      expect(panel().querySelector('.student-match')).withContext(estado).toBeNull();
    }
    expect(api.detalle).not.toHaveBeenCalled();
    expect(toast).not.toHaveBeenCalled();
  });

  it('al pasar de una fila PENDIENTE que carga a otra que no lo está, se descarta la carga', () => {
    const respuesta = new Subject<any>();
    api.detalle.and.returnValue(respuesta);
    component.seleccionar(component.items[0]);
    expect(component.cargandoCoincidencias).toBeTrue();

    component.seleccionar(fila({ referencia: 'PRE-2', estado: 'FINALIZADA' }));
    respuesta.error(new Error('500'));

    expect(component.cargandoCoincidencias).toBeFalse();
    expect(component.errorCoincidencias).toBeFalse();
    expect(component.seleccion.referencia).toBe('PRE-2');
  });

  it('la respuesta de una fila anterior no pisa la seleccionada después', () => {
    const primera = new Subject<any>();
    api.detalle.and.returnValues(primera, of({ ...fila({ referencia: 'PRE-2' }), alumnosCoincidentes: [] }));
    component.seleccionar(component.items[0]);
    component.seleccionar(fila({ referencia: 'PRE-2' }));

    primera.next({ ...fila(), alumnosCoincidentes: [CANDIDATA] });

    expect(component.seleccion.referencia).toBe('PRE-2');
    expect(component.seleccion.alumnosCoincidentes).toEqual([]);
    expect(component.cargandoCoincidencias).toBeFalse();
  });
});
