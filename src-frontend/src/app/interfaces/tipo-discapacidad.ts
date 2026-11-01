export const TIPOS_DISCAPACIDAD = {
  FISICA: 'Física',
  INTELECTUAL: 'Intelectual',
  VISUAL: 'Visual',
  AUDITIVA: 'Auditiva',
  PSICOSOCIAL: 'Psicosocial',
  OTRA: 'Otra',
} as const;
export type TipoDiscapacidad = keyof typeof TIPOS_DISCAPACIDAD;
export const ORDEN_DISCAPACIDAD = Object.keys(TIPOS_DISCAPACIDAD) as TipoDiscapacidad[];
