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
  private lastScrollTop: number = 0;
  username: string | null = null;

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
    });
  }

  toggleAdminMenu(): void {
    this.adminMenuVisible = !this.adminMenuVisible;
  }

  closeAdminMenu(): void {
    console.log('closeAdminMenu called');
    this.adminMenuVisible = false;
  }

  @HostListener('window:scroll', [])
  onWindowScroll(): void {
    const currentScrollTop = window.scrollY || document.documentElement.scrollTop;
    this.scrollSubject.next(currentScrollTop);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    // Close admin menu when clicking outside
    if (!this.adminMenuVisible) {
      return; // Menu is closed, nothing to do
    }

    const target = event.target as HTMLElement;
    console.log('Document clicked, target:', target.className);

    // Check if clicked on toggle button or its children
    const clickedToggleButton = target.closest('.admin-toggle-button');
    if (clickedToggleButton) {
      console.log('Clicked toggle button, ignoring');
      return; // Let the toggle button handle it
    }

    // Check if clicked inside the menu or its children
    const clickedInsideMenu = target.closest('.admin-menu');
    if (!clickedInsideMenu) {
      // Clicked outside the menu, close it
      console.log('Clicked outside menu, closing');
      this.adminMenuVisible = false;
    } else {
      console.log('Clicked inside menu, keeping open');
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