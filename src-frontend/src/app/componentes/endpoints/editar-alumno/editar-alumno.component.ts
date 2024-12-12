import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule, Location } from '@angular/common';
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
import { ActivatedRoute, Router } from '@angular/router';
import { Producto } from '../../../interfaces/producto';
import { ProductoAlumnoDTO } from '../../../interfaces/producto-alumno-dto';
import { formatDate } from '../../../utilities/formatear-fecha';

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
  alumnoId: number | null = null;
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
  tipoTarifaEditado: boolean = false;
  deportes = ['TAEKWONDO', 'KICKBOXING', 'PILATES'];
  grados: any[] = [];
  todosLosGrados: any[] = [];
  products: Producto[] = [];
  selectedProductoId: number | null = null;
  productosAlumno: ProductoAlumnoDTO[] = [];
  mostrarModalConvocatorias = false;
  mostrarModalEliminarConvocatorias = false;
  convocatoriasDisponibles: any[] = [];
  convocatoriaActual: any | null = null;
  convocatoriasDelAlumno: any[] = [];

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly fb: FormBuilder,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly location: Location
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
        telefono: [
          '',
          [
            Validators.required,
            Validators.pattern('^[0-9]+$'),
            Validators.maxLength(9),
          ],
        ],
        tipoTarifa: ['', Validators.required],
        deporte: ['', Validators.required],
        cuantiaTarifa: ['', Validators.required],
        fechaAlta: ['', Validators.required],
        fechaBaja: [''],
        autorizacionWeb: [true, Validators.required],
        grado: [''],
        competidor: [false],
        peso: [''],
        fechaPeso: [''],
        tieneLicencia: [false],
        numeroLicencia: [''],
        fechaLicencia: [''],
        aptoParaExamen: [false],
      },
      {
        validators: [
          this.fechaBajaPosteriorAFechaAltaValidator,
          this.fechaNacimientoPosteriorAFechaAltaValidator,
        ],
      }
    );
  }

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      const nombre = params['nombre'];
      if (nombre) {
        this.nombreFiltro = nombre;
        this.filtrarPorNombre();
      }
    });

    // Obtener la lista de alumnos
    this.route.params.subscribe((params) => {
      const idParam = params['id'];
      if (idParam) {
        this.alumnoId = +idParam;
        this.mostrarFormulario = true;
        this.obtenerAlumnoPorId(this.alumnoId);
      }
      this.obtenerAlumnos();
    });

    if (this.alumnoId) {
      this.cargarConvocatoriasDelAlumno(this.alumnoId);
    }

    this.alumnoForm.get('deporte')?.valueChanges.subscribe((event: Event) => {
      this.onDeporteChange(event);
    });

    // Asegúrate de que el formulario escuche los cambios en la fecha de nacimiento
    this.alumnoForm
      .get('fechaNacimiento')
      ?.valueChanges.subscribe((fechaNacimiento: string) => {
        if (fechaNacimiento) {
          this.obtenerGradosDisponibles(fechaNacimiento);
        }
      });

    // Escucha cambios en el tipo de descuento para actualizar la cuantía
    this.alumnoForm
      .get('tipoTarifa')
      ?.valueChanges.subscribe((tipoTarifa: TipoTarifa) => {
        if (this.tipoTarifaEditado) {
          // Solo se actualiza si el usuario cambió el tipo de descuento
          const nuevaCuantia = this.asignarCuantiaTarifa(tipoTarifa);
          this.alumnoForm.get('cuantiaTarifa')?.setValue(nuevaCuantia);
        }
        this.tipoTarifaEditado = true;
      });

    // Observador para cambios en el campo competidor
    this.alumnoForm
      .get('competidor')
      ?.valueChanges.subscribe((isCompetidor: boolean) => {
        this.handleCompetidorFields(isCompetidor);
      });

    this.alumnoForm
      .get('tieneLicencia')
      ?.valueChanges.subscribe((tieneLicencia: boolean) => {
        this.handleLicenciaFields(tieneLicencia);
      });

    // Validaciones adicionales para fechas: fechaBaja debe ser posterior a fechaAlta y la fecha de nacimiento debe ser anterior a la fechaAlta
    this.alumnoForm.setValidators([
      this.fechaBajaPosteriorAFechaAltaValidator,
      this.fechaNacimientoPosteriorAFechaAltaValidator,
    ]);

    this.cargarGrados();
  }

  obtenerAlumnoPorId(id: number) {
    this.endpointsService.obtenerAlumnoPorId(id).subscribe({
      next: (alumno) => {
        this.alumnoEditado = alumno;
        this.imagenPreview = alumno.fotoAlumno?.url
          ? alumno.fotoAlumno.url
          : '../../../../assets/media/default.webp';
        this.alumnoId = alumno.id;

        this.configurarFormulario(alumno);
      },
      error: (error) => {
        // Manejar el error si el alumno no se encuentra
        Swal.fire({
          title: 'Error',
          text: 'No se pudo obtener el alumno con el ID especificado.',
          icon: 'error',
        });
        this.router.navigate(['/alumnosEditar']);
      },
    });
  }

  configurarFormulario(alumno: any): void {
    const fechaNacimiento = formatDate(alumno.fechaNacimiento);
    if (fechaNacimiento) {
      this.obtenerGradosDisponibles(fechaNacimiento);
    }

    const fechaAlta = formatDate(alumno.fechaAlta);
    const fechaBaja = alumno.fechaBaja ? formatDate(alumno.fechaBaja) : '';
    const peso = alumno.peso || '';
    const fechaPeso = alumno.fechaPeso ? formatDate(alumno.fechaPeso) : '';
    const numeroLicencia = alumno.numeroLicencia || '';
    const fechaLicencia = alumno.fechaLicencia
      ? formatDate(alumno.fechaLicencia)
      : '';
    const grado = alumno.grado || '';
    const aptoParaExamen = alumno.aptoParaExamen ?? false;

    this.alumnoForm.patchValue({
      ...alumno,
      fechaNacimiento: fechaNacimiento,
      deporte: alumno.deporte,
      fechaAlta: fechaAlta,
      fechaBaja: fechaBaja,
      autorizacionWeb: alumno.autorizacionWeb,
      cuantiaTarifa: alumno.cuantiaTarifa,
      peso: peso,
      fechaPeso: fechaPeso,
      competidor: alumno.competidor,
      numeroLicencia: numeroLicencia,
      fechaLicencia: fechaLicencia,
      tieneLicencia: alumno.tieneLicencia,
      grado: grado,
      aptoParaExamen: aptoParaExamen,
    });

    this.onDeporteChange(alumno.deporte);
  }

  obtenerProductosAlumno(alumnoId: number, alumno: any) {
    this.endpointsService.obtenerProductosDelAlumno(alumnoId).subscribe({
      next: (productos) => {
        this.productosAlumno = productos;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron obtener los productos del alumno.',
          icon: 'error',
        });
        alumno.productos = [];
      },
    });
  }

  handleLicenciaFields(tieneLicencia: boolean) {
    const numeroLicenciaControl = this.alumnoForm.get('numeroLicencia');
    const fechaLicenciaControl = this.alumnoForm.get('fechaLicencia');

    if (tieneLicencia) {
      numeroLicenciaControl?.setValidators([Validators.required]);
      fechaLicenciaControl?.setValidators([Validators.required]);

      numeroLicenciaControl?.enable();
      fechaLicenciaControl?.enable();
    } else {
      numeroLicenciaControl?.clearValidators();
      fechaLicenciaControl?.clearValidators();

      numeroLicenciaControl?.disable();
      fechaLicenciaControl?.disable();
    }

    numeroLicenciaControl?.updateValueAndValidity();
    fechaLicenciaControl?.updateValueAndValidity();
  }
  /**
   * Controla la habilitación y validación de los campos relacionados con el competidor.
   */
  handleCompetidorFields(isCompetidor: boolean) {
    const pesoControl = this.alumnoForm.get('peso');
    const fechaPesoControl = this.alumnoForm.get('fechaPeso');
    const categoriaControl = this.alumnoForm.get('categoria');

    if (isCompetidor) {
      pesoControl?.setValidators([Validators.required]);
      fechaPesoControl?.setValidators([Validators.required]);

      pesoControl?.enable();
      fechaPesoControl?.enable();

      // Si no hay fecha de peso asignada, establecer la fecha actual
      if (!fechaPesoControl?.value) {
        fechaPesoControl?.setValue(this.getFechaActual());
      }
    } else {
      // Limpiar y deshabilitar los campos si no es competidor
      pesoControl?.clearValidators();
      fechaPesoControl?.clearValidators();

      pesoControl?.disable();
      fechaPesoControl?.disable();
      categoriaControl?.setValue(null); // Eliminar la categoría del formulario
      this.alumnoEditado.categoria = null; // Eliminar la categoría del alumno editado
    }

    pesoControl?.updateValueAndValidity();
    fechaPesoControl?.updateValueAndValidity();
  }

  obtenerGradosDisponibles(fechaNacimiento: string) {
    this.endpointsService
      .obtenerGradosPorFechaNacimiento(fechaNacimiento)
      .subscribe({
        next: (grados: TipoGrado[]) => {
          this.tiposGrado = grados; // Asigna la lista de grados recibidos
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudieron cargar los grados disponibles.',
            icon: 'error',
          });
        },
      });
  }

  cargarGrados(): void {
    this.endpointsService.obtenerGrados().subscribe({
      next: (grados) => {
        this.todosLosGrados = grados; // Almacena todos los grados
        this.grados = grados; // Inicialmente, muestra todos los grados
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

  obtenerAlumnos() {
    this.endpointsService
      .obtenerAlumnos(
        this.paginaActual,
        this.tamanoPagina,
        this.nombreFiltro,
        this.mostrarInactivos
      )
      .subscribe({
        next: (response) => {
          this.alumnos = response.content;

          this.alumnos.forEach((alumno) => {
            this.obtenerProductosAlumno(alumno.id, alumno);
            this.cargarConvocatoriasDelAlumno(alumno.id);
          });

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
      }
    });
  }

  actualizarAlumno(id: number) {
    const formData = new FormData();
    formData.append('alumnoEditado', JSON.stringify(this.alumnoForm.value));

    if (this.alumnoEditado.fotoAlumno === null) {
      formData.append('file', 'null');
    } else if (this.alumnoEditado.fotoAlumno) {
      formData.append('file', this.alumnoEditado.fotoAlumno);
    }
    this.endpointsService.actualizarAlumno(id, formData).subscribe({
      next: (response) => {
        Swal.fire({
          title: '¡Bien!',
          text: '¡Alumno actualizado correctamente!',
          icon: 'success',
        });
        this.mostrarFormulario = false;
        this.obtenerAlumnos();
        this.router.navigate(['/alumnosEditar']);
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

  eliminarFoto(id: number) {
    this.endpointsService.eliminarImagenAlumno(id).subscribe({
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

  cargarConvocatoriasDelAlumno(alumnoId: number): void {
    this.endpointsService.obtenerConvocatoriasDeAlumno(alumnoId).subscribe({
      next: (convocatorias) => {
        this.convocatoriasDelAlumno = convocatorias;
      },
      error: (error) => {
        console.error('Error al obtener convocatorias del alumno:', error);
        this.convocatoriasDelAlumno = [];
      },
    });
  }
  

  agregarAConvocatoriaEspecifica(convocatoria: any): void {
    if (!this.alumnoId) {
      Swal.fire({
        title: 'Error',
        text: 'No se ha seleccionado un alumno válido.',
        icon: 'error',
      });
      return;
    }
    this.endpointsService
      .agregarAlumnoAConvocatoria(this.alumnoId, convocatoria.id)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Alumno agregado a la convocatoria seleccionada',
            icon: 'success',
          });
          this.cerrarModalConvocatorias();
          this.cargarConvocatoriasDelAlumno(this.alumnoId!);
          this.obtenerProductosAlumno(this.alumnoId!, this.alumnoEditado);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error,
            icon: 'error',
          });
        },
      });
  }

  abrirModalConvocatorias(alumno: any): void {
    this.alumnoId = alumno.id;
    this.cargarConvocatoriasDisponibles(alumno);
    this.mostrarModalConvocatorias = true;
  }
  
  cerrarModalConvocatorias(): void {
    this.mostrarModalConvocatorias = false;
  }
  
  abrirModalEliminarConvocatorias(alumno: any): void {
    this.alumnoId = alumno.id;
    this.cargarConvocatoriasDelAlumno(alumno.id);
    this.mostrarModalEliminarConvocatorias = true;
  }
  
  cerrarModalEliminarConvocatorias(): void {
    this.mostrarModalEliminarConvocatorias = false;
  }
  

  eliminarDeConvocatoriaSeleccionada(convocatoria: any): void {
    if (!this.alumnoId) return;

    this.cerrarModalEliminarConvocatorias();

    Swal.fire({
      title: '¿Estás seguro?',
      text: `Eliminarás al alumno de la convocatoria de ${convocatoria.deporte} del ${formatDate(convocatoria.fechaConvocatoria)}`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, eliminar',
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarAlumnoDeConvocatoria(this.alumnoId!, convocatoria.id).subscribe({
          next: () => {
            Swal.fire('Eliminado', 'El alumno ha sido eliminado de la convocatoria.', 'success');
            this.cargarConvocatoriasDelAlumno(this.alumnoId!);
            this.obtenerProductosAlumno(this.alumnoId!, this.alumnoEditado);
          },
          error: (error) => {
            Swal.fire('Error', 'No se pudo eliminar al alumno de la convocatoria.', 'error');
          },
        });
      }
      else if (result.dismiss === Swal.DismissReason.cancel) {
        this.abrirModalEliminarConvocatorias({ id: this.alumnoId });
      }
    });
  }
  
  cambiarPagina(pageNumber: number): void {
    this.paginaActual = pageNumber;
    this.obtenerAlumnos();
  }

  alternarFormulario(alumno: any): void {
    this.mostrarFormulario = !this.mostrarFormulario;

    if (this.mostrarFormulario) {
      this.alumnoEditado = { ...alumno };
      this.imagenPreview = alumno.fotoAlumno?.url
        ? alumno.fotoAlumno.url
        : '../../../../assets/media/default.webp';
      this.tipoTarifaEditado = false;
      this.configurarFormulario(alumno);

      // Navegar a la ruta con el ID del alumno
      this.router.navigate(['/alumnosEditar', alumno.id]);
    } else {
      // Navegar a la ruta sin ID cuando se cierra el formulario
      this.router.navigate(['/alumnosEditar']);
    }
  }

  // Método actualizado para recibir el evento y obtener el valor como string
  onDeporteChange(event: Event | string): void {
    const deporteSeleccionado =
      typeof event === 'string'
        ? event
        : (event.target as HTMLSelectElement).value;
    this.resetFormControls();

    if (deporteSeleccionado === 'TAEKWONDO') {
      this.showAllFields();
      this.tiposTarifa = Object.values(TipoTarifa);
      this.grados = this.todosLosGrados;
    } else if (deporteSeleccionado === 'KICKBOXING') {
      this.showAllFields();
      this.tiposTarifa = [
        TipoTarifa.PADRES_HIJOS,
        TipoTarifa.HERMANOS,
        TipoTarifa.FAMILIAR,
      ];
      this.filtrarGradosParaKickboxing();
    } else if (deporteSeleccionado === 'PILATES') {
      this.hideFieldsForPilates();
      this.tiposTarifa = [TipoTarifa.PILATES];
      this.grados = [];
    }
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

  licenciaEnVigor(fechaLicencia: string): boolean {
    const fechaActual = new Date();
    const fechaLic = new Date(fechaLicencia);

    const diferenciaAnios = fechaActual.getFullYear() - fechaLic.getFullYear();

    // Si la diferencia es menor a 1 año, la licencia está en vigor
    return diferenciaAnios < 1;
  }

  asignarCuantiaTarifa(tipoTarifa: TipoTarifa): number {
    switch (tipoTarifa) {
      case TipoTarifa.PILATES:
        return 30.0;
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
        throw new Error('Tipo de descuento no válido');
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

  abrirModal(imagenUrl: string | null) {
    const modal = document.getElementById('imageModal');
    const modalImg = document.getElementById('imgAmpliada') as HTMLImageElement;

    if (!imagenUrl || imagenUrl.trim() === '') {
      Swal.fire({
        icon: 'error',
        title: 'Imagen no disponible',
        text: 'No hay imagen disponible para mostrar.',
      });
      return; // Detenemos la ejecución si no hay imagen
    }

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
  
  cargarConvocatoriasDisponibles(alumno: any): void {
    const deporte = alumno.deporte; // Obtenemos el deporte del alumno
  
    this.endpointsService.obtenerConvocatorias(deporte).subscribe({
      next: (convocatorias) => {
        const hoy = new Date();
  
        // Encuentra la convocatoria actual basándote en la fecha de hoy
        this.convocatoriaActual = convocatorias.find((convocatoria) => {
          const fechaConvocatoria = new Date(convocatoria.fechaConvocatoria);
          return (
            fechaConvocatoria.getFullYear() === hoy.getFullYear() &&
            fechaConvocatoria.getMonth() === hoy.getMonth() &&
            fechaConvocatoria.getDate() === hoy.getDate()
          );
        });
  
        // Filtra convocatorias excluyendo la actual
        this.convocatoriasDisponibles = convocatorias.filter(
          (convocatoria) => convocatoria.id !== this.convocatoriaActual?.id
        );
      },
      error: (error) => {
        console.error('Error al obtener convocatorias disponibles:', error);
      },
    });
  }

  resetFormControls(): void {
    // Remover validaciones de campos que podrían cambiar
    this.alumnoForm.get('tipoTarifa')?.clearValidators();
    this.alumnoForm.get('grado')?.clearValidators();
    this.alumnoForm.get('competidor')?.clearValidators();
    this.alumnoForm.get('tieneLicencia')?.clearValidators();
    this.alumnoForm.get('numeroLicencia')?.clearValidators();
    this.alumnoForm.get('fechaLicencia')?.clearValidators();

    // Actualizar validez de los campos
    this.alumnoForm.get('tipoTarifa')?.updateValueAndValidity();
    this.alumnoForm.get('grado')?.updateValueAndValidity();
    this.alumnoForm.get('competidor')?.updateValueAndValidity();
    this.alumnoForm.get('tieneLicencia')?.updateValueAndValidity();
    this.alumnoForm.get('numeroLicencia')?.updateValueAndValidity();
    this.alumnoForm.get('fechaLicencia')?.updateValueAndValidity();

    // Habilitar todos los campos
    this.alumnoForm.get('tipoTarifa')?.enable();
    this.alumnoForm.get('grado')?.enable();
    this.alumnoForm.get('competidor')?.enable();
    this.alumnoForm.get('tieneLicencia')?.enable();
    this.alumnoForm.get('numeroLicencia')?.enable();
    this.alumnoForm.get('fechaLicencia')?.enable();
  }

  showAllFields(): void {
    // Añadir validaciones necesarias
    this.alumnoForm.get('tipoTarifa')?.setValidators(Validators.required);
    this.alumnoForm.get('grado')?.setValidators(Validators.required);

    // Actualizar validez de los campos
    this.alumnoForm.get('tipoTarifa')?.updateValueAndValidity();
    this.alumnoForm.get('grado')?.updateValueAndValidity();
  }

  hideFieldsForPilates(): void {
    this.alumnoForm.get('tipoTarifa')?.setValue(TipoTarifa.PILATES);

    this.alumnoForm.get('grado')?.disable();
    this.alumnoForm.get('grado')?.clearValidators();
    this.alumnoForm.get('grado')?.setValue(null);

    this.alumnoForm.get('competidor')?.disable();
    this.alumnoForm.get('competidor')?.setValue(false);

    this.alumnoForm.get('tieneLicencia')?.disable();
    this.alumnoForm.get('tieneLicencia')?.setValue(false);

    this.alumnoForm.get('numeroLicencia')?.disable();
    this.alumnoForm.get('numeroLicencia')?.clearValidators();
    this.alumnoForm.get('numeroLicencia')?.setValue(null);

    this.alumnoForm.get('fechaLicencia')?.disable();
    this.alumnoForm.get('fechaLicencia')?.clearValidators();
    this.alumnoForm.get('fechaLicencia')?.setValue(null);

    // Actualizar validez de los campos
    this.alumnoForm.get('tipoTarifa')?.updateValueAndValidity();
    this.alumnoForm.get('grado')?.updateValueAndValidity();
    this.alumnoForm.get('numeroLicencia')?.updateValueAndValidity();
    this.alumnoForm.get('fechaLicencia')?.updateValueAndValidity();
  }

  filtrarGradosParaKickboxing(): void {
    const gradosCompletos = [
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
    this.grados = this.todosLosGrados.filter((grado) =>
      gradosCompletos.includes(grado.tipoGrado)
    );
  }

  getGradoNombre(grado: any): string {
    const deporteSeleccionado = this.alumnoForm.get('deporte')?.value;

    if (deporteSeleccionado === 'KICKBOXING' && grado.tipoGrado === 'ROJO') {
      return 'MARRON';
    }

    return grado.tipoGrado;
  }

  calcularTotal(precio: number, cantidad: number): number {
    return precio * cantidad;
  }

  navegarAGestionProductos(alumnoId: number) {
    this.router.navigate(['/alumnos', alumnoId, 'productos']);
  }

  volver() {
    this.location.back();
  }
}
