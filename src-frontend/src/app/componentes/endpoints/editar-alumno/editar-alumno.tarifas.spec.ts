import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';

import { EditarAlumnoComponent } from './editar-alumno.component';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { Catalogo } from '../../../features/catalogo/catalogo.models';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';

describe('EditarAlumnoComponent: tarifas del catálogo', () => {
  /** Cada llamada usa un módulo de pruebas nuevo (varias instancias por prueba). */
  function crear(catalogo: Catalogo, deportes: Partial<AlumnoDeporteDTO>[] = []): EditarAlumnoComponent {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(catalogo)] });
    const component = TestBed.runInInjectionContext(
      () => new EditarAlumnoComponent({} as any, {} as any, new FormBuilder(), {} as any, {} as any, {} as any, {} as any)
    );
    component.deportesDelAlumno = deportes as AlumnoDeporteDTO[];
    return component;
  }

  const opciones = (c: EditarAlumnoComponent, deporte: string) =>
    c.tarifasParaDeporte(deporte).map((t) => ({ valor: t.codigo, texto: t.nombre }));

  describe('D7/D8 lista por disciplina', () => {
    it('Taekwondo: sus 7 tarifas en orden del catálogo (ahora también INFANTIL_GRUPO y FAMILIAR), con nombre', () => {
      const c = crear(CATALOGO_TAEMOI, [{ deporte: 'TAEKWONDO', tipoTarifa: 'ADULTO' }]);
      expect(opciones(c, 'TAEKWONDO')).toEqual([
        { valor: 'ADULTO', texto: 'Adulto' },
        { valor: 'ADULTO_GRUPO', texto: 'Adulto Grupo' },
        { valor: 'FAMILIAR', texto: 'Familiar' },
        { valor: 'INFANTIL', texto: 'Infantil' },
        { valor: 'INFANTIL_GRUPO', texto: 'Infantil Grupo' },
        { valor: 'HERMANOS', texto: 'Hermanos' },
        { valor: 'PADRES_HIJOS', texto: 'Padres e Hijos' },
      ]);
    });

    it('Kickboxing, Pilates y Defensa Personal Femenina: solo su tarifa (ya no las de otras disciplinas)', () => {
      const c = crear(CATALOGO_TAEMOI);
      expect(opciones(c, 'KICKBOXING')).toEqual([{ valor: 'KICKBOXING', texto: 'Kickboxing' }]);
      expect(opciones(c, 'PILATES')).toEqual([{ valor: 'PILATES', texto: 'Pilates' }]);
      expect(opciones(c, 'DEFENSA_PERSONAL_FEMENINA')).toEqual([
        { valor: 'DEFENSA_PERSONAL_FEMENINA', texto: 'Defensa Personal Femenina' },
      ]);
    });

    it('la tarifa que ya tiene el alumno se sigue mostrando aunque la disciplina no la admita', () => {
      const c = crear(CATALOGO_TAEMOI, [{ deporte: 'KICKBOXING', tipoTarifa: 'ADULTO' }]);
      expect(opciones(c, 'KICKBOXING')).toEqual([
        { valor: 'KICKBOXING', texto: 'Kickboxing' },
        { valor: 'ADULTO', texto: 'Adulto' },
      ]);
      const desconocida = crear(CATALOGO_TAEMOI, [{ deporte: 'PILATES', tipoTarifa: 'ANTIGUA' }]);
      expect(opciones(desconocida, 'PILATES')).toEqual([
        { valor: 'PILATES', texto: 'Pilates' },
        { valor: 'ANTIGUA', texto: 'ANTIGUA' },
      ]);
    });

    it('segunda escuela: Judo ofrece la general y la propia', () => {
      const c = crear(CATALOGO_SEGUNDA_ESCUELA);
      expect(opciones(c, 'JUDO')).toEqual([
        { valor: 'BONO', texto: 'Bono' },
        { valor: 'JUDO_MES', texto: 'Judo mensual' },
      ]);
    });

    it('sin tarifas en el catálogo (no gestor o fallo de carga): lista vacía', () => {
      const c = crear({ ...CATALOGO_TAEMOI, tarifas: [] });
      expect(opciones(c, 'TAEKWONDO')).toEqual([]);
    });
  });

  describe('importes y banderas', () => {
    it('importe por defecto al cambiar de tarifa, igual que antes', () => {
      const esperados: [string, number][] = [
        ['ADULTO', 30], ['ADULTO_GRUPO', 20], ['FAMILIAR', 0], ['INFANTIL', 28], ['INFANTIL_GRUPO', 20],
        ['HERMANOS', 26], ['PADRES_HIJOS', 0], ['KICKBOXING', 30], ['PILATES', 30], ['DEFENSA_PERSONAL_FEMENINA', 25],
      ];
      esperados.forEach(([codigo, importe]) => {
        const c = crear(CATALOGO_TAEMOI, [{ deporte: 'TAEKWONDO', tipoTarifa: 'X', rolFamiliar: 'NINGUNO' }]);
        c.onTipoTarifaModelChange('TAEKWONDO', codigo);
        expect(c.pendingTarifaChanges.get('TAEKWONDO')?.cuantiaTarifa).withContext(codigo).toBe(importe);
      });
    });

    it('PADRES_HIJOS con el rol guardado del alumno: 28 padre/madre, 26 hijo/hija', () => {
      const c = crear(CATALOGO_TAEMOI, [{ deporte: 'TAEKWONDO', tipoTarifa: 'ADULTO', rolFamiliar: 'MADRE' }]);
      c.onTipoTarifaModelChange('TAEKWONDO', 'PADRES_HIJOS');
      expect(c.pendingTarifaChanges.get('TAEKWONDO')?.cuantiaTarifa).toBe(28);
    });

    it('el rol recalcula el importe solo si la tarifa pide rol', () => {
      const c = crear(CATALOGO_TAEMOI, [{ deporte: 'TAEKWONDO', tipoTarifa: 'PADRES_HIJOS', cuantiaTarifa: 0 }]);
      c.onRolFamiliarModelChange('TAEKWONDO', 'HIJA');
      expect(c.pendingTarifaChanges.get('TAEKWONDO')?.cuantiaTarifa).toBe(26);
      c.onRolFamiliarModelChange('TAEKWONDO', 'PADRE');
      expect(c.pendingTarifaChanges.get('TAEKWONDO')?.cuantiaTarifa).toBe(28);

      const otra = crear(CATALOGO_TAEMOI, [{ deporte: 'TAEKWONDO', tipoTarifa: 'HERMANOS', cuantiaTarifa: 26 }]);
      otra.onRolFamiliarModelChange('TAEKWONDO', 'PADRE');
      expect(otra.pendingTarifaChanges.get('TAEKWONDO')?.cuantiaTarifa).toBeUndefined();
    });

    it('rol y grupo según las banderas de la tarifa mostrada (pendiente o guardada)', () => {
      const c = crear(CATALOGO_TAEMOI, [{ deporte: 'TAEKWONDO', tipoTarifa: 'PADRES_HIJOS' }]);
      expect(c.requiereRolFamiliar('TAEKWONDO')).toBeTrue();
      expect(c.requiereGrupoFamiliar('TAEKWONDO')).toBeFalse();
      c.onTipoTarifaModelChange('TAEKWONDO', 'HERMANOS');
      expect(c.requiereRolFamiliar('TAEKWONDO')).toBeFalse();
      expect(c.requiereGrupoFamiliar('TAEKWONDO')).toBeTrue();
      c.onTipoTarifaModelChange('TAEKWONDO', 'ADULTO');
      expect(c.requiereRolFamiliar('TAEKWONDO')).toBeFalse();
      expect(c.requiereGrupoFamiliar('TAEKWONDO')).toBeFalse();
    });

    it('segunda escuela: las banderas de sus tarifas, no los códigos de Taemoi', () => {
      const conBanderas: Catalogo = {
        ...CATALOGO_SEGUNDA_ESCUELA,
        tarifas: CATALOGO_SEGUNDA_ESCUELA.tarifas.map((t) => (t.codigo === 'JUDO_MES' ? { ...t, requiereRolFamiliar: true } : t)),
      };
      const c = crear(conBanderas, [{ deporte: 'JUDO', tipoTarifa: 'JUDO_MES', cuantiaTarifa: 35 }]);
      expect(c.requiereRolFamiliar('JUDO')).toBeTrue();
      c.onRolFamiliarModelChange('JUDO', 'PADRE');
      expect(c.pendingTarifaChanges.get('JUDO')?.cuantiaTarifa).toBe(40);
    });
  });

  it('D1: asignarCuantiaTarifa ya no existe', () => {
    expect((EditarAlumnoComponent.prototype as any).asignarCuantiaTarifa).toBeUndefined();
  });
});
