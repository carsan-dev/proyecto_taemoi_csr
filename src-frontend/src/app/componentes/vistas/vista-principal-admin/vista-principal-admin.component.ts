import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { ActivatedRoute, RouterLink } from '@angular/router';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-vista-principal-admin',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './vista-principal-admin.component.html',
  styleUrl: './vista-principal-admin.component.scss',
})
export class VistaPrincipalAdminComponent implements OnInit {
  nombreUsuario: string | null = '';
  usuarioLogueado: boolean = false;

  // Array simple sin interfaz
  seccionesAdmin = [
    {
      titulo: 'Alumnos',
      descripcion: 'Gestiona información de los alumnos',
      ruta: '/alumnosListar',
      icono: 'bi bi-people',
    },
    {
      titulo: 'Grupos',
      descripcion: 'Organiza y administra grupos',
      ruta: '/gruposListar',
      icono: 'bi bi-people-fill',
    },
    {
      titulo: 'Turnos',
      descripcion: 'Controla y edita turnos disponibles',
      ruta: '/turnosListar',
      icono: 'bi bi-calendar3',
    },
    {
      titulo: 'Eventos',
      descripcion: 'Crea y administra eventos especiales',
      ruta: '/eventosListar',
      icono: 'bi bi-calendar-check',
    },
    {
      titulo: 'Productos',
      descripcion: 'Gestión de productos y servicios',
      ruta: '/productosListar',
      icono: 'bi bi-box-seam',
    },
    {
      titulo: 'Convocatorias',
      descripcion: 'Publica y gestiona convocatorias',
      ruta: '/convocatoriasListar',
      icono: 'bi bi-clipboard-check',
    },
  ];

  constructor(
    private readonly authService: AuthenticationService,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.authService.obtenerNombreUsuario().subscribe((nombre) => {
      this.nombreUsuario = nombre ?? '';

      // Check if this is a fresh OAuth2 login (coming from backend redirect)
      // We check if user just logged in by verifying URL doesn't have any prior navigation
      if (nombre && !sessionStorage.getItem('welcomeShown')) {
        Swal.fire({
          title: 'Inicio de sesión exitoso',
          text: `¡Bienvenido/a, ${nombre}!`,
          icon: 'success',
          timer: 2000,
        });
        sessionStorage.setItem('welcomeShown', 'true');
      }
    });
  }
}
