export interface GradoColorInfo {
  colors: string[];
  isSplit: boolean;
  needsBorder: boolean;
}

/**
 * Gets the color(s) for a given grade type
 */
export function getGradoColors(tipoGrado: string): GradoColorInfo {
  if (!tipoGrado) {
    return { colors: ['#CCCCCC'], isSplit: false, needsBorder: false };
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

  // Check if it's a split color grade (contains underscore)
  if (gradoUpper.includes('_') && !gradoUpper.includes('DAN') && !gradoUpper.includes('PUM')) {
    const parts = gradoUpper.split('_');
    const colors: string[] = [];

    for (const part of parts) {
      if (colorMap[part]) {
        colors.push(colorMap[part]);
      }
    }

    if (colors.length === 2) {
      return {
        colors: colors,
        isSplit: true,
        needsBorder: colors.some(c => c === '#FFFFFF'),
      };
    }
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
  const r = parseInt(hex.substring(0, 2), 16);
  const g = parseInt(hex.substring(2, 4), 16);
  const b = parseInt(hex.substring(4, 6), 16);

  // Calculate luminance
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;

  // If luminance is > 0.7, it's a light color
  return luminance > 0.7;
}

/**
 * Gets inline styles for displaying a grade with colored text
 */
export function getGradoTextStyle(tipoGrado: string): string {
  const colorInfo = getGradoColors(tipoGrado);

  if (colorInfo.isSplit) {
    // For split colors, we'll use a gradient
    const gradient = `linear-gradient(to right, ${colorInfo.colors[0]} 50%, ${colorInfo.colors[1]} 50%)`;
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
