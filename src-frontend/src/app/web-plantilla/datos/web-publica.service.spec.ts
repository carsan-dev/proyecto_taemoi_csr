import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { LEGAL_DE_PRUEBA as LEGAL, eventoDetalleDePrueba, eventoListaDePrueba, sitioDePrueba } from './datos-de-prueba';
import { WebPublicaService } from './web-publica.service';

describe('WebPublicaService', () => {
  const url = `${environment.apiUrl}/publico/web`;
  let servicio: WebPublicaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(WebPublicaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide la web publicada una sola vez y reutiliza la respuesta en las navegaciones siguientes', () => {
    const primero = servicio.sitio(null);
    const segundo = servicio.sitio(null);
    expect(primero()).toEqual({ estado: 'cargando' });

    const peticion = http.expectOne((r) => r.url === url);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.keys()).toEqual([]);
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush(sitioDePrueba());

    expect(primero()).toEqual({ estado: 'listo', datos: sitioDePrueba() });
    expect(segundo()).toBe(primero());
    expect(servicio.sitio(null)()).toEqual({ estado: 'listo', datos: sitioDePrueba() });
    http.expectNone((r) => r.url === url);
  });

  it('vista previa: pide vistaPrevia=true con el tema pedido, aparte de lo publicado', () => {
    const publicada = servicio.sitio(null);
    http.expectOne((r) => r.url === url && !r.params.has('vistaPrevia')).flush(sitioDePrueba());

    const previa = servicio.sitio('chandal');
    const peticion = http.expectOne((r) => r.url === url && r.params.get('vistaPrevia') === 'true');
    expect(peticion.request.params.get('tema')).toBe('chandal');
    peticion.flush(sitioDePrueba({ tema: 'chandal', vistaPrevia: true, publicada: false }));

    expect(previa()).toEqual({
      estado: 'listo',
      datos: sitioDePrueba({ tema: 'chandal', vistaPrevia: true, publicada: false }),
    });
    expect(publicada()).toEqual({ estado: 'listo', datos: sitioDePrueba() });
    // Otra vez la misma vista previa: sin petición nueva.
    servicio.sitio('chandal');
    http.expectNone((r) => r.url === url);
  });

  it('vista previa con un tema que no existe: vistaPrevia=true sin tema (la API daría 400)', () => {
    servicio.sitio('inventado');
    const peticion = http.expectOne((r) => r.url === url);
    expect(peticion.request.params.get('vistaPrevia')).toBe('true');
    expect(peticion.request.params.has('tema')).toBeFalse();
    peticion.flush(sitioDePrueba());
  });

  it('expone el tema pedido en la vista previa y null fuera de ella', () => {
    expect(servicio.temaVistaPrevia('chandal')).toBe('chandal');
    expect(servicio.temaVistaPrevia('inventado')).toBeNull();
    expect(servicio.temaVistaPrevia('')).toBeNull();
    expect(servicio.temaVistaPrevia(null)).toBeNull();
    expect(servicio.esVistaPrevia('')).toBeTrue();
    expect(servicio.esVistaPrevia(null)).toBeFalse();
  });

  it('404: no-publicada, y no se vuelve a pedir', () => {
    const estado = servicio.sitio(null);
    http.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
    expect(estado()).toEqual({ estado: 'no-publicada' });
    servicio.sitio(null);
    http.expectNone(url);
  });

  it('error: estado error, y la siguiente página lo vuelve a pedir', () => {
    const estado = servicio.sitio(null);
    http.expectOne(url).flush(null, { status: 500, statusText: 'Server Error' });
    expect(estado()).toEqual({ estado: 'error' });

    const otra = servicio.sitio(null);
    expect(otra()).toEqual({ estado: 'cargando' });
    http.expectOne(url).flush(sitioDePrueba());
    expect(otra()).toEqual({ estado: 'listo', datos: sitioDePrueba() });
  });

  it('legal: una petición, publicada y vista previa por separado', () => {
    const legal = servicio.legal(null);
    servicio.legal(null);
    http.expectOne((r) => r.url === `${url}/legal` && !r.params.has('vistaPrevia')).flush(LEGAL);
    expect(legal()).toEqual({ estado: 'listo', datos: LEGAL });

    const previa = servicio.legal('chandal');
    const peticion = http.expectOne((r) => r.url === `${url}/legal`);
    expect(peticion.request.params.get('vistaPrevia')).toBe('true');
    expect(peticion.request.params.has('tema')).toBeFalse();
    peticion.flush(null, { status: 404, statusText: 'Not Found' });
    expect(previa()).toEqual({ estado: 'no-publicada' });
    expect(legal()).toEqual({ estado: 'listo', datos: LEGAL });
  });
  it('eventos: una petición, publicada y vista previa (sin tema) por separado; 404 es no-publicada', () => {
    const datos = { proximos: [eventoListaDePrueba()], anteriores: [] };
    const eventos = servicio.eventos(null);
    servicio.eventos(null);
    http.expectOne((r) => r.url === `${url}/eventos` && !r.params.has('vistaPrevia')).flush(datos);
    expect(eventos()).toEqual({ estado: 'listo', datos });

    const previa = servicio.eventos('linea-comarcal');
    const peticion = http.expectOne((r) => r.url === `${url}/eventos`);
    expect(peticion.request.params.get('vistaPrevia')).toBe('true');
    expect(peticion.request.params.has('tema')).toBeFalse();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush(null, { status: 404, statusText: 'Not Found' });
    expect(previa()).toEqual({ estado: 'no-publicada' });
    expect(eventos()).toEqual({ estado: 'listo', datos });
  });

  it('evento: una petición por id y por vista previa; la caché no mezcla ids', () => {
    const siete = servicio.evento(7, null);
    servicio.evento(7, null);
    http.expectOne((r) => r.url === `${url}/eventos/7` && !r.params.has('vistaPrevia')).flush(eventoDetalleDePrueba());
    expect(siete()).toEqual({ estado: 'listo', datos: eventoDetalleDePrueba() });

    servicio.evento(8, null);
    http.expectOne(`${url}/eventos/8`).flush(eventoDetalleDePrueba({ id: 8 }));
    servicio.evento(7, '');
    const previa = http.expectOne((r) => r.url === `${url}/eventos/7` && r.params.get('vistaPrevia') === 'true');
    expect(previa.request.params.has('tema')).toBeFalse();
    previa.flush(eventoDetalleDePrueba());
    servicio.evento(7, null);
    http.expectNone(`${url}/eventos/7`);
  });

  it('evento: 404 es no-encontrado (oculto, de otra escuela o la web sin publicar lo dice sitio) y no se vuelve a pedir', () => {
    const estado = servicio.evento(99, null);
    http.expectOne(`${url}/eventos/99`).flush(null, { status: 404, statusText: 'Not Found' });
    expect(estado()).toEqual({ estado: 'no-encontrado' });
    servicio.evento(99, null);
    http.expectNone(`${url}/eventos/99`);
  });

  it('evento: error, y se vuelve a pedir', () => {
    const estado = servicio.evento(5, null);
    http.expectOne(`${url}/eventos/5`).flush(null, { status: 500, statusText: 'Server Error' });
    expect(estado()).toEqual({ estado: 'error' });
    servicio.evento(5, null);
    http.expectOne(`${url}/eventos/5`).flush(eventoDetalleDePrueba({ id: 5 }));
  });
});
