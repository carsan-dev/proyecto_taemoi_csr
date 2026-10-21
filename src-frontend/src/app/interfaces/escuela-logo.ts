/** Escuela tal como la lista plataforma para gestionar su logo y su color. */
export interface EscuelaLogo {
  id: number;
  nombre: string;
  tieneLogo: boolean;
  /** Marca propia de la plataforma: su logo no se cambia desde aquí. */
  marcaHeredada: boolean;
  /** Color de la escuela tal cual se guardó (`#RRGGBB`); `null` sin color (el panel usa `#1A2230`). */
  colorPrimario: string | null;
}
