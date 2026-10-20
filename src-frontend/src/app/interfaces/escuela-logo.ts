/** Escuela tal como la lista plataforma para gestionar su logo. */
export interface EscuelaLogo {
  id: number;
  nombre: string;
  tieneLogo: boolean;
  /** Marca propia de la plataforma: su logo no se cambia desde aquí. */
  marcaHeredada: boolean;
}
