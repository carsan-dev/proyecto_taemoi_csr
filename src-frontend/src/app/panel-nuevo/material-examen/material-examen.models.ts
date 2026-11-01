/** Respuestas de `/api/material-examen` (`MaterialGestionDTO`, pieza R1). Las fechas van en ISO-8601 UTC. */

export interface GradoDelBloque {
  codigo: string;
  nombre: string;
}

export interface TotalesMaterial {
  temarios: number;
  videos: number;
  bytes: number;
}

export interface BloqueResumen {
  id: string;
  nombre: string;
  grados: GradoDelBloque[];
  tieneTemario: boolean;
  videos: number;
  bytes: number;
}

export interface DisciplinaConBloques {
  codigo: string;
  nombre: string;
  bloques: BloqueResumen[];
  totales: TotalesMaterial;
}

export interface ListadoMaterial {
  disciplinas: DisciplinaConBloques[];
  totales: TotalesMaterial;
}

export interface TemarioDetalle {
  nombre: string;
  bytes: number;
  fecha: string;
}

export interface VideoDetalle {
  fichero: string;
  titulo: string;
  orden: number;
  bytes: number;
  fecha: string;
  tambienEnSuperiores: boolean;
}

export interface DocumentoDetalle {
  fichero: string;
  titulo: string;
  mime: string | null;
  bytes: number;
  fecha: string;
}

export interface DetalleBloque {
  disciplina: string;
  id: string;
  nombre: string;
  grados: GradoDelBloque[];
  temario: TemarioDetalle | null;
  videos: VideoDetalle[];
  documentos: DocumentoDetalle[];
}

/** Resultado de una subida nueva: el nombre final (puede llevar sufijo) y el bloque tal como queda. */
export interface SubidaMaterial {
  fichero: string;
  bloque: DetalleBloque;
}
