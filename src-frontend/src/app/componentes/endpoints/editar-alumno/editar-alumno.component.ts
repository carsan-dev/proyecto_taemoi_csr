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
  mostrarInactivos: boolean = false;
  tipoTarifaEditado: boolean = false; // Nueva bandera para saber si el usuario cambió el tipo de tarifa

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
        nif: [
          '',
          [Validators.required, Validators.pattern('^[0-9]{8}[A-Za-z]$')],
        ],
        email: ['', [Validators.required, Validators.email]],
        telefono: ['', [Validators.required, Validators.pattern('^[0-9]+$'), Validators.maxLength(9)]],
        tipoTarifa: ['', Validators.required],
        cuantiaTarifa: ['', Validators.required],
        fechaAlta: ['', Validators.required],
        fechaBaja: [''],
        autorizacionWeb: [true, Validators.required],
        grado: [''],
        competidor: [false], // Nuevo campo para competidor
        peso: [''], // Nuevo campo para peso
        fechaPeso: [''], // Nuevo campo para la fecha de registro del peso
      },
      { validators: [this.fechaBajaPosteriorAFechaAltaValidator, this.fechaNacimientoPosteriorAFechaAltaValidator]}
    );
  }

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      this.obtenerAlumnos();
    }

    // Escucha cambios en el tipo de tarifa para actualizar la cuantía
    this.alumnoForm.get('tipoTarifa')?.valueChanges.subscribe((tipoTarifa: TipoTarifa) => {
      if (this.tipoTarifaEditado) { // Solo se actualiza si el usuario cambió el tipo de tarifa
        const nuevaCuantia = this.asignarCuantiaTarifa(tipoTarifa);
        this.alumnoForm.get('cuantiaTarifa')?.setValue(nuevaCuantia);
      }
      this.tipoTarifaEditado = true; // Se marca como editado después de un cambio
    });

    // Escucha cambios en el campo competidor para validar peso y fechaPeso
    this.alumnoForm.get('competidor')?.valueChanges.subscribe((isCompetidor: boolean) => {
      const pesoControl = this.alumnoForm.get('peso');
      const fechaPesoControl = this.alumnoForm.get('fechaPeso');

      if (isCompetidor) {
        pesoControl?.setValidators([Validators.required]);
        fechaPesoControl?.setValidators([Validators.required]);

        if (!pesoControl?.value) {
          fechaPesoControl?.setValue(this.getFechaActual()); // Solo establece si no tiene fecha previa
        }
      } else {
        // Cuando se desmarca competidor, no borres los valores actuales
        // Pero mantén los valores para que no se pierdan al guardar
      }

      pesoControl?.updateValueAndValidity();
      fechaPesoControl?.updateValueAndValidity();
    });
  }

  obtenerAlumnos() {
    const token = localStorage.getItem('token');

    if (token) {
      this.endpointsService
        .obtenerAlumnos(
          token,
          this.paginaActual,
          this.tamanoPagina,
          this.nombreFiltro,
          this.mostrarInactivos
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
    this.imagenPreview = alumno.fotoAlumno?.url ? alumno.fotoAlumno.url : '../../../../assets/media/default.webp';

    const fechaNacimiento = this.formatDate(alumno.fechaNacimiento);
    const fechaAlta = this.formatDate(alumno.fechaAlta);
    const fechaBaja = alumno.fechaBaja ? this.formatDate(alumno.fechaBaja) : '';
    this.tipoTarifaEditado = false; // Resetea la bandera antes de aplicar el valor

    const peso = alumno.peso || '';
    const fechaPeso = alumno.fechaPeso ? this.formatDate(alumno.fechaPeso) : '';

    this.alumnoForm.patchValue({
        ...this.alumnoEditado,
        fechaNacimiento: fechaNacimiento,
        fechaAlta: fechaAlta,
        fechaBaja: fechaBaja,
        autorizacionWeb: alumno.autorizacionWeb,
        cuantiaTarifa: alumno.cuantiaTarifa,
        peso: peso,
        fechaPeso: fechaPeso,
        competidor: alumno.competidor
    });
}

  private formatDate(fecha: string): string {
    const date = new Date(fecha);
    const userTimezoneOffset = date.getTimezoneOffset() * 60000;
    const adjustedDate = new Date(date.getTime() - userTimezoneOffset);
    return adjustedDate.toISOString().split('T')[0];
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

  asignarCuantiaTarifa(tipoTarifa: TipoTarifa): number {
    switch (tipoTarifa) {
      case TipoTarifa.ADULTO:
        return 30.0;
      case TipoTarifa.ADULTO_GRUPO:
        return 20.0;
      case TipoTarifa.FAMILIAR:
        return 0.0;
      case TipoTarifa.INFANTIL:
        return 25.0;
      case TipoTarifa.INFANTIL_GRUPO:
        return 20.0;
      case TipoTarifa.HERMANOS:
        return 23.0;
      case TipoTarifa.PADRES_HIJOS:
        return 0.0;
      default:
        throw new Error('Tipo de tarifa no válido');
    }
  }

  alternarInactivos(): void {
    this.mostrarInactivos = !this.mostrarInactivos;
    this.obtenerAlumnos();
  }

  onCompetidorChange(event: any) {
    const isCompetidor = event.target.checked;
    if (!isCompetidor) {
      this.alumnoForm.patchValue({ peso: '', fechaPeso: '' });
    }
  }

  private getFechaActual(): string {
    const today = new Date();
    return today.toISOString().split('T')[0];
  }

  abrirModal(imagenUrl: string) {
    const modal = document.getElementById('imageModal');
    const modalImg = document.getElementById('imgAmpliada') as HTMLImageElement;
  
    if (modal && modalImg) {
      modal.style.display = 'block';
      modalImg.src = imagenUrl;
    }
  }
  
  cerrarModal() {
    const modal = document.getElementById('imageModal');
    if (modal) {
      modal.style.display = 'none';
    }
  }
  
}
