import { Component, HostListener, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
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
    // Escuchar cambios en el estado de usuario logueado
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;

      if (estado) {
        this.comprobarRoles(); // Verificar roles si el usuario está logueado
      }
    });

    // Suscribirse a los cambios en el nombre de usuario
    this.authService.usernameCambio.subscribe((username: string | null) => {
      if (username) {
        this.username = username; // Actualizar el nombre de usuario
      } else {
        this.username = null; // Limpiar el nombre de usuario si no existe
      }
    });

    // Comprobar si ya está logueado al cargar el componente
    if (this.authService.comprobarLogueado()) {
      this.usuarioLogueado = true;
      this.comprobarRoles();
    }
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
  
      // Escuchar un solo cambio después de logout
      const subscription = this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
        if (!estado) {
          Swal.fire({
            title: 'Sesión cerrada con éxito',
            text: '¡Hasta la próxima!',
            icon: 'success',
          });
  
          // Redirigir al usuario después de cerrar sesión
          this.router.navigate(['/inicio']).then(() => {
            this.adminMenuVisible = false;
          });
  
          // Cancelar la suscripción después del logout
          subscription.unsubscribe();
        }
      });
    } else {
      Swal.fire({
        title: 'Atención',
        text: 'No has iniciado sesión. No se puede cerrar la sesión.',
        icon: 'warning',
      });
    }
  }  
}