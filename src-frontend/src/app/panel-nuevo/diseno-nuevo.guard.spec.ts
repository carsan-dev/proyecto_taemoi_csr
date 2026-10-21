import { TestBed } from '@angular/core/testing';
import { Route, UrlSegment } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of, throwError } from 'rxjs';

import { PermisosSesion, PermisosSesionService } from '../servicios/authentication/permisos-sesion.service';
import { EstilosPanelService } from './estilos-panel.service';
import { disenoNuevoGuard } from './diseno-nuevo.guard';

function permisos(disenoNuevo?: PermisosSesion['disenoNuevo']): PermisosSesion {
  return {
    roles: ['PROPIETARIO'],
    esAdminPlataforma: false,
    sedes: [],
    variasSedes: false,
    tieneGrupos: false,
    capacidades: [],
    ...(disenoNuevo ? { disenoNuevo } : {}),
  };
}

describe('disenoNuevoGuard (8b-I): falla cerrado', () => {
  let obtener: jasmine.Spy<() => Observable<PermisosSesion>>;
  let cargar: jasmine.Spy<() => Promise<void>>;

  beforeEach(() => {
    obtener = jasmine.createSpy('obtener');
    cargar = jasmine.createSpy('cargar').and.resolveTo();
    TestBed.configureTestingModule({
      providers: [
        { provide: PermisosSesionService, useValue: { obtener } },
        { provide: EstilosPanelService, useValue: { cargar } },
      ],
    });
  });

  async function decidir(): Promise<boolean> {
    const resultado = TestBed.runInInjectionContext(() =>
      disenoNuevoGuard({ path: 'x' } as Route, [new UrlSegment('x', {})])
    );
    if (isObservable(resultado)) {
      return (await firstValueFrom(resultado)) as boolean;
    }
    return (await resultado) as boolean;
  }

  it('encendido: true solo después de cargar la hoja del panel nuevo', async () => {
    let resolverHoja!: () => void;
    cargar.and.returnValue(new Promise<void>((r) => (resolverHoja = r)));
    obtener.and.returnValue(of(permisos({ activo: true, permitido: true })));

    let decidido: boolean | undefined;
    const promesa = decidir().then((v) => (decidido = v));
    await Promise.resolve();
    expect(cargar).toHaveBeenCalledTimes(1);
    expect(decidido).toBeUndefined();

    resolverHoja();
    await promesa;
    expect(decidido).toBeTrue();
  });

  it('apagado: false y no carga la hoja', async () => {
    obtener.and.returnValue(of(permisos({ activo: false, permitido: true })));
    expect(await decidir()).toBeFalse();
    expect(cargar).not.toHaveBeenCalled();
  });

  it('sin disenoNuevo en la respuesta (servidor anterior): false', async () => {
    obtener.and.returnValue(of(permisos()));
    expect(await decidir()).toBeFalse();
    expect(cargar).not.toHaveBeenCalled();
  });

  it('error de permisos (401, 500…): false', async () => {
    obtener.and.returnValue(throwError(() => ({ status: 500 })));
    expect(await decidir()).toBeFalse();
    expect(cargar).not.toHaveBeenCalled();
  });

  it('error de la hoja: false (pantalla de hoy, sin pantalla a medias)', async () => {
    obtener.and.returnValue(of(permisos({ activo: true, permitido: true })));
    cargar.and.rejectWith(new Error('No se pudo cargar panel-nuevo.css'));
    expect(await decidir()).toBeFalse();
  });
});
