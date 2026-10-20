export interface TesoreriaMovimiento {
  productoAlumnoId: number;
  alumnoId: number | null;
  alumnoNombreCompleto: string;
  deporte: string;
  concepto: string;
  categoria: string;
  fechaAsignacion: string | Date | null;
  pagado: boolean;
  fechaPago: string | Date | null;
  precio: number;
  notas: string | null;
  alumnoActivo?: boolean;
  /** 6c-6 (D6c-12): el cargo está devuelto (su cobro activo tiene una devolución enlazada). Sigue pagado (153). */
  devuelto?: boolean;
}
