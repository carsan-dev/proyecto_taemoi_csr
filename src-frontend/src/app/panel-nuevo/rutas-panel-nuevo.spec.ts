import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Observable, of, throwError } from 'rxjs';

import { PermisosSesion, PermisosSesionService } from '../servicios/authentication/permisos-sesion.service';
import { EstilosPanelService } from './estilos-panel.service';
import { disenoNuevoGuard } from './diseno-nuevo.guard';

@Component({ standalone: true, template: '<p class="hoy">Pantalla de hoy</p>' })
class PantallaDeHoy {}

@Component({ standalone: true, template: '<p class="nueva">Pantalla nueva</p>' })
class PantallaNueva {}

/**
 * Contrato del enrutado (fase3-e8b-plan.md §3, punto de revisión 1): cada pantalla rediseñada tiene dos entradas con la
 * misma ruta, primero la nueva con `canMatch: [disenoNuevoGuard]` y `loadComponent`, después la de hoy. Con el guard en
 * false el router pasa a la de hoy sin llamar al `loadComponent` nuevo: no se descarga ningún chunk de panel-nuevo.
 */
describe('Rutas del panel nuevo con disenoNuevoGuard (8b-I)', () => {
  let cargarNueva: jasmine.Spy;
  let obtener: jasmine.Spy<() => Observable<PermisosSesion>>;
  let cargarHoja: jasmine.Spy<() => Promise<void>>;

  function permisos(activo: boolean): PermisosSesion {
    return {
      roles: ['ADMINISTRADOR'],
      esAdminPlataforma: false,
      sedes: [],
      variasSedes: false,
      tieneGrupos: false,
      capacidades: [],
      disenoNuevo: { activo, permitido: activo },
    };
  }

  beforeEach(() => {
    cargarNueva = jasmine.createSpy('loadComponent nuevo').and.resolveTo(PantallaNueva);
    obtener = jasmine.createSpy('obtener');
    cargarHoja = jasmine.createSpy('cargar').and.resolveTo();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'alumnosListar', canMatch: [disenoNuevoGuard], loadComponent: cargarNueva },
          { path: 'alumnosListar', component: PantallaDeHoy },
        ]),
        { provide: PermisosSesionService, useValue: { obtener } },
        { provide: EstilosPanelService, useValue: { cargar: cargarHoja } },
      ],
    });
  });

  async function navegar(): Promise<HTMLElement> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/alumnosListar');
    return harness.routeNativeElement as HTMLElement;
  }

  it('apagado: la pantalla de hoy y ni se llama al loadComponent nuevo', async () => {
    obtener.and.returnValue(of(permisos(false)));
    const el = await navegar();
    expect(el.querySelector('.hoy')).not.toBeNull();
    expect(cargarNueva).not.toHaveBeenCalled();
    expect(cargarHoja).not.toHaveBeenCalled();
  });

  it('error de permisos: la pantalla de hoy y sin descargar la nueva', async () => {
    obtener.and.returnValue(throwError(() => ({ status: 500 })));
    const el = await navegar();
    expect(el.querySelector('.hoy')).not.toBeNull();
    expect(cargarNueva).not.toHaveBeenCalled();
  });

  it('error de la hoja: la pantalla de hoy y sin descargar la nueva', async () => {
    obtener.and.returnValue(of(permisos(true)));
    cargarHoja.and.rejectWith(new Error('sin hoja'));
    const el = await navegar();
    expect(el.querySelector('.hoy')).not.toBeNull();
    expect(cargarNueva).not.toHaveBeenCalled();
  });

  it('encendido: la nueva, tras cargar la hoja', async () => {
    obtener.and.returnValue(of(permisos(true)));
    const el = await navegar();
    expect(cargarHoja).toHaveBeenCalledTimes(1);
    expect(cargarNueva).toHaveBeenCalledTimes(1);
    expect(el.querySelector('.nueva')).not.toBeNull();
    expect(TestBed.inject(Router).url).toBe('/alumnosListar');
  });
});
