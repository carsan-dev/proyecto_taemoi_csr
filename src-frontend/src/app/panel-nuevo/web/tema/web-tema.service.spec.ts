import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';
import { WebTemaService } from './web-tema.service';

const API = environment.apiUrl;
const ESTADO = { tema: 'linea-comarcal', publicada: true, publicadaEn: '2026-11-01T10:00:00Z', temasDisponibles: ['linea-comarcal'] };

describe('WebTemaService (W7)', () => {
  let servicio: WebTemaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), WebTemaService] });
    servicio = TestBed.inject(WebTemaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GET /api/escuela/web con credenciales y el error tratado en la pantalla', () => {
    let leido: unknown;
    servicio.obtener().subscribe((e) => (leido = e));
    const peticion = http.expectOne(`${API}/escuela/web`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush(ESTADO);
    expect(leido).toEqual(ESTADO);
  });

  it('PUT /api/escuela/web/publicacion con {tema}', () => {
    servicio.publicar('linea-comarcal').subscribe();
    const peticion = http.expectOne(`${API}/escuela/web/publicacion`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ tema: 'linea-comarcal' });
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush(ESTADO);
  });

  it('el slug de la primera sede con página, pedido una sola vez a la vista previa pública', () => {
    const slugs: (string | null)[] = [];
    servicio.slugPrimeraSede('linea-comarcal').subscribe((s) => slugs.push(s));
    const peticion = http.expectOne((r) => r.url === `${API}/publico/web`);
    expect(peticion.request.params.get('vistaPrevia')).toBe('true');
    expect(peticion.request.params.get('tema')).toBe('linea-comarcal');
    expect(peticion.request.withCredentials).toBeTrue();
    peticion.flush({ sedes: [{ id: 1, slug: null }, { id: 2, slug: 'casariche' }, { id: 3, slug: 'estepa' }] });
    servicio.slugPrimeraSede('chandal').subscribe((s) => slugs.push(s));
    http.expectNone((r) => r.url === `${API}/publico/web`);
    expect(slugs).toEqual(['casariche', 'casariche']);
  });

  it('un fallo no se cachea: el siguiente acceso vuelve a pedir; un éxito sí', () => {
    const slugs: (string | null)[] = [];
    servicio.slugPrimeraSede('linea-comarcal').subscribe((s) => slugs.push(s));
    http.expectOne((r) => r.url === `${API}/publico/web`).flush(null, { status: 500, statusText: 'Error' });
    servicio.slugPrimeraSede('linea-comarcal').subscribe((s) => slugs.push(s));
    http.expectOne((r) => r.url === `${API}/publico/web`).flush({ sedes: [{ id: 1, slug: 'casariche' }] });
    servicio.slugPrimeraSede('linea-comarcal').subscribe((s) => slugs.push(s));
    http.expectNone((r) => r.url === `${API}/publico/web`);
    expect(slugs).toEqual([null, 'casariche', 'casariche']);
  });

  it('sin sedes o con error, null', () => {
    let slug: string | null | undefined;
    servicio.slugPrimeraSede('linea-comarcal').subscribe((s) => (slug = s));
    http.expectOne((r) => r.url === `${API}/publico/web`).flush({ sedes: [] });
    expect(slug).toBeNull();

    const otro = TestBed.runInInjectionContext(() => new WebTemaService());
    slug = undefined;
    otro.slugPrimeraSede('linea-comarcal').subscribe((s) => (slug = s));
    http.expectOne((r) => r.url === `${API}/publico/web`).flush(null, { status: 500, statusText: 'Error' });
    expect(slug).toBeNull();
  });
});
