export interface RetoDiarioRankingGeneralItem {
  posicion: number;
  alias: string;
  mejorRacha: number;
  diasCompletadosTotales: number;
  esUsuarioActual: boolean;
}

export interface RetoDiarioRankingGeneralMiPosicion {
  posicion: number;
  alias: string;
  mejorRacha: number;
  diasCompletadosTotales: number;
  diasParaSuperarSiguiente: number | null;
}

export interface RetoDiarioRankingGeneral {
  deporte: string;
  totalParticipantes: number;
  top: RetoDiarioRankingGeneralItem[];
  miPosicion: RetoDiarioRankingGeneralMiPosicion | null;
}
