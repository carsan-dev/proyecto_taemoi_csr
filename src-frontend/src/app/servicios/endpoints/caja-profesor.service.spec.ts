import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CajaProfesorService } from './caja-profesor.service';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

describe('CajaProfesorService', () => {
  let servicio: CajaProfesorService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/caja/profesor`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(CajaProfesorService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('cargos pendientes: GET con credenciales, errores locales e importes y fechas normalizados', () => {
    let cargos: any;
    servicio.cargosPendientes().subscribe((c) => (cargos = c));
    const peticion = http.expectOne(`${api}/cargos-pendientes`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush([
      {
        cargoId: 7, alumnoId: 3, alumnoNombre: 'Lucía', alumnoApellidos: 'Prado', concepto: 'Mensualidad',
        importe: '35.00', fechaAsignacion: 1791614609000, sedeId: 1, sedeNombre: 'Centro',
      },
    ]);
    expect(cargos[0].importe).toBe(35);
    expect(cargos[0].fechaAsignacion).toBe(new Date(1791614609000).toISOString());
  });

  it('cobrar un cargo: POST a /cargos/{id}/cobrar y devuelve el cobro', () => {
    let cobro: any;
    servicio.cobrarCargo(7).subscribe((c) => (cobro = c));
    const peticion = http.expectOne(`${api}/cargos/7/cobrar`);
    expect(peticion.request.method).toBe('POST');
    peticion.flush({ cobroId: 50, cargoId: 7, importe: '35.00', concepto: 'Mensualidad', venta: false }, { status: 201, statusText: 'Created' });
    expect(cobro.cobroId).toBe(50);
    expect(cobro.importe).toBe(35);
    expect(cobro.venta).toBeFalse();
  });

  it('deshacer: POST con el motivo en el cuerpo', () => {
    servicio.deshacerCobro(50, 'NO_HUBO_VENTA').subscribe();
    const peticion = http.expectOne(`${api}/cobros/50/deshacer`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ motivo: 'NO_HUBO_VENTA' });
    peticion.flush(null, { status: 204, statusText: 'No Content' });

    servicio.deshacerCobro(51, 'AUN_NO_PAGADO').subscribe();
    expect(http.expectOne(`${api}/cobros/51/deshacer`).request.body).toEqual({ motivo: 'AUN_NO_PAGADO' });
  });

  it('productos vendibles: GET y precio numérico', () => {
    let productos: any;
    servicio.productos().subscribe((p) => (productos = p));
    http.expectOne(`${api}/productos`).flush([{ id: 4, concepto: 'Camiseta', precio: '12.50' }]);
    expect(productos).toEqual([{ id: 4, concepto: 'Camiseta', precio: 12.5 }]);
  });

  it('vender: POST sin cuerpo a /alumnos/{a}/productos/{p}/cobrar', () => {
    let cobro: any;
    servicio.venderProducto(3, 4).subscribe((c) => (cobro = c));
    const peticion = http.expectOne(`${api}/alumnos/3/productos/4/cobrar`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toBeNull();
    peticion.flush({ cobroId: 60, importe: 12.5, venta: true });
    expect(cobro.venta).toBeTrue();
  });

  it('pendientes de entrega: número, total y cobros normalizados', () => {
    let mis: any;
    servicio.cobrosPendientesEntrega().subscribe((m) => (mis = m));
    http.expectOne(`${api}/cobros-pendientes-entrega`).flush({
      numeroCobros: 2,
      importe: '47.50',
      cobros: [
        { cobroId: 1, importe: '35', fechaCobro: 1791614609000, venta: false },
        { cobroId: 2, importe: 12.5, fechaCobro: null, venta: true },
      ],
    });
    expect(mis.numeroCobros).toBe(2);
    expect(mis.importe).toBe(47.5);
    expect(mis.cobros[0].importe).toBe(35);
    expect(mis.cobros[1].venta).toBeTrue();
  });

  it('alumnos de mis grupos: GET y grupos siempre como lista', () => {
    let alumnos: any;
    servicio.alumnos().subscribe((a) => (alumnos = a));
    http.expectOne(`${api}/alumnos`).flush([{ alumnoId: 3, nombre: 'Lucía', apellidos: 'Prado', grupos: null }]);
    expect(alumnos[0].grupos).toEqual([]);
  });
});
