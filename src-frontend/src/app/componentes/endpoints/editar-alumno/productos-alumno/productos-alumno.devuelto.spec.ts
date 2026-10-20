import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import type { ProductoAlumnoDTO } from '../../../../interfaces/producto-alumno-dto';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { SolicitudesService } from '../../../../servicios/endpoints/solicitudes.service';
import { ProductosAlumnoComponent } from './productos-alumno.component';

/** Pieza 6c-9 (153): en la ficha, el cargo devuelto se ve como devuelto y no se desmarca. */
describe('ProductosAlumnoComponent: devuelto', () => {
  const base: ProductoAlumnoDTO = {
    id: 1, productoId: 20, alumnoId: 10, concepto: 'DOBOK T-160', fechaAsignacion: new Date('2026-10-01T10:00:00Z'),
    fechaPago: new Date('2026-10-02T10:00:00Z'), cantidad: 1, precio: 25, pagado: true, notas: '',
  };

  async function montar(productos: ProductoAlumnoDTO[]): Promise<HTMLElement> {
    const endpoints = jasmine.createSpyObj('EndpointsService', ['obtenerProductosDelAlumno']);
    endpoints.obtenerProductosDelAlumno.and.returnValue(of(productos.map((p) => ({ ...p }))));
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ProductosAlumnoComponent],
      providers: [
        { provide: ActivatedRoute, useValue: {} },
        { provide: EndpointsService, useValue: endpoints },
        { provide: Location, useValue: {} },
        { provide: AuthenticationService, useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => true } },
        { provide: SolicitudesService, useValue: jasmine.createSpyObj('SolicitudesService', ['crear']) },
      ],
    }).compileComponents();
    spyOn(ProductosAlumnoComponent.prototype, 'ngOnInit').and.callFake(function (this: ProductosAlumnoComponent) {
      this.resolverPermisos();
    });
    const fixture = TestBed.createComponent(ProductosAlumnoComponent);
    fixture.componentInstance.alumnoId = 10;
    fixture.componentInstance.obtenerProductosAlumno(10);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('el cargo devuelto lleva la marca en lugar de la casilla de pagado; el resto, como siempre', async () => {
    // Los productos se pintan del más reciente al más antiguo (la lista llega invertida).
    const el = await montar([{ ...base, id: 2, concepto: 'CAMISETA' }, { ...base, devuelto: true }]);
    const [devuelto, normal] = Array.from(el.querySelectorAll<HTMLElement>('tbody tr'));

    expect(devuelto.textContent).toContain('DOBOK T-160');
    expect(devuelto.querySelector('.chip-devuelto')?.textContent?.trim()).toBe('devuelto');
    expect(devuelto.querySelector('input[type="checkbox"]')).toBeNull();
    expect(normal.querySelector('.chip-devuelto')).toBeNull();
    expect(normal.querySelector<HTMLInputElement>('input[type="checkbox"]')?.checked).toBeTrue();
  });
});
