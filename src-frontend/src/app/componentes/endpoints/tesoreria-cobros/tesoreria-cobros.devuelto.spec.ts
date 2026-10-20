import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { CatalogoService } from '../../../features/catalogo/catalogo.service';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { SolicitudesService } from '../../../servicios/endpoints/solicitudes.service';
import { LoadingService } from '../../../servicios/generales/loading.service';
import { TesoreriaCobrosComponent } from './tesoreria-cobros.component';

/** Pieza 6c-9 (D6c-12): el cargo devuelto se marca en su fila y el resumen dice cuánto se ha devuelto. */
describe('TesoreriaCobrosComponent: devuelto', () => {
  let fixture: ComponentFixture<TesoreriaCobrosComponent>;

  const fila = (cambios: Record<string, unknown>) => ({
    productoAlumnoId: 10, alumnoId: 1, alumnoNombreCompleto: 'Alumno Test', deporte: 'TAEKWONDO',
    concepto: 'DOBOK T-160', categoria: 'OTRO', fechaAsignacion: '2026-10-01', pagado: true,
    fechaPago: '2026-10-02', precio: 25, notas: null, alumnoActivo: true, ...cambios,
  });

  async function montar(admin: boolean, importeDevuelto: number | undefined): Promise<HTMLElement> {
    const endpoints = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'obtenerTesoreriaMovimientos', 'obtenerTesoreriaResumen', 'obtenerTesoreriaAniosDisponibles',
    ]);
    endpoints.obtenerTesoreriaResumen.and.returnValue(
      of({ mes: 10, ano: 2026, importePagado: 60, totalPagados: 2, importeDevuelto } as any)
    );
    endpoints.obtenerTesoreriaMovimientos.and.returnValue(
      of({
        content: [fila({ devuelto: true }), fila({ productoAlumnoId: 11, concepto: 'CAMISETA', precio: 35 })],
        totalPages: 1, totalElements: 2, size: 25, number: 0,
      } as any)
    );
    endpoints.obtenerTesoreriaAniosDisponibles.and.returnValue(of([2026]));

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
        { provide: SolicitudesService, useValue: jasmine.createSpyObj('SolicitudesService', ['crear']) },
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TesoreriaCobrosComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => fixture?.destroy());

  const filas = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLElement>('tbody tr'));
  const textos = (tr: HTMLElement, selector: string) =>
    Array.from(tr.querySelectorAll(selector)).map((n) => n.textContent?.replace(/\s+/g, ' ').trim());

  it('la fila devuelta lleva la marca y no ofrece revertir ni pedirlo; las demás, como siempre', async () => {
    for (const admin of [true, false]) {
      const el = await montar(admin, 25);
      const [devuelta, normal] = filas(el);
      expect(textos(devuelta, '.estado-chip')).toEqual(['Pagado', 'Devuelto']);
      expect(devuelta.querySelector('.btn-revertir, .btn-pedir')).toBeNull();
      expect(textos(normal, '.estado-chip')).toEqual(['Pagado']);
      expect(normal.querySelector(admin ? '.btn-revertir' : '.btn-pedir')).not.toBeNull();
    }
  });

  it('el resumen dice cuánto se ha devuelto; sin devoluciones (Moiskimdo), nada nuevo', async () => {
    let el = await montar(true, 25);
    const cobrado = el.querySelector('.resumen-card.pagados')!.textContent!.replace(/\s+/g, ' ');
    expect(cobrado).toMatch(/25[.,]00 EUR devueltos/);

    el = await montar(true, 0);
    expect(el.querySelector('.resumen-card.pagados .detalle-devuelto')).toBeNull();
    el = await montar(true, undefined);
    expect(el.querySelector('.resumen-card.pagados .detalle-devuelto')).toBeNull();
  });
});
