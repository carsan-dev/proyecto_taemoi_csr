import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CajaProfesorService } from './caja-profesor.service';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

/** Pieza 6c-8: rutas del profesor de encargos (6c-5), devolución (6c-6) y pedir deshacer (6c-4). */
describe('CajaProfesorService (pedidos, 6c-8)', () => {
  let servicio: CajaProfesorService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/caja/profesor`;

  const solicitudApi = (extra: Record<string, unknown> = {}) => ({
    id: 9, tipo: 'ENCARGO', estado: 'PEDIDO', sedeId: 1, sedeNombre: 'Sede Norte', motivo: 'Talla 140',
    solicitadoEn: [2026, 10, 18, 17, 30], importe: '25.00', devolucionPendiente: false, propia: true,
    movimientoDevolucionId: null, devolucionEnSede: null, ...extra,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(CajaProfesorService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('productos encargables: GET con credenciales, errores locales y precio a número', () => {
    let productos: any;
    servicio.productosEncargables().subscribe((p) => (productos = p));
    const peticion = http.expectOne(`${api}/productos-encargables`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush([{ id: 4, concepto: 'Dobok', precio: '25.00' }]);
    expect(productos).toEqual([{ id: 4, concepto: 'Dobok', precio: 25 }]);
  });

  it('mis solicitudes: GET y normaliza fechas, importes y los campos de la devolución', () => {
    let lista: any;
    servicio.misSolicitudes().subscribe((l) => (lista = l));
    const peticion = http.expectOne(`${api}/solicitudes`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush([solicitudApi({ estado: 'RECHAZADO', devolucionPendiente: true, devolucionEnSede: false, estadoCobro: 'COBRADO' })]);
    expect(lista[0].estadoCobro).toBe('COBRADO');
    expect(lista[0].importe).toBe(25);
    expect(lista[0].solicitadoEn).toBe(new Date(2026, 9, 18, 17, 30).toISOString());
    expect(lista[0].devolucionPendiente).toBeTrue();
    expect(lista[0].devolucionEnSede).toBeFalse();
    expect(lista[0].movimientoDevolucionId).toBeNull();
  });

  it('alumnos: conserva las sedes de sus grupos (D6c-9)', () => {
    let alumnos: any;
    servicio.alumnos().subscribe((a) => (alumnos = a));
    http.expectOne(`${api}/alumnos`).flush([
      { alumnoId: 3, nombre: 'Alumna', apellidos: 'Uno', grupos: ['A', 'B'], sedes: [{ id: 1, nombre: 'Sede Norte' }, { id: 2, nombre: 'Sede Sur' }] },
    ]);
    expect(alumnos[0].sedes.map((s: any) => s.id)).toEqual([1, 2]);
  });

  it('pedir encargo: POST al alumno con producto y nota; la sede solo si se indica', () => {
    let creada: any;
    servicio.pedirEncargo(3, { productoId: 4, nota: 'Talla 140' }).subscribe((s) => (creada = s));
    const sinSede = http.expectOne(`${api}/alumnos/3/encargos`);
    expect(sinSede.request.method).toBe('POST');
    expect(sinSede.request.body).toEqual({ productoId: 4, nota: 'Talla 140' });
    sinSede.flush(solicitudApi(), { status: 201, statusText: 'Created' });
    expect(creada.id).toBe(9);
    expect(creada.importe).toBe(25);

    servicio.pedirEncargo(3, { productoId: 4, nota: 'Talla 140', sedeId: 2 }).subscribe();
    expect(http.expectOne(`${api}/alumnos/3/encargos`).request.body).toEqual({ productoId: 4, nota: 'Talla 140', sedeId: 2 });
  });

  it('retirar y marcar entregado: POST sin cuerpo a su ruta', () => {
    servicio.retirarSolicitud(9).subscribe();
    const retirar = http.expectOne(`${api}/solicitudes/9/retirar`);
    expect(retirar.request.method).toBe('POST');
    expect(retirar.request.body).toBeNull();
    retirar.flush(solicitudApi({ estado: 'RETIRADA' }));

    servicio.marcarEntregado(9).subscribe();
    const entregado = http.expectOne(`${api}/solicitudes/9/entregado`);
    expect(entregado.request.method).toBe('POST');
    entregado.flush(solicitudApi({ estado: 'ENTREGADO' }));
  });

  it('registrar devolución: POST con la nota (o null) y sin importe ni tipo', () => {
    servicio.registrarDevolucion(9, 'Se devuelve en mano').subscribe();
    const conNota = http.expectOne(`${api}/solicitudes/9/devolucion`);
    expect(conNota.request.method).toBe('POST');
    expect(conNota.request.body).toEqual({ nota: 'Se devuelve en mano' });
    conNota.flush(solicitudApi({ estado: 'RETIRADA', movimientoDevolucionId: 70 }));

    servicio.registrarDevolucion(9, null).subscribe();
    expect(http.expectOne(`${api}/solicitudes/9/devolucion`).request.body).toEqual({ nota: null });
  });

  it('entregados recientes: GET y normaliza importe, fechas y marcas', () => {
    let filas: any;
    servicio.cobrosEntregadosRecientes().subscribe((f) => (filas = f));
    const peticion = http.expectOne(`${api}/cobros-entregados-recientes`);
    expect(peticion.request.method).toBe('GET');
    peticion.flush([
      {
        cobroId: 50, cargoId: 7, alumnoId: 3, alumnoNombre: 'Alumna', alumnoApellidos: 'Uno', concepto: 'Camiseta',
        importe: '12.50', fechaCobro: 1791614609000, entregaId: 5, fechaEntrega: [2026, 10, 15, 19, 0], sedeId: 1,
        sedeNombre: 'Sede Norte', venta: 1, solicitudPendienteId: null, noSePuedePedir: null,
      },
    ]);
    expect(filas[0].importe).toBe(12.5);
    expect(filas[0].fechaCobro).toBe(new Date(1791614609000).toISOString());
    expect(filas[0].fechaEntrega).toBe(new Date(2026, 9, 15, 19, 0).toISOString());
    expect(filas[0].venta).toBeTrue();
  });

  it('pedir deshacer: POST con motivo y motivoDeshacer', () => {
    let creada: any;
    servicio.pedirDeshacer(50, { motivo: 'Lo cobré dos veces', motivoDeshacer: 'AUN_NO_PAGADO' }).subscribe((s) => (creada = s));
    const peticion = http.expectOne(`${api}/cobros/50/pedir-deshacer`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ motivo: 'Lo cobré dos veces', motivoDeshacer: 'AUN_NO_PAGADO' });
    peticion.flush(solicitudApi({ tipo: 'DESHACER_COBRO_ENTREGADO', estado: 'PENDIENTE' }), { status: 201, statusText: 'Created' });
    expect(creada.tipo).toBe('DESHACER_COBRO_ENTREGADO');
  });
});
