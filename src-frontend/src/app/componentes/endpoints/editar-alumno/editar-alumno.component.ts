import { Component, ElementRef, ViewChild } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import Swal from 'sweetalert2';
import { FormsModule } from '@angular/forms';
import { TipoTarifa } from '../../../enums/tipo-tarifa';
import { TipoGrado } from '../../../enums/tipo-grado';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';

@Component({
  selector: 'app-editar-alumno',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginacionComponent],
  templateUrl: './editar-alumno.component.html',
  styleUrl: './editar-alumno.component.scss',
})
export class EditarAlumnoComponent {
  alumnos: any[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 1;
  totalPaginas: number = 0;
  mostrarPaginas: number[] = [];
  mostrarFormulario: boolean = false;
  nombreFiltro: string = '';
  alumnoEditado: any = {
    tipoTarifa: null,
    tipoGrado: null,
  };

  tiposTarifa = Object.values(TipoTarifa);
  tiposGrado = Object.values(TipoGrado);
  @ViewChild('inputFile', { static: false }) inputFile!: ElementRef;
  imagenPreview: string | null = null;

  constructor(private endpointsService: EndpointsService) {}

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      this.obtenerAlumnos();
    }
  }

  obtenerAlumnos() {
    const token = localStorage.getItem('token');

    if (token) {
      this.endpointsService
        .obtenerAlumnos(
          token,
          this.paginaActual,
          this.tamanoPagina,
          this.nombreFiltro
        )
        .subscribe({
          next: (response) => {
            this.alumnos = response.content;
            this.totalPaginas = response.totalPages;
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

  actualizarAlumno(id: number) {
    const token = localStorage.getItem('token');

    const formData = new FormData();
    formData.append('alumnoEditado', JSON.stringify(this.alumnoEditado));

    if (this.alumnoEditado.fotoAlumno === null) {
      formData.append('file', 'null');
    } else if (this.alumnoEditado.fotoAlumno) {
      formData.append('file', this.alumnoEditado.fotoAlumno);
    }

    if (token) {
      this.endpointsService.actualizarAlumno(id, formData, token).subscribe({
        next: (response) => {
          Swal.fire({
            title: '¡Bien!',
            text: '¡Alumno actualizado correctamente!',
            icon: 'success',
          });
          this.obtenerAlumnos();
        },
        error: (error) => {
          Swal.fire({
            title: 'Error al actualizar',
            text: 'Error al actualizar al alumno',
            icon: 'error',
          });
        },
      });
    }
  }

  eliminarFoto(id: number) {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService.eliminarImagenAlumno(id, token).subscribe({
        next: (response) => {
          this.inputFile.nativeElement.value = '';
          this.alumnoEditado.fotoAlumno = null;
          this.imagenPreview = 'assets/media/default.webp';
          this.obtenerAlumnos();
        },

        error: (error) => {
          Swal.fire({
            title: 'Error al actualizar',
            text: 'Error al actualizar al alumno',
            icon: 'error',
          });
        },
      });
    }
  }

  cambiarPagina(pageNumber: number): void {
    this.paginaActual = pageNumber;
    this.obtenerAlumnos();
  }

  alternarFormulario(alumno: any): void {
    this.mostrarFormulario = !this.mostrarFormulario;
    this.alumnoEditado = { ...alumno };
    this.imagenPreview = alumno.fotoAlumno
      ? 'data:' + alumno.fotoAlumno.tipo + ';base64,' + alumno.fotoAlumno.datos
      : 'assets/media/default.webp';
  }

  onFileSelected(event: any) {
    const file: File = event.target.files[0];
    this.alumnoEditado.fotoAlumno = file;

    const reader = new FileReader();
    reader.onload = (e: any) => {
      this.imagenPreview = e.target.result;
    };
    reader.readAsDataURL(file);
  }

  filtrarPorNombre(): void {
    this.paginaActual = 1;
    this.obtenerAlumnos();
  }
}
