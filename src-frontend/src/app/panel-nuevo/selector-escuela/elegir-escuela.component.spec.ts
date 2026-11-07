import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { ElegirEscuelaComponent } from './elegir-escuela.component';
import { escuela } from './escuelas-prueba';
import { EscuelaUsuario, IR_A_URL, RUTA_ENTRADA_SESION } from './selector-escuela.service';

const URL_LISTA = `${environment.apiUrl}/auth/organizaciones`;
const URL_CAMBIO = `${environment.apiUrl}/auth/organizacion`;

describe('ElegirEscuelaComponent (tras entrar)', () => {
  let fixture: ComponentFixture<ElegirEscuelaComponent>;
  let http: HttpTestingController;
  let irA: jasmine.Spy;
  let navegar: jasmine.Spy;
  let logout: jasmine.Spy;
  let logueado: BehaviorSubject<boolean>;

  const NORTE = escuela({ id: 1, nombre: 'Escuela Ficticia Norte', roles: ['ROLE_ADMIN'] });
  const SUR = escuela({ id: 2, nombre: 'Club Ficticio Sur', activa: true, roles: ['ROLE_MANAGER'], numeroSedes: 6 });
  const ESTE = escuela({ id: 3, nombre: 'Escuela del Este', roles: ['ROLE_PROFESOR'] });

  async function montar(): Promise<void> {
    irA = jasmine.createSpy('irA');
    logout = jasmine.createSpy('logout');
    logueado = new BehaviorSubject(true);
    await TestBed.configureTestingModule({
      imports: [ElegirEscuelaComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthenticationService,
          useValue: {
            emailCambio: new BehaviorSubject('ana@example.test'),
            usernameCambio: new BehaviorSubject('ana'),
            usuarioLogueadoCambio: logueado,
            resolverRutaInicio: () => of('/adminpage'),
            logout,
          },
        },
        {
          provide: PermisosSesionService,
          useValue: { obtener: () => of({ nombreVisible: 'Ana López', roles: [], capacidades: [] }) },
        },
        { provide: IR_A_URL, useValue: irA },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navegar = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(ElegirEscuelaComponent);
    fixture.detectChanges();
  }

  function responder(escuelas: EscuelaUsuario[] = [NORTE, SUR, ESTE]): void {
    http.expectOne(URL_LISTA).flush(escuelas);
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const opciones = (): HTMLButtonElement[] => Array.from(el().querySelectorAll<HTMLButtonElement>('.pn-paso__opcion'));

  afterEach(() => http.verify());

  it('pregunta en qué escuela entrar, con la cuenta y cuántas escuelas tiene', async () => {
    await montar();
    responder();
    expect(el().querySelector('.pn')).not.toBeNull();
    expect(el().querySelector('h1')?.textContent?.trim()).toBe('¿En qué escuela quieres entrar?');
    expect(el().querySelector('.pn-paso__cuenta')?.textContent).toContain('Has entrado como Ana López');
    expect(el().querySelector('.pn-paso__intro')?.textContent?.trim()).toBe(
      'Tu cuenta está en 3 escuelas. Luego puedes cambiar desde el nombre de la escuela, arriba en el panel.'
    );
  });

  it('pone primero «La última vez» (la activa) y luego las demás en el orden de la API', async () => {
    await montar();
    responder();
    const lista = opciones();
    expect(lista.map((b) => b.querySelector('.pn-paso__nombre')?.textContent?.trim())).toEqual([
      'Club Ficticio Sur',
      'Escuela Ficticia Norte',
      'Escuela del Este',
    ]);
    expect(lista[0].querySelector('.pn-paso__ultima')?.textContent?.trim()).toBe('La última vez');
    expect(el().querySelectorAll('.pn-paso__ultima').length).toBe(1);
    expect(lista.map((b) => b.querySelector('.pn-paso__meta')?.textContent?.trim())).toEqual([
      'Gestión · 6 sedes',
      'Administración',
      'Profesorado',
    ]);
  });

  it('con una sola escuela no se queda: sigue al destino de siempre', async () => {
    await montar();
    responder([SUR]);
    expect(navegar).toHaveBeenCalledOnceWith(['/adminpage'], { replaceUrl: true });
    expect(opciones().length).toBe(0);
  });

  it('si no se pueden leer las escuelas, sigue al destino de siempre', async () => {
    await montar();
    http.expectOne(URL_LISTA).flush('no', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(navegar).toHaveBeenCalledOnceWith(['/adminpage'], { replaceUrl: true });
  });

  it('elegir «La última vez» entra sin pedir nada a la API', async () => {
    await montar();
    responder();
    opciones()[0].click();
    fixture.detectChanges();
    http.expectNone(URL_CAMBIO);
    expect(navegar).toHaveBeenCalledOnceWith(['/adminpage'], { replaceUrl: true });
    expect(irA).not.toHaveBeenCalled();
  });

  it('elegir otra cambia la escuela con «Entrando en…» y recarga la app', async () => {
    await montar();
    responder();
    opciones()[1].click();
    fixture.detectChanges();
    expect(el().querySelector('pn-entrando-escuela')?.textContent).toContain('Entrando en Escuela Ficticia Norte…');
    const peticion = http.expectOne(URL_CAMBIO);
    expect(peticion.request.body).toEqual({ organizacionId: 1 });
    peticion.flush({});
    expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
    expect(navegar).not.toHaveBeenCalled();
  });

  it('un doble clic no lanza dos cambios', async () => {
    await montar();
    responder();
    opciones()[1].click();
    opciones()[1]?.click();
    opciones()[2]?.click();
    fixture.detectChanges();
    http.expectOne(URL_CAMBIO).flush({});
    expect(irA).toHaveBeenCalledTimes(1);
  });

  it('si el cambio falla, se queda en la lista con el error y «Reintentar», que vuelve a pedirlo', async () => {
    await montar();
    responder();
    opciones()[2].click();
    fixture.detectChanges();
    http.expectOne(URL_CAMBIO).flush({ mensaje: 'Acceso denegado.' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(el().querySelector('pn-entrando-escuela')).toBeNull();
    expect(irA).not.toHaveBeenCalled();
    const fallo = el().querySelector<HTMLElement>('.pn-paso__fallo')!;
    expect(fallo.getAttribute('role')).toBe('alert');
    expect(fallo.textContent).toContain('No se ha podido cambiar de escuela. Inténtalo otra vez.');
    expect(fallo.textContent).toContain(
      'Si vuelve a fallar, entra en la de «La última vez» y cambia luego desde el panel.'
    );
    const reintentar = fallo.querySelector<HTMLButtonElement>('button')!;
    expect(reintentar.textContent?.trim()).toBe('Reintentar');
    expect(document.activeElement).toBe(reintentar);
    expect(opciones().length).toBe(3);

    reintentar.click();
    fixture.detectChanges();
    const otra = http.expectOne(URL_CAMBIO);
    expect(otra.request.body).toEqual({ organizacionId: 3 });
    otra.flush({});
    expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
  });

  it('las flechas mueven el foco entre las escuelas', async () => {
    await montar();
    responder();
    const lista = opciones();
    lista[0].focus();
    lista[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(lista[1]);
    lista[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(lista[0]);
    lista[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(lista[2]);
  });

  it('«¿No eres …? Cerrar sesión» cierra la sesión y va a la portada', async () => {
    await montar();
    responder();
    const pie = el().querySelector<HTMLElement>('.pn-paso__pie')!;
    expect(pie.textContent).toContain('¿No eres Ana López?');
    pie.querySelector<HTMLButtonElement>('button')!.click();
    expect(logout).toHaveBeenCalledTimes(1);
    logueado.next(false);
    expect(navegar).toHaveBeenCalledOnceWith(['/']);
  });
});
