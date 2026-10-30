import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AlumnoDeLista, ClaseAsistencia } from '../../interfaces/asistencia';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { PasarListaComponent } from './pasar-lista.component';

const clase = (cambios: Partial<ClaseAsistencia> = {}): ClaseAsistencia => ({
  turnoId: 9, fecha: '2026-10-28', horaInicio: '18:00', horaFin: '19:00', pasada: false, presentes: null, total: null, alumnos: 3, ...cambios,
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

  function crear(opciones: { prefijo?: 'profesor' | 'gestion'; consulta?: Record<string, string> } = {}): void {
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error']);
    confirmar = jasmine.createSpy('confirmar').and.resolveTo({ confirmado: true, valor: null });
    TestBed.configureTestingModule({
      imports: [PasarListaComponent],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: AvisosService, useValue: avisos },
        { provide: ConfirmacionService, useValue: { confirmar } },
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
    expect(texto('.pasar-lista__estado')).toBe('0 de 3 presentes');
    expect(filas().every((f) => f.getAttribute('aria-pressed') === 'false')).toBeTrue();
    filas()[1].click();
    fixture.detectChanges();
    expect(filas()[1].getAttribute('aria-pressed')).toBe('true');
    expect(filas()[1].textContent).toContain('Presente');
    expect(filas()[0].textContent).not.toContain('Presente');
    expect(texto('.pasar-lista__estado')).toBe('1 de 3 presentes');
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
    expect(texto('.pasar-lista__estado')).toBe('3 de 3 presentes');
    boton('Desmarcar todos')!.click();
    fixture.detectChanges();
    expect(texto('.pasar-lista__estado')).toBe('0 de 3 presentes');
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
    expect(put.request.body).toEqual({ presentes: [1, 3] });
    put.flush(clase({ presentes: 2, total: 3 }));
    fixture.detectChanges();
    expect(texto('.pasar-lista__guardada')).toBe('Lista guardada');
    expect(texto('.pasar-lista__estado')).toContain('2 de 3 presentes');
    expect(boton('Volver al grupo')?.getAttribute('href')).toBe('/mis-grupos/7');
    expect(boton('Guardar lista')).toBeUndefined();
    filas()[1].click();
    fixture.detectChanges();
    expect(boton('Guardar lista')).toBeTruthy();
    expect(texto('.pasar-lista__estado')).toBe('3 de 3 presentes');
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
    expect(texto('.pasar-lista__estado')).toBe('1 de 2 presentes');
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
      expect(texto('.pasar-lista__estado')).toBe('2 de 3 presentes');
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
});
