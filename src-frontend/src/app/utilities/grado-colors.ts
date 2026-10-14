import { catalogoActual, disciplinaActual } from '../features/catalogo/catalogo-actual';
import { CatalogoGrado } from '../features/catalogo/catalogo.models';
import { coloresGrado, estiloTextoGrado, gradosDeDisciplina } from '../features/catalogo/catalogo-reglas';

export interface GradoColorInfo {
  colors: string[];
  isSplit: boolean;
  needsBorder: boolean;
}

/** Grado del sistema de la disciplina en el catálogo cargado (código sin distinguir mayúsculas); `undefined` si no hay. */
function gradoDelCatalogo(tipoGrado: string, deporte: string | null | undefined): CatalogoGrado | undefined {
  const catalogo = catalogoActual();
  const disciplina = deporte ? disciplinaActual(deporte) : undefined;
  if (!catalogo || !disciplina) {
    return undefined;
  }
  const codigo = tipoGrado.toUpperCase();
  return gradosDeDisciplina(catalogo.sistemasGrados, disciplina).find((g) => g.codigo.toUpperCase() === codigo);
}

/**
 * Colores de un grado. Con `deporte` y el grado en el catálogo con `color1` (D5): sus colores
 * (`color1` mitad inferior, `color2` superior; KB ROJO es marrón, D4). En cualquier otro caso (sin
 * deporte, grado desconocido o `color1` nulo): la paleta por nombre de color de este archivo.
 */
export function getGradoColors(tipoGrado: string, deporte?: string | null): GradoColorInfo {
  if (!tipoGrado) {
    return { colors: ['#CCCCCC'], isSplit: false, needsBorder: false };
  }

  const delCatalogo = gradoDelCatalogo(tipoGrado, deporte);
  if (delCatalogo?.color1) {
    return coloresGrado(delCatalogo);
  }

  const gradoUpper = tipoGrado.toUpperCase();

  // Map grade types to colors
  const colorMap: { [key: string]: string } = {
    BLANCO: '#FFFFFF',
    AMARILLO: '#FFFF00',
    NARANJA: '#FFA500',
    VERDE: '#008000',
    AZUL: '#0000FF',
    ROJO: '#FF0000',
    NEGRO: '#000000',
  };

  // Check if it's a split color grade (supports underscore, hyphen, slash, or space)
  const parts = gradoUpper.split(/[\s/_-]+/).filter(Boolean);
  const colors: string[] = [];

  for (const part of parts) {
    if (colorMap[part]) {
      colors.push(colorMap[part]);
    }
  }

  if (colors.length >= 2) {
    const splitColors = colors.slice(0, 2);
    return {
      colors: splitColors,
      isSplit: true,
      needsBorder: splitColors.some(c => c === '#FFFFFF'),
    };
  }

  // Single color grades
  let color = '#CCCCCC';

  if (gradoUpper.startsWith('BLANCO')) {
    color = colorMap['BLANCO'];
  } else if (gradoUpper.startsWith('AMARILLO')) {
    color = colorMap['AMARILLO'];
  } else if (gradoUpper.startsWith('NARANJA')) {
    color = colorMap['NARANJA'];
  } else if (gradoUpper.startsWith('VERDE')) {
    color = colorMap['VERDE'];
  } else if (gradoUpper.startsWith('AZUL')) {
    color = colorMap['AZUL'];
  } else if (gradoUpper.startsWith('ROJO')) {
    color = colorMap['ROJO'];
  } else if (gradoUpper.includes('NEGRO') || gradoUpper.includes('DAN')) {
    color = colorMap['NEGRO'];
  }

  return {
    colors: [color],
    isSplit: false,
    needsBorder: color === '#FFFFFF',
  };
}

/**
 * Check if a color is light and needs a dark outline for visibility
 */
function isLightColor(hexColor: string): boolean {
  // Convert hex to RGB
  const hex = hexColor.replace('#', '');
  const r = Number.parseInt(hex.substring(0, 2), 16);
  const g = Number.parseInt(hex.substring(2, 4), 16);
  const b = Number.parseInt(hex.substring(4, 6), 16);

  // Calculate luminance
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;

  // If luminance is > 0.7, it's a light color
  return luminance > 0.7;
}

/**
 * Nombre a mostrar de un grado: `grado.nombre` del catálogo para la disciplina (D3; KB ROJO es «MARRÓN», D4).
 * Grado desconocido, sin deporte o catálogo sin cargar: el propio código.
 *
 * @param tipoGrado El código del grado (ej: 'ROJO', 'BLANCO')
 * @param deporte El código de la disciplina (ej: 'KICKBOXING', 'TAEKWONDO')
 */
export function getGradoNombreParaDeporte(tipoGrado: string | null | undefined, deporte: string | null | undefined): string {
  if (!tipoGrado) {
    return '';
  }

  return gradoDelCatalogo(tipoGrado, deporte)?.nombre ?? tipoGrado;
}

/**
 * Gets inline styles for displaying a grade with colored text. Pasar `deporte` para usar los colores del
 * catálogo (D4/D5); sin él, la paleta por nombre (los componentes de listados lo pasarán en la rebanada 5).
 */
export function getGradoTextStyle(tipoGrado: string, deporte?: string | null): string {
  const colorInfo = getGradoColors(tipoGrado, deporte);

  if (colorInfo.isSplit) {
    // For split colors, we'll use a gradient
    const gradient = `linear-gradient(to bottom, ${colorInfo.colors[1]} 50%, ${colorInfo.colors[0]} 50%)`;
    let style = `background: ${gradient}; -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; font-weight: bold;`;

    // Check if any color is light
    const hasLightColor = colorInfo.colors.some(c => isLightColor(c));
    if (hasLightColor || colorInfo.needsBorder) {
      // Add subtle drop shadow for visibility
      style += ' filter: drop-shadow(0px 1px 1px rgba(0,0,0,0.4)) drop-shadow(0px 0px 2px rgba(0,0,0,0.3));';
    }

    return style;
  } else {
    // Single color
    const color = colorInfo.colors[0];
    let style = `color: ${color}; font-weight: bold;`;

    // Add subtle shadow for light colors
    if (isLightColor(color) || colorInfo.needsBorder) {
      style += ' text-shadow: 0px 1px 2px rgba(0,0,0,0.5), 0px 0px 3px rgba(0,0,0,0.3), 0px 0px 5px rgba(0,0,0,0.2);';
    }

    return style;
  }
}

/**
 * Estilo de texto de un grado identificado por su NOMBRE (`grado.nombre`, p. ej. «MARRÓN»), cuando solo se
 * conoce el nombre y no la disciplina (listados cortos del backend, `AlumnoCortoDTO.grado`). Busca el primer
 * grado con ese nombre y `color1` en cualquier sistema del catálogo (D5); si no hay (catálogo sin cargar,
 * nombre desconocido, `color1` nulo): paleta por nombre de color de este archivo.
 * Con nombres repetidos entre disciplinas con colores distintos el resultado es el del primer sistema:
 * limitación hasta que el DTO incluya disciplina o colores.
 */
export function getGradoTextStylePorNombre(nombre: string | null | undefined): string {
  const buscado = (nombre ?? '').trim().toUpperCase();
  if (buscado) {
    const grado = catalogoActual()
      ?.sistemasGrados.flatMap((sistema) => sistema.grados)
      .find((g) => g.color1 && g.nombre.trim().toUpperCase() === buscado);
    if (grado) {
      return estiloTextoGrado(grado);
    }
  }
  return getGradoTextStyle(nombre ?? '');
}
