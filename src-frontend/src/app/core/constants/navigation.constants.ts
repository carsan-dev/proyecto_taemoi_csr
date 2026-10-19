/**
 * Navigation constants for consistent menu items across the application
 */

export interface NavigationItem {
  label: string;
  route: string;
  icon?: string;
}

export interface AdminQuickNavigationItem extends NavigationItem {
  exact?: boolean;
  activePrefixes?: string[];
  priority?: number;
  adminOnly?: boolean;
  /** Solo para la sesión administradora de plataforma (se detecta con PlataformaService). */
  plataformaOnly?: boolean;
  /** Lleva el distintivo con el número de lo que espera acción (pieza 6c-7: solicitudes al propietario). */
  contador?: 'solicitudes';
}

export interface NavigationDropdownItem {
  label: string;
  items: NavigationItem[];
}

export type NavigationMenuItem = NavigationItem | NavigationDropdownItem;

/**
 * Check if menu item is a dropdown
 */
export function isDropdown(item: NavigationMenuItem): item is NavigationDropdownItem {
  return 'items' in item;
}

/**
 * Main navigation menu items (header)
 */
export const MAIN_NAV_ITEMS: NavigationMenuItem[] = [
  {
    label: 'Inicio',
    route: '/',
  },
  {
    label: '¿Qué hacemos?',
    items: [
      {
        label: 'Taekwondo',
        route: '/taekwondo',
      },
      {
        label: 'Kickboxing',
        route: '/kickboxing',
      },
      {
        label: 'Pilates',
        route: '/pilates',
      },
      {
        label: 'Defensa Personal Femenina',
        route: '/defensa-personal-femenina',
      },
    ],
  },
  {
    label: 'Eventos',
    route: '/eventos',
  },
  {
    label: 'Horarios',
    route: '/horarios',
  },
  {
    label: 'Contacto',
    route: '/contacto',
  },
];

/**
 * Admin header quick-access items.
 * Uses "listado" routes as canonical destinations for each module.
 */
export const ADMIN_HEADER_QUICK_LINKS: AdminQuickNavigationItem[] = [
  {
    label: 'Dashboard',
    route: '/adminpage',
    icon: 'bi-speedometer2',
    exact: true,
    activePrefixes: ['/adminpage'],
    priority: 1,
  },
  {
    label: 'Alumnos',
    route: '/alumnosListar',
    icon: 'bi-people',
    activePrefixes: [
      '/alumnosListar',
      '/alumnosEditar',
      '/alumnosCrear',
      '/alumnosEliminar',
      '/alumnos',
    ],
    priority: 2,
  },
  {
    label: 'Grupos y turnos',
    route: '/gruposListar',
    icon: 'bi-collection',
    activePrefixes: [
      '/gruposListar',
      '/gruposCrear',
      '/gruposEditar',
      '/gestionarAlumnos',
      '/seleccionarAlumnos',
      '/gestionarTurnosAlumno',
      '/turnosCrear',
      '/turnosEditar',
    ],
    priority: 5,
  },
  {
    label: 'Eventos',
    route: '/eventosListar',
    icon: 'bi-calendar-check-fill',
    activePrefixes: ['/eventosListar', '/eventosCrear', '/eventosEditar'],
    priority: 6,
  },
  {
    label: 'Productos',
    route: '/productosListar',
    icon: 'bi-box-seam-fill',
    activePrefixes: ['/productosListar', '/productosCrear', '/productosEditar'],
    priority: 7,
  },
  {
    label: 'Tesorería',
    route: '/tesoreriaCobros',
    icon: 'bi-cash-stack',
    activePrefixes: ['/tesoreriaCobros'],
    priority: 3,
  },
  {
    label: 'Caja de sede',
    route: '/cajaSede',
    icon: 'bi-safe2',
    activePrefixes: ['/cajaSede'],
    priority: 4,
  },
  {
    label: 'Solicitudes',
    route: '/solicitudes',
    icon: 'bi-inbox',
    activePrefixes: ['/solicitudes'],
    priority: 2,
    contador: 'solicitudes',
  },
  {
    label: 'Registro de acciones',
    route: '/auditoriaSistema',
    icon: 'bi-journal-text',
    activePrefixes: ['/auditoriaSistema'],
    priority: 4,
    adminOnly: true,
  },
  {
    label: 'Convocatorias',
    route: '/convocatoriasListar',
    icon: 'bi-clipboard-check-fill',
    activePrefixes: ['/convocatoriasListar'],
    priority: 5,
  },
  {
    label: 'Configuración',
    route: '/configuracion-sistema',
    icon: 'bi-sliders',
    activePrefixes: ['/configuracion-sistema'],
    priority: 9,
    adminOnly: true,
  },
  {
    label: 'Errores de plataforma',
    route: '/plataformaErrores',
    icon: 'bi-exclamation-octagon',
    plataformaOnly: true,
  },
];

/**
 * Social media links
 */
export const SOCIAL_LINKS = [
  {
    label: 'Facebook',
    url: 'https://www.facebook.com/moiskimdo.escueladetaekwondo/',
    icon: 'bi-facebook',
  },
  {
    label: 'Instagram',
    url: 'https://www.instagram.com/moiskimdo_/',
    icon: 'bi-instagram',
  },
  {
    label: 'TikTok',
    url: 'https://www.tiktok.com/@clubmoiskimdo',
    icon: 'bi-tiktok',
  },
];

