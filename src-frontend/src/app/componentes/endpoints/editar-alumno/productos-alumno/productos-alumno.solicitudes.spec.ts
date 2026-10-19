import { Location } from '@angular/common';
import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import type { ProductoAlumnoDTO } from '../../../../interfaces/producto-alumno-dto';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { SolicitudesService } from '../../../../servicios/endpoints/solicitudes.service';
import { Toast } from '../../../../utils/toast.util';
import { ProductosAlumnoComponent } from './productos-alumno.component';

/** Pieza 6c-7 (decisiones 190 y 191): en la ficha, la gestión desmarca un cargo pagado y se abre «Pedir al propietario». */
describe('ProductosAlumnoComponent: «Pedir al propietario»', () => {
  let endpoints: jasmine.SpyObj<any>;
  let solicitudes: jasmine.SpyObj<SolicitudesService>;
  let swal: jasmine.Spy;
  let guardado: ProductoAlumnoDTO;

  function crear(propietario: boolean): ProductosAlumnoComponent {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: SolicitudesService, useValue: solicitudes }] });
    const auth = { rolesEstanCargados: () => true, tieneRolAdmin: () => propietario };
    const component = new ProductosAlumnoComponent({} as any, endpoints, {} as any, auth as any, TestBed.inject(Injector));
    component.resolverPermisos();
    component.alumnoId = 10;
    component.obtenerProductosAlumno(10);
    return component;
  }

  beforeEach(() => {
    guardado = {
      id: 1, productoId: 20, alumnoId: 10, concepto: 'DOBOK', fechaAsignacion: new Date('2026-01-20T10:00:00Z'),
      fechaPago: new Date('2026-01-25T10:00:00Z'), cantidad: 2, precio: 25, pagado: true, notas: '',
    };
    endpoints = jasmine.createSpyObj('EndpointsService', ['obtenerProductosDelAlumno', 'actualizarProductoAlumno']);
    endpoints.obtenerProductosDelAlumno.and.callFake(() => of([{ ...guardado }]));
    solicitudes = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', ['crear']);
    solicitudes.crear.and.returnValue(of({ id: 1 } as any));
    spyOn(Toast, 'fire').and.returnValue(Promise.resolve({} as any));
    swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true, value: 'pagó otro hermano' } as any));
  });

  it('la gestión desmarca un cargo pagado: el checkbox vuelve a marcado y se abre el diálogo', async () => {
    const component = crear(false);
    const producto = component.productosAlumno[0];

    producto.pagado = false;
    const pedido = component.onPagadoChange(producto);

    expect(producto.pagado).toBeTrue();
    expect(component.hasPendingChanges()).toBeFalse();
    await pedido;
    const config = swal.calls.mostRecent().args[0] as any;
    expect(config.title).toBe('Pedir al propietario que revierta este cobro');
    expect(config.html).toContain('DOBOK');
    expect(config.html).toContain('50,00');
    expect(solicitudes.crear).toHaveBeenCalledWith({
      tipo: 'REVERTIR_COBRO', productoAlumnoId: 1, motivo: 'pagó otro hermano',
    });
  });

  it('en pantalla, el checkbox desmarcado por la gestión vuelve a verse marcado', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ProductosAlumnoComponent],
      providers: [
        { provide: ActivatedRoute, useValue: {} },
        { provide: EndpointsService, useValue: endpoints },
        { provide: Location, useValue: {} },
        { provide: AuthenticationService, useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => false } },
        { provide: SolicitudesService, useValue: solicitudes },
      ],
    }).compileComponents();
    spyOn(ProductosAlumnoComponent.prototype, 'ngOnInit').and.callFake(function (this: ProductosAlumnoComponent) {
      this.resolverPermisos();
    });
    const fixture = TestBed.createComponent(ProductosAlumnoComponent);
    fixture.componentInstance.alumnoId = 10;
    fixture.componentInstance.obtenerProductosAlumno(10);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const checkbox = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type="checkbox"].form-check-input')!;
    expect(checkbox.checked).toBeTrue();
    checkbox.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(checkbox.checked).toBeTrue();
    expect(fixture.componentInstance.productosAlumno[0].pagado).toBeTrue();
    expect(swal).toHaveBeenCalled();
  });

  it('el propietario desmarca como hoy (cambio pendiente, sin diálogo)', () => {
    const component = crear(true);
    const producto = component.productosAlumno[0];

    producto.pagado = false;
    component.onPagadoChange(producto);

    expect(producto.pagado).toBeFalse();
    expect(component.hasPendingChanges()).toBeTrue();
    expect(swal).not.toHaveBeenCalled();
  });

  it('la gestión marca o desmarca un cargo que no estaba pagado sin diálogo', () => {
    guardado = { ...guardado, pagado: false, fechaPago: null };
    const component = crear(false);
    const producto = component.productosAlumno[0];

    producto.pagado = true;
    component.onPagadoChange(producto);
    expect(component.hasPendingChanges()).toBeTrue();
    producto.pagado = false;
    component.onPagadoChange(producto);
    expect(swal).not.toHaveBeenCalled();
  });
});
