import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';

@Component({
  selector: 'app-escaparate-principal',
  standalone: true,
  imports: [],
  templateUrl: './escaparate-principal.component.html',
  styleUrl: './escaparate-principal.component.scss'
})
export class EscaparatePrincipalComponent implements OnInit {
  usuarioLogueado: boolean = false;

  constructor(private authService: AuthenticationService) {}

  ngOnInit(): void {
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
  }
}
