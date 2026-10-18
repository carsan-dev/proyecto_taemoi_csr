import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';

import { ListadoAlumnosComponent } from './listado-alumnos.component';
import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';

/** R2-P6: el filtro por deporte lo hace el servidor; el listado ya no descarga todos los alumnos. */
describe('ListadoAlumnosComponent filtro por deporte en el servidor', () => {
  const kickboxingDeBaja = { id: 31, deporte: 'KICKBOXING', activo: false, principal: false, fechaAlta: '2024-01-01' };
  const taekwondo = { id: 30, deporte: 'TAEKWONDO', activo: true, principal: true, fechaAlta: '2023-09-01' };
  const pagina = (content: any[], totalPages: number) => ({ content, totalPages, totalElements: content.length });

  let endpoints: any;
  let component: ListadoAlumnosComponent;

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['obtenerAlumnos']);
    TestBed.configureTestingModule({
      providers: [proveerCatalogoTesting(CATALOGO_TAEMOI), { provide: InformePdfService, useValue: {} }],
    });
    component = TestBed.runInInjectionContext(
      () => new ListadoAlumnosComponent(endpoints, {} as any, {} as any, {} as any, {} as any)
    );
    component.tamanoPagina = 6;
    spyOn(sessionStorage, 'setItem');
  });

  it('pide la página al servidor con el deporte y usa lo que devuelve', () => {
    endpoints.obtenerAlumnos.and.returnValue(of(pagina([{ id: 7, activo: true, deportes: [taekwondo, kickboxingDeBaja] }], 3)));
    component.paginaActual = 2;
    component.deporteFiltro = 'KICKBOXING';

    component.filtrarPorDeporte();

    expect(endpoints.obtenerAlumnos).toHaveBeenCalledOnceWith(1, 6, '', false, false, 'KICKBOXING');
    expect(component.alumnos.map((a) => a.id)).toEqual([7]);
    expect(component.totalPaginas).toBe(3);
    expect(component.usandoPaginacionCliente).toBeTrue();
    // Los deportes de la tarjeta, los de la página: activos y el principal primero.
    expect(component.getDeportesDeAlumno(7).map((d) => d.deporte)).toEqual(['TAEKWONDO']);
  });

  it('cambia de página, busca e incluye inactivos sin salir del filtro', () => {
    endpoints.obtenerAlumnos.and.returnValue(of(pagina([], 4)));
    component.deporteFiltro = 'PILATES';
    component.filtrarPorDeporte();

    component.cambiarPagina(3);
    expect(endpoints.obtenerAlumnos).toHaveBeenCalledWith(3, 6, '', false, false, 'PILATES');

    component.nombreFiltro = ' Lucía ';
    component.filtrarPorNombre();
    expect(endpoints.obtenerAlumnos).toHaveBeenCalledWith(1, 6, ' Lucía ', false, false, 'PILATES');

    component.alternarInactivos();
    expect(endpoints.obtenerAlumnos).toHaveBeenCalledWith(1, 6, ' Lucía ', true, false, 'PILATES');
  });

  it('como antes, con deporte no aplica «Solo aptos»', () => {
    endpoints.obtenerAlumnos.and.returnValue(of(pagina([], 0)));
    component.aptoParaExamenFiltro = true;
    component.deporteFiltro = 'TAEKWONDO';

    component.filtrarPorDeporte();

    expect(endpoints.obtenerAlumnos).toHaveBeenCalledOnceWith(1, 6, '', false, false, 'TAEKWONDO');
  });

  it('una búsqueda nueva cancela la respuesta pendiente de la anterior', () => {
    const primera = new Subject<any>();
    const segunda = new Subject<any>();
    endpoints.obtenerAlumnos.and.returnValues(primera, segunda);
    component.deporteFiltro = 'KICKBOXING';
    component.usandoPaginacionCliente = true;

    component.nombreFiltro = 'l';
    component.filtrarPorNombre();
    component.nombreFiltro = 'lu';
    component.filtrarPorNombre();
    // La cancelada no deja la lista como cargada mientras la nueva sigue en vuelo.
    expect(component.cargando).toBeTrue();
    segunda.next(pagina([{ id: 2, deportes: [] }], 1));
    primera.next(pagina([{ id: 1, deportes: [] }], 1));

    expect(primera.observed).toBeFalse();
    expect(component.alumnos.map((a) => a.id)).toEqual([2]);
  });

  it('con «Todos los deportes» vuelve al listado sin deporte', () => {
    endpoints.obtenerAlumnos.and.returnValue(of(pagina([], 0)));
    component.deporteFiltro = 'KICKBOXING';
    component.filtrarPorDeporte();
    component.deporteFiltro = 'TODOS';

    component.filtrarPorDeporte();

    expect(endpoints.obtenerAlumnos.calls.mostRecent().args).toEqual([1, 6, '', false, false]);
    expect(component.usandoPaginacionCliente).toBeFalse();
  });
});
