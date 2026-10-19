import { HttpErrorResponse } from '@angular/common/http';
import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import type { ProductoAlumnoDTO } from '../../../../interfaces/producto-alumno-dto';
import { Toast } from '../../../../utils/toast.util';
import { ProductosAlumnoComponent } from './productos-alumno.component';

/** Pieza 6c-1: la ficha muestra el mensaje del 409 del backend al cambiar el precio de un cargo cobrado (187). */
describe('ProductosAlumnoComponent: bloqueos del cargo cobrado', () => {
  const PRECIO_CON_COBRO_ACTIVO =
    'El cargo está cobrado: no se puede cambiar el precio ni la cantidad. Revierte el cobro antes.';

  let endpoints: jasmine.SpyObj<any>;
  let toast: jasmine.Spy;
  let guardado: ProductoAlumnoDTO;

  function crear(): ProductosAlumnoComponent {
    TestBed.resetTestingModule();
    const component = TestBed.runInInjectionContext(
      () => new ProductosAlumnoComponent({} as any, endpoints, {} as any, {} as any, TestBed.inject(Injector))
    );
    component.alumnoId = 10;
    component.obtenerProductosAlumno(10);
    return component;
  }

  beforeEach(() => {
    guardado = {
      id: 1, productoId: 20, alumnoId: 10, concepto: 'DOBOK', fechaAsignacion: new Date('2026-01-20T10:00:00Z'),
      fechaPago: new Date('2026-01-25T10:00:00Z'), cantidad: 1, precio: 25, pagado: true, notas: '',
    };
    endpoints = jasmine.createSpyObj('EndpointsService', ['obtenerProductosDelAlumno', 'actualizarProductoAlumno']);
    endpoints.obtenerProductosDelAlumno.and.callFake(() => of([{ ...guardado }]));
    toast = spyOn(Toast, 'fire').and.returnValue(Promise.resolve({} as any));
  });

  function guardarPrecioNuevo(component: ProductosAlumnoComponent): void {
    const producto = component.productosAlumno[0];
    producto.precio = 30;
    component.onProductoChange(producto);
    component.confirmarCambiosPendientes();
  }

  it('guardar un precio nuevo en un cargo cobrado: toast con el mensaje del backend', () => {
    endpoints.actualizarProductoAlumno.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: PRECIO_CON_COBRO_ACTIVO } }))
    );
    const component = crear();

    guardarPrecioNuevo(component);

    expect(endpoints.actualizarProductoAlumno).toHaveBeenCalled();
    expect(toast).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', title: PRECIO_CON_COBRO_ACTIVO }));
    // Al terminar se recargan los productos: el precio vuelve al guardado.
    expect(endpoints.obtenerProductosDelAlumno).toHaveBeenCalledTimes(2);
    expect(component.productosAlumno[0].precio).toBe(25);
  });

  it('sin mensaje del backend, el texto de siempre', () => {
    endpoints.actualizarProductoAlumno.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: null }))
    );
    const component = crear();

    guardarPrecioNuevo(component);

    expect(toast).toHaveBeenCalledWith(
      jasmine.objectContaining({ icon: 'error', title: 'No se pudo actualizar un producto' })
    );
  });
});
