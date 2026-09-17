import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import Swal from 'sweetalert2';
import { DocumentoInforme, InformePdfService, ResultadoInformes } from './informe-pdf.service';
import { LoadingService } from './loading.service';

describe('InformePdfService', () => {
  let service: InformePdfService;
  let loading: jasmine.SpyObj<LoadingService>;
  const documento: DocumentoInforme = { blob: new Blob(['pdf']), nombreArchivo: 'informe.pdf', titulo: 'Informe' };
  beforeEach(() => {
    loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    TestBed.configureTestingModule({ providers: [InformePdfService, { provide: LoadingService, useValue: loading }] });
    service = TestBed.inject(InformePdfService);
    spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: false, isDenied: false, isDismissed: true });
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test');
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click');
  });
  it('ver conserva el Blob sin descargar; descargar conserva nombre y libera URL', () => {
    service.generar(of({ documentos: [documento] }), 'ver', 'Error');
    expect(service.documentos[0]).toBe(documento);
    expect(URL.createObjectURL).not.toHaveBeenCalled();
    let nombre = '';
    (HTMLAnchorElement.prototype.click as jasmine.Spy).and.callFake(function(this: HTMLAnchorElement) { nombre = this.download; });
    service.generar(of({ documentos: [documento] }), 'descargar', 'Error');
    expect(nombre).toBe('informe.pdf');
    expect(URL.createObjectURL).toHaveBeenCalledOnceWith(documento.blob);
    expect(URL.revokeObjectURL).toHaveBeenCalledOnceWith('blob:test');
  });
  it('evita duplicados y cancela peticiones al destruir la pantalla', () => {
    const fin = jasmine.createSpy('cancelar');
    const subscribe = jasmine.createSpy('subscribe');
    const request = new Observable<ResultadoInformes>(() => { subscribe(); return fin; });
    service.generar(request, 'ver', 'Error');
    service.generar(request, 'descargar', 'Error');
    expect(subscribe).toHaveBeenCalledTimes(1);
    expect(service.ocupado).toBeTrue();
    service.ngOnDestroy();
    expect(fin).toHaveBeenCalledTimes(1);
    expect(service.ocupado).toBeFalse();
    expect(loading.hide).toHaveBeenCalledTimes(1);
    service.generar(request, 'ver', 'Error');
    expect(subscribe).toHaveBeenCalledTimes(1);
  });
  it('descarta resultados tardíos y permite reintentar tras error', () => {
    service.generar(throwError(() => new Error()), 'ver', 'Error de generación');
    expect(Swal.fire).toHaveBeenCalledWith('Error', 'Error de generación', 'error');
    expect(service.ocupado).toBeFalse();
    const request = new Subject<ResultadoInformes>();
    service.generar(request, 'ver', 'Error');
    request.next({ documentos: [documento] });
    expect(service.documentos.length).toBe(1);
    service.ngOnDestroy();
    request.next({ documentos: [documento] });
    expect(service.documentos).toEqual([]);
  });
  it('conserva éxitos y aviso parcial; descarga directa incluye todos los correctos', () => {
    const resultado = { documentos: [documento, { ...documento, nombreArchivo: 'dos.pdf' }], aviso: 'Falló jueves' };
    service.generar(of(resultado), 'ver', 'Error');
    expect(service.documentos.length).toBe(2);
    expect(service.aviso).toBe('Falló jueves');
    expect(Swal.fire).not.toHaveBeenCalled();
    service.generar(of(resultado), 'descargar', 'Error');
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(2);
    expect(Swal.fire).toHaveBeenCalledWith('Aviso', 'Falló jueves', 'warning');
  });
  it('no abre visor vacío cuando fallan todos los PDF', () => {
    service.generar(of({ documentos: [], aviso: 'Fallaron todos' }), 'ver', 'Error');
    expect(service.documentos).toEqual([]);
    expect(Swal.fire).toHaveBeenCalledWith('Aviso', 'Fallaron todos', 'warning');
  });
  it('descarta selección pendiente al destruir', async () => {
    const selection = service.seleccionar({ title: 'Temporada' });
    service.ngOnDestroy();
    expect(await selection).toBeNull();
  });
});
