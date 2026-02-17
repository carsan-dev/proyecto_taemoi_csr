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
}
