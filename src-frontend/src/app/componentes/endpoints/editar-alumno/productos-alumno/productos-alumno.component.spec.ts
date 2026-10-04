import { formatDate, Location } from '@angular/common';
import type { ComponentFixture } from '@angular/core/testing';
import type { ProductoAlumnoDTO } from '../../../../interfaces/producto-alumno-dto';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import type { AlumnoDeporteDTO } from '../../../../interfaces/alumno-deporte-dto';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { ProductosAlumnoComponent } from './productos-alumno.component';

describe('ProductosAlumnoComponent: fecha de asignación', () => {
  let fixture: ComponentFixture<ProductosAlumnoComponent>;
  let component: ProductosAlumnoComponent;
  let endpoints: jasmine.SpyObj<EndpointsService>;
  let stored: ProductoAlumnoDTO;

  beforeEach(async () => {
    // HTTP returns ISO strings, despite the DTO's Date annotation.
    stored = {
      id: 1, productoId: 20, alumnoId: 10, concepto: 'DOBOK',
      fechaAsignacion: '2026-01-20T23:30:12.123-11:00' as unknown as Date,
      fechaPago: '2026-01-25T10:15:00.000Z' as unknown as Date,
      cantidad: 1, precio: 25, pagado: true, notas: '',
    };
    endpoints = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'obtenerProductosDelAlumno', 'actualizarProductoAlumno',
    ]);
    endpoints.obtenerProductosDelAlumno.and.callFake(() => of([{ ...stored }]));
    endpoints.actualizarProductoAlumno.and.callFake((_id, cambios) => {
      // Model the JSON serialization and subsequent HTTP reload.
      stored = JSON.parse(JSON.stringify({ ...stored, ...cambios })) as ProductoAlumnoDTO;
      return of({ ...stored });
    });
    await TestBed.configureTestingModule({
      imports: [ProductosAlumnoComponent],
      providers: [
        { provide: ActivatedRoute, useValue: {} },
        { provide: EndpointsService, useValue: endpoints },
        { provide: Location, useValue: {} },
      ],
    }).compileComponents();
    spyOn(ProductosAlumnoComponent.prototype, 'ngOnInit').and.stub();
    spyOn(Swal, 'fire').and.stub();
    fixture = TestBed.createComponent(ProductosAlumnoComponent);
    component = fixture.componentInstance;
    component.alumnoId = 10;
    component.obtenerProductosAlumno(10);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => fixture.destroy());

  function input(): HTMLInputElement {
    const control = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type="date"]');
    expect(control).withContext('inline calendar control').not.toBeNull();
    if (!control) { throw new Error('Missing assignment date control'); }
    return control;
  }

  async function changeDate(value: string): Promise<void> {
    input().value = value;
    input().dispatchEvent(new Event('input'));
    input().dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('detecta cambios solo de fecha en el snapshot', () => {
    component.productosAlumno[0].fechaAsignacion = new Date(2026, 1, 15, 12);
    component.onProductoChange(component.productosAlumno[0]);
    expect(component.hasPendingChanges()).toBeTrue();
  });

  it('muestra el día del date pipe para ISO con offset cerca de medianoche, sin mutarlo', () => {
    expect(input().value).toBe(formatDate(stored.fechaAsignacion, 'yyyy-MM-dd', 'en-US'));
    expect(input().getAttribute('aria-label')).toContain('DOBOK');
    expect(component.productosAlumno[0].fechaAsignacion).toBe(stored.fechaAsignacion);
    expect(component.hasPendingChanges()).toBeFalse();
  });

  it('edita en el navegador, confirma y recarga el mismo día local conservando hora y fecha de pago', async () => {
    const original = new Date(stored.fechaAsignacion);
    const pago = stored.fechaPago;
    await changeDate('2026-02-15');
    expect(component.hasPendingChanges()).toBeTrue();
    expect(endpoints.actualizarProductoAlumno).not.toHaveBeenCalled();
    const root: HTMLElement = fixture.nativeElement;
    const confirmar = Array.from(root.querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.includes('Confirmar'))!;
    confirmar.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(endpoints.actualizarProductoAlumno).toHaveBeenCalledTimes(1);
    const enviado = endpoints.actualizarProductoAlumno.calls.mostRecent().args[1];
    const fecha = new Date(enviado.fechaAsignacion!);
    expect(formatDate(fecha, 'yyyy-MM-dd', 'en-US')).toBe('2026-02-15');
    expect([fecha.getHours(), fecha.getMinutes(), fecha.getSeconds(), fecha.getMilliseconds()])
      .toEqual([original.getHours(), original.getMinutes(), original.getSeconds(), original.getMilliseconds()]);
    expect(enviado.fechaPago).toBe(pago);
    expect(input().value).toBe('2026-02-15');
    expect(component.hasPendingChanges()).toBeFalse();
  });

  it('cancelar restaura el timestamp original sin guardar y las filas eliminadas deshabilitan la fecha', async () => {
    const original = stored.fechaAsignacion;
    await changeDate('2026-02-15');
    component.cancelarCambiosPendientes();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.productosAlumno[0].fechaAsignacion).toBe(original);
    expect(input().value).toBe(formatDate(original, 'yyyy-MM-dd', 'en-US'));
    expect(component.hasPendingChanges()).toBeFalse();
    expect(endpoints.actualizarProductoAlumno).not.toHaveBeenCalled();
    component.marcarProductoParaEliminar(1);
    fixture.detectChanges();
    expect(input().disabled).toBeTrue();
  });

  it('no modifica timestamps al seleccionar el mismo día o editar otro campo', async () => {
    const original = stored.fechaAsignacion;
    await changeDate(input().value);
    expect(component.hasPendingChanges()).toBeFalse();
    await changeDate(''); // Clearing is not supported.
    expect(component.productosAlumno[0].fechaAsignacion).toBe(original);
    component.productosAlumno[0].cantidad = 2;
    component.onProductoChange(component.productosAlumno[0]);
    component.confirmarCambiosPendientes();
    expect(endpoints.actualizarProductoAlumno.calls.mostRecent().args[1].fechaAsignacion).toBe(original);
  });

  it('conserva null legacy al editar cantidad y permite asignarle un día', async () => {
    stored.fechaAsignacion = null as unknown as Date;
    component.obtenerProductosAlumno(10);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(input().value).toBe('');
    expect(component.hasPendingChanges()).toBeFalse();
    component.productosAlumno[0].cantidad = 2;
    component.onProductoChange(component.productosAlumno[0]);
    component.confirmarCambiosPendientes();
    expect(endpoints.actualizarProductoAlumno.calls.mostRecent().args[1].fechaAsignacion).toBeNull();
    fixture.detectChanges();
    await fixture.whenStable();
    await changeDate('2026-02-15');
    component.confirmarCambiosPendientes();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(input().value).toBe('2026-02-15');
  });
});

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
