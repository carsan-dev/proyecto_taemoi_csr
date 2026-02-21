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

export interface MaterialExamenDocumentoDTO {
  id: string;
  fileName: string;
  title: string;
  order: number;
  mimeType: string;
  previewable: boolean;
  openUrl: string;
  downloadUrl: string;
}

export interface MaterialExamenDTO {
  deporte: string;
  gradoActual: string | null;
  bloqueId: string | null;
  temario: MaterialExamenTemarioDTO | null;
  documentos: MaterialExamenDocumentoDTO[];
  videos: MaterialExamenVideoDTO[];
}
