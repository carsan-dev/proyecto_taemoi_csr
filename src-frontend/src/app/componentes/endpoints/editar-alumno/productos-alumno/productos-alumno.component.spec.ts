import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import type { AlumnoDeporteDTO } from '../../../../interfaces/alumno-deporte-dto';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { ProductosAlumnoComponent } from './productos-alumno.component';

describe('ProductosAlumnoComponent: matrícula por deporte', () => {
  let component: ProductosAlumnoComponent;
  let endpoints: jasmine.SpyObj<EndpointsService>;

  beforeEach(() => {
    endpoints = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'asignarProductoAAlumno', 'asignarProductoAAlumnoDeporte', 'obtenerProductosDelAlumno',
    ]);
    component = new ProductosAlumnoComponent({} as ActivatedRoute, endpoints, {} as Location);
    component.alumnoId = 10;
    component.products = [{
      id: 20, concepto: 'Matrícula anual', precio: 25,
      fechaAsignacion: new Date('2026-01-20'), cantidad: 1,
      pagado: false, fechaPago: null, notas: '',
    }];
    component.selectedProductoId = 20;
    spyOn(Swal, 'fire').and.stub();
  });

  it('no encola matrícula sin deporte ni crea un cargo', () => {
    component.asignarProducto();
    expect(component.pendingAsignaciones.length).toBe(0);
    expect(endpoints.asignarProductoAAlumno).not.toHaveBeenCalled();
  });

  it('muestra el selector y el deporte pendiente en la plantilla del navegador', async () => {
    await TestBed.configureTestingModule({
      imports: [ProductosAlumnoComponent],
      providers: [
        { provide: ActivatedRoute, useValue: {} },
        { provide: EndpointsService, useValue: endpoints },
        { provide: Location, useValue: {} },
      ],
    }).compileComponents();
    spyOn(ProductosAlumnoComponent.prototype, 'ngOnInit').and.stub();
    const fixture = TestBed.createComponent(ProductosAlumnoComponent);
    const vista = fixture.componentInstance;
    vista.cargando = false;
    vista.products = component.products;
    vista.selectedProductoId = 20;
    vista.deportesAlumno = [{ id: 1, deporte: 'TAEKWONDO' } as AlumnoDeporteDTO];
    fixture.detectChanges();
    await fixture.whenStable();
    const root: HTMLElement = fixture.nativeElement;
    const selector = root.querySelector<HTMLSelectElement>('#deporte-matricula');
    const asignar = Array.from(root.querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.includes('Asignar Producto'))!;
    expect(selector).not.toBeNull();
    expect(root.querySelector('label[for="deporte-matricula"]')?.textContent).toContain('Deporte de la matrícula');
    expect(asignar.disabled).toBeTrue();
    selector!.selectedIndex = 1;
    selector!.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(asignar.disabled).toBeFalse();
    asignar.click();
    fixture.detectChanges();
    expect(root.querySelector('.pending-name')?.textContent).toContain('TAEKWONDO');
    expect(root.querySelector('#deporte-matricula')).toBeNull();
    expect(endpoints.asignarProductoAAlumnoDeporte).not.toHaveBeenCalled();
    fixture.destroy();
  });

  it('rechaza un deporte que no pertenece al alumno', () => {
    component.selectedDeporte = 'PILATES';
    component.asignarProducto();
    expect(component.pendingAsignaciones.length).toBe(0);
  });

  it('encola y confirma dos deportes de forma explícita sin cargos adicionales', () => {
    component.deportesAlumno = [
      { id: 1, deporte: 'TAEKWONDO' } as AlumnoDeporteDTO,
      { id: 2, deporte: 'KICKBOXING' } as AlumnoDeporteDTO,
    ];
    for (const deporte of component.deportesAlumno) {
      component.selectedProductoId = 20;
      component.selectedDeporte = deporte.deporte;
      component.asignarProducto();
    }
    expect(component.pendingAsignaciones).toEqual([
      { productoId: 20, deporte: 'TAEKWONDO' },
      { productoId: 20, deporte: 'KICKBOXING' },
    ]);
    expect(component.selectedDeporte).toBeNull();
    expect(endpoints.asignarProductoAAlumnoDeporte).not.toHaveBeenCalled();
    endpoints.asignarProductoAAlumnoDeporte.and.returnValue(of());
    spyOn(component, 'obtenerProductosAlumno');
    component.confirmarCambiosPendientes();
    expect(endpoints.asignarProductoAAlumnoDeporte.calls.count()).toBe(2);
    expect(endpoints.asignarProductoAAlumnoDeporte.calls.argsFor(0).slice(0, 3)).toEqual([10, 20, 'TAEKWONDO']);
    expect(endpoints.asignarProductoAAlumnoDeporte.calls.argsFor(1).slice(0, 3)).toEqual([10, 20, 'KICKBOXING']);
    expect(endpoints.asignarProductoAAlumno).not.toHaveBeenCalled();
  });

  it('detecta matrícula con acentos y sin acentos, independientemente de mayúsculas', () => {
    for (const concepto of ['Matrícula anual', 'MATRICULA', 'matrícula - PILATES']) {
      component.products[0].concepto = concepto;
      expect(component.esMatriculaSeleccionada()).toBeTrue();
    }
  });

  it('quitar o cancelar pendientes no asigna productos', () => {
    component.products[0].concepto = 'DOBOK';
    component.asignarProducto();
    component.removePendingAsignacion(0);
    expect(component.hasPendingChanges()).toBeFalse();
    component.selectedDeporte = 'PILATES';
    spyOn(component, 'obtenerProductosAlumno');
    component.cancelarCambiosPendientes();
    expect(component.selectedDeporte).toBeNull();
    expect(endpoints.asignarProductoAAlumno).not.toHaveBeenCalled();
  });

  it('conserva la asignación genérica para otros productos', () => {
    component.products[0].concepto = 'DOBOK';
    component.asignarProducto();
    expect(component.pendingAsignaciones).toEqual([{ productoId: 20, deporte: null }]);
    endpoints.asignarProductoAAlumno.and.returnValue(of());
    spyOn(component, 'obtenerProductosAlumno');
    component.confirmarCambiosPendientes();
    expect(endpoints.asignarProductoAAlumno).toHaveBeenCalledWith(10, 20, jasmine.objectContaining({ cantidad: 1 }));
    expect(endpoints.asignarProductoAAlumnoDeporte).not.toHaveBeenCalled();
  });
});
