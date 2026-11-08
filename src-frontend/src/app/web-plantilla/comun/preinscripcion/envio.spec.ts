import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Injector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';
import { EnvioPreinscripcion, enlaceDocumento, envioPreinscripcion, normasPreinscripcion } from './envio';
import { SolicitudPreinscripcion } from './preinscripcion';

const SOLICITUD = { deporte: 'TAEKWONDO', turnoIds: [1] } as SolicitudPreinscripcion;
const RESPUESTA = {
  referencia: 'TSS-2026-0142',
  temporada: '2026-2027',
  tokenDocumento: 'abc',
  emailEnviado: false,
  estado: 'PENDIENTE',
  plazaAsignada: true,
  posicionListaEspera: null,
  mensaje: 'Preinscripción recibida con plaza asignada.',
};

/** Envío y normas de la preinscripción de la plantilla: mismas rutas y cabecera que Moiskimdo, errores en la página. */
describe('preinscripción de la web por plantilla · envío', () => {
  const base = `${environment.apiUrl}/preinscripciones`;
  let http: HttpTestingController;
  let injector: Injector;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    injector = TestBed.inject(Injector);
  });

  afterEach(() => http.verify());

  const crear = (): EnvioPreinscripcion => runInInjectionContext(injector, () => envioPreinscripcion());

  it('POST con la solicitud, Idempotency-Key y errores locales; hecho con la respuesta; no reenvía', () => {
    const envio = crear();
    envio.enviar(SOLICITUD);
    expect(envio.estado().estado).toBe('enviando');
    envio.enviar(SOLICITUD);
    const peticion = http.expectOne(base);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(SOLICITUD);
    expect(peticion.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush(RESPUESTA, { status: 201, statusText: 'Created' });
    expect(envio.estado()).toEqual({ estado: 'hecho', respuesta: RESPUESTA });
    envio.enviar(SOLICITUD);
    http.expectNone(base);
  });

  it('400: el mensaje de la API; al reintentar, la misma clave de idempotencia', () => {
    const envio = crear();
    envio.enviar(SOLICITUD);
    const primera = http.expectOne(base);
    const clave = primera.request.headers.get('Idempotency-Key');
    primera.flush({ mensaje: 'Todos los turnos deben ser de la misma sede.' }, { status: 400, statusText: 'Bad Request' });
    expect(envio.estado()).toEqual({ estado: 'error', mensaje: 'Todos los turnos deben ser de la misma sede.' });
    envio.enviar(SOLICITUD);
    const segunda = http.expectOne(base);
    expect(segunda.request.headers.get('Idempotency-Key')).toBe(clave);
    segunda.flush(RESPUESTA);
  });

  it('error sin mensaje (red, 500): el de Moiskimdo', () => {
    const envio = crear();
    envio.enviar(SOLICITUD);
    http.expectOne(base).flush(null, { status: 500, statusText: 'Error' });
    expect(envio.estado()).toEqual({ estado: 'error', mensaje: 'No se pudo enviar la preinscripción.' });
  });

  it('normas: GET de la configuración de la disciplina; sin normas o con error, error; no repite la misma', () => {
    const normas = runInInjectionContext(injector, () => normasPreinscripcion());
    normas.cargar('TAEKWONDO');
    const peticion = http.expectOne(`${base}/public/configuracion/TAEKWONDO`);
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush({ deporte: 'TAEKWONDO', version: 3, contenido: { normas: ['Llega a tu hora.'], importes: 'Mensualidad: 25 €' } });
    expect(normas.estado()).toEqual({ estado: 'listo', normas: ['Llega a tu hora.'], importes: 'Mensualidad: 25 €' });
    normas.cargar('TAEKWONDO');
    http.expectNone(`${base}/public/configuracion/TAEKWONDO`);

    normas.cargar('KICKBOXING');
    http.expectOne(`${base}/public/configuracion/KICKBOXING`).flush({ contenido: { normas: [] } });
    expect(normas.estado()).toEqual({ estado: 'error' });
    normas.cargar('PILATES');
    http.expectOne(`${base}/public/configuracion/PILATES`).flush(null, { status: 404, statusText: 'Not Found' });
    expect(normas.estado()).toEqual({ estado: 'error' });
  });

  it('enlace de la solicitud firmada, como Moiskimdo', () => {
    expect(enlaceDocumento({ referencia: 'TSS-2026-0142', tokenDocumento: 'a b' })).toBe(`${base}/TSS-2026-0142/documento?token=a%20b`);
  });
});
