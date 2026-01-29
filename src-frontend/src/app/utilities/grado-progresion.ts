import { esMenor } from './categoria-por-edad';

type MapaProgresion = Record<string, string>;

const MAPA_TAEKWONDO_MENORES: MapaProgresion = {
  BLANCO: 'BLANCO_AMARILLO',
  BLANCO_AMARILLO: 'AMARILLO',
  AMARILLO: 'AMARILLO_NARANJA',
  AMARILLO_NARANJA: 'NARANJA',
  NARANJA: 'NARANJA_VERDE',
  NARANJA_VERDE: 'VERDE',
  VERDE: 'VERDE_AZUL',
  VERDE_AZUL: 'AZUL',
  AZUL: 'AZUL_ROJO',
  AZUL_ROJO: 'ROJO',
  ROJO: 'ROJO_NEGRO_1_PUM',
  ROJO_NEGRO_1_PUM: 'ROJO_NEGRO_2_PUM',
  ROJO_NEGRO_2_PUM: 'ROJO_NEGRO_3_PUM',
};

const MAPA_TAEKWONDO_MAYORES: MapaProgresion = {
  BLANCO: 'AMARILLO',
  BLANCO_AMARILLO: 'AMARILLO',
  AMARILLO: 'NARANJA',
  AMARILLO_NARANJA: 'NARANJA',
  NARANJA: 'VERDE',
  NARANJA_VERDE: 'VERDE',
  VERDE: 'AZUL',
  VERDE_AZUL: 'AZUL',
  AZUL: 'ROJO',
  AZUL_ROJO: 'ROJO',
  ROJO: 'NEGRO_1_DAN',
  NEGRO_1_DAN: 'NEGRO_2_DAN',
  NEGRO_2_DAN: 'NEGRO_3_DAN',
  NEGRO_3_DAN: 'NEGRO_4_DAN',
  NEGRO_4_DAN: 'NEGRO_5_DAN',
};

const MAPA_KICKBOXING: MapaProgresion = {
  BLANCO: 'AMARILLO',
  BLANCO_AMARILLO: 'AMARILLO',
  AMARILLO: 'NARANJA',
  AMARILLO_NARANJA: 'NARANJA',
  NARANJA: 'VERDE',
  NARANJA_VERDE: 'VERDE',
  VERDE: 'AZUL',
  VERDE_AZUL: 'AZUL',
  AZUL: 'ROJO',
  AZUL_ROJO: 'ROJO',
  ROJO: 'NEGRO_1_DAN',
  NEGRO_1_DAN: 'NEGRO_2_DAN',
  NEGRO_2_DAN: 'NEGRO_3_DAN',
  NEGRO_3_DAN: 'NEGRO_4_DAN',
  NEGRO_4_DAN: 'NEGRO_5_DAN',
};

export function obtenerSiguienteGrado(
  deporte: string | null | undefined,
  gradoActual: string | null | undefined,
  fechaNacimiento: string | Date | null | undefined
): string | null {
  if (!deporte || !gradoActual) {
    return null;
  }

  const deporteKey = deporte.toUpperCase();

  if (deporteKey === 'KICKBOXING') {
    return MAPA_KICKBOXING[gradoActual] ?? null;
  }

  if (deporteKey !== 'TAEKWONDO') {
    return null;
  }

  const esMenorAlumno = esMenor(fechaNacimiento, deporteKey);
  const mapa = esMenorAlumno ? MAPA_TAEKWONDO_MENORES : MAPA_TAEKWONDO_MAYORES;
  return mapa[gradoActual] ?? null;
}

export function esSiguienteGradoRojo(
  deporte: string | null | undefined,
  gradoActual: string | null | undefined,
  fechaNacimiento: string | Date | null | undefined
): boolean {
  return obtenerSiguienteGrado(deporte, gradoActual, fechaNacimiento) === 'ROJO';
}
