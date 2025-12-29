import { calcularEdad } from './calcular-edad';

export function calcularCategoriaPorEdad(
  fechaNacimiento: string | Date | null | undefined
): string {
  if (!fechaNacimiento) {
    return '';
  }

  const fecha =
    fechaNacimiento instanceof Date
      ? fechaNacimiento.toISOString()
      : fechaNacimiento;

  const edad = calcularEdad(fecha);
  if (Number.isNaN(edad)) {
    return '';
  }

  if (edad <= 9) {
    return 'Infantil';
  }
  if (edad <= 11) {
    return 'Precadete';
  }
  if (edad <= 13) {
    return 'Cadete';
  }
  if (edad <= 16) {
    return 'Junior';
  }
  return 'Senior';
}
