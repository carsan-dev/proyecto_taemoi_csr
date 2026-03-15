import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuditoriaSistemaComponent } from './auditoria-sistema.component';
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

  it('carga eventos y modulos al iniciar', () => {
    expect(endpointsSpy.obtenerAuditoriaModulos).toHaveBeenCalled();
    expect(endpointsSpy.obtenerAuditoriaEventos).toHaveBeenCalledWith(
      jasmine.objectContaining({ resultado: 'EXITO', incluirRuido: false }),
      1,
      25
    );
    expect(component.eventos.length).toBe(1);
  });

  it('permite cambiar a pestana de errores', () => {
    component.cambiarPestanaResultado('ERRORES');

    expect(endpointsSpy.obtenerAuditoriaEventos).toHaveBeenCalledWith(
      jasmine.objectContaining({ resultado: 'ERROR', incluirRuido: false }),
      1,
      25
    );
  });

  it('debe cargar detalle al solicitarlo', () => {
    component.verDetalle(component.eventos[0]);
    expect(endpointsSpy.obtenerAuditoriaEventoDetalle).toHaveBeenCalledWith(1);
  });
});
