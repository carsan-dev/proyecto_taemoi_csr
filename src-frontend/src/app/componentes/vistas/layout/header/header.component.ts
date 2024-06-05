import { Component, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { CommonModule } from '@angular/common';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
})
export class HeaderComponent implements OnInit {
  usuarioLogueado: boolean = false;

  constructor(
    private authService: AuthenticationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
  }

  irARuta(ruta: string) {
    this.router.navigate([ruta]);
  }

  cerrarSesion() {
    const token = localStorage.getItem('token');
    if (token) {
      this.authService.logout();
      Swal.fire({
        title: 'Sesión cerrada con éxito',
        text: '¡Hasta la proxima!',
        icon: 'success',
      });
    } else {
      Swal.fire({
        title: 'Atención',
        text: 'No has iniciado sesión. No se puede cerrar la sesión.',
        icon: 'warning',
      });
      this.usuarioLogueado = false;
    }
    this.router.navigate(['/inicio']);
  }
}
