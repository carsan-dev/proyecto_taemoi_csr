export interface Evento {
  id: number;
  titulo: string;
  descripcion: string;
  fotoEvento: {
    id: number;
    nombre: string;
    tipo: string;
    datos: string;
  } | null;
}
