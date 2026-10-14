// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { EditarAlumnoComponent } from './editar-alumno.component';
import { TipoTarifa } from '../../../enums/tipo-tarifa';

/** Instancia sin constructor ni TestBed; solo se simula el formulario que lee el método. */
function crear(rolFamiliar: string | null) {
  const c = Object.create(EditarAlumnoComponent.prototype);
  c.alumnoForm = { get: (campo: string) => (campo === 'rolFamiliar' ? { value: rolFamiliar } : null) };
  return c as EditarAlumnoComponent;
}

describe('editar-alumno asignarCuantiaTarifa (caracterización)', () => {
  const fijas: [TipoTarifa, number][] = [
    [TipoTarifa.ADULTO, 30],
    [TipoTarifa.ADULTO_GRUPO, 20],
    [TipoTarifa.FAMILIAR, 0],
    [TipoTarifa.INFANTIL, 28],
    [TipoTarifa.INFANTIL_GRUPO, 20],
    [TipoTarifa.HERMANOS, 26],
    [TipoTarifa.KICKBOXING, 30],
  ];
  fijas.forEach(([tarifa, importe]) => {
    it(`${tarifa} -> ${importe}`, () => {
      expect(crear('NINGUNO').asignarCuantiaTarifa(tarifa)).toBe(importe);
    });
  });

  const roles: [string | null, number][] = [
    ['PADRE', 28], ['MADRE', 28], ['HIJO', 26], ['HIJA', 26], ['NINGUNO', 0], [null, 0],
  ];
  roles.forEach(([rol, importe]) => {
    it(`PADRES_HIJOS con rol ${rol} -> ${importe}`, () => {
      expect(crear(rol).asignarCuantiaTarifa(TipoTarifa.PADRES_HIJOS)).toBe(importe);
    });
  });

  [TipoTarifa.PILATES, TipoTarifa.DEFENSA_PERSONAL_FEMENINA].forEach(tarifa => {
    it(`${tarifa} lanza error actualmente`, () => {
      expect(() => crear('NINGUNO').asignarCuantiaTarifa(tarifa)).toThrowError('Tipo de descuento no válido');
    });
  });

  it('tarifa desconocida lanza error', () => {
    expect(() => crear('NINGUNO').asignarCuantiaTarifa('X' as TipoTarifa)).toThrowError('Tipo de descuento no válido');
  });
});
