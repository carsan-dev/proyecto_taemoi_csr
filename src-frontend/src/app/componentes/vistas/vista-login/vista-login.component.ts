import { Component, Injector, OnDestroy, OnInit, Optional, signal } from '@angular/core';
import { catchError, from, of, switchMap } from 'rxjs';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LoginInterface } from '../../../interfaces/login-interface';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { environment } from '../../../../environments/environment';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { Subscription } from 'rxjs';
import { MARCA_LEGADO, MarcaEscuelaLoginService, MarcaLogin } from './marca-escuela-login.service';

/** Si la marca de la escuela no llega en este tiempo, el login pinta la de siempre (SVG legado). */
export const ESPERA_MARCA_MS = 3000;

@Component({
  selector: 'app-vista-login',
  standalone: true,
  imports: [FormsModule, CommonModule],
  templateUrl: './vista-login.component.html',
  styleUrls: ['./vista-login.component.scss'],
})
export class VistaLoginComponent implements OnInit, OnDestroy {
  /** Zona de marca (fase 4, pieza 5): mientras llega la escuela del Host, el hueco del logo de siempre, invisible. */
  readonly marca = signal<MarcaLogin>({ tipo: 'pendiente' });
  private marcaSuscripcion: Subscription | null = null;
  private marcaEspera: ReturnType<typeof setTimeout> | null = null;
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
    private readonly route: ActivatedRoute,
    private readonly marcaEscuela: MarcaEscuelaLoginService,
    @Optional() private readonly injector?: Injector
  ) {}

  ngOnInit(): void {
    this.cargarMarca();

    // Check for OAuth2 error parameters
    this.route.queryParams.subscribe(params => {
      if (params['modo'] === 'registro') {
        this.mostrarRegistro = true;
        if (typeof params['email'] === 'string') {
          this.registro.email = params['email'].trim().toLowerCase();
        }
      }
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

  ngOnDestroy(): void {
    this.terminarEsperaMarca();
    this.marcaSuscripcion?.unsubscribe();
  }

  /** Color de la escuela para su zona de marca (solo `SUBIDO` y `NINGUNO`); null deja el degradado de siempre. */
  colorMarca(campo: 'primario' | 'sobrePrimario'): string | null {
    const marca = this.marca();
    const color = marca.tipo === 'subido' || marca.tipo === 'ninguno' ? marca.tema?.[campo] : null;
    return color && /^#[0-9A-Fa-f]{6}$/.test(color) ? color : null;
  }

  nombreEscuela(): string {
    const marca = this.marca();
    return marca.tipo === 'subido' || marca.tipo === 'ninguno' ? marca.nombre : '';
  }

  inicialesEscuela(): string {
    const marca = this.marca();
    return marca.tipo === 'ninguno' ? marca.iniciales : '';
  }

  urlLogoSubido(): string | null {
    const marca = this.marca();
    return marca.tipo === 'subido' ? marca.url : null;
  }

  /** El logo subido no se pudo cargar: el nombre de la escuela en texto, nunca el logo de Moiskimdo. */
  logoSubidoFallo(): void {
    const marca = this.marca();
    if (marca.tipo === 'subido') {
      this.marca.set(this.marcaEscuela.marcaDe({ nombre: marca.nombre, logo: 'NINGUNO', logoVersion: null, tema: marca.tema }));
    }
  }

  /**
   * Pide la escuela del Host. Si falla o tarda más de {@link ESPERA_MARCA_MS}, la marca de siempre; una respuesta
   * tardía todavía se aplica.
   */
  private cargarMarca(): void {
    this.marcaEspera = setTimeout(() => {
      this.marcaEspera = null;
      if (this.marca().tipo === 'pendiente') {
        this.marca.set(MARCA_LEGADO);
      }
    }, ESPERA_MARCA_MS);
    this.marcaSuscripcion = this.marcaEscuela.escuela().subscribe({
      next: (escuela) => {
        this.terminarEsperaMarca();
        this.marca.set(this.marcaEscuela.marcaDe(escuela));
      },
      error: () => {
        this.terminarEsperaMarca();
        this.marca.set(MARCA_LEGADO);
      },
    });
  }

  private terminarEsperaMarca(): void {
    if (this.marcaEspera !== null) {
      clearTimeout(this.marcaEspera);
      this.marcaEspera = null;
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

            this.redirigirTrasEntrar(roles);
          }
        });
      },
      error: (error) => {
        const { title, message } = this.obtenerErrorLogin(error);
        Swal.fire({
          title,
          text: message,
          icon: 'error',
        });
      },
    });
  }

  solicitarRegistro(): void {
    if (this.registroEnviando) {
      return;
    }
    const fechaNacimiento = this.normalizarFechaNacimiento(this.registro.fechaNacimiento);
    if (!this.registro.email || !fechaNacimiento) {
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
      fechaNacimiento,
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
    this.authService.resolverRutaInicio(roles).subscribe((rutaDestino) => this.router.navigate([rutaDestino]));
  }

  /**
   * Tras entrar con contraseña, el selector de escuela (en diferido, fuera del bundle inicial) decide si antes se pasa
   * por «Tras entrar» (dos escuelas o más). Si no se puede cargar o falla, el destino de siempre.
   */
  private redirigirTrasEntrar(roles: string[]) {
    this.authService
      .resolverRutaInicio(roles)
      .pipe(
        switchMap((ruta) =>
          from(import('../../../panel-nuevo/selector-escuela/selector-escuela.service')).pipe(
            switchMap(({ SelectorEscuelaService }) =>
              this.injector!.get(SelectorEscuelaService).destinoTrasEntrar(ruta)
            ),
            catchError(() => of(ruta))
          )
        )
      )
      .subscribe((rutaDestino) => this.router.navigate([rutaDestino]));
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

  private obtenerErrorLogin(error: any): { title: string; message: string } {
    const status = error?.status;
    const backendMessage =
      error?.error?.mensaje ||
      error?.error?.message ||
      error?.error?.error;

    if (status === 0) {
      return {
        title: 'Error de conexion',
        message: 'No hemos podido conectar con el servidor.',
      };
    }
    if (status === 403) {
      const normalizedMessage =
        typeof backendMessage === 'string' ? backendMessage.toLowerCase() : '';

      if (normalizedMessage.includes('alumnos activos')) {
        return {
          title: 'Cuenta inactiva',
          message:
            backendMessage ||
            'Tu cuenta no tiene alumnos activos asociados. Contacta con administracion.',
        };
      }

      return {
        title: 'Acceso denegado',
        message: backendMessage || 'No tienes permisos para iniciar sesion.',
      };
    }
    if (status === 401 || status === 404) {
      return {
        title: 'Credenciales incorrectas',
        message: 'El correo o la contraseña no son correctos.',
      };
    }
    // Límite de intentos de nginx: su cuerpo es HTML, no un mensaje del servidor.
    if (status === 429) {
      return {
        title: 'Demasiados intentos',
        message: 'Espera un minuto y vuelve a intentarlo.',
      };
    }
    return {
      title: 'No se pudo iniciar sesion',
      message: backendMessage || 'Revisa tus datos e intentalo de nuevo.',
    };
  }

  private normalizarFechaNacimiento(valor: string | Date): string {
    if (!valor) {
      return '';
    }
    if (valor instanceof Date) {
      if (Number.isNaN(valor.getTime())) {
        return '';
      }
      return valor.toISOString().slice(0, 10);
    }
    let texto = valor.toString().trim();
    if (!texto) {
      return '';
    }
    if (texto.startsWith('"') && texto.endsWith('"') && texto.length > 1) {
      texto = texto.slice(1, -1);
    }
    const indexT = texto.indexOf('T');
    return indexT > 0 ? texto.slice(0, indexT) : texto;
  }
}
