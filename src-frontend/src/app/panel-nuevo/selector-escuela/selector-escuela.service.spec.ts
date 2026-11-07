import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { sesionCerrada } from '../../servicios/authentication/sesion-cerrada';
import { contraste } from '../acento-escuela';
import { LOGO_LEGADO } from '../marca-escuela-panel.service';
import {
  IR_A_URL,
  RUTA_ELEGIR_ESCUELA,
  RUTA_ENTRADA_SESION,
  SelectorEscuelaService,
  coloresIniciales,
  debeElegirTrasEntrar,
  inicialesEscuela,
  metaEscuela,
  ordenTrasEntrar,
  textoRoles,
  urlLogoEscuela,
} from './selector-escuela.service';
import { escuela } from './escuelas-prueba';

const URL_LISTA = `${environment.apiUrl}/auth/organizaciones`;
const URL_CAMBIO = `${environment.apiUrl}/auth/organizacion`;

describe('Selector de escuela: reglas puras', () => {
  it('«Tras entrar» solo con dos escuelas o más y alguna que no sea solo de familia', () => {
    expect(debeElegirTrasEntrar([])).toBeFalse();
    expect(debeElegirTrasEntrar([escuela({ activa: true })])).toBeFalse();
    expect(
      debeElegirTrasEntrar([escuela({ id: 1, roles: ['ROLE_USER'] }), escuela({ id: 2, roles: ['ROLE_USER'] })])
    ).toBeFalse();
    expect(
      debeElegirTrasEntrar([escuela({ id: 1, roles: ['ROLE_USER'] }), escuela({ id: 2, roles: ['ROLE_PROFESOR'] })])
    ).toBeTrue();
    expect(debeElegirTrasEntrar([escuela({ id: 1 }), escuela({ id: 2 }), escuela({ id: 3 })])).toBeTrue();
  });

  it('pone primero la de «La última vez» (activa) y deja las demás en el orden de la API', () => {
    const lista = [escuela({ id: 1 }), escuela({ id: 2 }), escuela({ id: 3, activa: true })];
    expect(ordenTrasEntrar(lista).map((e) => e.id)).toEqual([3, 1, 2]);
    expect(ordenTrasEntrar([escuela({ id: 4 }), escuela({ id: 5 })]).map((e) => e.id)).toEqual([4, 5]);
  });

  it('escribe los roles con los nombres de la decisión 262, de más a menos poder', () => {
    expect(textoRoles(['ROLE_ADMIN'])).toBe('Administración');
    expect(textoRoles(['ROLE_USER', 'ROLE_MANAGER'])).toBe('Gestión y familia');
    expect(textoRoles(['ROLE_USER', 'ROLE_PROFESOR', 'ROLE_MANAGER'])).toBe('Gestión, profesorado y familia');
    expect(textoRoles(['ROLE_RESPONSABLE_SEDE'])).toBe('Responsable de sede');
    expect(textoRoles(['ROLE_ADMIN_PLATAFORMA'])).toBe('');
  });

  it('añade «N sedes» solo con más de una sede', () => {
    expect(metaEscuela(escuela({ roles: ['ROLE_MANAGER'], numeroSedes: 6 }))).toBe('Gestión · 6 sedes');
    expect(metaEscuela(escuela({ roles: ['ROLE_MANAGER'], numeroSedes: 1 }))).toBe('Gestión');
    expect(metaEscuela(escuela({ roles: ['ROLE_MANAGER'], numeroSedes: 0 }))).toBe('Gestión');
  });

  it('saca las iniciales sin artículos ni preposiciones', () => {
    expect(inicialesEscuela('Taekwondo Sierra Sur')).toBe('TS');
    expect(inicialesEscuela('Club de la Prueba')).toBe('CP');
    expect(inicialesEscuela("Moi's Kim Do")).toBe('MK');
    expect(inicialesEscuela('Judo')).toBe('J');
    expect(inicialesEscuela(null)).toBe('');
  });

  it('da la URL del logo de cada escuela: el subido con su versión, el de siempre o ninguno', () => {
    expect(urlLogoEscuela(escuela({ id: 7, logo: 'SUBIDO', logoVersion: 31 }))).toBe(
      `${environment.apiUrl}/auth/organizaciones/7/logo?v=31`
    );
    expect(urlLogoEscuela(escuela({ logo: 'SUBIDO', logoVersion: null }))).toBeNull();
    expect(urlLogoEscuela(escuela({ logo: 'LEGADO' }))).toBe(LOGO_LEGADO);
    expect(urlLogoEscuela(escuela({ logo: 'NINGUNO' }))).toBeNull();
  });

  it('las iniciales llegan siempre a 4,5:1 sobre su fondo', () => {
    for (const color of ['#0B5CAD', '#1B2B2E', '#F7E04B', '#777777', '#3E9B4F', null, 'rojo', '#FFFFFF']) {
      const { fondo, texto } = coloresIniciales(color);
      expect(contraste(fondo, texto)).withContext(String(color)).toBeGreaterThanOrEqual(4.5);
    }
    expect(coloresIniciales('#0b5cad')).toEqual({ fondo: '#0B5CAD', texto: '#FFFFFF' });
    expect(coloresIniciales(null).fondo).toBe('#1A2230');
  });
});

describe('SelectorEscuelaService', () => {
  let servicio: SelectorEscuelaService;
  let http: HttpTestingController;
  let email: BehaviorSubject<string | null>;
  let irA: jasmine.Spy;

  beforeEach(() => {
    email = new BehaviorSubject<string | null>('gestor@example.test');
    irA = jasmine.createSpy('irA');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthenticationService, useValue: { emailCambio: email.asObservable() } },
        { provide: IR_A_URL, useValue: irA },
      ],
    });
    servicio = TestBed.inject(SelectorEscuelaService);
    http = TestBed.inject(HttpTestingController);
    sessionStorage.setItem('alumnoId', '41');
    sessionStorage.setItem('panelProductosEstado', '{"filtros":{}}');
  });

  afterEach(() => {
    http.verify();
    sessionStorage.removeItem('alumnoId');
    sessionStorage.removeItem('panelProductosEstado');
  });

  it('pide las escuelas una vez y comparte la respuesta, sin el aviso global de error', async () => {
    const a = firstValueFrom(servicio.organizaciones());
    const b = firstValueFrom(servicio.organizaciones());
    const peticion = http.expectOne(URL_LISTA);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush([escuela({ activa: true })]);
    expect((await a).length).toBe(1);
    expect(await b).toEqual(await a);
    expect((await firstValueFrom(servicio.organizaciones())).length).toBe(1);
    http.expectNone(URL_LISTA);
  });

  it('no guarda un error: la siguiente llamada vuelve a pedir', async () => {
    const fallo = firstValueFrom(servicio.organizaciones()).catch((e) => e);
    http.expectOne(URL_LISTA).flush('no', { status: 500, statusText: 'Error' });
    expect((await fallo).status).toBe(500);
    const bien = firstValueFrom(servicio.organizaciones());
    http.expectOne(URL_LISTA).flush([escuela()]);
    expect((await bien).length).toBe(1);
  });

  it('descarta la lista al entrar con otra cuenta y al cerrar sesión', () => {
    servicio.organizaciones().subscribe();
    http.expectOne(URL_LISTA).flush([escuela()]);

    email.next('otra@example.test');
    servicio.organizaciones().subscribe();
    http.expectOne(URL_LISTA).flush([escuela({ id: 2 })]);

    sesionCerrada.next();
    servicio.organizaciones().subscribe();
    http.expectOne(URL_LISTA).flush([]);
  });

  it('tras el login manda a «Tras entrar» solo si toca; si no, o si falla, al destino de siempre', async () => {
    const dos = firstValueFrom(servicio.destinoTrasEntrar('/adminpage'));
    http.expectOne(URL_LISTA).flush([escuela({ id: 1, activa: true }), escuela({ id: 2 })]);
    expect(await dos).toBe(RUTA_ELEGIR_ESCUELA);

    email.next('una@example.test');
    const una = firstValueFrom(servicio.destinoTrasEntrar('/adminpage'));
    http.expectOne(URL_LISTA).flush([escuela({ activa: true })]);
    expect(await una).toBe('/adminpage');

    email.next('falla@example.test');
    const falla = firstValueFrom(servicio.destinoTrasEntrar('/mis-grupos'));
    http.expectOne(URL_LISTA).flush('no', { status: 503, statusText: 'Error' });
    expect(await falla).toBe('/mis-grupos');
  });

  it('cambia de escuela: POST, vacía el sessionStorage y recarga la app en la entrada de la sesión', async () => {
    const cambio = firstValueFrom(servicio.cambiar(2));
    const peticion = http.expectOne(URL_CAMBIO);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ organizacionId: 2 });
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(irA).not.toHaveBeenCalled();
    peticion.flush({ token: 'nuevo' });

    expect(await cambio).toBeTrue();
    expect(sessionStorage.getItem('alumnoId')).toBeNull();
    expect(sessionStorage.getItem('panelProductosEstado')).toBeNull();
    expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
    expect(RUTA_ENTRADA_SESION).toBe('/login/google');
  });

  it('un segundo cambio con otro en vuelo no lanza otra petición', async () => {
    servicio.cambiar(2).subscribe();
    const segundo = await firstValueFrom(servicio.cambiar(3));
    expect(segundo).toBeFalse();
    expect(servicio.ocupado).toBeTrue();
    http.expectOne(URL_CAMBIO).flush({});
    // Ya recargando: sigue sin admitir otro cambio.
    expect(await firstValueFrom(servicio.cambiar(3))).toBeFalse();
    http.expectNone(URL_CAMBIO);
    expect(irA).toHaveBeenCalledTimes(1);
  });

  it('si el POST falla, la escuela no cambia: ni recarga ni vacía nada, y se puede reintentar', async () => {
    const fallo = firstValueFrom(servicio.cambiar(2)).catch((e) => e);
    http.expectOne(URL_CAMBIO).flush({ mensaje: 'Acceso denegado.' }, { status: 403, statusText: 'Forbidden' });
    expect((await fallo).status).toBe(403);
    expect(irA).not.toHaveBeenCalled();
    expect(sessionStorage.getItem('alumnoId')).toBe('41');
    expect(servicio.ocupado).toBeFalse();

    servicio.cambiar(2).subscribe();
    http.expectOne(URL_CAMBIO).flush({});
    expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
  });
});
