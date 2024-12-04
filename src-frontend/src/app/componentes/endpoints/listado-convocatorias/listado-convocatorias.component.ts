import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-listado-convocatorias',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './listado-convocatorias.component.html',
  styleUrl: './listado-convocatorias.component.scss',
})
export class ListadoConvocatoriasComponent implements OnInit {
  convocatorias: any[] = [];
  convocatoriaSeleccionada: any;
  deportes = ['TAEKWONDO', 'KICKBOXING'];
  deporteSeleccionado = 'TAEKWONDO';

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.obtenerConvocatorias();
  }

  obtenerConvocatorias(): void {
    this.endpointsService
      .obtenerConvocatorias(this.deporteSeleccionado)
      .subscribe({
        next: (data) => {
          this.convocatorias = data;
        },
        error: (error) => {
          console.error('Error al obtener las convocatorias:', error);
        },
      });
  }

  crearConvocatoria(): void {
    const nuevaConvocatoria = {
      fechaConvocatoria: new Date(),
      deporte: this.deporteSeleccionado,
    };
    this.endpointsService.crearConvocatoria(nuevaConvocatoria).subscribe({
      next: (data) => {
        this.convocatorias.push(data);
      },
      error: (error) => {
        console.error('Error al crear la convocatoria:', error);
      },
    });
  }

  seleccionarConvocatoria(convocatoria: any): void {
    this.convocatoriaSeleccionada = convocatoria;
  }
}
