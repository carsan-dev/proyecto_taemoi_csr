import { TestBed } from '@angular/core/testing';
import { Route, UrlSegment } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of, throwError } from 'rxjs';

import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { gestionPanelGuard } from './gestion-panel.guard';

function permisos(roles: string[]): PermisosSesion {
  return { roles, esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false, capacidades: [] };
}

describe('gestionPanelGuard (8b-M2b): falla cerrado', () => {
  let obtener: jasmine.Spy<() => Observable<PermisosSesion>>;

  beforeEach(() => {
    obtener = jasmine.createSpy('obtener');
    TestBed.configureTestingModule({ providers: [{ provide: PermisosSesionService, useValue: { obtener } }] });
  });

  async function decidir(): Promise<boolean> {
    const r = TestBed.runInInjectionContext(() => gestionPanelGuard({ path: '**' } as Route, [new UrlSegment('x', {})]));
    return isObservable(r) ? firstValueFrom(r as Observable<boolean>) : (r as boolean);
  }

  it('responsable de sede (PANEL_SEDE): casa', async () => {
    obtener.and.returnValue(of({ ...permisos(['RESPONSABLE_SEDE']), capacidades: ['PANEL_SEDE'] }));
    expect(await decidir()).toBeTrue();
  });

  it('el rol sin la capacidad no casa', async () => {
    obtener.and.returnValue(of({ ...permisos(['RESPONSABLE_SEDE']), capacidades: [] }));
    expect(await decidir()).toBeFalse();
  });

  for (const rol of ['PROPIETARIO', 'ADMINISTRADOR']) {
    it(`${rol}: casa`, async () => {
      obtener.and.returnValue(of(permisos([rol])));
      expect(await decidir()).toBeTrue();
    });
  }

  for (const roles of [[], ['PROFESOR'], ['FAMILIA'], ['ROLE_USER']]) {
    it(`${JSON.stringify(roles)}: no casa`, async () => {
      obtener.and.returnValue(of(permisos(roles)));
      expect(await decidir()).toBeFalse();
    });
  }

  it('error de permisos (visitante sin sesión): no casa', async () => {
    obtener.and.returnValue(throwError(() => ({ status: 401 })));
    expect(await decidir()).toBeFalse();
  });
});
