import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-vista-principal-user',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './vista-principal-user.component.html',
  styleUrl: './vista-principal-user.component.scss',
})
export class VistaPrincipalUserComponent implements OnInit {
  grupos: any[] = [];

  constructor(
    private endpointsService: EndpointsService,
    private authService: AuthenticationService
  ) {}

  ngOnInit() {
    if (typeof localStorage !== 'undefined') {
      const token = localStorage.getItem('token');
      if (token) {
        this.authService.obtenerUsuarioAutenticado(token).subscribe({
          next: (usuario) => {
            if (usuario && usuario.alumnoDTO) {
              const alumnoId = usuario.alumnoDTO.id;
              this.cargarGruposDelAlumno(alumnoId);
            } else {
              Swal.fire({
                title: 'Error',
                text: 'El usuario no tiene alumno asociado.',
                icon: 'error',
              });
            }
          },
          error: (error) => {
            Swal.fire({
              title: 'Error en la petición',
              text: 'Error al obtener el usuario.',
              icon: 'error',
            });
          },
        });
      }
    }
  }

  cargarGruposDelAlumno(alumnoId: number) {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService.obtenerGruposDelAlumno(alumnoId, token).subscribe({
        next: (grupos) => {
          this.grupos = grupos;
        },
        error: (error) => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'Error al obtener los grupos del alumno.',
            icon: 'error',
          });
        },
      });
    }
  }
}
