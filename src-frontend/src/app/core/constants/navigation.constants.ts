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

