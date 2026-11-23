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

  function paso(antes: number, p: -2 | -1 | 1 | 2, poner: jasmine.Spy, enCurso: jasmine.Spy, apto = false): void {
    servicio.darPaso({
      prefijo: 'profesor', grupoId: 7, alumno: lucia, disciplina: 'Taekwondo', antes, apto, paso: p, poner, enCurso, destruir,
    });
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

  it('apto: confirma con los textos de Pasar lista y, si sale bien, deja apta y las rallas en 10', async () => {
    const poner = jasmine.createSpy('poner');
    confirmar.and.callFake(async (o: { ejecutar: () => unknown }) => {
      const r = o.ejecutar();
      expect(isObservable(r)).toBeTrue();
      return { confirmado: true, respuesta: { alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 10, aptoParaExamen: true } };
    });
    await servicio.confirmarApto({ prefijo: 'profesor', grupoId: 7, alumno: lucia, disciplina: 'Taekwondo', poner });
    const o = confirmar.calls.mostRecent().args[0];
    expect(o.titulo).toBe('¿Marcar a Lucía apta para examen?');
    expect(o.mensaje).toEqual([
      { fuerte: 'Lucía Prueba' }, ' quedará apta para examen en Taekwondo. Las rallas se quedan hasta que cambie de grado.',
    ]);
    expect(o.accion).toBe('Marcar apta');
    expect(o.nombre).toBe('rallas-apto');
    expect(poner).toHaveBeenCalledWith(10, true);
    expect(avisos.exito).toHaveBeenCalledWith('Lucía queda apta para examen en Taekwondo.');
  });

  it('apto: la llamada va a la ruta de apto del grupo', async () => {
    confirmar.and.callFake(async (o: { ejecutar: () => Promise<unknown> | unknown }) => {
      const r = o.ejecutar() as never;
      const promesa = firstValueFrom(r);
      http.expectOne(`${url}/apto`).flush({ alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 10, aptoParaExamen: true });
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

  // ------------------------------------------------------------------ quitar desde las 5 rallas

  const alQuitar = `${url}/al-quitar`;
  const estado = (extra: object = {}) => ({
    alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 10, aptoParaExamen: true, pases: [], convocatorias: [], ...extra,
  });
  const pase = { id: 55, concepto: 'Pase con derecho a examen - TAEKWONDO', importe: 30 };
  const convocatoria = { convocatoriaId: 9, fecha: '2026-12-12', cargoId: 77, concepto: 'Examen diciembre', importe: 25 };

  /** El diálogo confirma y lanza `ejecutar` contra el backend de prueba con la respuesta dada. */
  function confirmarConRespuesta(cuerpo: object, opciones?: { status: number; statusText: string }): void {
    confirmar.and.callFake(async (o: { ejecutar: () => unknown }) => {
      const promesa = firstValueFrom(o.ejecutar() as never);
      http.expectOne((r) => r.method === 'POST' && r.url === url).flush(cuerpo, opciones);
      try {
        return { confirmado: true, respuesta: await promesa };
      } catch (error) {
        return { confirmado: true, error };
      }
    });
  }

  async function esperarDialogo(): Promise<void> {
    await new Promise((r) => setTimeout(r));
  }

  it('quitar desde 10 siendo apta, con pase y convocatoria: pregunta, confirma con lo que se borra y manda los ids', async () => {
    const poner = jasmine.createSpy('poner');
    const enCurso = jasmine.createSpy('enCurso');
    confirmarConRespuesta({
      alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 9, aptoParaExamen: false,
      pasesBorrados: [pase], convocatoriasSalidas: [convocatoria],
    });
    paso(10, -1, poner, enCurso, true);
    expect(poner).not.toHaveBeenCalled();
    expect(enCurso).toHaveBeenCalledWith(true);
    const get = http.expectOne(alQuitar);
    expect(get.request.method).toBe('GET');
    get.flush(estado({ pases: [pase], convocatorias: [convocatoria] }));
    await esperarDialogo();

    const o = confirmar.calls.mostRecent().args[0];
    expect(o.titulo).toBe('¿Quitar media ralla a Lucía?');
    expect(o.mensaje).toEqual([
      { fuerte: 'Lucía Prueba' }, ' se quedará con 4 y media de 5 rallas y dejará de estar apta para examen en Taekwondo.',
    ]);
    expect(o.aviso.titulo).toBe('También se borrará su pase pendiente de cobro:');
    expect(o.aviso.lineas.length).toBe(2);
    expect(o.aviso.lineas[0][0]).toMatch(/^Pase con derecho a examen - TAEKWONDO — 30,00\s€$/);
    expect(o.aviso.lineas[1][0]).toMatch(
      /^Se le sacará de la convocatoria del 12\/12\/2026 y se borrará su cargo: Examen diciembre — 25,00\s€$/);
    expect(o.accion).toBe('Quitar y borrar el pase');
    expect(o.destructivo).toBeTrue();

    const post = http.match(() => true);
    expect(post.length).toBe(0);
    expect(poner).toHaveBeenCalledWith(9, false);
    expect(avisos.exito).toHaveBeenCalledWith(
      'Lucía ya no está apta para examen. Se ha borrado su pase pendiente. Ya no está en la convocatoria del 12/12/2026.');
    expect(enCurso.calls.mostRecent().args).toEqual([false]);
  });

  it('quitar desde 10: el POST lleva la confirmación y los ids del aviso', async () => {
    let cuerpo: unknown;
    confirmar.and.callFake(async (o: { ejecutar: () => unknown }) => {
      const promesa = firstValueFrom(o.ejecutar() as never);
      const req = http.expectOne((r) => r.method === 'POST' && r.url === url);
      cuerpo = req.request.body;
      req.flush({ alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 8, aptoParaExamen: false, pasesBorrados: [pase] });
      return { confirmado: true, respuesta: await promesa };
    });
    paso(10, -2, jasmine.createSpy('poner'), jasmine.createSpy('enCurso'), true);
    http.expectOne(alQuitar).flush(estado({ pases: [pase], convocatorias: [convocatoria] }));
    await esperarDialogo();
    expect(cuerpo).toEqual({ paso: -2, confirmarQuitarApto: true, pasesABorrar: [55], convocatoriasASalir: [9] });
    expect(confirmar.calls.mostRecent().args[0].titulo).toBe('¿Quitar una ralla a Lucía?');
  });

  it('quitar desde 10 siendo apta sin pases: confirma sin aviso y con el botón del paso, no destructivo', async () => {
    const poner = jasmine.createSpy('poner');
    confirmarConRespuesta({ alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 8, aptoParaExamen: false, pasesBorrados: [] });
    paso(10, -2, poner, jasmine.createSpy('enCurso'), true);
    http.expectOne(alQuitar).flush(estado());
    await esperarDialogo();
    const o = confirmar.calls.mostRecent().args[0];
    expect(o.mensaje).toEqual([
      { fuerte: 'Lucía Prueba' }, ' se quedará con 4 de 5 rallas y dejará de estar apta para examen en Taekwondo.',
    ]);
    expect(o.aviso).toBeUndefined();
    expect(o.accion).toBe('Quitar una ralla');
    expect(o.destructivo).toBeFalse();
    expect(poner).toHaveBeenCalledWith(8, false);
    expect(avisos.exito).toHaveBeenCalledWith('Lucía ya no está apta para examen.');
  });

  it('quitar desde 10 sin apto y solo con convocatoria: sin «dejará de estar» y botón de sacar de la convocatoria', async () => {
    confirmar.and.resolveTo({ confirmado: false });
    paso(10, -1, jasmine.createSpy('poner'), jasmine.createSpy('enCurso'), false);
    http.expectOne(alQuitar).flush(estado({ aptoParaExamen: false, convocatorias: [{ ...convocatoria, cargoId: null, concepto: null, importe: null }] }));
    await esperarDialogo();
    const o = confirmar.calls.mostRecent().args[0];
    expect(o.mensaje).toEqual([{ fuerte: 'Lucía Prueba' }, ' se quedará con 4 y media de 5 rallas.']);
    expect(o.aviso.titulo).toBeUndefined();
    expect(o.aviso.lineas).toEqual([['Se le sacará de la convocatoria del 12/12/2026']]);
    expect(o.accion).toBe('Quitar y sacar de la convocatoria');
    expect(o.destructivo).toBeTrue();
  });

  it('quitar desde 10 cancelado: no guarda ni cambia nada', async () => {
    const poner = jasmine.createSpy('poner');
    confirmar.and.resolveTo({ confirmado: false });
    paso(10, -1, poner, jasmine.createSpy('enCurso'), true);
    http.expectOne(alQuitar).flush(estado({ pases: [pase] }));
    await esperarDialogo();
    expect(poner).not.toHaveBeenCalled();
    expect(avisos.exito).not.toHaveBeenCalled();
  });

  it('quitar desde 10 con 409: avisa con el mensaje del servidor y queda como estaba', async () => {
    const poner = jasmine.createSpy('poner');
    confirmarConRespuesta(
      { mensaje: 'Algo ha cambiado mientras tanto. Vuelve a abrir la hoja.' }, { status: 409, statusText: 'Conflict' });
    paso(10, -1, poner, jasmine.createSpy('enCurso'), true);
    http.expectOne(alQuitar).flush(estado({ pases: [pase] }));
    await esperarDialogo();
    expect(poner.calls.allArgs()).toEqual([[10, true]]);
    expect(avisos.error).toHaveBeenCalledWith('Algo ha cambiado mientras tanto. Vuelve a abrir la hoja.');
    expect(avisos.exito).not.toHaveBeenCalled();
  });

  it('quitar desde 10: si falla la consulta, avisa y no guarda', () => {
    const poner = jasmine.createSpy('poner');
    const enCurso = jasmine.createSpy('enCurso');
    paso(10, -1, poner, enCurso, true);
    http.expectOne(alQuitar).flush({ mensaje: 'No se puede borrar el pase.' }, { status: 409, statusText: 'Conflict' });
    expect(confirmar).not.toHaveBeenCalled();
    expect(poner).not.toHaveBeenCalled();
    expect(avisos.error).toHaveBeenCalledWith('No se puede borrar el pase.');
    expect(enCurso.calls.allArgs()).toEqual([[true], [false]]);
  });

  it('quitar desde 10 sin apto ni pases ni convocatorias: el paso de siempre, sin confirmación', () => {
    const poner = jasmine.createSpy('poner');
    const enCurso = jasmine.createSpy('enCurso');
    paso(10, -1, poner, enCurso);
    http.expectOne(alQuitar).flush(estado({ aptoParaExamen: false }));
    expect(confirmar).not.toHaveBeenCalled();
    expect(poner).toHaveBeenCalledWith(9);
    const req = http.expectOne((r) => r.method === 'POST' && r.url === url);
    expect(req.request.body).toEqual({ paso: -1 });
    req.flush({ alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 9, aptoParaExamen: false });
    expect(poner).toHaveBeenCalledWith(9, false);
    // En curso desde la consulta hasta que se guarda el paso; nunca se suelta en medio.
    expect(enCurso.calls.allArgs()).toEqual([[true], [true], [false]]);
  });
});
