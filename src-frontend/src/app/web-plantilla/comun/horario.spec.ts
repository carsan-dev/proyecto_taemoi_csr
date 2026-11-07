import { GrupoWeb, Plazas, SedeWeb, TurnoWeb } from '../datos/modelos';
import {
  diasEnTexto,
  edadDeInicio,
  edadesEnTexto,
  enlaceMapa,
  estadoPlazas,
  gruposPorSede,
  importeEnTexto,
  numeroDia,
  numeroEnLetras,
  peorPlazas,
  proximaClase,
  salidasDeSede,
  telefonoEnlace,
  telefonoVisible,
  textoPlazas,
} from './horario';

const quedan = (n: number): Plazas => ({ estado: 'QUEDAN', quedan: n });
const LLENO: Plazas = { estado: 'LLENO', quedan: null };
const ESPERA: Plazas = { estado: 'LISTA_ESPERA', quedan: null };
const TEXTOS = { lleno: 'Lleno', espera: 'Lista de espera' };

let siguienteId = 1;
function turno(dia: string, inicio: string, fin: string, plazas: Plazas = quedan(5)): TurnoWeb {
  return { id: siguienteId++, diaSemana: dia, horaInicio: inicio, horaFin: fin, plazas };
}

function grupo(nombre: string, turnos: TurnoWeb[], min: number | null = 4, max: number | null = 6): GrupoWeb {
  return {
    id: siguienteId++,
    nombre,
    disciplina: 'TAEKWONDO',
    disciplinaNombre: 'Taekwondo',
    rangoEdadMin: min,
    rangoEdadMax: max,
    plazas: peorPlazas(turnos.map((t) => t.plazas)),
    turnos,
  };
}

function sede(grupos: GrupoWeb[]): SedeWeb {
  return {
    id: 1,
    nombre: 'Estepa',
    direccion: null,
    localidad: null,
    codigoPostal: null,
    telefono: null,
    slug: 'estepa',
    lugar: null,
    indicacionesAcceso: null,
    fotoUrl: null,
    foto2Url: null,
    grupos,
  };
}

describe('horario · lógica común de la web por plantilla', () => {
  it('días: nombres con o sin tilde y en mayúsculas; texto en el orden de la semana', () => {
    expect(numeroDia('Lunes')).toBe(1);
    expect(numeroDia('Miércoles')).toBe(3);
    expect(numeroDia('MIERCOLES')).toBe(3);
    expect(numeroDia('sábado')).toBe(6);
    expect(numeroDia('Domingo')).toBe(0);
    expect(numeroDia('otro')).toBeNull();
    expect(diasEnTexto([3, 1])).toBe('lunes y miércoles');
    expect(diasEnTexto([5, 1, 3])).toBe('lunes, miércoles y viernes');
    expect(diasEnTexto([0])).toBe('domingo');
  });

  describe('plazas (§4.3): «Quedan N», «Lleno», «Lista de espera»; sin «Últimas N»', () => {
    it('bordes 0, 1, 2 y 3 y la lista de espera', () => {
      expect(textoPlazas(quedan(3), TEXTOS)).toBe('Quedan 3 plazas');
      expect(textoPlazas(quedan(2), TEXTOS)).toBe('Quedan 2 plazas');
      expect(textoPlazas(quedan(1), TEXTOS)).toBe('Queda 1 plaza');
      expect(textoPlazas(quedan(0), TEXTOS)).toBe('Lleno');
      expect(estadoPlazas(quedan(0))).toBe('lleno');
      expect(textoPlazas(LLENO, TEXTOS)).toBe('Lleno');
      expect(textoPlazas(ESPERA, TEXTOS)).toBe('Lista de espera');
      expect(textoPlazas(null, TEXTOS)).toBe('');
      expect(textoPlazas(LLENO, { lleno: 'Completo', espera: 'En espera' })).toBe('Completo');
    });

    it('el peor (§10 n.º 5): espera, luego lleno, luego el que menos libres tiene', () => {
      expect(peorPlazas([quedan(4), ESPERA, LLENO])).toEqual(ESPERA);
      expect(peorPlazas([quedan(4), LLENO])).toEqual(LLENO);
      expect(peorPlazas([quedan(4), quedan(1), quedan(2)])).toEqual(quedan(1));
      expect(peorPlazas([null, undefined])).toBeNull();
    });
  });

  it('salidas: un grupo con la misma hora junta sus días; otra hora, otra salida; por hora de inicio', () => {
    const s = sede([
      grupo('Infantil', [turno('Lunes', '18:00', '19:00', quedan(4)), turno('Miércoles', '18:00', '19:00', quedan(1))], 7, 11),
      grupo('Peques', [turno('Viernes', '17:00', '18:00'), turno('Martes', '16:00', '17:00', LLENO)]),
    ]);
    const salidas = salidasDeSede(s);
    expect(salidas.map((x) => `${x.grupo.nombre} ${x.inicio} ${x.dias.join(',')}`)).toEqual([
      'Peques 16:00 2',
      'Peques 17:00 5',
      'Infantil 18:00 1,3',
    ]);
    expect(salidas[0].plazas).toEqual(LLENO);
    expect(salidas[2].plazas).toEqual(quedan(1));
    expect(salidasDeSede(sede([grupo('Sin turnos', [])]))).toEqual([]);
  });

  it('edades: rango, desde, hasta o nada', () => {
    expect(edadesEnTexto(4, 6)).toBe('4 a 6 años');
    expect(edadesEnTexto(12, null)).toBe('Desde 12 años');
    expect(edadesEnTexto(null, 6)).toBe('Hasta 6 años');
    expect(edadesEnTexto(5, 5)).toBe('5 años');
    expect(edadesEnTexto(null, null)).toBeNull();
  });

  describe('próxima clase', () => {
    const s = sede([grupo('Peques', [turno('Lunes', '17:00', '18:00'), turno('Jueves', '19:30', '21:00')])]);

    it('hoy, si aún no ha empezado', () => {
      // Lunes 2 de noviembre de 2026, 16:00.
      expect(proximaClase(s, new Date(2026, 10, 2, 16, 0))).toEqual({ hoy: true, cuando: '', hora: '17:00' });
    });

    it('si la de hoy ya empezó, la siguiente; mañana o el día con mayúscula', () => {
      expect(proximaClase(s, new Date(2026, 10, 2, 17, 0))).toEqual({ hoy: false, cuando: 'Jueves', hora: '19:30' });
      expect(proximaClase(s, new Date(2026, 10, 4, 12, 0))).toEqual({ hoy: false, cuando: 'Mañana', hora: '19:30' });
    });

    it('la de dentro de una semana, el mismo día', () => {
      expect(proximaClase(s, new Date(2026, 10, 5, 22, 0))).toEqual({ hoy: false, cuando: 'Lunes', hora: '17:00' });
      const soloLunes = sede([grupo('Peques', [turno('Lunes', '17:00', '18:00')])]);
      expect(proximaClase(soloLunes, new Date(2026, 10, 2, 18, 0))).toEqual({ hoy: false, cuando: 'Lunes', hora: '17:00' });
    });

    it('sin turnos: null', () => {
      expect(proximaClase(sede([]), new Date(2026, 10, 2, 16, 0))).toBeNull();
    });
  });

  it('enlace a mapas con lo que haya', () => {
    expect(enlaceMapa(['Pabellón municipal', null, ' Estepa '])).toBe(
      'https://www.google.com/maps/search/?api=1&query=Pabell%C3%B3n%20municipal%2C%20Estepa',
    );
    expect(enlaceMapa([null, '  '])).toBeNull();
  });

  it('cifras: edad de inicio, grupos por sede, número en letras, importe y teléfono', () => {
    const a = sede([grupo('A', [], 7, 11), grupo('B', [], 4, 6), grupo('C', [], null, null)]);
    const b = sede([grupo('D', [], 12, null)]);
    expect(edadDeInicio([a, b])).toBe(4);
    expect(edadDeInicio([sede([])])).toBeNull();
    expect(gruposPorSede([a, b])).toEqual({ maximo: 3, iguales: false });
    expect(gruposPorSede([a, a])).toEqual({ maximo: 3, iguales: true });
    expect(gruposPorSede([sede([])])).toBeNull();
    expect(numeroEnLetras(1)).toBe('una');
    expect(numeroEnLetras(6)).toBe('seis');
    expect(numeroEnLetras(14)).toBe('14');
    expect(importeEnTexto(25)).toBe('25 €');
    expect(importeEnTexto(25.5)).toBe('25,50 €');
    expect(telefonoVisible('600000000')).toBe('600 000 000');
    expect(telefonoVisible('+34 600 000 000')).toBe('600 000 000');
    expect(telefonoVisible('955 12')).toBe('955 12');
    expect(telefonoEnlace('600 000 000')).toBe('tel:+34600000000');
    expect(telefonoEnlace('+34 600 000 000')).toBe('tel:+34600000000');
  });
});
