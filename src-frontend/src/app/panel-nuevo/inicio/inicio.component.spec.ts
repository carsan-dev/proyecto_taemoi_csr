import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../environments/environment';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PlataformaService } from '../../servicios/plataforma/plataforma.service';
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

  beforeEach(async () => {
    jasmine.clock().install();
    // Miércoles 21/10/2026 a las 08:00: las dos clases del día están por llegar.
    jasmine.clock().mockDate(new Date(2026, 9, 21, 8, 0));
    await TestBed.configureTestingModule({
      imports: [InicioComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthenticationService, useValue: { getRoles: () => of(['ROLE_ADMIN']) } },
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
    fixture.destroy();
    jasmine.clock().uninstall();
  });

  function responder(dto: PendienteVencido = pendiente()): void {
    http.match((r) => r.url === `${API}/alumnos` && r.params.get('incluirInactivos') === 'true').forEach((r) =>
      r.flush({ totalElements: 52 })
    );
    http.match((r) => r.url === `${API}/alumnos` && r.params.get('incluirInactivos') === 'false').forEach((r) =>
      r.flush({ totalElements: 50 })
    );
    http.match(`${API}/grupos/resumen`).forEach((r) => r.flush(GRUPOS));
    http.match(`${API}/turnos/resumen`).forEach((r) => r.flush([{ id: 11 }, { id: 12 }, { id: 21 }]));
    http.match(`${API}/turnos/limite`).forEach((r) => r.flush(36));
    http.match(`${API}/alumnos/aptos`).forEach((r) => r.flush([{ id: 1 }]));
    http.match(`${API}/alumnos/deportes/distribucion`).forEach((r) => r.flush({ TAEKWONDO: 6, PILATES: 1 }));
    http.match(`${API}/eventos/admin/todos`).forEach((r) =>
      r.flush([
        { id: 1, titulo: 'Examen de diciembre', visible: true },
        { id: 2, titulo: 'Torneo', visible: false },
      ])
    );
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

  it('pide los datos de hoy: grupos, turnos, límite, aptos, eventos y el pendiente vencido', () => {
    expect(http.match(`${API}/grupos/resumen`).length).toBe(1);
    expect(http.match(`${API}/turnos/resumen`).length).toBe(1);
    expect(http.match(`${API}/turnos/limite`).length).toBe(1);
    expect(http.match(`${API}/alumnos/aptos`).length).toBe(1);
    expect(http.match(`${API}/eventos/admin/todos`).length).toBe(1);
    expect(http.match(`${API}/tesoreria/pendiente-vencido`).length).toBe(1);
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

  it('«Cobrar» abre la confirmación de hoy y hace el mismo PUT con el mismo cuerpo', async () => {
    responder();
    const promesa = Promise.resolve({ isConfirmed: true, value: 'BIZUM' } as any);
    const fire = spyOn(Swal, 'fire').and.returnValue(promesa);
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.inicio__cobrar')!.click();
    const opciones = fire.calls.mostRecent().args[0] as any;
    expect(opciones.title).toBe('Confirmar cobro');
    expect(opciones.text).toBe('Marcar como pagado "MENSUALIDAD SEPTIEMBRE 2026 - TAEKWONDO"?');
    expect(opciones.input).toBe('select');
    expect(opciones.inputLabel).toBe('Método (opcional)');
    expect(opciones.confirmButtonText).toBe('Si, marcar pagado');
    await promesa;
    await Promise.resolve();
    const put = http.expectOne(`${API}/productos-alumno/501`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ pagado: true, metodoPago: 'BIZUM' });
    expect(put.request.withCredentials).toBeTrue();
    put.flush({ id: 501, pagado: true });
    expect(http.match(`${API}/tesoreria/pendiente-vencido`).length).toBe(1);
  });

  it('«Cobrar» sin indicar método no envía metodoPago, y cancelar no envía nada', async () => {
    responder();
    const cancelar = Promise.resolve({ isConfirmed: false } as any);
    const fire = spyOn(Swal, 'fire').and.returnValue(cancelar);
    const boton = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.inicio__cobrar')!;
    boton.click();
    await cancelar;
    await Promise.resolve();
    http.expectNone(`${API}/productos-alumno/501`);

    const sinMetodo = Promise.resolve({ isConfirmed: true, value: '' } as any);
    fire.and.returnValue(sinMetodo);
    boton.click();
    await sinMetodo;
    await Promise.resolve();
    expect(http.expectOne(`${API}/productos-alumno/501`).request.body).toEqual({ pagado: true });
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
});
