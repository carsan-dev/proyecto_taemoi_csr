import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { TesoreriaCobrosComponent } from './tesoreria-cobros.component';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { LoadingService } from '../../../servicios/generales/loading.service';

describe('TesoreriaCobrosComponent', () => {
  let component: TesoreriaCobrosComponent;
  let fixture: ComponentFixture<TesoreriaCobrosComponent>;
  let endpointsSpy: jasmine.SpyObj<EndpointsService>;
  let authSpy: jasmine.SpyObj<AuthenticationService>;
  let loadingSpy: jasmine.SpyObj<LoadingService>;

  beforeEach(async () => {
    endpointsSpy = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'obtenerTesoreriaMovimientos',
      'obtenerTesoreriaResumen',
      'obtenerTesoreriaAniosDisponibles',
      'actualizarCobroTesoreria',
      'exportarTesoreriaPDF',
      'exportarTesoreriaCSV',
    ]);
    authSpy = jasmine.createSpyObj<AuthenticationService>('AuthenticationService', [
      'rolesEstanCargados',
      'tieneRolAdmin',
      'obtenerRoles',
    ]);
    loadingSpy = jasmine.createSpyObj<LoadingService>('LoadingService', ['show', 'hide']);

    endpointsSpy.obtenerTesoreriaResumen.and.returnValue(
      of({
        mes: 1,
        ano: 2026,
        deporte: 'TODOS',
        totalMovimientos: 1,
        totalPagados: 1,
        totalPendientes: 0,
        importeTotal: 35,
        importePagado: 35,
        importePendiente: 0,
        alumnosConPendientes: 0,
      } as any)
    );
    endpointsSpy.obtenerTesoreriaMovimientos.and.returnValue(
      of({
        content: [
          {
            productoAlumnoId: 10,
            alumnoId: 1,
            alumnoNombreCompleto: 'Alumno Test',
            deporte: 'TAEKWONDO',
            concepto: 'MENSUALIDAD ENERO',
            categoria: 'MENSUALIDAD',
            fechaAsignacion: '2026-01-01',
            pagado: true,
            fechaPago: '2026-01-02',
            precio: 35,
            notas: 'ok',
            alumnoActivo: true,
          },
        ],
        totalPages: 1,
        totalElements: 1,
        size: 25,
        number: 0,
      } as any)
    );
    endpointsSpy.obtenerTesoreriaAniosDisponibles.and.returnValue(of([2026]));
    endpointsSpy.actualizarCobroTesoreria.and.returnValue(of({} as any));
    endpointsSpy.exportarTesoreriaPDF.and.returnValue(of(new Blob()));
    endpointsSpy.exportarTesoreriaCSV.and.returnValue(of(new Blob()));

    authSpy.rolesEstanCargados.and.returnValue(true);
    authSpy.tieneRolAdmin.and.returnValue(false);
    authSpy.obtenerRoles.and.returnValue(of(['ROLE_MANAGER']));

    await TestBed.configureTestingModule({
      imports: [TesoreriaCobrosComponent],
      providers: [
        { provide: EndpointsService, useValue: endpointsSpy },
        { provide: AuthenticationService, useValue: authSpy },
        { provide: LoadingService, useValue: loadingSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TesoreriaCobrosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crear el componente', () => {
    expect(component).toBeTruthy();
  });

  it('aplica por defecto el filtro de solo alumnos activos', () => {
    expect(endpointsSpy.obtenerTesoreriaMovimientos).toHaveBeenCalledWith(
      component.filtroMes,
      component.filtroAno,
      component.filtroDeporte,
      false,
      '',
      true,
      1,
      25
    );
  });

  it('no muestra boton de revertir para usuario no admin', () => {
    fixture.detectChanges();
    const botonesRevertir = fixture.nativeElement.querySelectorAll('.btn-revertir');
    expect(botonesRevertir.length).toBe(0);
  });

  it('muestra boton de revertir para admin', () => {
    component.isAdmin = true;
    fixture.detectChanges();
    const botonesRevertir = fixture.nativeElement.querySelectorAll('.btn-revertir');
    expect(botonesRevertir.length).toBe(1);
  });
});
