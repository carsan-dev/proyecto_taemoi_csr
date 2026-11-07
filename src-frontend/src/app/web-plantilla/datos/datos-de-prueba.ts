import { GrupoWeb, Legal, Plazas, SedeWeb, SitioWeb, TurnoWeb } from './modelos';

/** Datos de prueba de los specs de la web por plantilla (no los importa ningún código de la aplicación). */
export function sitioDePrueba(cambios: Partial<SitioWeb> = {}): SitioWeb {
  return {
    escuela: { nombre: 'Taekwondo Sierra Sur', colorPrimario: '#B3261E', logoVersion: 42 },
    tema: 'linea-comarcal',
    publicada: true,
    vistaPrevia: false,
    temporada: '2026-2027',
    bloques: [],
    sedes: [],
    tarifas: [],
    tarifasSede: [],
    licencias: [],
    examenDesde: null,
    proximosEventos: [],
    contacto: { email: 'info@example.com', telefono: '600000000' },
    ...cambios,
  };
}

export const LEGAL_DE_PRUEBA: Legal = {
  titular: {
    nombre: 'Taekwondo Sierra Sur',
    nombreLegal: 'Club Taekwondo Sierra Sur',
    cif: 'G00000000',
    direccion: 'Calle Mayor 1',
    localidad: 'Estepa',
    provincia: 'Sevilla',
    codigoPostal: '41560',
    email: 'info@example.com',
    telefono: '600000000',
  },
  normas: [],
  actualizadaEn: '2026-11-01',
};

export const PLAZAS = {
  quedan: (n: number): Plazas => ({ estado: 'QUEDAN', quedan: n }),
  lleno: { estado: 'LLENO', quedan: null } as Plazas,
  espera: { estado: 'LISTA_ESPERA', quedan: null } as Plazas,
};

let siguienteId = 1000;

export function turnoDePrueba(dia: string, inicio: string, fin: string, plazas: Plazas = PLAZAS.quedan(4)): TurnoWeb {
  return { id: siguienteId++, diaSemana: dia, horaInicio: inicio, horaFin: fin, plazas };
}

export function grupoDePrueba(nombre: string, turnos: TurnoWeb[], cambios: Partial<GrupoWeb> = {}): GrupoWeb {
  return {
    id: siguienteId++,
    nombre,
    disciplina: 'TAEKWONDO',
    disciplinaNombre: 'Taekwondo',
    rangoEdadMin: 4,
    rangoEdadMax: 6,
    plazas: turnos[0]?.plazas ?? null,
    turnos,
    ...cambios,
  };
}

export function sedeDePrueba(id: number, nombre: string, cambios: Partial<SedeWeb> = {}): SedeWeb {
  return {
    id,
    nombre,
    direccion: 'C/ del Deporte, 2',
    localidad: nombre,
    codigoPostal: '41560',
    telefono: null,
    slug: nombre.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '').replace(/\s+/g, '-'),
    lugar: 'Pabellón municipal',
    indicacionesAcceso: null,
    fotoUrl: null,
    foto2Url: null,
    grupos: [
      grupoDePrueba('Peques', [turnoDePrueba('Lunes', '17:00', '17:45'), turnoDePrueba('Miércoles', '17:00', '17:45')]),
      grupoDePrueba('Infantil', [turnoDePrueba('Martes', '18:00', '19:00')], { rangoEdadMin: 7, rangoEdadMax: 11 }),
      grupoDePrueba('Jóvenes y adultos', [turnoDePrueba('Jueves', '19:30', '21:00')], { rangoEdadMin: 12, rangoEdadMax: null }),
    ],
    ...cambios,
  };
}

/** Las seis sedes de Sierra Sur (datos inventados, como los bocetos). */
export const NOMBRES_SEDES = ['Herrera', 'Estepa', 'Casariche', 'Lora de Estepa', 'Roda de Andalucía', 'Mollina'];

/** Un sitio de prueba con `n` sedes (1 = Estepa sola), una tarifa de 25 € y sin eventos ni profesores. */
export function sitioConSedes(n: number, cambios: Partial<SitioWeb> = {}): SitioWeb {
  const nombres = n === 1 ? ['Estepa'] : NOMBRES_SEDES.slice(0, n);
  return sitioDePrueba({
    sedes: nombres.map((nombre, i) => sedeDePrueba(i + 1, nombre)),
    tarifas: [{ id: 1, codigo: 'GENERAL', nombre: 'Mensualidad', disciplina: null, importe: 25, importeAdulto: null, importeMenor: null }],
    ...cambios,
  });
}
