import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { PageScrollService } from '../../../servicios/generales/page-scroll.service';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { Catalogo } from '../../../features/catalogo/catalogo.models';
import { CrearAlumnoComponent } from './crear-alumno.component';

describe('CrearAlumnoComponent: disciplinas del catálogo', () => {
  let fixture: ComponentFixture<CrearAlumnoComponent>;
  let component: CrearAlumnoComponent;

  async function crear(catalogo: Catalogo): Promise<void> {
    const endpoints = {
      obtenerGrados: () => of([]),
      obtenerGradosPorFechaNacimiento: () => of([]),
    };
    await TestBed.configureTestingModule({
      imports: [CrearAlumnoComponent],
      providers: [
        provideRouter([]),
        { provide: EndpointsService, useValue: endpoints },
        { provide: PageScrollService, useValue: {} },
        proveerCatalogoTesting(catalogo),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CrearAlumnoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const raiz = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const existe = (selector: string): boolean => raiz().querySelector(selector) !== null;
  const fila = () => component.deportesFormArray.at(0);

  function elegirDeporte(codigo: string): void {
    fila().get('deporte')?.setValue(codigo);
    component.onDeporteChangeEnLista(0);
    fixture.detectChanges();
  }

  function opcionesDeporte(): { valor: string; texto: string }[] {
    const opciones = Array.from(raiz().querySelectorAll<HTMLOptionElement>('select#deporte-0 option'));
    return opciones.filter((o) => o.value !== '').map((o) => ({ valor: o.value, texto: o.textContent?.trim() ?? '' }));
  }

  describe('Taemoi', () => {
    beforeEach(() => crear(CATALOGO_TAEMOI));

    it('ofrece las disciplinas activas con el nombre del catálogo, en su orden', () => {
      expect(opcionesDeporte()).toEqual([
        { valor: 'TAEKWONDO', texto: 'Taekwondo' },
        { valor: 'KICKBOXING', texto: 'Kickboxing' },
        { valor: 'PILATES', texto: 'Pilates' },
        { valor: 'DEFENSA_PERSONAL_FEMENINA', texto: 'Defensa Personal Femenina' },
      ]);
    });

    it('Taekwondo: grado, licencia y (con licencia) competidor', () => {
      elegirDeporte('TAEKWONDO');
      expect(existe('#grado-0')).toBeTrue();
      expect(existe('#tieneLicencia-0')).toBeTrue();
      expect(existe('#competidor-0')).toBeFalse();

      fila().get('tieneLicencia')?.setValue(true);
      fixture.detectChanges();
      expect(existe('#competidor-0')).toBeTrue();
    });

    it('Pilates: sin grado, sin licencia y sin competidor', () => {
      elegirDeporte('PILATES');
      expect(existe('#grado-0')).toBeFalse();
      expect(existe('#tieneLicencia-0')).toBeFalse();
      expect(existe('#competidor-0')).toBeFalse();
    });

    it('al pasar a una disciplina sin grado quita la exigencia de grado', () => {
      elegirDeporte('TAEKWONDO');
      expect(fila().get('grado')?.hasError('required')).toBeTrue();
      elegirDeporte('DEFENSA_PERSONAL_FEMENINA');
      expect(fila().get('grado')?.valid).toBeTrue();
      expect(fila().get('fechaGrado')?.value).toBe('');
    });

    it('Taekwondo -> Kickboxing conserva licencia y competición (ambas las admiten)', () => {
      elegirDeporte('TAEKWONDO');
      fila().patchValue({ tieneLicencia: true, numeroLicencia: 123, competidor: true, categoria: 'Cadete', peso: 50 });
      elegirDeporte('KICKBOXING');
      expect(fila().value).toEqual(
        jasmine.objectContaining({ tieneLicencia: true, numeroLicencia: 123, competidor: true, categoria: 'Cadete', peso: 50 })
      );
    });

    it('al cambiar a una disciplina que no admite licencia ni competición reinicia esos datos', () => {
      elegirDeporte('TAEKWONDO');
      fila().patchValue({
        tieneLicencia: true,
        numeroLicencia: 123,
        competidor: true,
        categoria: 'Cadete',
        fechaAltaCompeticion: '2026-01-01',
        fechaAltaCompetidorInicial: '2026-01-01',
        peso: 50,
      });

      elegirDeporte('PILATES');

      expect(fila().value).toEqual(
        jasmine.objectContaining({
          tieneLicencia: false,
          numeroLicencia: null,
          competidor: false,
          categoria: '',
          fechaAltaCompeticion: '',
          fechaAltaCompetidorInicial: '',
          peso: null,
        })
      );
    });

    it('no envía competidor ni categoría de una disciplina que no compite', () => {
      elegirDeporte('TAEKWONDO');
      fila().patchValue({ tieneLicencia: true, competidor: true, categoria: 'Cadete', peso: 50 });
      elegirDeporte('PILATES');
      // Aunque el valor se hubiera filtrado, el envío lo descarta.
      fila().patchValue({ competidor: true });
      const enviado: any[] = [];
      (component as any).endpointsService.crearAlumno = (datos: any) => {
        enviado.push(datos);
        return of({ id: 1 });
      };
      (component as any).router = { navigate: () => Promise.resolve(true) };
      component.alumnoData.patchValue({
        nombre: 'Ana', apellidos: 'Pérez', fechaNacimiento: '2010-01-01', direccion: 'Calle 1',
        telefono: '600000000', email: 'ana@example.test',
      });
      fila().patchValue({ tipoTarifa: 'PILATES' });
      component.onSubmit();
      expect(enviado.length).toBe(1);
      expect(enviado[0].deportesInicial[0].competidor).toBeFalse();
      expect(enviado[0].deportesInicial[0].categoria).toBe('');
    });
  });

  describe('segunda escuela', () => {
    beforeEach(() => crear(CATALOGO_SEGUNDA_ESCUELA));

    it('ofrece solo sus disciplinas activas (JUDO), no las de Taemoi ni las inactivas', () => {
      expect(opcionesDeporte()).toEqual([{ valor: 'JUDO', texto: 'Judo' }]);
    });

    it('JUDO: grado, sin licencia obligatoria y competidor disponible sin licencia', () => {
      elegirDeporte('JUDO');
      expect(existe('#grado-0')).toBeTrue();
      expect(existe('#tieneLicencia-0')).toBeFalse();
      expect(existe('#competidor-0')).toBeTrue();
    });

    it('Judo -> Yoga (inactiva, sin competición) reinicia la competición', () => {
      elegirDeporte('JUDO');
      fila().patchValue({ competidor: true, categoria: 'Infantil judo', peso: 30 });
      elegirDeporte('YOGA');
      expect(fila().value).toEqual(jasmine.objectContaining({ competidor: false, categoria: '', peso: null }));
    });

    describe('sin disciplina elegida', () => {
      beforeEach(() => {
        jasmine.clock().install();
        jasmine.clock().mockDate(new Date(2026, 5, 15, 12, 0, 0));
      });
      afterEach(() => jasmine.clock().uninstall());

      it('la categoría se calcula con la primera disciplina activa, no con TAEKWONDO', () => {
        component.alumnoData.get('fechaNacimiento')?.setValue('2018-03-01', { emitEvent: false });
        fila().patchValue({ competidor: true });
        component.onCompetidorChangeEnLista(0);
        expect(fila().get('categoria')?.value).toBe('Infantil judo');
      });
    });
  });
});
