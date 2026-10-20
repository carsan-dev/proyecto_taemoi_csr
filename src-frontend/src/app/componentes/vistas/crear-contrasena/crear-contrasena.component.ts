import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AuthenticationService, DatosInvitacion } from '../../../servicios/authentication/authentication.service';
import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { showSuccessToast } from '../../../utils/toast.util';

const RUTA = '/crear-contrasena';
const ENLACE_NO_VALE = 'El enlace ha caducado o ya se usó. Pide que te lo reenvíen.';

type Estado = 'cargando' | 'listo' | 'caducado' | 'sinComprobar' | 'sinSesion';

/**
 * «Crea tu contraseña» (pieza E8-6, D8-13): la página del enlace de invitación. Reutiliza los estilos y las reglas
 * de contraseña de «Nueva contraseña» (reset-password). El token sale de la barra en cuanto se lee y, mientras dura la
 * página, el navegador no manda la URL como referrer (revisión F6).
 */
@Component({
  selector: 'app-crear-contrasena',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './crear-contrasena.component.html',
  styleUrls: ['../reset-password/reset-password.component.scss', './crear-contrasena.component.scss'],
})
export class CrearContrasenaComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly meta = inject(Meta);
  private readonly auth = inject(AuthenticationService);
  private readonly destroyRef = inject(DestroyRef);

  private token: string | null = null;
  readonly enlaceNoVale = ENLACE_NO_VALE;
  estado: Estado = 'cargando';
  datos: DatosInvitacion | null = null;
  mensajeSinSesion = '';
  errorEnvio: string | null = null;
  nuevaContrasena = '';
  confirmarContrasena = '';
  enviando = false;
  mostrarContrasena = false;

  ngOnInit(): void {
    this.meta.updateTag({ name: 'referrer', content: 'no-referrer' });
    this.token = this.route.snapshot.queryParamMap.get('token');
    // Fuera de la barra al momento y también del estado del router: una navegación cancelada no lo restaura (F2).
    this.location.replaceState(RUTA);
    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    if (!this.token) {
      this.estado = 'caducado';
      return;
    }
    this.comprobarEnlace();
  }

  /** Solo un 400 dice que el enlace no vale; un corte de red o un 5xx se puede reintentar (F4). */
  comprobarEnlace(): void {
    if (!this.token) {
      return;
    }
    this.estado = 'cargando';
    this.auth
      .datosInvitacion(this.token)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (datos) => {
          this.datos = datos;
          this.estado = 'listo';
        },
        error: (error: unknown) =>
          (this.estado = error instanceof HttpErrorResponse && error.status === 400 ? 'caducado' : 'sinComprobar'),
      });
  }

  ngOnDestroy(): void {
    this.meta.removeTag("name='referrer'");
  }

  get tipoContrasena(): string {
    return this.mostrarContrasena ? 'text' : 'password';
  }

  get tieneMinimo(): boolean {
    return this.nuevaContrasena.length >= 8;
  }

  get tieneMayuscula(): boolean {
    return /[A-Z]/.test(this.nuevaContrasena);
  }

  get tieneMinuscula(): boolean {
    return /[a-z]/.test(this.nuevaContrasena);
  }

  get tieneNumero(): boolean {
    return /\d/.test(this.nuevaContrasena);
  }

  get contrasenaValida(): boolean {
    return this.tieneMinimo && this.tieneMayuscula && this.tieneMinuscula && this.tieneNumero;
  }

  get contrasenasCoinciden(): boolean {
    return this.nuevaContrasena.length > 0 && this.nuevaContrasena === this.confirmarContrasena;
  }

  get puedeEnviar(): boolean {
    return this.estado === 'listo' && !this.enviando && this.contrasenaValida && this.contrasenasCoinciden;
  }

  toggleMostrarContrasena(): void {
    this.mostrarContrasena = !this.mostrarContrasena;
  }

  crearContrasena(): void {
    if (!this.puedeEnviar || !this.token || !this.datos) {
      return;
    }
    this.enviando = true;
    this.errorEnvio = null;
    this.auth
      .aceptarInvitacion(this.token, this.nuevaContrasena, this.datos.email)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (roles) => {
          this.enviando = false;
          showSuccessToast('Contraseña creada');
          this.router.navigate([this.auth.resolverRutaInicioPorRoles(roles)]);
        },
        error: (error: unknown) => {
          this.enviando = false;
          const status = error instanceof HttpErrorResponse ? error.status : 0;
          if (status === 400) {
            this.estado = 'caducado';
          } else if (status === 403) {
            this.mensajeSinSesion = mensajeDeError(error) ?? 'Tu contraseña está creada, pero aún no puedes entrar.';
            this.estado = 'sinSesion';
          } else {
            this.errorEnvio = mensajeDeError(error) ?? 'No se ha podido crear la contraseña. Inténtalo de nuevo.';
          }
        },
      });
  }

  irAlLogin(): void {
    this.router.navigate(['/login']);
  }
}
