import { Component, OnInit} from '@angular/core';
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
  usuarioLogueado: boolean = false;

  constructor(private authService: AuthenticationService, private router: Router) {}

  ngOnInit(): void {
    const token = localStorage.getItem('token');
    if (token) {
      const nombreUsuario = this.extraerNombreUsuario(this.credenciales.email);
      Swal.fire({
        title: 'Atención',
        text: `Ya estás logueado, ${nombreUsuario}`,
        icon: 'warning',
      });
      this.router.navigate(['/vistaprincipal']);
    } else {
      this.usuarioLogueado = this.authService.comprobarLogueado();
    }
  }

  login() {
    this.authService.login(this.credenciales).subscribe({
      next: (response) => {
        localStorage.setItem('token', response.token);
        this.usuarioLogueado = true;

        const nombreUsuario = this.extraerNombreUsuario(this.credenciales.email);
        Swal.fire({
          title: 'Inicio de sesión exitoso',
          text: `¡Bienvenido, ${nombreUsuario}!`,
          icon: 'success',
        });

        this.router.navigate(['/vistaprincipal']);
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

  private extraerNombreUsuario(email: string): string {
    return email.substring(0, email.indexOf('@'));
  }
}
