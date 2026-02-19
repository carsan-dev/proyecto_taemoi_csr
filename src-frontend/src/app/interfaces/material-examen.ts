export interface MaterialExamenTemarioDTO {
  fileName: string;
  downloadUrl: string;
}

export interface MaterialExamenVideoDTO {
  id: string;
  title: string;
  order: number;
  streamUrl: string;
}

export interface MaterialExamenDTO {
  deporte: string;
  gradoActual: string | null;
  bloqueId: string | null;
  temario: MaterialExamenTemarioDTO | null;
  videos: MaterialExamenVideoDTO[];
}
