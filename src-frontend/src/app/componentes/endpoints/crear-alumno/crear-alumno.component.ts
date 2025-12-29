import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import {
  FormArray,
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
import { Deporte } from '../../../enums/deporte';
import { Router } from '@angular/router';
import { ScrollService } from '../../../servicios/generales/scroll.service';
import { calcularCategoriaPorEdad } from '../../../utilities/categoria-por-edad';
import { obtenerCuantiaTarifaEstandar } from '../../../constants/tarifa.constants';

/**
 * Interface for sport entry in the form
 */
interface DeporteEntry {
  deporte: string;
  grado: string;
  fechaGrado: string;
  fechaAlta: string;
  fechaAltaInicial: string;
  tipoTarifa: string;
  cuantiaTarifa: number | null;
  rolFamiliar: string;
  grupoFamiliar: string;
  categoria: string;
  competidor: boolean;
  fechaAltaCompeticion: string | null;
  fechaAltaCompetidorInicial: string | null;
  peso: number | null;
  fechaPeso: string;
  tieneLicencia: boolean;
  numeroLicencia: number | null;
  fechaLicencia: string;
}

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
  categorias = ['Infantil', 'Precadete', 'Cadete', 'Junior', 'Senior'];

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
        this.actualizarCategoriasPorEdad();
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
      telefono2: [
        '',
        [
          Validators.pattern('^[0-9]+$'),
          Validators.maxLength(9),
        ],
      ],
      email: ['', [Validators.required, Validators.email]],
      autorizacionWeb: [true, Validators.required],
      rolFamiliar: [{ value: RolFamiliar.NINGUNO, disabled: true }],
      grupoFamiliar: [{ value: '', disabled: true }],
      tieneDiscapacidad: [false],
      // NEW: FormArray for multiple sports
      deportesInicial: this.fb.array(
        [this.crearDeporteFormGroup()], // Start with 1 sport
        Validators.required
      ),
    });
  }

  crearDeporteFormGroup(): FormGroup {
    return this.fb.group({
      deporte: ['', Validators.required],
      grado: [''],  // Not required by default, will be conditionally required
      fechaGrado: [this.obtenerFechaHoy()],  // Not required by default, will be conditionally required
      fechaAlta: [this.obtenerFechaHoy(), Validators.required],
      fechaAltaInicial: [''],  // Optional, defaults to fechaAlta if not provided
      tipoTarifa: ['', Validators.required],
      cuantiaTarifa: [null],
      rolFamiliar: [{ value: '', disabled: true }],  // For PADRES_HIJOS tarifa, disabled by default
      grupoFamiliar: [{ value: '', disabled: true }],  // For HERMANOS tarifa, disabled by default
      categoria: [''],
      competidor: [false],
      fechaAltaCompeticion: [''],
      fechaAltaCompetidorInicial: [''],
      peso: [null],
      fechaPeso: [this.obtenerFechaHoy()],
      tieneLicencia: [false],
      numeroLicencia: [null],
      fechaLicencia: [this.obtenerFechaHoy()],
    });
  }

  obtenerFechaHoy(): string {
    return new Date().toISOString().split('T')[0];
  }

  get deportesFormArray(): FormArray {
    return this.alumnoData.get('deportesInicial') as FormArray;
  }

  agregarDeporte(): void {
    if (this.deportesFormArray.length < 4) {
      // Max 4 sports
      this.deportesFormArray.push(this.crearDeporteFormGroup());
    } else {
      Swal.fire({
        title: 'Límite alcanzado',
        text: 'Un alumno puede tener un máximo de 4 deportes',
        icon: 'warning',
      });
    }
  }

  eliminarDeporte(index: number): void {
    if (this.deportesFormArray.length > 1) {
      this.deportesFormArray.removeAt(index);
    } else {
      Swal.fire({
        title: 'Acción no permitida',
        text: 'Un alumno debe tener al menos 1 deporte',
        icon: 'error',
      });
    }
  }

  obtenerGradosFiltradosParaDeporte(index: number): any[] {
    const deporteControl = this.deportesFormArray.at(index).get('deporte');
    const deporteSeleccionado = deporteControl?.value;

    if (!deporteSeleccionado) {
      return [];
    }

    let gradosFiltrados = this.todosLosGrados;

    // Filter by sport (Kickboxing has limited grades, Pilates has none)
    if (deporteSeleccionado === 'KICKBOXING') {
      const gradosKickboxing = new Set<string>([
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
      ]);
      gradosFiltrados = gradosFiltrados.filter((grado) =>
        gradosKickboxing.has(grado.tipoGrado)
      );
    } else if (deporteSeleccionado === 'PILATES' || deporteSeleccionado === 'DEFENSA_PERSONAL_FEMENINA') {
      return []; // Pilates and Defensa Personal don't have grades
    }

    // Filter by age if fechaNacimiento is set
    const fechaNacimiento = this.alumnoData.get('fechaNacimiento')?.value;
    if (fechaNacimiento && this.tiposGrado.length > 0) {
      gradosFiltrados = gradosFiltrados.filter((grado) =>
        this.tiposGrado.includes(grado.tipoGrado)
      );
    }

    return gradosFiltrados;
  }

  obtenerTarifasFiltradas(index: number): TipoTarifa[] {
    const deporteControl = this.deportesFormArray.at(index).get('deporte');
    const deporteSeleccionado = deporteControl?.value;

    if (!deporteSeleccionado) {
      return [];
    }

    // Filter tarifas based on selected sport
    switch (deporteSeleccionado) {
      case 'TAEKWONDO':
        // All tarifas except sport-specific ones (KICKBOXING, PILATES, DEFENSA_PERSONAL_FEMENINA)
        return this.tiposTarifa.filter(tarifa =>
          tarifa !== TipoTarifa.KICKBOXING &&
          tarifa !== TipoTarifa.PILATES &&
          tarifa !== TipoTarifa.DEFENSA_PERSONAL_FEMENINA
        );

      case 'KICKBOXING':
        // Only KICKBOXING tarifa
        return [TipoTarifa.KICKBOXING];

      case 'PILATES':
        // Only PILATES tarifa
        return [TipoTarifa.PILATES];

      case 'DEFENSA_PERSONAL_FEMENINA':
        // Only DEFENSA_PERSONAL_FEMENINA tarifa
        return [TipoTarifa.DEFENSA_PERSONAL_FEMENINA];

      default:
        return [];
    }
  }

  onDeporteChangeEnLista(index: number): void {
    const deporteControl = this.deportesFormArray.at(index).get('deporte');
    const gradoControl = this.deportesFormArray.at(index).get('grado');
    const fechaGradoControl = this.deportesFormArray.at(index).get('fechaGrado');
    const tipoTarifaControl = this.deportesFormArray.at(index).get('tipoTarifa');

    // Reset tarifa when sport changes, as valid tarifas depend on the sport
    const currentTarifa = tipoTarifaControl?.value;
    const validTarifas = this.obtenerTarifasFiltradas(index);

    // If current tarifa is not valid for the new sport, clear it
    if (currentTarifa && !validTarifas.includes(currentTarifa)) {
      tipoTarifaControl?.setValue('');
    }

    if (deporteControl?.value === 'PILATES' || deporteControl?.value === 'DEFENSA_PERSONAL_FEMENINA') {
      // Clear validators and values for Pilates and Defensa Personal Femenina
      gradoControl?.clearValidators();
      gradoControl?.setValue('');
      fechaGradoControl?.clearValidators();
      fechaGradoControl?.setValue('');
    } else {
      // Set validators for Taekwondo and Kickboxing
      gradoControl?.setValidators(Validators.required);
      fechaGradoControl?.setValidators(Validators.required);
    }
    gradoControl?.updateValueAndValidity();
    fechaGradoControl?.updateValueAndValidity();
  }

  onTipoTarifaChangeEnLista(index: number): void {
    const tipoTarifaControl = this.deportesFormArray.at(index).get('tipoTarifa');
    const cuantiaTarifaControl = this.deportesFormArray.at(index).get('cuantiaTarifa');
    const rolFamiliarControl = this.deportesFormArray.at(index).get('rolFamiliar');
    const grupoFamiliarControl = this.deportesFormArray.at(index).get('grupoFamiliar');

    if (tipoTarifaControl?.value) {
      // Set default cuantía
      const rolFamiliar = rolFamiliarControl?.value || undefined;
      const cuantiaDefault = obtenerCuantiaTarifaEstandar(tipoTarifaControl.value, rolFamiliar);
      cuantiaTarifaControl?.setValue(cuantiaDefault);

      // Handle PADRES_HIJOS tarifa - needs rolFamiliar
      if (tipoTarifaControl.value === 'PADRES_HIJOS') {
        rolFamiliarControl?.setValidators(Validators.required);
        rolFamiliarControl?.enable();
        grupoFamiliarControl?.clearValidators();
        grupoFamiliarControl?.setValue('');
        grupoFamiliarControl?.disable();
      }
      // Handle HERMANOS tarifa - needs grupoFamiliar
      else if (tipoTarifaControl.value === 'HERMANOS') {
        grupoFamiliarControl?.setValidators(Validators.required);
        grupoFamiliarControl?.enable();
        rolFamiliarControl?.clearValidators();
        rolFamiliarControl?.setValue('');
        rolFamiliarControl?.disable();
      }
      // Other tarifas - clear both fields
      else {
        rolFamiliarControl?.clearValidators();
        rolFamiliarControl?.setValue('');
        rolFamiliarControl?.disable();
        grupoFamiliarControl?.clearValidators();
        grupoFamiliarControl?.setValue('');
        grupoFamiliarControl?.disable();
      }

      rolFamiliarControl?.updateValueAndValidity();
      grupoFamiliarControl?.updateValueAndValidity();
    }
  }

  onRolFamiliarChangeEnLista(index: number, event: any): void {
    const deporteForm = this.deportesFormArray.at(index);
    const tipoTarifa = deporteForm.get('tipoTarifa')?.value;
    if (tipoTarifa !== 'PADRES_HIJOS') {
      return;
    }

    const rolFamiliar = event.target.value;
    const cuantiaTarifaControl = deporteForm.get('cuantiaTarifa');
    const cuantiaDefault = obtenerCuantiaTarifaEstandar(tipoTarifa, rolFamiliar);
    cuantiaTarifaControl?.setValue(cuantiaDefault);
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
      const gradosKickboxing = new Set<string>([
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
      ]);
      gradosFiltrados = gradosFiltrados.filter((grado) =>
        gradosKickboxing.has(grado.tipoGrado)
      );
    }

    // Then, filter by age if tiposGrado has values
    if (this.tiposGrado.length > 0) {
      gradosFiltrados = gradosFiltrados.filter((grado) =>
        this.tiposGrado.includes(grado.tipoGrado)
      );
    }

    this.grados = gradosFiltrados;
  }

  private obtenerCategoriaPorEdadActual(): string {
    return calcularCategoriaPorEdad(this.alumnoData.get('fechaNacimiento')?.value || null);
  }

  private actualizarCategoriasPorEdad(): void {
    const categoriaPorEdad = this.obtenerCategoriaPorEdadActual();
    this.deportesFormArray.controls.forEach((control) => {
      const competidor = control.get('competidor')?.value;
      const categoriaControl = control.get('categoria');
      if (!categoriaControl) {
        return;
      }

      if (competidor) {
        if (!categoriaControl.value || !categoriaControl.dirty) {
          categoriaControl.setValue(categoriaPorEdad, { emitEvent: false });
        }
      } else if (categoriaControl.value) {
        categoriaControl.setValue('', { emitEvent: false });
      }
    });
  }

  onCompetidorChangeEnLista(index: number): void {
    const deporteForm = this.deportesFormArray.at(index);
    const competidor = deporteForm.get('competidor')?.value;
    const categoriaControl = deporteForm.get('categoria');
    const fechaAltaCompeticionControl = deporteForm.get('fechaAltaCompeticion');
    const fechaAltaCompetidorInicialControl = deporteForm.get('fechaAltaCompetidorInicial');
    if (!categoriaControl) {
      return;
    }

    if (competidor) {
      const fechaAlta = deporteForm.get('fechaAlta')?.value || this.obtenerFechaHoy();
      const fechaAltaInicial = deporteForm.get('fechaAltaInicial')?.value || fechaAlta;
      const categoriaPorEdad = this.obtenerCategoriaPorEdadActual();
      if (categoriaPorEdad && (!categoriaControl.value || !categoriaControl.dirty)) {
        categoriaControl.setValue(categoriaPorEdad);
      }
      if (fechaAltaCompeticionControl && !fechaAltaCompeticionControl.value) {
        fechaAltaCompeticionControl.setValue(fechaAlta);
      }
      if (fechaAltaCompetidorInicialControl && !fechaAltaCompetidorInicialControl.value) {
        fechaAltaCompetidorInicialControl.setValue(fechaAltaInicial);
      }
    } else {
      categoriaControl.setValue('');
      if (fechaAltaCompeticionControl) {
        fechaAltaCompeticionControl.setValue('');
      }
      if (fechaAltaCompetidorInicialControl) {
        fechaAltaCompetidorInicialControl.setValue('');
      }
    }
  }

  onTieneLicenciaChangeEnLista(index: number): void {
    const deporteForm = this.deportesFormArray.at(index);
    const tieneLicencia = deporteForm.get('tieneLicencia')?.value;
    if (tieneLicencia) {
      return;
    }

    deporteForm.patchValue(
      {
        competidor: false,
        categoria: '',
        fechaAltaCompeticion: '',
        fechaAltaCompetidorInicial: '',
        peso: null,
      },
      { emitEvent: false }
    );
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
    if (this.alumnoData.invalid) {
      Swal.fire({
        title: 'Formulario incompleto',
        text: 'Por favor completa todos los campos requeridos',
        icon: 'error',
      });
      return;
    }

    const alumnoData = this.alumnoData.getRawValue();
    const categoriaPorEdad = this.obtenerCategoriaPorEdadActual();

    // For each sport, set fechaAltaInicial to fechaAlta if not provided
    if (alumnoData.deportesInicial) {
      alumnoData.deportesInicial.forEach((deporte: any) => {
        if (!deporte.fechaAltaInicial) {
          deporte.fechaAltaInicial = deporte.fechaAlta;
        }
        if (!deporte.tieneLicencia) {
          deporte.competidor = false;
        }
        if (deporte.competidor) {
          if (!deporte.fechaAltaCompeticion) {
            deporte.fechaAltaCompeticion = deporte.fechaAlta || this.obtenerFechaHoy();
          }
          if (!deporte.fechaAltaCompetidorInicial) {
            deporte.fechaAltaCompetidorInicial =
              deporte.fechaAltaInicial || deporte.fechaAlta || this.obtenerFechaHoy();
          }
          if (!deporte.categoria) {
            deporte.categoria = categoriaPorEdad;
          }
          if (deporte.peso === '' || deporte.peso === null || deporte.peso === undefined) {
            deporte.peso = null;
          } else {
            deporte.peso = Number(deporte.peso);
          }
        } else {
          deporte.categoria = '';
          deporte.fechaAltaCompeticion = null;
          deporte.fechaAltaCompetidorInicial = null;
          deporte.peso = null;
        }
      });
    }

    // Remove deprecated single-sport fields (not needed with deportesInicial)
    delete alumnoData.deporte;
    delete alumnoData.grado;

    alumnoData.aptoParaExamen = false;

    this.endpointsService.crearAlumno(alumnoData, this.imagen).subscribe({
      next: (response) => {
        const numDeportes = alumnoData.deportesInicial?.length || 0;
        Swal.fire({
          title: 'Perfecto!',
          text: `Has creado un nuevo alumno con ${numDeportes} deporte(s)`,
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
          text: error.error || 'No has completado todos los campos requeridos',
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
      // Tarifas para Taekwondo
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
      this.showAllFields();
      // Tarifas para Pilates
      this.tiposTarifa = [
        TipoTarifa.PILATES,
      ];
      // Pilates typically doesn't have grades, so clear grade requirement
      this.alumnoData.get('grado')?.clearValidators();
      this.alumnoData.get('grado')?.updateValueAndValidity();
    } else if (selectedDeporte === 'DEFENSA_PERSONAL_FEMENINA') {
      this.showAllFields();
      // Tarifas para Defensa Personal Femenina
      this.tiposTarifa = [
        TipoTarifa.DEFENSA_PERSONAL_FEMENINA,
      ];
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
