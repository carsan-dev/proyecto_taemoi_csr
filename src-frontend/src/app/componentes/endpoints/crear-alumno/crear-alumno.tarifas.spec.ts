import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { PageScrollService } from '../../../servicios/generales/page-scroll.service';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { Catalogo } from '../../../features/catalogo/catalogo.models';
import { CrearAlumnoComponent } from './crear-alumno.component';

describe('CrearAlumnoComponent: tarifas del catálogo', () => {
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

  function elegirTarifa(codigo: string): void {
    fila().get('tipoTarifa')?.setValue(codigo);
    component.onTipoTarifaChangeEnLista(0);
    fixture.detectChanges();
  }

  function elegirRol(rol: string): void {
    fila().get('rolFamiliar')?.setValue(rol);
    component.onRolFamiliarChangeEnLista(0, { target: { value: rol } });
    fixture.detectChanges();
  }

  function opcionesTarifa(): { valor: string; texto: string }[] {
    const opciones = Array.from(raiz().querySelectorAll<HTMLOptionElement>('select#tipoTarifa-0 option'));
    return opciones.filter((o) => o.value !== '').map((o) => ({ valor: o.value, texto: o.textContent?.trim() ?? '' }));
  }

  describe('Taemoi', () => {
    beforeEach(() => crear(CATALOGO_TAEMOI));

    it('D7/D8 Taekwondo: sus 7 tarifas en orden del catálogo, con el nombre y el código como valor', () => {
      elegirDeporte('TAEKWONDO');
      expect(opcionesTarifa()).toEqual([
        { valor: 'ADULTO', texto: 'Adulto' },
        { valor: 'ADULTO_GRUPO', texto: 'Adulto Grupo' },
        { valor: 'FAMILIAR', texto: 'Familiar' },
        { valor: 'INFANTIL', texto: 'Infantil' },
        { valor: 'INFANTIL_GRUPO', texto: 'Infantil Grupo' },
        { valor: 'HERMANOS', texto: 'Hermanos' },
        { valor: 'PADRES_HIJOS', texto: 'Padres e Hijos' },
      ]);
    });

    it('D7 Kickboxing, Pilates y Defensa Personal Femenina: solo su tarifa', () => {
      elegirDeporte('KICKBOXING');
      expect(opcionesTarifa()).toEqual([{ valor: 'KICKBOXING', texto: 'Kickboxing' }]);
      elegirDeporte('PILATES');
      expect(opcionesTarifa()).toEqual([{ valor: 'PILATES', texto: 'Pilates' }]);
      elegirDeporte('DEFENSA_PERSONAL_FEMENINA');
      expect(opcionesTarifa()).toEqual([{ valor: 'DEFENSA_PERSONAL_FEMENINA', texto: 'Defensa Personal Femenina' }]);
    });

    it('sin disciplina: sin tarifas', () => {
      expect(opcionesTarifa()).toEqual([]);
    });

    it('al cambiar a una disciplina que no admite la tarifa elegida, la tarifa se vacía', () => {
      elegirDeporte('TAEKWONDO');
      elegirTarifa('ADULTO');
      elegirDeporte('KICKBOXING');
      expect(fila().get('tipoTarifa')?.value).toBe('');
    });

    it('importes por defecto iguales a los anteriores', () => {
      elegirDeporte('TAEKWONDO');
      const esperados: [string, number][] = [
        ['ADULTO', 30], ['ADULTO_GRUPO', 20], ['FAMILIAR', 0], ['INFANTIL', 28],
        ['INFANTIL_GRUPO', 20], ['HERMANOS', 26], ['PADRES_HIJOS', 0],
      ];
      esperados.forEach(([codigo, importe]) => {
        elegirTarifa(codigo);
        expect(fila().get('cuantiaTarifa')?.value).withContext(codigo).toBe(importe);
      });
      elegirDeporte('DEFENSA_PERSONAL_FEMENINA');
      elegirTarifa('DEFENSA_PERSONAL_FEMENINA');
      expect(fila().get('cuantiaTarifa')?.value).toBe(25);
    });

    it('PADRES_HIJOS pide el rol (obligatorio) y el importe sigue al rol: 28 padre/madre, 26 hijo/hija', () => {
      elegirDeporte('TAEKWONDO');
      elegirTarifa('PADRES_HIJOS');
      expect(existe('#rolFamiliar-0')).toBeTrue();
      expect(existe('#grupoFamiliar-0')).toBeFalse();
      expect(fila().get('rolFamiliar')?.enabled).toBeTrue();
      expect(fila().get('rolFamiliar')?.hasError('required')).toBeTrue();

      elegirRol('MADRE');
      expect(fila().get('cuantiaTarifa')?.value).toBe(28);
      elegirRol('HIJO');
      expect(fila().get('cuantiaTarifa')?.value).toBe(26);
    });

    it('HERMANOS pide el grupo (obligatorio) y no el rol', () => {
      elegirDeporte('TAEKWONDO');
      elegirTarifa('HERMANOS');
      expect(existe('#grupoFamiliar-0')).toBeTrue();
      expect(existe('#rolFamiliar-0')).toBeFalse();
      expect(fila().get('grupoFamiliar')?.enabled).toBeTrue();
      expect(fila().get('grupoFamiliar')?.hasError('required')).toBeTrue();
      expect(fila().get('rolFamiliar')?.disabled).toBeTrue();
    });

    it('una tarifa sin banderas no pide rol ni grupo y los vacía', () => {
      elegirDeporte('TAEKWONDO');
      elegirTarifa('PADRES_HIJOS');
      elegirRol('PADRE');
      elegirTarifa('ADULTO');
      expect(existe('#rolFamiliar-0')).toBeFalse();
      expect(existe('#grupoFamiliar-0')).toBeFalse();
      expect(fila().get('rolFamiliar')?.value).toBe('');
      expect(fila().get('rolFamiliar')?.disabled).toBeTrue();
      expect(fila().get('cuantiaTarifa')?.value).toBe(30);
    });

    it('el rol no cambia el importe de una tarifa que no pide rol', () => {
      elegirDeporte('TAEKWONDO');
      elegirTarifa('INFANTIL');
      component.onRolFamiliarChangeEnLista(0, { target: { value: 'PADRE' } });
      expect(fila().get('cuantiaTarifa')?.value).toBe(28);
    });
  });

  describe('segunda escuela', () => {
    /** Banderas en tarifas propias: las peticiones de rol y grupo salen de ellas, no del código. */
    const conBanderas: Catalogo = {
      ...CATALOGO_SEGUNDA_ESCUELA,
      tarifas: CATALOGO_SEGUNDA_ESCUELA.tarifas.map((t) =>
        t.codigo === 'JUDO_MES' ? { ...t, requiereRolFamiliar: true } : { ...t, requiereGrupoFamiliar: true }
      ),
    };

    it('Judo: sus tarifas (la general y la propia) en orden, con su nombre', async () => {
      await crear(CATALOGO_SEGUNDA_ESCUELA);
      elegirDeporte('JUDO');
      expect(opcionesTarifa()).toEqual([
        { valor: 'BONO', texto: 'Bono' },
        { valor: 'JUDO_MES', texto: 'Judo mensual' },
      ]);
      elegirTarifa('JUDO_MES');
      expect(fila().get('cuantiaTarifa')?.value).toBe(35);
      expect(existe('#rolFamiliar-0')).toBeFalse();
      expect(existe('#grupoFamiliar-0')).toBeFalse();
    });

    it('rol y grupo según las banderas de la tarifa', async () => {
      await crear(conBanderas);
      elegirDeporte('JUDO');
      elegirTarifa('JUDO_MES');
      expect(existe('#rolFamiliar-0')).toBeTrue();
      expect(existe('#grupoFamiliar-0')).toBeFalse();
      elegirRol('PADRE');
      expect(fila().get('cuantiaTarifa')?.value).toBe(40);

      elegirTarifa('BONO');
      expect(existe('#grupoFamiliar-0')).toBeTrue();
      expect(existe('#rolFamiliar-0')).toBeFalse();
      expect(fila().get('cuantiaTarifa')?.value).toBe(10);
    });
  });
});
