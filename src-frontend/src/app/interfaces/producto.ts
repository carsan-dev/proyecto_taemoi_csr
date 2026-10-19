export interface Producto {
    id: number;
    concepto: string;
    fechaAsignacion: Date;
    cantidad: number;
    precio: number;
    pagado: boolean;
    fechaPago: Date | null;
    notas: string;
    /** Pieza 6b-3b: el profesor lo puede vender desde «Mi caja». */
    vendibleProfesor?: boolean;
  }