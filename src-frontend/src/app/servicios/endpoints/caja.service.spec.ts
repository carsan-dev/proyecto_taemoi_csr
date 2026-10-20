import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CajaService, periodoDeMes } from './caja.service';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

describe('CajaService', () => {
  let servicio: CajaService;
  let http: HttpTestingController;
  const api = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(CajaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sedes: las de la tesorería por sede, sin la fila sin sede y por nombre', () => {
    let sedes: unknown;
    servicio.sedesGestionables().subscribe((s) => (sedes = s));
    const peticion = http.expectOne((r) => r.url === `${api}/tesoreria/resumen`);
    expect(peticion.request.withCredentials).toBeTrue();
    peticion.flush({
      porSede: [
        { sedeId: 3, nombre: 'Sede Norte' },
        { sedeId: null, nombre: null },
        { sedeId: 1, nombre: 'Centro' },
      ],
    });
    expect(sedes).toEqual([{ id: 1, nombre: 'Centro' }, { id: 3, nombre: 'Sede Norte' }]);
  });

  it('resumen: período desde/hasta y números normalizados', () => {
    let resumen: any;
    servicio.resumen(5, { desde: '2026-10-01', hasta: '2026-10-31' }).subscribe((r) => (resumen = r));
    const peticion = http.expectOne((r) => r.url === `${api}/caja/sedes/5/resumen`);
    expect(peticion.request.params.get('desde')).toBe('2026-10-01');
    expect(peticion.request.params.get('hasta')).toBe('2026-10-31');
    peticion.flush({
      sedeId: 5, desde: '2026-10-01', hasta: '2026-10-31',
      pendienteDeEntregar: '120.50', recibidoEnPeriodo: 300, diferenciasEnPeriodo: -2.5, saldoCaja: null,
    });
    expect(resumen.pendienteDeEntregar).toBe(120.5);
    expect(resumen.recibidoEnPeriodo).toBe(300);
    expect(resumen.diferenciasEnPeriodo).toBe(-2.5);
    expect(resumen.saldoCaja).toBe(0);
  });

  it('pendiente de entrega: profesores con sus cobros e importes numéricos', () => {
    let filas: any;
    servicio.pendienteEntrega(5).subscribe((r) => (filas = r));
    http.expectOne(`${api}/caja/sedes/5/pendiente-entrega`).flush([
      {
        usuarioId: 9, nombre: 'Profe Uno', numeroCobros: 2, importe: '45.00', cobroMasAntiguo: 1791614609000,
        cobros: [{ cobroId: 1, cargoId: 11, alumnoId: 21, alumnoNombre: 'Ana', alumnoApellidos: 'Ruiz', concepto: 'MENSUALIDAD', importe: '30.00', fechaCobro: 1791614609000, sedeId: 5 }],
      },
    ]);
    expect(filas[0].importe).toBe(45);
    expect(filas[0].cobros[0].importe).toBe(30);
    expect(filas[0].cobros[0].alumnoNombre).toBe('Ana');
    // java.util.Date llega en milisegundos: se pasa a ISO.
    expect(filas[0].cobroMasAntiguo).toBe(new Date(1791614609000).toISOString());
    expect(filas[0].cobros[0].fechaCobro).toBe(new Date(1791614609000).toISOString());
  });

  it('entregas: página desde 1 y tamaño 25', () => {
    let pagina: any;
    servicio.entregas(5, 2).subscribe((r) => (pagina = r));
    const peticion = http.expectOne((r) => r.url === `${api}/caja/sedes/5/entregas`);
    expect(peticion.request.params.get('page')).toBe('2');
    expect(peticion.request.params.get('size')).toBe('25');
    peticion.flush({
      content: [
        { id: 1, importeDeclarado: '10.00', importeContado: '9.50', diferencia: '-0.50', estado: 'ACTIVA', cobroIds: [1], autoentrega: false, fecha: [2026, 10, 19, 10, 43, 29, 732386000] },
        { id: 2, importeDeclarado: '25.00', importeContado: '0.00', diferencia: '-25.00', estado: 'ACTIVA', cobroIds: [3], autoentrega: true, fecha: [2026, 10, 19, 11, 0], movimientosDeCobro: [{ id: 8, tipo: 'DEVOLUCION', importe: '-25.00', cobroId: 3, estado: 'ACTIVO' }] },
      ],
      totalPages: 3, totalElements: 51, number: 1, size: 25,
    });
    // 6c-9: los movimientos de cobro de la entrega, con su importe a número; sin ellos, lista vacía.
    expect(pagina.content[0].movimientosDeCobro).toEqual([]);
    expect(pagina.content[1].movimientosDeCobro).toEqual([{ id: 8, tipo: 'DEVOLUCION', importe: -25, cobroId: 3, estado: 'ACTIVO' }]);
    expect(pagina.content[0].diferencia).toBe(-0.5);
    expect(pagina.content[0].importeContado).toBe(9.5);
    expect(pagina.totalPages).toBe(3);
    // LocalDateTime llega como [año, mes, día, hora, min, s, nanos] en hora local.
    expect(pagina.content[0].fecha).toBe(new Date(2026, 9, 19, 10, 43, 29, 732).toISOString());
  });

  it('registrar entrega: POST con el cuerpo de la API y errores gestionados en la pantalla', () => {
    servicio.registrarEntrega(5, { entregadoPorUsuarioId: 9, cobroIds: [1, 2], importeContado: 44, nota: 'faltan 1 €' }).subscribe();
    const peticion = http.expectOne(`${api}/caja/sedes/5/entregas`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ entregadoPorUsuarioId: 9, cobroIds: [1, 2], importeContado: 44, nota: 'faltan 1 €' });
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush({ id: 1, importeDeclarado: 45, importeContado: 44, diferencia: -1, estado: 'ACTIVA', cobroIds: [1, 2] });
  });

  it('movimientos, crear y anular usan sus rutas', () => {
    let leidos: any;
    servicio.movimientos(5, { desde: '2026-10-01', hasta: '2026-10-31' }).subscribe((m) => (leidos = m));
    const lista = http.expectOne((r) => r.url === `${api}/caja/sedes/5/movimientos`);
    expect(lista.request.params.get('desde')).toBe('2026-10-01');
    lista.flush([{ id: 4, tipo: 'DEVOLUCION', importe: '-15.00', estado: 'ACTIVO', fecha: [2026, 10, 2, 9, 0] }]);
    expect(leidos[0].importe).toBe(-15);
    expect(leidos[0].fecha).toBe(new Date(2026, 9, 2, 9, 0).toISOString());

    servicio.crearMovimiento(5, { tipo: 'DEVOLUCION', importe: -15, alumnoId: 21, nota: 'devuelto' }).subscribe();
    const crear = http.expectOne(`${api}/caja/sedes/5/movimientos`);
    expect(crear.request.method).toBe('POST');
    expect(crear.request.body).toEqual({ tipo: 'DEVOLUCION', importe: -15, alumnoId: 21, nota: 'devuelto' });
    crear.flush({ id: 5, tipo: 'DEVOLUCION', importe: -15, estado: 'ACTIVO' });

    servicio.anularMovimiento(5, 'error').subscribe();
    const anularMov = http.expectOne(`${api}/caja/movimientos/5/anular`);
    expect(anularMov.request.body).toEqual({ motivo: 'error' });
    anularMov.flush({ id: 5, importe: -15, estado: 'ANULADO' });

    servicio.anularEntrega(7, 'mal contado').subscribe();
    const anularEnt = http.expectOne(`${api}/caja/entregas/7/anular`);
    expect(anularEnt.request.body).toEqual({ motivo: 'mal contado' });
    anularEnt.flush({ id: 7, estado: 'ANULADA', importeDeclarado: 1, importeContado: 1, diferencia: 0 });
  });

  it('periodoDeMes: primer y último día del mes', () => {
    expect(periodoDeMes(2026, 2)).toEqual({ desde: '2026-02-01', hasta: '2026-02-28' });
    expect(periodoDeMes(2024, 2)).toEqual({ desde: '2024-02-01', hasta: '2024-02-29' });
    expect(periodoDeMes(2026, 12)).toEqual({ desde: '2026-12-01', hasta: '2026-12-31' });
  });
});
