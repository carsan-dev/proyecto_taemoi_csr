import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { Toast } from '../../../utils/toast.util';
import { RegistroConfirmarComponent } from './registro-confirmar.component';

/**
 * El token del enlace de confirmar el registro sale de la barra al abrir la página: los eventos automáticos de
 * Google Analytics usan la URL del documento y no deben llevarlo.
 */
describe('RegistroConfirmarComponent · token fuera de la barra', () => {
  let auth: jasmine.SpyObj<AuthenticationService>;
  let location: jasmine.SpyObj<Location>;
  let router: jasmine.SpyObj<Router>;
  let route: { snapshot: { queryParamMap: ReturnType<typeof convertToParamMap> } };

  function montar(token: string | null): RegistroConfirmarComponent {
    auth = jasmine.createSpyObj<AuthenticationService>('AuthenticationService', ['confirmarRegistro']);
    auth.confirmarRegistro.and.returnValue(of(undefined) as never);
    location = jasmine.createSpyObj<Location>('Location', ['replaceState']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
    route = { snapshot: { queryParamMap: convertToParamMap(token === null ? {} : { token }) } };
    TestBed.configureTestingModule({
      imports: [RegistroConfirmarComponent],
      providers: [
        { provide: AuthenticationService, useValue: auth },
        { provide: Location, useValue: location },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: route },
      ],
    });
    spyOn(Toast, 'fire').and.resolveTo({} as never);
    const fixture = TestBed.createComponent(RegistroConfirmarComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('confirma con el token leído y lo quita de la barra y del estado del router', () => {
    const componente = montar('tok-789');
    expect(auth.confirmarRegistro).toHaveBeenCalledWith('tok-789');
    expect(componente.estado).toBe('success');
    expect(location.replaceState).toHaveBeenCalledWith('/registro-confirmar');
    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({ relativeTo: route, queryParams: {}, replaceUrl: true })
    );
  });

  it('sin token no llama a la API, limpia la barra igual y queda en «invalid»', () => {
    const componente = montar(null);
    expect(auth.confirmarRegistro).not.toHaveBeenCalled();
    expect(componente.estado).toBe('invalid');
    expect(location.replaceState).toHaveBeenCalledWith('/registro-confirmar');
  });
});
