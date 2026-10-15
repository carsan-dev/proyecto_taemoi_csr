/**
 * Fixtures de `GET /api/catalogo` para las pruebas. Solo se importan desde specs.
 *
 * - `CATALOGO_TAEMOI`: lo que devuelve el backend para la organización 1 tras V23/V24/V28/V30
 *   (rol gestor, con tarifas). Grados: V23 (orden, aplica_a, requiere_examen) + V30 (nombre, colores);
 *   kickboxing: copia con DAN orden-3, PUM orden+5, intermedios/PUM `NINGUNO` y ROJO = «MARRÓN» #8B4513.
 *   Tarifas: V24 (importes) + V30 (orden). Categorías: V28 (rangos de TAEKWONDO). `codigo` de las
 *   categorías tal como lo deja V28 (ContraccionCatalogoMigracionIntegrationTest): nulo salvo CADETE y SENIOR.
 * - `CATALOGO_SEGUNDA_ESCUELA`: como `CatalogoControllerIntegrationTest.segundaEscuela_soloVeSuCatalogo`
 *   (JUDO activa con grados BLANCO_JUDO/MARRON_JUDO, YOGA inactiva, categorías IT-INF/IT-ABS, tarifas
 *   BONO general y JUDO_MES) y sin legado de Taemoi.
 */
import { AplicaGrado, Catalogo, CatalogoGrado, CatalogoPublico } from '../catalogo.models';

type FilaGrado = [codigo: string, nombre: string, ordenTkd: number, aplicaTkd: AplicaGrado, color1: string, color2: string | null];

const FILAS_GRADOS: FilaGrado[] = [
  ['BLANCO', 'BLANCO', 1, 'TODOS', '#FFFFFF', null],
  ['BLANCO_AMARILLO', 'BLANCO-AMARILLO', 2, 'MENORES', '#FFFFFF', '#FFFF00'],
  ['AMARILLO', 'AMARILLO', 3, 'TODOS', '#FFFF00', null],
  ['AMARILLO_NARANJA', 'AMARILLO-NARANJA', 4, 'MENORES', '#FFFF00', '#FFA500'],
  ['NARANJA', 'NARANJA', 5, 'TODOS', '#FFA500', null],
  ['NARANJA_VERDE', 'NARANJA-VERDE', 6, 'MENORES', '#FFA500', '#008000'],
  ['VERDE', 'VERDE', 7, 'TODOS', '#008000', null],
  ['VERDE_AZUL', 'VERDE-AZUL', 8, 'MENORES', '#008000', '#0000FF'],
  ['AZUL', 'AZUL', 9, 'TODOS', '#0000FF', null],
  ['AZUL_ROJO', 'AZUL-ROJO', 10, 'MENORES', '#0000FF', '#FF0000'],
  ['ROJO', 'ROJO', 11, 'TODOS', '#FF0000', null],
  ['ROJO_NEGRO_1_PUM', 'ROJO-NEGRO 1º PUM', 12, 'MENORES', '#FF0000', '#000000'],
  ['ROJO_NEGRO_2_PUM', 'ROJO-NEGRO 2º PUM', 13, 'MENORES', '#FF0000', '#000000'],
  ['ROJO_NEGRO_3_PUM', 'ROJO-NEGRO 3º PUM', 14, 'MENORES', '#FF0000', '#000000'],
  ['NEGRO_1_DAN', 'NEGRO 1º DAN', 15, 'ADULTOS', '#000000', null],
  ['NEGRO_2_DAN', 'NEGRO 2º DAN', 16, 'ADULTOS', '#000000', null],
  ['NEGRO_3_DAN', 'NEGRO 3º DAN', 17, 'ADULTOS', '#000000', null],
  ['NEGRO_4_DAN', 'NEGRO 4º DAN', 18, 'ADULTOS', '#000000', null],
  ['NEGRO_5_DAN', 'NEGRO 5º DAN', 19, 'ADULTOS', '#000000', null],
];

function gradosTaekwondo(): CatalogoGrado[] {
  return FILAS_GRADOS.map(([codigo, nombre, orden, aplicaA, color1, color2]) => ({
    codigo,
    nombre,
    orden,
    aplicaA,
    color1,
    color2,
    requiereExamen: codigo !== 'BLANCO',
  }));
}

function gradosKickboxing(): CatalogoGrado[] {
  return FILAS_GRADOS.map(([codigo, nombre, ordenTkd, aplicaTkd, color1, color2]) => {
    const esDan = codigo.endsWith('_DAN');
    const esPum = codigo.endsWith('_PUM');
    const esRojo = codigo === 'ROJO';
    return {
      codigo,
      nombre: esRojo ? 'MARRÓN' : nombre,
      orden: esDan ? ordenTkd - 3 : esPum ? ordenTkd + 5 : ordenTkd,
      aplicaA: (aplicaTkd === 'MENORES' ? 'NINGUNO' : 'TODOS') as AplicaGrado,
      color1: esRojo ? '#8B4513' : color1,
      color2: esRojo ? null : color2,
      requiereExamen: codigo !== 'BLANCO',
    };
  }).sort((a, b) => a.orden - b.orden);
}

export const CATALOGO_TAEMOI: Catalogo = {
  organizacion: { id: 1, comportamientoLegadoTaemoi: true },
  disciplinas: [
    { codigo: 'TAEKWONDO', nombre: 'Taekwondo', orden: 1, activa: true, edadAdulto: 14, permiteCompeticion: true, requiereLicencia: true, sistemaGradosId: 1, tieneMaterialExamen: true },
    { codigo: 'KICKBOXING', nombre: 'Kickboxing', orden: 2, activa: true, edadAdulto: 15, permiteCompeticion: true, requiereLicencia: true, sistemaGradosId: 2, tieneMaterialExamen: true },
    { codigo: 'PILATES', nombre: 'Pilates', orden: 3, activa: true, edadAdulto: 14, permiteCompeticion: false, requiereLicencia: false, sistemaGradosId: null, tieneMaterialExamen: false },
    { codigo: 'DEFENSA_PERSONAL_FEMENINA', nombre: 'Defensa Personal Femenina', orden: 4, activa: true, edadAdulto: 14, permiteCompeticion: false, requiereLicencia: false, sistemaGradosId: null, tieneMaterialExamen: false },
  ],
  sistemasGrados: [
    { id: 1, nombre: 'Taekwondo', grados: gradosTaekwondo() },
    { id: 2, nombre: 'Kickboxing', grados: gradosKickboxing() },
  ],
  categorias: [
    { codigo: null, nombre: 'Infantil', disciplina: 'TAEKWONDO', edadMin: null, edadMax: 9, orden: 1 },
    { codigo: null, nombre: 'Precadete', disciplina: 'TAEKWONDO', edadMin: 10, edadMax: 11, orden: 2 },
    { codigo: 'CADETE', nombre: 'Cadete', disciplina: 'TAEKWONDO', edadMin: 12, edadMax: 13, orden: 3 },
    { codigo: null, nombre: 'Junior', disciplina: 'TAEKWONDO', edadMin: 14, edadMax: 16, orden: 4 },
    { codigo: 'SENIOR', nombre: 'Senior', disciplina: 'TAEKWONDO', edadMin: 17, edadMax: null, orden: 5 },
  ],
  tarifas: [
    { codigo: 'ADULTO', nombre: 'Adulto', disciplina: 'TAEKWONDO', importe: 30, importeAdulto: null, importeMenor: null, orden: 1, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: false },
    { codigo: 'ADULTO_GRUPO', nombre: 'Adulto Grupo', disciplina: 'TAEKWONDO', importe: 20, importeAdulto: null, importeMenor: null, orden: 2, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: false },
    { codigo: 'FAMILIAR', nombre: 'Familiar', disciplina: 'TAEKWONDO', importe: 0, importeAdulto: null, importeMenor: null, orden: 3, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: false },
    { codigo: 'INFANTIL', nombre: 'Infantil', disciplina: 'TAEKWONDO', importe: 28, importeAdulto: null, importeMenor: null, orden: 4, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: false },
    { codigo: 'INFANTIL_GRUPO', nombre: 'Infantil Grupo', disciplina: 'TAEKWONDO', importe: 20, importeAdulto: null, importeMenor: null, orden: 5, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: false },
    { codigo: 'HERMANOS', nombre: 'Hermanos', disciplina: 'TAEKWONDO', importe: 26, importeAdulto: null, importeMenor: null, orden: 6, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: true },
    { codigo: 'PADRES_HIJOS', nombre: 'Padres e Hijos', disciplina: 'TAEKWONDO', importe: 0, importeAdulto: 28, importeMenor: 26, orden: 7, activa: true, requiereRolFamiliar: true, requiereGrupoFamiliar: false },
    { codigo: 'KICKBOXING', nombre: 'Kickboxing', disciplina: 'KICKBOXING', importe: 30, importeAdulto: null, importeMenor: null, orden: 8, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: false },
    { codigo: 'PILATES', nombre: 'Pilates', disciplina: 'PILATES', importe: 30, importeAdulto: null, importeMenor: null, orden: 9, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: false },
    { codigo: 'DEFENSA_PERSONAL_FEMENINA', nombre: 'Defensa Personal Femenina', disciplina: 'DEFENSA_PERSONAL_FEMENINA', importe: 25, importeAdulto: null, importeMenor: null, orden: 10, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: false },
  ],
};

export const CATALOGO_SEGUNDA_ESCUELA: Catalogo = {
  organizacion: { id: 2, comportamientoLegadoTaemoi: false },
  disciplinas: [
    { codigo: 'JUDO', nombre: 'Judo', orden: 1, activa: true, edadAdulto: 16, permiteCompeticion: true, requiereLicencia: false, sistemaGradosId: 7, tieneMaterialExamen: false },
    { codigo: 'YOGA', nombre: 'Yoga', orden: 2, activa: false, edadAdulto: null, permiteCompeticion: false, requiereLicencia: false, sistemaGradosId: null, tieneMaterialExamen: false },
  ],
  sistemasGrados: [
    {
      id: 7,
      nombre: 'Judo',
      grados: [
        { codigo: 'BLANCO_JUDO', nombre: 'Blanco judo', orden: 1, aplicaA: 'TODOS', color1: '#FAFAFA', color2: null, requiereExamen: false },
        { codigo: 'MARRON_JUDO', nombre: 'Marrón judo', orden: 2, aplicaA: 'TODOS', color1: '#8B4513', color2: '#FAFAFA', requiereExamen: true },
      ],
    },
  ],
  categorias: [
    { codigo: 'IT-INF', nombre: 'Infantil judo', disciplina: 'JUDO', edadMin: null, edadMax: 12, orden: 1 },
    { codigo: 'IT-ABS', nombre: 'Absoluta judo', disciplina: 'JUDO', edadMin: 18, edadMax: null, orden: 2 },
  ],
  tarifas: [
    { codigo: 'BONO', nombre: 'Bono', disciplina: null, importe: 10, importeAdulto: null, importeMenor: null, orden: 1, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: false },
    { codigo: 'JUDO_MES', nombre: 'Judo mensual', disciplina: 'JUDO', importe: 35, importeAdulto: 40, importeMenor: 30, orden: 2, activa: true, requiereRolFamiliar: false, requiereGrupoFamiliar: false },
  ],
};

export const CATALOGO_PUBLICO_TAEMOI: CatalogoPublico = {
  disciplinas: [
    { codigo: 'TAEKWONDO', nombre: 'Taekwondo', orden: 1 },
    { codigo: 'KICKBOXING', nombre: 'Kickboxing', orden: 2 },
    { codigo: 'PILATES', nombre: 'Pilates', orden: 3 },
    { codigo: 'DEFENSA_PERSONAL_FEMENINA', nombre: 'Defensa Personal Femenina', orden: 4 },
  ],
};
