import { Component, HostListener, OnDestroy, OnInit, AfterViewInit, NgZone } from '@angular/core';
import { NavigationEnd, NavigationStart, Router, RouterModule } from '@angular/router';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
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
  isUser: boolean = false;
  isHidden: boolean = false;
  adminMenuVisible: boolean = false;
  isMobileMenuOpen: boolean = false;
  isMobileUserQuickMenuOpen: boolean = false;
  isDesktopUserMenuOpen: boolean = false;
  isAuthChecked: boolean = false; // Prevents header flash during auth check
  private lastScrollTop: number = 0;
  private readonly scrollThreshold: number = 100;
  username: string | null = null;
  private windowScrollHandler?: () => void;
  private resizeHandler?: () => void;
  private scrollTicking = false;

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

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router,
    private readonly zone: NgZone
  ) {}

  ngOnInit(): void {
    // Subscribe to authentication state changes
    this.subscriptions.add(
      this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
        this.usuarioLogueado = estado;
        if (estado) {
          this.comprobarRoles();
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
        }
        // Reset scroll tracking on navigation end to prevent stale lastScrollTop values
        if (event instanceof NavigationEnd) {
          this.lastScrollTop = 0;
          this.isHidden = false;
        }
      })
    );
  }

  ngAfterViewInit(): void {
    const windowRef = this.getWindowRef();
    if (windowRef !== undefined) {
      this.zone.runOutsideAngular(() => {
        this.windowScrollHandler = () => this.scheduleScrollUpdate();
        windowRef.addEventListener('scroll', this.windowScrollHandler, { passive: true });
      });

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

    // Clean up window scroll listener
    const windowRef = this.getWindowRef();
    if (this.windowScrollHandler && windowRef !== undefined) {
      windowRef.removeEventListener('scroll', this.windowScrollHandler);
      this.windowScrollHandler = undefined;
    }

    // Clean up resize listener
    if (this.resizeHandler && windowRef !== undefined) {
      windowRef.removeEventListener('resize', this.resizeHandler);
      this.resizeHandler = undefined;
    }
  }

  toggleMobileMenu(): void {
    this.closeDesktopUserMenu();
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
    this.isDesktopUserMenuOpen = !this.isDesktopUserMenuOpen;
  }

  closeDesktopUserMenu(): void {
    this.isDesktopUserMenuOpen = false;
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
      this.isAdmin = roles.includes('ROLE_ADMIN');
      this.isUser = roles.includes('ROLE_USER');
      this.isAuthChecked = true;
    });
  }

  toggleAdminMenu(): void {
    this.adminMenuVisible = !this.adminMenuVisible;
  }

  closeAdminMenu(): void {
    this.adminMenuVisible = false;
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

  private scheduleScrollUpdate(): void {
    if (this.scrollTicking) {
      return;
    }

    this.scrollTicking = true;
    requestAnimationFrame(() => {
      this.scrollTicking = false;
      this.handleScrollEvent();
    });
  }

  private getWindowRef(): Window | undefined {
    return (globalThis as { window?: Window }).window;
  }

  private getDocumentRef(): Document | undefined {
    return (globalThis as { document?: Document }).document;
  }

  private handleScrollEvent(): void {
    const windowRef = this.getWindowRef();
    const documentRef = this.getDocumentRef();
    if (windowRef !== undefined && documentRef !== undefined) {
      const currentScrollTop = this.getEffectiveScrollTop(windowRef, documentRef);
      const hiddenAnterior = this.isHidden;
      let nuevoEstadoHidden = hiddenAnterior;

      // Siempre mostrar header en la parte superior
      if (currentScrollTop <= this.scrollThreshold) {
        nuevoEstadoHidden = false;
        this.lastScrollTop = currentScrollTop;
      }
      // Scroll hacia abajo: ocultar
      else if (currentScrollTop > this.lastScrollTop && currentScrollTop > this.scrollThreshold) {
        nuevoEstadoHidden = true;
      }
      // Scroll hacia arriba: mostrar
      else if (currentScrollTop < this.lastScrollTop) {
        nuevoEstadoHidden = false;
      }

      this.lastScrollTop = Math.max(currentScrollTop, 0);
      if (nuevoEstadoHidden !== hiddenAnterior) {
        this.zone.run(() => {
          this.isHidden = nuevoEstadoHidden;
        });
      }
    }
  }

  private getEffectiveScrollTop(windowRef: Window, documentRef: Document): number {
    const scrollingElement = documentRef.scrollingElement as HTMLElement | null;
    const scrollingElementTop = scrollingElement?.scrollTop ?? 0;
    const windowScrollTop = windowRef.scrollY || documentRef.documentElement.scrollTop || documentRef.body?.scrollTop || 0;

    return Math.max(scrollingElementTop, windowScrollTop);
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

    const clickedInsideMenu = target.closest('.admin-menu');
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
        this.router.navigate(['/']);
      }
    });
  }
}
