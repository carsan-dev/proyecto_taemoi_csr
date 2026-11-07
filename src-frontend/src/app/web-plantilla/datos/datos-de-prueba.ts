import { Legal, SitioWeb } from './modelos';

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
