import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { ListadoProductosComponent } from './listado-productos.component';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { Toast } from '../../../utils/toast.util';

describe('ListadoProductosComponent', () => {
  let component: ListadoProductosComponent;
  let fixture: ComponentFixture<ListadoProductosComponent>;
  let service: jasmine.SpyObj<EndpointsService>;
  let toastSpy: jasmine.Spy;

  const pagina = () => ({
    content: [
      { id: 1, concepto: 'Cuota', precio: 10, vendibleProfesor: false },
      { id: 2, concepto: 'Agua', precio: 1.5, vendibleProfesor: true },
      { id: 3, concepto: 'Casco', precio: 30, vendibleProfesor: false },
    ],
    totalPages: 1,
    totalElements: 3,
  });

  beforeEach(async () => {
    sessionStorage.clear();
    service = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'obtenerTodosLosProductosPaginado',
      'cambiarVendibleProfesor',
      'cambiarVendibleProfesorEnBloque',
      'eliminarProducto',
    ]);
    service.obtenerTodosLosProductosPaginado.and.callFake(() => of(pagina()));
    toastSpy = spyOn(Toast, 'fire');

    await TestBed.configureTestingModule({
      imports: [ListadoProductosComponent],
      providers: [provideRouter([]), { provide: EndpointsService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(ListadoProductosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  const el = (): HTMLElement => fixture.nativeElement;
  const switches = () =>
    Array.from(el().querySelectorAll<HTMLInputElement>('input[role="switch"]'));
  const botonesBloque = () =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('.action-buttons button')).filter((b) =>
      /Mi caja/.test(b.getAttribute('aria-label') ?? '')
    );
  const ultimoToast = () => toastSpy.calls.mostRecent().args[0] as any;

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('pinta un interruptor por producto con su estado y etiqueta accesible', () => {
    const inputs = switches();
    expect(inputs.length).toBe(3);
    expect(inputs.map((i) => i.checked)).toEqual([false, true, false]);
    expect(inputs[0].getAttribute('aria-label')).toBe('Lo puede vender el profesor: Cuota');
    expect(inputs[0].title).toBe('Lo puede vender el profesor: Cuota');
  });

  it('interruptor: guarda, actualiza el producto y avisa', () => {
    service.cambiarVendibleProfesor.and.returnValue(
      of({ id: 1, concepto: 'Cuota', vendibleProfesor: true } as any)
    );
    const input = switches()[0];
    input.click();
    fixture.detectChanges();

    expect(service.cambiarVendibleProfesor).toHaveBeenCalledWith(1, true);
    expect(component.productos[0].vendibleProfesor).toBeTrue();
    expect(input.checked).toBeTrue();
    expect(ultimoToast().icon).toBe('success');
    expect(ultimoToast().title).toBe('«Cuota»: ya se puede vender desde Mi caja');
  });

  it('interruptor: al quitar avisa con el texto de quitar', () => {
    service.cambiarVendibleProfesor.and.returnValue(
      of({ id: 2, concepto: 'Agua', vendibleProfesor: false } as any)
    );
    switches()[1].click();
    expect(service.cambiarVendibleProfesor).toHaveBeenCalledWith(2, false);
    expect(ultimoToast().title).toBe('«Agua»: ya no sale en Mi caja');
  });

  it('interruptor: en error vuelve al valor anterior y muestra el mensaje del servidor', () => {
    service.cambiarVendibleProfesor.and.returnValue(
      throwError(() => ({ error: { mensaje: 'Producto no encontrado' } }))
    );
    const input = switches()[0];
    input.click();
    fixture.detectChanges();

    expect(component.productos[0].vendibleProfesor).toBeFalse();
    expect(input.checked).toBeFalse();
    expect(ultimoToast().icon).toBe('error');
    expect(ultimoToast().title).toBe('Producto no encontrado');
  });

  it('interruptor: en error sin mensaje usa el texto por defecto', () => {
    service.cambiarVendibleProfesor.and.returnValue(throwError(() => ({ status: 500 })));
    switches()[0].click();
    expect(ultimoToast().title).toBe('No se pudo cambiar «Mi caja» de «Cuota»');
  });

  it('interruptor: queda deshabilitado mientras guarda', () => {
    const respuesta = new Subject<any>();
    service.cambiarVendibleProfesor.and.returnValue(respuesta);
    switches()[0].click();
    fixture.detectChanges();

    expect(switches()[0].disabled).toBeTrue();
    expect(switches()[1].disabled).toBeFalse();

    respuesta.next({ id: 1, concepto: 'Cuota', vendibleProfesor: true });
    respuesta.complete();
    fixture.detectChanges();
    expect(switches()[0].disabled).toBeFalse();
  });

  it('bloque: botones deshabilitados sin selección', () => {
    const botones = botonesBloque();
    expect(botones.length).toBe(2);
    expect(botones.every((b) => b.disabled)).toBeTrue();
  });

  it('bloque: éxito marca los productos, vacía la selección y avisa', () => {
    service.cambiarVendibleProfesorEnBloque.and.returnValue(of({ actualizados: 2 }));
    component.productos[0].selected = true;
    component.productos[2].selected = true;
    fixture.detectChanges();

    const [dar] = botonesBloque();
    expect(dar.disabled).toBeFalse();
    expect(dar.getAttribute('aria-label')).toBe('Dar a Mi caja (2)');
    dar.click();
    fixture.detectChanges();

    expect(service.cambiarVendibleProfesorEnBloque).toHaveBeenCalledWith([1, 3], true);
    expect(component.productos.map((p) => p.vendibleProfesor)).toEqual([true, true, true]);
    expect(component.productos.some((p) => p.selected)).toBeFalse();
    expect(ultimoToast().title).toBe('2 productos ahora en Mi caja');
    expect(botonesBloque().every((b) => b.disabled)).toBeTrue();
  });

  it('bloque: quitar usa singular con un producto', () => {
    service.cambiarVendibleProfesorEnBloque.and.returnValue(of({ actualizados: 1 }));
    component.productos[1].selected = true;
    fixture.detectChanges();

    botonesBloque()[1].click();

    expect(service.cambiarVendibleProfesorEnBloque).toHaveBeenCalledWith([2], false);
    expect(component.productos[1].vendibleProfesor).toBeFalse();
    expect(ultimoToast().title).toBe('1 producto quitado de Mi caja');
  });

  it('bloque: error no cambia la lista ni la selección y muestra el mensaje', () => {
    service.cambiarVendibleProfesorEnBloque.and.returnValue(
      throwError(() => ({ error: { mensaje: 'Producto 3 inexistente' } }))
    );
    component.productos[0].selected = true;
    fixture.detectChanges();

    botonesBloque()[0].click();
    fixture.detectChanges();

    expect(component.productos.map((p) => p.vendibleProfesor)).toEqual([false, true, false]);
    expect(component.productos[0].selected).toBeTrue();
    expect(ultimoToast().icon).toBe('error');
    expect(ultimoToast().title).toBe('Producto 3 inexistente');
    expect(botonesBloque()[0].disabled).toBeFalse();
  });

  it('bloque: error sin mensaje usa el texto por defecto', () => {
    service.cambiarVendibleProfesorEnBloque.and.returnValue(throwError(() => ({ status: 500 })));
    component.productos[0].selected = true;
    component.cambiarMiCajaSeleccionados(true);
    expect(ultimoToast().title).toBe('No se pudo cambiar «Mi caja» de los productos seleccionados');
  });

  it('bloque: los dos botones quedan deshabilitados mientras va la petición', () => {
    const respuesta = new Subject<any>();
    service.cambiarVendibleProfesorEnBloque.and.returnValue(respuesta);
    component.productos[0].selected = true;
    fixture.detectChanges();

    botonesBloque()[0].click();
    fixture.detectChanges();
    expect(botonesBloque().every((b) => b.disabled)).toBeTrue();

    respuesta.error({});
    fixture.detectChanges();
    expect(botonesBloque().every((b) => b.disabled)).toBeFalse();
  });

  it('estado vacío ocupa las 6 columnas', () => {
    service.obtenerTodosLosProductosPaginado.and.returnValue(
      of({ content: [], totalPages: 0, totalElements: 0 })
    );
    component.obtenerProductos();
    fixture.detectChanges();
    expect(el().querySelector('td[colspan="6"]')).not.toBeNull();
  });
});
