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
import { TipoGrado } from '../../../enums/tipo-grado';
import { RolFamiliar } from '../../../enums/rol-familiar';
import { Router } from '@angular/router';
import { ScrollService } from '../../../servicios/generales/scroll.service';

@Component({
  selector: 'app-crear-alumno',
  standalone: true,
  imports: [FormsModule, CommonModule, ReactiveFormsModule],
  templateUrl: './crear-alumno.component.html',
  styleUrls: ['./crear-alumno.component.scss'],
})
export class CrearAlumnoComponent implements OnInit {
  alumnoData!: FormGroup;
  imagen: File | null = null;
  imagenPreview: string | ArrayBuffer | null = null;
  tiposTarifa: TipoTarifa[] = Object.values(TipoTarifa);
  rolesFamiliares: RolFamiliar[] = Object.values(RolFamiliar);
  tiposGrado: TipoGrado[] = [];
  grados: any[] = [];
  todosLosGrados: any[] = [];

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly router: Router,
    private readonly scrollService: ScrollService
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.cargarGrados();

    // Subscribe to fechaNacimiento changes to update available grades
    this.alumnoData.get('fechaNacimiento')?.valueChanges.subscribe((valor) => {
      if (valor) {
        this.obtenerGradosDisponibles(valor);
      }
    });
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
      fechaAlta: ['', Validators.required],
      autorizacionWeb: [true, Validators.required],
      tipoTarifa: [TipoTarifa.ADULTO, Validators.required],
      rolFamiliar: [{ value: RolFamiliar.NINGUNO, disabled: true }],
      grupoFamiliar: [{ value: '', disabled: true }],
      grado: ['', Validators.required],
      competidor: [false],
      peso: [{ value: null, disabled: true }, Validators.required],
      tieneLicencia: [false],
      numeroLicencia: [{ value: '', disabled: true }, Validators.required],
      fechaLicencia: [{ value: '', disabled: true }, Validators.required],
      deporte: ['', Validators.required],
      tieneDiscapacidad: [false],
    });
  }

  cargarGrados(): void {
    this.endpointsService.obtenerGrados().subscribe({
      next: (grados) => {
        this.todosLosGrados = grados;
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

  obtenerGradosDisponibles(fechaNacimiento: string): void {
    this.endpointsService.obtenerGradosPorFechaNacimiento(fechaNacimiento).subscribe({
      next: (tiposGrado: TipoGrado[]) => {
        this.tiposGrado = tiposGrado;
        this.aplicarFiltrosGrado();
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los grados disponibles.',
          icon: 'error',
        });
      },
    });
  }

  aplicarFiltrosGrado(): void {
    const deporteSeleccionado = this.alumnoData.get('deporte')?.value;
    let gradosFiltrados = this.todosLosGrados;

    // First, filter by sport
    if (deporteSeleccionado === 'KICKBOXING') {
      const gradosKickboxing = [
        'BLANCO',
        'AMARILLO',
        'NARANJA',
        'VERDE',
        'AZUL',
        'ROJO',
        'NEGRO_1_DAN',
        'NEGRO_2_DAN',
        'NEGRO_3_DAN',
        'NEGRO_4_DAN',
        'NEGRO_5_DAN',
      ];
      gradosFiltrados = gradosFiltrados.filter((grado) =>
        gradosKickboxing.includes(grado.tipoGrado)
      );
    } else if (deporteSeleccionado === 'PILATES' || deporteSeleccionado === 'DEFENSA_PERSONAL_FEMENINA') {
      gradosFiltrados = [];
    }

    // Then, filter by age if tiposGrado has values
    if (this.tiposGrado.length > 0) {
      gradosFiltrados = gradosFiltrados.filter((grado) =>
        this.tiposGrado.includes(grado.tipoGrado)
      );
    }

    this.grados = gradosFiltrados;
  }

  onTipoTarifaChange(event: any): void {
    const tipoTarifa = event.target.value;

    // Reset family fields
    this.alumnoData.get('rolFamiliar')?.setValue(RolFamiliar.NINGUNO);
    this.alumnoData.get('grupoFamiliar')?.setValue('');
    this.alumnoData.get('rolFamiliar')?.disable();
    this.alumnoData.get('grupoFamiliar')?.disable();
    this.alumnoData.get('rolFamiliar')?.clearValidators();
    this.alumnoData.get('grupoFamiliar')?.clearValidators();

    // Enable fields based on tarifa type
    if (tipoTarifa === TipoTarifa.PADRES_HIJOS) {
      this.alumnoData.get('rolFamiliar')?.enable();
      this.alumnoData.get('rolFamiliar')?.setValidators(Validators.required);
    } else if (tipoTarifa === TipoTarifa.HERMANOS) {
      this.alumnoData.get('grupoFamiliar')?.enable();
      this.alumnoData.get('grupoFamiliar')?.setValidators(Validators.required);
    }

    this.alumnoData.get('rolFamiliar')?.updateValueAndValidity();
    this.alumnoData.get('grupoFamiliar')?.updateValueAndValidity();
  }

  onSubmit(): void {
    const alumnoData = this.alumnoData.getRawValue();
    alumnoData.aptoParaExamen = false;

    this.endpointsService.crearAlumno(alumnoData, this.imagen).subscribe({
      next: (response) => {
        Swal.fire({
          title: 'Perfecto!',
          text: 'Has creado un nuevo alumno',
          icon: 'success',
          timer: 2000,
        }).then(() => {
          // Scroll to top after success message
          this.scrollService.scrollToTop();
        });
        // Navigate directly to edit page with the newly created alumno's ID
        if (response && response.id) {
          this.router.navigate(['/alumnosEditar', response.id]);
        } else {
          // Fallback to list page if ID is not available
          this.router.navigate(['/alumnosListar']);
        }
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No has completado todos los campos requeridos',
          icon: 'error',
        }).then(() => {
          // Scroll to top to show form errors
          this.scrollService.scrollToTop();
        });
      },
      complete: () => {},
    });
  }

  onFileChange(event: any) {
    const fileList: FileList = event.target.files;
    if (fileList.length > 0) {
      this.imagen = fileList[0];
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
    const today = new Date().toISOString().split('T')[0];

    if (tieneLicencia) {
      this.alumnoData.get('numeroLicencia')?.enable();
      this.alumnoData.get('fechaLicencia')?.setValue(today);
      this.alumnoData.get('fechaLicencia')?.enable();
    } else {
      this.alumnoData.get('numeroLicencia')?.disable();
      this.alumnoData.get('numeroLicencia')?.setValue('');
      this.alumnoData.get('fechaLicencia')?.disable();
      this.alumnoData.get('fechaLicencia')?.setValue('');
    }

    this.alumnoData.get('numeroLicencia')?.updateValueAndValidity();
    this.alumnoData.get('fechaLicencia')?.updateValueAndValidity();
  }

  onDeporteChange(event: any): void {
    const selectedDeporte = event.target.value;

    this.resetFormControls();

    if (selectedDeporte === 'TAEKWONDO') {
      this.showAllFields();
      // Tarifas para Taekwondo (excluir Pilates, Defensa Personal y Kickboxing)
      this.tiposTarifa = [
        TipoTarifa.ADULTO,
        TipoTarifa.ADULTO_GRUPO,
        TipoTarifa.INFANTIL,
        TipoTarifa.INFANTIL_GRUPO,
        TipoTarifa.HERMANOS,
        TipoTarifa.PADRES_HIJOS,
        TipoTarifa.FAMILIAR,
      ];
      this.aplicarFiltrosGrado();
    } else if (selectedDeporte === 'KICKBOXING') {
      this.showAllFields();
      // Tarifas específicas para Kickboxing
      this.tiposTarifa = [
        TipoTarifa.KICKBOXING,
      ];
      this.aplicarFiltrosGrado();
    } else if (selectedDeporte === 'PILATES') {
      this.hideFieldsForPilatesOrDefPersFem();
      // Solo tarifa PILATES
      this.tiposTarifa = [TipoTarifa.PILATES];
      this.aplicarFiltrosGrado();
    } else if (selectedDeporte === 'DEFENSA_PERSONAL_FEMENINA') {
      this.hideFieldsForPilatesOrDefPersFem();
      // Solo tarifa DEFENSA_PERSONAL_FEMENINA
      this.tiposTarifa = [TipoTarifa.DEFENSA_PERSONAL_FEMENINA];
      this.aplicarFiltrosGrado();
    }
  }

  resetFormControls(): void {
    this.alumnoData.get('tipoTarifa')?.clearValidators();
    this.alumnoData.get('rolFamiliar')?.clearValidators();
    this.alumnoData.get('grupoFamiliar')?.clearValidators();
    this.alumnoData.get('grado')?.clearValidators();
    this.alumnoData.get('competidor')?.clearValidators();
    this.alumnoData.get('tieneLicencia')?.clearValidators();
    this.alumnoData.get('numeroLicencia')?.clearValidators();
    this.alumnoData.get('fechaLicencia')?.clearValidators();

    this.alumnoData.get('tipoTarifa')?.updateValueAndValidity();
    this.alumnoData.get('rolFamiliar')?.updateValueAndValidity();
    this.alumnoData.get('grupoFamiliar')?.updateValueAndValidity();
    this.alumnoData.get('grado')?.updateValueAndValidity();
    this.alumnoData.get('competidor')?.updateValueAndValidity();
    this.alumnoData.get('tieneLicencia')?.updateValueAndValidity();
    this.alumnoData.get('numeroLicencia')?.updateValueAndValidity();
    this.alumnoData.get('fechaLicencia')?.updateValueAndValidity();

    this.alumnoData.get('tipoTarifa')?.enable();
    this.alumnoData.get('rolFamiliar')?.disable();
    this.alumnoData.get('grupoFamiliar')?.disable();
    this.alumnoData.get('grado')?.enable();
    this.alumnoData.get('competidor')?.enable();
    this.alumnoData.get('tieneLicencia')?.enable();
    this.alumnoData.get('numeroLicencia')?.enable();
    this.alumnoData.get('fechaLicencia')?.enable();
  }

  showAllFields(): void {
    this.alumnoData.get('tipoTarifa')?.setValidators(Validators.required);
    this.alumnoData.get('grado')?.setValidators(Validators.required);

    this.alumnoData.get('tipoTarifa')?.updateValueAndValidity();
    this.alumnoData.get('grado')?.updateValueAndValidity();
  }

  hideFieldsForPilatesOrDefPersFem(): void {
    this.alumnoData.get('tipoTarifa')?.disable();
    this.alumnoData.get('tipoTarifa')?.clearValidators();
    this.alumnoData.get('grado')?.disable();
    this.alumnoData.get('grado')?.clearValidators();
    this.alumnoData.get('competidor')?.disable();
    this.alumnoData.get('tieneLicencia')?.disable();
    this.alumnoData.get('numeroLicencia')?.disable();
    this.alumnoData.get('numeroLicencia')?.clearValidators();
    this.alumnoData.get('fechaLicencia')?.disable();
    this.alumnoData.get('fechaLicencia')?.clearValidators();

    this.alumnoData.get('tipoTarifa')?.updateValueAndValidity();
    this.alumnoData.get('grado')?.updateValueAndValidity();
    this.alumnoData.get('numeroLicencia')?.updateValueAndValidity();
    this.alumnoData.get('fechaLicencia')?.updateValueAndValidity();
  }

  getGradoNombre(grado: any): string {
    const deporteSeleccionado = this.alumnoData.get('deporte')?.value;

    if (deporteSeleccionado === 'KICKBOXING' && grado.tipoGrado === 'ROJO') {
      return 'MARRÓN';
    }

    // Puedes agregar más transformaciones si es necesario
    return grado.tipoGrado;
  }

  removeImage() {
    this.imagen = null;
    this.imagenPreview = null;
    const fileInput = document.getElementById('fotoAlumno') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
  }
}
