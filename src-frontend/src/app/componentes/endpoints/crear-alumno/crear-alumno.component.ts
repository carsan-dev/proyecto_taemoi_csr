import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TipoTarifa } from '../../../enums/tipo-tarifa';
import { Router } from '@angular/router';

@Component({
  selector: 'app-crear-alumno',
  standalone: true,
  imports: [FormsModule, CommonModule, ReactiveFormsModule],
  templateUrl: './crear-alumno.component.html',
  styleUrl: './crear-alumno.component.scss',
})
export class CrearAlumnoComponent implements OnInit {
  alumnoData!: FormGroup;
  imagen: File | null = null;
  imagenPreview: string | ArrayBuffer | null = null;
  tiposTarifa = Object.values(TipoTarifa);
  grados: any[] = [];

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.cargarGrados();
  }

  initForm(): void {
    this.alumnoData = this.fb.group({
      nombre: ['', Validators.required],
      apellidos: ['', Validators.required],
      fechaNacimiento: ['', Validators.required],
      nif: [
        '',
        [Validators.required, Validators.pattern('^[0-9]{8}[A-Za-z]$')],
      ],
      direccion: ['', Validators.required],
      telefono: [
        '',
        [
          Validators.required,
          Validators.pattern('^[0-9]+$'),
          Validators.maxLength(9),
        ],
      ],
      email: ['', [Validators.required, Validators.email]],
      tipoTarifa: ['', Validators.required],
      fechaAlta: ['', Validators.required],
      autorizacionWeb: [true, Validators.required],
      competidor: [false],
      peso: [{ value: null, disabled: true }, Validators.required],
      tieneLicencia: [false],
      numeroLicencia: [{ value: '', disabled: true }, Validators.required],
      fechaLicencia: [{ value: '', disabled: true }, Validators.required],
      grado: ['BLANCO', Validators.required],
    });
  }

  cargarGrados(): void {
    this.endpointsService.obtenerGrados().subscribe({
      next: (grados) => {
        this.grados = grados;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los grados',
          icon: 'error',
        });
      },
    });
  }

  onSubmit(): void {
    const alumnoData = this.alumnoData.value;
    alumnoData.aptoParaExamen = false;

    this.endpointsService.crearAlumno(alumnoData, this.imagen).subscribe({
      next: (response) => {
        Swal.fire({
          title: 'Perfecto!',
          text: 'Has creado un nuevo alumno',
          icon: 'success',
        });
        this.router.navigate(['/alumnosListar']);
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

  onFileChange(event: any) {
    const fileList: FileList = event.target.files;
    if (fileList.length > 0) {
      this.imagen = fileList[0];
      // Crear una vista previa de la imagen
      const reader = new FileReader();
      reader.onload = (e) => (this.imagenPreview = e.target?.result ?? null);
      reader.readAsDataURL(this.imagen);
    }
  }

  onCompetidorChange(event: any): void {
    if (event.target.checked) {
      this.alumnoData
        .get('peso')
        ?.setValidators([Validators.required, Validators.min(0)]);
      this.alumnoData.get('peso')?.enable();
    } else {
      this.alumnoData.get('peso')?.clearValidators();
      this.alumnoData.get('peso')?.disable();
      this.alumnoData.get('peso')?.setValue(null);
    }
    this.alumnoData.get('peso')?.updateValueAndValidity();
  }

  onLicenciaChange(event: any): void {
    const tieneLicencia = event.target.checked;
    const today = new Date().toISOString().split('T')[0]; // Formato de fecha en YYYY-MM-DD

    if (tieneLicencia) {
      // Habilitar y rellenar los campos de licencia
      this.alumnoData.get('numeroLicencia')?.enable();
      this.alumnoData.get('fechaLicencia')?.setValue(today);
      this.alumnoData.get('fechaLicencia')?.enable();
    } else {
      // Deshabilitar y limpiar los campos de licencia
      this.alumnoData.get('numeroLicencia')?.disable();
      this.alumnoData.get('numeroLicencia')?.setValue('');
      this.alumnoData.get('fechaLicencia')?.disable();
      this.alumnoData.get('fechaLicencia')?.setValue('');
    }
  }

  removeImage() {
    this.imagen = null;
    this.imagenPreview = null;
    const fileInput = document.getElementById('fotoAlumno') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = ''; // Limpiar el valor del input
    }
  }
}
