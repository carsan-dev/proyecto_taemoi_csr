import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { ProductoAlumnoDTO } from '../../interfaces/producto-alumno-dto';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { ProductosAlumnoPanelComponent } from './productos-alumno-panel.component';
import { salirSinGuardarGuard } from './salir-sin-guardar.guard';

const API = environment.apiUrl;

@Component({ standalone: true, template: '<p class="ficha">Ficha</p>' })
class FichaFalsa {}

function cargo(id: number): ProductoAlumnoDTO {
  return {
    id,
    productoId: 1,
    alumnoId: 7,
    concepto: `Cargo ${id}`,
    fechaAsignacion: new Date(2026, 9, 1, 18, 30).getTime() as unknown as Date,
    cantidad: 1,
    precio: 30,
    pagado: false,
    fechaPago: null,
    notas: '',
  };
}

const ALUMNO = { id: 7, nombre: 'Lucía', apellidos: 'Ortega', direccion: 'C/ Mayor 1', activo: true, deportes: [] };
const ANONIMIZADO = {
  id: 7,
  nombre: 'Alumno',
  apellidos: 'eliminado',
  direccion: 'Sin datos',
  activo: false,
  deportes: [],
  alumnoEliminado: true,
};

describe('ProductosAlumnoPanelComponent (8b-3n)', () => {
  let http: HttpTestingController;
  let confirmar: jasmine.Spy;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    sessionStorage.clear();
    confirmar = jasmine.createSpy('confirmar').and.resolveTo({ confirmado: false, valor: null });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'alumnos/:id/productos', component: ProductosAlumnoPanelComponent, canDeactivate: [salirSinGuardarGuard] },
          { path: 'alumnosEditar/:id', component: FichaFalsa },
        ]),
        { provide: ConfirmacionService, useValue: { confirmar } },
        { provide: AvisosService, useValue: jasmine.createSpyObj('Avisos', ['exito', 'error', 'info']) },
        {
          provide: AuthenticationService,
          useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => true, obtenerRoles: () => of([]) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  async function abrir(alumno: unknown, status = 200): Promise<ProductosAlumnoPanelComponent> {
    const pantalla = await harness.navigateByUrl('/alumnos/7/productos', ProductosAlumnoPanelComponent);
    http.expectOne(`${API}/productos-alumno/alumno/7`).flush([cargo(1), cargo(2)]);
    http.expectOne(`${API}/productos/todos`).flush([{ id: 1, concepto: 'Mensualidad', precio: 30 }]);
    const peticion = http.expectOne(`${API}/alumnos/7`);
    if (status === 200) {
      peticion.flush(alumno as object);
    } else {
      peticion.flush({ mensaje: 'Alumno no encontrado con ID: 7' }, { status, statusText: 'Error' });
    }
    harness.detectChanges();
    return pantalla;
  }

  function el(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  it('pinta los cargos y «Volver a la ficha» lleva a la pestaña Pagos', async () => {
    await abrir(ALUMNO);
    expect(el().querySelectorAll('tbody tr').length).toBe(2);
    expect(el().textContent).toContain('Lucía Ortega');
    const volver = el().querySelector<HTMLAnchorElement>('header a')!;
    expect(volver.getAttribute('href')).toBe('/alumnosEditar/7?pestana=pagos');
  });

  it('decisiones 293 y 299: con `alumnoEliminado` pinta el mismo bloque de error que un alumno que no existe', async () => {
    await abrir(ANONIMIZADO);
    const anonimizado = el().querySelector('main')!.innerHTML;
    expect(el().querySelector('pn-bloque-error')).not.toBeNull();
    expect(el().textContent).toContain('No se han podido cargar los productos del alumno.');
    expect(el().textContent).not.toContain('eliminado');
    expect(el().querySelector('table')).toBeNull();
    await harness.navigateByUrl('/alumnosEditar/7', FichaFalsa);
    await abrir(null, 404);
    expect(el().querySelector('main')!.innerHTML).toBe(anonimizado);
  });

  it('sin cambios se sale sin preguntar', async () => {
    await abrir(ALUMNO);
    await harness.navigateByUrl('/alumnosEditar/7', FichaFalsa);
    expect(confirmar).not.toHaveBeenCalled();
  });

  it('con cambios pregunta al salir: «Seguir editando» se queda y «Salir sin guardar» sale', async () => {
    const pantalla = await abrir(ALUMNO);
    pantalla.estado.cambiarNotas(1, 'x');
    const router = TestBed.inject(Router);
    expect(await router.navigateByUrl('/alumnosEditar/7')).toBeFalse();
    expect(confirmar).toHaveBeenCalledTimes(1);
    expect(confirmar.calls.mostRecent().args[0].titulo).toBe('¿Salir sin guardar?');
    expect(router.url).toBe('/alumnos/7/productos');
    confirmar.and.resolveTo({ confirmado: true, valor: null });
    expect(await router.navigateByUrl('/alumnosEditar/7')).toBeTrue();
    expect(router.url).toBe('/alumnosEditar/7');
  });

  it('beforeunload: el aviso del navegador solo con cambios', async () => {
    // No se lanza el evento en window: Karma lo toma por una recarga de la página.
    const pantalla = await abrir(ALUMNO);
    const evento = () => ({ preventDefault: jasmine.createSpy('preventDefault'), returnValue: undefined as unknown });
    const sin = evento();
    pantalla.antesDeDescargar(sin as unknown as BeforeUnloadEvent);
    expect(sin.preventDefault).not.toHaveBeenCalled();
    pantalla.estado.cambiarNotas(1, 'x');
    const con = evento();
    pantalla.antesDeDescargar(con as unknown as BeforeUnloadEvent);
    expect(con.preventDefault).toHaveBeenCalled();
    expect(con.returnValue).toBe('');
    pantalla.estado.descartar();
  });
});
