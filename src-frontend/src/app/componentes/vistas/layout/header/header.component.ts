import { Component, HostListener, OnInit } from '@angular/core';
import { NavigationStart, Router, RouterModule } from '@angular/router';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { CommonModule } from '@angular/common';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit {
  usuarioLogueado: boolean = false;
  isAdmin: boolean = false;
  isUser: boolean = false;
  isHidden: boolean = false;
  adminMenuVisible: boolean = false;
  private lastScrollTop: number = 0;
  username: string | null = null;

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;

      if (estado) {
        this.comprobarRoles();
      }
    });

    this.authService.usernameCambio.subscribe((username: string | null) => {
      if (username) {
        this.username = username;
      } else {
        this.username = null;
      }
    });

    if (this.authService.comprobarLogueado()) {
      this.usuarioLogueado = true;
      this.comprobarRoles();
    }

    this.router.events.subscribe(event => {
      if (event instanceof NavigationStart) {
        this.adminMenuVisible = false;
      }
    });
    
  }

  comprobarRoles() {
    this.authService.getRoles().subscribe((roles: string[]) => {
      this.isAdmin = roles.includes('ROLE_ADMIN');
      this.isUser = roles.includes('ROLE_USER');
    });
  }

  toggleAdminMenu() {
    this.adminMenuVisible = !this.adminMenuVisible;
  }

  @HostListener('window:scroll', [])
  onWindowScroll(): void {
    const currentScrollTop =
      window.scrollY || document.documentElement.scrollTop;
    if (currentScrollTop > this.lastScrollTop && currentScrollTop > 100) {
      this.isHidden = true;
    } else {
      this.isHidden = false;
    }
    this.lastScrollTop = currentScrollTop;
  }

  cerrarSesion() {
    if (this.usuarioLogueado) {
      this.authService.logout();

      const subscription = this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
        if (!estado) {
          Swal.fire({
            title: 'Sesión cerrada con éxito',
            text: '¡Hasta la próxima!',
            icon: 'success',
            timer: 2000,
          });

          this.router.navigate(['/inicio']).then(() => {
            this.adminMenuVisible = false;
          });
          
          subscription.unsubscribe();
        }
      });
    } else {
      Swal.fire({
        title: 'Atención',
        text: 'No has iniciado sesión. No se puede cerrar la sesión.',
        icon: 'warning',
        timer: 2000,
      });
    }
  }  
}