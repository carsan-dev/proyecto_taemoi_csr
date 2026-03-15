export interface Producto {
    id: number;
    concepto: string;
    fechaAsignacion: Date;
    cantidad: number;
    precio: number;
    pagado: boolean;
    fechaPago: Date | null;
    notas: string;
  }