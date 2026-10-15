import { Component, OnInit, inject } from '@angular/core';
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
import { RolFamiliar } from '../../../enums/rol-familiar';
import { Router } from '@angular/router';
import { PageScrollService } from '../../../servicios/generales/page-scroll.service';
import { calcularCategoriaPorEdad } from '../../../utilities/categoria-por-edad';
import { getGradoNombreParaDeporte } from '../../../utilities/grado-colors';
import { SearchableSelectDirective } from '../../../directives/searchable-select.directive';
import { showSuccessToast } from '../../../utils/toast.util';
import { CatalogoService } from '../../../features/catalogo/catalogo.service';
import { CatalogoTarifa } from '../../../features/catalogo/catalogo.models';
import { cuantiaEstandar } from '../../../features/catalogo/catalogo-reglas';

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
  imports: [FormsModule, CommonModule, ReactiveFormsModule, SearchableSelectDirective],
  templateUrl: './crear-alumno.component.html',
  styleUrls: ['./crear-alumno.component.scss'],
})
export class CrearAlumnoComponent implements OnInit {
  alumnoData!: FormGroup;
  imagen: File | null = null;
  imagenPreview: string | ArrayBuffer | null = null;
  rolesFamiliares: RolFamiliar[] = Object.values(RolFamiliar);
  /** Códigos de grado que el backend ofrece para la fecha de nacimiento (filtro por edad). */
  tiposGrado: string[] = [];
  grados: any[] = [];
  todosLosGrados: any[] = [];
  categorias = ['Infantil', 'Precadete', 'Cadete', 'Junior', 'Senior'];
  readonly catalogo = inject(CatalogoService);

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly router: Router,
    private readonly scrollService: PageScrollService
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
        [Validators.pattern('^[0-9]{8}[A-Za-z]$')],
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
      responsableLegalNombre: ['', Validators.maxLength(180)],
      responsableLegalNif: ['', Validators.pattern('^[0-9XYZxyz][0-9]{7}[A-Za-z]$')],
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
      rolFamiliar: [{ value: '', disabled: true }],  // Solo para tarifas con requiereRolFamiliar; deshabilitado por defecto
      grupoFamiliar: [{ value: '', disabled: true }],  // Solo para tarifas con requiereGrupoFamiliar; deshabilitado por defecto
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

    // Filter by sport: the grades the discipline's grading system offers (none if it has no system)
    if (!this.catalogo.tieneGrados(deporteSeleccionado)) {
      return [];
    }
    const gradosDeLaDisciplina = new Set<string>(
      this.catalogo.gradosSeleccionables(deporteSeleccionado).map((grado) => grado.codigo)
    );
    gradosFiltrados = gradosFiltrados.filter((grado) => gradosDeLaDisciplina.has(grado.tipoGrado));

    // Filter by age if fechaNacimiento is set
    const fechaNacimiento = this.alumnoData.get('fechaNacimiento')?.value;
    if (fechaNacimiento && this.tiposGrado.length > 0) {
      gradosFiltrados = gradosFiltrados.filter((grado) =>
        this.tiposGrado.includes(grado.tipoGrado)
      );
    }

    return gradosFiltrados;
  }

  /** D7: tarifas que admite la disciplina de la fila, en orden del catálogo (sin disciplina: ninguna). */
  obtenerTarifasFiltradas(index: number): CatalogoTarifa[] {
    const deporteSeleccionado = this.deportesFormArray.at(index).get('deporte')?.value;
    return deporteSeleccionado ? this.catalogo.tarifasDe(deporteSeleccionado) : [];
  }

  /** Tarifa elegida en la fila (del catálogo), o `undefined`. */
  private tarifaSeleccionada(index: number): CatalogoTarifa | undefined {
    return this.catalogo.tarifa(this.deportesFormArray.at(index).get('tipoTarifa')?.value);
  }

  /** La tarifa elegida pide rol familiar (bandera `requiereRolFamiliar` del catálogo). */
  requiereRolFamiliar(index: number): boolean {
    return this.tarifaSeleccionada(index)?.requiereRolFamiliar === true;
  }

  /** La tarifa elegida pide grupo familiar (bandera `requiereGrupoFamiliar` del catálogo). */
  requiereGrupoFamiliar(index: number): boolean {
    return this.tarifaSeleccionada(index)?.requiereGrupoFamiliar === true;
  }

  onDeporteChangeEnLista(index: number): void {
    const deporteControl = this.deportesFormArray.at(index).get('deporte');
    const gradoControl = this.deportesFormArray.at(index).get('grado');
    const fechaGradoControl = this.deportesFormArray.at(index).get('fechaGrado');
    const tipoTarifaControl = this.deportesFormArray.at(index).get('tipoTarifa');

    // Reset tarifa when sport changes, as valid tarifas depend on the sport
    const currentTarifa = tipoTarifaControl?.value;
    const validTarifas = this.obtenerTarifasFiltradas(index).map((tarifa) => tarifa.codigo);

    // If current tarifa is not valid for the new sport, clear it
    if (currentTarifa && !validTarifas.includes(currentTarifa)) {
      tipoTarifaControl?.setValue('');
    }

    const deporte = deporteControl?.value;
    if (this.catalogo.tieneGrados(deporte)) {
      // The discipline has a grading system: grade and grade date are required
      gradoControl?.setValidators(Validators.required);
      fechaGradoControl?.setValidators(Validators.required);
    } else {
      // No grading system (e.g. Pilates): clear validators and values
      gradoControl?.clearValidators();
      gradoControl?.setValue('');
      fechaGradoControl?.clearValidators();
      fechaGradoControl?.setValue('');
    }
    gradoControl?.updateValueAndValidity();
    fechaGradoControl?.updateValueAndValidity();

    this.reiniciarLicenciaYCompeticionSiNoAplican(index);
  }

  /**
   * Al cambiar de disciplina, los datos de licencia y de competición que la nueva disciplina no admite
   * se reinician: los controles quedan ocultos y, si no, el valor anterior se enviaría al guardar.
   */
  private reiniciarLicenciaYCompeticionSiNoAplican(index: number): void {
    const deporteForm = this.deportesFormArray.at(index);
    const deporte = deporteForm.get('deporte')?.value;

    if (!this.catalogo.requiereLicencia(deporte)) {
      deporteForm.patchValue(
        {
          tieneLicencia: false,
          numeroLicencia: null,
          fechaLicencia: this.obtenerFechaHoy(),
        },
        { emitEvent: false }
      );
    }

    if (!this.catalogo.permiteCompeticion(deporte)) {
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
  }

  onTipoTarifaChangeEnLista(index: number): void {
    const tipoTarifaControl = this.deportesFormArray.at(index).get('tipoTarifa');
    const cuantiaTarifaControl = this.deportesFormArray.at(index).get('cuantiaTarifa');
    const rolFamiliarControl = this.deportesFormArray.at(index).get('rolFamiliar');
    const grupoFamiliarControl = this.deportesFormArray.at(index).get('grupoFamiliar');

    if (tipoTarifaControl?.value) {
      // Set default cuantía
      const rolFamiliar = rolFamiliarControl?.value || undefined;
      const cuantiaDefault = cuantiaEstandar(this.tarifaSeleccionada(index), rolFamiliar);
      cuantiaTarifaControl?.setValue(cuantiaDefault);

      // Tarifa que pide rol familiar
      if (this.requiereRolFamiliar(index)) {
        rolFamiliarControl?.setValidators(Validators.required);
        rolFamiliarControl?.enable();
        grupoFamiliarControl?.clearValidators();
        grupoFamiliarControl?.setValue('');
        grupoFamiliarControl?.disable();
      }
      // Tarifa que pide grupo familiar
      else if (this.requiereGrupoFamiliar(index)) {
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
    if (!this.requiereRolFamiliar(index)) {
      return;
    }

    const rolFamiliar = event.target.value;
    const cuantiaTarifaControl = deporteForm.get('cuantiaTarifa');
    const cuantiaDefault = cuantiaEstandar(this.tarifaSeleccionada(index), rolFamiliar);
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
      next: (tiposGrado: string[]) => {
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

    // First, filter by sport: the grades its grading system offers
    if (deporteSeleccionado) {
      const gradosDeLaDisciplina = new Set<string>(
        this.catalogo.gradosSeleccionables(deporteSeleccionado).map((grado) => grado.codigo)
      );
      gradosFiltrados = gradosFiltrados.filter((grado) => gradosDeLaDisciplina.has(grado.tipoGrado));
    }

    // Then, filter by age if tiposGrado has values
    if (this.tiposGrado.length > 0) {
      gradosFiltrados = gradosFiltrados.filter((grado) =>
        this.tiposGrado.includes(grado.tipoGrado)
      );
    }

    this.grados = gradosFiltrados;
  }

  private obtenerCategoriaPorEdadActual(deporte?: string | null): string {
    return calcularCategoriaPorEdad(this.alumnoData.get('fechaNacimiento')?.value || null, deporte);
  }

  private actualizarCategoriasPorEdad(): void {
    this.deportesFormArray.controls.forEach((control) => {
      const competidor = control.get('competidor')?.value;
      const categoriaControl = control.get('categoria');
      const deporteSeleccionado = control.get('deporte')?.value || this.catalogo.primeraDisciplinaActiva();
      if (!categoriaControl) {
        return;
      }

      if (competidor) {
        const categoriaPorEdad = this.obtenerCategoriaPorEdadActual(deporteSeleccionado);
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
    const deporteSeleccionado = deporteForm.get('deporte')?.value || this.catalogo.primeraDisciplinaActiva();
    if (!categoriaControl) {
      return;
    }

    if (!competidor) {
      categoriaControl.setValue('');
      if (fechaAltaCompeticionControl) {
        fechaAltaCompeticionControl.setValue('');
      }
      if (fechaAltaCompetidorInicialControl) {
        fechaAltaCompetidorInicialControl.setValue('');
      }
      return;
    }

    const fechaAlta = deporteForm.get('fechaAlta')?.value || this.obtenerFechaHoy();
    const fechaAltaInicial = deporteForm.get('fechaAltaInicial')?.value || fechaAlta;
    const categoriaPorEdad = this.obtenerCategoriaPorEdadActual(deporteSeleccionado);
    if (categoriaPorEdad && (!categoriaControl.value || !categoriaControl.dirty)) {
      categoriaControl.setValue(categoriaPorEdad);
    }
    if (fechaAltaCompeticionControl && !fechaAltaCompeticionControl.value) {
      fechaAltaCompeticionControl.setValue(fechaAlta);
    }
    if (fechaAltaCompetidorInicialControl && !fechaAltaCompetidorInicialControl.value) {
      fechaAltaCompetidorInicialControl.setValue(fechaAltaInicial);
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
    const nifNormalizado =
      typeof alumnoData.nif === 'string' ? alumnoData.nif.trim().toUpperCase() : '';
    alumnoData.nif = nifNormalizado === '' ? null : nifNormalizado;
    const responsableNif = typeof alumnoData.responsableLegalNif === 'string' ? alumnoData.responsableLegalNif.trim().toUpperCase() : '';
    alumnoData.responsableLegalNif = responsableNif === '' ? null : responsableNif;

    // For each sport, set fechaAltaInicial to fechaAlta if not provided
    if (alumnoData.deportesInicial) {
      alumnoData.deportesInicial.forEach((deporteData: any) => {
        if (!deporteData.fechaAltaInicial) {
          deporteData.fechaAltaInicial = deporteData.fechaAlta;
        }
        if (
          !this.catalogo.permiteCompeticion(deporteData.deporte) ||
          (this.catalogo.requiereLicencia(deporteData.deporte) && !deporteData.tieneLicencia)
        ) {
          deporteData.competidor = false;
        }
        if (deporteData.competidor) {
          if (!deporteData.fechaAltaCompeticion) {
            deporteData.fechaAltaCompeticion = deporteData.fechaAlta || this.obtenerFechaHoy();
          }
          if (!deporteData.fechaAltaCompetidorInicial) {
            deporteData.fechaAltaCompetidorInicial =
              deporteData.fechaAltaInicial || deporteData.fechaAlta || this.obtenerFechaHoy();
          }
          if (!deporteData.categoria) {
            // Calcular categoría según el deporte específico
            deporteData.categoria = this.obtenerCategoriaPorEdadActual(deporteData.deporte || this.catalogo.primeraDisciplinaActiva());
          }
          if (deporteData.peso === '' || deporteData.peso === null || deporteData.peso === undefined) {
            deporteData.peso = null;
          } else {
            deporteData.peso = Number(deporteData.peso);
          }
        } else {
          deporteData.categoria = '';
          deporteData.fechaAltaCompeticion = null;
          deporteData.fechaAltaCompetidorInicial = null;
          deporteData.peso = null;
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
        showSuccessToast(`Alumno creado con ${numDeportes} deporte(s)`);
        // Navigate directly to edit page with the newly created alumno's ID
        if (response && response.id) {
          this.router.navigate(['/alumnosEditar', response.id]);
        } else {
          // Fallback to list page if ID is not available
          this.router.navigate(['/alumnosListar']);
        }
      },
      error: (error) => {
        // Move before opening the modal so the overlay locks the final viewport.
        this.scrollService.scrollToTop('auto');
        void Swal.fire({
          title: 'Error en la petición',
          text: error.error || 'No has completado todos los campos requeridos',
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

  getGradoNombre(grado: any, deporte?: string): string {
    const deporteSeleccionado = deporte || this.alumnoData.get('deporte')?.value;
    return getGradoNombreParaDeporte(grado?.tipoGrado, deporteSeleccionado);
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
