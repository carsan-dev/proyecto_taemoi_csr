import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AlumnoDeLista, ClaseAsistencia, ListaDeClase } from '../../interfaces/asistencia';
import { GradoProfesor } from '../../interfaces/profesor';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { PasarListaComponent } from './pasar-lista.component';

const clase = (cambios: Partial<ClaseAsistencia> = {}): ClaseAsistencia => ({
  turnoId: 9, fecha: '2026-10-28', horaInicio: '18:00', horaFin: '19:00', pasada: false, presentes: null, total: null, alumnos: 3, festivo: null, festivoConLista: null, ...cambios,
});

const AMARILLO: GradoProfesor = { codigo: 'AMARILLO', nombre: 'Amarillo', color1: '#FFFF00', color2: null };

function alumno(
  id: number, nombre: string,
  r: { medias?: number | null; aplica?: boolean; puede?: boolean; grado?: GradoProfesor | null; genero?: string | null } = {},
): AlumnoDeLista {
  const aplica = r.aplica ?? true;
  return {
    id, nombre, apellidos: 'Prueba', presente: false, genero: r.genero ?? null, puedeCambiarRallas: r.puede ?? aplica,
    deportes: [{
      deporte: 'TAEKWONDO', deporteNombre: 'Taekwondo', grado: r.grado === undefined ? AMARILLO : r.grado, fechaGrado: null, apto: false,
      delGrupo: true, mediasRallas: aplica ? (r.medias ?? 0) : null, aplicaRallas: aplica,
    }],
  };
}

describe('PasarListaComponent · rallas hacia el examen (decisiones 510-513)', () => {
  let fixture: ComponentFixture<PasarListaComponent>;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;
  let confirmar: jasmine.Spy;
  const api = environment.apiUrl;
  const base = (prefijo: 'profesor' | 'gestion') => (prefijo === 'profesor' ? `${api}/profesor/grupos/7` : `${api}/grupos/7`);
  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined) => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const items = () => Array.from(el().querySelectorAll<HTMLElement>('.pasar-lista__item'));
  const cinto = (i: number) => items()[i].querySelector<HTMLElement>('.pasar-lista__cinto')!;
  const hoja = () => document.querySelector<HTMLDialogElement>('dialog[data-dialogo="rallas"]');
  const cuenta = () => texto(hoja()?.querySelector('[data-cuenta]'));

  function crear(prefijo: 'profesor' | 'gestion' = 'profesor'): void {
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error']);
    confirmar = jasmine.createSpy('confirmar');
    TestBed.configureTestingModule({
      imports: [PasarListaComponent],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: AvisosService, useValue: avisos },
        { provide: ConfirmacionService, useValue: { confirmar } },
        { provide: PermisosSesionService, useValue: { obtener: () => of({ variasSedes: false }) } },
        { provide: EndpointsService, useValue: { obtenerGrupoPorId: () => of({ nombre: 'Taekwondo infantil' }) } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              data: { prefijoAsistencia: prefijo },
              paramMap: convertToParamMap({ grupoId: '7' }),
              queryParamMap: convertToParamMap({}),
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PasarListaComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    if (prefijo === 'profesor') {
      http.expectOne(`${api}/profesor/grupos`).flush([{ id: 7, nombre: 'Taekwondo infantil', sede: null, rol: 'TITULAR', alumnosActivos: 3, disciplina: null, turnos: [] }]);
    }
  }

  function arrancar(lista: Partial<ListaDeClase> & { alumnos: AlumnoDeLista[] }, prefijo: 'profesor' | 'gestion' = 'profesor'): void {
    const c = lista.clase ?? clase();
    http.expectOne(`${base(prefijo)}/clases`).flush({ hoy: [c], recientes: [], proxima: null });
    http.expectOne(`${base(prefijo)}/clases/${c.turnoId}/${c.fecha}`).flush({ sede: null, puedeCambiarRallas: true, ...lista, clase: c });
    fixture.detectChanges();
  }

  function abrir(i: number): void {
    (cinto(i) as HTMLButtonElement).click();
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

  it('cabecera: «Toca el cinturón para dar o quitar rallas.»; la punta es un botón aparte de la fila con su etiqueta', () => {
    crear();
    arrancar({ alumnos: [alumno(1, 'Adrián', { medias: 5 }), alumno(2, 'Beto')] });
    expect(texto(el().querySelector('[data-ayuda-rallas]'))).toBe('Toca el cinturón para dar o quitar rallas.');
    const b = cinto(0);
    expect(b.tagName).toBe('BUTTON');
    expect(b.closest('.pasar-lista__fila')).toBeNull();
    expect(b.getAttribute('aria-label')).toBe('Rallas de Adrián Prueba: 2 y media de 5 rallas');
    expect(b.getAttribute('aria-haspopup')).toBe('dialog');
    expect(b.querySelectorAll('pn-cinturon [data-ralla]').length).toBe(3);
    expect(texto(items()[0].querySelector('.pasar-lista__grado'))).toContain(', 2 y media de 5 rallas');
  });

  it('ayudante: ve las rallas sin botón y la frase del boceto', () => {
    crear();
    arrancar({ puedeCambiarRallas: false, alumnos: [alumno(1, 'Adrián', { medias: 4, puede: false })] });
    expect(texto(el().querySelector('[data-ayuda-rallas]'))).toBe('Ves las rallas; solo las cambian el titular y la escuela.');
    expect(cinto(0).tagName).toBe('SPAN');
    expect(cinto(0).getAttribute('aria-hidden')).toBe('true');
    expect(cinto(0).querySelectorAll('[data-ralla]').length).toBe(2);
    expect(el().querySelector('button.pasar-lista__cinto')).toBeNull();
  });

  it('sin grado: punta vacía y sin hoja; PUM/DAN o disciplina sin rallas: cinturón sin rallas y sin hoja', () => {
    crear();
    arrancar({
      alumnos: [
        alumno(1, 'Nora', { grado: null, aplica: false }),
        alumno(2, 'Javier', { grado: { codigo: 'ROJO_NEGRO_1_PUM', nombre: 'Rojo-negro 1.º PUM', color1: '#FF0000', color2: '#000000' }, aplica: false }),
        alumno(3, 'Adrián', { medias: 2 }),
      ],
    });
    expect(cinto(0).tagName).toBe('SPAN');
    expect(cinto(0).querySelector('rect[stroke-dasharray]')).not.toBeNull();
    expect(cinto(1).tagName).toBe('SPAN');
    expect(cinto(1).querySelectorAll('[data-ralla]').length).toBe(0);
    expect(cinto(1).querySelector('[data-franja]')).not.toBeNull();
    expect(cinto(2).tagName).toBe('BUTTON');
  });

  it('sin nadie con rallas no hay frase de rallas en la cabecera', () => {
    crear();
    arrancar({ alumnos: [alumno(1, 'Nora', { aplica: false })] });
    expect(el().querySelector('[data-ayuda-rallas]')).toBeNull();
  });

  it('«Listo/Lista para examen» con 10 medias, presente o ausente', () => {
    crear();
    arrancar({ alumnos: [alumno(1, 'Lucía', { medias: 10, genero: 'FEMENINO' }), alumno(2, 'Marcos', { medias: 10 }), alumno(3, 'Adrián', { medias: 9 })] });
    items()[0].querySelector<HTMLButtonElement>('.pasar-lista__fila')!.click();
    fixture.detectChanges();
    expect(texto(items()[0].querySelector('[data-listo]'))).toBe('Lista para examen');
    expect(texto(items()[1].querySelector('[data-listo]'))).toBe('Listo para examen');
    expect(items()[2].querySelector('[data-listo]')).toBeNull();
  });

  it('un paso se guarda al tocar (POST con el paso), optimista: la hoja cambia antes de la respuesta', () => {
    crear();
    arrancar({ alumnos: [alumno(1, 'Adrián', { medias: 4 })] });
    abrir(0);
    expect(hoja()!.open).toBeTrue();
    expect(cuenta()).toBe('2 de 5 rallas');
    pulsar(1);
    expect(cuenta()).toBe('2 y media de 5 rallas');
    const post = http.expectOne(`${base('profesor')}/alumnos/1/rallas`);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ paso: 1 });
    // Mientras la llamada está en curso, otro toque no hace nada.
    pulsar(2);
    http.expectNone(`${base('profesor')}/alumnos/1/rallas`);
    post.flush({ alumnoId: 1, deporte: 'TAEKWONDO', mediasRallas: 5, aptoParaExamen: false });
    fixture.detectChanges();
    expect(cuenta()).toBe('2 y media de 5 rallas');
    expect(cinto(0).querySelectorAll('[data-ralla]').length).toBe(3);
    pulsar(2);
    http.expectOne(`${base('profesor')}/alumnos/1/rallas`).flush({ alumnoId: 1, deporte: 'TAEKWONDO', mediasRallas: 7, aptoParaExamen: false });
    fixture.detectChanges();
    expect(cuenta()).toBe('3 y media de 5 rallas');
  });

  it('si el paso falla (409 o red), se deshace y se avisa', () => {
    crear('gestion');
    arrancar({ alumnos: [alumno(1, 'Adrián', { medias: 9 })] }, 'gestion');
    abrir(0);
    pulsar(1);
    expect(cuenta()).toBe('5 de 5 rallas');
    http.expectOne(`${base('gestion')}/alumnos/1/rallas`).flush({ mensaje: 'No caben más rallas: el máximo son 5.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(cuenta()).toBe('4 y media de 5 rallas');
    expect(avisos.error).toHaveBeenCalledWith('No caben más rallas: el máximo son 5.');
    pulsar(-1);
    http.expectOne(`${base('gestion')}/alumnos/1/rallas`).error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(cuenta()).toBe('4 y media de 5 rallas');
    expect(avisos.error).toHaveBeenCalledWith('No hay conexión con el servidor. Comprueba la cobertura y vuelve a intentarlo.');
    pulsar(-1);
    http.expectOne(`${base('gestion')}/alumnos/1/rallas`).flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(cuenta()).toBe('4 y media de 5 rallas');
    expect(avisos.error).toHaveBeenCalledWith('No se ha podido guardar la ralla. Inténtalo de nuevo.');
  });

  it('un paso deshabilitado no llama al API', () => {
    crear();
    arrancar({ alumnos: [alumno(1, 'Hugo', { medias: 0 })] });
    abrir(0);
    pulsar(-1);
    pulsar(-2);
    http.expectNone(`${base('profesor')}/alumnos/1/rallas`);
    expect(cuenta()).toBe('Sin rallas');
  });

  it('con 10 medias, «Marcar apta para examen» cierra la hoja, confirma y deja apta con las rallas a 0', async () => {
    crear();
    arrancar({ alumnos: [alumno(1, 'Lucía', { medias: 10, genero: 'FEMENINO' })] });
    confirmar.and.callFake(() => {
      expect(hoja()).toBeNull();
      return Promise.resolve({ confirmado: true, valor: null, respuesta: { alumnoId: 1, deporte: 'TAEKWONDO', mediasRallas: 0, aptoParaExamen: true } });
    });
    abrir(0);
    hoja()!.querySelector<HTMLButtonElement>('[data-apto]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    const opciones = confirmar.calls.mostRecent().args[0];
    expect(opciones.titulo).toBe('¿Marcar a Lucía apta para examen?');
    expect(opciones.mensaje).toEqual([{ fuerte: 'Lucía Prueba' }, ' quedará apta para examen en Taekwondo y sus rallas volverán a 0.']);
    expect(opciones.accion).toBe('Marcar apta');
    expect(avisos.exito).toHaveBeenCalledWith('Lucía queda apta para examen en Taekwondo. Sus rallas vuelven a 0.');
    expect(items()[0].querySelector('[data-listo]')).toBeNull();
    expect(cinto(0).querySelectorAll('[data-ralla]').length).toBe(0);
    // `ejecutar` llama a la ruta de «apto» (sin cuerpo).
    opciones.ejecutar().subscribe();
    const post = http.expectOne(`${base('profesor')}/alumnos/1/rallas/apto`);
    expect(post.request.body).toBeNull();
    post.flush({ alumnoId: 1, deporte: 'TAEKWONDO', mediasRallas: 0, aptoParaExamen: true });
  });

  it('si «Marcar apto» falla, se avisa y no cambia nada', async () => {
    crear();
    arrancar({ alumnos: [alumno(1, 'Marcos', { medias: 10 })] });
    confirmar.and.resolveTo({ confirmado: true, valor: null, error: new HttpErrorResponse({ status: 409, error: { mensaje: 'Solo se marca apto desde aquí con las 5 rallas.' } }) });
    abrir(0);
    hoja()!.querySelector<HTMLButtonElement>('[data-apto]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(avisos.error).toHaveBeenCalledWith('Solo se marca apto desde aquí con las 5 rallas.');
    expect(texto(items()[0].querySelector('[data-listo]'))).toBe('Listo para examen');
  });

  it('festivo: sin lista ni cinturones, con la frase del boceto', () => {
    crear();
    const festiva = clase({ festivo: { id: 3, nombre: null, tipo: 'DIA', deUnDiaYUnaSede: true, fechaDesde: '2026-10-28', fechaHasta: '2026-10-28', sedes: 1 } });
    arrancar({ clase: festiva, alumnos: [alumno(1, 'Adrián', { medias: 3 })] });
    expect(el().querySelector('.pasar-lista__lista')).toBeNull();
    expect(el().querySelector('pn-cinturon')).toBeNull();
    expect(texto(el().querySelector('[data-festivo-rallas]'))).toBe('Sin lista no hay cinturones: las rallas se dan en otra clase.');
  });

  it('a 320 px, una fila ausente con motivo y cinturón no se sale ni se solapa', () => {
    crear();
    arrancar({ alumnos: [alumno(1, 'María de los Ángeles Fernández-Villaverde', { medias: 9 })] });
    const host = el();
    host.style.display = 'block';
    host.style.width = '320px';
    fixture.detectChanges();
    const li = items()[0];
    const caja = li.getBoundingClientRect();
    const partes = ['.pasar-lista__fila', '.pasar-lista__motivo', '.pasar-lista__cinto'].map((s) => li.querySelector(s)!.getBoundingClientRect());
    for (const p of partes) {
      expect(p.right).toBeLessThanOrEqual(caja.right + 0.5);
    }
    const solapan = (a: DOMRect, b: DOMRect) => a.left < b.right - 0.5 && b.left < a.right - 0.5 && a.top < b.bottom - 0.5 && b.top < a.bottom - 0.5;
    expect(solapan(partes[0], partes[1]) || solapan(partes[0], partes[2]) || solapan(partes[1], partes[2])).toBeFalse();
  });
});
