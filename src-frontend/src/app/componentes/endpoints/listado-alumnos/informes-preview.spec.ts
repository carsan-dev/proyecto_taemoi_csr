import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import Swal from 'sweetalert2';
import { HerramientasAlumnosComponent } from './herramientas-alumnos/herramientas-alumnos.component';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { LoadingService } from '../../../servicios/generales/loading.service';
import { InformeModalComponent } from '../../generales/informe-modal/informe-modal.component';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';

// 8b-Z: el listado de hoy, Convocatorias y Tesorería de hoy se retiraron; quedan las herramientas del pie (que hereda el
// panel nuevo) y el selector de informes.
describe('Acciones de informes administrativos', () => {
  let service: InformePdfService;
  let herramientas: HerramientasAlumnosComponent;
  let api: any;
  const blob = new Blob(['pdf']);
  beforeEach(() => {
    const loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    api = jasmine.createSpyObj('EndpointsService', ['descargarAsistencia', 'generarListadoMensualidadMensual']);
    Object.values(api).forEach((spy: any) => spy.and.returnValue(of(blob)));
    TestBed.configureTestingModule({ providers: [InformePdfService, { provide: LoadingService, useValue: loading },
      { provide: EndpointsService, useValue: api }, proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    service = TestBed.inject(InformePdfService);
    // 8b-3a: las herramientas del pie (asistencia y listado de mensualidades) salen del listado a su propio componente.
    herramientas = TestBed.runInInjectionContext(() => new HerramientasAlumnosComponent());
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test'); spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click');
    spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: false, isDenied: false, isDismissed: true });
  });
  it('asistencia: orden seleccionado, fallo parcial y todos los PDF en descarga directa', () => {
    herramientas.mesAnoAsistencia = '2026-09';
    ['viernes', 'lunes', 'martes'].forEach(grupo => herramientas.toggleGrupoSeleccionado(grupo));
    api.descargarAsistencia.and.callFake((_y: number, _m: number, grupo: string) =>
      grupo === 'lunes' ? throwError(() => new Error()) : of(blob));
    herramientas.generarListadoAsistencia('ver');
    expect(api.descargarAsistencia.calls.allArgs()).toEqual([[2026, 9, 'viernes'], [2026, 9, 'lunes'], [2026, 9, 'martes']]);
    expect(service.documentos.map(d => d.nombreArchivo)).toEqual(['Asistencia-viernes-2026-09.pdf', 'Asistencia-martes-2026-09.pdf']);
    expect(service.aviso).toContain('lunes'); expect(URL.createObjectURL).not.toHaveBeenCalled();
    herramientas.generarListadoAsistencia('descargar');
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(2);
  });
  it('captura mes al iniciar; bloquea duplicados y conserva validaciones', () => {
    herramientas.generarListadoMensualidadMensual('ver');
    expect(api.generarListadoMensualidadMensual).not.toHaveBeenCalled();
    const pending = new Subject<Blob>(); api.generarListadoMensualidadMensual.and.returnValue(pending);
    herramientas.mesAnoMensualidad = '2026-09'; herramientas.generarListadoMensualidadMensual('ver');
    herramientas.mesAnoMensualidad = '2026-10'; herramientas.generarListadoMensualidadMensual('descargar');
    expect(api.generarListadoMensualidadMensual).toHaveBeenCalledOnceWith('2026-09', true);
    pending.next(blob); pending.complete();
    expect(service.documentos[0].nombreArchivo).toBe('listado_mensualidad_SEPTIEMBRE 2026.pdf');
  });
  it('selector emite acción y deshabilita ambas mientras genera', () => {
    const fixture = TestBed.createComponent(InformeModalComponent);
    const selector = fixture.componentInstance;
    selector.selectedInforme = 'general'; spyOn(selector.informeSeleccionado, 'emit');
    selector.generarInforme('ver'); expect(selector.informeSeleccionado.emit).toHaveBeenCalledWith({
      tipo: 'general', soloActivos: true, temporada: undefined, accion: 'ver',
    });
    selector.generando = true; selector.generarInforme('descargar');
    expect(selector.informeSeleccionado.emit).toHaveBeenCalledTimes(1);
    fixture.detectChanges();
    expect(Array.from(fixture.nativeElement.querySelectorAll('.btn-generate')).every((b: any) => b.disabled)).toBeTrue();
    fixture.destroy();
  });
});
