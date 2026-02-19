export interface RetoDiarioRankingItem {
  posicion: number;
  alias: string;
  diasCompletados: number;
  esUsuarioActual: boolean;
}

export interface RetoDiarioRankingMiPosicion {
  posicion: number;
  alias: string;
  diasCompletados: number;
  diasParaSuperarSiguiente: number | null;
}

export interface RetoDiarioRankingSemanal {
  deporte: string;
  anioIso: number;
  semanaIso: number;
  totalParticipantes: number;
  top: RetoDiarioRankingItem[];
  miPosicion: RetoDiarioRankingMiPosicion | null;
}
