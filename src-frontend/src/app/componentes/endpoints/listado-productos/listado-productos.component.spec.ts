import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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

  describe('filtros (pieza 6b-3d)', () => {
    const selectTipo = () => el().querySelector<HTMLSelectElement>('#filtro-tipo')!;
    const selectMiCaja = () => el().querySelector<HTMLSelectElement>('#filtro-mi-caja')!;
    const inputConcepto = () => el().querySelector<HTMLInputElement>('#filtro-concepto')!;
    const botonLimpiar = () =>
      Array.from(el().querySelectorAll<HTMLButtonElement>('.filter-actions button')).find((b) =>
        /Limpiar/.test(b.textContent ?? '')
      )!;
    const chips = () => Array.from(el().querySelectorAll<HTMLElement>('.filtro-chip'));
    const textoChips = () => chips().map((c) => c.querySelector('span')!.textContent!.trim());
    const llamada = () => service.obtenerTodosLosProductosPaginado.calls.mostRecent().args;
    const elegir = (select: HTMLSelectElement, valor: string) => {
      select.value = valor;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };
    const total = () => el().querySelector('.filtros-total')?.textContent?.trim();

    it('cada campo tiene su label visible y los desplegables ofrecen Todos y las opciones', () => {
      expect(el().querySelector('label[for="filtro-concepto"]')?.textContent).toContain('Concepto');
      expect(el().querySelector('label[for="filtro-tipo"]')?.textContent).toContain('Tipo');
      expect(el().querySelector('label[for="filtro-mi-caja"]')?.textContent).toContain('Mi caja');
      expect(Array.from(selectTipo().options).map((o) => o.textContent!.trim())).toEqual([
        'Todos', 'Mensualidad', 'Reserva de plaza', 'Licencia', 'Competidor', 'Examen', 'Recompensa', 'Otro',
      ]);
      expect(Array.from(selectMiCaja().options).map((o) => o.textContent!.trim())).toEqual(['Todos', 'Sí', 'No']);
    });

    it('sin filtros activos solo envía el concepto vacío, sin tipo ni Mi caja', () => {
      const args = llamada();
      expect(args[0]).toBe('');
      expect(args[5]).toBeUndefined();
      expect(args[6]).toBeNull();
      expect(chips().length).toBe(0);
      expect(botonLimpiar().disabled).toBeTrue();
    });

    it('cambiar «Tipo» recarga desde la página 1 con el tipo elegido', () => {
      component.paginaActual = 3;
      elegir(selectTipo(), 'OTRO');
      const args = llamada();
      expect(args[1]).toBe(1);
      expect(args[5]).toBe('OTRO');
      expect(args[6]).toBeNull();
      expect(textoChips()).toEqual(['Tipo: Otro']);
    });

    it('cambiar «Mi caja» envía true o false y recarga desde la página 1', () => {
      component.paginaActual = 2;
      elegir(selectMiCaja(), 'true');
      expect(llamada()[1]).toBe(1);
      expect(llamada()[6]).toBeTrue();
      expect(llamada()[5]).toBeUndefined();
      expect(textoChips()).toEqual(['Mi caja: Sí']);

      elegir(selectMiCaja(), 'false');
      expect(llamada()[6]).toBeFalse();
      expect(textoChips()).toEqual(['Mi caja: No']);

      elegir(selectMiCaja(), '');
      expect(llamada()[6]).toBeNull();
      expect(chips().length).toBe(0);
    });

    it('el buscador mantiene su debounce y recarga desde la página 1', fakeAsync(() => {
      component.paginaActual = 4;
      const antes = service.obtenerTodosLosProductosPaginado.calls.count();
      inputConcepto().value = 'dobok';
      inputConcepto().dispatchEvent(new Event('input'));
      tick(499);
      expect(service.obtenerTodosLosProductosPaginado.calls.count()).toBe(antes);
      tick(1);
      expect(service.obtenerTodosLosProductosPaginado.calls.count()).toBe(antes + 1);
      expect(llamada()[0]).toBe('dobok');
      expect(llamada()[1]).toBe(1);
      fixture.detectChanges();
      expect(textoChips()).toEqual(['"dobok"']);
    }));

    it('teclear lo mismo que ya se aplicó no repite la petición, pero sí tras Limpiar', fakeAsync(() => {
      inputConcepto().value = 'dobok';
      inputConcepto().dispatchEvent(new Event('input'));
      tick(500);
      const tras = service.obtenerTodosLosProductosPaginado.calls.count();
      inputConcepto().dispatchEvent(new Event('input'));
      tick(500);
      expect(service.obtenerTodosLosProductosPaginado.calls.count()).toBe(tras);

      component.limpiarFiltros();
      expect(service.obtenerTodosLosProductosPaginado.calls.count()).toBe(tras + 1);
      inputConcepto().value = 'dobok';
      inputConcepto().dispatchEvent(new Event('input'));
      tick(500);
      expect(service.obtenerTodosLosProductosPaginado.calls.count()).toBe(tras + 2);
    }));

    it('combina los tres filtros en una sola petición', () => {
      component.conceptoFiltro = 'dobok';
      elegir(selectTipo(), 'EXAMEN');
      elegir(selectMiCaja(), 'false');
      const args = llamada();
      expect(args[0]).toBe('dobok');
      expect(args[5]).toBe('EXAMEN');
      expect(args[6]).toBeFalse();
      fixture.detectChanges();
      expect(textoChips()).toEqual(['"dobok"', 'Tipo: Examen', 'Mi caja: No']);
    });

    it('«Limpiar» resetea los tres filtros, vuelve a la página 1 y recarga', async () => {
      component.conceptoFiltro = 'dobok';
      component.paginaActual = 2;
      elegir(selectTipo(), 'LICENCIA');
      elegir(selectMiCaja(), 'true');
      fixture.detectChanges();
      expect(botonLimpiar().disabled).toBeFalse();

      botonLimpiar().click();
      fixture.detectChanges();
      await fixture.whenStable();

      const args = llamada();
      expect(args[0]).toBe('');
      expect(args[1]).toBe(1);
      expect(args[5]).toBeUndefined();
      expect(args[6]).toBeNull();
      expect(component.conceptoFiltro).toBe('');
      expect(selectTipo().value).toBe('');
      expect(selectMiCaja().value).toBe('');
      expect(chips().length).toBe(0);
    });

    it('la × de un chip quita solo ese filtro', () => {
      component.conceptoFiltro = 'dobok';
      elegir(selectTipo(), 'OTRO');
      elegir(selectMiCaja(), 'true');
      fixture.detectChanges();

      const quitar = (texto: string) =>
        chips()
          .find((c) => c.textContent!.includes(texto))!
          .querySelector<HTMLButtonElement>('button')!;
      expect(quitar('Tipo').getAttribute('aria-label')).toBe('Quitar filtro Tipo: Otro');

      quitar('Tipo').click();
      fixture.detectChanges();
      expect(llamada()[5]).toBeUndefined();
      expect(llamada()[6]).toBeTrue();
      expect(llamada()[0]).toBe('dobok');
      expect(textoChips()).toEqual(['"dobok"', 'Mi caja: Sí']);

      quitar('Mi caja').click();
      fixture.detectChanges();
      expect(llamada()[6]).toBeNull();
      expect(textoChips()).toEqual(['"dobok"']);

      quitar('dobok').click();
      fixture.detectChanges();
      expect(llamada()[0]).toBe('');
      expect(inputConcepto().value).toBe('');
      expect(chips().length).toBe(0);
    });

    it('muestra el total real: plural, singular y cero', () => {
      expect(total()).toBe('3 productos');

      service.obtenerTodosLosProductosPaginado.and.returnValue(
        of({ content: [{ id: 1, concepto: 'Cuota', precio: 10 }], totalPages: 1, totalElements: 1 })
      );
      component.obtenerProductos();
      fixture.detectChanges();
      expect(total()).toBe('1 producto');

      service.obtenerTodosLosProductosPaginado.and.returnValue(
        of({ content: [], totalPages: 0, totalElements: 0 })
      );
      component.obtenerProductos();
      fixture.detectChanges();
      expect(total()).toBe('0 productos');
    });

    it('usa totalElements de la página, no los productos de la página', () => {
      service.obtenerTodosLosProductosPaginado.and.returnValue(
        of({ content: pagina().content, totalPages: 5, totalElements: 48 })
      );
      component.obtenerProductos();
      fixture.detectChanges();
      expect(total()).toBe('48 productos');
    });

    it('con filtros y sin resultados, el vacío lo dice', () => {
      elegir(selectTipo(), 'OTRO');
      service.obtenerTodosLosProductosPaginado.and.returnValue(
        of({ content: [], totalPages: 0, totalElements: 0 })
      );
      component.obtenerProductos();
      fixture.detectChanges();
      expect(el().querySelector('td[colspan="6"]')?.textContent).toContain('Ningún producto coincide');
    });

    it('guarda los filtros en sessionStorage junto al estado de siempre', () => {
      component.conceptoFiltro = 'dobok';
      elegir(selectTipo(), 'RECOMPENSA');
      elegir(selectMiCaja(), 'false');
      const estado = JSON.parse(sessionStorage.getItem('listadoProductosEstado')!);
      expect(estado.tipoFiltro).toBe('RECOMPENSA');
      expect(estado.miCajaFiltro).toBe('false');
      expect(estado.conceptoFiltro).toBe('dobok');
      expect(estado.paginaActual).toBe(1);
    });

    it('restaura los filtros guardados y pide la lista con ellos', async () => {
      sessionStorage.setItem(
        'listadoProductosEstado',
        JSON.stringify({
          paginaActual: 2, tamanoPagina: 10, conceptoFiltro: 'dobok', tipoFiltro: 'LICENCIA',
          miCajaFiltro: 'true', orderBy: 'precio', order: 'desc',
        })
      );
      service.obtenerTodosLosProductosPaginado.calls.reset();
      const otro = TestBed.createComponent(ListadoProductosComponent);
      otro.detectChanges();

      expect(service.obtenerTodosLosProductosPaginado.calls.first().args).toEqual([
        'dobok', 2, 10, 'precio', 'desc', 'LICENCIA', true,
      ]);
      await otro.whenStable();
      const nativo: HTMLElement = otro.nativeElement;
      expect(nativo.querySelector<HTMLSelectElement>('#filtro-tipo')!.value).toBe('LICENCIA');
      expect(nativo.querySelector<HTMLSelectElement>('#filtro-mi-caja')!.value).toBe('true');
      expect(nativo.querySelector<HTMLInputElement>('#filtro-concepto')!.value).toBe('dobok');
    });

    it('un estado guardado con valores no válidos los ignora', () => {
      sessionStorage.setItem(
        'listadoProductosEstado',
        JSON.stringify({ paginaActual: 1, conceptoFiltro: '', tipoFiltro: 'ZAPATO', miCajaFiltro: 'quizas' })
      );
      service.obtenerTodosLosProductosPaginado.calls.reset();
      const otro = TestBed.createComponent(ListadoProductosComponent);
      otro.detectChanges();
      const args = service.obtenerTodosLosProductosPaginado.calls.first().args;
      expect(args[5]).toBeUndefined();
      expect(args[6]).toBeNull();
    });

    it('un estado guardado sin los filtros nuevos (versión anterior) se restaura como siempre', () => {
      sessionStorage.setItem(
        'listadoProductosEstado',
        JSON.stringify({ paginaActual: 2, tamanoPagina: 10, conceptoFiltro: 'cuota', orderBy: 'concepto', order: 'asc' })
      );
      service.obtenerTodosLosProductosPaginado.calls.reset();
      const otro = TestBed.createComponent(ListadoProductosComponent);
      otro.detectChanges();
      expect(service.obtenerTodosLosProductosPaginado.calls.first().args).toEqual([
        'cuota', 2, 10, 'concepto', 'asc', undefined, null,
      ]);
    });
  });
});
