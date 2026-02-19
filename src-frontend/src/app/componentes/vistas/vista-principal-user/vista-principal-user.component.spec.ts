import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { VistaPrincipalUserComponent } from './vista-principal-user.component';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';

describe('VistaPrincipalUserComponent', () => {
  let component: VistaPrincipalUserComponent;
  let fixture: ComponentFixture<VistaPrincipalUserComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthenticationService>;
  let endpointsServiceSpy: jasmine.SpyObj<EndpointsService>;
  let alumnoServiceSpy: jasmine.SpyObj<AlumnoService>;

  const alumnosMock = [
    { id: 1, nombre: 'Alumno', apellidos: 'Uno' },
    { id: 2, nombre: 'Alumno', apellidos: 'Dos' },
  ];

  const documentoMock = {
    id: 15,
    nombre: 'autorizacion.pdf',
    tipo: 'application/pdf',
    url: '',
    ruta: 'Documentos_Alumnos_Moiskimdo/1/autorizacion.pdf',
  };

  beforeEach(async () => {
    window.localStorage.clear();

    authServiceSpy = jasmine.createSpyObj<AuthenticationService>(
      'AuthenticationService',
      ['obtenerNombreUsuario', 'obtenerTodosLosAlumnos', 'obtenerRecordatorioRachaEmail', 'actualizarRecordatorioRachaEmail']
    );
    endpointsServiceSpy = jasmine.createSpyObj<EndpointsService>(
      'EndpointsService',
      [
        'obtenerGruposDelAlumnoObservable',
        'obtenerTurnosDelAlumnoObservable',
        'obtenerConvocatoriasDeAlumno',
        'obtenerEstadoRetoDiario',
        'obtenerRankingRetoDiarioSemanal',
        'obtenerDocumentosDeAlumno',
        'obtenerUrlDescargaDocumentoAlumno',
        'descargarDocumentoAlumno',
        'obtenerEventos',
      ],
      { gruposDelAlumno$: of([]), eventos$: of([]) }
    );
    alumnoServiceSpy = jasmine.createSpyObj<AlumnoService>('AlumnoService', ['obtenerDeportesDelAlumno']);

    authServiceSpy.obtenerNombreUsuario.and.returnValue(of('usuario'));
    authServiceSpy.obtenerTodosLosAlumnos.and.returnValue(of(alumnosMock as any));
    authServiceSpy.obtenerRecordatorioRachaEmail.and.returnValue(of({ habilitado: false }));
    authServiceSpy.actualizarRecordatorioRachaEmail.and.returnValue(of({ habilitado: true }));
    endpointsServiceSpy.obtenerGruposDelAlumnoObservable.and.returnValue(of([] as any));
    endpointsServiceSpy.obtenerTurnosDelAlumnoObservable.and.returnValue(of([] as any));
    endpointsServiceSpy.obtenerConvocatoriasDeAlumno.and.returnValue(of([] as any));
    endpointsServiceSpy.obtenerEstadoRetoDiario.and.returnValue(of({
      racha: 0,
      completadoHoy: false,
      fechaCompletado: null,
      nextResetAtEpochMs: null,
    }));
    endpointsServiceSpy.obtenerRankingRetoDiarioSemanal.and.returnValue(of({
      deporte: 'TAEKWONDO',
      anioIso: 2026,
      semanaIso: 8,
      totalParticipantes: 1,
      top: [
        { posicion: 1, alias: 'Alumno U.', diasCompletados: 2, esUsuarioActual: true },
      ],
      miPosicion: {
        posicion: 1,
        alias: 'Alumno U.',
        diasCompletados: 2,
        diasParaSuperarSiguiente: null,
      },
    } as any));
    endpointsServiceSpy.obtenerDocumentosDeAlumno.and.returnValue(of([documentoMock] as any));
    endpointsServiceSpy.obtenerUrlDescargaDocumentoAlumno.and.callFake(
      (_alumnoId: number, _documentoId: number, forzarDescarga: boolean = false) =>
        forzarDescarga
          ? 'https://moiskimdo.es/api/alumnos/2/documentos/15/descargar?download=true'
          : 'https://moiskimdo.es/api/alumnos/2/documentos/15/descargar'
    );
    endpointsServiceSpy.descargarDocumentoAlumno.and.returnValue(of(new Blob(['pdf'])));
    alumnoServiceSpy.obtenerDeportesDelAlumno.and.returnValue(of([]));

    spyOn(Swal, 'fire').and.returnValue(Promise.resolve({} as any));
    spyOn(globalThis.URL, 'createObjectURL').and.returnValue('blob://test-url');
    spyOn(globalThis.URL, 'revokeObjectURL').and.stub();
    spyOn(window, 'open').and.returnValue(null);

    await TestBed.configureTestingModule({
      imports: [VistaPrincipalUserComponent],
      providers: [
        provideRouter([]),
        { provide: AuthenticationService, useValue: authServiceSpy },
        { provide: EndpointsService, useValue: endpointsServiceSpy },
        { provide: AlumnoService, useValue: alumnoServiceSpy },
      ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(VistaPrincipalUserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('debe cargar la preferencia de recordatorio de racha al iniciar', () => {
    expect(authServiceSpy.obtenerRecordatorioRachaEmail).toHaveBeenCalled();
    expect(component.recordatorioRachaEmailHabilitado).toBeFalse();
  });

  it('debe actualizar la preferencia de recordatorio al cambiar el toggle', () => {
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.checked = true;

    component.onToggleRecordatorioRachaEmail({ target: input } as unknown as Event);

    expect(authServiceSpy.actualizarRecordatorioRachaEmail).toHaveBeenCalledWith(true);
    expect(component.recordatorioRachaEmailHabilitado).toBeTrue();
  });

  it('debe cargar documentos del alumno inicial al iniciar', () => {
    expect(endpointsServiceSpy.obtenerDocumentosDeAlumno).toHaveBeenCalledWith(1);
    expect(component.documentosAlumno.length).toBe(1);
    expect(component.documentosAlumno[0].id).toBe(15);
  });

  it('debe recargar documentos al cambiar de alumno', () => {
    component.seleccionarAlumno(alumnosMock[1]);

    const llamadas = endpointsServiceSpy.obtenerDocumentosDeAlumno.calls.allArgs().map((args) => args[0]);
    expect(llamadas).toContain(2);
  });

  it('debe usar endpoint seguro al abrir y descargar documento', () => {
    component.selectedAlumno = alumnosMock[1];

    component.abrirDocumento(documentoMock);
    component.descargarDocumento(documentoMock);

    expect(endpointsServiceSpy.descargarDocumentoAlumno).toHaveBeenCalledWith(2, 15, false);
    expect(endpointsServiceSpy.descargarDocumentoAlumno).toHaveBeenCalledWith(2, 15, true);
    expect(endpointsServiceSpy.descargarDocumentoAlumno.calls.count()).toBe(2);
  });

  it('debe usar URL directa en iOS para abrir y descargar documento', () => {
    component.selectedAlumno = alumnosMock[1];
    spyOnProperty(globalThis.navigator, 'userAgent', 'get').and.returnValue(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1'
    );

    component.abrirDocumento(documentoMock);
    component.descargarDocumento(documentoMock);

    expect(endpointsServiceSpy.obtenerUrlDescargaDocumentoAlumno).toHaveBeenCalledWith(2, 15, false);
    expect(endpointsServiceSpy.obtenerUrlDescargaDocumentoAlumno).toHaveBeenCalledWith(2, 15, true);
    expect(endpointsServiceSpy.obtenerUrlDescargaDocumentoAlumno.calls.count()).toBe(2);
    expect(endpointsServiceSpy.descargarDocumentoAlumno).not.toHaveBeenCalled();
    expect(window.open).toHaveBeenCalledWith(
      'https://moiskimdo.es/api/alumnos/2/documentos/15/descargar',
      '_blank',
      'noopener'
    );
    expect(window.open).toHaveBeenCalledTimes(1);
  });

  it('debe cargar ranking semanal cuando hay deportes activos', () => {
    alumnoServiceSpy.obtenerDeportesDelAlumno.and.returnValue(of([
      { id: 11, deporte: 'TAEKWONDO', activo: true, principal: true },
    ] as any));

    component.seleccionarAlumno(alumnosMock[1]);

    expect(endpointsServiceSpy.obtenerRankingRetoDiarioSemanal).toHaveBeenCalledWith(2, 'TAEKWONDO', 10);
    expect(component.rankingSemanal?.deporte).toBe('TAEKWONDO');
  });

  it('debe recargar ranking al cambiar el deporte del ranking', () => {
    component.selectedAlumno = alumnosMock[0];
    component.deportesRankingDisponibles = ['TAEKWONDO', 'KICKBOXING'];
    component.deporteRankingSeleccionado = 'TAEKWONDO';

    component.onSeleccionarDeporteRanking('KICKBOXING');

    expect(endpointsServiceSpy.obtenerRankingRetoDiarioSemanal).toHaveBeenCalledWith(1, 'KICKBOXING', 10);
    expect(component.deporteRankingSeleccionado).toBe('KICKBOXING');
  });

  it('debe marcar documentos como vistos al ir a la seccion de documentos', () => {
    component.selectedAlumno = alumnosMock[0];
    component.documentosAlumno = [documentoMock as any];
    component.novedadesDocumentos = 1;
    spyOn(component, 'scrollToSection');
    window.localStorage.setItem('dashboard-user-vistos-documentos-1', JSON.stringify([]));

    component.irADocumentos();

    expect(component.scrollToSection).toHaveBeenCalledWith('mis-documentos');
    expect(component.novedadesDocumentos).toBe(0);
    expect(window.localStorage.getItem('dashboard-user-vistos-documentos-1')).toBe('[15]');
  });

  it('debe paginar documentos por bloques y cargar mas al avanzar', () => {
    component.documentosAlumno = Array.from({ length: 18 }, (_, index) => ({
      ...documentoMock,
      id: index + 1,
      nombre: `doc-${index + 1}.pdf`,
    })) as any;

    (component as any).reiniciarPaginacionDocumentos();
    expect(component.getDocumentosVisibles().length).toBe(8);
    expect(component.hayMasDocumentosPorCargar()).toBeTrue();

    component.cargarMasDocumentos();
    expect(component.getDocumentosVisibles().length).toBe(16);
    expect(component.hayMasDocumentosPorCargar()).toBeTrue();

    component.cargarMasDocumentos();
    expect(component.getDocumentosVisibles().length).toBe(18);
    expect(component.hayMasDocumentosPorCargar()).toBeFalse();
  });
});
