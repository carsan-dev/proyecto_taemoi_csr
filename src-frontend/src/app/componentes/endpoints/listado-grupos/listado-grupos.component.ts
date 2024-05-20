import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-listado-grupos',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './listado-grupos.component.html',
  styleUrl: './listado-grupos.component.scss'
})
export class ListadoGruposComponent implements OnInit {
  grupos: any[] = [];

  constructor(private endpointsService: EndpointsService) { }

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      this.obtenerGrupos();
    }
  }

  obtenerGrupos(): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService.obtenerTodosLosGrupos(token).subscribe({
        next: (response) => {
          this.grupos = response;
        },
        error: (error) => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
    }
  }
}
