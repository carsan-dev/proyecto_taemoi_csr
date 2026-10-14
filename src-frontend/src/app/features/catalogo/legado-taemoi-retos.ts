/**
 * Retos diarios por disciplina de Taemoi (vista de usuario). Excepción de Taemoi como `legado-taemoi.ts`, pero en un
 * archivo aparte: son ~80 líneas de texto que solo necesita la vista de usuario (carga diferida) y no deben
 * engordar el paquete inicial, que sí importa `legado-taemoi.ts`.
 */

// ---------------------------------------------------------------------------------------------
// Retos diarios por disciplina (vista de usuario)
// ---------------------------------------------------------------------------------------------

/** Retos diarios que Taemoi escribió para cada una de sus disciplinas (contenido, no dato del catálogo). */
const RETOS_DIARIOS_LEGADO: Readonly<Record<string, readonly string[]>> = {
  TAEKWONDO: [
    'Ap chagi: 4 series de 12 repeticiones por pierna',
    'Yop chagi: 4 series de 10 repeticiones por pierna',
    'Dollyo chagi: 3 series de 12 repeticiones por pierna',
    'Paso adelante + ap chagi: 3 series de 8 repeticiones por lado',
    'Paso atrás + contraataque: 3 series de 8 repeticiones por lado',
    'Guardia de taekwondo con rebote suave: 5 minutos',
    'Rodilla arriba + extensión controlada: 3 series de 10 repeticiones por pierna',
    'Equilibrio en pierna de apoyo: 4 series de 20 segundos por lado',
    'Combinación ap chagi + dollyo chagi: 3 series de 8 repeticiones por lado',
    'Bloqueos medios + altos: 3 series de 10 repeticiones por lado',
    'Desplazamientos de combate con cambios de dirección: 2 series de 90 segundos',
    'Elevaciones de rodilla explosiva: 3 series de 12 repeticiones por lado',
    'Giro de cadera y pivote para técnica circular: 3 series de 8 repeticiones por lado',
    'Trabajo de distancia sin impacto: 4 series de 30 segundos',
    'Chagi a altura media con control: 3 series de 10 repeticiones por pierna',
    'Sombra técnica de taekwondo: 2 series de 2 minutos',
    'Combinación de 1-2 patadas: 3 series de 6 repeticiones por lado',
    'Saltos cortos con pies juntos: 3 series de 12 repeticiones',
    'Cámara de patada mantenida 2 segundos: 3 series de 10 repeticiones por lado',
    'Movilidad específica de cadera para patada: 4 minutos',
    'Colocarte el cinturón: 1 repetición',
    'Combinación de patadas sin bajar la pierna: 3 repeticiones',
    'Apertura de piernas: 3 series de 30 segundos',
    'Piernas juntas, tocar el suelo con las palmas sin doblar rodillas: 3 series de 12 repeticiones'
  ],
  KICKBOXING: [
    'Jab en sombra con técnica limpia: 4 series de 20 segundos',
    'Jab-cross en sombra: 4 series de 20 segundos',
    'Low kick técnica: 3 series de 15 repeticiones por pierna',
    'Front kick: 3 series de 12 repeticiones por pierna',
    'Teep + paso atrás: 3 series de 10 repeticiones por lado',
    'Sombra con guardia alta: 2 series de 2 minutos',
    'Desplazamiento lateral en guardia: 4 series de 30 segundos',
    'Slip izquierda + slip derecha: 3 series de 10 repeticiones',
    'Bloqueo de low kick: 3 series de 10 repeticiones por lado',
    'Rodillazos al aire: 4 series de 12 repeticiones por lado',
    'Combinación jab-cross-low kick: 3 series de 8 repeticiones por lado',
    'Combinación jab-cross-hook: 3 series de 8 repeticiones por lado',
    'Defensa activa sin bajar manos: 2 series de 90 segundos',
    'Uppercut al aire: 3 series de 12 repeticiones por lado',
    'Paso adelante + 1-2: 3 series de 10 repeticiones por lado',
    'Paso atrás + contra 1-2: 3 series de 10 repeticiones por lado',
    'Sombra a ritmo alto: 4 series de 20 segundos',
    'Chequeo de distancia con jab: 3 series de 10 repeticiones',
    'Condición física específica (cuerda o rebote): 2 series de 2 minutos',
    'Sentadilla + low kick técnica: 3 series de 12 repeticiones por lado',
  ],
  PILATES: [
    'Hundred modificado con control respiratorio: 3 series de 10 repeticiones',
    'Puente articulado de columna: 3 series de 12 repeticiones',
    'Roll down segmentado: 3 series de 10 repeticiones',
    'Dead bug controlado: 3 series de 10 repeticiones por lado',
    'Clam shell: 3 series de 12 repeticiones por lado',
    'Side kick en decúbito lateral: 3 series de 10 repeticiones por lado',
    'Swimming en colchoneta: 3 series de 8 repeticiones',
    'Cat-cow lento con respiración: 3 series de 10 repeticiones',
    'Movilización de columna torácica: 3 series de 10 repeticiones',
    'Single leg stretch: 3 series de 10 repeticiones por lado',
    'Double leg stretch controlado: 3 series de 8 repeticiones',
    'Plancha de antebrazos con alineación: 2 series de 60 segundos',
    'Elevación de pelvis unilateral: 3 series de 10 repeticiones por lado',
    'Apertura de cadera en cuadrupedia: 3 series de 10 repeticiones por lado',
    'Respiración costal lateral: 5 minutos',
    'Elevaciones escapulares tumbado: 3 series de 12 repeticiones',
    'Articulación de hombros sin dolor: 3 series de 10 repeticiones',
    'Equilibrio en un pie con centro activo: 3 series de 8 repeticiones por lado',
    'Movilidad suave de columna y cadera: 4 minutos',
    'Control del core en posición neutra: 3 series de 10 repeticiones',
  ],
  DEFENSA_PERSONAL_FEMENINA: [
    'Postura base + manos preparadas: 3 series de 10 repeticiones',
    'Desplazamiento lateral y salida de línea: 3 series de 12 repeticiones',
    'Palm strike al aire: 3 series de 10 repeticiones por lado',
    'Golpe de codo corto: 3 series de 10 repeticiones por lado',
    'Rodillazo frontal: 3 series de 10 repeticiones por lado',
    'Secuencia voz fuerte + paso atrás + guardia: 3 series de 8 repeticiones',
    'Práctica de distancia de seguridad: 2 series de 2 minutos',
    'Liberación de agarre de muñeca: 3 series de 8 repeticiones por lado',
    'Salida de agarre de ropa: 3 series de 8 repeticiones por lado',
    'Empuje y escape a espacio seguro: 3 series de 10 repeticiones',
    'Giro de cadera para salir de agarre: 3 series de 8 repeticiones por lado',
    'Combinación palm strike + escape: 3 series de 10 repeticiones',
    'Combinación rodillazo + salida lateral: 3 series de 10 repeticiones',
    'Simulación de reacción rápida: 2 series de 90 segundos',
    'Trabajo de base y equilibrio: 3 series de 12 repeticiones',
    'Defensa ante agarre frontal sin fuerza bruta: 3 series de 8 repeticiones',
    'Defensa ante aproximación con antebrazos: 3 series de 8 repeticiones',
    'Técnica corta de choque y separación: 3 series de 10 repeticiones',
    'Respiración para bajar estrés post-esfuerzo: 4 minutos',
    'Secuencia simple de bloqueo, golpe y salida: 3 series de 8 repeticiones',
  ],
};

/** Retos propios de la disciplina: los de Taemoi por código con el flag; para el resto, ninguno (solo retos generales). */
export function retosDiariosDisciplina(legado: boolean, codigoDisciplina: string | null | undefined): readonly string[] {
  return legado && codigoDisciplina && Object.hasOwn(RETOS_DIARIOS_LEGADO, codigoDisciplina)
    ? RETOS_DIARIOS_LEGADO[codigoDisciplina]
    : [];
}

