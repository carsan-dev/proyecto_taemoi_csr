import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import Swal from 'sweetalert2';
import { ListadoAlumnosComponent } from './listado-alumnos.component';
import { ListadoConvocatoriasComponent } from '../listado-convocatorias/listado-convocatorias.component';
import { TesoreriaCobrosComponent } from '../tesoreria-cobros/tesoreria-cobros.component';
import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { LoadingService } from '../../../servicios/generales/loading.service';
import { InformeModalComponent } from '../../generales/informe-modal/informe-modal.component';

describe('Acciones de informes administrativos', () => {
  let service: InformePdfService;
  let alumnos: ListadoAlumnosComponent;
  let api: any;
  const blob = new Blob(['pdf']);
  const casos = [
    ['general', 'generarInformeAlumnosPorGrado'], ['taekwondo', 'generarInformeTaekwondoPorGrado'],
    ['kickboxing', 'generarInformeKickboxingPorGrado'], ['licencias', 'generarInformeLicencias'],
    ['infantiles', 'generarInformeInfantilesAPromocionar'], ['adultos', 'generarInformeAdultosAPromocionar'],
    ['infantiles-taekwondo', 'generarInformeInfantilesAPromocionarTaekwondo'],
    ['infantiles-kickboxing', 'generarInformeInfantilesAPromocionarKickboxing'],
    ['adultos-taekwondo', 'generarInformeAdultosAPromocionarTaekwondo'],
    ['adultos-kickboxing', 'generarInformeAdultosAPromocionarKickboxing'],
    ['mensualidades', 'generarInformeMensualidades'], ['mensualidades-taekwondo', 'generarInformeMensualidadesTaekwondo'],
    ['mensualidades-kickboxing', 'generarInformeMensualidadesKickboxing'],
    ['productos', 'generarInformeProductos'], ['competidores', 'generarInformeCompetidores'],
  ];
  beforeEach(() => {
    const loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    TestBed.configureTestingModule({ providers: [InformePdfService, { provide: LoadingService, useValue: loading }] });
    service = TestBed.inject(InformePdfService);
    api = jasmine.createSpyObj('EndpointsService', [...casos.map(c => c[1]), 'generarInformeDeudas',
      'generarInformeDeudasCSV', 'generarInformeReservasPlaza', 'descargarAsistencia',
      'generarListadoMensualidadMensual', 'descargarInformePDFConvocatoria', 'exportarTesoreriaPDF', 'exportarTesoreriaCSV']);
    Object.values(api).forEach((spy: any) => spy.and.returnValue(of(blob)));
    alumnos = TestBed.runInInjectionContext(() => new ListadoAlumnosComponent(api, {} as any, loading, {} as any, {} as any));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test'); spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click');
    spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: false, isDenied: false, isDismissed: true });
  });
  for (const [tipo, method] of casos) {
    it(`${tipo}: Ver y Descargar usan mismos parámetros y nombre`, () => {
      alumnos.generarInformeSeleccionado({ tipo, soloActivos: false, accion: 'ver' });
      expect(URL.createObjectURL).not.toHaveBeenCalled();
      expect(service.documentos[0].blob).toBe(blob);
      expect(service.documentos[0].nombreArchivo).toBe('informe.pdf');
      const params = api[method].calls.mostRecent().args;
      expect(params).toEqual(['productos', 'competidores'].includes(tipo) ? [] : [false]);
      let nombre = '';
      (HTMLAnchorElement.prototype.click as jasmine.Spy).and.callFake(function(this: HTMLAnchorElement) { nombre = this.download; });
      alumnos.generarInformeSeleccionado({ tipo, soloActivos: false, accion: 'descargar' });
      expect(api[method].calls.mostRecent().args).toEqual(params);
      expect(nombre).toBe(service.documentos[0].nombreArchivo);
    });
  }
  it('deudas: Ver genera PDF y Descargar conserva elección CSV', async () => {
    alumnos.generarInformeSeleccionado({ tipo: 'deudas', soloActivos: true, accion: 'ver' });
    expect(Swal.fire).not.toHaveBeenCalled(); expect(api.generarInformeDeudas).toHaveBeenCalledWith(true);
    (Swal.fire as jasmine.Spy).and.resolveTo({ isConfirmed: false, isDenied: true, isDismissed: false });
    alumnos.generarInformeSeleccionado({ tipo: 'deudas', soloActivos: false, accion: 'descargar' });
    await Promise.resolve(); await Promise.resolve();
    expect(api.generarInformeDeudasCSV).toHaveBeenCalledWith(false);
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
  });
  it('reservas: cancelación sin petición; temporada confirmada conserva nombre', async () => {
    alumnos.temporadasReservasPlaza = ['2025/2026'];
    const event = { tipo: 'reservas-plaza', soloActivos: false, temporada: '2025/2026', accion: 'ver' as const };
    alumnos.generarInformeSeleccionado(event);
    await Promise.resolve(); await Promise.resolve();
    expect(api.generarInformeReservasPlaza).not.toHaveBeenCalled();
    (Swal.fire as jasmine.Spy).and.resolveTo({ isConfirmed: true, value: '2025/2026' });
    alumnos.generarInformeSeleccionado(event);
    await Promise.resolve(); await Promise.resolve();
    expect(api.generarInformeReservasPlaza).toHaveBeenCalledWith('2025/2026', false);
    expect(service.documentos[0].nombreArchivo).toBe('informe_reservas_plaza_2025_2026.pdf');
  });
  it('asistencia: orden seleccionado, fallo parcial y todos los PDF en descarga directa', () => {
    alumnos.mesAnoAsistencia = '2026-09';
    ['viernes', 'lunes', 'martes'].forEach(grupo => alumnos.toggleGrupoSeleccionado(grupo));
    api.descargarAsistencia.and.callFake((_y: number, _m: number, grupo: string) =>
      grupo === 'lunes' ? throwError(() => new Error()) : of(blob));
    alumnos.generarListadoAsistencia('ver');
    expect(api.descargarAsistencia.calls.allArgs()).toEqual([[2026, 9, 'viernes'], [2026, 9, 'lunes'], [2026, 9, 'martes']]);
    expect(service.documentos.map(d => d.nombreArchivo)).toEqual(['Asistencia-viernes-2026-09.pdf', 'Asistencia-martes-2026-09.pdf']);
    expect(service.aviso).toContain('lunes'); expect(URL.createObjectURL).not.toHaveBeenCalled();
    alumnos.generarListadoAsistencia('descargar');
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(2);
  });
  it('captura mes al iniciar; bloquea duplicados y conserva validaciones', () => {
    alumnos.generarListadoMensualidadMensual('ver');
    expect(api.generarListadoMensualidadMensual).not.toHaveBeenCalled();
    const pending = new Subject<Blob>(); api.generarListadoMensualidadMensual.and.returnValue(pending);
    alumnos.mesAnoMensualidad = '2026-09'; alumnos.generarListadoMensualidadMensual('ver');
    alumnos.mesAnoMensualidad = '2026-10'; alumnos.generarListadoMensualidadMensual('descargar');
    expect(api.generarListadoMensualidadMensual).toHaveBeenCalledOnceWith('2026-09', true);
    pending.next(blob); pending.complete();
    expect(service.documentos[0].nombreArchivo).toBe('listado_mensualidad_SEPTIEMBRE 2026.pdf');
  });
  it('convocatorias: mantiene ID y nombre en ambas acciones', () => {
    const screen = TestBed.runInInjectionContext(() => new ListadoConvocatoriasComponent(api, {} as any, {} as any));
    const convocatoria = { id: 7, deporte: 'TAEKWONDO', fechaConvocatoria: '2026-09-17' };
    screen.generarReporte(convocatoria, 'ver');
    expect(api.descargarInformePDFConvocatoria).toHaveBeenCalledWith(7);
    expect(service.documentos[0].nombreArchivo).toBe('informe_convocatoria_TAEKWONDO_17_9_2026.pdf');
    expect(URL.createObjectURL).not.toHaveBeenCalled();
    screen.generarReporte(convocatoria, 'descargar'); expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(1);
  });
  it('tesorería: conserva filtros PDF/CSV', () => {
    const screen = TestBed.runInInjectionContext(() => new TesoreriaCobrosComponent(api, {} as any, {} as any, {} as any));
    screen.filtroMes = 9; screen.filtroAno = 2026; screen.filtroDeporte = 'TAEKWONDO';
    screen.filtroEstado = 'PENDIENTES'; screen.filtroTexto = 'Ana'; screen.filtroSoloActivos = false;
    screen.exportarInformeDeudasPDF('ver');
    expect(api.exportarTesoreriaPDF).toHaveBeenCalledWith(9, 2026, 'TAEKWONDO', false, 'Ana', false);
    expect(URL.createObjectURL).not.toHaveBeenCalled();
    const nombre = service.documentos[0].nombreArchivo;
    let download = '';
    (HTMLAnchorElement.prototype.click as jasmine.Spy).and.callFake(function(this: HTMLAnchorElement) { download = this.download; });
    screen.exportarInformeDeudasPDF(); expect(download).toBe(nombre);
    screen.exportarInformeDeudasCSV(); expect(download).toMatch(/\.csv$/);
    expect(api.exportarTesoreriaCSV).toHaveBeenCalledWith(9, 2026, 'TAEKWONDO', false, 'Ana', false);
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
