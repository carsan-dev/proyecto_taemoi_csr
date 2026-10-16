import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuditoriaSistemaComponent, describirAccion, formatearCuando } from './auditoria-sistema.component';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';

describe('AuditoriaSistemaComponent', () => {
  let component: AuditoriaSistemaComponent;
  let fixture: ComponentFixture<AuditoriaSistemaComponent>;
  let endpointsSpy: jasmine.SpyObj<EndpointsService>;

  beforeEach(async () => {
    endpointsSpy = jasmine.createSpyObj<EndpointsService>('EndpointsService', [
      'obtenerAuditoriaEventos',
      'obtenerAuditoriaEventoDetalle',
      'obtenerAuditoriaModulos',
    ]);

    endpointsSpy.obtenerAuditoriaModulos.and.returnValue(of(['alumnos', 'productos']));
    endpointsSpy.obtenerAuditoriaEventos.and.returnValue(
      of({
        content: [
          {
            id: 1,
            fechaEvento: '2026-02-10T10:00:00Z',
            accion: 'UPDATE',
            metodoHttp: 'PUT',
            endpoint: '/api/productos-alumno/1',
            modulo: 'productos-alumno',
            recursoId: 1,
            estadoHttp: 200,
            usuarioId: 99,
            usuarioEmail: 'admin@test.com',
            usuarioNombre: 'Admin Test',
            resumen: 'UPDATE productos-alumno #1 -> 200',
            payloadTruncado: false,
          },
        ],
        totalPages: 1,
        totalElements: 1,
        size: 25,
        number: 0,
      } as any)
    );
    endpointsSpy.obtenerAuditoriaEventoDetalle.and.returnValue(
      of({
        id: 1,
        fechaEvento: '2026-02-10T10:00:00Z',
        accion: 'UPDATE',
        metodoHttp: 'PUT',
        endpoint: '/api/productos-alumno/1',
        modulo: 'productos-alumno',
        recursoId: 1,
        estadoHttp: 200,
        usuarioId: 99,
        usuarioEmail: 'admin@test.com',
        usuarioNombre: 'Admin Test',
        ipCliente: '127.0.0.1',
        userAgent: 'test-agent',
        queryParamsJson: '{}',
        payloadJson: '{"pagado":true}',
        payloadTruncado: false,
        resumen: 'UPDATE productos-alumno #1 -> 200',
      } as any)
    );

    await TestBed.configureTestingModule({
      imports: [AuditoriaSistemaComponent],
      providers: [{ provide: EndpointsService, useValue: endpointsSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditoriaSistemaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crear el componente', () => {
    expect(component).toBeTruthy();
  });

  it('carga eventos y modulos al iniciar pidiendo solo acciones con éxito', () => {
    expect(endpointsSpy.obtenerAuditoriaModulos).toHaveBeenCalled();
    expect(endpointsSpy.obtenerAuditoriaEventos).toHaveBeenCalledWith(
      jasmine.objectContaining({ resultado: 'EXITO' }),
      1,
      25
    );
    expect(component.eventos.length).toBe(1);
  });

  it('no envía filtro de endpoint ni de ruido, y mantiene resultado EXITO al filtrar', () => {
    component.filtroAccion = 'DELETE';
    component.filtroTexto = 'Ana';
    component.aplicarFiltros();

    const filtros = endpointsSpy.obtenerAuditoriaEventos.calls.mostRecent().args[0];
    expect(filtros.resultado).toBe('EXITO');
    expect(filtros.accion).toBe('DELETE');
    expect(filtros.texto).toBe('Ana');
    expect('endpoint' in filtros).toBeFalse();
    expect('incluirRuido' in filtros).toBeFalse();
  });

  it('el filtro Consultas de salud envía accion=READ y modulo=salud aunque haya otro módulo elegido', () => {
    component.filtroModulo = 'alumnos';
    component.filtroAccion = 'READ';
    component.aplicarFiltros();

    const filtros = endpointsSpy.obtenerAuditoriaEventos.calls.mostRecent().args[0];
    expect(filtros.accion).toBe('READ');
    expect(filtros.modulo).toBe('salud');
    expect(filtros.resultado).toBe('EXITO');

    fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#filtroModulo');
    expect(select.disabled).toBeTrue();
    expect(select.options[select.selectedIndex].textContent?.trim()).toBe('salud');
  });

  it('pinta título, columnas y chip de acción del registro', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('h1')?.textContent).toContain('Registro de acciones');
    const cabeceras = Array.from(el.querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(cabeceras.slice(0, 5)).toEqual(['Cuándo', 'Acción', 'Qué', 'Módulo', 'Quién']);
    expect(el.querySelector('.accion-chip.cambio')?.textContent?.trim()).toBe('Cambio');
    expect(el.textContent).not.toContain('/api/productos-alumno/1');
    expect(el.querySelector('.resultado-tabs')).toBeNull();
  });

  it('muestra el estado vacío cuando no hay acciones', () => {
    endpointsSpy.obtenerAuditoriaEventos.and.returnValue(
      of({ content: [], totalPages: 0, totalElements: 0, size: 25, number: 0 } as any)
    );
    component.aplicarFiltros();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Todavía no hay acciones en este periodo');
  });

  it('debe cargar detalle al solicitarlo y ofrecer datos técnicos si hay payload', () => {
    component.verDetalle(component.eventos[0]);
    fixture.detectChanges();

    expect(endpointsSpy.obtenerAuditoriaEventoDetalle).toHaveBeenCalledWith(1);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.detalle-card')?.textContent).toContain('Ficha afectada');
    expect(el.querySelector('details.datos-tecnicos')).not.toBeNull();
  });

  it('oculta los datos técnicos cuando payload y query params son null (vista propietario)', () => {
    endpointsSpy.obtenerAuditoriaEventoDetalle.and.returnValue(
      of({ id: 1, fechaEvento: '2026-02-10T10:00:00Z', accion: 'CREATE', metodoHttp: 'POST', endpoint: '/api/alumnos',
        modulo: 'alumnos', recursoId: 7, estadoHttp: 201, usuarioId: 99, usuarioEmail: 'a@test.com', usuarioNombre: null,
        ipCliente: null, userAgent: null, queryParamsJson: null, payloadJson: null, payloadTruncado: false,
        resumen: 'Alta de alumno' } as any)
    );
    component.verDetalle(component.eventos[0]);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('details.datos-tecnicos')).toBeNull();
    expect(el.querySelector('.detalle-card')?.textContent).toContain('a@test.com');
  });
});

describe('describirAccion', () => {
  it('traduce las acciones y marca las consultas de salud', () => {
    expect(describirAccion('CREATE', 'alumnos')).toEqual({ etiqueta: 'Alta', tono: 'alta' });
    expect(describirAccion('UPDATE', 'alumnos')).toEqual({ etiqueta: 'Cambio', tono: 'cambio' });
    expect(describirAccion('DELETE', 'alumnos')).toEqual({ etiqueta: 'Baja', tono: 'baja' });
    expect(describirAccion('READ', 'salud')).toEqual({ etiqueta: 'Salud', tono: 'salud' });
    expect(describirAccion('READ', 'alumnos').tono).toBe('neutro');
  });
});

describe('formatearCuando', () => {
  const ahora = new Date(2026, 9, 16, 12, 0, 0);

  it('usa hoy/ayer y fecha corta para el resto', () => {
    expect(formatearCuando(new Date(2026, 9, 16, 10, 42), ahora)).toBe('hoy 10:42');
    expect(formatearCuando(new Date(2026, 9, 15, 19, 3), ahora)).toBe('ayer 19:03');
    expect(formatearCuando(new Date(2026, 9, 2, 8, 5), ahora)).toBe('02/10 08:05');
    expect(formatearCuando(new Date(2025, 11, 31, 23, 59), ahora)).toBe('31/12/2025 23:59');
    expect(formatearCuando(null, ahora)).toBe('-');
  });
});
