import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

import { VistaPrincipalAdminComponent } from './vista-principal-admin.component';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CatalogoService } from '../../../features/catalogo/catalogo.service';
import { PlataformaService } from '../../../servicios/plataforma/plataforma.service';

describe('VistaPrincipalAdminComponent', () => {
  let fixture: ComponentFixture<VistaPrincipalAdminComponent>;
  let component: VistaPrincipalAdminComponent;
  let roles: BehaviorSubject<string[]>;
  let endpointsSpy: jasmine.SpyObj<EndpointsService>;
  let plataformaSpy: jasmine.SpyObj<PlataformaService>;

  async function crear(rolesIniciales: string[], esAdminPlataforma: boolean, totalErrores = 0): Promise<void> {
    roles = new BehaviorSubject<string[]>(rolesIniciales);
    const eventos$ = new BehaviorSubject<any[]>([]);

    endpointsSpy = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'obtenerTodosLosEventos',
      'obtenerAlumnos',
      'obtenerResumenGrupos',
      'obtenerResumenTurnos',
      'obtenerAlumnosAptosParaExamen',
      'obtenerTesoreriaResumen',
      'obtenerDistribucionAlumnosPorDeporte',
      'obtenerAuditoriaEventos',
    ]);
    (endpointsSpy as any).eventos$ = eventos$.asObservable();
    endpointsSpy.obtenerTodosLosEventos.and.callFake(() => eventos$.next([]));
    endpointsSpy.obtenerAlumnos.and.returnValue(of({ totalElements: 5 } as any));
    endpointsSpy.obtenerResumenGrupos.and.returnValue(of([{ id: 1 }] as any));
    endpointsSpy.obtenerResumenTurnos.and.returnValue(of([{ id: 1 }] as any));
    endpointsSpy.obtenerAlumnosAptosParaExamen.and.returnValue(of([] as any));
    endpointsSpy.obtenerTesoreriaResumen.and.returnValue(of({ totalPendientes: 0, importePendiente: 0 } as any));
    endpointsSpy.obtenerDistribucionAlumnosPorDeporte.and.returnValue(of({} as any));

    plataformaSpy = jasmine.createSpyObj<PlataformaService>('PlataformaService', ['esAdminPlataforma', 'errores']);
    plataformaSpy.esAdminPlataforma.and.returnValue(of(esAdminPlataforma));
    // 4xx devuelve 1 y 5xx el resto, para comprobar que el aviso suma ambas familias.
    plataformaSpy.errores.and.callFake((filtros) => {
      const cuatro = Math.min(totalErrores, 1);
      const total = filtros.estado === '4xx' ? cuatro : totalErrores - cuatro;
      return of({ content: [], totalElements: total, totalPages: 1, size: 1, number: 0 });
    });

    const authMock = {
      obtenerNombreUsuario: () => of(null),
      getRoles: () => roles.asObservable(),
    };
    const catalogoMock = {
      etiquetaDisciplina: (d: string) => d,
      emojiDisciplina: () => '',
      colorDisciplina: () => '#000',
    };

    await TestBed.configureTestingModule({
      imports: [VistaPrincipalAdminComponent],
      providers: [
        provideRouter([]),
        { provide: AuthenticationService, useValue: authMock },
        { provide: EndpointsService, useValue: endpointsSpy },
        { provide: CatalogoService, useValue: catalogoMock },
        { provide: PlataformaService, useValue: plataformaSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(VistaPrincipalAdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const rutasTarjetas = (selector: string) =>
    Array.from(fixture.nativeElement.querySelectorAll(selector)).map((a: any) => a.getAttribute('href'));

  afterEach(() => fixture?.destroy());

  it('ROLE_ADMIN ve Registro de acciones y Roles y Configuracion', async () => {
    await crear(['ROLE_ADMIN'], false);

    expect(rutasTarjetas('a.operation-card')).toContain('/auditoriaSistema');
    expect(rutasTarjetas('a.operation-card')).toContain('/configuracion-sistema');
    expect(rutasTarjetas('a.quick-access-card')).toContain('/auditoriaSistema');
    expect(fixture.nativeElement.textContent).toContain('Registro de acciones');
  });

  it('ROLE_MANAGER no ve las tarjetas solo-admin (D10)', async () => {
    await crear(['ROLE_MANAGER'], false);

    expect(rutasTarjetas('a.operation-card')).not.toContain('/auditoriaSistema');
    expect(rutasTarjetas('a.operation-card')).not.toContain('/configuracion-sistema');
    expect(rutasTarjetas('a.quick-access-card')).not.toContain('/auditoriaSistema');
    expect(rutasTarjetas('a.operation-card')).toContain('/tesoreriaCobros');
  });

  it('ya no consulta errores de auditoría de la escuela', async () => {
    await crear(['ROLE_ADMIN'], false);

    expect(endpointsSpy.obtenerAuditoriaEventos).not.toHaveBeenCalled();
    expect(plataformaSpy.errores).not.toHaveBeenCalled();
    expect(component.alertasOperativas.some((a) => a.ruta === '/plataformaErrores')).toBeFalse();
  });

  it('el administrador de plataforma ve el aviso de errores de los últimos 7 días', async () => {
    await crear(['ROLE_ADMIN'], true, 3);

    // Solo 4xx + 5xx (sin los anónimos con 2xx): dos consultas, una por familia.
    expect(plataformaSpy.errores).toHaveBeenCalledWith(jasmine.objectContaining({ estado: '4xx', page: 1, size: 1 }));
    expect(plataformaSpy.errores).toHaveBeenCalledWith(jasmine.objectContaining({ estado: '5xx', page: 1, size: 1 }));
    expect(plataformaSpy.errores.calls.allArgs().every(([f]) => !!f.estado)).toBeTrue();
    const alerta = component.alertasOperativas.find((a) => a.id === 'errores-plataforma');
    expect(alerta?.titulo).toBe('3 errores en 7 días');
    expect(alerta?.tipo).toBe('critical');
    expect(rutasTarjetas('a.alert-card')).toContain('/plataformaErrores');
  });

  it('sin errores de plataforma no muestra el aviso', async () => {
    await crear(['ROLE_ADMIN'], true, 0);

    expect(component.alertasOperativas.some((a) => a.id === 'errores-plataforma')).toBeFalse();
  });
});
