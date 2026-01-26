/**
 * Navigation constants for consistent menu items across the application
 */

export interface NavigationItem {
  label: string;
  route: string;
  icon?: string;
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
        route: '/defensapersonalfemenina',
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
