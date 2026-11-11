import { edadTemporada, errorFecha, inicioTemporada, salidasParaEdad } from '../comun/preinscripcion/preinscripcion';
import { PLAZAS, grupoDePrueba, sedeDePrueba, sitioConSedes, turnoDePrueba } from '../datos/datos-de-prueba';
import { SitioWeb } from '../datos/modelos';
import { EjemploPreinscripcion, REFERENCIA_DE_EJEMPLO, simuladorDe, simuladorDeLaVista } from './casos-vista';

/** Sábado 7 de noviembre de 2026, 12:00. */
const AHORA = new Date(2026, 10, 7, 12, 0);

/** Herrera sin grupos; Estepa con Peques lleno a las 17:00 e Infantil con plaza a las 18:00 y teléfono. */
function sitio(): SitioWeb {
  const s = sitioConSedes(3);
  s.sedes[0] = sedeDePrueba(1, 'Herrera', { grupos: [] });
  s.sedes[1] = sedeDePrueba(2, 'Estepa', {
    telefono: '600000002',
    grupos: [
      grupoDePrueba('Peques', [turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.lleno)], { id: 70 }),
      grupoDePrueba('Infantil', [turnoDePrueba('Martes', '18:00', '19:00'), turnoDePrueba('Jueves', '18:00', '19:00')], {
        id: 71,
        rangoEdadMin: 7,
        rangoEdadMax: 11,
      }),
    ],
  });
  return s;
}

const ejemplo = (s: SitioWeb, caso: Parameters<typeof simuladorDeLaVista>[0], paso: Parameters<typeof simuladorDeLaVista>[1]) => {
  const simulador = simuladorDeLaVista(caso, paso)!;
  return simulador.preinscripcion(simulador.sitio(s), AHORA);
};

const edad = (s: SitioWeb, e: EjemploPreinscripcion) =>
  edadTemporada({ dia: e.datos.dia ?? '', mes: e.datos.mes ?? '', anio: e.datos.anio ?? '' }, inicioTemporada(s.temporada), AHORA);

/**
 * W12c-ter: el paso y los casos de la preinscripción que simula la capa de edición (boceto `preinscripcionHtml` y
 * `CASOS.preinscripcion`): datos de ejemplo en memoria sacados de los de la escuela, sin almacenamiento ni envío.
 */
describe('ejemplo de la preinscripción en el editor (W12c-ter)', () => {
  it('sin caso ni paso, los datos reales (null); un caso sin paso no toca la preinscripción', () => {
    expect(simuladorDeLaVista(null, null)).toBeNull();
    expect(ejemplo(sitio(), 'lleno', null)).toBeNull();
    const solo = simuladorDeLaVista(null, 1)!;
    const s = sitio();
    expect(solo.sitio(s)).toBe(s);
  });

  it('paso 1: sin sede elegida ni nada escrito', () => {
    expect(ejemplo(sitio(), null, 1)).toEqual({ paso: 1, consulta: {}, datos: {}, respuesta: null });
  });

  it('paso 2: la primera sede con grupos (Herrera no tiene) y sin fecha, como el boceto', () => {
    expect(ejemplo(sitio(), null, 2)).toEqual({ paso: 2, consulta: { sede: 'estepa' }, datos: {}, respuesta: null });
  });

  it('«Sin grupo para su edad» (paso 2): 15 años, como el boceto, si su sede no tiene grupo de esa edad', () => {
    const s = sitio();
    const e = ejemplo(s, 'sin-grupo', 2)!;
    expect(e.paso).toBe(2);
    expect(e.consulta).toEqual({ sede: 'estepa' });
    expect(edad(s, e)).toBe(15);
    expect(salidasParaEdad(s.sedes[1], 15)).toEqual([]);
  });

  it('«Sin grupo para su edad» con grupo de 15 años: la edad justo por debajo de la menor de la sede', () => {
    const s = sitioConSedes(2);
    const e = ejemplo(s, 'sin-grupo', 2)!;
    expect(edad(s, e)).toBe(3);
    expect(salidasParaEdad(s.sedes[0], 3)).toEqual([]);
  });

  it('paso 3: el primer grupo con plaza de la sede, sus días y una fecha válida de su edad', () => {
    const s = sitio();
    const e = ejemplo(s, null, 3)!;
    expect(e.paso).toBe(3);
    expect(e.consulta).toEqual({ sede: 'estepa', grupo: '71', dias: '2,4' });
    expect(errorFecha({ dia: e.datos.dia!, mes: e.datos.mes!, anio: e.datos.anio! }, AHORA)).toBeNull();
    expect(edad(s, e)).toBe(7);
    expect(e.datos.telefono).toBeUndefined();
    expect(e.respuesta).toBeNull();
  });

  it('«Grupo completo» (paso 3): sin plaza en ningún grupo, el primero, con todos sus turnos en lista de espera', () => {
    const s = sitio();
    const simulador = simuladorDeLaVista('espera', 3)!;
    const visto = simulador.sitio(s);
    expect(visto.sedes[1].grupos.flatMap((g) => g.turnos).every((t) => t.plazas.estado === 'LISTA_ESPERA')).toBeTrue();
    const e = simulador.preinscripcion(visto, AHORA)!;
    expect(e.consulta).toEqual({ sede: 'estepa', grupo: '70', dias: '1' });
    expect(edad(s, e)).toBe(4);
  });

  it('paso 4: enviada con plaza, la referencia del boceto y el solicitante de ejemplo (nunca el teléfono de la sede)', () => {
    const e = ejemplo(sitio(), null, 4)!;
    expect(e.paso).toBe(4);
    expect(e.consulta).toEqual({ sede: 'estepa', grupo: '71', dias: '2,4' });
    expect(e.datos.telefono).toBe('600 000 000');
    expect(e.datos).toEqual(jasmine.objectContaining({ nombre: 'Lucía', apellidos: 'García Ruiz', tutorNombre: 'Ana Ruiz Pérez', email: 'ana@ejemplo.es' }));
    expect(e.datos.telefono).not.toBe('600000002');
    expect(e.respuesta).toEqual({ referencia: REFERENCIA_DE_EJEMPLO, tokenDocumento: '', plazaAsignada: true, posicionListaEspera: null });
    expect(REFERENCIA_DE_EJEMPLO).toBe('TSS-2026-0142');
  });

  it('«En la lista de espera» (paso 4): sin plaza y en la posición 3, como el boceto', () => {
    const e = ejemplo(sitio(), 'fin-espera', 4)!;
    expect(e.respuesta).toEqual({ referencia: 'TSS-2026-0142', tokenDocumento: '', plazaAsignada: false, posicionListaEspera: 3 });
  });

  it('sin ninguna sede con grupos, el paso 1 (el tema decide lo que enseña)', () => {
    const s = sitioConSedes(2, { sedes: [sedeDePrueba(1, 'Herrera', { grupos: [] })] });
    expect(ejemplo(s, null, 3)).toEqual({ paso: 1, consulta: {}, datos: {}, respuesta: null });
  });

  it('no toca el sitio de entrada, ni el almacenamiento del navegador', () => {
    const setItem = spyOn(Storage.prototype, 'setItem').and.callThrough();
    const removeItem = spyOn(Storage.prototype, 'removeItem').and.callThrough();
    const s = sitio();
    const antes = JSON.stringify(s);
    for (const [caso, paso] of [[null, 1], [null, 2], ['sin-grupo', 2], [null, 3], ['espera', 3], [null, 4], ['fin-espera', 4]] as const) {
      ejemplo(s, caso, paso);
    }
    expect(JSON.stringify(s)).toBe(antes);
    expect(setItem).not.toHaveBeenCalled();
    expect(removeItem).not.toHaveBeenCalled();
  });
});

describe('«Sin clases esta semana» de Una sede (W12c-ter)', () => {
  const vista = () => ({
    nombre: 'Estepa',
    horarios: { rotulo: 'Curso', titulo: 'Grupos', proxima: { rotulo: 'Próxima clase', hoy: false, cuando: 'Lunes', hora: '17:00', grupo: 'Peques' }, salidas: [{ hora: '17:00' }], leyenda: { lleno: 'Lleno' } },
  });

  it('quita la próxima clase y mantiene las salidas y lo demás (boceto: sin_clases en su sitio)', () => {
    const v = vista();
    const visto = simuladorDe('sin-clases').vistaSede(v);
    expect(visto.horarios.proxima).toBeNull();
    expect(visto.horarios.salidas).toEqual(v.horarios.salidas);
    expect({ ...visto, horarios: { ...visto.horarios, proxima: v.horarios.proxima } }).toEqual(v);
    expect(v.horarios.proxima).not.toBeNull();
  });

  it('los demás casos no la tocan, y este no toca el sitio', () => {
    const v = vista();
    expect(simuladorDe('lleno').vistaSede(v)).toBe(v);
    const s = sitio();
    expect(simuladorDe('sin-clases').sitio(s)).toBe(s);
    expect(simuladorDe('sin-clases').preinscripcion(s, AHORA)).toBeNull();
  });
});
