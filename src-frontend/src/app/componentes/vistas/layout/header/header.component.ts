import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { CommonModule } from '@angular/common';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
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
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
  }

  irAVistaAdmin() {
    this.router.navigate(['/vista-principal']);
  }

  irAVistaPrincipal() {
    this.router.navigate(['']);
  }

  irALogin() {
    this.router.navigate(['/login']);
  }

  cerrarSesion() {
    const token = localStorage.getItem('token');
    if (token) {
      localStorage.removeItem('token');
      this.usuarioLogueado = false;
      Swal.fire({
        title: 'Sesión cerrada con éxito',
        text: 'Hasta la proxima!',
        icon: 'success',
      });
    }
    else {
      Swal.fire({
        title: 'Atención',
        text: 'No has iniciado sesión. No se puede cerrar la sesión.',
        icon: 'warning'
      });
      this.usuarioLogueado = false;
    }
    this.router.navigate(['/login']);
  }
}
