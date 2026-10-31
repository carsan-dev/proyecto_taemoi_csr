import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { By } from '@angular/platform-browser';

import { MASCOTA_CARGADORES } from '../mascota/mascota.component';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { environment } from '../../../environments/environment';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { MascotaComponent } from '../mascota/mascota.component';
import { PlataformaService } from '../../servicios/plataforma/plataforma.service';
import { AcentoEscuelaActual } from '../acento-escuela.directive';
import { MOISKIMDO_PRUEBA } from '../tema-prueba';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { InicioComponent } from './inicio.component';
import { PendienteVencido } from './pendiente-vencido.service';

const API = environment.apiUrl;
const NBSP = ' ';

const GRUPOS = [
  {
    id: 1,
    nombre: 'Taekwondo Lunes y Miércoles',
    deporte: 'TAEKWONDO',
    numAlumnos: 6,
    turnos: [
      { id: 11, diaSemana: 'Lunes', horaInicio: '17:00', horaFin: '18:00' },
      { id: 12, diaSemana: 'Miércoles', horaInicio: '17:00', horaFin: '18:00' },
    ],
  },
  {
    id: 2,
    nombre: 'Pilates Miércoles',
    deporte: 'PILATES',
    numAlumnos: 1,
    turnos: [{ id: 21, diaSemana: 'Miércoles', horaInicio: '19:00', horaFin: '20:00' }],
  },
];

function pendiente(parcial: Partial<PendienteVencido> = {}): PendienteVencido {
  return {
    total: 1274,
    cargos: 48,
    alumnos: 37,
    diaLimiteMensualidad: 5,
    porTipo: [
      { tipo: 'MENSUALIDAD', importe: 1074, cargos: 41 },
      { tipo: 'EXAMEN', importe: 200, cargos: 7 },
    ],
    porMes: [
      { mes: '2026-09', importe: 264, cargos: 13 },
      { mes: '2026-10', importe: 1010, cargos: 35 },
    ],
    porSede: [
      { sedeId: 1, sede: 'Umbrete', importe: 1070, cargos: 41 },
      { sedeId: null, sede: null, importe: 204, cargos: 7 },
    ],
    masAntiguos: [
      {
        productoAlumnoId: 501,
        alumnoId: 9,
        alumnoNombre: 'Triana Vidal Zamora',
        concepto: 'MENSUALIDAD SEPTIEMBRE 2026 - TAEKWONDO',
        tipo: 'MENSUALIDAD',
        importe: 30,
        mes: '2026-09',
        vencidoDesde: '2026-09-06',
        sedeId: 1,
      },
    ],
    ...parcial,
  };
}

describe('InicioComponent (8b-2, Inicio I1)', () => {
  let fixture: ComponentFixture<InicioComponent>;
  let http: HttpTestingController;
  let permisos$: BehaviorSubject<Partial<PermisosSesion>>;

  beforeEach(async () => {
    sessionStorage.removeItem('pn-inicio-saludado');
    quitarTokens = ponerTokensDePrueba();
    permisos$ = new BehaviorSubject<Partial<PermisosSesion>>({ cumpleanos: false });
    jasmine.clock().install();
    // Miércoles 21/10/2026 a las 08:00: las dos clases del día están por llegar.
    jasmine.clock().mockDate(new Date(2026, 9, 21, 8, 0));
    await TestBed.configureTestingModule({
      imports: [InicioComponent],
      providers: [
        { provide: MASCOTA_CARGADORES, useValue: {
          cumpleanos: () => Promise.resolve('<svg viewBox="0 0 320 320"></svg>'),
          saludar: () => Promise.resolve('<svg viewBox="0 0 320 320"></svg>'),
          descansar: () => Promise.resolve('<svg viewBox="0 0 320 320"></svg>'),
          compacta: () => Promise.resolve('<svg viewBox="0 0 320 320"></svg>'),
        } },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthenticationService, useValue: { getRoles: () => of(['ROLE_ADMIN']), tieneRolAdmin: () => true, tieneAccesoAdmin: () => true } },
        { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => permisos$ } },
        { provide: PlataformaService, useValue: { esAdminPlataforma: () => of(false) } },
        {
          provide: CatalogoService,
          useValue: {
            disciplinasActivas: () => [
              { codigo: 'TAEKWONDO', nombre: 'Taekwondo' },
              { codigo: 'KICKBOXING', nombre: 'Kickboxing' },
              { codigo: 'PILATES', nombre: 'Pilates' },
            ],
            etiquetaDisciplina: (c: string) => (c === 'PILATES' ? 'Pilates' : c === 'TAEKWONDO' ? 'Taekwondo' : c),
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InicioComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    sessionStorage.removeItem('pn-inicio-saludado');
    fixture.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    jasmine.clock().uninstall();
    quitarTokens();
  });

  let quitarTokens: () => void = () => undefined;

  // ---- 8b-D1: el diálogo y los avisos se pintan en el body
  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  function dialogo(): HTMLDialogElement | null {
    return document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  }

  function botonDialogo(textoBoton: string): HTMLButtonElement {
    return Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => (b.getAttribute('aria-label') ?? b.textContent ?? '').trim() === textoBoton
    )!;
  }

  async function esperar(): Promise<void> {
    for (let i = 0; i < 10; i++) {
      await Promise.resolve();
    }
    pintar();
  }

  function elemento(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  interface Respuestas {
    totales?: number;
    activos?: number;
    grupos?: unknown[];
    /** Número del límite o 'error' para que falle la petición. */
    limite?: number | 'error';
    /** Si es true, no responde la petición de eventos (llegan por su cuenta, más tarde). */
    sinEventos?: boolean;
  }

  function responder(dto: PendienteVencido = pendiente(), opciones: Respuestas = {}): void {
    http.match((r) => r.url === `${API}/alumnos` && r.params.get('incluirInactivos') === 'true').forEach((r) =>
      r.flush({ totalElements: opciones.totales ?? 52 })
    );
    http.match((r) => r.url === `${API}/alumnos` && r.params.get('incluirInactivos') === 'false').forEach((r) =>
      r.flush({ totalElements: opciones.activos ?? 50 })
    );
    http.match(`${API}/grupos/resumen`).forEach((r) => r.flush(opciones.grupos ?? GRUPOS));
    http.match(`${API}/turnos/resumen`).forEach((r) => r.flush([{ id: 11 }, { id: 12 }, { id: 21 }]));
    http.match(`${API}/turnos/limite`).forEach((r) =>
      opciones.limite === 'error'
        ? r.flush({ mensaje: 'fallo' }, { status: 500, statusText: 'Error' })
        : r.flush(opciones.limite ?? 36)
    );
    http.match(`${API}/alumnos/aptos`).forEach((r) => r.flush([{ id: 1 }]));
    http.match(`${API}/alumnos/deportes/distribucion`).forEach((r) => r.flush({ TAEKWONDO: 6, PILATES: 1 }));
    if (!opciones.sinEventos) {
      http.match(`${API}/eventos/admin/todos`).forEach((r) =>
        r.flush([
          { id: 1, titulo: 'Examen de diciembre', visible: true },
          { id: 2, titulo: 'Torneo', visible: false },
        ])
      );
    }
    http.match(`${API}/tesoreria/pendiente-vencido`).forEach((r) => r.flush(dto));
    fixture.detectChanges();
  }

  /** Texto visible del elemento con un espacio entre nodos (las celdas y etiquetas no llevan espacios entre sí). */
  function texto(selector: string): string {
    const raiz = (fixture.nativeElement as HTMLElement).querySelector(selector);
    if (!raiz) {
      return '';
    }
    const partes: string[] = [];
    const recorrido = document.createTreeWalker(raiz, NodeFilter.SHOW_TEXT);
    while (recorrido.nextNode()) {
      const t = (recorrido.currentNode.textContent ?? '').replace(/\s+/g, ' ').trim();
      if (t) {
        partes.push(t);
      }
    }
    return partes.join(' ');
  }

  function poseCabecera(): string | undefined {
    const el = fixture.debugElement.query(By.css('.inicio__bienvenida pn-mascota'));
    return (el?.componentInstance as MascotaComponent | undefined)?.pose;
  }

  describe('saludo del título', () => {
    function titulo(): string {
      return fixture.nativeElement.querySelector('h1.inicio__titulo').textContent.trim();
    }

    function abrir(): void {
      fixture.destroy();
      fixture = TestBed.createComponent(InicioComponent);
      fixture.detectChanges();
    }

    it('primera visita con nombre: «Hola, Nombre»', () => {
      fixture.destroy();
      permisos$.next({ nombreSaludo: 'Nombre', cumpleanos: false });
      abrir();
      expect(titulo()).toBe('Hola, Nombre');
    });

    it('primera visita en cumpleaños: «¡Feliz cumpleaños, Nombre!»', () => {
      fixture.destroy();
      permisos$.next({ nombreSaludo: 'Nombre', cumpleanos: true });
      abrir();
      expect(titulo()).toBe('¡Feliz cumpleaños, Nombre!');
    });

    it('usa el nombre de saludo entero, con espacios', () => {
      fixture.destroy();
      permisos$.next({ nombreSaludo: 'Ana María', cumpleanos: false });
      abrir();
      expect(titulo()).toBe('Hola, Ana María');
    });

    it('sin nombreSaludo (aunque haya nombreVisible): «Inicio»', () => {
      fixture.destroy();
      permisos$.next({ nombreVisible: 'Nombre A.', cumpleanos: false });
      abrir();
      expect(titulo()).toBe('Inicio');
    });

    it('segunda visita de la sesión: «Inicio»', () => {
      fixture.destroy();
      permisos$.next({ nombreSaludo: 'Nombre', cumpleanos: false });
      abrir();
      expect(titulo()).toBe('Hola, Nombre');
      abrir();
      expect(titulo()).toBe('Inicio');
    });

    it('sin nombre: «Inicio» y no marca la sesión', () => {
      fixture.destroy();
      permisos$.next({ nombreSaludo: null, cumpleanos: true });
      abrir();
      expect(titulo()).toBe('Inicio');
      expect(sessionStorage.getItem('pn-inicio-saludado')).toBeNull();
    });

    it('el texto no cambia a mitad de visita', () => {
      fixture.destroy();
      permisos$.next({ nombreSaludo: 'Nombre', cumpleanos: false });
      abrir();
      permisos$.next({ nombreSaludo: 'Otro', cumpleanos: true });
      fixture.detectChanges();
      expect(titulo()).toBe('Hola, Nombre');
    });

    it('si sessionStorage lanza, no rompe y saluda', () => {
      spyOn(Storage.prototype, 'getItem').and.throwError('bloqueado');
      spyOn(Storage.prototype, 'setItem').and.throwError('bloqueado');
      fixture.destroy();
      permisos$.next({ nombreSaludo: 'Nombre', cumpleanos: false });
      expect(() => abrir()).not.toThrow();
      expect(titulo()).toBe('Hola, Nombre');
    });
  });

  it('8b-M2: el día del cumpleaños la mascota de la cabecera pone la pose cumpleanos; el resto, saludar', () => {
    responder();
    expect(poseCabecera()).withContext('sin cumpleaños').toBe('saludar');
    permisos$.next({ cumpleanos: true });
    fixture.detectChanges();
    expect(poseCabecera()).withContext('cumpleaños').toBe('cumpleanos');
    permisos$.next({ cumpleanos: false });
    fixture.detectChanges();
    expect(poseCabecera()).withContext('otra vez sin cumpleaños').toBe('saludar');
  });

  it('shows the greeting and only the approved empty-state mascot', () => {
    responder(pendiente(), { grupos: [] });
    const c = fixture.componentInstance;
    c.disciplinas = [];
    c.eventos = [];
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(poseCabecera()).toBe('saludar');
    const empty = root.querySelector('.inicio__hoy pn-mascota')!;
    expect(empty.getAttribute('pose')).toBe('descansar');
    expect(empty.getAttribute('tamano')).toBe('md');
    expect(empty.getAttribute('animacion')).toBe('aparecer');
    expect(empty.getAttribute('aria-hidden')).toBe('true');
    for (const message of ['Aún no hay turnos con grupo', 'No hay datos de inscripciones.', 'No hay eventos activos.']) {
      const text = Array.from(root.querySelectorAll('.inicio__vacio')).find(node => node.textContent?.includes(message));
      expect(text).withContext(message).toBeDefined();
      expect(text!.parentElement!.querySelector('pn-mascota')).withContext(message).toBeNull();
    }
    expect(root.querySelectorAll('pn-mascota').length).toBe(2);
  });

  it('pide los datos de hoy: grupos, turnos, límite, aptos, eventos y el pendiente vencido', () => {
    expect(http.match(`${API}/grupos/resumen`).length).toBe(1);
    expect(http.match(`${API}/turnos/resumen`).length).toBe(1);
    expect(http.match(`${API}/turnos/limite`).length).toBe(1);
    expect(http.match(`${API}/alumnos/aptos`).length).toBe(1);
    expect(http.match(`${API}/eventos/admin/todos`).length).toBe(1);
    expect(http.match(`${API}/tesoreria/pendiente-vencido`).length).toBe(1);
  });

  it('el límite y el pendiente (su fallo lo enseña su bloque de error) van con ERROR_HANDLED_LOCALLY; el resto, no (8b-D2a)', () => {
    const local = (url: string) => http.match(url).map((r) => r.request.context.get(ERROR_HANDLED_LOCALLY));
    expect(local(`${API}/turnos/limite`)).toEqual([true]);
    expect(local(`${API}/tesoreria/pendiente-vencido`)).toEqual([true]);
    expect(local(`${API}/grupos/resumen`)).toEqual([false]);
    expect(local(`${API}/turnos/resumen`)).toEqual([false]);
    expect(local(`${API}/alumnos/aptos`)).toEqual([false]);
    expect(
      http.match((r) => r.url === `${API}/alumnos`).map((r) => r.request.context.get(ERROR_HANDLED_LOCALLY))
    ).toEqual([false, false]);
  });

  it('la ocupación lleva una tabla equivalente para lectores de pantalla', () => {
    responder();
    const tabla = (fixture.nativeElement as HTMLElement).querySelector('table.inicio__ocupacion-tabla');
    expect(tabla).not.toBeNull();
    expect(tabla!.querySelector('caption')?.textContent).toContain('Ocupación de la semana');
    const columnas = Array.from(tabla!.querySelectorAll('thead th')).map((th) => th.textContent?.trim());
    expect(columnas).toEqual(['Franja', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']);
    const filas = Array.from(tabla!.querySelectorAll('tbody tr'));
    expect(filas.map((f) => f.querySelector('th')?.textContent?.trim())).toEqual(['17:00', '19:00']);
    const celdas17 = Array.from(filas[0].querySelectorAll('td')).map((td) => td.textContent?.trim());
    expect(celdas17[0]).toBe('6 inscritos de 36 plazas (17 %)');
    expect(celdas17[1]).toBe('Sin clase');
    expect(celdas17[2]).toBe('6 inscritos de 36 plazas (17 %)');
    expect(texto('.inicio__ocupacion-total')).toBe('13 plazas ocupadas de 108');
    expect(texto('.inicio__ocupacion-nota')).toContain('límite común de 36 por turno');
    expect(texto('.inicio__ocupacion-nota')).toContain('cuenta sus alumnos en cada día');
  });

  it('plurales: clases de hoy, cargos y alumnos', () => {
    responder(pendiente({ cargos: 1, alumnos: 1 }));
    expect(texto('.inicio__hoy-cuenta')).toBe('2 clases');
    expect(texto('.inicio__pendiente-resumen')).toContain('1 cargo de 1 alumno');
    fixture.componentInstance.actualizar();
    responder(pendiente());
    expect(texto('.inicio__pendiente-resumen')).toContain('48 cargos de 37 alumnos');
  });

  it('Hoy: la siguiente clase resaltada y las plazas en texto', () => {
    responder();
    const filas = (fixture.nativeElement as HTMLElement).querySelectorAll('.inicio__clase');
    expect(filas.length).toBe(2);
    expect(filas[0].classList).toContain('inicio__clase--siguiente');
    expect(filas[0].textContent).toContain('Siguiente');
    expect(filas[0].textContent).toContain('6 de 36 plazas ocupadas');
    expect(filas[1].classList).not.toContain('inicio__clase--siguiente');
  });

  it('importes en es-ES, título claro y desglose por tipo, mes y sede', () => {
    responder();
    expect(texto('.inicio__pendiente h2')).toBe('Pendiente de cobro · vencido');
    expect(texto('.inicio__pendiente-total')).toBe(`1.274,00${NBSP}€`);
    expect(texto('.inicio__pendiente-resumen')).toContain('las mensualidades vencen el día 5');
    const pendienteTexto = texto('.inicio__pendiente');
    expect(pendienteTexto).toContain(`Mensualidades 41 1.074,00${NBSP}€`);
    expect(pendienteTexto).toContain(`Derechos de examen 7 200,00${NBSP}€`);
    expect(pendienteTexto).toContain('octubre de 2026');
    expect(pendienteTexto).toContain(`1.010,00${NBSP}€`);
    expect(pendienteTexto).toContain('Sin sede');
    expect(pendienteTexto).toContain('Triana Vidal Zamora');
    expect(pendienteTexto).not.toMatch(/\d\.\d{2} ?€|EUR/);
  });

  it('«Cobrar» abre el diálogo nuevo y hace el mismo PUT con el mismo cuerpo que hoy; aviso y recarga del pendiente (8b-D1)', async () => {
    responder();
    elemento().querySelector<HTMLButtonElement>('.inicio__cobrar')!.click();
    pintar();
    const d = dialogo()!;
    expect(d.querySelector('h2')!.textContent!.trim()).toBe('Cobrar mensualidad');
    const textoDialogo = d.textContent!.replace(/\s+/g, ' ');
    expect(textoDialogo).toContain('Se registra el pago de Triana Vidal Zamora y la mensualidad deja de figurar como pendiente.');
    expect(textoDialogo).toContain('MENSUALIDAD SEPTIEMBRE 2026 - TAEKWONDO');
    expect(d.querySelector('.pn-dialogo__destacado-valor')!.textContent!.trim()).toBe('30,00\u00a0€');
    Array.from(d.querySelectorAll<HTMLButtonElement>('[role="radio"]')).find((b) => b.textContent!.trim() === 'Bizum')!.click();
    pintar();
    botonDialogo('Marcar como pagado').click();
    pintar();
    expect(botonDialogo('Cobrando…').getAttribute('aria-disabled')).toBe('true');
    const put = http.expectOne(`${API}/productos-alumno/501`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ pagado: true, metodoPago: 'BIZUM' });
    expect(put.request.withCredentials).toBeTrue();
    put.flush({ id: 501, pagado: true });
    await esperar();
    expect(dialogo()).toBeNull();
    expect(document.querySelector('.pn-aviso')!.textContent).toContain('Cobro registrado: 30,00\u00a0€ · Triana Vidal Zamora');
    expect(http.match(`${API}/tesoreria/pendiente-vencido`).length).toBe(1);
  });

  it('«Cobrar» sin indicar método no envía metodoPago, y cancelar (X) no envía nada', async () => {
    responder();
    const boton = elemento().querySelector<HTMLButtonElement>('.inicio__cobrar')!;
    boton.click();
    pintar();
    botonDialogo('Cerrar').click();
    await esperar();
    http.expectNone(`${API}/productos-alumno/501`);

    boton.click();
    pintar();
    botonDialogo('Marcar como pagado').click();
    expect(http.expectOne(`${API}/productos-alumno/501`).request.body).toEqual({ pagado: true });
  });

  it('cargando: esqueletos con la forma de cada bloque a partir de los 300 ms (8b-D1)', () => {
    expect(elemento().querySelector('pn-esqueleto .pn-esqueleto__forma')).toBeNull();
    jasmine.clock().tick(300);
    fixture.detectChanges();
    const etiquetas = Array.from(elemento().querySelectorAll('pn-esqueleto [role="status"]')).map((e) => e.textContent!.trim());
    expect(etiquetas).toEqual([
      'Cargando las clases de hoy',
      'Cargando lo pendiente',
      'Cargando la ocupación',
      'Cargando las cifras',
      'Cargando los eventos',
    ]);
    expect(elemento().querySelectorAll('.inicio__ocupacion pn-esqueleto .inicio__hueso--celda').length).toBeGreaterThan(0);
    // Sin el límite aún, la nota no dice «límite común de 0».
    expect(elemento().querySelector('.inicio__ocupacion-nota')).toBeNull();
    responder();
    expect(elemento().querySelector('pn-esqueleto')).toBeNull();
  });

  it('las cifras esperan a los eventos: sin ellos siguen en carga y no pintan «0»', () => {
    responder(pendiente(), { sinEventos: true });
    jasmine.clock().tick(300);
    fixture.detectChanges();
    const cifras = elemento().querySelector('.inicio__cifras')!;
    expect(cifras.querySelector('pn-esqueleto')?.textContent).toContain('Cargando las cifras');
    expect(cifras.querySelector('.inicio__totales')).toBeNull();
    expect(cifras.textContent).not.toContain('Eventos');
    http.match(`${API}/eventos/admin/todos`).forEach((r) =>
      r.flush([
        { id: 1, titulo: 'A', visible: true },
        { id: 2, titulo: 'B', visible: true },
        { id: 3, titulo: 'C', visible: true },
        { id: 4, titulo: 'D', visible: false },
      ])
    );
    fixture.detectChanges();
    expect(elemento().querySelector('.inicio__cifras pn-esqueleto')).toBeNull();
    const eventos = Array.from(elemento().querySelectorAll('.inicio__cifras .inicio__total')).find((a) =>
      a.textContent!.includes('Eventos')
    )!;
    expect(eventos.querySelector('.inicio__total-cifra')!.textContent!.trim()).toBe('4');
    expect(eventos.querySelector('.inicio__total-detalle')!.textContent!.trim()).toBe('3 visibles');
  });

  it('error de los datos: bloques de error con «Reintentar», que vuelve a pedirlos (8b-D1)', () => {
    http.match((r) => r.url === `${API}/alumnos`).forEach((r) => r.flush('x', { status: 500, statusText: 'Error' }));
    http.match(`${API}/grupos/resumen`).forEach((r) => r.flush('x', { status: 500, statusText: 'Error' }));
    http.match(`${API}/turnos/resumen`).forEach((r) => r.flush('x', { status: 500, statusText: 'Error' }));
    http.match(`${API}/turnos/limite`).forEach((r) => r.flush(36));
    http.match(`${API}/alumnos/aptos`).forEach((r) => r.flush([]));
    http.match(`${API}/alumnos/deportes/distribucion`).forEach((r) => r.flush({}));
    fixture.detectChanges();
    const titulos = Array.from(elemento().querySelectorAll('pn-bloque-error .pn-bloque-error__titulo')).map((t) => t.textContent!.trim());
    expect(titulos).toEqual(['No se pudieron cargar las clases', 'No se pudo cargar la ocupación', 'No se pudieron cargar las cifras']);
    expect(elemento().querySelector('.inicio__ocupacion pn-bloque-error')!.textContent).toContain(
      'El resto del inicio sigue disponible. Comprueba la conexión y vuelve a intentarlo.'
    );
    elemento().querySelector<HTMLButtonElement>('.inicio__ocupacion pn-bloque-error button')!.click();
    expect(http.match(`${API}/grupos/resumen`).length).toBe(1);
  });

  it('error del pendiente: bloque de error con «Reintentar», que solo vuelve a pedir el pendiente (8b-D1)', () => {
    http.match(`${API}/tesoreria/pendiente-vencido`).forEach((r) => r.flush('x', { status: 500, statusText: 'Error' }));
    fixture.detectChanges();
    const bloque = elemento().querySelector<HTMLElement>('.inicio__pendiente pn-bloque-error')!;
    expect(bloque.textContent).toContain('No se pudo cargar lo pendiente');
    bloque.querySelector<HTMLButtonElement>('button')!.click();
    expect(http.match(`${API}/tesoreria/pendiente-vencido`).length).toBe(1);
    expect(http.match(`${API}/grupos/resumen`).length).toBe(1);
  });

  it('nada vencido: lo dice y no hay «Cobrar»', () => {
    responder(pendiente({ total: 0, cargos: 0, alumnos: 0, porTipo: [], porMes: [], porSede: [], masAntiguos: [] }));
    expect(texto('.inicio__pendiente')).toContain('No hay nada vencido sin cobrar.');
    expect((fixture.nativeElement as HTMLElement).querySelector('.inicio__cobrar')).toBeNull();
  });

  it('accesos con cifra: aptos para examen y eventos sin publicar', () => {
    responder();
    expect(texto('[data-acceso="aptos"]')).toContain('Alumnos aptos para examen 1');
    expect(texto('[data-acceso="eventos"]')).toContain('Eventos sin publicar 1');
  });

  it('la escuela en cifras: totales y la tabla por disciplina', () => {
    responder();
    const cifras = texto('.inicio__cifras');
    expect(cifras).toContain('Alumnos 52 50 activos, 2 inactivos');
    expect(cifras).toContain('Grupos 2 3 turnos');
    expect(cifras).toContain('Eventos 2 1 visible');
    expect(cifras).toContain('Taekwondo 6 86 %');
    expect(cifras).toContain('Total de inscripciones 7');
    expect(cifras).toContain('2 alumnos inactivos');
  });

  it('plurales en singular: plaza ocupada, activo e inactivo (ronda 2)', () => {
    const uno = [
      {
        id: 3,
        nombre: 'Grupo de uno',
        deporte: 'TAEKWONDO',
        numAlumnos: 1,
        turnos: [{ id: 31, diaSemana: 'Miércoles', horaInicio: '17:00', horaFin: '18:00' }],
      },
    ];
    responder(pendiente(), { totales: 2, activos: 1, grupos: uno, limite: 1 });
    expect(texto('.inicio__ocupacion-total')).toBe('1 plaza ocupada de 1');
    expect(texto('.inicio__clase')).toContain('1 de 1 plaza ocupada');
    expect(texto('.inicio__clase')).not.toContain('plazas');
    expect(texto('.inicio__cifras')).toContain('Alumnos 2 1 activo, 1 inactivo');
    expect(texto('.inicio__ocupacion-nota')).toContain('límite común de 1 por turno');
  });

  it('plurales con miles: cifras agrupadas en «Hoy» y en el total de la ocupación', () => {
    const muchos = [
      {
        id: 4,
        nombre: 'Grupo enorme',
        deporte: 'TAEKWONDO',
        numAlumnos: 1200,
        turnos: [{ id: 41, diaSemana: 'Miércoles', horaInicio: '17:00', horaFin: '18:00' }],
      },
    ];
    responder(pendiente(), { totales: 1500, activos: 1300, grupos: muchos, limite: 2000 });
    expect(texto('.inicio__clase')).toContain('1.200 de 2.000 plazas ocupadas');
    expect(texto('.inicio__ocupacion-total')).toBe('1.200 plazas ocupadas de 2.000');
    expect(texto('.inicio__cifras')).toContain('1.300 activos, 200 inactivos');
  });

  for (const caso of [0, 'error'] as const) {
    it(`límite ${caso === 0 ? 'en 0' : 'que falla'}: la ocupación muestra un error y no «/0» (ronda 2)`, () => {
      responder(pendiente(), { limite: caso });
      const ocupacion = texto('.inicio__ocupacion');
      // 8b-D1: el error del límite pasa al bloque de error común, con «Reintentar».
      expect(ocupacion).toContain('No se pudo leer el límite de plazas por turno');
      expect((fixture.nativeElement as HTMLElement).querySelector('.inicio__ocupacion pn-bloque-error')).not.toBeNull();
      expect(ocupacion).not.toContain('/0');
      expect(ocupacion).not.toContain('límite común de 0');
      expect((fixture.nativeElement as HTMLElement).querySelector('table.inicio__ocupacion-tabla')).toBeNull();
      expect(texto('.inicio__hoy')).not.toContain('de 0');
      expect(texto('.inicio__clase')).toContain('6 inscritos');

      fixture.componentInstance.actualizar();
      responder();
      expect((fixture.nativeElement as HTMLElement).querySelector('table.inicio__ocupacion-tabla')).not.toBeNull();
      expect(texto('.inicio__ocupacion')).not.toContain('No se pudo leer el límite');
    });
  }

  it('«Ir a Tesorería»: la tesorería de hoy no filtra lo vencido de todos los meses (ronda 2)', () => {
    responder();
    const enlace = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>('.inicio__pendiente .inicio__enlace');
    expect(enlace?.textContent?.trim()).toBe('Ir a Tesorería');
    expect(enlace?.getAttribute('href')).toBe('/tesoreriaCobros');
  });

  it('8b-C: con la marca, el enlace, los iconos de «Accesos» y el «Cobrar» de fila llevan el acento', () => {
    TestBed.inject(AcentoEscuelaActual).poner(MOISKIMDO_PRUEBA);
    responder();
    const raiz = fixture.nativeElement as HTMLElement;
    expect(getComputedStyle(raiz.querySelector('.inicio__pendiente .inicio__enlace')!).color).withContext('enlace').toBe(
      'rgb(1, 112, 127)'
    );
    expect(getComputedStyle(raiz.querySelector('.inicio__accesos .inicio__acceso > .bi')!).color)
      .withContext('icono de «Accesos»')
      .toBe('rgb(1, 112, 127)');
    const cobrar = getComputedStyle(raiz.querySelector('.inicio__cobrar')!);
    expect(cobrar.borderTopColor).withContext('«Cobrar»: borde').toBe('rgb(69, 149, 163)');
    expect(cobrar.color).withContext('«Cobrar»: texto').toBe('rgb(1, 112, 127)');
  });

  describe('responsable de sede (decisión 370 y A12 del inventario)', () => {
    function abrirComoResponsable(): void {
      fixture.destroy();
      http.match(() => true);
      spyOn(TestBed.inject(PermisosSesionService), 'tiene').and.callFake((c: string) => c === 'PANEL_SEDE');
      fixture = TestBed.createComponent(InicioComponent);
      fixture.detectChanges();
    }

    it('pide el vencido y las lecturas de sus sedes, pero no los eventos (403)', () => {
      abrirComoResponsable();
      expect(http.match(`${API}/eventos/admin/todos`).length).toBe(0);
      expect(http.match(`${API}/tesoreria/pendiente-vencido`).length).toBe(1);
      expect(http.match(`${API}/grupos/resumen`).length).toBe(1);
    });

    it('sin nada de Eventos, Productos ni bajas, y las cifras no se quedan cargando', () => {
      abrirComoResponsable();
      responder();
      expect(fixture.componentInstance.cifrasCargando).toBeFalse();
      const raiz = elemento();
      expect(raiz.querySelector('[data-acceso="eventos"]')).toBeNull();
      expect(raiz.querySelector('.inicio__eventos')).toBeNull();
      expect(raiz.querySelector('.inicio__totales--tres')).not.toBeNull();
      const enlaces = Array.from(raiz.querySelectorAll('a')).map((a) => a.getAttribute('href') ?? '');
      for (const ruta of ['/eventosListar', '/productosListar', '/alumnosEliminar']) {
        expect(enlaces).withContext(ruta).not.toContain(ruta);
      }
    });
  });
});
