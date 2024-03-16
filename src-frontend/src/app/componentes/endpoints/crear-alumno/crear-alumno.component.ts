import { Component } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TipoTarifa } from '../../../enums/tipo-tarifa';
import { SidebarComponent } from '../../vistas/layout/sidebar/sidebar.component';
import { Router } from '@angular/router';

@Component({
  selector: 'app-crear-alumno',
  standalone: true,
  imports: [FormsModule, CommonModule, SidebarComponent],
  templateUrl: './crear-alumno.component.html',
  styleUrl: './crear-alumno.component.scss',
})
export class CrearAlumnoComponent {
  nuevoAlumno: any = {
    tipoTarifa: null,
  };

  tiposTarifa = Object.values(TipoTarifa);

  constructor(private endpointsService: EndpointsService, private router: Router) {}

  crearAlumno() {
    const token = localStorage.getItem('token');

    if (!token) {
      Swal.fire({
        title: 'Error',
        text: 'No estás autorizado para realizar esta operación.',
        icon: 'error',
      });
      return;
    }

    this.endpointsService.crearAlumno(this.nuevoAlumno, token).subscribe({
      next: (response) => {
        Swal.fire({
          title: 'Perfecto!',
          text: 'Has creado un nuevo alumno',
          icon: 'success',
        });
        this.router.navigate(['/alumnos']);
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No has completado todos los campos requeridos',
          icon: 'error',
        });
      },
      complete: () => {},
    });
  }
}
