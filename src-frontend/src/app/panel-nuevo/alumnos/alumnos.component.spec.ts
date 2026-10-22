import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import Swal from 'sweetalert2';

import { environment } from '../../../environments/environment';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { LoadingService } from '../../servicios/generales/loading.service';
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

  beforeEach(async () => {
    localStorage.removeItem(CLAVE_VISTA);
    await TestBed.configureTestingModule({
      imports: [AlumnosComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LoadingService, useValue: jasmine.createSpyObj('LoadingService', ['show', 'hide']) },
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
    localStorage.removeItem(CLAVE_VISTA);
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

  it('«Cobrar» con un cargo: el diálogo y el PUT de la tesorería de hoy, y refresca ese alumno', async () => {
    crear();
    responder();
    const promesa = Promise.resolve({ isConfirmed: true, value: 'EFECTIVO' } as never);
    const fire = spyOn(Swal, 'fire').and.returnValue(promesa);
    el().querySelectorAll<HTMLButtonElement>('.alumnos__fila')[1].querySelector<HTMLButtonElement>('.alumnos__cobrar')!.click();
    const opciones = fire.calls.mostRecent().args[0] as any;
    expect(opciones.title).toBe('Confirmar cobro');
    expect(opciones.text).toBe('Marcar como pagado "MENSUALIDAD FEBRERO 2026 - TAEKWONDO"?');
    expect(opciones.inputLabel).toBe('Método (opcional)');
    await promesa;
    await Promise.resolve();
    const put = http.expectOne(`${API}/productos-alumno/801`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ pagado: true, metodoPago: 'EFECTIVO' });
    expect(put.request.withCredentials).toBeTrue();
    put.flush({ id: 801, pagado: true });
    const [refresco] = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    expect(refresco.request.params.get('alumnoIds')).toBe('2');
    refresco.flush([]);
    fixture.detectChanges();
    expect(el().querySelectorAll('.alumnos__fila')[1].querySelector('.alumnos__al-dia')).not.toBeNull();
  });

  it('«Cobrar» cancelado no envía nada; sin método no envía metodoPago', async () => {
    crear();
    responder();
    const cancelar = Promise.resolve({ isConfirmed: false } as never);
    const fire = spyOn(Swal, 'fire').and.returnValue(cancelar);
    const boton = el().querySelectorAll('.alumnos__fila')[1].querySelector<HTMLButtonElement>('.alumnos__cobrar')!;
    boton.click();
    await cancelar;
    await Promise.resolve();
    http.expectNone(`${API}/productos-alumno/801`);
    const sinMetodo = Promise.resolve({ isConfirmed: true, value: '' } as never);
    fire.and.returnValue(sinMetodo);
    boton.click();
    await sinMetodo;
    await Promise.resolve();
    expect(http.expectOne(`${API}/productos-alumno/801`).request.body).toEqual({ pagado: true });
  });

  it('«Cobrar» con varios cargos abre el panel con cada cargo; cada «Cobrar» hace lo mismo que hoy', async () => {
    crear();
    responder();
    const fire = spyOn(Swal, 'fire');
    const boton = el().querySelector<HTMLButtonElement>('.alumnos__fila .alumnos__cobrar')!;
    boton.click();
    fixture.detectChanges();
    expect(fire).not.toHaveBeenCalled();
    const panel = el().querySelector<HTMLElement>('.alumnos__cargos')!;
    expect(panel.getAttribute('role')).toBe('dialog');
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    const cargos = panel.querySelectorAll('.alumnos__cargo');
    expect(cargos.length).toBe(2);
    expect(cargos[0].textContent).toContain('MENSUALIDAD ENERO 2026 - TAEKWONDO');
    expect(cargos[0].textContent).toContain(`30,00${NBSP}€`);
    expect(cargos[0].textContent).toContain('Vencido desde el 06/01/2026');

    const promesa = Promise.resolve({ isConfirmed: true, value: 'BIZUM' } as never);
    fire.and.returnValue(promesa);
    cargos[1].querySelector<HTMLButtonElement>('button')!.click();
    expect((fire.calls.mostRecent().args[0] as any).text).toBe('Marcar como pagado "DERECHO A EXAMEN"?');
    await promesa;
    await Promise.resolve();
    const put = http.expectOne(`${API}/productos-alumno/702`);
    expect(put.request.body).toEqual({ pagado: true, metodoPago: 'BIZUM' });
    put.flush({});
    const [refresco] = http.match((r) => r.url === `${API}/tesoreria/pendientes-por-alumno`);
    expect(refresco.request.params.get('alumnoIds')).toBe('1');
    refresco.flush([{ ...DOS_CARGOS, importe: 30, cargos: 1, detalle: [DOS_CARGOS.detalle[0]] }]);
    fixture.detectChanges();
    expect(el().querySelectorAll('.alumnos__cargos .alumnos__cargo').length).toBe(1);
    expect(el().querySelector('.alumnos__fila .alumnos__importe')!.textContent!.trim()).toBe(`30,00${NBSP}€`);
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
  });

  it('error al leer la página: lo dice y deja reintentar', () => {
    crear();
    http.match((r) => r.url === `${API}/alumnos` && r.params.get('size') === '1').forEach((r) => r.flush({ totalElements: 1 }));
    listados()[0].flush('fallo', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(texto('.alumnos__estado')).toContain('No se pudo cargar el listado.');
    el().querySelector<HTMLButtonElement>('.alumnos__estado button')!.click();
    expect(listados().length).toBe(1);
  });

  it('«Informes»: menú con teclado, el diálogo de tipos de hoy y las tres herramientas del pie', () => {
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

    boton.click();
    fixture.detectChanges();
    el().querySelectorAll<HTMLButtonElement>('[role="menuitem"]')[2].click();
    fixture.detectChanges();
    const asistencia = Array.from(el().querySelectorAll<HTMLDetailsElement>('app-herramientas-alumnos details'))[1];
    expect(asistencia.open).toBeTrue();
    expect(asistencia.textContent).toContain('Listado de Asistencia');

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

  it('las tres herramientas del pie son las de hoy (mismo componente)', () => {
    crear();
    responder();
    const titulos = Array.from(el().querySelectorAll('app-herramientas-alumnos summary span')).map((s) => s.textContent!.trim());
    expect(titulos).toEqual(['Gestión de Mensualidades y Licencias', 'Listado de Asistencia', 'Listado de Mensualidades']);
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
    el().querySelector<HTMLButtonElement>('.alumnos__estado button')!.click();
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
});
