import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';

import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { elegirEscuelaGuard } from './elegir-escuela.guard';
import { escuela } from './escuelas-prueba';
import { EscuelaUsuario, SelectorEscuelaService } from './selector-escuela.service';
import { RUTAS_ELEGIR_ESCUELA } from './selector-escuela.routes';

describe('elegirEscuelaGuard («Tras entrar» solo si toca)', () => {
  function ejecutar(escuelas: Observable<EscuelaUsuario[]>): Promise<boolean | UrlTree> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SelectorEscuelaService, useValue: { organizaciones: () => escuelas } },
        { provide: AuthenticationService, useValue: { resolverRutaInicio: () => of('/adminpage') } },
      ],
    });
    const resultado = TestBed.runInInjectionContext(() =>
      elegirEscuelaGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    return firstValueFrom(resultado as Observable<boolean | UrlTree>);
  }

  const url = (resultado: boolean | UrlTree): string =>
    resultado instanceof UrlTree ? TestBed.inject(Router).serializeUrl(resultado) : String(resultado);

  it('con dos escuelas o más (y alguna que no sea solo de familia) activa la pantalla', async () => {
    expect(await ejecutar(of([escuela({ id: 1, activa: true }), escuela({ id: 2 })]))).toBeTrue();
  });

  it('con una sola escuela no la activa: al destino de siempre', async () => {
    expect(url(await ejecutar(of([escuela({ activa: true })])))).toBe('/adminpage');
  });

  it('con escuelas solo de familia tampoco', async () => {
    const familia = [escuela({ id: 1, roles: ['ROLE_USER'] }), escuela({ id: 2, roles: ['ROLE_USER'] })];
    expect(url(await ejecutar(of(familia)))).toBe('/adminpage');
  });

  it('si no se pueden leer las escuelas, al destino de siempre', async () => {
    expect(url(await ejecutar(throwError(() => new Error('503'))))).toBe('/adminpage');
  });

  it('va en la ruta de la pantalla, antes de activarla', () => {
    expect(RUTAS_ELEGIR_ESCUELA[0].canActivate).toEqual([elegirEscuelaGuard]);
  });
});
