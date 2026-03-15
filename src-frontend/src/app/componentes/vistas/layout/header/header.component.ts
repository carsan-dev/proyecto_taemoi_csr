import { Component, HostListener, OnDestroy, OnInit, AfterViewInit, NgZone } from '@angular/core';
import { NavigationEnd, NavigationStart, Router, RouterModule } from '@angular/router';
import { AuthenticationService, PortalPreferido } from '../../../../servicios/authentication/authentication.service';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import Swal from 'sweetalert2';
import {
  ADMIN_HEADER_QUICK_LINKS,
  AdminQuickNavigationItem,
} from '../../../../core/constants/navigation.constants';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit, AfterViewInit, OnDestroy {
  usuarioLogueado: boolean = false;
  isAdmin: boolean = false;
  isAdminOnly: boolean = false;
  isUser: boolean = false;
  isUserRoute: boolean = false;
  tieneAccesoDual: boolean = false;
  isHidden: boolean = false;
  adminMenuVisible: boolean = false;
  isMobileMenuOpen: boolean = false;
  isMobileUserQuickMenuOpen: boolean = false;
  isDesktopUserMenuOpen: boolean = false;
  isAdminAccountMenuOpen: boolean = false;
  isAuthChecked: boolean = false; // Prevents header flash during auth check
  private lastScrollTop: number = 0;
  private readonly scrollThreshold: number = 100;
  username: string | null = null;
  private documentScrollHandler?: (event: Event) => void;
  private resizeHandler?: () => void;

  // Expandable menu sections
  expandedSections: { [key: string]: boolean } = {
    alumnos: false,
    grupos: false,
    turnos: false,
    eventos: false,
    productos: false,
    convocatorias: false,
    administracion: false
  };

  // Scroll optimization
  private readonly subscriptions = new Subscription();
  readonly adminQuickLinks: AdminQuickNavigationItem[] = ADMIN_HEADER_QUICK_LINKS;

  get visibleAdminQuickLinks(): AdminQuickNavigationItem[] {
    return this.adminQuickLinks.filter((item) => !item.adminOnly || this.isAdminOnly);
  }

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router,
    private readonly zone: NgZone
  ) {}

  ngOnInit(): void {
    this.actualizarContextoRuta(this.router.url);

    // Subscribe to authentication state changes
    this.subscriptions.add(
      this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
        this.usuarioLogueado = estado;
        if (estado) {
          this.comprobarRoles();
        } else {
          this.isAdmin = false;
          this.isAdminOnly = false;
          this.isUser = false;
          this.tieneAccesoDual = false;
          this.isAuthChecked = true;
        }
      })
    );

    // Subscribe to username changes
    this.subscriptions.add(
      this.authService.usernameCambio.subscribe((username: string | null) => {
        this.username = username;
      })
    );

    // Check initial login state
    if (this.authService.comprobarLogueado()) {
      this.usuarioLogueado = true;
      this.comprobarRoles();
    } else {
      // Not logged in, auth check complete
      this.isAuthChecked = true;
    }

    // Close admin menu on navigation and reset scroll state
    this.subscriptions.add(
      this.router.events.subscribe((event) => {
        if (event instanceof NavigationStart) {
          this.adminMenuVisible = false;
          this.closeMobileMenu();
          this.closeDesktopUserMenu();
          this.closeAdminAccountMenu();
        }
        // Reset scroll tracking on navigation end to prevent stale lastScrollTop values
        if (event instanceof NavigationEnd) {
          this.lastScrollTop = 0;
          this.isHidden = false;
          this.actualizarContextoRuta(event.urlAfterRedirects || event.url);
        }
      })
    );
  }

  ngAfterViewInit(): void {
    const documentRef = this.getDocumentRef();
    if (documentRef !== undefined) {
      // Listen to document scroll events (same as botonscroll)
      this.documentScrollHandler = (event: Event) => {
        this.zone.run(() => this.handleScrollEvent(event));
      };
      documentRef.addEventListener('scroll', this.documentScrollHandler, true);
    }

    const windowRef = this.getWindowRef();
    if (windowRef !== undefined) {
      // Update scrollbar width on resize (initial calculation is done in index.html)
      this.resizeHandler = () => {
        this.updateScrollbarWidth();
        if (windowRef.innerWidth < 992) {
          this.closeDesktopUserMenu();
        }
      };
      windowRef.addEventListener('resize', this.resizeHandler);
    }
  }

  private updateScrollbarWidth(): void {
    const documentRef = this.getDocumentRef();
    if (documentRef !== undefined) {
      const outer = documentRef.createElement('div');
      outer.style.cssText = 'visibility:hidden;overflow:scroll;width:100px;height:100px;position:absolute;top:-9999px';
      documentRef.body.appendChild(outer);
      const inner = documentRef.createElement('div');
      inner.style.cssText = 'width:100%;height:100%';
      outer.appendChild(inner);
      const scrollbarWidth = outer.offsetWidth - inner.offsetWidth;
      documentRef.body.removeChild(outer);
      documentRef.documentElement.style.setProperty('--scrollbar-width', `${scrollbarWidth}px`);
    }
  }


  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.setBodyScrollLock(false);

    // Clean up document scroll listener
    const documentRef = this.getDocumentRef();
    if (this.documentScrollHandler && documentRef !== undefined) {
      documentRef.removeEventListener('scroll', this.documentScrollHandler, true);
      this.documentScrollHandler = undefined;
    }

    // Clean up resize listener
    const windowRef = this.getWindowRef();
    if (this.resizeHandler && windowRef !== undefined) {
      windowRef.removeEventListener('resize', this.resizeHandler);
      this.resizeHandler = undefined;
    }
  }

  toggleMobileMenu(): void {
    this.closeDesktopUserMenu();
    this.closeAdminAccountMenu();
    this.closeMobileUserQuickMenu();
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
    this.setBodyScrollLock(this.isMobileMenuOpen);
  }

  closeMobileMenu(): void {
    this.closeMobileUserQuickMenu();
    if (!this.isMobileMenuOpen) {
      return;
    }
    this.isMobileMenuOpen = false;
    this.setBodyScrollLock(false);
  }

  toggleMobileUserQuickMenu(): void {
    this.isMobileUserQuickMenuOpen = !this.isMobileUserQuickMenuOpen;
  }

  closeMobileUserQuickMenu(): void {
    this.isMobileUserQuickMenuOpen = false;
  }

  toggleDesktopUserMenu(): void {
    this.closeMobileUserQuickMenu();
    this.closeAdminAccountMenu();
    this.isDesktopUserMenuOpen = !this.isDesktopUserMenuOpen;
  }

  closeDesktopUserMenu(): void {
    this.isDesktopUserMenuOpen = false;
  }

  toggleAdminAccountMenu(): void {
    this.closeDesktopUserMenu();
    this.closeMobileUserQuickMenu();
    this.isAdminAccountMenuOpen = !this.isAdminAccountMenuOpen;
  }

  closeAdminAccountMenu(): void {
    this.isAdminAccountMenuOpen = false;
  }

  private setBodyScrollLock(locked: boolean): void {
    const documentRef = this.getDocumentRef();
    if (!documentRef?.body) {
      return;
    }
    documentRef.body.style.overflow = locked ? 'hidden' : '';
  }

  comprobarRoles() {
    this.authService.getRoles().subscribe((roles: string[]) => {
      this.isAdmin = this.authService.tieneAccesoAdmin(roles);
      this.isAdminOnly = roles.includes('ROLE_ADMIN');
      this.isUser = this.authService.tieneAccesoUser(roles);
      this.tieneAccesoDual = this.authService.tieneAccesoDual(roles);
      this.isAuthChecked = true;
    });
  }

  get mostrarHeaderAdmin(): boolean {
    if (!this.isAuthChecked || !this.usuarioLogueado || !this.isAdmin) {
      return false;
    }
    return !this.isUserRoute;
  }

  get mostrarHeaderUser(): boolean {
    if (!this.isAuthChecked || !this.usuarioLogueado || !this.isUser) {
      return false;
    }
    if (!this.isAdmin) {
      return true;
    }
    return this.isUserRoute;
  }

  toggleAdminMenu(): void {
    this.adminMenuVisible = !this.adminMenuVisible;
  }

  closeAdminMenu(): void {
    this.adminMenuVisible = false;
    this.closeAdminAccountMenu();
    Object.keys(this.expandedSections).forEach(key => {
      this.expandedSections[key] = false;
    });
  }

  toggleSection(section: string): void {
    this.expandedSections[section] = !this.expandedSections[section];
  }

  isAdminQuickRouteActive(item: AdminQuickNavigationItem): boolean {
    const currentPath = this.obtenerRutaActualSinQuery();
    const prefixes = item.activePrefixes && item.activePrefixes.length > 0
      ? item.activePrefixes
      : [item.route];

    if (item.exact) {
      return prefixes.some((prefix) => currentPath === prefix);
    }

    return prefixes.some((prefix) =>
      currentPath === prefix || currentPath.startsWith(`${prefix}/`)
    );
  }

  @HostListener('window:scroll', [])
  onWindowScroll(): void {
    this.handleScrollEvent();
  }

  private getWindowRef(): Window | undefined {
    return (globalThis as { window?: Window }).window;
  }

  private getDocumentRef(): Document | undefined {
    return (globalThis as { document?: Document }).document;
  }

  private handleScrollEvent(event?: Event): void {
    const windowRef = this.getWindowRef();
    const documentRef = this.getDocumentRef();
    if (windowRef !== undefined && documentRef !== undefined) {
      const currentScrollTop = this.getEffectiveScrollTop(event, windowRef, documentRef);

      // Siempre mostrar header en la parte superior
      if (currentScrollTop <= this.scrollThreshold) {
        this.isHidden = false;
        this.lastScrollTop = currentScrollTop;
      }
      // Scroll hacia abajo: ocultar
      else if (currentScrollTop > this.lastScrollTop && currentScrollTop > this.scrollThreshold) {
        this.isHidden = true;
      }
      // Scroll hacia arriba: mostrar
      else if (currentScrollTop < this.lastScrollTop) {
        this.isHidden = false;
      }

      this.lastScrollTop = Math.max(currentScrollTop, 0);

      // Collapse navbar when scrolling
      this.collapseNavbar();
    }
  }

  private getEffectiveScrollTop(event: Event | undefined, windowRef: Window, documentRef: Document): number {
    const target = event?.target;
    const targetScrollTop = target instanceof HTMLElement ? target.scrollTop : 0;
    const scrollingElement = documentRef.scrollingElement as HTMLElement | null;
    const scrollingElementTop = scrollingElement?.scrollTop ?? 0;
    const windowScrollTop = windowRef.scrollY || documentRef.documentElement.scrollTop || documentRef.body?.scrollTop || 0;

    return Math.max(targetScrollTop, scrollingElementTop, windowScrollTop);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (this.isMobileUserQuickMenuOpen && !target.closest('.mobile-user-quick-menu')) {
      this.closeMobileUserQuickMenu();
    }
    if (this.isDesktopUserMenuOpen && !target.closest('.user-desktop-menu')) {
      this.closeDesktopUserMenu();
    }
    if (this.isAdminAccountMenuOpen && !target.closest('.admin-account-menu')) {
      this.closeAdminAccountMenu();
    }

    if (this.shouldIgnoreAdminClick(target)) {
      return;
    }

    this.handleNavbarClick(target);
  }

  private shouldIgnoreAdminClick(target: HTMLElement): boolean {
    if (!this.adminMenuVisible) {
      return false;
    }

    const clickedToggleButton = target.closest('.admin-toggle-button');
    if (clickedToggleButton) {
      return true;
    }

    const clickedInsideMenu = target.closest('.admin-sidebar');
    if (!clickedInsideMenu) {
      this.adminMenuVisible = false;
    }

    return false;
  }

  private handleNavbarClick(target: HTMLElement): void {
    const documentRef = this.getDocumentRef();
    if (documentRef !== undefined) {
      const navbarCollapse = documentRef.querySelector('.navbar-collapse.show');
      if (!navbarCollapse) {
        return;
      }

      if (this.shouldSkipNavbarClose(target)) {
        return;
      }

      const clickedInsideNavbar = target.closest('.navbar');
      const clickedNavLink = target.closest('.nav-link:not(.dropdown-toggle)'); // Exclude dropdown toggles
      const clickedButton = target.closest('.btn-login')
        || target.closest('.btn-my-classes')
        || target.closest('.btn-my-classes-mobile')
        || target.closest('.social-link');

      if (clickedNavLink || clickedButton || !clickedInsideNavbar) {
        this.collapseNavbar();
      }
    }
  }

  private shouldSkipNavbarClose(target: HTMLElement): boolean {
    const clickedToggler = target.closest('.navbar-toggler');
    if (clickedToggler) {
      return true;
    }

    const clickedDropdownToggle = target.closest('.dropdown-toggle');
    if (clickedDropdownToggle) {
      return true;
    }

    const clickedInsideDropdown = target.closest('.dropdown-menu');
    if (clickedInsideDropdown) {
      const clickedDropdownItem = target.closest('.dropdown-item');
      if (clickedDropdownItem) {
        this.collapseNavbar();
      }
      return true;
    }

    return false;
  }

  private collapseNavbar(): void {
    const navbarCollapse = document.querySelector('.navbar-collapse.show') as HTMLElement;
    if (navbarCollapse) {
      // Use Bootstrap's Collapse API for smooth animation
      const bsCollapse = (globalThis as any).bootstrap?.Collapse?.getInstance(navbarCollapse);
      if (bsCollapse) {
        bsCollapse.hide();
      } else {
        // Fallback: manually remove class with transition
        navbarCollapse.classList.remove('show');
      }
    }
  }

  private obtenerRutaActualSinQuery(): string {
    const currentUrl = this.router.url || '';
    return currentUrl.split('?')[0].split('#')[0];
  }

  cambiarVistaUsuario(): void {
    this.cambiarVista('user');
  }

  cambiarVistaAdmin(): void {
    this.cambiarVista('admin');
  }

  private cambiarVista(portal: PortalPreferido): void {
    this.authService.guardarVistaPreferida(portal);
    this.closeAdminAccountMenu();
    this.closeDesktopUserMenu();
    this.closeMobileUserQuickMenu();
    this.closeMobileMenu();
    this.closeAdminMenu();
    const rutaDestino = portal === 'admin' ? '/adminpage' : '/userpage';
    this.router.navigate([rutaDestino]);
  }

  private actualizarContextoRuta(url: string): void {
    const rutaActual = this.normalizarRuta(url);
    this.isUserRoute = rutaActual.startsWith('/userpage');
    this.authService.marcarVistaPreferidaSegunRuta(rutaActual);
  }

  private normalizarRuta(url: string): string {
    return (url || '').split('?')[0].split('#')[0];
  }

  cerrarSesion(): void {
    if (!this.usuarioLogueado) {
      Swal.fire({
        title: 'Atención',
        text: 'No has iniciado sesión.',
        icon: 'warning',
        timer: 2000,
      });
      return;
    }

    this.authService.logout();

    // Create temporary subscription for logout confirmation
    const subscription = this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      if (!estado) {
        subscription.unsubscribe();

        Swal.fire({
          title: 'Sesión cerrada con éxito',
          text: '¡Hasta la próxima!',
          icon: 'success',
          timer: 2000,
        });

        this.adminMenuVisible = false;
        this.closeAdminAccountMenu();
        this.router.navigate(['/']);
      }
    });
  }
}
