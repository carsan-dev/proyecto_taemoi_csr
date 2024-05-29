import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-vista-principal-admin',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './vista-principal-admin.component.html',
  styleUrl: './vista-principal-admin.component.scss',
})
export class VistaPrincipalAdminComponent implements OnInit {
  nombreUsuario: string | null = '';

  constructor(
    private authService: AuthenticationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (
      !this.authService.tieneRolAdmin() &&
      !this.authService.tieneRolManager()
    ) {
      this.router.navigate(['/inicio']);
    } else {
      this.nombreUsuario = this.authService.obtenerNombreUsuario();
    }
  }
}
