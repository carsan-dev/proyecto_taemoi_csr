import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DestroyRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, isObservable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { PasosRallasService, TEXTOS_RALLAS } from './pasos-rallas.service';

describe('PasosRallasService · pasos y apto de la hoja de rallas (decisiones 513 y 514)', () => {
  let servicio: PasosRallasService;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;
  let confirmar: jasmine.Spy;
  let destruir: DestroyRef;
  const url = `${environment.apiUrl}/profesor/grupos/7/alumnos/3/rallas`;
  const lucia = { id: 3, nombre: 'Lucía', apellidos: 'Prueba', genero: 'FEMENINO' };

  beforeEach(() => {
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error']);
    confirmar = jasmine.createSpy('confirmar');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: AvisosService, useValue: avisos },
        { provide: ConfirmacionService, useValue: { confirmar } },
      ],
    });
    servicio = TestBed.inject(PasosRallasService);
    http = TestBed.inject(HttpTestingController);
    destruir = TestBed.inject(DestroyRef);
  });

  afterEach(() => http.verify());

  function paso(antes: number, p: -2 | -1 | 1 | 2, poner: jasmine.Spy, enCurso: jasmine.Spy): void {
    servicio.darPaso({ prefijo: 'profesor', grupoId: 7, alumnoId: 3, antes, paso: p, poner, enCurso, destruir });
  }

  it('un paso: optimista, guarda al momento y deja el valor del servidor', () => {
    const poner = jasmine.createSpy('poner');
    const enCurso = jasmine.createSpy('enCurso');
    paso(4, 1, poner, enCurso);
    expect(enCurso).toHaveBeenCalledWith(true);
    expect(poner).toHaveBeenCalledWith(5);
    const req = http.expectOne(url);
    expect(req.request.body).toEqual({ paso: 1 });
    req.flush({ alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 5, aptoParaExamen: false });
    expect(poner).toHaveBeenCalledWith(5, false);
    expect(enCurso).toHaveBeenCalledWith(false);
  });

  it('si falla, deshace el paso y avisa con el mensaje del servidor', () => {
    const poner = jasmine.createSpy('poner');
    const enCurso = jasmine.createSpy('enCurso');
    paso(4, 2, poner, enCurso);
    http.expectOne(url).flush({ mensaje: 'No caben más rallas.' }, { status: 409, statusText: 'Conflict' });
    expect(poner.calls.allArgs()).toEqual([[6], [4]]);
    expect(avisos.error).toHaveBeenCalledWith('No caben más rallas.');
    expect(enCurso).toHaveBeenCalledWith(false);
  });

  it('un paso que no cabe no hace nada', () => {
    const poner = jasmine.createSpy('poner');
    const enCurso = jasmine.createSpy('enCurso');
    paso(0, -1, poner, enCurso);
    paso(10, 1, poner, enCurso);
    http.expectNone(url);
    expect(poner).not.toHaveBeenCalled();
    expect(enCurso).not.toHaveBeenCalled();
  });

  it('apto: confirma con los textos de Pasar lista y, si sale bien, deja apta y rallas a 0', async () => {
    const poner = jasmine.createSpy('poner');
    confirmar.and.callFake(async (o: { ejecutar: () => unknown }) => {
      const r = o.ejecutar();
      expect(isObservable(r)).toBeTrue();
      return { confirmado: true, respuesta: { alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 0, aptoParaExamen: true } };
    });
    await servicio.confirmarApto({ prefijo: 'profesor', grupoId: 7, alumno: lucia, disciplina: 'Taekwondo', poner });
    const o = confirmar.calls.mostRecent().args[0];
    expect(o.titulo).toBe('¿Marcar a Lucía apta para examen?');
    expect(o.mensaje).toEqual([{ fuerte: 'Lucía Prueba' }, ' quedará apta para examen en Taekwondo y sus rallas volverán a 0.']);
    expect(o.accion).toBe('Marcar apta');
    expect(o.nombre).toBe('rallas-apto');
    expect(poner).toHaveBeenCalledWith(0, true);
    expect(avisos.exito).toHaveBeenCalledWith('Lucía queda apta para examen en Taekwondo. Sus rallas vuelven a 0.');
  });

  it('apto: la llamada va a la ruta de apto del grupo', async () => {
    confirmar.and.callFake(async (o: { ejecutar: () => Promise<unknown> | unknown }) => {
      const r = o.ejecutar() as never;
      const promesa = firstValueFrom(r);
      http.expectOne(`${url}/apto`).flush({ alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 0, aptoParaExamen: true });
      return { confirmado: true, respuesta: await promesa };
    });
    await servicio.confirmarApto({ prefijo: 'profesor', grupoId: 7, alumno: lucia, disciplina: 'Taekwondo', poner: () => undefined });
    expect(avisos.exito).toHaveBeenCalled();
  });

  it('apto cancelado no toca nada; con error avisa sin cambiar', async () => {
    const poner = jasmine.createSpy('poner');
    confirmar.and.resolveTo({ confirmado: false });
    await servicio.confirmarApto({ prefijo: 'profesor', grupoId: 7, alumno: lucia, disciplina: '', poner });
    expect(poner).not.toHaveBeenCalled();
    confirmar.and.resolveTo({ confirmado: true, error: new HttpErrorResponse({ status: 500 }) });
    await servicio.confirmarApto({ prefijo: 'profesor', grupoId: 7, alumno: lucia, disciplina: '', poner });
    expect(poner).not.toHaveBeenCalled();
    expect(avisos.error).toHaveBeenCalledWith(TEXTOS_RALLAS.errorApto);
    expect(avisos.exito).not.toHaveBeenCalled();
  });
});
