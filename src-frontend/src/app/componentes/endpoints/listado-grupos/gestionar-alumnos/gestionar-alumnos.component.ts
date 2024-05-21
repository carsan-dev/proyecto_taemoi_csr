import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { AlumnoDTO } from '../../../../interfaces/alumno-dto';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { GrupoDTO } from '../../../../interfaces/grupo-dto';

@Component({
  selector: 'app-gestionar-alumnos',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './gestionar-alumnos.component.html',
  styleUrl: './gestionar-alumnos.component.scss',
})
export class GestionarAlumnosComponent implements OnInit {
  grupo!: GrupoDTO;
  grupoId!: number;
  alumnos: AlumnoDTO[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private endpointsService: EndpointsService
  ) {}

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      this.route.params.subscribe((params) => {
        this.grupoId = +params['id'];
        this.cargarGrupo();
      });
    }
  }

  cargarGrupo(): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService.obtenerGrupoPorId(this.grupoId, token).subscribe({
        next: (grupo: GrupoDTO) => {
          this.grupo = grupo;
          this.alumnos = grupo.alumnos;
        },
        error: () => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
    }
  }

  redirigirSeleccionarAlumnos(): void {
    this.router.navigate(['/seleccionarAlumnos', this.grupoId]);
  }

  eliminarAlumno(alumnoId: number): void {
    const token = localStorage.getItem('token');
    if (token) {
      Swal.fire({
        title: '¿Estás seguro?',
        text: 'No podrás revertir esta acción',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
        confirmButtonText: 'Sí, eliminarlo',
      }).then((result) => {
        if (result.isConfirmed) {
          this.endpointsService
            .eliminarAlumnoDeGrupo(this.grupoId, alumnoId, token)
            .subscribe({
              next: () => {
                Swal.fire(
                  'Eliminado',
                  'Alumno correctamente eliminado del grupo!',
                  'success'
                );
                this.alumnos = this.alumnos.filter(
                  (alumno) => alumno.id !== alumnoId
                );
              },
              error: () => {
                Swal.fire({
                  title: 'Error al eliminar alumno',
                  text: 'No hemos podido eliminar el alumno',
                  icon: 'error',
                });
              },
            });
        }
      });
    }
  }
}
