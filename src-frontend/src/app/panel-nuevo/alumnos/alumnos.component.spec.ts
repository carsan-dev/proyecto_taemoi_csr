import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { LoadingService } from '../../servicios/generales/loading.service';
import { AcentoEscuelaActual } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { AlumnosComponent, CLAVE_VISTA } from './alumnos.component';
import { PendienteAlumno } from './pendientes-alumno.service';

const API = environment.apiUrl;
const NBSP = ' ';

function alumno(id: number, parcial: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id,
    nombre: `Nombre${id}`,
    apellidos: `Apellido${id} Segundo`,
    numeroExpediente: String(100 + id),
    fechaNacimiento: '2002-01-15',
    activo: true,
    fechaBaja: null,
    fotoAlumno: null,
    deportes: [
      {
        id: id * 10,
        deporte: 'TAEKWONDO',
        grado: 'NARANJA',
        fechaGrado: '2025-08-22',
        activo: true,
        principal: true,
        fechaAlta: '2024-10-10',
        fechaAltaInicial: null,
        tipoTarifa: 'ADULTO',
        categoria: 'Senior',
        tieneLicencia: false,
        fechaLicencia: null,
      },
    ],
    ...parcial,
  };
}

function pagina(total: number, cuantos = 25, desde = 1, parcial: Record<number, Record<string, unknown>> = {}) {
  const content = Array.from({ length: cuantos }, (_, i) => alumno(desde + i, parcial[desde + i] ?? {}));
  return { content, totalElements: total, totalPages: Math.ceil(total / 25), number: 0, size: 25 };
}

const DOS_CARGOS: PendienteAlumno = {
  alumnoId: 1,
  importe: 50,
  cargos: 2,
  detalle: [
    { productoAlumnoId: 701, concepto: 'MENSUALIDAD ENERO 2026 - TAEKWONDO', importe: 30, vencidoDesde: '2026-01-06' },
    { productoAlumnoId: 702, concepto: 'DERECHO A EXAMEN', importe: 20, vencidoDesde: '2026-03-06' },
  ],
};

const UN_CARGO: PendienteAlumno = {
  alumnoId: 2,
  importe: 30,
  cargos: 1,
  detalle: [{ productoAlumnoId: 801, concepto: 'MENSUALIDAD FEBRERO 2026 - TAEKWONDO', importe: 30, vencidoDesde: '2026-02-06' }],
};

describe('AlumnosComponent (8b-3a, listado de alumnos C)', () => {
  let fixture: ComponentFixture<AlumnosComponent>;
  let http: HttpTestingController;

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function texto(selector: string): string {
    return (el().querySelector(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  // ---- 8b-D1: diálogo, avisos y panel lateral del panel nuevo (fuera del fixture, en el body o en la capa de arriba)
  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  function dialogo(): HTMLDialogElement | null {
    return document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  }

  function botonDialogo(textoBoton: string): HTMLButtonElement {
    const hallado = Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => (b.getAttribute('aria-label') ?? b.textContent ?? '').trim() === textoBoton
    );
    if (!hallado) {
      throw new Error(`Sin botón «${textoBoton}» en el diálogo`);
    }
    return hallado;
  }

  function pastilla(textoPastilla: string): void {
    Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('[role="radio"]'))
      .find((b) => b.textContent!.trim() === textoPastilla)!
      .click();
    pintar();
  }

  function textoDialogo(): string {
    // Sin tocar el espacio duro de los importes.
    return (dialogo()?.textContent ?? '').replace(/[ \t\r\n]+/g, ' ').trim();
  }

  /** Cada aviso con la región viva por la que se anuncia: `alert` el error, `status` el resto. */
  function avisos(): { rol: string | null; texto: string }[] {
    return Array.from(document.querySelectorAll<HTMLElement>('.pn-aviso')).map((a) => ({
      rol: a.classList.contains('pn-aviso--error') ? 'alert' : 'status',
      texto: a.querySelector('.pn-aviso__texto')!.textContent!.trim(),
    }));
  }

  async function esperar(): Promise<void> {
    for (let i = 0; i < 10; i++) {
      await Promise.resolve();
    }
    pintar();
  }

  function crear(): void {
    fixture = TestBed.createComponent(AlumnosComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  /** Las peticiones del listado (no la del total de activos). */
  function listados(): TestRequest[] {
    return http.match((r) => r.url === `${API}/alumnos` && r.params.get('size') === '25');
  }

  /** Responde a la carga: el total de activos, la página y los pendientes de sus ids. */
  function responder(total = 812, pendientes: PendienteAlumno[] = [DOS_CARGOS, UN_CARGO], cuerpo = pagina(total)): void {
    http.match((r) => r.url === `${API}/alumnos` && r.params.get('size') === '1').forEach((r) => r.flush({ totalElements: 790 }));
    const peticiones = listados();
    expect(peticiones.length).toBe(1);
    peticiones[0].flush(cuerpo);
    fixture.detectChanges();
    const p = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    if (cuerpo.content.length > 0) {
      expect(p.length).toBe(1);
      p[0].flush(pendientes);
    }
    fixture.detectChanges();
  }

  let quitarTokens: () => void;
  let esAdmin = false;

  beforeEach(async () => {
    quitarTokens = ponerTokensDePrueba();
    esAdmin = false;
    localStorage.removeItem(CLAVE_VISTA);
    await TestBed.configureTestingModule({
      imports: [AlumnosComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LoadingService, useValue: jasmine.createSpyObj('LoadingService', ['show', 'hide']) },
        { provide: AuthenticationService, useValue: { tieneRolAdmin: () => esAdmin } },
        {
          provide: CatalogoService,
          useValue: {
            disciplinasActivas: () => [
              { codigo: 'TAEKWONDO', nombre: 'Taekwondo', requiereLicencia: true },
              { codigo: 'KICKBOXING', nombre: 'Kickboxing', requiereLicencia: true },
              { codigo: 'PILATES', nombre: 'Pilates', requiereLicencia: false },
            ],
            etiquetaDisciplina: (c: string) => c.charAt(0) + c.slice(1).toLowerCase(),
            requiereLicencia: (c: string) => c !== 'PILATES',
            sinGrados: (c: string) => c === 'PILATES',
            tarifa: (c: string) => (c === 'ADULTO' ? { codigo: 'ADULTO', nombre: 'Adulto' } : undefined),
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
    localStorage.removeItem(CLAVE_VISTA);
    quitarTokens();
  });

  it('pide 25 por página al servidor y una sola vez los pendientes de los ids de la página', () => {
    crear();
    const [peticion] = listados();
    expect(peticion.request.params.get('page')).toBe('1');
    expect(peticion.request.params.get('size')).toBe('25');
    expect(peticion.request.params.get('incluirInactivos')).toBe('false');
    expect(peticion.request.params.has('nombre')).toBeFalse();
    expect(peticion.request.params.has('deporte')).toBeFalse();
    expect(peticion.request.params.has('aptoParaExamen')).toBeFalse();
    peticion.flush(pagina(812));
    fixture.detectChanges();
    const [pendientes] = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    const ids = Array.from({ length: 25 }, (_, i) => i + 1).join(',');
    expect(pendientes.request.params.get('alumnoIds')).toBe(ids);
    expect(pendientes.request.withCredentials).toBeTrue();
    pendientes.flush([]);
    fixture.detectChanges();
    expect(el().querySelectorAll('.alumnos__fila').length).toBe(25);
  });

  it('el contador es el total del servidor, no el de la página; paginación «1–25 de N»', () => {
    crear();
    responder(1044);
    expect(texto('.alumnos__contador')).toBe('1.044 alumnos');
    expect(texto('.alumnos__subtitulo')).toBe('790 alumnos activos, 25 por página');
    expect(texto('.alumnos__rango')).toBe('1–25 de 1.044');
  });

  it('plurales en singular: un alumno, un deporte y un año', () => {
    crear();
    responder(1, [], pagina(1, 1, 1, { 1: { fechaNacimiento: '2025-01-15' } }));
    expect(texto('.alumnos__contador')).toBe('1 alumno');
    expect(texto('.alumnos__rango')).toBe('1–1 de 1');
    expect(el().querySelector('.alumnos__num-deportes')!.getAttribute('aria-label')).toBe('1 disciplina');
    expect(texto('.alumnos__edad')).toBe('1 año');
  });

  it('plurales en plural: dos deportes y la edad en años', () => {
    crear();
    const dos = alumno(1, {
      deportes: [
        ...(alumno(1)['deportes'] as unknown[]),
        { id: 11, deporte: 'PILATES', grado: null, activo: true, principal: false, fechaAlta: '2025-01-01', tipoTarifa: null },
      ],
    });
    responder(2, [], { content: [dos, alumno(2)], totalElements: 2, totalPages: 1, number: 0, size: 25 } as never);
    expect(el().querySelector('.alumnos__num-deportes')!.getAttribute('aria-label')).toBe('2 disciplinas');
    expect(texto('.alumnos__edad')).toMatch(/^\d+ años$/);
    expect(texto('.alumnos__contador')).toBe('2 alumnos');
  });

  it('los filtros van a la API con sus parámetros y vuelven a la página 1', fakeAsync(() => {
    crear();
    responder();
    const c = fixture.componentInstance;

    c.irAPagina(2);
    let [p] = listados();
    expect(p.request.params.get('page')).toBe('2');
    p.flush(pagina(812, 25, 26));
    http.match(() => true).forEach((r) => r.flush([]));

    const buscador = el().querySelector<HTMLInputElement>('#alumnos-buscar')!;
    buscador.value = 'garcía';
    buscador.dispatchEvent(new Event('input'));
    tick(299);
    expect(listados().length).toBe(0);
    tick(1);
    [p] = listados();
    expect(p.request.params.get('nombre')).toBe('garcía');
    expect(p.request.params.get('page')).toBe('1');
    p.flush(pagina(3, 3));
    http.match(() => true).forEach((r) => r.flush([]));
    fixture.detectChanges();

    const disciplina = el().querySelector<HTMLSelectElement>('#alumnos-disciplina')!;
    disciplina.value = 'PILATES';
    disciplina.dispatchEvent(new Event('change'));
    [p] = listados();
    expect(p.request.params.get('deporte')).toBe('PILATES');
    expect(p.request.params.get('nombre')).toBe('garcía');
    p.flush(pagina(3, 3));
    http.match(() => true).forEach((r) => r.flush([]));
    fixture.detectChanges();

    el().querySelector<HTMLButtonElement>('.alumnos__filtro--inactivos')!.click();
    [p] = listados();
    expect(p.request.params.get('incluirInactivos')).toBe('true');
    p.flush(pagina(3, 3));
    http.match(() => true).forEach((r) => r.flush([]));
    fixture.detectChanges();
    expect(el().querySelector('.alumnos__filtro--inactivos')!.getAttribute('aria-pressed')).toBe('true');

    el().querySelector<HTMLButtonElement>('.alumnos__filtro--aptos')!.click();
    [p] = listados();
    expect(p.request.params.get('aptoParaExamen')).toBe('true');
    expect(p.request.params.get('deporte')).toBe('PILATES');
    p.flush(pagina(3, 3));
    http.match(() => true).forEach((r) => r.flush([]));
    flush();
  }));

  it('«Filas | Tarjetas» se recuerda en localStorage', () => {
    crear();
    responder();
    expect(el().querySelector('.alumnos__lista--tarjetas')).toBeNull();
    const tarjetas = el().querySelector<HTMLButtonElement>('.alumnos__vista-opcion[data-vista="tarjetas"]')!;
    tarjetas.click();
    fixture.detectChanges();
    expect(localStorage.getItem(CLAVE_VISTA)).toBe('tarjetas');
    expect(tarjetas.getAttribute('aria-pressed')).toBe('true');
    expect(el().querySelector('.alumnos__lista--tarjetas')).not.toBeNull();
    fixture.destroy();

    crear();
    responder();
    expect(el().querySelector('.alumnos__lista--tarjetas')).not.toBeNull();
  });

  it('sin localStorage (bloqueado) no falla y usa filas', () => {
    spyOn(Storage.prototype, 'getItem').and.throwError('bloqueado');
    spyOn(Storage.prototype, 'setItem').and.throwError('bloqueado');
    crear();
    responder();
    expect(el().querySelector('.alumnos__lista--tarjetas')).toBeNull();
    el().querySelector<HTMLButtonElement>('.alumnos__vista-opcion[data-vista="tarjetas"]')!.click();
    fixture.detectChanges();
    expect(el().querySelector('.alumnos__lista--tarjetas')).not.toBeNull();
  });

  it('pendiente: importe es-ES con «Cobrar» o «Al día»', () => {
    crear();
    responder();
    const filas = el().querySelectorAll('.alumnos__fila');
    expect(filas[0].querySelector('.alumnos__importe')!.textContent!.trim()).toBe(`50,00${NBSP}€`);
    expect(filas[0].querySelector('.alumnos__cobrar')).not.toBeNull();
    expect(filas[2].querySelector('.alumnos__al-dia')!.textContent!.trim()).toBe('Al día');
    expect(filas[2].querySelector('.alumnos__cobrar')).toBeNull();
  });

  it('«Cobrar» con un cargo: el diálogo nuevo y el PUT de la tesorería de hoy; aviso y refresco de ese alumno (8b-D1)', async () => {
    crear();
    responder();
    el().querySelectorAll<HTMLButtonElement>('.alumnos__fila')[1].querySelector<HTMLButtonElement>('.alumnos__cobrar')!.click();
    pintar();
    expect(dialogo()!.querySelector('h2')!.textContent!.trim()).toBe('Cobrar cargo');
    expect(textoDialogo()).toContain('Se registra el pago de Nombre2 Apellido2 Segundo y el cargo deja de figurar como pendiente.');
    expect(textoDialogo()).toContain('MENSUALIDAD FEBRERO 2026 - TAEKWONDO');
    expect(textoDialogo()).toContain(`30,00${NBSP}€`);
    expect(dialogo()!.querySelector('[role="radiogroup"]')).not.toBeNull();
    pastilla('Efectivo');
    botonDialogo('Marcar como pagado').click();
    pintar();
    const put = http.expectOne(`${API}/productos-alumno/801`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ pagado: true, metodoPago: 'EFECTIVO' });
    expect(put.request.withCredentials).toBeTrue();
    put.flush({ id: 801, pagado: true });
    await esperar();
    expect(dialogo()).toBeNull();
    expect(avisos()).toEqual([{ rol: 'status', texto: `Cobro registrado: 30,00${NBSP}€ · Nombre2 Apellido2 Segundo` }]);
    const [refresco] = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    expect(refresco.request.params.get('alumnoIds')).toBe('2');
    refresco.flush([]);
    fixture.detectChanges();
    expect(el().querySelectorAll('.alumnos__fila')[1].querySelector('.alumnos__al-dia')).not.toBeNull();
  });

  it('«Cobrar» cancelado (Esc) no envía nada; sin método no envía metodoPago', async () => {
    crear();
    responder();
    const boton = el().querySelectorAll('.alumnos__fila')[1].querySelector<HTMLButtonElement>('.alumnos__cobrar')!;
    boton.click();
    pintar();
    dialogo()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    await esperar();
    expect(dialogo()).toBeNull();
    http.expectNone(`${API}/productos-alumno/801`);
    boton.click();
    pintar();
    botonDialogo('Marcar como pagado').click();
    expect(http.expectOne(`${API}/productos-alumno/801`).request.body).toEqual({ pagado: true });
  });

  it('«Cobrar» con varios cargos abre el panel con cada cargo; cada «Cobrar» abre el diálogo nuevo y hace lo mismo que hoy', async () => {
    crear();
    responder();
    const boton = el().querySelector<HTMLButtonElement>('.alumnos__fila .alumnos__cobrar')!;
    boton.click();
    fixture.detectChanges();
    expect(dialogo()).toBeNull();
    const panel = el().querySelector<HTMLElement>('.alumnos__cargos')!;
    expect(panel.getAttribute('role')).toBe('dialog');
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    const cargos = panel.querySelectorAll('.alumnos__cargo');
    expect(cargos.length).toBe(2);
    expect(cargos[0].textContent).toContain('MENSUALIDAD ENERO 2026 - TAEKWONDO');
    expect(cargos[0].textContent).toContain(`30,00${NBSP}€`);
    expect(cargos[0].textContent).toContain('Vencido desde el 06/01/2026');

    cargos[1].querySelector<HTMLButtonElement>('button')!.click();
    pintar();
    expect(textoDialogo()).toContain('DERECHO A EXAMEN');
    expect(textoDialogo()).toContain(`20,00${NBSP}€`);
    pastilla('Bizum');
    botonDialogo('Marcar como pagado').click();
    const put = http.expectOne(`${API}/productos-alumno/702`);
    expect(put.request.body).toEqual({ pagado: true, metodoPago: 'BIZUM' });
    put.flush({});
    await esperar();
    const [refresco] = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    expect(refresco.request.params.get('alumnoIds')).toBe('1');
    refresco.flush([{ ...DOS_CARGOS, importe: 30, cargos: 1, detalle: [DOS_CARGOS.detalle[0]] }]);
    fixture.detectChanges();
    expect(el().querySelectorAll('.alumnos__cargos .alumnos__cargo').length).toBe(1);
    expect(el().querySelector('.alumnos__fila .alumnos__importe')!.textContent!.trim()).toBe(`30,00${NBSP}€`);
  });

  it('«Deshacer» del aviso solo para el propietario, con la misma llamada que «Revertir» de la tesorería de hoy', async () => {
    esAdmin = true;
    crear();
    responder();
    el().querySelectorAll('.alumnos__fila')[1].querySelector<HTMLButtonElement>('.alumnos__cobrar')!.click();
    pintar();
    botonDialogo('Marcar como pagado').click();
    http.expectOne(`${API}/productos-alumno/801`).flush({});
    await esperar();
    http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`).forEach((r) => r.flush([]));
    const deshacer = document.querySelector<HTMLButtonElement>('.pn-aviso__accion')!;
    expect(deshacer.textContent!.trim()).toBe('Deshacer');
    deshacer.click();
    const revertir = http.expectOne(`${API}/productos-alumno/801`);
    expect(revertir.request.body).toEqual({ pagado: false, motivoCambio: null });
    revertir.flush({});
    await esperar();
    const [refresco] = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    expect(refresco.request.params.get('alumnoIds')).toBe('2');
    refresco.flush([UN_CARGO]);
    expect(avisos().map((a) => a.texto)).toContain('Cobro revertido a pendiente');
  });

  it('teclado del panel: foco en el primer cargo; Esc lo cierra y devuelve el foco a «Cobrar»', () => {
    crear();
    responder();
    document.body.appendChild(el());
    const boton = el().querySelector<HTMLButtonElement>('.alumnos__fila .alumnos__cobrar')!;
    boton.focus();
    boton.click();
    fixture.detectChanges();
    const panel = el().querySelector<HTMLElement>('.alumnos__cargos')!;
    expect(document.activeElement).toBe(panel.querySelector('.alumnos__cargo button'));
    panel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(el().querySelector('.alumnos__cargos')).toBeNull();
    expect(document.activeElement).toBe(boton);
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    el().remove();
  });

  it('fila desplegable: botón nativo con aria-expanded y aria-controls; detalle por deporte y acciones', () => {
    crear();
    responder();
    const fila = el().querySelector<HTMLElement>('.alumnos__fila')!;
    const desplegar = fila.querySelector<HTMLButtonElement>('.alumnos__desplegar')!;
    // Botón nativo: Enter y Espacio lo activan sin código propio.
    expect(desplegar.tagName).toBe('BUTTON');
    expect(desplegar.type).toBe('button');
    expect(desplegar.getAttribute('aria-expanded')).toBe('false');
    expect(fila.querySelector('.alumnos__detalle')).toBeNull();
    desplegar.click();
    fixture.detectChanges();
    expect(desplegar.getAttribute('aria-expanded')).toBe('true');
    const detalle = fila.querySelector<HTMLElement>('.alumnos__detalle')!;
    expect(detalle.id).toBe(desplegar.getAttribute('aria-controls')!);
    const linea = detalle.querySelector('.alumnos__deporte')!.textContent!.replace(/\s+/g, ' ');
    expect(linea).toContain('Principal');
    expect(linea).toContain('Tarifa Adulto');
    expect(linea).toContain('Alta 10/10/2024');
    expect(linea).toContain('Categoría Senior');
    expect(linea).toContain('con el grado');
    const acciones = Array.from(detalle.querySelectorAll('.alumnos__accion')).map((a) => a.textContent!.trim());
    expect(acciones).toEqual(['Editar ficha', 'Vista de familia', 'Dar de baja']);
    expect(detalle.querySelector('a.alumnos__accion')!.getAttribute('href')).toBe('/alumnosEditar/1');
    desplegar.click();
    fixture.detectChanges();
    expect(desplegar.getAttribute('aria-expanded')).toBe('false');
    expect(fila.querySelector('.alumnos__detalle')).toBeNull();
  });

  it('«Marcar principal» llama a la misma ruta que hoy', () => {
    crear();
    const dos = alumno(1, {
      deportes: [
        ...(alumno(1)['deportes'] as unknown[]),
        { id: 11, deporte: 'KICKBOXING', grado: 'VERDE', activo: true, principal: false, fechaAlta: '2025-01-01', tipoTarifa: null },
      ],
    });
    responder(1, [], { content: [dos], totalElements: 1, totalPages: 1, number: 0, size: 25 } as never);
    el().querySelector<HTMLButtonElement>('.alumnos__desplegar')!.click();
    fixture.detectChanges();
    el().querySelector<HTMLButtonElement>('.alumnos__marcar-principal')!.click();
    const put = http.expectOne(`${API}/alumnos/1/deportes/KICKBOXING/principal`);
    expect(put.request.method).toBe('PUT');
    put.flush({});
    pintar();
    // 8b-D1: el aviso del panel nuevo, con el texto de hoy.
    expect(avisos()).toEqual([{ rol: 'status', texto: 'Deporte principal actualizado a Kickboxing' }]);
  });

  it('«Marcar principal» que falla: aviso de error con el texto del servidor (8b-D1)', () => {
    crear();
    const dos = alumno(1, {
      deportes: [
        ...(alumno(1)['deportes'] as unknown[]),
        { id: 11, deporte: 'KICKBOXING', grado: 'VERDE', activo: true, principal: false, fechaAlta: '2025-01-01', tipoTarifa: null },
      ],
    });
    responder(1, [], { content: [dos], totalElements: 1, totalPages: 1, number: 0, size: 25 } as never);
    el().querySelector<HTMLButtonElement>('.alumnos__desplegar')!.click();
    fixture.detectChanges();
    el().querySelector<HTMLButtonElement>('.alumnos__marcar-principal')!.click();
    http.expectOne(`${API}/alumnos/1/deportes/KICKBOXING/principal`).flush('No permitido', { status: 400, statusText: 'Bad' });
    pintar();
    expect(avisos()).toEqual([{ rol: 'alert', texto: 'No permitido' }]);
  });

  it('«Dar de baja»: diálogo destructivo con el foco en «Cancelar»; Esc no envía nada; confirmado, el PUT de hoy (8b-D1)', async () => {
    crear();
    responder();
    document.body.appendChild(el());
    el().querySelector<HTMLButtonElement>('.alumnos__desplegar')!.click();
    fixture.detectChanges();
    const baja = Array.from(el().querySelectorAll<HTMLButtonElement>('.alumnos__accion')).find((b) => b.textContent!.trim() === 'Dar de baja')!;
    baja.focus();
    baja.click();
    pintar();
    expect(dialogo()!.querySelector('h2')!.textContent!.trim()).toBe('¿Dar de baja a Nombre1 Apellido1 Segundo?');
    expect(textoDialogo()).toContain('Se desactivan todas sus disciplinas desde hoy');
    expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
    expect(document.activeElement).toBe(botonDialogo('Cancelar'));
    dialogo()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    await esperar();
    http.expectNone(`${API}/alumnos/1/baja`);
    expect(document.activeElement).toBe(baja);

    baja.click();
    pintar();
    botonDialogo('Dar de baja').click();
    await esperar();
    const put = http.expectOne(`${API}/alumnos/1/baja`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({});
    put.flush({});
    await esperar();
    expect(avisos()).toEqual([{ rol: 'status', texto: 'Alumno dado de baja correctamente' }]);
    expect(listados().length).toBe(1);
    el().remove();
  });

  it('«Dar de alta»: con un deporte inactivo lo activa como hoy; con varios, el diálogo con «Todos los deportes» (8b-D1)', async () => {
    crear();
    const deBaja = (id: number) =>
      alumno(id, { activo: false, deportes: [{ ...(alumno(id)['deportes'] as any[])[0], activo: false }] });
    const filas = () => ({ content: [deBaja(1), deBaja(2)], totalElements: 2, totalPages: 1, number: 0, size: 25 });
    responder(2, [], filas() as never);
    const alta = (i: number) => {
      el().querySelectorAll<HTMLButtonElement>('.alumnos__desplegar')[i].click();
      fixture.detectChanges();
      el().querySelectorAll<HTMLElement>('.alumnos__fila')[i]
        .querySelector<HTMLButtonElement>('.alumnos__accion--bien')!
        .click();
    };
    alta(0);
    http.expectOne(`${API}/alumnos/1/deportes`).flush([{ deporte: 'TAEKWONDO', activo: false }, { deporte: 'PILATES', activo: true }]);
    pintar();
    expect(dialogo()).toBeNull();
    http.expectOne(`${API}/alumnos/1/deportes/TAEKWONDO/activar`).flush({});
    pintar();
    expect(avisos().map((a) => a.texto)).toContain('Alumno dado de alta en Taekwondo');
    listados().forEach((r) => r.flush(filas()));
    http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`).forEach((r) => r.flush([]));
    fixture.detectChanges();

    alta(1);
    http.expectOne(`${API}/alumnos/2/deportes`).flush([{ deporte: 'TAEKWONDO', activo: false }, { deporte: 'KICKBOXING', activo: false }]);
    pintar();
    expect(dialogo()!.querySelector('h2')!.textContent!.trim()).toBe('Selecciona el deporte a activar');
    const radios = Array.from(dialogo()!.querySelectorAll('[role="radio"]')).map((r) => r.textContent!.trim());
    expect(radios).toEqual(['Todos los deportes', 'Taekwondo', 'Kickboxing']);
    expect(botonDialogo('Dar de alta').disabled).toBeTrue();
    pastilla('Todos los deportes');
    botonDialogo('Dar de alta').click();
    await esperar();
    http.expectOne(`${API}/alumnos/2/deportes/TAEKWONDO/activar`).flush({});
    http.expectOne(`${API}/alumnos/2/deportes/KICKBOXING/activar`).flush({});
    pintar();
    expect(avisos().map((a) => a.texto)).toContain('Alumno dado de alta en todos sus deportes: Taekwondo, Kickboxing');
  });

  it('«Dar de alta» sin deportes inactivos: aviso de información como el de hoy (8b-D1)', () => {
    crear();
    const deBaja = alumno(1, { activo: false, deportes: [{ ...(alumno(1)['deportes'] as any[])[0], activo: false }] });
    responder(1, [], { content: [deBaja], totalElements: 1, totalPages: 1, number: 0, size: 25 } as never);
    el().querySelector<HTMLButtonElement>('.alumnos__desplegar')!.click();
    fixture.detectChanges();
    el().querySelector<HTMLButtonElement>('.alumnos__accion--bien')!.click();
    http.expectOne(`${API}/alumnos/1/deportes`).flush([{ deporte: 'TAEKWONDO', activo: true }]);
    pintar();
    expect(avisos()).toEqual([{ rol: 'status', texto: 'El alumno ya tiene todos sus deportes activos.' }]);
  });

  it('cargando: esqueleto de filas a partir de los 300 ms (8b-D1)', fakeAsync(() => {
    crear();
    expect(el().querySelector('.alumnos__esqueleto')).toBeNull();
    tick(300);
    fixture.detectChanges();
    const esqueleto = el().querySelector('pn-esqueleto')!;
    expect(esqueleto.querySelector('[role="status"]')!.textContent!.trim()).toBe('Cargando alumnos');
    expect(esqueleto.querySelectorAll('.alumnos__esqueleto').length).toBe(6);
    responder();
    expect(el().querySelector('pn-esqueleto')).toBeNull();
    flush();
  }));

  it('vacío por filtros: «Ningún alumno coincide…» con «Quitar filtros» (8b-D1)', fakeAsync(() => {
    crear();
    responder();
    el().querySelector<HTMLButtonElement>('.alumnos__filtro--inactivos')!.click();
    listados()[0].flush(pagina(0, 0));
    fixture.detectChanges();
    const vacio = el().querySelector<HTMLElement>('pn-bloque-vacio [role="status"]')!;
    expect(vacio.textContent).toContain('Ningún alumno coincide con la búsqueda');
    expect(vacio.textContent).toContain('Prueba con otro nombre o quita los filtros de disciplina e inactivos.');
    vacio.querySelector<HTMLButtonElement>('button')!.click();
    const [peticion] = listados();
    expect(peticion.request.params.get('incluirInactivos')).toBe('false');
    flush();
  }));

  it('error al leer la página: lo dice y deja reintentar', () => {
    crear();
    http.match((r) => r.url === `${API}/alumnos` && r.params.get('size') === '1').forEach((r) => r.flush({ totalElements: 1 }));
    listados()[0].flush('fallo', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    // 8b-D1: el bloque de error común.
    expect(texto('pn-bloque-error [role="alert"]')).toContain('No se pudo cargar el listado');
    expect(texto('pn-bloque-error')).toContain('Comprueba la conexión y vuelve a intentarlo.');
    el().querySelector<HTMLButtonElement>('pn-bloque-error button')!.click();
    expect(listados().length).toBe(1);
  });

  it('«Informes»: menú con teclado, el diálogo de tipos de hoy y las tres herramientas en el panel lateral (8b-D1)', () => {
    crear();
    responder();
    document.body.appendChild(el());
    const boton = el().querySelector<HTMLButtonElement>('#alumnos-informes')!;
    boton.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    fixture.detectChanges();
    const menu = el().querySelector<HTMLElement>('[role="menu"]')!;
    const opciones = Array.from(menu.querySelectorAll<HTMLButtonElement>('[role="menuitem"]'));
    expect(opciones.map((o) => o.textContent!.trim())).toEqual([
      'Generar informe…',
      'Gestión de mensualidades y licencias',
      'Listado de asistencia',
      'Listado de mensualidades',
    ]);
    expect(document.activeElement).toBe(opciones[0]);
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(el().querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(boton);

    const titulos = ['Gestión de mensualidades y licencias', 'Listado de asistencia', 'Listado de mensualidades'];
    titulos.forEach((titulo, i) => {
      boton.click();
      fixture.detectChanges();
      el().querySelectorAll<HTMLButtonElement>('[role="menuitem"]')[i + 1].click();
      fixture.detectChanges();
      const panel = el().querySelector<HTMLDialogElement>('dialog.pn-panel')!;
      expect(panel.open).toBeTrue();
      expect(panel.querySelector('h2')!.textContent!.trim()).toBe(titulo);
      const pie = Array.from(panel.querySelectorAll('.pn-panel__pie button')).map((b) => b.textContent!.trim());
      expect(pie[0]).toBe('Cancelar');
      if (i > 0) {
        expect(pie.slice(1)).toEqual(['Ver PDF', 'Descargar PDF']);
      }
      panel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
      fixture.detectChanges();
      expect(el().querySelector('dialog.pn-panel')).toBeNull();
      expect(document.activeElement).toBe(boton);
    });

    boton.click();
    fixture.detectChanges();
    el().querySelector<HTMLButtonElement>('[role="menuitem"]')!.click();
    fixture.detectChanges();
    const modal = el().querySelector('app-informe-modal');
    expect(modal).not.toBeNull();
    expect(fixture.componentInstance.opcionesInforme.length).toBe(18);
    // Como hoy, al abrir el diálogo se piden las temporadas de reservas de plaza.
    http.expectOne(`${API}/informes/reservas-plaza/temporadas`).flush(['2025/2026']);
    expect(fixture.componentInstance.temporadasReservasPlaza).toEqual(['2025/2026']);
    el().remove();
  });

  it('las tres herramientas salen del pie: el listado acaba en la paginación (decisión 270)', () => {
    crear();
    responder();
    expect(el().querySelector('app-herramientas-alumnos')).toBeNull();
    expect(el().querySelector('.alumnos__herramientas')).toBeNull();
    const contenido = el().querySelector('.alumnos__pagina')!;
    expect(contenido.lastElementChild!.classList).toContain('alumnos__paginacion');
  });

  it('«Quitar filtros» hace una sola petición del listado (la búsqueda vaciada no repite la carga)', fakeAsync(() => {
    crear();
    responder();
    const buscador = el().querySelector<HTMLInputElement>('#alumnos-buscar')!;
    buscador.value = 'zzz';
    buscador.dispatchEvent(new Event('input'));
    tick(300);
    listados()[0].flush(pagina(0, 0));
    fixture.detectChanges();
    el().querySelector<HTMLButtonElement>('pn-bloque-vacio button')!.click();
    tick(1000);
    const peticiones = listados();
    expect(peticiones.length).toBe(1);
    expect(peticiones[0].request.params.has('nombre')).toBeFalse();
    peticiones[0].flush(pagina(812));
    http.match(() => true).forEach((r) => r.flush([]));
    tick(1000);
    expect(listados().length).toBe(0);
    // Volver a buscar el mismo texto sí carga.
    buscador.value = 'zzz';
    buscador.dispatchEvent(new Event('input'));
    tick(300);
    expect(listados().length).toBe(1);
    flush();
  }));

  it('disciplina con grados y sin grado: «sin grado» en la línea 2 y «Sin grado» en el detalle', () => {
    crear();
    const sinGrado = alumno(1, { deportes: [{ ...(alumno(1)['deportes'] as any[])[0], grado: null }] });
    responder(1, [], { content: [sinGrado], totalElements: 1, totalPages: 1, number: 0, size: 25 } as never);
    expect(texto('.alumnos__linea2')).toContain('sin grado');
    el().querySelector<HTMLButtonElement>('.alumnos__desplegar')!.click();
    fixture.detectChanges();
    expect(texto('.alumnos__deporte')).toContain('Sin grado');
  });

  it('grado en el detalle con el texto de hoy: nombre del catálogo o el código sin guiones bajos', () => {
    crear();
    const raro = alumno(1, { deportes: [{ ...(alumno(1)['deportes'] as any[])[0], grado: 'ROJO_NEGRO_X' }] });
    responder(1, [], { content: [raro], totalElements: 1, totalPages: 1, number: 0, size: 25 } as never);
    el().querySelector<HTMLButtonElement>('.alumnos__desplegar')!.click();
    fixture.detectChanges();
    const detalle = texto('.alumnos__grado-detalle');
    expect(detalle).toBe('ROJO NEGRO X');
    expect(texto('.alumnos__linea2')).toContain('rojo negro x');
  });

  it('panel de cargos modal: aria-modal y el Tab no sale del panel', () => {
    crear();
    responder();
    document.body.appendChild(el());
    el().querySelector<HTMLButtonElement>('.alumnos__fila .alumnos__cobrar')!.click();
    fixture.detectChanges();
    const panel = el().querySelector<HTMLElement>('.alumnos__cargos')!;
    expect(panel.getAttribute('aria-modal')).toBe('true');
    const botones = Array.from(panel.querySelectorAll<HTMLButtonElement>('button'));
    const primero = botones[0];
    const ultimo = botones[botones.length - 1];
    ultimo.focus();
    const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    ultimo.dispatchEvent(tab);
    expect(tab.defaultPrevented).toBeTrue();
    expect(document.activeElement).toBe(primero);
    const atras = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true });
    primero.dispatchEvent(atras);
    expect(atras.defaultPrevented).toBeTrue();
    expect(document.activeElement).toBe(ultimo);
    expect(el().querySelector('.alumnos__cargos')).not.toBeNull();
    el().remove();
  });

  it('al cerrar la pantalla se cancela el refresco del pendiente en vuelo', async () => {
    crear();
    responder();
    el().querySelectorAll('.alumnos__fila')[1].querySelector<HTMLButtonElement>('.alumnos__cobrar')!.click();
    pintar();
    botonDialogo('Marcar como pagado').click();
    http.expectOne(`${API}/productos-alumno/801`).flush({});
    await esperar();
    const [refresco] = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    fixture.destroy();
    expect(refresco.cancelled).toBeTrue();
  });

  // ---------------------------------------------------------------- 8b-3b: estado en la dirección y carreras

  it('búsqueda, disciplina, «Inactivos», «Solo aptos», página y vista quedan en la dirección sin repetir cargas', fakeAsync(() => {
    crear();
    responder();
    const router = TestBed.inject(Router);
    const c = fixture.componentInstance;
    const buscador = el().querySelector<HTMLInputElement>('#alumnos-buscar')!;
    buscador.value = 'garcía';
    buscador.dispatchEvent(new Event('input'));
    tick(300);
    listados()[0].flush(pagina(60, 25));
    http.match(() => true).forEach((r) => r.flush([]));
    flush();
    expect(router.url).toContain('nombre=garc%C3%ADa');
    const disciplina = el().querySelector<HTMLSelectElement>('#alumnos-disciplina')!;
    disciplina.value = 'PILATES';
    disciplina.dispatchEvent(new Event('change'));
    listados()[0].flush(pagina(60, 25));
    http.match(() => true).forEach((r) => r.flush([]));
    el().querySelector<HTMLButtonElement>('.alumnos__filtro--inactivos')!.click();
    listados()[0].flush(pagina(60, 25));
    http.match(() => true).forEach((r) => r.flush([]));
    el().querySelector<HTMLButtonElement>('.alumnos__filtro--aptos')!.click();
    listados()[0].flush(pagina(60, 25));
    http.match(() => true).forEach((r) => r.flush([]));
    fixture.detectChanges();
    c.irAPagina(2);
    listados()[0].flush(pagina(60, 25, 26));
    http.match(() => true).forEach((r) => r.flush([]));
    fixture.detectChanges();
    el().querySelector<HTMLButtonElement>('.alumnos__vista-opcion[data-vista="tarjetas"]')!.click();
    flush();
    const params = router.parseUrl(router.url).queryParams;
    expect(params).toEqual({
      nombre: 'garcía',
      deporte: 'PILATES',
      inactivos: 'true',
      aptoParaExamen: 'true',
      pagina: '2',
      vista: 'tarjetas',
    });
    expect(listados().length).withContext('escribir la dirección no vuelve a cargar').toBe(0);
    // Sin filtros, la dirección se queda limpia.
    c.quitarFiltros();
    tick(1000);
    listados().forEach((r) => r.flush(pagina(812)));
    http.match(() => true).forEach((r) => r.flush([]));
    flush();
    expect(router.parseUrl(router.url).queryParams).toEqual({ vista: 'tarjetas' });
  }));

  it('al entrar lee de la dirección la búsqueda, los filtros, la página y la vista', async () => {
    await TestBed.inject(Router).navigateByUrl(
      '/?nombre=garc%C3%ADa&deporte=PILATES&inactivos=true&aptoParaExamen=true&pagina=2&vista=tarjetas'
    );
    crear();
    const [p] = listados();
    expect(p.request.params.get('nombre')).toBe('garcía');
    expect(p.request.params.get('deporte')).toBe('PILATES');
    expect(p.request.params.get('incluirInactivos')).toBe('true');
    expect(p.request.params.get('aptoParaExamen')).toBe('true');
    expect(p.request.params.get('page')).toBe('2');
    p.flush(pagina(812, 25, 26));
    http.match(() => true).forEach((r) => r.flush([]));
    fixture.detectChanges();
    expect(el().querySelector<HTMLInputElement>('#alumnos-buscar')!.value).toBe('garcía');
    expect(el().querySelector('.alumnos__lista--tarjetas')).not.toBeNull();
    expect(el().querySelector('.alumnos__filtro--inactivos')!.getAttribute('aria-pressed')).toBe('true');
  });

  it('una página inválida en la dirección empieza en la 1', async () => {
    await TestBed.inject(Router).navigateByUrl('/?pagina=abc');
    crear();
    expect(listados()[0].request.params.get('page')).toBe('1');
  });

  it('cambiar de página con el panel de cargos abierto: no queda ningún cargo de la página anterior', () => {
    crear();
    responder();
    el().querySelector<HTMLButtonElement>('.alumnos__fila .alumnos__cobrar')!.click();
    fixture.detectChanges();
    expect(el().querySelector('.alumnos__cargos')).not.toBeNull();
    const pasos = el().querySelectorAll<HTMLButtonElement>('.alumnos__pagina-paso');
    pasos[pasos.length - 1].click();
    fixture.detectChanges();
    expect(el().querySelector('.alumnos__cargos')).toBeNull();
    expect(el().querySelector('.alumnos__cobrar')).toBeNull();
    listados()[0].flush(pagina(812, 25, 26));
    fixture.detectChanges();
    expect(el().querySelector('.alumnos__cobrar')).withContext('pendientes sin llegar').toBeNull();
    const [p] = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    expect(p.request.params.get('alumnoIds')!.split(',')[0]).toBe('26');
    // Una respuesta con alumnos de otra página no pinta nada.
    p.flush([DOS_CARGOS, UN_CARGO]);
    fixture.detectChanges();
    expect(el().querySelector('.alumnos__cobrar')).toBeNull();
  });

  it('los pendientes que no han llegado al cambiar de página o de filtro se cancelan', () => {
    crear();
    http.match((r) => r.url === `${API}/alumnos` && r.params.get('size') === '1').forEach((r) => r.flush({ totalElements: 790 }));
    listados()[0].flush(pagina(812));
    fixture.detectChanges();
    const [enVuelo] = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    fixture.componentInstance.irAPagina(2);
    expect(enVuelo.cancelled).toBeTrue();
    listados()[0].flush(pagina(812, 25, 26));
    const [otra] = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    fixture.componentInstance.alternarInactivos();
    expect(otra.cancelled).toBeTrue();
  });

  // ---- 8b-C: el acento de la escuela (Moiskimdo) en los controles elegidos, los secundarios y la página actual
  it('8b-C: con la marca, el filtro activo, el secundario, la vista elegida, la página actual y la pastilla del cobro llevan el acento', fakeAsync(() => {
    const ACENTO = 'rgb(1, 112, 127)';
    const SUAVE = 'rgb(213, 245, 252)';
    const BORDE = 'rgb(69, 149, 163)';
    crear();
    TestBed.inject(AcentoEscuelaActual).poner({ acento: '#01707F', acentoSuave: '#D5F5FC', acentoBorde: '#4595A3' });
    responder(60);
    const raiz = el().querySelector<HTMLElement>('.pn')!;
    expect(raiz.style.getPropertyValue('--pn-acento')).toBe('#01707F');

    el().querySelector<HTMLButtonElement>('.alumnos__filtro--inactivos')!.click();
    const [p] = listados();
    p.flush(pagina(60));
    http.match(() => true).forEach((r) => r.flush([DOS_CARGOS, UN_CARGO]));
    fixture.detectChanges();

    const filtro = getComputedStyle(el().querySelector('.alumnos__filtro--inactivos')!);
    expect(filtro.backgroundColor).withContext('filtro activo: fondo').toBe(SUAVE);
    expect(filtro.borderTopColor).withContext('filtro activo: borde').toBe(BORDE);
    expect(filtro.color).withContext('filtro activo: texto').toBe(ACENTO);

    const informes = getComputedStyle(el().querySelector('.alumnos__boton-secundario')!);
    expect(informes.borderTopColor).withContext('secundario: borde').toBe(BORDE);
    expect(informes.color).withContext('secundario: texto').toBe(ACENTO);
    const cobrar = getComputedStyle(el().querySelector('.alumnos__cobrar')!);
    expect(cobrar.borderTopColor).withContext('«Cobrar» de fila: borde').toBe(BORDE);
    expect(cobrar.color).withContext('«Cobrar» de fila: texto').toBe(ACENTO);

    const vista = getComputedStyle(el().querySelector('.alumnos__vista-opcion[aria-pressed="true"]')!);
    expect(vista.backgroundColor).withContext('segmento elegido: fondo').toBe(SUAVE);
    expect(vista.color).withContext('segmento elegido: texto').toBe(ACENTO);
    expect(vista.boxShadow).withContext('segmento elegido: borde').toContain(BORDE);

    const actual = getComputedStyle(el().querySelector('.alumnos__pagina-num[aria-current="page"]')!);
    expect(actual.backgroundColor).withContext('página actual: fondo').toBe(SUAVE);
    expect(actual.color).withContext('página actual: texto').toBe(ACENTO);

    const avatar = getComputedStyle(el().querySelector('.alumnos__avatar')!);
    expect(avatar.backgroundColor).withContext('avatar de iniciales: fondo').toBe(SUAVE);
    expect(avatar.color).withContext('avatar de iniciales: texto').toBe(ACENTO);

    const primario = getComputedStyle(el().querySelector('.alumnos__boton-primario')!);
    expect(primario.backgroundColor).withContext('el principal no cambia').toBe('rgb(26, 34, 48)');

    el().querySelectorAll<HTMLButtonElement>('.alumnos__fila')[1].querySelector<HTMLButtonElement>('.alumnos__cobrar')!.click();
    pintar();
    const elegida = getComputedStyle(dialogo()!.querySelector('[role="radio"][aria-checked="true"]')!);
    expect(elegida.backgroundColor).withContext('pastilla elegida: fondo').toBe(SUAVE);
    expect(elegida.borderTopColor).withContext('pastilla elegida: borde').toBe(BORDE);
    expect(elegida.color).withContext('pastilla elegida: texto').toBe(ACENTO);
    const cancelar = getComputedStyle(botonDialogo('Cancelar'));
    expect(cancelar.borderTopColor).withContext('«Cancelar» del diálogo: borde').toBe(BORDE);
    expect(cancelar.color).withContext('«Cancelar» del diálogo: texto').toBe(ACENTO);
    botonDialogo('Cancelar').click();
    pintar();
    flush();
  }));
});
