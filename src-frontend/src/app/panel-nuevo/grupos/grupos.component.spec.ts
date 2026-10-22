import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { environment } from '../../../environments/environment';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { GrupoResumen } from '../../interfaces/grupo-resumen';
import { TurnoResumen } from '../../interfaces/turno-resumen';
import { LoadingService } from '../../servicios/generales/loading.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { GruposComponent } from './grupos.component';

const API = environment.apiUrl;
const T = (id: number, diaSemana: string, horaInicio: string, horaFin: string) => ({ id, diaSemana, horaInicio, horaFin });

// Datos nuevos en cada prueba: la pantalla mueve turnos entre listas.
function datosGrupos(): GrupoResumen[] {
  return [
    { id: 1, nombre: 'Taekwondo Infantil', deporte: 'TAEKWONDO', rangoEdadMin: 6, rangoEdadMax: 12, numAlumnos: 18,
      turnos: [T(101, 'Lunes', '17:00', '18:00'), T(102, 'Miércoles', '17:00', '18:00')] },
    { id: 2, nombre: 'Kickboxing Adultos', deporte: 'KICKBOXING', rangoEdadMin: 16, rangoEdadMax: null, numAlumnos: 1_204,
      turnos: [T(103, 'Martes', '19:00', '20:00'), T(104, 'Jueves', '20:00', '21:00'), T(105, 'Sábado', '10:00', '11:00')] },
    { id: 3, nombre: 'Taekwondo Adultos', deporte: 'TAEKWONDO', rangoEdadMin: null, rangoEdadMax: null, numAlumnos: 22, turnos: [] },
    { id: 4, nombre: 'Kickboxing Peques', deporte: 'KICKBOXING', rangoEdadMin: null, rangoEdadMax: 7, numAlumnos: 1,
      turnos: [T(106, 'Viernes', '17:00', '17:45')] },
  ];
}

function datosTurnos(): TurnoResumen[] {
  const conGrupo = datosGrupos().flatMap((g) =>
    g.turnos.map((t) => ({ ...t, grupoId: g.id, grupoNombre: g.nombre, tipoGrupo: null as string | null }))
  );
  return [
    ...conGrupo,
    { id: 127, diaSemana: 'Viernes', horaInicio: '18:00', horaFin: '19:00', grupoId: null, grupoNombre: 'Sin grupo', tipoGrupo: null },
    { id: 128, diaSemana: 'Sábado', horaInicio: '09:00', horaFin: '10:00', grupoId: null, grupoNombre: 'Sin grupo', tipoGrupo: null },
  ];
}

const CONTEO = { Taekwondo: { active: 1_234, total: 1_300 }, 'Taekwondo Competición': { active: 12, total: 14 }, Kickboxing: { active: 50, total: 61 } };

function alumnosTipo(): any[] {
  return Array.from({ length: 12 }, (_, i) => ({
    id: 300 + i,
    nombre: `Nombre${i}`,
    apellidos: i === 3 ? 'Buscado Ficticio' : 'Apellido Ficticio',
    numeroExpediente: 700 + i,
    grado: 'Naranja',
    activo: i !== 4,
    fotoAlumno: null,
  }));
}

describe('GruposComponent (8b-3c, Grupos del panel nuevo)', () => {
  let fixture: ComponentFixture<GruposComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let loading: jasmine.SpyObj<LoadingService>;

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function texto(nodo: Element | null | undefined): string {
    return (nodo?.textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  async function esperar(): Promise<void> {
    for (let i = 0; i < 10; i++) {
      await Promise.resolve();
    }
    pintar();
  }

  function dialogo(): HTMLDialogElement | null {
    return document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  }

  function botonDialogo(t: string): HTMLButtonElement {
    const b = Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => (x.getAttribute('aria-label') ?? x.textContent ?? '').trim() === t
    );
    if (!b) {
      throw new Error(`Sin botón «${t}» en el diálogo`);
    }
    return b;
  }

  function avisos(): { tipo: string; texto: string }[] {
    return Array.from(document.querySelectorAll<HTMLElement>('.pn-aviso')).map((a) => ({
      tipo: a.classList.contains('pn-aviso--error') ? 'error' : 'otro',
      texto: a.querySelector('.pn-aviso__texto')!.textContent!.trim(),
    }));
  }

  function tarjeta(id: number): HTMLElement {
    return el().querySelector<HTMLElement>(`.grupos__tarjeta[data-grupo="${id}"]`)!;
  }

  function dato(id: number, etiqueta: string): string {
    const dts = Array.from(tarjeta(id).querySelectorAll('dt'));
    const dt = dts.find((d) => texto(d) === etiqueta);
    return texto(dt?.nextElementSibling);
  }

  function boton(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => (x.getAttribute('aria-label') ?? texto(x)) === t
    );
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  function segmentos(): HTMLButtonElement[] {
    return Array.from(el().querySelectorAll<HTMLButtonElement>('.grupos__filtro [role="radio"]'));
  }

  function crear(): void {
    fixture = TestBed.createComponent(GruposComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function lecturas(): { grupos: TestRequest[]; turnos: TestRequest[]; conteo: TestRequest[] } {
    return {
      grupos: http.match(`${API}/grupos/resumen`),
      turnos: http.match(`${API}/turnos/resumen`),
      conteo: http.match(`${API}/grupos/conteo-alumnos`),
    };
  }

  function responder(grupos = datosGrupos(), turnos = datosTurnos(), conteo: object = CONTEO): void {
    const l = lecturas();
    expect(l.grupos.length).toBe(1);
    expect(l.turnos.length).toBe(1);
    expect(l.conteo.length).toBe(1);
    l.grupos[0].flush(grupos);
    l.turnos[0].flush(turnos);
    l.conteo[0].flush(conteo);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    quitarTokens = ponerTokensDePrueba();
    loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    await TestBed.configureTestingModule({
      imports: [GruposComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LoadingService, useValue: loading },
        {
          provide: CatalogoService,
          useValue: {
            disciplinasActivas: () => [
              { codigo: 'TAEKWONDO', nombre: 'Taekwondo' },
              { codigo: 'KICKBOXING', nombre: 'Kickboxing' },
              { codigo: 'PILATES', nombre: 'Pilates' },
            ],
            etiquetaDisciplina: (c: string) => c,
            catalogo: () => ({ sistemasGrados: [{ grados: [{ nombre: 'Naranja', color1: '#F28C28' }] }] }),
          },
        },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    quitarTokens();
  });

  // ------------------------------------------------------------------ lecturas, cabecera y estados

  it('pide grupos, turnos y conteo una vez, con credenciales y ERROR_HANDLED_LOCALLY (su fallo lo enseña la pantalla)', () => {
    crear();
    const l = lecturas();
    for (const r of [l.grupos[0], l.turnos[0], l.conteo[0]]) {
      expect(r.request.method).toBe('GET');
      expect(r.request.withCredentials).toBeTrue();
      expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    }
    l.grupos[0].flush([]);
    l.turnos[0].flush([]);
    l.conteo[0].flush({});
    expect(loading.show).not.toHaveBeenCalled();
  });

  it('cabecera: «Grupos», «N grupos · M turnos» con plurales y «Nuevo turno» / «Nuevo grupo» a las rutas de hoy', () => {
    crear();
    responder();
    expect(texto(el().querySelector('h1'))).toBe('Grupos');
    expect(texto(el().querySelector('.grupos__subtitulo'))).toBe('4 grupos · 8 turnos');
    const turno = el().querySelector<HTMLAnchorElement>('.grupos__boton-secundario')!;
    const grupo = el().querySelector<HTMLAnchorElement>('.grupos__boton-primario')!;
    expect(texto(turno)).toBe('Nuevo turno');
    expect(turno.getAttribute('href')).toBe('/turnosCrear');
    expect(texto(grupo)).toBe('Nuevo grupo');
    expect(grupo.getAttribute('href')).toBe('/gruposCrear');
    expect(el().querySelectorAll('.grupos__boton-primario').length).toBe(1);
  });

  it('cabecera en singular: «1 grupo · 1 turno»', () => {
    crear();
    const [g] = datosGrupos();
    const uno = { ...g, turnos: [g.turnos[0]] };
    responder([uno], [{ ...g.turnos[0], grupoId: 1, grupoNombre: g.nombre, tipoGrupo: null }]);
    expect(texto(el().querySelector('.grupos__subtitulo'))).toBe('1 grupo · 1 turno');
  });

  it('esqueleto con la forma de las tarjetas a los 300 ms, y nada antes', fakeAsync(() => {
    crear();
    tick(299);
    fixture.detectChanges();
    expect(el().querySelector('.pn-esqueleto__forma')).toBeNull();
    tick(1);
    fixture.detectChanges();
    expect(el().querySelectorAll('.grupos__esqueleto-tarjeta').length).toBeGreaterThanOrEqual(3);
    expect(el().querySelector('pn-esqueleto [role="status"]')!.textContent).toContain('Cargando grupos');
    responder();
    expect(el().querySelector('pn-esqueleto')).toBeNull();
    flush();
  }));

  it('error de grupos: bloque con «Reintentar» que vuelve a pedirlos, sin SweetAlert2', () => {
    crear();
    const l = lecturas();
    l.grupos[0].flush({}, { status: 500, statusText: 'Error' });
    l.turnos[0].flush(datosTurnos());
    l.conteo[0].flush(CONTEO);
    fixture.detectChanges();
    const error = el().querySelector('.grupos__lista-estado pn-bloque-error')!;
    expect(texto(error)).toContain('No se pudieron cargar los grupos');
    expect(document.querySelector('.swal2-container')).toBeNull();
    boton(error, 'Reintentar').click();
    fixture.detectChanges();
    const [otra] = http.match(`${API}/grupos/resumen`);
    expect(otra.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    otra.flush(datosGrupos());
    fixture.detectChanges();
    expect(el().querySelectorAll('.grupos__tarjeta').length).toBe(4);
  });

  it('error de turnos: bloque en «Turnos sin grupo» con «Reintentar»; los grupos se ven', () => {
    crear();
    const l = lecturas();
    l.grupos[0].flush(datosGrupos());
    l.turnos[0].flush({}, { status: 500, statusText: 'Error' });
    l.conteo[0].flush(CONTEO);
    fixture.detectChanges();
    expect(el().querySelectorAll('.grupos__tarjeta').length).toBe(4);
    const error = el().querySelector('.grupos__sin-grupo pn-bloque-error')!;
    expect(texto(error)).toContain('No se pudieron cargar los turnos');
    boton(error, 'Reintentar').click();
    http.expectOne(`${API}/turnos/resumen`).flush(datosTurnos());
    fixture.detectChanges();
    expect(el().querySelectorAll('.grupos__sin-grupo li').length).toBe(2);
  });

  it('error del conteo: bloque en la fila de alumnos por tipo con «Reintentar»', () => {
    crear();
    const l = lecturas();
    l.grupos[0].flush(datosGrupos());
    l.turnos[0].flush(datosTurnos());
    l.conteo[0].flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    const error = el().querySelector('.grupos__tipos pn-bloque-error')!;
    expect(texto(error)).toContain('No se pudo leer cuántos alumnos hay por tipo');
    boton(error, 'Reintentar').click();
    http.expectOne(`${API}/grupos/conteo-alumnos`).flush(CONTEO);
    fixture.detectChanges();
    expect(el().querySelectorAll('.grupos__tipo').length).toBe(3);
  });

  it('sin grupos: bloque vacío con «Nuevo grupo»', () => {
    crear();
    responder([], []);
    const vacio = el().querySelector('pn-bloque-vacio')!;
    expect(texto(vacio)).toContain('Todavía no hay grupos');
    expect(vacio.querySelector('a')!.getAttribute('href')).toBe('/gruposCrear');
  });

  // ------------------------------------------------------------------ alumnos por tipo

  it('alumnos por tipo: un botón por tipo de hoy con sus activos en es-ES', () => {
    crear();
    responder();
    expect(Array.from(el().querySelectorAll('.grupos__tipo')).map(texto)).toEqual([
      'Taekwondo · 1.234',
      'Taekwondo Competición · 12',
      'Kickboxing · 50',
    ]);
  });

  it('alumnos por tipo: abre el panel lateral con la lista de hoy (solo activos, buscador, 8 por página) y lleva al alumno', fakeAsync(() => {
    crear();
    responder();
    const navegar = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    (el().querySelectorAll<HTMLButtonElement>('.grupos__tipo')[1]).click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/grupos/tipo/Taekwondo%20Competici%C3%B3n/alumnos`);
    expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    const panel = el().querySelector<HTMLDialogElement>('dialog.pn-panel')!;
    expect(panel.open).toBeTrue();
    expect(texto(panel.querySelector('.pn-panel__titulo'))).toBe('Alumnos de Taekwondo Competición');
    r.flush(alumnosTipo());
    fixture.detectChanges();
    // Solo activos por defecto: 11 de 12, 8 en la primera página.
    expect(texto(panel.querySelector('.tipo__contador'))).toBe('Mostrando 11 de 12 alumnos');
    expect(panel.querySelectorAll('.tipo__alumno').length).toBe(8);
    expect(texto(panel.querySelector('.tipo__pagina-actual'))).toBe('Página 1 de 2');
    boton(panel, 'Página siguiente').click();
    fixture.detectChanges();
    expect(panel.querySelectorAll('.tipo__alumno').length).toBe(3);
    // Todos.
    const activos = panel.querySelector<HTMLInputElement>('#tipo-solo-activos')!;
    expect(activos.checked).toBeTrue();
    activos.click();
    fixture.detectChanges();
    expect(texto(panel.querySelector('.tipo__contador'))).toBe('Mostrando 12 alumnos');
    // Buscador con la espera de hoy (300 ms).
    const buscar = panel.querySelector<HTMLInputElement>('input[type="search"]')!;
    buscar.value = 'buscado';
    buscar.dispatchEvent(new Event('input'));
    tick(299);
    fixture.detectChanges();
    expect(panel.querySelectorAll('.tipo__alumno').length).toBe(8);
    tick(1);
    fixture.detectChanges();
    expect(panel.querySelectorAll('.tipo__alumno').length).toBe(1);
    expect(texto(panel.querySelector('.tipo__alumno'))).toContain('Nombre3 Buscado Ficticio');
    expect(texto(panel.querySelector('.tipo__alumno'))).toContain('Exp. 703');
    expect(texto(panel.querySelector('.tipo__alumno'))).toContain('Naranja');
    panel.querySelector<HTMLButtonElement>('.tipo__alumno')!.click();
    fixture.detectChanges();
    expect(navegar).toHaveBeenCalledWith(['/alumnosEditar', 303]);
    expect(el().querySelector('dialog.pn-panel')).toBeNull();
    flush();
  }));

  it('alumnos por tipo: sin coincidencias, el texto de hoy; la X cierra el panel', fakeAsync(() => {
    crear();
    responder();
    el().querySelector<HTMLButtonElement>('.grupos__tipo')!.click();
    fixture.detectChanges();
    http.expectOne(`${API}/grupos/tipo/Taekwondo/alumnos`).flush([]);
    fixture.detectChanges();
    const panel = el().querySelector<HTMLDialogElement>('dialog.pn-panel')!;
    expect(texto(panel)).toContain('No hay alumnos en este grupo');
    boton(panel, 'Cerrar').click();
    fixture.detectChanges();
    expect(el().querySelector('dialog.pn-panel')).toBeNull();
    flush();
  }));

  it('alumnos por tipo: el fallo se enseña dentro del panel con «Reintentar» (sin SweetAlert2)', fakeAsync(() => {
    crear();
    responder();
    el().querySelector<HTMLButtonElement>('.grupos__tipo')!.click();
    fixture.detectChanges();
    http.expectOne(`${API}/grupos/tipo/Taekwondo/alumnos`).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    const panel = el().querySelector<HTMLDialogElement>('dialog.pn-panel')!;
    expect(texto(panel.querySelector('pn-bloque-error'))).toContain('No hemos podido obtener los alumnos del grupo');
    expect(document.querySelector('.swal2-container')).toBeNull();
    boton(panel, 'Reintentar').click();
    fixture.detectChanges();
    http.expectOne(`${API}/grupos/tipo/Taekwondo/alumnos`).flush(alumnosTipo());
    fixture.detectChanges();
    expect(panel.querySelectorAll('.tipo__alumno').length).toBe(8);
    flush();
  }));

  // ------------------------------------------------------------------ filtro por disciplina

  it('filtro: radiogroup con «Todas» y cada disciplina con su número de grupos', () => {
    crear();
    responder();
    const grupo = el().querySelector('.grupos__filtro')!;
    expect(grupo.getAttribute('role')).toBe('radiogroup');
    expect(grupo.getAttribute('aria-label')).toBe('Filtrar por disciplina');
    expect(segmentos().map(texto)).toEqual(['Todas · 4', 'Taekwondo · 2', 'Kickboxing · 2', 'Pilates · 0']);
    expect(segmentos().map((s) => s.getAttribute('aria-checked'))).toEqual(['true', 'false', 'false', 'false']);
    expect(segmentos().map((s) => s.getAttribute('tabindex'))).toEqual(['0', '-1', '-1', '-1']);
  });

  it('filtro: elegir una disciplina deja solo su sección; Pilates sin grupos da el bloque vacío con «Ver todas»', () => {
    crear();
    responder();
    expect(Array.from(el().querySelectorAll('.grupos__seccion-titulo')).map(texto)).toEqual(['Taekwondo', 'Kickboxing']);
    segmentos()[2].click();
    fixture.detectChanges();
    expect(segmentos()[2].getAttribute('aria-checked')).toBe('true');
    expect(Array.from(el().querySelectorAll('.grupos__seccion-titulo')).map(texto)).toEqual(['Kickboxing']);
    expect(Array.from(el().querySelectorAll('.grupos__tarjeta')).map((t) => t.getAttribute('data-grupo'))).toEqual(['2', '4']);
    segmentos()[3].click();
    fixture.detectChanges();
    expect(el().querySelectorAll('.grupos__tarjeta').length).toBe(0);
    const vacio = el().querySelector('pn-bloque-vacio')!;
    expect(texto(vacio)).toContain('No hay grupos de Pilates');
    boton(vacio, 'Ver todas').click();
    fixture.detectChanges();
    expect(el().querySelectorAll('.grupos__tarjeta').length).toBe(4);
    expect(segmentos()[0].getAttribute('aria-checked')).toBe('true');
  });

  it('filtro: flechas, Inicio y Fin mueven la elección y el foco (tabindex itinerante, vuelta al principio)', () => {
    crear();
    responder();
    const tecla = (key: string) => {
      (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
      fixture.detectChanges();
    };
    segmentos()[0].focus();
    tecla('ArrowRight');
    expect(document.activeElement).toBe(segmentos()[1]);
    expect(segmentos()[1].getAttribute('aria-checked')).toBe('true');
    expect(segmentos().map((s) => s.getAttribute('tabindex'))).toEqual(['-1', '0', '-1', '-1']);
    tecla('ArrowDown');
    expect(document.activeElement).toBe(segmentos()[2]);
    tecla('End');
    expect(document.activeElement).toBe(segmentos()[3]);
    tecla('ArrowRight');
    expect(document.activeElement).toBe(segmentos()[0]);
    tecla('ArrowLeft');
    expect(document.activeElement).toBe(segmentos()[3]);
    tecla('Home');
    expect(document.activeElement).toBe(segmentos()[0]);
    tecla('ArrowUp');
    expect(document.activeElement).toBe(segmentos()[3]);
    expect(segmentos()[3].getAttribute('aria-checked')).toBe('true');
  });

  // ------------------------------------------------------------------ secciones y tarjetas

  it('secciones por disciplina con el total de alumnos y los grupos en el orden de hoy', () => {
    crear();
    responder();
    const secciones = Array.from(el().querySelectorAll('.grupos__seccion'));
    expect(secciones.map((s) => texto(s.querySelector('.grupos__seccion-total')))).toEqual(['40 alumnos', '1.205 alumnos']);
    expect(Array.from(secciones[0].querySelectorAll('.grupos__tarjeta')).map((t) => t.getAttribute('data-grupo'))).toEqual(['1', '3']);
  });

  it('tarjeta: nombre que lleva a Editar y los datos Alumnos, Edad y Horario', () => {
    crear();
    responder();
    const nombre = tarjeta(1).querySelector<HTMLAnchorElement>('.grupos__nombre')!;
    expect(texto(nombre)).toBe('Taekwondo Infantil');
    expect(nombre.getAttribute('href')).toBe('/gruposEditar/1');
    expect(dato(1, 'Alumnos')).toBe('18');
    expect(dato(1, 'Edad')).toBe('6–12 años');
    expect(dato(1, 'Horario')).toBe('L y X · 17:00');
    expect(dato(2, 'Alumnos')).toBe('1.204');
    expect(dato(2, 'Edad')).toBe('Desde 16 años');
    expect(dato(2, 'Horario')).toBe('3 turnos');
    expect(dato(3, 'Edad')).toBe('Sin rango');
    expect(dato(3, 'Horario')).toBe('Sin turnos');
    expect(dato(4, 'Edad')).toBe('Hasta 7 años');
    expect(dato(4, 'Horario')).toBe('V · 17:00');
  });

  it('tarjeta: «Turnos (n)» despliega la lista con aria-expanded y aria-controls', () => {
    crear();
    responder();
    const b = boton(tarjeta(1), 'Turnos (2)');
    expect(b.getAttribute('aria-expanded')).toBe('false');
    const id = b.getAttribute('aria-controls')!;
    expect(id).toBeTruthy();
    expect(tarjeta(1).querySelector('.grupos__turnos-lista')).toBeNull();
    b.click();
    fixture.detectChanges();
    expect(b.getAttribute('aria-expanded')).toBe('true');
    const lista = document.getElementById(id)!;
    expect(lista).not.toBeNull();
    expect(Array.from(lista.querySelectorAll('.grupos__turno-texto')).map(texto)).toEqual(['Lunes 17:00–18:00', 'Miércoles 17:00–18:00']);
    expect(lista.querySelector<HTMLAnchorElement>('a[aria-label="Editar turno Lunes 17:00–18:00"]')!.getAttribute('href')).toBe('/turnosEditar/101');
    b.click();
    fixture.detectChanges();
    expect(b.getAttribute('aria-expanded')).toBe('false');
    expect(document.getElementById(id)).toBeNull();
  });

  it('tarjeta: pie con «Alumnos», «Editar», «Rango de edad» y la papelera, a las rutas de hoy', () => {
    crear();
    responder();
    const pie = tarjeta(1).querySelector('.grupos__pie')!;
    expect(pie.querySelector<HTMLAnchorElement>('a[href="/gestionarAlumnos/1"]')).not.toBeNull();
    expect(texto(pie.querySelector('a[href="/gestionarAlumnos/1"]'))).toBe('Alumnos');
    expect(texto(pie.querySelector('a[href="/gruposEditar/1"]'))).toBe('Editar');
    expect(boton(pie, 'Rango de edad')).toBeTruthy();
    expect(boton(pie, 'Eliminar grupo Taekwondo Infantil')).toBeTruthy();
  });

  it('objetivos: ningún control por debajo de 24 px; los iconos de turno, 32 px como mínimo', () => {
    crear();
    responder();
    boton(tarjeta(2), 'Turnos (3)').click();
    fixture.detectChanges();
    const controles = Array.from(el().querySelectorAll<HTMLElement>('.grupos button, .grupos a, .grupos select'));
    expect(controles.length).toBeGreaterThan(20);
    for (const c of controles) {
      const r = c.getBoundingClientRect();
      expect(r.height).withContext(c.className).toBeGreaterThanOrEqual(24);
      expect(r.width).withContext(c.className).toBeGreaterThanOrEqual(24);
    }
    const iconos = Array.from(tarjeta(2).querySelectorAll<HTMLElement>('.grupos__turno-icono'));
    expect(iconos.length).toBe(6);
    for (const i of iconos) {
      const r = i.getBoundingClientRect();
      expect(r.width).toBeGreaterThanOrEqual(32);
      expect(r.height).toBeGreaterThanOrEqual(32);
    }
  });

  // ------------------------------------------------------------------ acciones

  it('quitar turno: diálogo destructivo con los textos de hoy y el foco en «Cancelar»; cancelar no envía nada', async () => {
    crear();
    responder();
    boton(tarjeta(1), 'Turnos (2)').click();
    fixture.detectChanges();
    boton(tarjeta(1), 'Quitar turno Lunes 17:00–18:00 del grupo').click();
    pintar();
    expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('¿Eliminar turno del grupo?');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('El turno seguirá existiendo pero sin grupo asignado');
    expect(document.activeElement).toBe(botonDialogo('Cancelar'));
    botonDialogo('Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'DELETE');
  });

  it('quitar turno: DELETE /grupos/{id}/turnos/{turnoId}, aviso y el turno pasa a los turnos sin grupo', async () => {
    crear();
    responder();
    boton(tarjeta(1), 'Turnos (2)').click();
    fixture.detectChanges();
    boton(tarjeta(1), 'Quitar turno Lunes 17:00–18:00 del grupo').click();
    pintar();
    botonDialogo('Sí, eliminar').click();
    await esperar();
    const r = http.expectOne(`${API}/grupos/1/turnos/101`);
    expect(r.request.method).toBe('DELETE');
    expect(r.request.withCredentials).toBeTrue();
    r.flush({});
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Turno eliminado del grupo' }]);
    expect(Array.from(tarjeta(1).querySelectorAll('.grupos__turno-texto')).map(texto)).toEqual(['Miércoles 17:00–18:00']);
    expect(dato(1, 'Horario')).toBe('X · 17:00');
    expect(texto(el().querySelector('.grupos__sin-grupo'))).toContain('Lunes 17:00–18:00');
  });

  it('quitar turno: el error, en un aviso con el texto de hoy', async () => {
    crear();
    responder();
    boton(tarjeta(1), 'Turnos (2)').click();
    fixture.detectChanges();
    boton(tarjeta(1), 'Quitar turno Lunes 17:00–18:00 del grupo').click();
    pintar();
    botonDialogo('Sí, eliminar').click();
    await esperar();
    http.expectOne(`${API}/grupos/1/turnos/101`).flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo eliminar el turno' }]);
    expect(tarjeta(1).querySelectorAll('.grupos__turno-texto').length).toBe(2);
  });

  it('añadir turno: desplegable con los turnos sin grupo; POST con cuerpo nulo, botón ocupado y aviso', () => {
    crear();
    responder();
    boton(tarjeta(1), 'Turnos (2)').click();
    fixture.detectChanges();
    const select = tarjeta(1).querySelector<HTMLSelectElement>('select')!;
    expect(Array.from(select.options).map((o) => o.textContent!.trim())).toEqual([
      'Elige un turno sin grupo',
      'Viernes 18:00–19:00',
      'Sábado 09:00–10:00',
    ]);
    const anadir = boton(tarjeta(1), 'Añadir');
    expect(anadir.disabled).toBeTrue();
    select.value = '127';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(anadir.disabled).toBeFalse();
    anadir.click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/grupos/1/turnos/127`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toBeNull();
    expect(anadir.getAttribute('aria-busy')).toBe('true');
    anadir.click();
    http.expectNone(`${API}/grupos/1/turnos/127`);
    r.flush({});
    pintar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Turno asignado al grupo' }]);
    expect(Array.from(tarjeta(1).querySelectorAll('.grupos__turno-texto')).map(texto)).toEqual([
      'Lunes 17:00–18:00',
      'Miércoles 17:00–18:00',
      'Viernes 18:00–19:00',
    ]);
    expect(Array.from(el().querySelectorAll('.grupos__sin-grupo .grupos__turno-texto')).map(texto)).toEqual(['Sábado 09:00–10:00']);
    expect(dato(1, 'Horario')).toBe('3 turnos');
  });

  it('añadir turno: el error, en un aviso con el texto de hoy, y el turno sigue sin grupo', () => {
    crear();
    responder();
    boton(tarjeta(1), 'Turnos (2)').click();
    fixture.detectChanges();
    const select = tarjeta(1).querySelector<HTMLSelectElement>('select')!;
    select.value = '128';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    boton(tarjeta(1), 'Añadir').click();
    http.expectOne(`${API}/grupos/1/turnos/128`).flush({}, { status: 500, statusText: 'Error' });
    pintar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo asignar el turno al grupo' }]);
    expect(el().querySelectorAll('.grupos__sin-grupo li').length).toBe(2);
  });

  it('rango de edad: diálogo con los dos campos rellenos; PUT /grupos/{id} con el cuerpo de hoy y aviso', async () => {
    crear();
    responder();
    boton(tarjeta(1).querySelector('.grupos__pie')!, 'Rango de edad').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Rango de edad del grupo');
    const min = dialogo()!.querySelector<HTMLInputElement>('#rangoEdadMin')!;
    const max = dialogo()!.querySelector<HTMLInputElement>('#rangoEdadMax')!;
    expect(min.type).toBe('number');
    expect(min.value).toBe('6');
    expect(max.value).toBe('12');
    min.value = '8';
    min.dispatchEvent(new Event('input'));
    botonDialogo('Guardar').click();
    await esperar();
    const r = http.expectOne(`${API}/grupos/1`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ id: 1, nombre: 'Taekwondo Infantil', deporte: 'TAEKWONDO', rangoEdadMin: 8, rangoEdadMax: 12 });
    r.flush({});
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Rango de edad actualizado' }]);
    expect(dato(1, 'Edad')).toBe('8–12 años');
  });

  it('rango de edad: vacíos envían null; la validación de hoy en línea y nada enviado; cancelar no envía', async () => {
    crear();
    responder();
    const abrir = () => {
      boton(tarjeta(3).querySelector('.grupos__pie')!, 'Rango de edad').click();
      pintar();
    };
    const poner = (id: string, v: string) => {
      const i = dialogo()!.querySelector<HTMLInputElement>(id)!;
      i.value = v;
      i.dispatchEvent(new Event('input'));
      pintar();
    };
    abrir();
    poner('#rangoEdadMin', '12');
    poner('#rangoEdadMax', '6');
    botonDialogo('Guardar').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__error'))).toBe('La edad mínima no puede ser mayor que la máxima.');
    poner('#rangoEdadMin', '-1');
    botonDialogo('Guardar').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__error'))).toBe('La edad mínima debe ser un número válido.');
    poner('#rangoEdadMin', '');
    poner('#rangoEdadMax', '-3');
    botonDialogo('Guardar').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__error'))).toBe('La edad máxima debe ser un número válido.');
    http.expectNone((r) => r.method === 'PUT');
    botonDialogo('Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'PUT');
    abrir();
    botonDialogo('Guardar').click();
    await esperar();
    const r = http.expectOne(`${API}/grupos/3`);
    expect(r.request.body).toEqual({ id: 3, nombre: 'Taekwondo Adultos', deporte: 'TAEKWONDO', rangoEdadMin: null, rangoEdadMax: null });
    r.flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo actualizar el rango de edad' }]);
  });

  it('eliminar grupo: diálogo destructivo con los textos de hoy, foco en «Cancelar»; cancelar no envía', async () => {
    crear();
    responder();
    boton(tarjeta(1), 'Eliminar grupo Taekwondo Infantil').click();
    pintar();
    expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('¿Estás seguro?');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('No podrás revertir esto');
    expect(document.activeElement).toBe(botonDialogo('Cancelar'));
    botonDialogo('Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'DELETE');
  });

  it('eliminar grupo: DELETE /grupos/{id}, aviso y vuelve a leer grupos y turnos como hoy', async () => {
    crear();
    responder();
    boton(tarjeta(1), 'Eliminar grupo Taekwondo Infantil').click();
    pintar();
    botonDialogo('Sí, eliminarlo').click();
    await esperar();
    const r = http.expectOne(`${API}/grupos/1`);
    expect(r.request.method).toBe('DELETE');
    r.flush({});
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Grupo eliminado correctamente' }]);
    const sin1 = datosGrupos().filter((g) => g.id !== 1);
    http.expectOne(`${API}/grupos/resumen`).flush(sin1);
    http.expectOne(`${API}/turnos/resumen`).flush(datosTurnos().filter((t) => t.grupoId !== 1));
    fixture.detectChanges();
    expect(el().querySelector('.grupos__tarjeta[data-grupo="1"]')).toBeNull();
    expect(el().querySelectorAll('.grupos__tarjeta').length).toBe(3);
  });

  it('eliminar grupo: el error, en un aviso con el texto de hoy', async () => {
    crear();
    responder();
    boton(tarjeta(1), 'Eliminar grupo Taekwondo Infantil').click();
    pintar();
    botonDialogo('Sí, eliminarlo').click();
    await esperar();
    http.expectOne(`${API}/grupos/1`).flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo eliminar el grupo' }]);
    http.expectNone(`${API}/grupos/resumen`);
  });

  it('turnos sin grupo: bloque al final con cada turno y «Editar» a la ruta de hoy', () => {
    crear();
    responder();
    const bloque = el().querySelector('.grupos__sin-grupo')!;
    expect(texto(bloque.querySelector('h2'))).toBe('Turnos sin grupo asignado (2)');
    const filas = Array.from(bloque.querySelectorAll('li'));
    expect(filas.map((f) => texto(f.querySelector('.grupos__turno-texto')))).toEqual(['Viernes 18:00–19:00', 'Sábado 09:00–10:00']);
    expect(filas[0].querySelector('a')!.getAttribute('href')).toBe('/turnosEditar/127');
    expect(texto(filas[0].querySelector('a'))).toBe('Editar');
  });
});
