/** Días que cuentan el aviso del panel y el filtro por defecto de errores de plataforma. */
export const DIAS_VENTANA_ERRORES = 7;

export function fechaIsoLocal(fecha: Date): string {
  const ano = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

/** Últimos N días contando hoy: el mismo periodo en el panel principal y en la pantalla de errores. */
export function ventanaUltimosDias(dias: number = DIAS_VENTANA_ERRORES, hoy: Date = new Date()): { desde: string; hasta: string } {
  const desde = new Date(hoy);
  desde.setDate(hoy.getDate() - (dias - 1));
  return { desde: fechaIsoLocal(desde), hasta: fechaIsoLocal(hoy) };
}
