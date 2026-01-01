import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LoginInterface } from '../../../interfaces/login-interface';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { environment } from '../../../../environments/environment';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';

@Component({
  selector: 'app-vista-login',
  standalone: true,
  imports: [FormsModule, CommonModule],
  templateUrl: './vista-login.component.html',
  styleUrls: ['./vista-login.component.scss'],
})
export class VistaLoginComponent implements OnInit {
  credenciales: LoginInterface = { email: '', contrasena: '', rememberMe: false };
  passwordFieldType: string = 'password';
  passwordToggleIcon: string = 'bi bi-eye-fill';
  nombreUsuario: string | null = '';
  mostrarRegistro: boolean = false;
  registro = {
    email: '',
    fechaNacimiento: '',
    contrasena: '',
    confirmarContrasena: '',
  };
  registroEnviando: boolean = false;
  registroMostrarContrasena: boolean = false;

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    // Check for OAuth2 error parameters
    this.route.queryParams.subscribe(params => {
      if (params['error']) {
        const errorType = params['error'];
        const errorMessage = params['message'] || 'Error desconocido';

        if (errorType === 'no_alumno_found') {
          Swal.fire({
            title: 'No se pudo iniciar sesión',
            text: errorMessage,
            icon: 'error',
            confirmButtonText: 'Entendido'
          });
        } else {
          Swal.fire({
            title: 'Error de autenticación',
            text: errorMessage,
            icon: 'error',
            confirmButtonText: 'Entendido'
          });
        }
      }
    });

    // Verificar si el usuario ya está autenticado
    if (this.authService.comprobarLogueado()) {
      if (this.authService.rolesEstanCargados()) {
        const roles = this.authService.getRolesActuales();
        this.authService.obtenerNombreUsuario().subscribe((nombreUsuario) => {
          if (nombreUsuario) {  // Evitar mostrar "null"
            Swal.fire({
              title: 'Atención',
              text: `Ya estás logueado, ${nombreUsuario}`,
              icon: 'warning',
              timer: 2000,
            });
          }
          this.redirigirSegunRol(roles);
        });
      } else {
        this.authService.obtenerRoles().subscribe((roles) => {
          this.authService.obtenerNombreUsuario().subscribe((nombreUsuario) => {
            if (nombreUsuario) {  // Evitar mostrar "null"
              Swal.fire({
              title: 'Atención',
              text: `Ya estás logueado, ${nombreUsuario}`,
                icon: 'warning',
                timer: 2000,
              });
            }
            this.redirigirSegunRol(roles);
          });
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

  get registroTipoContrasena(): string {
    return this.registroMostrarContrasena ? 'text' : 'password';
  }

  get registroPasswordToggleIcon(): string {
    return this.registroMostrarContrasena ? 'bi bi-eye-slash-fill' : 'bi bi-eye-fill';
  }

  get registroTieneMinimo(): boolean {
    return this.registro.contrasena.length >= 8;
  }

  get registroTieneMayuscula(): boolean {
    return /[A-Z]/.test(this.registro.contrasena);
  }

  get registroTieneMinuscula(): boolean {
    return /[a-z]/.test(this.registro.contrasena);
  }

  get registroTieneNumero(): boolean {
    return /\d/.test(this.registro.contrasena);
  }

  get registroContrasenaValida(): boolean {
    return (
      this.registroTieneMinimo &&
      this.registroTieneMayuscula &&
      this.registroTieneMinuscula &&
      this.registroTieneNumero
    );
  }

  get registroContrasenasCoinciden(): boolean {
    return (
      this.registro.contrasena.length > 0 &&
      this.registro.contrasena === this.registro.confirmarContrasena
    );
  }

  get registroPuedeEnviar(): boolean {
    return (
      !this.registroEnviando &&
      !!this.registro.email &&
      !!this.registro.fechaNacimiento &&
      this.registroContrasenaValida &&
      this.registroContrasenasCoinciden
    );
  }

  toggleRegistroPasswordVisibility(): void {
    this.registroMostrarContrasena = !this.registroMostrarContrasena;
  }

  toggleRegistro(): void {
    this.mostrarRegistro = !this.mostrarRegistro;
  }

  login() {
    this.authService.login(this.credenciales).subscribe({
      next: (roles) => {
        this.authService.obtenerNombreUsuario().subscribe((nombreUsuario) => {
          if (nombreUsuario) {  // Asegurarse de que el nombre de usuario no es null
            Swal.fire({
              title: 'Inicio de sesión exitoso',
              text: `¡Bienvenido/a, ${nombreUsuario}!`,
              icon: 'success',
              timer: 2000,
            });

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
    });
  }

  solicitarRegistro(): void {
    if (this.registroEnviando) {
      return;
    }
    if (!this.registro.email || !this.registro.fechaNacimiento) {
      showErrorToast('Completa el correo y la fecha de nacimiento.');
      return;
    }
    if (!this.registroContrasenaValida) {
      showErrorToast('La contraseña debe tener mayúsculas, minúsculas y números (mínimo 8).');
      return;
    }
    if (!this.registroContrasenasCoinciden) {
      showErrorToast('Las contraseñas no coinciden.');
      return;
    }

    this.registroEnviando = true;
    const payload = {
      email: this.registro.email.trim(),
      fechaNacimiento: this.registro.fechaNacimiento,
      contrasena: this.registro.contrasena,
    };
    this.authService.solicitarRegistro(payload).subscribe({
      next: (response) => {
        this.registroEnviando = false;
        const mensaje = response?.mensaje || 'Si el correo existe, enviaremos un enlace de verificación.';
        showSuccessToast(mensaje);
        this.registro = { email: '', fechaNacimiento: '', contrasena: '', confirmarContrasena: '' };
        this.mostrarRegistro = false;
      },
      error: (error) => {
        this.registroEnviando = false;
        const mensaje = error?.error?.mensaje || 'No se pudo solicitar el registro.';
        showErrorToast(mensaje);
      },
    });
  }

  loginWithGoogle() {
    // Redirect to Google OAuth2 authorization endpoint
    this.setRememberMeCookie();
    const baseUrl = environment.apiUrl.replaceAll('/api', '');
    globalThis.location.href = `${baseUrl}/oauth2/authorization/google`;
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

  private setRememberMeCookie(): void {
    if (typeof document === 'undefined') {
      return;
    }
    const maxAge = this.credenciales.rememberMe ? 60 * 5 : 0;
    const value = this.credenciales.rememberMe ? 'true' : '';
    let cookie = `rememberMe=${value}; path=/; max-age=${maxAge}; SameSite=Lax`;
    if (globalThis.location?.protocol === 'https:') {
      cookie += '; Secure';
    }
    document.cookie = cookie;
  }
}
