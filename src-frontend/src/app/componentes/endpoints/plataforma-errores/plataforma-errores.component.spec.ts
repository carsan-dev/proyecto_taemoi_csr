import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { PlataformaErroresComponent } from './plataforma-errores.component';
import { ventanaUltimosDias } from '../../../utils/ventana-fechas.util';
import { PlataformaService } from '../../../servicios/plataforma/plataforma.service';

describe('PlataformaErroresComponent', () => {
  let fixture: ComponentFixture<PlataformaErroresComponent>;
  let component: PlataformaErroresComponent;
  let plataformaSpy: jasmine.SpyObj<PlataformaService>;

  const pagina = (content: any[], totalPages = 1) =>
    of({ content, totalPages, totalElements: content.length, size: 25, number: 0 } as any);

  const errores = [
    { id: 1, fechaEvento: '2026-10-16T10:00:00Z', organizacionId: 4, metodoHttp: 'GET', endpoint: '/api/alumnos',
      modulo: 'alumnos', estadoHttp: 500, usuarioId: 12, resumen: 'Fallo interno' },
    { id: 2, fechaEvento: '2026-10-15T09:00:00Z', organizacionId: 2, metodoHttp: 'POST', endpoint: '/api/grupos',
      modulo: 'grupos', estadoHttp: 404, usuarioId: null, resumen: 'No encontrado' },
  ];

  beforeEach(async () => {
    plataformaSpy = jasmine.createSpyObj<PlataformaService>('PlataformaService', ['errores', 'esAdminPlataforma']);
    plataformaSpy.errores.and.returnValue(pagina(errores));

    await TestBed.configureTestingModule({
      imports: [PlataformaErroresComponent],
      providers: [{ provide: PlataformaService, useValue: plataformaSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(PlataformaErroresComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('carga los últimos 7 días con ambas familias de estado', () => {
    const ventana = ventanaUltimosDias();
    expect(plataformaSpy.errores).toHaveBeenCalledWith(
      jasmine.objectContaining({ desde: ventana.desde, hasta: ventana.hasta, estado: null, organizacionId: null, page: 1, size: 25 })
    );
  });

  it('construye el selector de escuela con las escuelas vistas', () => {
    expect(component.escuelas).toEqual([2, 4]);
    const opciones = Array.from(fixture.nativeElement.querySelectorAll('#filtroEscuela option')).map((o: any) => o.textContent.trim());
    expect(opciones).toEqual(['Todas', 'Escuela 2', 'Escuela 4']);
  });

  it('pinta estado con tono por familia y la petición con método y endpoint', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.estado.estado-5xx')?.textContent?.trim()).toBe('500');
    expect(el.querySelector('.estado.estado-4xx')?.textContent?.trim()).toBe('404');
    expect(el.querySelector('.col-peticion')?.textContent).toContain('GET');
    expect(el.querySelector('.col-peticion')?.textContent).toContain('/api/alumnos');
  });

  it('al desmarcar 4xx filtra por 5xx y no deja desmarcar ambas', () => {
    component.alternarEstado('4xx');
    expect(plataformaSpy.errores.calls.mostRecent().args[0].estado).toBe('5xx');

    const llamadas = plataformaSpy.errores.calls.count();
    component.alternarEstado('5xx');
    expect(component.estados['5xx']).toBeTrue();
    expect(plataformaSpy.errores.calls.count()).toBe(llamadas);
  });

  it('muestra el estado vacío', () => {
    plataformaSpy.errores.and.returnValue(pagina([], 0));
    component.aplicarFiltros();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sin errores en este periodo');
  });

  it('un 400 muestra un aviso sobre las fechas', () => {
    plataformaSpy.errores.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    component.aplicarFiltros();
    fixture.detectChanges();

    const aviso = fixture.nativeElement.querySelector('.aviso-fechas');
    expect(aviso?.textContent).toContain('fechas');
  });

  it('no consulta si Desde es posterior a Hasta', () => {
    const llamadas = plataformaSpy.errores.calls.count();
    component.filtroDesde = '2026-10-16';
    component.filtroHasta = '2026-10-01';
    component.aplicarFiltros();
    fixture.detectChanges();

    expect(plataformaSpy.errores.calls.count()).toBe(llamadas);
    expect(fixture.nativeElement.querySelector('.aviso-fechas')).not.toBeNull();
  });
});

describe('ventanaUltimosDias', () => {
  it('cuenta 7 días incluyendo hoy', () => {
    expect(ventanaUltimosDias(7, new Date(2026, 9, 16))).toEqual({ desde: '2026-10-10', hasta: '2026-10-16' });
  });
});
