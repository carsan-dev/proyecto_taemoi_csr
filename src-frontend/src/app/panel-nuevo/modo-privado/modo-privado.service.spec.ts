import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { ModoPrivadoService } from './modo-privado.service';

function permisos(importesTapados?: boolean): PermisosSesion {
  return {
    roles: [],
    esAdminPlataforma: false,
    sedes: [],
    variasSedes: false,
    tieneGrupos: false,
    capacidades: [],
    importesTapados,
  };
}

// Servicios reales (permisos y modo) con solo el HTTP simulado: lo que se prueba es cómo se reparten las peticiones.
describe('ModoPrivadoService (decisión 419)', () => {
  let http: HttpTestingController;
  let sesion: PermisosSesionService;
  let modo: ModoPrivadoService;

  const pendientes = () => http.match((r) => r.url.endsWith('/auth/permisos'));

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    sesion = TestBed.inject(PermisosSesionService);
    modo = TestBed.inject(ModoPrivadoService);
  });

  afterEach(() => http.verify());

  function responder(cuerpo: PermisosSesion | null): void {
    const peticiones = pendientes();
    expect(peticiones.length).toBe(1);
    peticiones[0].flush(cuerpo);
  }

  it('falla cerrado: mientras no se sabe, tapado y sin botón', () => {
    expect(modo.preferencia()).toBe('desconocida');
    expect(modo.tapado()).toBeTrue();
    expect(modo.disponible()).toBeFalse();

    modo.cargar();

    expect(modo.preferencia()).toBe('desconocida');
    expect(modo.tapado()).toBeTrue();
    expect(modo.disponible()).toBeFalse();
    responder(permisos(true));
  });

  it('con la preferencia activa, tapado y con botón; alternar destapa y tapar vuelve a tapar', () => {
    modo.cargar();
    responder(permisos(true));

    expect(modo.preferencia()).toBe('activa');
    expect(modo.tapado()).toBeTrue();
    expect(modo.disponible()).toBeTrue();

    modo.alternar();
    expect(modo.tapado()).toBeFalse();
    expect(modo.destapado()).toBeTrue();

    modo.alternar();
    expect(modo.tapado()).toBeTrue();

    modo.alternar();
    modo.tapar();
    expect(modo.tapado()).toBeTrue();
  });

  it('con la preferencia apagada, sin tapar y sin botón', () => {
    modo.cargar();
    responder(permisos(false));

    expect(modo.preferencia()).toBe('inactiva');
    expect(modo.tapado()).toBeFalse();
    expect(modo.disponible()).toBeFalse();
  });

  it('un servidor que aún no manda el campo (undefined) se ve como hoy: sin modo, abierto', () => {
    modo.cargar();
    responder(permisos());

    expect(modo.preferencia()).toBe('inactiva');
    expect(modo.tapado()).toBeFalse();
  });

  it('un cuerpo nulo no abre: queda activa, tapado y con botón, como un error', () => {
    modo.cargar();
    responder(null);

    expect(modo.preferencia()).toBe('activa');
    expect(modo.tapado()).toBeTrue();
    expect(modo.disponible()).toBeTrue();
  });

  it('si la petición falla, sigue tapado y el botón da la salida', () => {
    modo.cargar();
    pendientes()[0].flush('error', { status: 500, statusText: 'Server Error' });

    expect(modo.preferencia()).toBe('activa');
    expect(modo.tapado()).toBeTrue();
    expect(modo.disponible()).toBeTrue();
  });

  it('tras un error, un importe nuevo no relee ni vuelve a tapar lo destapado', () => {
    modo.cargar();
    pendientes()[0].flush('error', { status: 500, statusText: 'Server Error' });
    modo.alternar();
    expect(modo.tapado()).toBeFalse();

    // Otro `pn-importe` que se pinta después.
    modo.cargar();
    modo.cargar();

    expect(pendientes().length).toBe(0);
    expect(modo.preferencia()).toBe('activa');
    expect(modo.disponible()).toBeTrue();
    expect(modo.destapado()).toBeTrue();
    expect(modo.tapado()).toBeFalse();
  });

  it('varias llamadas con la petición en curso o ya resuelta hacen una sola petición y no tocan lo destapado', () => {
    modo.cargar();
    modo.cargar();
    const peticiones = pendientes();
    expect(peticiones.length).toBe(1);
    peticiones[0].flush(permisos(true));
    modo.alternar();

    modo.cargar();
    modo.cargar();

    expect(pendientes().length).toBe(0);
    expect(modo.destapado()).toBeTrue();
    expect(modo.preferencia()).toBe('activa');
  });

  it('otra sesión (login de otro usuario): vuelve a desconocida, tapa y lee de nuevo', () => {
    modo.cargar();
    responder(permisos(true));
    modo.alternar();
    expect(modo.tapado()).toBeFalse();

    sesion.reiniciar();
    modo.cargar();

    expect(modo.preferencia()).toBe('desconocida');
    expect(modo.destapado()).toBeFalse();
    expect(modo.tapado()).toBeTrue();
    expect(modo.disponible()).toBeFalse();
    responder(permisos(false));
    expect(modo.preferencia()).toBe('inactiva');
  });

  it('una respuesta de la sesión anterior que llega tarde no pisa la de la nueva', () => {
    modo.cargar();
    const anterior = pendientes()[0];

    sesion.reiniciar();
    modo.cargar();
    responder(permisos(true));
    anterior.flush(permisos(false));

    expect(modo.preferencia()).toBe('activa');
  });

  it('guardar Mi perfil (refrescado$) aplica la preferencia nueva sin volver a tapar lo destapado', () => {
    modo.cargar();
    responder(permisos(true));
    modo.alternar();

    sesion.refrescar();
    responder(permisos(true));

    expect(modo.preferencia()).toBe('activa');
    expect(modo.destapado()).toBeTrue();

    sesion.refrescar();
    responder(permisos(false));

    expect(modo.preferencia()).toBe('inactiva');
    expect(modo.tapado()).toBeFalse();
  });

  it('guardar Mi perfil activando el modo con la pantalla abierta la tapa', () => {
    modo.cargar();
    responder(permisos(false));
    expect(modo.tapado()).toBeFalse();

    sesion.refrescar();
    responder(permisos(true));

    expect(modo.preferencia()).toBe('activa');
    expect(modo.tapado()).toBeTrue();
  });

  it('textoImporte no dice la cifra mientras está tapado', () => {
    expect(modo.textoImporte('12,00 €')).toBe('importe oculto');

    modo.cargar();
    responder(permisos(false));

    expect(modo.textoImporte('12,00 €')).toBe('12,00 €');
  });
});
