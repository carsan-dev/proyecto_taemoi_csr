import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { Solicitud } from '../../interfaces/solicitud';
import { SolicitudesContadorService } from './solicitudes-contador.service';
import { SolicitudesService } from './solicitudes.service';

/** Pieza 6c-7: API de la bandeja y contador de la cabecera. */
describe('SolicitudesService', () => {
  let servicio: SolicitudesService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/solicitudes`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(SolicitudesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listar pide el segmento y normaliza fechas e importes', () => {
    let lista: Solicitud[] = [];
    servicio.listar('PENDIENTES').subscribe((l) => (lista = l));

    const peticion = http.expectOne((r) => r.url === api && r.params.get('segmento') === 'PENDIENTES');
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush([{ id: 7, tipo: 'REVERTIR_COBRO', estado: 'PENDIENTE', solicitadoEn: [2026, 10, 19, 9, 30], importe: '10.50' }]);

    expect(lista.length).toBe(1);
    expect(lista[0].importe).toBe(10.5);
    expect(lista[0].solicitadoEn).toBe(new Date(2026, 9, 19, 9, 30).toISOString());
    expect(lista[0].resueltoEn).toBeNull();
    expect(lista[0].propia).toBeFalse();
  });

  it('crear, retirar, aceptar y denegar llaman a sus rutas', () => {
    servicio.crear({ tipo: 'REVERTIR_COBRO', productoAlumnoId: 4, motivo: 'error' }).subscribe();
    const crear = http.expectOne(api);
    expect(crear.request.method).toBe('POST');
    expect(crear.request.body).toEqual({ tipo: 'REVERTIR_COBRO', productoAlumnoId: 4, motivo: 'error' });
    crear.flush({ id: 1 });

    servicio.retirar(1).subscribe();
    http.expectOne(`${api}/1/retirar`).flush({ id: 1 });

    servicio.aceptar(2).subscribe();
    const aceptar = http.expectOne(`${api}/2/aceptar`);
    expect(aceptar.request.method).toBe('POST');
    aceptar.flush({ id: 2 });

    servicio.denegar(3, 'no procede').subscribe();
    const denegar = http.expectOne(`${api}/3/denegar`);
    expect(denegar.request.body).toEqual({ respuesta: 'no procede' });
    denegar.flush({ id: 3 });
  });

  it('6c-9: encargado, recibido, rechazar y registrar devolución llaman a sus rutas', () => {
    let resultado: Solicitud | null = null;
    servicio.marcarEncargado(4).subscribe((s) => (resultado = s));
    const encargado = http.expectOne(`${api}/4/encargado`);
    expect(encargado.request.method).toBe('POST');
    expect(encargado.request.withCredentials).toBeTrue();
    expect(encargado.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    encargado.flush({ id: 4, estado: 'ENCARGADO', importe: '25.00', encargadoEn: [2026, 10, 20, 10, 0] });
    expect(resultado!.importe).toBe(25);
    expect(resultado!.encargadoEn).toBe(new Date(2026, 9, 20, 10, 0).toISOString());

    servicio.marcarRecibido(4).subscribe();
    const recibido = http.expectOne(`${api}/4/recibido`);
    expect(recibido.request.method).toBe('POST');
    recibido.flush({ id: 4, estado: 'RECIBIDO' });

    servicio.rechazar(5, 'No hay talla').subscribe();
    const rechazar = http.expectOne(`${api}/5/rechazar`);
    expect(rechazar.request.body).toEqual({ motivo: 'No hay talla' });
    rechazar.flush({ id: 5 });

    servicio.registrarDevolucion(6, null).subscribe();
    const devolucion = http.expectOne(`${api}/6/devolucion`);
    expect(devolucion.request.method).toBe('POST');
    expect(devolucion.request.body).toEqual({ nota: null });
    devolucion.flush({ id: 6 });

    servicio.registrarDevolucion(7, 'En mano a la madre').subscribe();
    expect(http.expectOne(`${api}/7/devolucion`).request.body).toEqual({ nota: 'En mano a la madre' });
  });
});

describe('SolicitudesContadorService', () => {
  let contador: SolicitudesContadorService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/solicitudes/contador`;

  beforeEach(() => {
    jasmine.clock().install();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    contador = TestBed.inject(SolicitudesContadorService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    jasmine.clock().uninstall();
    http.verify();
  });

  it('pide una vez por refresco y nunca solo (sin interval)', () => {
    expect(contador.total()).toBe(0);
    http.expectNone(url);

    contador.refrescar();
    const peticion = http.expectOne(url);
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush({ total: 3 });
    expect(contador.total()).toBe(3);

    jasmine.clock().tick(10 * 60 * 1000);
    http.expectNone(url);
  });

  it('un error deja el último valor; reiniciar lo pone a 0', () => {
    contador.refrescar();
    http.expectOne(url).flush({ total: 2 });

    contador.refrescar();
    http.expectOne(url).flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    expect(contador.total()).toBe(2);

    contador.refrescar();
    http.expectOne(url).flush({ total: 5 });
    expect(contador.total()).toBe(5);

    contador.reiniciar();
    expect(contador.total()).toBe(0);
  });
});
