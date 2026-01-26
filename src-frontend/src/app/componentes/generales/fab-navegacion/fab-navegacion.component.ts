import { Component, HostListener, OnInit, OnDestroy, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import Swal from 'sweetalert2';

interface NavItem {
  label: string;
  route: string;
  icon: string;
  color?: string;
  isDeporte?: boolean;
}

interface AdminSection {
  label: string;
  icon: string;
  items: NavItem[];
}

@Component({
  selector: 'app-fab-navegacion',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './fab-navegacion.component.html',
  styleUrl: './fab-navegacion.component.scss',
})
export class FabNavegacionComponent implements OnInit, OnDestroy, AfterViewChecked {
  isMenuOpen = false;
  isMobile = false;
  isTablet = false;
  isMobileMenuOpen = false;

  // Auth state
  usuarioLogueado = false;
  isAdmin = false;
  isUser = false;
  isAuthChecked = false;

  private readonly MOBILE_BREAKPOINT = 576;
  private readonly TABLET_BREAKPOINT = 992;
  private previousActiveElement: HTMLElement | null = null;
  private shouldFocusFirstItem = false;
  private readonly subscriptions = new Subscription();

  // Navigation items for public/user views
  readonly navItemsPrincipales: NavItem[] = [
    { label: 'Inicio', route: '/', icon: 'bi-house-door' },
    { label: 'Eventos', route: '/eventos', icon: 'bi-calendar-event' },
    { label: 'Horarios', route: '/horarios', icon: 'bi-clock' },
    { label: 'Tarifas', route: '/tarifas', icon: 'bi-tag' },
    { label: 'Contacto', route: '/contacto', icon: 'bi-envelope' },
  ];

  readonly navItemsDeportes: NavItem[] = [
    { label: 'Taekwondo', route: '/taekwondo', icon: 'bi-lightning-fill', color: '#0D47A1', isDeporte: true },
    { label: 'Kickboxing', route: '/kickboxing', icon: 'bi-gem', color: '#ff4500', isDeporte: true },
    { label: 'Pilates', route: '/pilates', icon: 'bi-heart-pulse', color: '#57A2A8', isDeporte: true },
    { label: 'Defensa Personal', route: '/defensapersonalfemenina', icon: 'bi-shield-fill-check', color: '#c2185b', isDeporte: true },
  ];

  readonly bottomNavItems: NavItem[] = [
    { label: 'Inicio', route: '/', icon: 'bi-house-door' },
    { label: 'Horarios', route: '/horarios', icon: 'bi-clock' },
    { label: 'Menú', route: '', icon: 'bi-grid-3x3-gap-fill' },
    { label: 'Eventos', route: '/eventos', icon: 'bi-calendar-event' },
    { label: 'Contacto', route: '/contacto', icon: 'bi-envelope' },
  ];

  // Navigation items for admin
  readonly bottomNavItemsAdmin: NavItem[] = [
    { label: 'Dashboard', route: '/adminpage', icon: 'bi-speedometer2' },
    { label: 'Alumnos', route: '/alumnosListar', icon: 'bi-people' },
    { label: 'Menú', route: '', icon: 'bi-grid-3x3-gap-fill' },
    { label: 'Grupos', route: '/gruposListar', icon: 'bi-collection' },
    { label: 'Eventos', route: '/eventosListar', icon: 'bi-calendar-check' },
  ];

  readonly adminSections: AdminSection[] = [
    {
      label: 'Principal',
      icon: 'bi-house',
      items: [
        { label: 'Dashboard', route: '/adminpage', icon: 'bi-speedometer2' },
        { label: 'Inicio (web)', route: '/', icon: 'bi-house-door' },
        { label: 'Horarios', route: '/horarios', icon: 'bi-clock' },
        { label: 'Tarifas', route: '/tarifas', icon: 'bi-tag' },
      ]
    },
    {
      label: 'Gestión',
      icon: 'bi-gear',
      items: [
        { label: 'Alumnos', route: '/alumnosListar', icon: 'bi-people' },
        { label: 'Grupos y Turnos', route: '/gruposListar', icon: 'bi-collection' },
        { label: 'Eventos', route: '/eventosListar', icon: 'bi-calendar-check' },
        { label: 'Productos', route: '/productosListar', icon: 'bi-cart3' },
        { label: 'Convocatorias', route: '/convocatoriasListar', icon: 'bi-clipboard-check' },
      ]
    },
    {
      label: 'Deportes',
      icon: 'bi-trophy',
      items: [
        { label: 'Taekwondo', route: '/taekwondo', icon: 'bi-lightning-fill', color: '#0D47A1' },
        { label: 'Kickboxing', route: '/kickboxing', icon: 'bi-gem', color: '#ff4500' },
        { label: 'Pilates', route: '/pilates', icon: 'bi-heart-pulse', color: '#57A2A8' },
        { label: 'Defensa Personal', route: '/defensapersonalfemenina', icon: 'bi-shield-fill-check', color: '#c2185b' },
      ]
    },
  ];

  // User-specific items
  readonly navItemsUser: NavItem[] = [
    { label: 'Mis Clases', route: '/userpage', icon: 'bi-calendar2-check' },
  ];

  constructor(
    private router: Router,
    private elementRef: ElementRef,
    private authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.checkScreenSize();
    this.initAuthSubscriptions();
  }

  ngOnDestroy(): void {
    this.closeAllMenus();
    this.subscriptions.unsubscribe();
  }

  ngAfterViewChecked(): void {
    if (this.shouldFocusFirstItem) {
      this.focusFirstMenuItem();
      this.shouldFocusFirstItem = false;
    }
  }

  private initAuthSubscriptions(): void {
    this.subscriptions.add(
      this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
        this.usuarioLogueado = estado;
        if (estado) {
          this.checkRoles();
        } else {
          this.isAdmin = false;
          this.isUser = false;
        }
      })
    );

    if (this.authService.comprobarLogueado()) {
      this.usuarioLogueado = true;
      this.checkRoles();
    } else {
      this.isAuthChecked = true;
    }
  }

  private checkRoles(): void {
    this.authService.getRoles().subscribe((roles: string[]) => {
      this.isAdmin = roles.includes('ROLE_ADMIN');
      this.isUser = roles.includes('ROLE_USER');
      this.isAuthChecked = true;
    });
  }

  @HostListener('window:resize')
  onResize(): void {
    this.checkScreenSize();
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.closeAllMenus();
  }

  private checkScreenSize(): void {
    const wasMobile = this.isMobile;
    const wasTablet = this.isTablet;
    const width = window.innerWidth;

    this.isMobile = width < this.MOBILE_BREAKPOINT;
    this.isTablet = width >= this.MOBILE_BREAKPOINT && width < this.TABLET_BREAKPOINT;

    if (wasMobile !== this.isMobile || wasTablet !== this.isTablet) {
      this.closeAllMenus();
    }
  }

  toggleMenu(): void {
    if (!this.isMenuOpen) {
      this.previousActiveElement = document.activeElement as HTMLElement;
      this.shouldFocusFirstItem = true;
    }
    this.isMenuOpen = !this.isMenuOpen;

    if (!this.isMenuOpen) {
      this.restoreFocus();
    }
  }

  toggleMobileMenu(): void {
    if (!this.isMobileMenuOpen) {
      this.previousActiveElement = document.activeElement as HTMLElement;
      this.shouldFocusFirstItem = true;
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
      this.restoreFocus();
    }
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }

  closeAllMenus(): void {
    const wasOpen = this.isMenuOpen || this.isMobileMenuOpen;
    this.isMenuOpen = false;
    this.isMobileMenuOpen = false;
    document.body.style.overflow = '';

    if (wasOpen) {
      this.restoreFocus();
    }
  }

  private focusFirstMenuItem(): void {
    const selector = this.isMobileMenuOpen
      ? '.mobile-menu-content .mobile-menu-item, .mobile-menu-content .admin-menu-item'
      : '.fab-menu.open .fab-menu-item';
    const firstItem = this.elementRef.nativeElement.querySelector(selector) as HTMLElement;
    firstItem?.focus();
  }

  private restoreFocus(): void {
    if (this.previousActiveElement) {
      this.previousActiveElement.focus();
      this.previousActiveElement = null;
    }
  }

  navigateTo(route: string): void {
    if (route) {
      this.router.navigate([route]);
      this.closeAllMenus();
    }
  }

  onBottomNavClick(item: NavItem): void {
    if (item.label === 'Menú') {
      this.toggleMobileMenu();
    } else {
      this.navigateTo(item.route);
    }
  }

  onBackdropClick(): void {
    this.closeAllMenus();
  }

  isActiveRoute(route: string): boolean {
    return this.router.url === route;
  }

  cerrarSesion(): void {
    this.closeAllMenus();
    this.authService.logout();

    const subscription = this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      if (!estado) {
        subscription.unsubscribe();
        Swal.fire({
          title: 'Sesión cerrada con éxito',
          text: '¡Hasta la próxima!',
          icon: 'success',
          timer: 2000,
        });
        this.router.navigate(['/']);
      }
    });
  }

  get currentBottomNavItems(): NavItem[] {
    return this.isAdmin ? this.bottomNavItemsAdmin : this.bottomNavItems;
  }
}
