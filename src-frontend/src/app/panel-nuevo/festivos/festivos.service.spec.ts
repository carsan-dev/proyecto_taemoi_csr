import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { FestivosService } from './festivos.service';

describe('FestivosService', () => {
  let servicio: FestivosService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/festivos`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(FestivosService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('listar: GET con pasados y, si se da, sedeId; errores locales y credenciales', () => {
    let sedes: unknown;
    servicio.listar(false).subscribe((l) => (sedes = l[0].sedes));
    const a = http.expectOne((r) => r.url === url);
    expect(a.request.method).toBe('GET');
    expect(a.request.params.get('pasados')).toBe('false');
    expect(a.request.params.has('sedeId')).toBeFalse();
    expect(a.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(a.request.withCredentials).toBeTrue();
    a.flush([{ id: 1 }]);
    expect(sedes).toEqual([]);

    servicio.listar(true, 4).subscribe();
    const b = http.expectOne((r) => r.url === url);
    expect(b.request.params.get('pasados')).toBe('true');
    expect(b.request.params.get('sedeId')).toBe('4');
    b.flush(null);
  });

  it('crear, editar y quitar', () => {
    const cuerpo = { tipo: 'DIA' as const, fechaDesde: '2026-11-05', fechaHasta: '2026-11-05', sedeIds: [2] };
    servicio.crear(cuerpo).subscribe();
    const c = http.expectOne(url);
    expect(c.request.method).toBe('POST');
    expect(c.request.body).toEqual(cuerpo);
    c.flush({});
    servicio.editar(7, cuerpo).subscribe();
    const e = http.expectOne(`${url}/7`);
    expect(e.request.method).toBe('PUT');
    e.flush({});
    servicio.quitar(7).subscribe();
    const q = http.expectOne(`${url}/7`);
    expect(q.request.method).toBe('DELETE');
    q.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('impacto: POST con excluirFestivoId solo al editar; respuesta vacía, ceros', () => {
    const cuerpo = { tipo: 'ANUAL' as const, mes: 12, dia: 8, sedeIds: [1, 2] };
    let impacto: unknown;
    servicio.impacto(cuerpo).subscribe((i) => (impacto = i));
    const a = http.expectOne(`${url}/impacto`);
    expect(a.request.method).toBe('POST');
    expect(a.request.body).toEqual(cuerpo);
    a.flush(null);
    expect(impacto).toEqual({ clases: 0, conLista: 0, conListaDetalle: [], clasesDetalle: [] });
    servicio.impacto(cuerpo, 9).subscribe();
    const b = http.expectOne(`${url}/impacto`);
    expect(b.request.body).toEqual({ ...cuerpo, excluirFestivoId: 9 });
    b.flush({ clases: 3, conLista: 0, conListaDetalle: [] });
  });

  it('impacto: pasa clasesDetalle con su lista (presentes y total)', () => {
    const detalle = [
      { sedeId: 2, sede: 'Estepa', fecha: '2026-11-05', horaInicio: '17:00', horaFin: '18:00', grupo: 'Iniciación', conLista: true, presentes: 11, total: 14 },
      { sedeId: 2, sede: 'Estepa', fecha: '2026-11-05', horaInicio: '18:00', horaFin: '19:00', grupo: 'Infantiles', conLista: false, presentes: null, total: null },
    ];
    let impacto: unknown;
    servicio.impacto({ tipo: 'DIA', fechaDesde: '2026-11-05', fechaHasta: '2026-11-05', sedeIds: [2] }).subscribe((i) => (impacto = i));
    http.expectOne(`${url}/impacto`).flush({ clases: 2, conLista: 1, conListaDetalle: [], clasesDetalle: detalle });
    expect((impacto as { clasesDetalle: unknown }).clasesDetalle).toEqual(detalle);
  });
});
