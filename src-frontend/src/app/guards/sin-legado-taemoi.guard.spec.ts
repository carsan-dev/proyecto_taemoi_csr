import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of, throwError } from 'rxjs';

import { CatalogoService } from '../features/catalogo/catalogo.service';
import { Catalogo } from '../features/catalogo/catalogo.models';
import { sinLegadoTaemoiGuard } from './sin-legado-taemoi.guard';

describe('sinLegadoTaemoiGuard (W7)', () => {
  let router: jasmine.SpyObj<Router>;
  let cargar: jasmine.Spy<() => Observable<Catalogo>>;

  const ejecutar: CanActivateFn = (...parametros) => TestBed.runInInjectionContext(() => sinLegadoTaemoiGuard(...parametros));

  async function resultado(): Promise<unknown> {
    const r = ejecutar({} as never, {} as never);
    return isObservable(r) ? firstValueFrom(r) : r;
  }

  const catalogo = (legado: boolean) => ({ organizacion: { id: legado ? 1 : 2, comportamientoLegadoTaemoi: legado } }) as unknown as Catalogo;

  beforeEach(() => {
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    cargar = jasmine.createSpy('cargar');
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: router },
        { provide: CatalogoService, useValue: { cargar } },
      ],
    });
  });

  it('deja pasar a una escuela de la plantilla', async () => {
    cargar.and.returnValue(of(catalogo(false)));
    expect(await resultado()).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('Moiskimdo (legado) va a la portada, como adminOnlyGuard', async () => {
    cargar.and.returnValue(of(catalogo(true)));
    expect(await resultado()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('sin catálogo, falla cerrado', async () => {
    cargar.and.returnValue(throwError(() => new Error('500')));
    expect(await resultado()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });
});
