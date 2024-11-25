export interface ProductoAlumnoDTO {
    id: number;
    productoId: number;
    alumnoId: number;
    concepto: string;
    fechaAsignacion: Date;
    cantidad: number;
    precio: number;
    pagado: boolean;
    fechaPago: Date | null;
    notas: string;
  }
  