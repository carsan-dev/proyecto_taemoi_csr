export interface AlumnoDTO {
  grupos: any;
  id: number;
  nombre: string;
  apellidos: string;
  fotoAlumno: {
    id: number;
    nombre: string;
    tipo: string;
    datos: string;
  };
  numeroExpediente?: number;
  grado?: string;
  activo?: boolean;
  responsableLegalNombre?: string | null;
  responsableLegalNif?: string | null;
}
