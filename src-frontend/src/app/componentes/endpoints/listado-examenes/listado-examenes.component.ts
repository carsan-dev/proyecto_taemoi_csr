import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { calcularEdad } from '../../../utilities/calcular-edad';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-listado-examenes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './listado-examenes.component.html',
  styleUrl: './listado-examenes.component.scss'
})

export class ListadoExamenesComponent implements OnInit {
  alumnos: any[] = [];  // Lista de alumnos que obtenemos desde el backend
  alumnosFiltrados: any[] = [];  // Lista que se mostrará con el filtro aplicado
  deporteSeleccionado: string = '';  // Filtro de deporte seleccionado
  deportesDisponibles: string[] = ['Taekwondo', 'Kickboxing', 'Pilates'];  // Lista de deportes disponibles

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerAlumnosAptos();  // Cargar alumnos aptos al inicializar el componente
  }

  // Obtener todos los alumnos aptos para examen
  obtenerAlumnosAptos(): void {
    this.endpointsService.obtenerAlumnosAptosParaExamen().subscribe({
      next: (response) => {
        this.alumnos = response.map((alumno: any) => ({
          ...alumno,
          deportes: this.obtenerDeportesAlumno(alumno)  // Mapear a una lista de deportes
        }));

        this.alumnosFiltrados = [...this.alumnos];  // Inicialmente todos los alumnos se muestran
      },
      error: (error) => {
        console.error('Error al obtener los alumnos aptos para examen:', error);
      }
    });
  }

  // Obtener la lista de deportes en los que participa un alumno
  obtenerDeportesAlumno(alumno: any): string[] {
    const grupos = alumno.grupos || [];

    const deportes: string[] = [];
    const taekwondoGruposIds = [1, 2, 3, 4, 5, 6, 7];
    const kickboxingGrupoId = 9;
    const pilatesGrupoId = 8;

    // Verificamos en qué grupos está el alumno y añadimos los deportes correspondientes
    if (grupos.some((grupo: any) => taekwondoGruposIds.includes(grupo.id))) {
      deportes.push('Taekwondo');
    }
    if (grupos.some((grupo: any) => grupo.id === kickboxingGrupoId)) {
      deportes.push('Kickboxing');
    }
    if (grupos.some((grupo: any) => grupo.id === pilatesGrupoId)) {
      deportes.push('Pilates');
    }

    return deportes.length > 0 ? deportes : ['Desconocido'];
  }

  // Cambiar el filtro de deporte
  cambiarDeporte(deporte: string): void {
    this.deporteSeleccionado = deporte;
    this.filtrarAlumnosPorDeporte();  // Filtrar alumnos al cambiar el deporte
  }

  // Filtrar los alumnos según el deporte seleccionado
  filtrarAlumnosPorDeporte(): void {
    if (!this.deporteSeleccionado) {
      // Si no hay deporte seleccionado, mostrar todos los alumnos
      this.alumnosFiltrados = [...this.alumnos];
    } else {
      // Filtrar alumnos que practican el deporte seleccionado
      this.alumnosFiltrados = this.alumnos.filter((alumno: any) => 
        alumno.deportes.includes(this.deporteSeleccionado)
      );
    }
  }

  calcularEdad(fechaNacimiento: string): number {
    return calcularEdad(fechaNacimiento); // Utiliza la función de utilidad para calcular la edad
  }
}