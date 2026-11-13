import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { ImportacionAlumnosService, InformeImportacion, nombreDescarga } from './importacion-alumnos.service';

describe('ImportacionAlumnosService', () => {
  let servicio: ImportacionAlumnosService;
  let http: HttpTestingController;
  const base = `${environment.apiUrl}/plataforma/escuelas/7/importacion/alumnos`;
  const hoja = new File([new Uint8Array([1, 2, 3])], 'hoja-maestra.xlsx');
  const INFORME: InformeImportacion = { filas: 2, listas: 2, creados: 0, yaCargados: 0, errores: [], avisos: [], yaCargadas: [] };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ImportacionAlumnosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ensayar: POST multipart con el campo file, con credenciales y sin disciplina', () => {
    servicio.ensayar(7, hoja).subscribe((informe) => expect(informe.listas).toBe(2));
    const peticion = http.expectOne(`${base}/ensayo`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.withCredentials).toBeTrue();
    const cuerpo = peticion.request.body as FormData;
    expect((cuerpo.get('file') as File).name).toBe('hoja-maestra.xlsx');
    expect(cuerpo.has('disciplina')).toBeFalse();
    expect(peticion.request.params.has('disciplina')).toBeFalse();
    peticion.flush(INFORME);
  });

  it('cargar: POST a carga con el mismo campo', () => {
    servicio.cargar(7, hoja).subscribe((informe) => expect(informe.listas).toBe(2));
    const peticion = http.expectOne(`${base}/carga`);
    expect(peticion.request.method).toBe('POST');
    expect((peticion.request.body as FormData).get('file')).toBeTruthy();
    peticion.flush(INFORME);
  });

  it('hojaConErrores: pide un blob y toma el nombre de Content-Disposition', () => {
    let resultado: { blob: Blob; nombre: string } | undefined;
    servicio.hojaConErrores(7, hoja).subscribe((r) => (resultado = r));
    const peticion = http.expectOne(`${base}/hoja-con-errores`);
    expect(peticion.request.responseType).toBe('blob');
    peticion.flush(new Blob(['x']), {
      headers: new HttpHeaders({ 'Content-Disposition': `attachment; filename*=UTF-8''hoja%20maestra-revisar.xlsx` }),
    });
    expect(resultado!.nombre).toBe('hoja maestra-revisar.xlsx');
    expect(resultado!.blob.size).toBe(1);
  });

  describe('nombreDescarga', () => {
    it('lee filename simple', () => {
      expect(nombreDescarga('attachment; filename="a-revisar.xlsx"', 'a.xlsx')).toBe('a-revisar.xlsx');
    });

    it('sin cabecera, <original>-revisar.xlsx', () => {
      expect(nombreDescarga(null, 'Hoja Sierra.xlsx')).toBe('Hoja Sierra-revisar.xlsx');
    });

    it('con filename* mal codificado cae a filename', () => {
      expect(nombreDescarga(`attachment; filename*=UTF-8''%E0%A4%A; filename="b.xlsx"`, 'a.xlsx')).toBe('b.xlsx');
    });
  });
});
