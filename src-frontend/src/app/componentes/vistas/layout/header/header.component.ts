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
  adminMenuVisible: boolean = false;
  isAdmin: boolean = false;
  isManager: boolean = false;
  isUser: boolean = false;
  isHidden: boolean = false;
  private lastScrollTop: number = 0;

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
    this.authService.isAdminCambio.subscribe((isAdmin: boolean) => {
      this.isAdmin = isAdmin;
    });
    this.authService.isManagerCambio.subscribe((isManager: boolean) => {
      this.isManager = isManager;
    });
    this.authService.isUserCambio.subscribe((isUser: boolean) => {
      this.isUser = isUser;
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
      Swal.fire({
        title: 'Sesión cerrada con éxito',
        text: '¡Hasta la próxima!',
        icon: 'success',
      });
    } else {
      Swal.fire({
        title: 'Atención',
        text: 'No has iniciado sesión. No se puede cerrar la sesión.',
        icon: 'warning',
      });
    }
    this.router.navigate(['/inicio']).then(() => {
      this.adminMenuVisible = false;
    });
  }
}
