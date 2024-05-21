export interface AlumnoDTO {
  id: number;
  nombre: string;
  apellidos: string;
  fotoAlumno: {
    id: number;
    nombre: string;
    tipo: string;
    datos: string;
  };
}
