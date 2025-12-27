export interface Documento {
  id: number;
  nombre: string;
  tipo: string;
  url: string;
  ruta: string;
}

export interface Evento {
  id: number;
  titulo: string;
  descripcion: string;
  fotoEvento: {
    ruta: string | null;
    url: string | null;
    id: number;
    nombre: string;
    tipo: string;
  } | null;
  documentos?: Documento[];
}
