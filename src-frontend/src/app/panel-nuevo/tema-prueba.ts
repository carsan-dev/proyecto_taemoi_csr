import { TemaAplicado } from './tema-escuela';

/**
 * Solo para los specs: el tema y el acento de dos escuelas tal como los deja `MarcaEscuelaPanelService.aplicarEn`
 * (tema de la API, acento de `acentoDe`). Moiskimdo con el color de su logo y una escuela ficticia verde.
 */
export const MOISKIMDO_PRUEBA: TemaAplicado = {
  tema: {
    primario: '#1B2B2E',
    sobrePrimario: '#FFFFFF',
    sobrePrimario2: '#C6CACB',
    primarioTinta: '#1B2B2E',
    primarioSuave: '#E8EAEA',
    ajustado: false,
  },
  acento: { acento: '#01707F', acentoSuave: '#D5F5FC', acentoBorde: '#4595A3' },
};

export const SIERRA_PRUEBA: TemaAplicado = {
  tema: {
    primario: '#3E9B4F',
    sobrePrimario: '#1A2230',
    sobrePrimario2: '#1A2230',
    primarioTinta: '#24853A',
    primarioSuave: '#ECF5ED',
    ajustado: false,
  },
  acento: { acento: '#0E782D', acentoSuave: '#E0F5E1', acentoBorde: '#67956B' },
};
