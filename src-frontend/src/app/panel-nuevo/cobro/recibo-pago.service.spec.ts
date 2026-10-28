import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AvisosService } from '../avisos/avisos.service';
import { ReciboPagoService, TEXTO_RECIBO_ANULADO, TEXTO_RECIBO_FALLO } from './recibo-pago.service';

describe('ReciboPagoService', () => {
  let servicio: ReciboPagoService;
  let http: HttpTestingController;
  let error: jasmine.Spy;
  let guardar: jasmine.Spy;
  const url = (id: number) => `${environment.apiUrl}/productos-alumno/${id}/recibo`;

  beforeEach(() => {
    error = jasmine.createSpy('error');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AvisosService, useValue: { error } }],
    });
    servicio = TestBed.inject(ReciboPagoService);
    http = TestBed.inject(HttpTestingController);
    guardar = spyOn(servicio, 'guardar');
  });

  afterEach(() => http.verify());

  it('pide el recibo del cargo y lo descarga con el nombre de Content-Disposition', async () => {
    const promesa = servicio.descargar(42);
    const peticion = http.expectOne(url(42));
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBeTrue();
    peticion.flush(new Blob(['pdf']), {
      headers: { 'Content-Disposition': 'attachment; filename="recibo-R-2026-000007.pdf"' },
    });
    await promesa;
    expect(guardar).toHaveBeenCalledOnceWith(jasmine.any(Blob), 'recibo-R-2026-000007.pdf');
    expect(error).not.toHaveBeenCalled();
    expect(servicio.descargando(42)).toBeFalse();
  });

  it('sin cabecera, el nombre es recibo.pdf', async () => {
    const promesa = servicio.descargar(1);
    http.expectOne(url(1)).flush(new Blob(['pdf']));
    await promesa;
    expect(guardar).toHaveBeenCalledOnceWith(jasmine.any(Blob), 'recibo.pdf');
  });

  it('evita el doble clic: con una descarga en curso no pide otra', async () => {
    const primera = servicio.descargar(5);
    expect(servicio.descargando(5)).toBeTrue();
    await servicio.descargar(5);
    const peticion = http.expectOne(url(5));
    peticion.flush(new Blob(['pdf']));
    await primera;
    expect(guardar).toHaveBeenCalledTimes(1);
    expect(servicio.descargando(5)).toBeFalse();
  });

  it('409: muestra el mensaje de la API (sin cobrar, anulado o devuelto)', async () => {
    const mensaje = 'El cobro de este cargo está devuelto: no se puede emitir el recibo.';
    const promesa = servicio.descargar(9);
    http
      .expectOne(url(9))
      .flush(new Blob([JSON.stringify({ mensaje })], { type: 'application/json' }), { status: 409, statusText: 'Conflict' });
    await promesa;
    expect(error).toHaveBeenCalledOnceWith(mensaje);
    expect(guardar).not.toHaveBeenCalled();
    expect(servicio.descargando(9)).toBeFalse();
  });

  it('409 sin mensaje de la API: el texto de respaldo', async () => {
    const promesa = servicio.descargar(9);
    http.expectOne(url(9)).flush(new Blob(['x']), { status: 409, statusText: 'Conflict' });
    await promesa;
    expect(error).toHaveBeenCalledOnceWith(TEXTO_RECIBO_ANULADO);
    expect(guardar).not.toHaveBeenCalled();
    expect(servicio.descargando(9)).toBeFalse();
  });

  it('otro error: aviso genérico', async () => {
    const promesa = servicio.descargar(9);
    http.expectOne(url(9)).flush(new Blob(['x']), { status: 500, statusText: 'Error' });
    await promesa;
    expect(error).toHaveBeenCalledOnceWith(TEXTO_RECIBO_FALLO);
  });
});
