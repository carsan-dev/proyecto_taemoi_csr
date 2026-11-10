import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DeporteDelAlumno, FichaAlumnoProfesor, GrupoCompartido } from '../../interfaces/profesor';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AsistenciaService } from '../../servicios/endpoints/asistencia.service';
import { ProfesorService } from '../../servicios/endpoints/profesor.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { FichaProfesorPanelComponent } from './ficha-profesor-panel.component';

function deporte(cambios: Partial<DeporteDelAlumno> = {}): DeporteDelAlumno {
  return {
    deporte: 'TAEKWONDO', deporteNombre: 'Taekwondo', grado: { codigo: 'AMARILLO', nombre: 'Amarillo' }, fechaGrado: '2026-03-12',
    apto: false, deMisGrupos: true, admitePases: true, siguienteGrado: { codigo: 'NARANJA', nombre: 'Naranja' }, pideRojoBordado: false,
    mediasRallas: 4, aplicaRallas: true, ...cambios,
  };
}

function ficha(deportes: DeporteDelAlumno[], grupo: Partial<GrupoCompartido> = {}, genero: string | null = null): FichaAlumnoProfesor {
  return {
    id: 3, nombre: 'Ana', apellidos: 'Prueba Ficticia', edad: 9, genero,
    grupos: [{ id: 4, nombre: 'Infantil', sede: null, deporte: 'TAEKWONDO', puedeCambiarRallas: true, ...grupo }],
    contacto: [], deportes,
  };
}

describe('FichaProfesorPanelComponent · rallas desde la ficha (decisión 514)', () => {
  let fixture: ComponentFixture<FichaProfesorPanelComponent>;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;
  let confirmar: jasmine.Spy;
  let cambios: unknown[];
  const url = `${environment.apiUrl}/profesor/grupos/4/alumnos/3/rallas`;
  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined) => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const bloqueDe = (nombre: string) =>
    Array.from(el().querySelectorAll<HTMLElement>('.ficha__bloque')).find((b) => b.querySelector('h2')?.textContent?.trim() === nombre)!;
  const punta = (nombre = 'Taekwondo') => bloqueDe(nombre).querySelector<HTMLButtonElement>('button.ficha__punta');
  const hoja = () => document.querySelector<HTMLDialogElement>('dialog[data-dialogo="rallas"]');
  const cuenta = () => texto(hoja()?.querySelector('[data-cuenta]'));

  function crear(datos: FichaAlumnoProfesor, grupo = '4'): void {
    const profesor = jasmine.createSpyObj<ProfesorService>('ProfesorService', ['ficha', 'salud', 'actualizarApto', 'actualizarFechaGrado', 'pasarGrado']);
    profesor.ficha.and.returnValue(of(datos));
    const asistencia = jasmine.createSpyObj<AsistenciaService>('AsistenciaService', ['deAlumno']);
    asistencia.deAlumno.and.returnValue(of({ presentes: 0, clases: 0, dias: [] }));
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error']);
    confirmar = jasmine.createSpy('confirmar');
    TestBed.configureTestingModule({
      imports: [FichaProfesorPanelComponent],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: ProfesorService, useValue: profesor },
        { provide: AsistenciaService, useValue: asistencia },
        { provide: AvisosService, useValue: avisos },
        { provide: ConfirmacionService, useValue: { confirmar } },
        { provide: AuthenticationService, useValue: { getRolesActuales: () => ['ROLE_PROFESOR'] } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ alumnoId: '3' }), queryParamMap: convertToParamMap({ grupo }) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FichaProfesorPanelComponent);
    cambios = [];
    fixture.componentInstance.rallasCambiadas.subscribe((c) => cambios.push(c));
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  }

  function abrir(): void {
    punta()!.click();
    fixture.detectChanges();
  }

  function pulsar(paso: number): void {
    hoja()!.querySelector<HTMLButtonElement>(`[data-paso="${paso}"]`)!.click();
    fixture.detectChanges();
  }

  afterEach(() => {
    http.verify();
    fixture.destroy();
    fixture.nativeElement.remove();
  });

  it('titular: la punta de la disciplina del grupo es un botón con la etiqueta y el aspecto de Pasar lista', () => {
    crear(ficha([deporte({ mediasRallas: 5 })]));
    const b = punta()!;
    expect(b).not.toBeNull();
    expect(b.getAttribute('aria-haspopup')).toBe('dialog');
    expect(b.getAttribute('aria-expanded')).toBe('false');
    expect(b.getAttribute('aria-label')).toBe('Rallas de Ana Prueba Ficticia: 2 y media de 5 rallas');
    expect(b.querySelectorAll('pn-cinturon [data-ralla]').length).toBe(3);
    const css = getComputedStyle(b);
    expect(css.borderTopWidth).toBe('0px');
    expect(css.backgroundColor).toBe('rgba(0, 0, 0, 0)');
    expect(b.getBoundingClientRect().width).toBeGreaterThanOrEqual(44);
    expect(b.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
    expect(getComputedStyle(b.querySelector('.ficha__punta-marco')!).borderTopWidth).toBe('1px');
    expect(texto(bloqueDe('Taekwondo').querySelector('[data-rallas]'))).toBe('2 y media de 5 rallas');
  });

  it('solo lectura: ayudante, otra disciplina, sin grado, sin rallas o sin grupo de origen', () => {
    crear(ficha([deporte()], { puedeCambiarRallas: false }));
    expect(punta()).toBeNull();
    expect(bloqueDe('Taekwondo').querySelector('pn-cinturon.ficha__cinto')).not.toBeNull();
    fixture.destroy();
    fixture.nativeElement.remove();
    TestBed.resetTestingModule();

    crear(ficha([
      deporte({ deporte: 'KICKBOXING', deporteNombre: 'Kickboxing' }),
      deporte({ deporte: 'PILATES', deporteNombre: 'Pilates', deMisGrupos: false }),
    ]));
    expect(punta('Kickboxing')).toBeNull();
    expect(punta('Pilates')).toBeNull();
    fixture.destroy();
    fixture.nativeElement.remove();
    TestBed.resetTestingModule();

    crear(ficha([deporte({ mediasRallas: null, aplicaRallas: false })]));
    expect(punta()).toBeNull();
    fixture.destroy();
    fixture.nativeElement.remove();
    TestBed.resetTestingModule();

    crear(ficha([deporte()]), '');
    expect(punta()).toBeNull();
  });

  it('abre la misma hoja: título, disciplina y grado, cuenta y lo que falta; el foco vuelve a la punta al cerrar', () => {
    crear(ficha([deporte()]));
    const b = punta()!;
    b.focus();
    abrir();
    expect(hoja()).not.toBeNull();
    expect(punta()!.getAttribute('aria-expanded')).toBe('true');
    expect(texto(hoja()!.querySelector('h2'))).toBe('Ana Prueba Ficticia');
    expect(texto(hoja()!.querySelector('.rallas-hoja__sub'))).toMatch(/^Taekwondo · /);
    expect(cuenta()).toBe('2 de 5 rallas');
    hoja()!.querySelector<HTMLButtonElement>('.rallas-hoja__cerrar')!.click();
    fixture.detectChanges();
    expect(hoja()).toBeNull();
    expect(document.activeElement).toBe(punta());
  });

  it('en la página propia, la hoja cuelga del alcance .pn de la ficha (tokens y acento de la escuela)', () => {
    crear(ficha([deporte()]));
    abrir();
    const raiz = document.querySelector<HTMLElement>('pn-hoja-rallas')!;
    expect(raiz).not.toBeNull();
    const alcance = raiz.closest('.pn');
    expect(alcance).not.toBeNull();
    expect(alcance!.matches('section.ficha--pagina')).toBeTrue();
  });

  it('un paso se guarda al tocar, optimista; la lista del grupo recibe el valor nuevo', () => {
    crear(ficha([deporte()]));
    abrir();
    pulsar(1);
    expect(cuenta()).toBe('2 y media de 5 rallas');
    const req = http.expectOne(url);
    expect(req.request.body).toEqual({ paso: 1 });
    req.flush({ alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 5, aptoParaExamen: false });
    fixture.detectChanges();
    expect(cuenta()).toBe('2 y media de 5 rallas');
    expect(texto(bloqueDe('Taekwondo').querySelector('[data-rallas]'))).toBe('2 y media de 5 rallas');
    expect(cambios[cambios.length - 1]).toEqual({ alumnoId: 3, deporte: 'TAEKWONDO', medias: 5, apto: false });
  });

  it('si el paso falla, se deshace (también en la lista) y se avisa', () => {
    crear(ficha([deporte()]));
    abrir();
    pulsar(2);
    expect(cuenta()).toBe('3 de 5 rallas');
    http.expectOne(url).flush({ mensaje: 'No caben más rallas: el máximo son 5.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(cuenta()).toBe('2 de 5 rallas');
    expect(avisos.error).toHaveBeenCalledWith('No caben más rallas: el máximo son 5.');
    expect(cambios[cambios.length - 1]).toEqual({ alumnoId: 3, deporte: 'TAEKWONDO', medias: 4 });
  });

  it('pasos deshabilitados como en Pasar lista: con 0 medias, quitar no llama al API', () => {
    crear(ficha([deporte({ mediasRallas: 0 })]));
    abrir();
    pulsar(-1);
    pulsar(-2);
    http.expectNone(url);
    expect(cuenta()).toBe('Sin rallas');
    expect(texto(hoja()!.querySelector('.rallas-hoja__nota'))).toBe('Aún no tiene rallas.');
  });

  it('con 10 medias, «Marcar apta» cierra la hoja, confirma y deja apta con las rallas a 0', async () => {
    crear(ficha([deporte({ mediasRallas: 10 })], {}, 'FEMENINO'));
    confirmar.and.callFake(() => {
      expect(hoja()).toBeNull();
      return Promise.resolve({ confirmado: true, valor: null, respuesta: { alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 0, aptoParaExamen: true } });
    });
    abrir();
    const apto = hoja()!.querySelector<HTMLButtonElement>('[data-apto]')!;
    expect(texto(apto)).toBe('Marcar apta para examen');
    apto.click();
    await fixture.whenStable();
    fixture.detectChanges();
    const opciones = confirmar.calls.mostRecent().args[0];
    expect(opciones.titulo).toBe('¿Marcar a Ana apta para examen?');
    expect(opciones.mensaje).toEqual([{ fuerte: 'Ana Prueba Ficticia' }, ' quedará apta para examen en Taekwondo y sus rallas volverán a 0.']);
    expect(avisos.exito).toHaveBeenCalledWith('Ana queda apta para examen en Taekwondo. Sus rallas vuelven a 0.');
    expect(cambios[cambios.length - 1]).toEqual({ alumnoId: 3, deporte: 'TAEKWONDO', medias: 0, apto: true });
    expect(bloqueDe('Taekwondo').querySelector('[data-listo]')).toBeNull();
    expect(texto(bloqueDe('Taekwondo').querySelector('[data-rallas]'))).toBe('Sin rallas');
    opciones.ejecutar().subscribe();
    const post = http.expectOne(`${url}/apto`);
    expect(post.request.body).toBeNull();
    post.flush({ alumnoId: 3, deporte: 'TAEKWONDO', mediasRallas: 0, aptoParaExamen: true });
  });

  it('si «Marcar apto» falla, se avisa y no cambia nada', async () => {
    crear(ficha([deporte({ mediasRallas: 10 })]));
    confirmar.and.resolveTo({ confirmado: true, valor: null, error: new HttpErrorResponse({ status: 409, error: { mensaje: 'Solo se marca apto desde aquí con las 5 rallas.' } }) });
    abrir();
    hoja()!.querySelector<HTMLButtonElement>('[data-apto]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(avisos.error).toHaveBeenCalledWith('Solo se marca apto desde aquí con las 5 rallas.');
    expect(cambios).toEqual([]);
    expect(texto(bloqueDe('Taekwondo').querySelector('[data-rallas]'))).toBe('5 de 5 rallas');
  });
});
