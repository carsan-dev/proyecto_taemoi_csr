import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';

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

describe('ModoPrivadoService (decisión 419)', () => {
  let refrescos: Subject<void>;
  let obtener: jasmine.Spy<() => Observable<PermisosSesion>>;
  let modo: ModoPrivadoService;

  beforeEach(() => {
    refrescos = new Subject<void>();
    obtener = jasmine.createSpy('obtener');
    TestBed.configureTestingModule({
      providers: [{ provide: PermisosSesionService, useValue: { obtener, refrescado$: refrescos } }],
    });
    modo = TestBed.inject(ModoPrivadoService);
  });

  it('falla cerrado: mientras no se sabe, tapado y sin botón', () => {
    expect(modo.preferencia()).toBe('desconocida');
    expect(modo.tapado()).toBeTrue();
    expect(modo.disponible()).toBeFalse();

    obtener.and.returnValue(new Subject<PermisosSesion>());
    modo.cargar();

    expect(modo.tapado()).toBeTrue();
    expect(modo.disponible()).toBeFalse();
  });

  it('con la preferencia activa, tapado y con botón; alternar destapa y tapar vuelve a tapar', () => {
    obtener.and.returnValue(of(permisos(true)));
    modo.cargar();

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
    obtener.and.returnValue(of(permisos(false)));
    modo.cargar();

    expect(modo.preferencia()).toBe('inactiva');
    expect(modo.tapado()).toBeFalse();
    expect(modo.disponible()).toBeFalse();
  });

  it('un servidor que aún no manda el campo se ve como hoy', () => {
    obtener.and.returnValue(of(permisos()));
    modo.cargar();

    expect(modo.preferencia()).toBe('inactiva');
  });

  it('si la petición falla, sigue tapado y el botón da la salida', () => {
    obtener.and.returnValue(throwError(() => new Error('sin red')));
    modo.cargar();

    expect(modo.preferencia()).toBe('activa');
    expect(modo.tapado()).toBeTrue();
    expect(modo.disponible()).toBeTrue();
  });

  it('con la misma petición no vuelve a suscribirse ni vuelve a tapar lo destapado', () => {
    const peticion$ = new Subject<PermisosSesion>();
    let suscripciones = 0;
    const contada$ = new Observable<PermisosSesion>((destino) => {
      suscripciones++;
      return peticion$.subscribe(destino);
    });
    obtener.and.returnValue(contada$);
    modo.cargar();
    peticion$.next(permisos(true));
    modo.alternar();

    modo.cargar();
    modo.cargar();

    expect(suscripciones).toBe(1);
    expect(modo.tapado()).toBeFalse();
  });

  it('otra sesión: la preferencia y lo destapado del usuario anterior no valen para el siguiente', () => {
    obtener.and.returnValue(of(permisos(false)));
    modo.cargar();
    expect(modo.tapado()).toBeFalse();

    const siguiente$ = new Subject<PermisosSesion>();
    obtener.and.returnValue(siguiente$);
    modo.cargar();

    expect(modo.preferencia()).toBe('desconocida');
    expect(modo.tapado()).toBeTrue();

    siguiente$.next(permisos(true));
    modo.alternar();
    expect(modo.tapado()).toBeFalse();

    obtener.and.returnValue(new Subject<PermisosSesion>());
    modo.cargar();

    expect(modo.destapado()).toBeFalse();
    expect(modo.tapado()).toBeTrue();
  });

  it('una respuesta de la sesión anterior que llega tarde no pisa la de la nueva', () => {
    const anterior$ = new Subject<PermisosSesion>();
    obtener.and.returnValue(anterior$);
    modo.cargar();

    obtener.and.returnValue(of(permisos(true)));
    modo.cargar();
    anterior$.next(permisos(false));

    expect(modo.preferencia()).toBe('activa');
  });

  it('guardar Mi perfil (refrescado$) aplica la preferencia sin recargar', () => {
    obtener.and.returnValue(of(permisos(false)));
    modo.cargar();
    expect(modo.tapado()).toBeFalse();

    obtener.and.returnValue(of(permisos(true)));
    refrescos.next();

    expect(modo.preferencia()).toBe('activa');
    expect(modo.tapado()).toBeTrue();
  });

  it('textoImporte no dice la cifra mientras está tapado', () => {
    expect(modo.textoImporte('12,00 €')).toBe('importe oculto');

    obtener.and.returnValue(of(permisos(false)));
    modo.cargar();

    expect(modo.textoImporte('12,00 €')).toBe('12,00 €');
  });
});
