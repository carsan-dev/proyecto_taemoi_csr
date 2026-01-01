import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { showErrorToast } from '../../../utils/toast.util';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-recuperar-contrasena',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './recuperar-contrasena.component.html',
  styleUrls: ['./recuperar-contrasena.component.scss']
})
export class RecuperarContrasenaComponent {
  email: string = '';
  enviando: boolean = false;

  constructor(
    private readonly authService: AuthenticationService,
    private readonly router: Router
  ) {}

  enviarSolicitud(): void {
    if (!this.email || this.enviando) {
      return;
    }
    this.enviando = true;
    this.authService.solicitarResetContrasena(this.email).subscribe({
      next: () => {
        this.enviando = false;
        Swal.fire({
          title: 'Solicitud enviada',
          text: 'Si el correo existe, enviaremos un enlace para cambiar la contraseña.',
          icon: 'info',
          confirmButtonText: 'Entendido'
        });
        this.email = '';
      },
      error: () => {
        this.enviando = false;
        showErrorToast('No se pudo enviar el correo de recuperación.');
      },
    });
  }

  volverAlLogin(): void {
    this.router.navigate(['/login']);
  }
}
