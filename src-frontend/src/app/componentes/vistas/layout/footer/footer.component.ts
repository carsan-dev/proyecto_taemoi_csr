import { Component, EventEmitter, Output, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';

const ESTADO_VER_WEB = { verWeb: true };

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.scss'
})
export class FooterComponent {
  /** «Configurar cookies»: el skeleton vuelve a abrir el aviso de cookies (decisión 250). */
  @Output() readonly configurarCookies = new EventEmitter<void>();

  private readonly auth = inject(AuthenticationService);

  /**
   * «Inicio» manda `verWeb` solo con sesión del panel (gestión o solo profesor), para que la portada no la devuelva al
   * panel. Visitantes y familias: el enlace se comporta como siempre.
   */
  get estadoInicio(): { verWeb: boolean } | undefined {
    return this.auth.tieneAccesoAdmin() || this.auth.esSoloProfesor() ? ESTADO_VER_WEB : undefined;
  }
}
