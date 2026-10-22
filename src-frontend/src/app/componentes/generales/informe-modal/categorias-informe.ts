/** Un tipo de informe con su icono y su descripción, como lo pinta el diálogo «Generar informe». */
export interface InformeOption {
  value: string;
  label: string;
  icon?: string;
  description?: string;
}

/** Un grupo de tipos de informe del diálogo «Generar informe». */
export interface InformeCategory {
  title: string;
  icon: string;
  color: string;
  opciones: InformeOption[];
}

const DESCRIPCIONES: { [key: string]: string } = {
  'general': 'Listado completo de alumnos organizados por cinturón',
  'taekwondo': 'Alumnos de Taekwondo por nivel de grado',
  'kickboxing': 'Alumnos de Kickboxing por nivel de grado',
  'competidores': 'Competidores activos de Taekwondo y Kickboxing organizados por categoría',
  'dpf': 'Listado de alumnas de Defensa Personal Femenina con sus nombres y apellidos',
  'licencias': 'Estado actual de todas las licencias deportivas',
  'infantiles': 'Alumnos menores aptos para promoción de grado',
  'adultos': 'Alumnos adultos aptos para promoción de grado',
  'deudas': 'Resumen de pagos pendientes y deudas',
  'mensualidades': 'Estado de mensualidades de todos los alumnos',
  'mensualidades-taekwondo': 'Mensualidades específicas de Taekwondo',
  'mensualidades-kickboxing': 'Mensualidades específicas de Kickboxing',
  'productos': 'Listado completo de productos con precios',
  'reservas-plaza': 'Reservas de plaza por temporada y deporte'
};

/** Descripción de cada tipo de informe (vacía si no tiene). */
export function descripcionInforme(value: string): string {
  return DESCRIPCIONES[value] || '';
}

/**
 * Reparte los tipos de informe en los grupos del diálogo «Generar informe», en su orden, y quita los grupos vacíos. Sale
 * de `InformeModalComponent` sin cambios (8b-D2a) para que el panel lateral del listado nuevo use los mismos grupos.
 */
export function categoriasDeInforme(opcionesInforme: ReadonlyArray<{ value: string; label: string }>): InformeCategory[] {
  const categorias: InformeCategory[] = [
    {
      title: 'Listados de Alumnos',
      icon: 'bi-trophy-fill',
      color: '#6366f1',
      opciones: []
    },
    {
      title: 'Informes de Promoción',
      icon: 'bi-arrow-up-circle-fill',
      color: '#10b981',
      opciones: []
    },
    {
      title: 'Informes Financieros',
      icon: 'bi-cash-stack',
      color: '#f59e0b',
      opciones: []
    },
    {
      title: 'Otros Informes',
      icon: 'bi-file-earmark-text-fill',
      color: '#8b5cf6',
      opciones: []
    }
  ];

  // Classify each report into its category
  opcionesInforme.forEach(opcion => {
    const value = opcion.value.toLowerCase();

    if (['general', 'taekwondo', 'kickboxing', 'competidores', 'dpf'].includes(value) || value.includes('grado')) {
      categorias[0].opciones.push({
        ...opcion,
        icon: 'bi-award',
        description: descripcionInforme(opcion.value)
      });
    } else if (value.includes('infantiles') || value.includes('adultos')) {
      categorias[1].opciones.push({
        ...opcion,
        icon: 'bi-people',
        description: descripcionInforme(opcion.value)
      });
    } else if (value.includes('deuda') || value.includes('mensualidad') || value.includes('producto') || value.includes('reservas-plaza')) {
      categorias[2].opciones.push({
        ...opcion,
        icon: 'bi-currency-euro',
        description: descripcionInforme(opcion.value)
      });
    } else {
      categorias[3].opciones.push({
        ...opcion,
        icon: 'bi-file-text',
        description: descripcionInforme(opcion.value)
      });
    }
  });

  // Only include categories that have reports
  return categorias.filter(cat => cat.opciones.length > 0);
}
