import { Component, OnInit } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';

const RUTA = '/registro-confirmar';

@Component({
  selector: 'app-registro-confirmar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './registro-confirmar.component.html',
  styleUrls: ['./registro-confirmar.component.scss']
})
export class RegistroConfirmarComponent implements OnInit {
  estado: 'loading' | 'success' | 'error' | 'invalid' = 'loading';
  mensajeError: string = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly authService: AuthenticationService,
    private readonly location: Location
  ) {}

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    // Fuera de la barra al momento y también del estado del router, como en «Nueva contraseña»: los eventos
    // automáticos de la analítica toman la URL del documento.
    this.location.replaceState(RUTA);
    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    if (!token) {
      this.estado = 'invalid';
      this.mensajeError = 'El enlace no es válido o ha caducado.';
      return;
    }

    this.estado = 'loading';
    this.authService.confirmarRegistro(token).subscribe({
      next: () => {
        this.estado = 'success';
        showSuccessToast('Cuenta confirmada. Ya puedes iniciar sesión.');
      },
      error: (error) => {
        this.estado = 'error';
        this.mensajeError = error?.error?.mensaje || 'No se pudo confirmar el registro.';
        showErrorToast(this.mensajeError);
      },
    });
  }

  volverAlLogin(): void {
    this.router.navigate(['/login']);
  }
}
