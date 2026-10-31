import { Component, HostListener, OnDestroy, OnInit, AfterViewInit, NgZone } from '@angular/core';
import { NavigationEnd, NavigationStart, Router, RouterModule } from '@angular/router';
import {
  AuthenticationService,
  PortalPreferido,
  esSoloProfesor,
} from '../../../../servicios/authentication/authentication.service';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import Swal from 'sweetalert2';
import { resolverPortalContexto } from '../../../../app.routes';
import { PageScrollService } from '../../../../servicios/generales/page-scroll.service';
import { ScrollLockRelease, ScrollLockService } from '../../../../servicios/generales/scroll-lock.service';
import { PermisosSesionService, pidePermisos } from '../../../../servicios/authentication/permisos-sesion.service';


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
  /** Sesión solo de profesor (sin gestión ni familia): en la web pública ve el menú público con «Volver al panel». */
  esProfesorSolo: boolean = false;
  /** La sesión tiene ROLE_PROFESOR: enlaza «Mi caja» desde el menú de familia. */
  tieneRolProfesor: boolean = false;
  /** «Mis grupos» (E8-5, decisión 217): la sesión tiene la capacidad MIS_GRUPOS de `GET /api/auth/permisos`. */
  veMisGrupos: boolean = false;
  isUserRoute: boolean = false;
  tieneAccesoDual: boolean = false;
  isHidden: boolean = false;
  isMobileMenuOpen: boolean = false;
  isMobileUserQuickMenuOpen: boolean = false;
  isDesktopUserMenuOpen: boolean = false;
  isAuthChecked: boolean = false; // Prevents header flash during auth check
  private lastScrollTop: number = 0;
  private readonly scrollThreshold: number = 100;
  username: string | null = null;
  private documentScrollHandler?: (event: Event) => void;
  private resizeHandler?: () => void;
  private mobileMenuScrollRelease?: ScrollLockRelease;
  private permisosSuscripcion?: Subscription;

  private readonly subscriptions = new Subscription();

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router,
    private readonly zone: NgZone,
    private readonly pageScroll: PageScrollService,
    private readonly scrollLock: ScrollLockService,
    private readonly permisos: PermisosSesionService
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
          this.isUser = false;
          this.tieneRolProfesor = false;
          this.actualizarPermisos([]);
          this.tieneAccesoDual = false;
          this.esProfesorSolo = false;
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

    // Cierra los menús al navegar y reinicia el estado del scroll
    this.subscriptions.add(
      this.router.events.subscribe((event) => {
        if (event instanceof NavigationStart) {
          this.closeMobileMenu();
          this.closeDesktopUserMenu();
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
    this.permisosSuscripcion?.unsubscribe();
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
    if (locked) {
      this.mobileMenuScrollRelease ??= this.scrollLock.lock();
      return;
    }
    this.mobileMenuScrollRelease?.();
    this.mobileMenuScrollRelease = undefined;
  }

  comprobarRoles() {
    this.authService.getRoles().subscribe((roles: string[]) => {
      this.isAdmin = this.authService.tieneAccesoAdmin(roles);
      this.isUser = this.authService.tieneAccesoUser(roles);
      this.tieneRolProfesor = roles.includes('ROLE_PROFESOR');
      this.actualizarPermisos(roles);
      this.tieneAccesoDual = this.authService.tieneAccesoDual(roles);
      this.esProfesorSolo = esSoloProfesor(roles);
      this.isAuthChecked = true;
    });
  }

  /**
   * Pide los permisos una vez por sesión con ROLE_PROFESOR o gestión (`pidePermisos`). «Mis grupos» sigue igual que
   * antes: solo con ROLE_PROFESOR y la capacidad MIS_GRUPOS, así que pedirlos con gestión no cambia ningún menú. Si
   * fallan, sin enlace.
   */
  private actualizarPermisos(roles: readonly string[]): void {
    this.permisosSuscripcion?.unsubscribe();
    this.permisosSuscripcion = undefined;
    this.veMisGrupos = false;
    if (!pidePermisos(roles)) {
      return;
    }
    const profesor = roles.includes('ROLE_PROFESOR');
    this.permisosSuscripcion = this.permisos
      .capacidades()
      .subscribe((capacidades) => (this.veMisGrupos = profesor && capacidades.includes('MIS_GRUPOS')));
  }

  /**
   * Sesión del panel (gestión o solo profesor) fuera de las rutas de familia (la web pública y, si el skeleton cae en
   * «lo de hoy», también las del panel): ve el menú público de siempre, con
   * «Volver al panel» en lugar de «Entrar». Las cuentas con gestión y familia conservan su cabecera en las rutas de
   * familia (`mostrarHeaderUser`).
   */
  get sesionDelPanelEnWeb(): boolean {
    if (!this.isAuthChecked || !this.usuarioLogueado || this.isUserRoute || this.mostrarHeaderUser) {
      return false;
    }
    return this.isAdmin || this.esProfesorSolo;
  }

  /** El menú público lo ven los visitantes y las sesiones del panel que miran la web. */
  get mostrarHeaderPublico(): boolean {
    if (!this.isAuthChecked) {
      return false;
    }
    return !this.usuarioLogueado || this.sesionDelPanelEnWeb;
  }

  /** Destino de «Volver al panel»: gestión siempre al panel de gestión (aunque prefiera la vista de familia); profesor, a sus grupos. */
  get destinoPanel(): string {
    return this.isAdmin ? '/adminpage' : '/mis-grupos';
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
    const currentScrollTop = this.pageScroll.scrollTop;

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
    this.collapseNavbar();
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

    this.handleNavbarClick(target);
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

  cambiarVistaAdmin(): void {
    this.cambiarVista('admin');
  }

  private cambiarVista(portal: PortalPreferido): void {
    this.authService.guardarVistaPreferida(portal);
    this.closeDesktopUserMenu();
    this.closeMobileUserQuickMenu();
    this.closeMobileMenu();
    const rutaDestino = portal === 'admin' ? '/adminpage' : '/userpage';
    this.router.navigate([rutaDestino]);
  }

  private actualizarContextoRuta(url: string): void {
    const rutaActual = this.normalizarRuta(url);
    this.isUserRoute = resolverPortalContexto(rutaActual) === 'user';
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

        this.router.navigate(['/']);
      }
    });
  }
}
