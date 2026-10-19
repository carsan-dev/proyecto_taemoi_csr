import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { CrearProductoComponent } from './crear-producto.component';
import { EditarProductoComponent } from '../editar-producto/editar-producto.component';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';

describe('Ficha de producto: «Lo puede vender el profesor»', () => {
  let endpoints: jasmine.SpyObj<EndpointsService>;

  beforeEach(() => {
    endpoints = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'crearProducto',
      'actualizarProducto',
      'obtenerProductoPorId',
    ]);
    endpoints.crearProducto.and.returnValue(of({} as any));
    endpoints.actualizarProducto.and.returnValue(of({}));
    endpoints.obtenerProductoPorId.and.returnValue(
      of({ id: 9, concepto: 'Camiseta', precio: 12.5, vendibleProfesor: true } as any)
    );
    TestBed.configureTestingModule({
      providers: [
        { provide: EndpointsService, useValue: endpoints },
        { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) },
        { provide: Location, useValue: { back: () => undefined } },
        { provide: ActivatedRoute, useValue: { snapshot: { params: { id: '9' } } } },
      ],
    });
  });

  it('crear: la casilla sale desmarcada, con su ayuda, y se envía en el POST', () => {
    const fixture = TestBed.createComponent(CrearProductoComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const casilla = el.querySelector<HTMLInputElement>('#vendibleProfesor')!;
    expect(casilla.checked).toBeFalse();
    expect(el.querySelector('label[for="vendibleProfesor"]')?.textContent?.trim()).toBe('Lo puede vender el profesor');
    expect(el.querySelector('#ayudaVendibleProfesor')?.textContent).toContain('Aparecerá en Mi caja');

    casilla.click();
    fixture.componentInstance.productoForm.patchValue({ concepto: 'Camiseta', precio: 12.5 });
    fixture.componentInstance.onSubmit();
    expect(endpoints.crearProducto).toHaveBeenCalledWith(
      jasmine.objectContaining({ concepto: 'Camiseta', precio: 12.5, vendibleProfesor: true })
    );
  });

  it('editar: carga la casilla del producto y la envía en el PUT', () => {
    const fixture = TestBed.createComponent(EditarProductoComponent);
    fixture.detectChanges();
    const casilla = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#vendibleProfesor')!;
    expect(casilla.checked).toBeTrue();

    casilla.click();
    fixture.componentInstance.onSubmit();
    expect(endpoints.actualizarProducto).toHaveBeenCalledWith(
      9,
      jasmine.objectContaining({ concepto: 'Camiseta', vendibleProfesor: false }) as any
    );
  });

  it('editar: un producto sin el campo queda desmarcado', () => {
    endpoints.obtenerProductoPorId.and.returnValue(of({ id: 9, concepto: 'Cuota', precio: 30 } as any));
    const fixture = TestBed.createComponent(EditarProductoComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.productoForm.value.vendibleProfesor).toBeFalse();
  });
});
