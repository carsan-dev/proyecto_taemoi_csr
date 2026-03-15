import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reset-password.component.html',
  styleUrls: ['./reset-password.component.scss']
})
export class ResetPasswordComponent implements OnInit {
  token: string | null = null;
  nuevaContrasena: string = '';
  confirmarContrasena: string = '';
  enviando: boolean = false;
  mostrarContrasena: boolean = false;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
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
    return !!this.token && !this.enviando && this.contrasenaValida && this.contrasenasCoinciden;
  }

  toggleMostrarContrasena(): void {
    this.mostrarContrasena = !this.mostrarContrasena;
  }

  actualizarContrasena(): void {
    if (!this.token) {
      showErrorToast('Token inválido o caducado.');
      return;
    }
    if (!this.contrasenaValida) {
      showErrorToast('La contraseña debe tener mayúsculas, minúsculas y números (mínimo 8).');
      return;
    }
    if (this.nuevaContrasena !== this.confirmarContrasena) {
      showErrorToast('Las contraseñas no coinciden.');
      return;
    }
    if (this.enviando) {
      return;
    }

    this.enviando = true;
    this.authService.resetearContrasena(this.token, this.nuevaContrasena).subscribe({
      next: () => {
        this.enviando = false;
        showSuccessToast('Contraseña actualizada. Ya puedes iniciar sesión.');
        this.router.navigate(['/login']);
      },
      error: (error) => {
        this.enviando = false;
        const mensaje = error?.error?.message || 'No se pudo actualizar la contraseña.';
        showErrorToast(mensaje);
      },
    });
  }

  volverAlLogin(): void {
    this.router.navigate(['/login']);
  }
}
