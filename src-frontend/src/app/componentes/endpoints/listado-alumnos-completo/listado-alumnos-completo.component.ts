import { Component } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-listado-alumnos-completo',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './listado-alumnos-completo.component.html',
  styleUrl: './listado-alumnos-completo.component.scss'
})
export class ListadoAlumnosCompletoDTOComponent {
  alumnos: any[] = [];

  constructor(private endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerAlumnos();
  }

  obtenerAlumnos() {
    const token = localStorage.getItem('token');

    if (token) {
      this.endpointsService.enviarToken(token).subscribe({
        next: (response) => {
          this.alumnos = response;
        },
        error: (error) => {
          console.log(error);
        },
      });
    }
  }
}
