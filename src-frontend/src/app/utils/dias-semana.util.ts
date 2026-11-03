/** Decisión 380: lunes a viernes salen siempre; sábado y domingo solo si la escuela tiene turnos ese día. */

export interface DiasVisibles {
  /** Nombre completo de cada día, en el orden de la semana. */
  dias: string[];
  /** Nombre corto de cada día, en el mismo orden. */
  cortos: string[];
}

/** «Miércoles», «MIERCOLES» o «miércoles» a «miercoles». */
export function claveDiaSemana(valor: unknown): string {
  return (valor ?? '')
    .toString()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .trim();
}

/**
 * Los días a pintar: los de `base` (lunes a viernes, con los textos de cada pantalla) y, detrás y en este orden, el
 * sábado y el domingo solo si algún turno cae en ese día. Cada pantalla pasa los textos de fin de semana que ya usa
 * (con o sin tilde, `Sáb` o `Sab`).
 */
export function diasConFinDeSemana(
  base: DiasVisibles,
  sabado: { dia: string; corto: string },
  domingo: { dia: string; corto: string },
  turnos: ReadonlyArray<{ diaSemana?: unknown } | null | undefined>,
): DiasVisibles {
  const claves = new Set((turnos ?? []).map((turno) => claveDiaSemana(turno?.diaSemana)));
  const dias = [...base.dias];
  const cortos = [...base.cortos];
  if (claves.has('sabado')) {
    dias.push(sabado.dia);
    cortos.push(sabado.corto);
  }
  if (claves.has('domingo')) {
    dias.push(domingo.dia);
    cortos.push(domingo.corto);
  }
  return { dias, cortos };
}
