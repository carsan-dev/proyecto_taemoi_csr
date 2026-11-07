import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AlumnoDeLista, ClaseAsistencia } from '../../interfaces/asistencia';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { PasarListaComponent } from './pasar-lista.component';

const clase = (cambios: Partial<ClaseAsistencia> = {}): ClaseAsistencia => ({
  turnoId: 9, fecha: '2026-10-28', horaInicio: '18:00', horaFin: '19:00', pasada: false, presentes: null, total: null, alumnos: 3, festivo: null, ...cambios,
});

const alumno = (id: number, nombre: string, presente = false): AlumnoDeLista => ({
  id, nombre, apellidos: 'Prueba', presente,
  deportes: [
    { deporte: 'KICKBOXING', deporteNombre: 'Kickboxing', grado: null, fechaGrado: null, apto: false, delGrupo: false },
    { deporte: 'TAEKWONDO', deporteNombre: 'Taekwondo', grado: { codigo: 'AMARILLO', nombre: 'Amarillo' }, fechaGrado: null, apto: false, delGrupo: true },
  ],
});

const ALUMNOS = [alumno(1, 'Ana'), alumno(2, 'Beto'), alumno(3, 'Cleo')];

describe('PasarListaComponent', () => {
  let fixture: ComponentFixture<PasarListaComponent>;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;
  let confirmar: jasmine.Spy;
  const api = environment.apiUrl;
  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (selector: string) => el().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
  const filas = () => Array.from(el().querySelectorAll<HTMLButtonElement>('.pasar-lista__fila'));
  const boton = (t: string) => Array.from(el().querySelectorAll<HTMLElement>('button, a')).find((b) => b.textContent?.replace(/\s+/g, ' ').trim() === t) as HTMLElement | undefined;

  function crear(opciones: { prefijo?: 'profesor' | 'gestion'; consulta?: Record<string, string>; variasSedes?: boolean } = {}): void {
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error']);
    confirmar = jasmine.createSpy('confirmar').and.resolveTo({ confirmado: true, valor: null });
    TestBed.configureTestingModule({
      imports: [PasarListaComponent],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: AvisosService, useValue: avisos },
        { provide: ConfirmacionService, useValue: { confirmar } },
        { provide: PermisosSesionService, useValue: { obtener: () => of({ variasSedes: opciones.variasSedes ?? false }) } },
        { provide: EndpointsService, useValue: { obtenerGrupoPorId: () => of({ nombre: 'Taekwondo gestión' }) } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              data: { prefijoAsistencia: opciones.prefijo ?? 'profesor' },
              paramMap: convertToParamMap({ grupoId: '7' }),
              queryParamMap: convertToParamMap(opciones.consulta ?? {}),
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PasarListaComponent);
    fixture.detectChanges();
  }

  const base = () => `${api}/profesor/grupos/7`;

  /** Contesta nombre del grupo y `/clases`; luego, la lista de la clase que se pida. */
  function arrancar(clases: { hoy?: ClaseAsistencia[]; recientes?: ClaseAsistencia[] }, lista?: { clase: ClaseAsistencia; alumnos: AlumnoDeLista[] }): void {
    http.expectOne(`${api}/profesor/grupos`).flush([{ id: 7, nombre: 'Infantil', sede: null, rol: 'TITULAR', alumnosActivos: 18, disciplina: null, turnos: [] }]);
    http.expectOne(`${base()}/clases`).flush({ hoy: clases.hoy ?? [], recientes: clases.recientes ?? [], proxima: null });
    if (lista) {
      http.expectOne(`${base()}/clases/${lista.clase.turnoId}/${lista.clase.fecha}`).flush(lista);
    }
    fixture.detectChanges();
  }

  afterEach(() => { http.verify(); fixture.destroy(); });

  it('esqueleto al cargar y cabecera según el boceto', () => {
    crear();
    expect(el().querySelector('pn-esqueleto')).not.toBeNull();
    arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
    expect(el().querySelector('pn-esqueleto')).toBeNull();
    expect(texto('h1')).toBe('Pasar lista');
    expect(texto('.pasar-lista__clase')).toContain('Miércoles 28 de octubre · 18:00–19:00');
    expect(boton('Cambiar clase')).toBeTruthy();
    expect(el().textContent).toContain('Toca a quien ha venido. Sin marcar cuenta como falta.');
    const migas = Array.from(el().querySelectorAll('.pasar-lista__migas a')).map((a) => [a.textContent?.trim(), a.getAttribute('href')]);
    expect(migas).toEqual([['Mis grupos', '/mis-grupos'], ['Infantil', '/mis-grupos/7']]);
  });

  it('abre la primera clase de hoy sin lista', () => {
    crear();
    const hecha = clase({ turnoId: 8, horaInicio: '17:00', horaFin: '18:00', presentes: 2, total: 3 });
    const sin = clase({ turnoId: 9 });
    arrancar({ hoy: [hecha, sin] }, { clase: sin, alumnos: ALUMNOS });
    expect(texto('.pasar-lista__clase')).toContain('18:00–19:00');
  });

  it('una clase festiva no se abre como inicial: solo se pide la siguiente normal sin lista', () => {
    crear();
    const festiva = clase({ turnoId: 8, horaInicio: '17:00', horaFin: '18:00', festivo: { id: 31, nombre: 'Feria', tipo: 'DIA', deUnDiaYUnaSede: true, fechaDesde: '2026-10-28', fechaHasta: '2026-10-28', sedes: 1 } });
    const normal = clase({ turnoId: 9 });
    arrancar({ hoy: [festiva, normal] }, { clase: normal, alumnos: ALUMNOS });
    expect(texto('.pasar-lista__clase')).toContain('18:00–19:00');
  });

  it('hoy = festiva y otra con lista: abre la que tiene lista', () => {
    crear();
    const festiva = clase({ turnoId: 8, horaInicio: '17:00', horaFin: '18:00', festivo: { id: 31, nombre: 'Feria', tipo: 'DIA', deUnDiaYUnaSede: true, fechaDesde: '2026-10-28', fechaHasta: '2026-10-28', sedes: 1 } });
    const hecha = clase({ turnoId: 9, presentes: 2, total: 3 });
    arrancar({ hoy: [festiva, hecha] }, { clase: hecha, alumnos: ALUMNOS });
    expect(texto('.pasar-lista__clase')).toContain('18:00–19:00');
  });

  it('con turno y fecha en la URL abre esa clase', () => {
    crear({ consulta: { turno: '8', fecha: '2026-10-26' } });
    const lunes = clase({ turnoId: 8, fecha: '2026-10-26' });
    arrancar({ hoy: [clase()], recientes: [lunes] }, { clase: lunes, alumnos: ALUMNOS });
    expect(texto('.pasar-lista__clase')).toContain('Lunes 26 de octubre');
  });

  it('sin hoy abre la más reciente con la hoja «Elegir clase» abierta', () => {
    crear();
    const lunes = clase({ fecha: '2026-10-26', presentes: 2, total: 3, pasada: true });
    arrancar({ recientes: [lunes] }, { clase: lunes, alumnos: [alumno(1, 'Ana', true), alumno(2, 'Beto', true), alumno(3, 'Cleo')] });
    expect(el().querySelector('dialog.pn-panel')).not.toBeNull();
    expect(texto('.pn-panel__titulo')).toBe('Elegir clase');
    expect(texto('.pn-panel__descripcion')).toBe('Últimos 14 días');
  });

  it('marcar: aria-pressed, «Presente» y la cuenta; sin guardar no hay marcas', () => {
    crear();
    arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: [alumno(1, 'Ana', true), alumno(2, 'Beto'), alumno(3, 'Cleo')] });
    expect(texto('.pasar-lista__estado')).toBe('0 presentes 3 faltas');
    expect(filas().every((f) => f.getAttribute('aria-pressed') === 'false')).toBeTrue();
    filas()[1].click();
    fixture.detectChanges();
    expect(filas()[1].getAttribute('aria-pressed')).toBe('true');
    expect(filas()[1].textContent).toContain('Presente');
    expect(filas()[0].textContent).not.toContain('Presente');
    expect(texto('.pasar-lista__estado')).toBe('1 presente 2 faltas');
  });

  it('muestra la disciplina del grupo, no la primera', () => {
    crear();
    arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
    expect(filas()[0].querySelector('.pasar-lista__grado')?.textContent).toContain('Amarillo');
    expect(filas()[0].querySelector('.pasar-lista__grado')?.textContent).not.toContain('Sin grado');
  });

  it('marcar todos y desmarcar todos', () => {
    crear();
    arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
    boton('Marcar todos')!.click();
    fixture.detectChanges();
    expect(filas().every((f) => f.getAttribute('aria-pressed') === 'true')).toBeTrue();
    expect(texto('.pasar-lista__estado')).toBe('3 presentes Ninguna falta');
    boton('Desmarcar todos')!.click();
    fixture.detectChanges();
    expect(texto('.pasar-lista__estado')).toBe('0 presentes 3 faltas');
  });

  it('guardar: PUT con los presentes, «Lista guardada» y «Volver al grupo»; un cambio vuelve a la barra inicial', () => {
    crear();
    arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
    filas()[0].click();
    filas()[2].click();
    fixture.detectChanges();
    boton('Guardar lista')!.click();
    const put = http.expectOne(`${base()}/clases/9/2026-10-28`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ presentes: [1, 3], motivos: {} });
    put.flush(clase({ presentes: 2, total: 3 }));
    fixture.detectChanges();
    expect(texto('.pasar-lista__guardada')).toBe('Lista guardada');
    expect(texto('.pasar-lista__estado')).toContain('2 de 3 presentes');
    expect(boton('Volver al grupo')?.getAttribute('href')).toBe('/mis-grupos/7');
    expect(boton('Guardar lista')).toBeUndefined();
    filas()[1].click();
    fixture.detectChanges();
    expect(boton('Guardar lista')).toBeTruthy();
    expect(texto('.pasar-lista__estado')).toBe('3 presentes Ninguna falta');
  });

  it('si guardar falla, conserva las marcas y avisa con el mensaje del servidor', () => {
    crear();
    arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
    filas()[0].click();
    fixture.detectChanges();
    boton('Guardar lista')!.click();
    http.expectOne(`${base()}/clases/9/2026-10-28`).flush({ mensaje: 'La fecha es futura.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(avisos.error).toHaveBeenCalledWith('La fecha es futura.');
    expect(filas()[0].getAttribute('aria-pressed')).toBe('true');
    expect(boton('Guardar lista')).toBeTruthy();
  });

  it('la lista de un lunes con otro tamaño que el grupo usa el tamaño de esa lista', () => {
    crear();
    const lunes = clase({ turnoId: 8, alumnos: 2 });
    arrancar({ hoy: [lunes] }, { clase: lunes, alumnos: [alumno(1, 'Ana'), alumno(2, 'Beto')] });
    filas()[0].click();
    fixture.detectChanges();
    expect(texto('.pasar-lista__estado')).toBe('1 presente 1 falta');
    expect(el().textContent).not.toContain('de 18');
  });

  it('turno sin alumnos: aviso y sin barra', () => {
    crear();
    const vacia = clase({ alumnos: 0 });
    arrancar({ hoy: [vacia] }, { clase: vacia, alumnos: [] });
    expect(el().querySelector('pn-bloque-vacio')?.textContent).toContain('Este turno no tiene alumnos asignados.');
    expect(el().querySelector('.pasar-lista__barra')).toBeNull();
    expect(boton('Marcar todos')).toBeUndefined();
  });

  it('error al cargar: bloque de error y reintentar', () => {
    crear();
    http.expectOne(`${api}/profesor/grupos`).flush([]);
    http.expectOne(`${base()}/clases`).flush({ mensaje: 'Sin acceso' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(el().querySelector('pn-bloque-error')?.textContent).toContain('Sin acceso');
    el().querySelector<HTMLButtonElement>('pn-bloque-error button')!.click();
    arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
    expect(filas().length).toBe(3);
  });

  describe('cambios sin guardar', () => {
    it('salir sin cambios no pregunta; con cambios pide confirmación y respeta «no»', async () => {
      crear();
      arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
      expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
      filas()[0].click();
      confirmar.and.resolveTo({ confirmado: false, valor: null });
      expect(await fixture.componentInstance.puedeSalir()).toBeFalse();
      expect(confirmar.calls.mostRecent().args[0].titulo).toBe('¿Salir sin guardar?');
      confirmar.and.resolveTo({ confirmado: true, valor: null });
      expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
    });

    it('tras guardar ya no hay cambios', async () => {
      crear();
      arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
      filas()[0].click();
      fixture.componentInstance.guardar();
      http.expectOne(`${base()}/clases/9/2026-10-28`).flush(clase({ presentes: 1, total: 3 }));
      expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
    });
  });

  describe('hoja «Elegir clase»', () => {
    const lunes = clase({ turnoId: 8, fecha: '2026-10-26', presentes: 16, total: 18, alumnos: 18 });
    const hoy = clase();

    it('lista las recientes con «Sin pasar» o «16 de 18», resalta la elegida y cierra con Esc devolviendo el foco', async () => {
      crear();
      arrancar({ hoy: [hoy], recientes: [hoy, lunes] }, { clase: hoy, alumnos: ALUMNOS });
      const abrir = boton('Cambiar clase')!;
      abrir.focus();
      abrir.click();
      fixture.detectChanges();
      const opciones = Array.from(el().querySelectorAll<HTMLButtonElement>('.pasar-lista__opcion'));
      expect(opciones.map((o) => o.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
        'Miércoles 28 de octubre Sin pasar', 'Lunes 26 de octubre 16 de 18',
      ]);
      expect(opciones[0].getAttribute('aria-current')).toBe('true');
      expect(opciones[1].getAttribute('aria-current')).toBeNull();
      el().querySelector('dialog')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      el().querySelector('dialog')!.dispatchEvent(new Event('cancel', { cancelable: true }));
      fixture.detectChanges();
      expect(el().querySelector('dialog.pn-panel')).toBeNull();
    });

    it('dos clases el mismo día: la fila lleva la hora', () => {
      crear();
      const tarde = clase({ turnoId: 10, horaInicio: '19:00', horaFin: '20:00' });
      arrancar({ hoy: [hoy, tarde], recientes: [tarde, hoy] }, { clase: hoy, alumnos: ALUMNOS });
      boton('Cambiar clase')!.click();
      fixture.detectChanges();
      const fechas = Array.from(el().querySelectorAll('.pasar-lista__opcion-fecha')).map((f) => f.textContent?.trim());
      expect(fechas).toEqual(['Miércoles 28 de octubre · 19:00', 'Miércoles 28 de octubre · 18:00']);
    });

    it('elegir otra clase la carga y deja su estado; sin cambios no pregunta', async () => {
      crear();
      arrancar({ hoy: [hoy], recientes: [hoy, lunes] }, { clase: hoy, alumnos: ALUMNOS });
      boton('Cambiar clase')!.click();
      fixture.detectChanges();
      el().querySelectorAll<HTMLButtonElement>('.pasar-lista__opcion')[1].click();
      await fixture.whenStable();
      expect(confirmar).not.toHaveBeenCalled();
      http.expectOne(`${base()}/clases/8/2026-10-26`).flush({ clase: lunes, alumnos: [alumno(1, 'Ana', true), alumno(2, 'Beto', true), alumno(3, 'Cleo')] });
      fixture.detectChanges();
      expect(texto('.pasar-lista__clase')).toContain('Lunes 26 de octubre');
      expect(texto('.pasar-lista__estado')).toBe('2 presentes 1 falta');
      expect(el().querySelector('dialog.pn-panel')).toBeNull();
    });

    it('con el PUT pendiente, elegir otra clase no la pide ni cambia la actual', async () => {
      crear();
      arrancar({ hoy: [hoy], recientes: [hoy, lunes] }, { clase: hoy, alumnos: ALUMNOS });
      filas()[0].click();
      fixture.detectChanges();
      boton('Guardar lista')!.click();
      fixture.detectChanges();
      const put = http.expectOne(`${base()}/clases/9/2026-10-28`);
      expect(boton('Cambiar clase')?.hasAttribute('disabled')).toBeTrue();
      await fixture.componentInstance.elegirClase(lunes);
      http.expectNone(`${base()}/clases/8/2026-10-26`);
      expect(fixture.componentInstance.clase).toEqual(hoy);
      put.flush(clase({ presentes: 1, total: 3 }));
      fixture.detectChanges();
      expect(fixture.componentInstance.clase?.turnoId).toBe(9);
      expect(texto('.pasar-lista__clase')).toContain('Miércoles 28 de octubre');
    });

    it('con lista guardada y turno vaciado, la fila de la hoja muestra «2 de 3»', () => {
      crear();
      const vaciada = clase({ alumnos: 0, presentes: 2, total: 3 });
      arrancar({ hoy: [hoy], recientes: [hoy, { ...lunes, ...vaciada, turnoId: 8, fecha: '2026-10-26' }] }, { clase: hoy, alumnos: ALUMNOS });
      boton('Cambiar clase')!.click();
      fixture.detectChanges();
      const estados = Array.from(el().querySelectorAll('.pasar-lista__opcion-estado')).map((e) => e.textContent?.trim());
      expect(estados).toEqual(['Sin pasar', '2 de 3']);
    });

    it('«Cambiar clase» y «Marcar todos» comparten fila, fuera de la fecha', () => {
      crear();
      arrancar({ hoy: [hoy] }, { clase: hoy, alumnos: ALUMNOS });
      const fila = el().querySelector('.pasar-lista__acciones')!;
      expect(Array.from(fila.querySelectorAll('button')).map((b) => b.textContent?.trim())).toEqual(['Cambiar clase', 'Marcar todos']);
      expect(el().querySelector('.pasar-lista__clase button')).toBeNull();
    });

    it('con cambios pide confirmación: «no» se queda, «sí» cambia y descarta las marcas', async () => {
      crear();
      arrancar({ hoy: [hoy], recientes: [hoy, lunes] }, { clase: hoy, alumnos: ALUMNOS });
      filas()[0].click();
      boton('Cambiar clase')!.click();
      fixture.detectChanges();
      confirmar.and.resolveTo({ confirmado: false, valor: null });
      el().querySelectorAll<HTMLButtonElement>('.pasar-lista__opcion')[1].click();
      await fixture.whenStable();
      expect(confirmar).toHaveBeenCalledTimes(1);
      expect(texto('.pasar-lista__clase')).toContain('Miércoles 28 de octubre');
      confirmar.and.resolveTo({ confirmado: true, valor: null });
      el().querySelectorAll<HTMLButtonElement>('.pasar-lista__opcion')[1].click();
      await fixture.whenStable();
      http.expectOne(`${base()}/clases/8/2026-10-26`).flush({ clase: lunes, alumnos: ALUMNOS });
      fixture.detectChanges();
      expect(texto('.pasar-lista__clase')).toContain('Lunes 26 de octubre');
    });
  });

  describe('gestión', () => {
    it('pide por /api/grupos/{id}, migas «Grupos / nombre /» y vuelve a gestión', () => {
      crear({ prefijo: 'gestion' });
      http.expectOne(`${api}/grupos/7/clases`).flush({ hoy: [clase()], recientes: [], proxima: null });
      http.expectOne(`${api}/grupos/7/clases/9/2026-10-28`).flush({ clase: clase(), alumnos: ALUMNOS });
      fixture.detectChanges();
      const migas = Array.from(el().querySelectorAll('.pasar-lista__migas a')).map((a) => [a.textContent?.trim(), a.getAttribute('href')]);
      expect(migas).toEqual([['Grupos', '/gruposListar'], ['Taekwondo gestión', '/gruposEditar/7']]);
      filas()[0].click();
      fixture.detectChanges();
      boton('Guardar lista')!.click();
      http.expectOne(`${api}/grupos/7/clases/9/2026-10-28`).flush(clase({ presentes: 1, total: 3 }));
      fixture.detectChanges();
      expect(boton('Volver al grupo')?.getAttribute('href')).toBe('/gruposEditar/7');
    });
  });

  describe('motivo de la falta (decisión 455)', () => {
    const motivos = () => Array.from(el().querySelectorAll<HTMLButtonElement>('.pasar-lista__motivo'));
    const opciones = () => Array.from(el().querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]'));
    const opcion = (t: string) => opciones().find((o) => o.textContent?.trim() === t)!;

    it('«Motivo» en cada fila sin marcar, también con la lista sin empezar; no en las presentes', () => {
      crear();
      arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
      expect(motivos().length).toBe(3);
      expect(motivos()[0].textContent?.trim()).toBe('Motivo');
      expect(motivos()[0].getAttribute('aria-haspopup')).toBe('menu');
      expect(motivos()[0].getAttribute('aria-expanded')).toBe('false');
      expect(motivos()[0].getAttribute('aria-label')).toBe('Motivo de la falta de Ana Prueba: sin motivo');
      // Hermano de la fila, no anidado en ella.
      expect(filas()[0].querySelector('.pasar-lista__motivo')).toBeNull();
      filas()[0].click();
      fixture.detectChanges();
      expect(motivos().length).toBe(2);
      expect(el().querySelectorAll('.pasar-lista__item')[0].querySelector('.pasar-lista__motivo')).toBeNull();
    });

    it('elegir: menú con «Sin motivo» y los cuatro, foco en la elegida; al elegir se cierra, se ve la pastilla y vuelve el foco', () => {
      crear();
      arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
      motivos()[1].click();
      fixture.detectChanges();
      const menu = el().querySelector('[role="menu"]')!;
      expect(menu.getAttribute('aria-label')).toBe('Motivo de la falta de Beto Prueba');
      expect(opciones().map((o) => o.textContent?.trim())).toEqual(['Sin motivo', 'Injustificada', 'Médica', 'Familiar', 'Otra justificada']);
      expect(opcion('Sin motivo').getAttribute('aria-checked')).toBe('true');
      expect(document.activeElement).toBe(opcion('Sin motivo'));
      expect(motivos()[1].getAttribute('aria-expanded')).toBe('true');
      opcion('Médica').click();
      fixture.detectChanges();
      expect(el().querySelector('[role="menu"]')).toBeNull();
      expect(motivos()[1].textContent?.trim()).toBe('Médica');
      expect(motivos()[1].classList.contains('pasar-lista__motivo--elegido')).toBeTrue();
      expect(motivos()[1].getAttribute('aria-label')).toBe('Motivo de la falta de Beto Prueba: Médica');
      expect(document.activeElement).toBe(motivos()[1]);
      expect(texto('.pasar-lista__estado')).toBe('0 presentes 3 faltas · 1 con motivo');
      // Cambiarlo y quitarlo.
      motivos()[1].click();
      fixture.detectChanges();
      expect(document.activeElement).toBe(opcion('Médica'));
      opcion('Familiar').click();
      fixture.detectChanges();
      expect(motivos()[1].textContent?.trim()).toBe('Familiar');
      motivos()[1].click();
      fixture.detectChanges();
      opcion('Sin motivo').click();
      fixture.detectChanges();
      expect(motivos()[1].textContent?.trim()).toBe('Motivo');
      expect(texto('.pasar-lista__estado')).toBe('0 presentes 3 faltas');
    });

    it('teclado: flechas con vuelta, Inicio, Fin y Esc (cierra y devuelve el foco)', () => {
      crear();
      arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
      motivos()[0].click();
      fixture.detectChanges();
      const menu = el().querySelector('[role="menu"]')!;
      const tecla = (key: string) => {
        (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        fixture.detectChanges();
      };
      tecla('ArrowDown');
      expect(document.activeElement).toBe(opcion('Injustificada'));
      tecla('ArrowUp');
      tecla('ArrowUp');
      expect(document.activeElement).toBe(opcion('Otra justificada'));
      tecla('Home');
      expect(document.activeElement).toBe(opcion('Sin motivo'));
      tecla('End');
      expect(document.activeElement).toBe(opcion('Otra justificada'));
      expect(menu.isConnected).toBeTrue();
      tecla('Escape');
      expect(el().querySelector('[role="menu"]')).toBeNull();
      expect(document.activeElement).toBe(motivos()[0]);
    });

    describe('menú sobre la barra fija', () => {
      const original = Element.prototype.getBoundingClientRect;
      /** La fila empieza en `fila` y la barra en `barra`; el menú, de 240 px, baja desde el pie de la fila o sube desde su cabeza. */
      function medidas(fila: number, barra: number): void {
        spyOn(Element.prototype, 'getBoundingClientRect').and.callFake(function (this: Element) {
          if (this.classList.contains('pasar-lista__menu')) {
            return this.classList.contains('pasar-lista__menu--arriba') ? new DOMRect(0, fila + 6 - 240, 240, 240) : new DOMRect(0, fila + 50, 240, 240);
          }
          if (this.classList.contains('pasar-lista__item')) return new DOMRect(0, fila, 600, 56);
          if (this.classList.contains('pasar-lista__barra')) return new DOMRect(0, barra, 600, 64);
          return original.call(this);
        });
      }

      it('se abre hacia arriba si hacia abajo lo tapa la barra', () => {
        crear();
        arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
        medidas(600, 700);
        motivos()[2].click();
        fixture.detectChanges();
        expect(el().querySelector('[role="menu"]')!.classList).toContain('pasar-lista__menu--arriba');
        expect(document.activeElement).toBe(opcion('Sin motivo'));
      });

      it('si cabe hacia abajo, abajo', () => {
        crear();
        arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
        medidas(300, 700);
        motivos()[0].click();
        fixture.detectChanges();
        expect(el().querySelector('[role="menu"]')!.classList).not.toContain('pasar-lista__menu--arriba');
      });

      it('sin sitio arriba ni abajo, desplaza la página hasta verlo entero', () => {
        crear();
        arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
        medidas(120, 300);
        const desplazar = spyOn(window, 'scrollBy');
        motivos()[0].click();
        fixture.detectChanges();
        expect(el().querySelector('[role="menu"]')!.classList).not.toContain('pasar-lista__menu--arriba');
        // El menú acaba en 120 + 50 + 240 = 410; la barra empieza en 300 y se dejan 8 de aire.
        expect(desplazar.calls.mostRecent().args as unknown[]).toEqual([{ top: 118 }]);
      });
    });

    it('un clic fuera cierra el menú sin elegir', () => {
      crear();
      arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
      motivos()[0].click();
      fixture.detectChanges();
      el().querySelector<HTMLElement>('h1')!.click();
      fixture.detectChanges();
      expect(el().querySelector('[role="menu"]')).toBeNull();
      expect(motivos()[0].textContent?.trim()).toBe('Motivo');
    });

    it('marcar presente borra el motivo; desmarcar vuelve sin motivo', () => {
      crear();
      arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
      motivos()[0].click();
      fixture.detectChanges();
      opcion('Injustificada').click();
      fixture.detectChanges();
      filas()[0].click();
      fixture.detectChanges();
      expect(texto('.pasar-lista__estado')).toBe('1 presente 2 faltas');
      filas()[0].click();
      fixture.detectChanges();
      expect(motivos()[0].textContent?.trim()).toBe('Motivo');
      // «Marcar todos» también los borra.
      motivos()[2].click();
      fixture.detectChanges();
      opcion('Médica').click();
      boton('Marcar todos')!.click();
      fixture.detectChanges();
      boton('Desmarcar todos')!.click();
      fixture.detectChanges();
      expect(motivos().map((m) => m.textContent?.trim())).toEqual(['Motivo', 'Motivo', 'Motivo']);
    });

    it('guardar: PUT con los motivos de las faltas; tras guardar no hay cambios', async () => {
      crear();
      arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
      filas()[0].click();
      fixture.detectChanges();
      motivos()[0].click();
      fixture.detectChanges();
      opcion('Otra justificada').click();
      fixture.detectChanges();
      boton('Guardar lista')!.click();
      const put = http.expectOne(`${base()}/clases/9/2026-10-28`);
      expect(put.request.body).toEqual({ presentes: [1], motivos: { 2: 'OTRA_JUSTIFICADA' } });
      put.flush(clase({ presentes: 1, total: 3 }));
      expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
    });

    it('lista guardada: trae sus motivos; cambiar uno cuenta como cambio sin guardar y volver al de partida no', async () => {
      crear();
      const guardada = clase({ presentes: 1, total: 3 });
      arrancar({ hoy: [guardada] }, {
        clase: guardada,
        alumnos: [alumno(1, 'Ana', true), { ...alumno(2, 'Beto'), motivo: 'MEDICA' }, alumno(3, 'Cleo')],
      });
      expect(motivos().map((m) => m.textContent?.trim())).toEqual(['Médica', 'Motivo']);
      expect(texto('.pasar-lista__estado')).toBe('1 presente 2 faltas · 1 con motivo');
      expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
      motivos()[0].click();
      fixture.detectChanges();
      opcion('Familiar').click();
      confirmar.and.resolveTo({ confirmado: false, valor: null });
      expect(await fixture.componentInstance.puedeSalir()).toBeFalse();
      expect(confirmar.calls.mostRecent().args[0].mensaje).toBeTruthy();
      motivos()[0].click();
      fixture.detectChanges();
      opcion('Médica').click();
      confirmar.calls.reset();
      expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
      expect(confirmar).not.toHaveBeenCalled();
    });

    it('lista sin guardar: los motivos que venga trayendo no cuentan', () => {
      crear();
      arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: [{ ...alumno(1, 'Ana'), motivo: 'MEDICA' }, alumno(2, 'Beto'), alumno(3, 'Cleo')] });
      expect(motivos()[0].textContent?.trim()).toBe('Motivo');
    });
  });

  describe('sede en la línea de la clase', () => {
    it('con dos o más sedes, la línea la nombra; con una, no', () => {
      crear({ prefijo: 'gestion', variasSedes: true });
      http.expectOne(`${api}/grupos/7/clases`).flush({ hoy: [clase()], recientes: [], proxima: null });
      http.expectOne(`${api}/grupos/7/clases/9/2026-10-28`).flush({ clase: clase(), alumnos: ALUMNOS, sede: { id: 2, nombre: 'Estepa' } });
      fixture.detectChanges();
      expect(texto('.pasar-lista__clase')).toBe('Miércoles 28 de octubre · 18:00–19:00 · Estepa');
    });

    it('una sola sede: sin nombre', () => {
      crear({ prefijo: 'gestion' });
      http.expectOne(`${api}/grupos/7/clases`).flush({ hoy: [clase()], recientes: [], proxima: null });
      http.expectOne(`${api}/grupos/7/clases/9/2026-10-28`).flush({ clase: clase(), alumnos: ALUMNOS, sede: { id: 2, nombre: 'Estepa' } });
      fixture.detectChanges();
      expect(texto('.pasar-lista__clase')).toBe('Miércoles 28 de octubre · 18:00–19:00');
    });
  });

  describe('festivos (decisiones 456-460)', () => {
    const gestion = `${api}/grupos/7`;
    const ESTEPA = { id: 2, nombre: 'Estepa' };
    const festivoDia = {
      id: 31, nombre: 'Feria de Estepa', tipo: 'DIA' as const, deUnDiaYUnaSede: true, fechaDesde: '2026-10-28', fechaHasta: '2026-10-28', sedes: 1,
    };

    function arrancarGestion(c: ClaseAsistencia, alumnos: AlumnoDeLista[] = ALUMNOS, recientes: ClaseAsistencia[] = []): void {
      crear({ prefijo: 'gestion', variasSedes: true });
      http.expectOne(`${gestion}/clases`).flush({ hoy: [c], recientes, proxima: null });
      http.expectOne(`${gestion}/clases/${c.turnoId}/${c.fecha}`).flush({ clase: c, alumnos, sede: ESTEPA });
      fixture.detectChanges();
    }

    const dialogo = () => el().querySelector<HTMLDialogElement>('dialog[data-dialogo="marcar-festivo"]');

    it('gestión ve «Marcar festivo» junto a «Cambiar clase»; el profesor no', () => {
      arrancarGestion(clase());
      const izq = el().querySelector('.pasar-lista__acciones-izq')!;
      expect(Array.from(izq.querySelectorAll('button')).map((b) => b.textContent?.trim())).toEqual(['Cambiar clase', 'Marcar festivo']);
      fixture.destroy();
      TestBed.resetTestingModule();
      crear();
      arrancar({ hoy: [clase()] }, { clase: clase(), alumnos: ALUMNOS });
      expect(boton('Marcar festivo')).toBeUndefined();
    });

    it('sin sede del grupo no se ofrece (falla cerrado)', () => {
      crear({ prefijo: 'gestion' });
      http.expectOne(`${gestion}/clases`).flush({ hoy: [clase()], recientes: [], proxima: null });
      http.expectOne(`${gestion}/clases/9/2026-10-28`).flush({ clase: clase(), alumnos: ALUMNOS, sede: null });
      fixture.detectChanges();
      expect(boton('Marcar festivo')).toBeUndefined();
    });

    it('diálogo con el impacto: título, frase, clases con lista, nota y nombre con contador; foco en el nombre', () => {
      arrancarGestion(clase());
      boton('Marcar festivo')!.click();
      const impacto = http.expectOne(`${api}/festivos/impacto`);
      expect(impacto.request.body).toEqual({ tipo: 'DIA', fechaDesde: '2026-10-28', fechaHasta: '2026-10-28', sedeIds: [2] });
      const deClase = (horaInicio: string, horaFin: string, grupo: string, presentes: number | null = null, total: number | null = null) => ({
        sedeId: 2, sede: 'Estepa', fecha: '2026-10-28', horaInicio, horaFin, grupo, conLista: presentes !== null, presentes, total,
      });
      impacto.flush({
        clases: 4, conLista: 2,
        conListaDetalle: [
          { sedeId: 2, sede: 'Estepa', fecha: '2026-10-28', horaInicio: '17:00', grupo: 'Iniciación' },
          { sedeId: 2, sede: 'Estepa', fecha: '2026-10-28', horaInicio: '19:00', grupo: 'Avanzados' },
        ],
        clasesDetalle: [
          deClase('17:00', '18:00', 'Iniciación', 11, 14), deClase('18:00', '19:00', 'Taekwondo gestión'),
          deClase('19:00', '20:00', 'Avanzados', 9, 12), deClase('20:30', '21:30', 'Adultos'),
        ],
      });
      fixture.detectChanges();
      const d = dialogo()!;
      expect(d.open).toBeTrue();
      expect(d.querySelector('h2')?.textContent?.trim()).toBe('¿Marcar festivo el miércoles 28 de octubre en Estepa?');
      expect(d.getAttribute('aria-labelledby')).toBe(d.querySelector('h2')!.id);
      const mensaje = d.querySelector('.festivo-dialogo__mensaje')!.textContent!.replace(/\s+/g, ' ').trim();
      expect(mensaje).toBe('Ese día no habrá lista en las clases de Estepa que aún no la tienen. Afecta a 2 de sus 4 clases:');
      const filasClases = Array.from(d.querySelectorAll('.festivo-dialogo__clases li')).map((li) => li.textContent?.replace(/\s+/g, ' ').trim());
      expect(filasClases).toEqual([
        '17:00–18:00 Iniciación Con lista · 11 de 14', '18:00–19:00 Taekwondo gestión (esta)',
        '19:00–20:00 Avanzados Con lista · 9 de 12', '20:30–21:30 Adultos',
      ]);
      expect(d.querySelector('.festivo-dialogo__nota')?.textContent).toContain('2 clases ya tienen lista y se quedan como están.');
      const input = d.querySelector<HTMLInputElement>('input')!;
      expect(document.activeElement).toBe(input);
      expect(input.getAttribute('maxlength')).toBe('60');
      expect(d.querySelector('label')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Nombre del festivo (opcional)');
      input.value = 'Feria de Estepa';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(d.querySelector('.festivo-dialogo__ayuda')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Sale en la lista y en el PDF. 15 / 60');
    });

    it('con 0 clases: «No afecta a ninguna clase.», no «Afecta a 0 clases»', () => {
      arrancarGestion(clase());
      boton('Marcar festivo')!.click();
      http.expectOne(`${api}/festivos/impacto`).flush({ clases: 0, conLista: 0, conListaDetalle: [] });
      fixture.detectChanges();
      const t = dialogo()!.querySelector('.festivo-dialogo__mensaje')!.textContent!.replace(/\s+/g, ' ').trim();
      expect(t).toBe('No afecta a ninguna clase.');
      expect(t).not.toContain('0 clases');
    });

    it('sin listas guardadas: «ninguna clase», sin nota; cancelar cierra sin crear', () => {
      arrancarGestion(clase());
      boton('Marcar festivo')!.click();
      http.expectOne(`${api}/festivos/impacto`).flush({ clases: 1, conLista: 0, conListaDetalle: [] });
      fixture.detectChanges();
      const d = dialogo()!;
      expect(d.querySelector('.festivo-dialogo__mensaje')!.textContent!.replace(/\s+/g, ' ').trim())
        .toBe('Ese día no habrá lista en ninguna clase de Estepa. Afecta a 1 clase.');
      expect(d.querySelector('.festivo-dialogo__clases')).toBeNull();
      expect(d.querySelector('.festivo-dialogo__nota')).toBeNull();
      Array.from(d.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Cancelar')!.click();
      fixture.detectChanges();
      expect(dialogo()).toBeNull();
    });

    it('diálogo: más de 20 clases, las del detalle y «y N más»', () => {
      arrancarGestion(clase());
      boton('Marcar festivo')!.click();
      const clasesDetalle = Array.from({ length: 20 }, (_, i) => ({
        sedeId: 2, sede: 'Estepa', fecha: '2026-10-28', horaInicio: `${String(8 + Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '30' : '00'}`,
        horaFin: '23:00', grupo: `Grupo ${i + 1}`, conLista: false, presentes: null, total: null,
      }));
      http.expectOne(`${api}/festivos/impacto`).flush({ clases: 23, conLista: 0, conListaDetalle: [], clasesDetalle });
      fixture.detectChanges();
      const d = dialogo()!;
      expect(d.querySelector('.festivo-dialogo__mensaje')!.textContent!.replace(/\s+/g, ' ').trim())
        .toBe('Ese día no habrá lista en ninguna clase de Estepa. Afecta a 23 clases:');
      const filasClases = Array.from(d.querySelectorAll('.festivo-dialogo__clases li'));
      expect(filasClases.length).toBe(21);
      expect(filasClases[0].textContent?.replace(/\s+/g, ' ').trim()).toBe('08:00–23:00 Grupo 1');
      expect(filasClases[20].textContent?.trim()).toBe('y 3 más');
    });

    it('crear: POST de un día con la sede del grupo y el nombre; aviso y la lista se recarga festiva', () => {
      arrancarGestion(clase());
      boton('Marcar festivo')!.click();
      http.expectOne(`${api}/festivos/impacto`).flush({ clases: 4, conLista: 0, conListaDetalle: [] });
      fixture.detectChanges();
      const input = dialogo()!.querySelector<HTMLInputElement>('input')!;
      input.value = '  Feria de Estepa ';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      Array.from(dialogo()!.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Marcar festivo')!.click();
      fixture.detectChanges();
      const post = http.expectOne(`${api}/festivos`);
      expect(post.request.method).toBe('POST');
      expect(post.request.body).toEqual({ tipo: 'DIA', fechaDesde: '2026-10-28', fechaHasta: '2026-10-28', sedeIds: [2], nombre: 'Feria de Estepa' });
      post.flush({ id: 31 }, { status: 201, statusText: 'Created' });
      fixture.detectChanges();
      expect(avisos.exito).toHaveBeenCalledWith('Festivo marcado.');
      expect(dialogo()).toBeNull();
      const festiva = clase({ festivo: festivoDia });
      http.expectOne(`${gestion}/clases`).flush({ hoy: [festiva], recientes: [festiva], proxima: null });
      http.expectOne(`${gestion}/clases/9/2026-10-28`).flush({ clase: festiva, alumnos: ALUMNOS, sede: ESTEPA });
      fixture.detectChanges();
      expect(texto('.pasar-lista__festivo h2')).toBe('Festivo en Estepa · Feria de Estepa');
    });

    it('si crear falla, avisa con el mensaje del servidor y el diálogo sigue abierto', () => {
      arrancarGestion(clase());
      boton('Marcar festivo')!.click();
      http.expectOne(`${api}/festivos/impacto`).flush({ clases: 4, conLista: 0, conListaDetalle: [] });
      fixture.detectChanges();
      Array.from(dialogo()!.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Marcar festivo')!.click();
      http.expectOne(`${api}/festivos`).flush({ mensaje: 'Ya hay un festivo ese día en Estepa.' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();
      expect(avisos.error).toHaveBeenCalledWith('Ya hay un festivo ese día en Estepa.');
      expect(dialogo()).not.toBeNull();
    });

    it('si el impacto falla, avisa y no abre el diálogo', () => {
      arrancarGestion(clase());
      boton('Marcar festivo')!.click();
      http.expectOne(`${api}/festivos/impacto`).flush({}, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      expect(avisos.error).toHaveBeenCalledWith('No se han podido consultar las clases de ese día. Inténtalo de nuevo.');
      expect(dialogo()).toBeNull();
    });

    it('clase festiva de un día y una sede: bloque, sin lista ni barra, y «Quitar festivo» con confirmación (DELETE)', async () => {
      const festiva = clase({ festivo: festivoDia });
      arrancarGestion(festiva);
      expect(texto('.pasar-lista__festivo h2')).toBe('Festivo en Estepa · Feria de Estepa');
      expect(texto('.pasar-lista__festivo p')).toBe('No hay clase en Estepa el miércoles 28 de octubre. No cuenta como falta para nadie.');
      expect(boton('Ver en Festivos')).toBeUndefined();
      expect(el().querySelector('.pasar-lista__festivo')?.getAttribute('role')).toBe('status');
      expect(el().querySelector('.pasar-lista__lista')).toBeNull();
      expect(el().querySelector('.pasar-lista__barra')).toBeNull();
      expect(boton('Marcar todos')).toBeUndefined();
      expect(boton('Marcar festivo')).toBeUndefined();
      expect(el().textContent).not.toContain('Toca a quien ha venido');
      confirmar.and.callFake(async (o: { ejecutar: () => { subscribe: (x: object) => void } }) => {
        o.ejecutar().subscribe({});
        return { confirmado: true, valor: null };
      });
      boton('Quitar festivo')!.click();
      await fixture.whenStable();
      const opciones = confirmar.calls.mostRecent().args[0];
      expect(opciones.titulo).toBe('¿Quitar el festivo del miércoles 28 de octubre en Estepa?');
      expect(opciones.mensaje).toBe('Ese día volverá a haber clase en Estepa: sus clases pedirán lista otra vez. No se borra ninguna lista.');
      expect(opciones.destructivo).toBeTrue();
      const borrar = http.expectOne(`${api}/festivos/31`);
      expect(borrar.request.method).toBe('DELETE');
      borrar.flush(null, { status: 204, statusText: 'No Content' });
      expect(avisos.exito).toHaveBeenCalledWith('Festivo quitado.');
      http.expectOne(`${gestion}/clases`).flush({ hoy: [clase()], recientes: [], proxima: null });
      http.expectOne(`${gestion}/clases/9/2026-10-28`).flush({ clase: clase(), alumnos: ALUMNOS, sede: ESTEPA });
      fixture.detectChanges();
      expect(el().querySelector('.pasar-lista__lista')).not.toBeNull();
    });

    it('festivo de varios días y sedes: texto del rango y «Ver en Festivos»', () => {
      arrancarGestion(clase({
        fecha: '2026-11-02',
        festivo: { id: 32, nombre: 'Puente de Todos los Santos', tipo: 'RANGO', deUnDiaYUnaSede: false, fechaDesde: '2026-10-30', fechaHasta: '2026-11-02', sedes: 2 },
      }));
      expect(texto('.pasar-lista__festivo h2')).toBe('Festivo en Estepa · Puente de Todos los Santos');
      expect(texto('.pasar-lista__festivo p')).toBe(
        'Del 30 de octubre al 2 de noviembre, en varias sedes. Esta clase no tiene lista ese día y no cuenta como falta para nadie.');
      expect(boton('Ver en Festivos')?.getAttribute('href')).toBe('/festivos');
      expect(boton('Quitar festivo')).toBeUndefined();
    });

    it('festivo anual: «Cada año», sin nombre, y «Ver en Festivos»', () => {
      arrancarGestion(clase({
        fecha: '2026-12-08',
        festivo: { id: 33, nombre: null, tipo: 'ANUAL', deUnDiaYUnaSede: false, fechaDesde: '2026-12-08', fechaHasta: '2026-12-08', sedes: 1 },
      }));
      expect(texto('.pasar-lista__festivo h2')).toBe('Festivo en Estepa');
      expect(texto('.pasar-lista__festivo p')).toBe('Cada año, 8 de diciembre, en Estepa. Esta clase no tiene lista ese día y no cuenta como falta para nadie.');
      expect(boton('Ver en Festivos')).toBeTruthy();
      expect(boton('Quitar festivo')).toBeUndefined();
    });

    it('el profesor ve el festivo sin acciones', () => {
      crear();
      const festiva = clase({ festivo: festivoDia });
      http.expectOne(`${api}/profesor/grupos`).flush([]);
      http.expectOne(`${base()}/clases`).flush({ hoy: [festiva], recientes: [], proxima: null });
      http.expectOne(`${base()}/clases/9/2026-10-28`).flush({ clase: festiva, alumnos: ALUMNOS, sede: ESTEPA });
      fixture.detectChanges();
      expect(el().querySelector('.pasar-lista__festivo')?.textContent).toContain('Si ese día hay clase, pide a la escuela que quite el festivo.');
      expect(boton('Quitar festivo')).toBeUndefined();
      expect(boton('Ver en Festivos')).toBeUndefined();
    });

    it('hoja «Elegir clase»: estado «Festivo» con su icono', () => {
      const festiva = clase({ turnoId: 8, fecha: '2026-10-22', festivo: festivoDia });
      arrancarGestion(clase(), ALUMNOS, [clase(), festiva]);
      boton('Cambiar clase')!.click();
      fixture.detectChanges();
      const estados = Array.from(el().querySelectorAll('.pasar-lista__opcion-estado'));
      expect(estados.map((e) => e.textContent?.trim())).toEqual(['Sin pasar', 'Festivo']);
      expect(estados[1].querySelector('svg')).not.toBeNull();
    });

    it('409 de festivo al guardar: avisa con el mensaje y recarga para ver la clase festiva', () => {
      arrancarGestion(clase());
      filas()[0].click();
      fixture.detectChanges();
      boton('Guardar lista')!.click();
      http.expectOne(`${gestion}/clases/9/2026-10-28`).flush({ mensaje: 'Ese día es festivo en Estepa.' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();
      expect(avisos.error).toHaveBeenCalledWith('Ese día es festivo en Estepa.');
      http.expectOne(`${gestion}/clases`).flush({ hoy: [clase()], recientes: [], proxima: null });
      const festiva = clase({ festivo: festivoDia });
      http.expectOne(`${gestion}/clases/9/2026-10-28`).flush({ clase: festiva, alumnos: ALUMNOS, sede: ESTEPA });
      fixture.detectChanges();
      expect(boton('Guardar lista')).toBeUndefined();
      expect(el().textContent).toContain('Feria de Estepa');
    });

    it('otro 409 al guardar: avisa con su mensaje, no recarga y conserva lo marcado', () => {
      arrancarGestion(clase());
      filas()[0].click();
      fixture.detectChanges();
      boton('Guardar lista')!.click();
      http.expectOne(`${gestion}/clases/9/2026-10-28`).flush(
        { mensaje: 'Esta lista se guardó con otro grupo y no se puede cambiar desde aquí.' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();
      expect(avisos.error).toHaveBeenCalledWith('Esta lista se guardó con otro grupo y no se puede cambiar desde aquí.');
      http.expectNone(`${gestion}/clases`);
      http.expectNone(`${gestion}/clases/9/2026-10-28`);
      expect(boton('Guardar lista')).toBeDefined();
      expect(filas()[0].getAttribute('aria-pressed')).toBe('true');
    });

    it('409 de concurrencia al guardar: avisa y recarga la clase y la hoja', () => {
      arrancarGestion(clase());
      filas()[0].click();
      fixture.detectChanges();
      boton('Guardar lista')!.click();
      http.expectOne(`${gestion}/clases/9/2026-10-28`).flush(
        { mensaje: 'Otra persona ha cambiado este dato a la vez; vuelve a intentarlo' }, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();
      expect(avisos.error).toHaveBeenCalledWith('Otra persona ha cambiado este dato a la vez; vuelve a intentarlo');
      http.expectOne(`${gestion}/clases`).flush({ hoy: [clase()], recientes: [], proxima: null });
      http.expectOne(`${gestion}/clases/9/2026-10-28`).flush({ clase: clase(), alumnos: ALUMNOS, sede: ESTEPA });
    });
  });
});
