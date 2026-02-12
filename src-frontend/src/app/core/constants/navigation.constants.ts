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
 * Admin sidebar navigation items
 */
export const ADMIN_SIDEBAR_SECTIONS = [
  {
    title: 'ACCIONES',
    items: [
      {
        label: 'Página principal',
        route: '/adminpage',
        icon: 'bi-house-fill',
      },
    ],
  },
  {
    title: 'ALUMNOS',
    items: [
      {
        label: 'Listado de alumnos',
        route: '/alumnosListar',
        icon: 'bi-people',
      },
      {
        label: 'Modificar alumnos',
        route: '/alumnosEditar',
        icon: 'bi-person-gear',
      },
      {
        label: 'Añadir alumnos',
        route: '/alumnosCrear',
        icon: 'bi-person-add',
      },
      {
        label: 'Eliminar alumnos',
        route: '/alumnosEliminar',
        icon: 'bi-person-x',
      },
    ],
  },
  {
    title: 'GRUPOS Y TURNOS',
    items: [
      {
        label: 'Gestión de grupos y turnos',
        route: '/gruposListar',
        icon: 'bi-people-fill',
      },
      {
        label: 'Crear turno',
        route: '/turnosCrear',
        icon: 'bi-calendar-plus',
      },
      {
        label: 'Crear grupo',
        route: '/gruposCrear',
        icon: 'bi-collection',
      },
    ],
  },
  {
    title: 'EVENTOS',
    items: [
      {
        label: 'Consultar eventos',
        route: '/eventosListar',
        icon: 'bi-calendar-check-fill',
      },
      {
        label: 'Añadir eventos',
        route: '/eventosCrear',
        icon: 'bi-calendar-plus-fill',
      },
    ],
  },
  {
    title: 'PRODUCTOS',
    items: [
      {
        label: 'Consultar productos',
        route: '/productosListar',
        icon: 'bi-box-seam-fill',
      },
      {
        label: 'Añadir productos',
        route: '/productosCrear',
        icon: 'bi-plus-square-fill',
      },
    ],
  },
  {
    title: 'TESORERÍA',
    items: [
      {
        label: 'Cobros y deudas',
        route: '/tesoreriaCobros',
        icon: 'bi-cash-stack',
      },
      {
        label: 'Auditoría sistema',
        route: '/auditoriaSistema',
        icon: 'bi-journal-text',
      },
    ],
  },
  {
    title: 'CONVOCATORIAS',
    items: [
      {
        label: 'Consultar convocatorias',
        route: '/convocatoriasListar',
        icon: 'bi-clipboard-check-fill',
      },
    ],
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
    label: 'Auditoría',
    route: '/auditoriaSistema',
    icon: 'bi-journal-text',
    activePrefixes: ['/auditoriaSistema'],
    priority: 4,
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
];

