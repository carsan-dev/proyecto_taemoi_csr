export interface Producto {
    id: number;
    concepto: string;
    fecha: Date;
    cantidad: number;
    precio: number;
    pagado: boolean;
    fechaPago: Date | null;
    notas: string;
    alumnoId: number;
  }