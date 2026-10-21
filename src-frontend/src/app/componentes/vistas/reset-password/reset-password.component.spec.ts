import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { Toast } from '../../../utils/toast.util';
import { ResetPasswordComponent } from './reset-password.component';

/**
 * El token del enlace de restablecer la contraseña sale de la barra al abrir la página: los eventos automáticos de
 * Google Analytics (formularios, scroll) usan la URL del documento y no deben llevarlo.
 */
describe('ResetPasswordComponent · token fuera de la barra', () => {
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let auth: jasmine.SpyObj<AuthenticationService>;
  let location: jasmine.SpyObj<Location>;
  let router: jasmine.SpyObj<Router>;
  let route: { snapshot: { queryParamMap: ReturnType<typeof convertToParamMap> } };

  function montar(token: string | null): void {
    auth = jasmine.createSpyObj<AuthenticationService>('AuthenticationService', ['resetearContrasena']);
    auth.resetearContrasena.and.returnValue(of(undefined));
    location = jasmine.createSpyObj<Location>('Location', ['replaceState']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
    route = { snapshot: { queryParamMap: convertToParamMap(token === null ? {} : { token }) } };
    TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        { provide: AuthenticationService, useValue: auth },
        { provide: Location, useValue: location },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: route },
      ],
    });
    spyOn(Toast, 'fire').and.resolveTo({} as never);
    fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.detectChanges();
  }

  it('lee el token y lo quita de la barra y del estado del router', () => {
    montar('tok-123');
    expect(fixture.componentInstance.token).toBe('tok-123');
    expect(location.replaceState).toHaveBeenCalledWith('/reset-password');
    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({ relativeTo: route, queryParams: {}, replaceUrl: true })
    );
  });

  it('sigue usando el token leído al enviar la nueva contraseña', () => {
    montar('tok-123');
    const componente = fixture.componentInstance;
    componente.nuevaContrasena = 'Segura123';
    componente.confirmarContrasena = 'Segura123';
    componente.actualizarContrasena();
    expect(auth.resetearContrasena).toHaveBeenCalledWith('tok-123', 'Segura123');
  });

  it('sin token no rompe: limpia la barra igual y no deja enviar', () => {
    montar(null);
    expect(fixture.componentInstance.token).toBeNull();
    expect(location.replaceState).toHaveBeenCalledWith('/reset-password');
    expect(fixture.componentInstance.puedeEnviar).toBeFalse();
  });

  it('sin token (por ejemplo, al recargar) pide abrir de nuevo el enlace del correo', () => {
    montar(null);
    const aviso = (fixture.nativeElement as HTMLElement).querySelector('.auth-error');
    expect(aviso?.textContent?.trim()).toBe('Abre de nuevo el enlace del correo. Si ha caducado, pide otro.');
  });

  it('sin token, debajo del aviso, enlaza a pedir un enlace nuevo en «Olvidé mi contraseña»', () => {
    montar(null);
    const raiz = fixture.nativeElement as HTMLElement;
    const enlace = raiz.querySelector<HTMLAnchorElement>('.auth-error + .auth-nuevo-enlace a');
    expect(enlace?.textContent?.trim()).toBe('Pedir un enlace nuevo');
    expect(enlace?.getAttribute('href')).toBe('/recuperar-contrasena');
  });

  it('con token no enseña el aviso', () => {
    montar('tok-123');
    expect((fixture.nativeElement as HTMLElement).querySelector('.auth-error')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('.auth-nuevo-enlace')).toBeNull();
  });
});
