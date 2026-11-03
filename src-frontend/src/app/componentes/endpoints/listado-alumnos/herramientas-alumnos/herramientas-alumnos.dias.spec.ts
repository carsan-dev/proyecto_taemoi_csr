import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { HerramientasAlumnosComponent } from './herramientas-alumnos.component';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { InformePdfService } from '../../../../servicios/generales/informe-pdf.service';
import { LoadingService } from '../../../../servicios/generales/loading.service';
import { proveerCatalogoTesting } from '../../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../../features/catalogo/testing/catalogo.fixtures';

describe('HerramientasAlumnosComponent: días del listado de asistencia (decisión 380)', () => {
  const crear = (turnos$: any) => {
    const api = { obtenerTurnosDTO: () => turnos$ };
    TestBed.configureTestingModule({
      providers: [
        InformePdfService,
        { provide: LoadingService, useValue: jasmine.createSpyObj('LoadingService', ['show', 'hide']) },
        { provide: EndpointsService, useValue: api },
        proveerCatalogoTesting(CATALOGO_TAEMOI),
      ],
    });
    const componente = TestBed.runInInjectionContext(() => new HerramientasAlumnosComponent());
    componente.ngOnInit();
    return componente;
  };
  const turnos = (...dias: string[]) => of(dias.map((diaSemana, id) => ({ id, diaSemana })));
  const laborables = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes'];

  it('sin turnos de fin de semana, cinco días', () => {
    expect(crear(turnos('Lunes', 'Viernes')).grupos).toEqual(laborables);
  });

  it('con sábado, seis días con sábado', () => {
    expect(crear(turnos('Lunes', 'Sábado')).grupos).toEqual([...laborables, 'sábado']);
  });

  it('solo con domingo, seis días con domingo detrás del viernes', () => {
    expect(crear(turnos('Domingo')).grupos).toEqual([...laborables, 'domingo']);
  });

  it('con ambos, siete días en orden', () => {
    expect(crear(turnos('Domingo', 'Sábado')).grupos).toEqual([...laborables, 'sábado', 'domingo']);
  });

  it('si fallan los turnos, los cinco de siempre', () => {
    expect(crear(throwError(() => new Error())).grupos).toEqual(laborables);
  });
});
