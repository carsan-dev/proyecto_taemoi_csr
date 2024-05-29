import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LoginInterface } from '../../../interfaces/login-interface';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-vista-login',
  standalone: true,
  imports: [FormsModule, CommonModule],
  templateUrl: './vista-login.component.html',
  styleUrl: './vista-login.component.scss',
})
export class VistaLoginComponent implements OnInit {
  credenciales: LoginInterface = { email: '', contrasena: '' };

  constructor(
    private authService: AuthenticationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      const token = localStorage.getItem('token');
      if (token) {
        this.authService.obtenerRoles(token);
        this.authService.rolesCambio.subscribe((roles) => {
          if (roles.length > 0) {
            const nombreUsuario = this.authService.obtenerNombreUsuario();
            Swal.fire({
              title: 'Atención',
              text: `Ya estás logueado, ${nombreUsuario}`,
              icon: 'warning',
            });
            this.redirigirSegunRol(roles);
          }
        });
      }
    }
  }

  login() {
    this.authService.login(this.credenciales).subscribe({
      next: (response) => {
        const token = response.token;
        const nombreUsuario = this.authService.obtenerNombreUsuario();
        localStorage.setItem('token', token);
        localStorage.setItem('username', nombreUsuario ?? '');
        this.authService.actualizarEstadoLogueado(true);
        this.authService.obtenerRoles(token);

        Swal.fire({
          title: 'Inicio de sesión exitoso',
          text: `¡Bienvenido/a, ${nombreUsuario}!`,
          icon: 'success',
        });

        this.authService.rolesCambio.subscribe((roles) => {
          if (roles.length > 0) {
            this.redirigirSegunRol(roles);
          }
        });
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido conectar con el servidor',
          icon: 'error',
        });
      },
      complete: () => {},
    });
  }

  private redirigirSegunRol(roles: string[]) {
    if (
      this.authService.tieneRolAdmin() ||
      this.authService.tieneRolManager()
    ) {
      this.router.navigate(['/adminpage']);
    } else if (this.authService.tieneRolUser()) {
      this.router.navigate(['/userpage']);
    } else {
      this.router.navigate(['/inicio']);
    }
  }
}
