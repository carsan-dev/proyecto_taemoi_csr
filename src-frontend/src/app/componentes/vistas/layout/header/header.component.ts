import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { NavigationStart, Router, RouterModule } from '@angular/router';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { CommonModule } from '@angular/common';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit, OnDestroy {
  usuarioLogueado: boolean = false;
  isAdmin: boolean = false;
  isUser: boolean = false;
  isHidden: boolean = false;
  adminMenuVisible: boolean = false;
  isAuthChecked: boolean = false; // Prevents header flash during auth check
  private lastScrollTop: number = 0;
  username: string | null = null;

  // Expandable menu sections
  expandedSections: { [key: string]: boolean } = {
    alumnos: false,
    grupos: false,
    turnos: false,
    eventos: false,
    productos: false,
    convocatorias: false
  };

  // Scroll optimization
  private scrollSubject = new Subject<number>();
  private subscriptions = new Subscription();

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router
  ) {
    // Debounce scroll events for better performance
    this.subscriptions.add(
      this.scrollSubject.pipe(debounceTime(10)).subscribe((scrollTop) => {
        this.handleScroll(scrollTop);
      })
    );
  }

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

    // Close admin menu on navigation
    this.subscriptions.add(
      this.router.events.subscribe((event) => {
        if (event instanceof NavigationStart) {
          this.adminMenuVisible = false;
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.scrollSubject.complete();
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

  @HostListener('window:scroll', [])
  onWindowScroll(): void {
    const currentScrollTop = window.scrollY || document.documentElement.scrollTop;
    this.scrollSubject.next(currentScrollTop);

    // Collapse navbar when scrolling
    this.collapseNavbar();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;

    // Close admin menu when clicking outside (for admin sidebar)
    if (this.adminMenuVisible) {
      // Check if clicked on toggle button or its children
      const clickedToggleButton = target.closest('.admin-toggle-button');
      if (clickedToggleButton) {
        return; // Let the toggle button handle it
      }

      // Check if clicked inside the menu or its children
      const clickedInsideMenu = target.closest('.admin-menu');
      if (!clickedInsideMenu) {
        // Clicked outside the menu, close it
        this.adminMenuVisible = false;
      }
    }

    // Close Bootstrap navbar when clicking outside (for anonymous/user navbars)
    const navbarCollapse = document.querySelector('.navbar-collapse.show');
    if (navbarCollapse) {
      const clickedToggler = target.closest('.navbar-toggler');

      // Don't close if clicking the toggler button (let Bootstrap handle it)
      if (clickedToggler) {
        return;
      }

      // Don't close if clicking a dropdown toggle (let it open the dropdown)
      const clickedDropdownToggle = target.closest('.dropdown-toggle');
      if (clickedDropdownToggle) {
        return;
      }

      // Don't close if clicking inside an open dropdown menu
      const clickedInsideDropdown = target.closest('.dropdown-menu');
      if (clickedInsideDropdown) {
        // Only close if clicked on a dropdown-item (actual menu option)
        const clickedDropdownItem = target.closest('.dropdown-item');
        if (clickedDropdownItem) {
          this.collapseNavbar();
        }
        return;
      }

      const clickedInsideNavbar = target.closest('.navbar');
      const clickedNavLink = target.closest('.nav-link:not(.dropdown-toggle)'); // Exclude dropdown toggles
      const clickedButton = target.closest('.btn-login') || target.closest('.btn-my-classes') || target.closest('.btn-my-classes-mobile') || target.closest('.social-link');

      // Close if clicked on a regular nav-link/button OR clicked outside the navbar
      if (clickedNavLink || clickedButton || !clickedInsideNavbar) {
        this.collapseNavbar();
      }
    }
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

  private handleScroll(currentScrollTop: number): void {
    if (currentScrollTop > this.lastScrollTop && currentScrollTop > 100) {
      this.isHidden = true;
    } else {
      this.isHidden = false;
    }
    this.lastScrollTop = currentScrollTop;
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
        this.router.navigate(['/inicio']);
      }
    });
  }
}
