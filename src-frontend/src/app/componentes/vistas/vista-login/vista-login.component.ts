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
  styleUrls: ['./vista-login.component.scss'],
})
export class VistaLoginComponent implements OnInit {
  credenciales: LoginInterface = { email: '', contrasena: '' };
  passwordFieldType: string = 'password';
  passwordToggleIcon: string = 'bi bi-eye-fill';

  constructor(
    private authService: AuthenticationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    // Verificar si el usuario ya está autenticado
    if (this.authService.comprobarLogueado()) {
      // Verificar si los roles ya están cargados
      if (this.authService.rolesEstanCargados()) {
        const roles = this.authService.getRolesActuales();
        const nombreUsuario = this.authService.obtenerNombreUsuario();
        Swal.fire({
          title: 'Atención',
          text: `Ya estás logueado, ${nombreUsuario}`,
          icon: 'warning',
        });
        this.redirigirSegunRol(roles);
      } else {
        // Obtener los roles si aún no están cargados
        this.authService.obtenerRoles().subscribe((roles) => {
          const nombreUsuario = this.authService.obtenerNombreUsuario();
          Swal.fire({
            title: 'Atención',
            text: `Ya estás logueado, ${nombreUsuario}`,
            icon: 'warning',
          });
          this.redirigirSegunRol(roles);
        });
      }
    }
  }

  togglePasswordVisibility(): void {
    if (this.passwordFieldType === 'password') {
      this.passwordFieldType = 'text';
      this.passwordToggleIcon = 'bi bi-eye-slash-fill';
    } else {
      this.passwordFieldType = 'password';
      this.passwordToggleIcon = 'bi bi-eye-fill';
    }
  }

  login() {
    this.authService.login(this.credenciales).subscribe({
      next: (roles) => {
        const nombreUsuario = this.authService.obtenerNombreUsuario();

        Swal.fire({
          title: 'Inicio de sesión exitoso',
          text: `¡Bienvenido/a, ${nombreUsuario}!`,
          icon: 'success',
        });

        this.redirigirSegunRol(roles);
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido conectar con el servidor',
          icon: 'error',
        });
      },
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
