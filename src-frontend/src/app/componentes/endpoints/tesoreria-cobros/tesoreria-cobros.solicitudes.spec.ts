import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { CatalogoService } from '../../../features/catalogo/catalogo.service';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { SolicitudesService } from '../../../servicios/endpoints/solicitudes.service';
import { LoadingService } from '../../../servicios/generales/loading.service';
import { Toast } from '../../../utils/toast.util';
import { TesoreriaCobrosComponent } from './tesoreria-cobros.component';

/** Pieza 6c-7 (decisión 190): en tesorería, la gestión pide al propietario lo que antes le daba 403. */
describe('TesoreriaCobrosComponent: «Pedir al propietario»', () => {
  let fixture: ComponentFixture<TesoreriaCobrosComponent>;
  let solicitudes: jasmine.SpyObj<SolicitudesService>;

  const pagado = {
    productoAlumnoId: 10, alumnoId: 1, alumnoNombreCompleto: 'Alumno Test', deporte: 'TAEKWONDO',
    concepto: 'MENSUALIDAD ENERO', categoria: 'MENSUALIDAD', fechaAsignacion: '2026-01-01', pagado: true,
    fechaPago: '2026-01-02', precio: 35, notas: null, alumnoActivo: true,
  };

  async function montar(admin: boolean): Promise<HTMLElement> {
    const endpoints = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'obtenerTesoreriaMovimientos', 'obtenerTesoreriaResumen', 'obtenerTesoreriaAniosDisponibles',
      'actualizarCobroTesoreria',
    ]);
    endpoints.obtenerTesoreriaResumen.and.returnValue(of({ mes: 1, ano: 2026 } as any));
    endpoints.obtenerTesoreriaMovimientos.and.returnValue(
      of({ content: [pagado], totalPages: 1, totalElements: 1, size: 25, number: 0 } as any)
    );
    endpoints.obtenerTesoreriaAniosDisponibles.and.returnValue(of([2026]));
    solicitudes = jasmine.createSpyObj<SolicitudesService>('SolicitudesService', ['crear']);
    solicitudes.crear.and.returnValue(of({ id: 1 } as any));

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [TesoreriaCobrosComponent],
      providers: [
        { provide: EndpointsService, useValue: endpoints },
        {
          provide: AuthenticationService,
          useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => admin, obtenerRoles: () => of([]) },
        },
        { provide: LoadingService, useValue: { show: () => undefined, hide: () => undefined } },
        { provide: CatalogoService, useValue: { disciplinasActivas: () => [], etiquetaDisciplina: (c: string) => c } },
        { provide: SolicitudesService, useValue: solicitudes },
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TesoreriaCobrosComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('la gestión ve «Pedir al propietario» y no «Revertir»; el propietario, al revés', async () => {
    let el = await montar(false);
    expect(el.querySelectorAll('.btn-revertir').length).toBe(0);
    expect(el.querySelector('.btn-pedir')?.textContent?.trim()).toBe('Pedir al propietario');

    el = await montar(true);
    expect(el.querySelectorAll('.btn-revertir').length).toBe(1);
    expect(el.querySelector('.btn-pedir')).toBeNull();
  });

  it('pide revertir por el cargo, con los datos del cobro y el motivo', async () => {
    await montar(false);
    spyOn(Toast, 'fire').and.returnValue(Promise.resolve({} as any));
    const swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true, value: 'cobrado dos veces' } as any));

    await fixture.componentInstance.pedirRevertirCobro(pagado as any);

    const config = swal.calls.mostRecent().args[0] as any;
    expect(config.title).toBe('Pedir al propietario que revierta este cobro');
    expect(config.html).toContain('Alumno Test');
    expect(config.html).toContain('MENSUALIDAD ENERO');
    expect(config.html).toContain('Mientras tanto el pago sigue marcado.');
    expect(solicitudes.crear).toHaveBeenCalledWith({
      tipo: 'REVERTIR_COBRO', productoAlumnoId: 10, motivo: 'cobrado dos veces',
    });
  });
});
