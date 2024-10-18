import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-vista-principal-admin',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './vista-principal-admin.component.html',
  styleUrl: './vista-principal-admin.component.scss',
})
export class VistaPrincipalAdminComponent implements OnInit {
  nombreUsuario: string | null = '';

  constructor(private readonly authService: AuthenticationService) {}

  ngOnInit(): void {
    // Nos suscribimos al observable del nombre de usuario
    this.authService.obtenerNombreUsuario().subscribe((nombre) => {
      if (nombre) {
        this.nombreUsuario = nombre;
      } else {
        this.nombreUsuario = '';
      }
    });
  }
}
