import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import Swal from 'sweetalert2';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { TipoTarifa } from '../../../enums/tipo-tarifa';
import { TipoGrado } from '../../../enums/tipo-grado';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';

@Component({
  selector: 'app-editar-alumno',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    PaginacionComponent,
    ReactiveFormsModule,
  ],
  templateUrl: './editar-alumno.component.html',
  styleUrl: './editar-alumno.component.scss',
})
export class EditarAlumnoComponent implements OnInit {
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
  alumnoForm: FormGroup;

  constructor(
    private endpointsService: EndpointsService,
    private fb: FormBuilder
  ) {
    this.alumnoForm = this.fb.group(
      {
        nombre: ['', Validators.required],
        apellidos: ['', Validators.required],
        direccion: ['', Validators.required],
        fechaNacimiento: ['', Validators.required],
        numeroExpediente: [
          '',
          [Validators.required, Validators.pattern('^[0-9]+$')],
        ],
        nif: [
          '',
          [Validators.required, Validators.pattern('^[0-9]{8}[A-Za-z]$')],
        ],
        email: ['', [Validators.required, Validators.email]],
        telefono: ['', [Validators.required, Validators.pattern('^[0-9]+$'), Validators.maxLength(9)]],
        tipoTarifa: ['', Validators.required],
        fechaAlta: ['', Validators.required],
        fechaBaja: [''],
        grado: [''],
      },
      { validators: [this.fechaBajaPosteriorAFechaAltaValidator, this.fechaNacimientoPosteriorAFechaAltaValidator]}
    );
  }

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

  confirmarYActualizarAlumno(id: number, alumno: any) {
    if (this.alumnoForm.invalid) {
      Swal.fire({
        title: 'Formulario inválido',
        text: 'Por favor, complete todos los campos requeridos correctamente',
        icon: 'error',
      });
      return;
    }

    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El alumno se actualizará con la información introducida',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, actualizarlo',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.actualizarAlumno(id);
        this.alternarFormulario(alumno);
      }
    });
  }

  actualizarAlumno(id: number) {
    const token = localStorage.getItem('token');

    const formData = new FormData();
    formData.append('alumnoEditado', JSON.stringify(this.alumnoForm.value));

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
          this.imagenPreview = '../../../../assets/media/default.webp';
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
      : '../../../../assets/media/default.webp';

    this.alumnoForm.patchValue(this.alumnoEditado);
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

  fechaBajaPosteriorAFechaAltaValidator(formGroup: AbstractControl) {
    const fechaAlta = formGroup.get('fechaAlta')!.value;
    const fechaBaja = formGroup.get('fechaBaja')!.value;

    if (!fechaAlta || !fechaBaja) {
      return null;
    }

    const fechaAltaDate = new Date(fechaAlta);
    const fechaBajaDate = new Date(fechaBaja);

    return fechaBajaDate > fechaAltaDate
      ? null
      : { fechaBajaAnteriorAFechaAlta: true };
  }

  fechaNacimientoPosteriorAFechaAltaValidator(formGroup: AbstractControl) {
    const fechaAlta = formGroup.get('fechaAlta')!.value;
    const fechaNacimiento = formGroup.get('fechaNacimiento')!.value;

    if (!fechaAlta || !fechaNacimiento) {
      return null;
    }

    const fechaAltaDate = new Date(fechaAlta);
    const fechaNacimientoDate = new Date(fechaNacimiento);

    return fechaAltaDate > fechaNacimientoDate
      ? null
      : { fechaAltaAnteriorAFechaNacimiento: true };
  }
}
