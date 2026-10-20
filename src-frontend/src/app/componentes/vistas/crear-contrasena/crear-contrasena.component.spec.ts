import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Location } from '@angular/common';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { Toast } from '../../../utils/toast.util';
import { CrearContrasenaComponent } from './crear-contrasena.component';

describe('CrearContrasenaComponent', () => {
  let fixture: ComponentFixture<CrearContrasenaComponent>;
  let auth: jasmine.SpyObj<AuthenticationService>;
  let location: jasmine.SpyObj<Location>;
  let router: jasmine.SpyObj<Router>;
  const datos = { email: 'lucia@ejemplo.test', nombre: 'Lucía', escuela: 'Escuela de Prueba' };
  const caducado = 'El enlace ha caducado o ya se usó. Pide que te lo reenvíen.';

  function error(status: number, mensaje: string): Observable<never> {
    return throwError(() => new HttpErrorResponse({ status, error: { mensaje } }));
  }

  function montar(token: string | null = 'tok-123', respuestaDatos: Observable<unknown> = of(datos)): void {
    auth = jasmine.createSpyObj<AuthenticationService>('AuthenticationService', [
      'datosInvitacion',
      'aceptarInvitacion',
      'resolverRutaInicioPorRoles',
    ]);
    auth.datosInvitacion.and.returnValue(respuestaDatos as never);
    auth.resolverRutaInicioPorRoles.and.returnValue('/mis-grupos');
    location = jasmine.createSpyObj<Location>('Location', ['replaceState']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
    TestBed.configureTestingModule({
      imports: [CrearContrasenaComponent],
      providers: [
        { provide: AuthenticationService, useValue: auth },
        { provide: Location, useValue: location },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token === null ? {} : { token }) } },
        },
      ],
    });
    spyOn(Toast, 'fire').and.resolveTo({} as never);
    fixture = TestBed.createComponent(CrearContrasenaComponent);
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function escribir(selector: string, valor: string): Promise<void> {
    await fixture.whenStable();
    const input = el().querySelector<HTMLInputElement>(selector)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  async function rellenar(contrasena = 'Segura123', repetida = contrasena): Promise<void> {
    await escribir('#nuevaContrasena', contrasena);
    await escribir('#confirmarContrasena', repetida);
  }

  function boton(): HTMLButtonElement {
    return el().querySelector<HTMLButtonElement>('button[type="submit"]')!;
  }

  function enviar(): void {
    el().querySelector<HTMLFormElement>('form.auth-form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function metaReferrer(): HTMLMetaElement | null {
    return document.head.querySelector<HTMLMetaElement>('meta[name="referrer"]');
  }

  afterEach(() => metaReferrer()?.remove());

  it('quita el token de la barra y del estado del router, y pide los datos con él', () => {
    montar();
    expect(location.replaceState).toHaveBeenCalledWith('/crear-contrasena');
    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({ queryParams: {}, replaceUrl: true })
    );
    expect(auth.datosInvitacion).toHaveBeenCalledWith('tok-123');
  });

  it('pone el meta referrer no-referrer mientras dura la página y lo quita al salir', () => {
    montar();
    expect(metaReferrer()?.content).toBe('no-referrer');
    fixture.destroy();
    expect(metaReferrer()).toBeNull();
  });

  it('con datos vigentes saluda y enseña email y escuela', () => {
    montar();
    expect(el().querySelector('h1')!.textContent).toContain('Crea tu contraseña');
    expect(el().querySelector('.invitacion-saludo')!.textContent).toContain('Hola, Lucía.');
    expect(el().querySelector('.invitacion-meta')!.textContent).toContain('lucia@ejemplo.test · Escuela de Prueba');
    expect(boton().textContent!.trim()).toBe('Crear contraseña y entrar');
  });

  it('enlace caducado: el mensaje y sin formulario', () => {
    montar('tok-123', error(400, caducado));
    expect(el().querySelector('.auth-error')!.textContent).toContain(caducado);
    expect(el().querySelector('form')).toBeNull();
  });

  it('un fallo de red o del servidor al comprobar el enlace ofrece reintentar, no lo da por caducado', () => {
    montar('tok-123', error(0, ''));
    expect(el().querySelector('.auth-error')!.textContent).toContain('No se pudo comprobar el enlace. Inténtalo de nuevo.');
    expect(el().textContent).not.toContain(caducado);
    expect(el().querySelector('form')).toBeNull();
    auth.datosInvitacion.and.returnValue(error(500, 'boom'));
    el().querySelector<HTMLButtonElement>('.invitacion-reintentar')!.click();
    fixture.detectChanges();
    expect(auth.datosInvitacion).toHaveBeenCalledTimes(2);
    expect(el().querySelector('.auth-error')!.textContent).toContain('No se pudo comprobar el enlace.');
    auth.datosInvitacion.and.returnValue(of(datos));
    el().querySelector<HTMLButtonElement>('.invitacion-reintentar')!.click();
    fixture.detectChanges();
    expect(auth.datosInvitacion).toHaveBeenCalledTimes(3);
    expect(auth.datosInvitacion.calls.mostRecent().args).toEqual(['tok-123']);
    expect(el().querySelector('form')).not.toBeNull();
  });

  it('sin token: el mensaje de caducado sin pedir nada', () => {
    montar(null);
    expect(auth.datosInvitacion).not.toHaveBeenCalled();
    expect(el().querySelector('.auth-error')!.textContent).toContain(caducado);
    expect(el().querySelector('form')).toBeNull();
  });

  it('contraseñas distintas o débiles bloquean el envío', async () => {
    montar();
    await rellenar('Segura123', 'Segura124');
    expect(boton().disabled).toBeTrue();
    enviar();
    expect(auth.aceptarInvitacion).not.toHaveBeenCalled();
    await rellenar('debil', 'debil');
    expect(boton().disabled).toBeTrue();
    await rellenar('Segura123', 'Segura123');
    expect(boton().disabled).toBeFalse();
  });

  it('aceptar con éxito navega donde lo haría el login', async () => {
    montar();
    auth.aceptarInvitacion.and.returnValue(of(['ROLE_PROFESOR']));
    await rellenar();
    enviar();
    expect(auth.aceptarInvitacion).toHaveBeenCalledWith('tok-123', 'Segura123', 'lucia@ejemplo.test');
    expect(auth.resolverRutaInicioPorRoles).toHaveBeenCalledWith(['ROLE_PROFESOR']);
    expect(router.navigate).toHaveBeenCalledWith(['/mis-grupos']);
  });

  it('deshabilita el botón mientras va y no envía dos veces', async () => {
    montar();
    const respuesta = new Subject<string[]>();
    auth.aceptarInvitacion.and.returnValue(respuesta);
    await rellenar();
    enviar();
    enviar();
    expect(auth.aceptarInvitacion).toHaveBeenCalledTimes(1);
    expect(boton().disabled).toBeTrue();
    respuesta.next(['ROLE_PROFESOR']);
    respuesta.complete();
  });

  it('un 400 al aceptar enseña el mensaje de caducado sin formulario', async () => {
    montar();
    auth.aceptarInvitacion.and.returnValue(error(400, 'La contrasena debe contener mayusculas, minusculas y numeros'));
    await rellenar();
    enviar();
    expect(el().querySelector('.auth-error')!.textContent).toContain(caducado);
    expect(el().querySelector('form')).toBeNull();
    expect(router.navigate).not.toHaveBeenCalledWith(['/mis-grupos']);
  });

  it('un 403 al aceptar enseña el mensaje del backend y lleva al login', async () => {
    montar();
    const motivo = 'Tu rol en esta escuela todavía no tiene acceso a la aplicación.';
    auth.aceptarInvitacion.and.returnValue(error(403, motivo));
    await rellenar();
    enviar();
    expect(el().querySelector('.auth-error')!.textContent).toContain(motivo);
    expect(el().querySelector('form')).toBeNull();
    el().querySelector<HTMLButtonElement>('.auth-footer button')!.click();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
