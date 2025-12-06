import { Component, ElementRef, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  FormsModule,
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
  AbstractControl,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { TipoTarifa } from '../../../enums/tipo-tarifa';
import { RolFamiliar } from '../../../enums/rol-familiar';
import { TipoGrado } from '../../../enums/tipo-grado';
import { ProductoAlumnoDTO } from '../../../interfaces/producto-alumno-dto';
import { AlumnoDeporteDTO, AlumnoConDeportesDTO } from '../../../interfaces/alumno-deporte-dto';
import { Deporte, DeporteLabels, getDeporteLabel } from '../../../enums/deporte';
import { formatDate } from '../../../utilities/formatear-fecha';
import { getGradoTextStyle } from '../../../utilities/grado-colors';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-editar-alumno',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    PaginacionComponent,
  ],
  templateUrl: './editar-alumno.component.html',
  styleUrls: ['./editar-alumno.component.scss'],
})
export class EditarAlumnoComponent implements OnInit, OnDestroy {
  alumno: any = null;
  cargando: boolean = true; // Loading state - start as true to show skeleton immediately

  // Pagination - each page represents one alumno
  paginaActual: number = 1;
  totalPaginas: number = 0;
  tamanoPagina: number = 1;
  alumnosIds: number[] = []; // List of all alumno IDs for navigation

  // Subscriptions management
  private routeSubscription?: Subscription;

  // Control de formulario
  mostrarFormulario: boolean = false;
  alumnoForm: FormGroup;
  imagenPreview: string | null = null;

  // Otras propiedades
  alumnoId: number | null = null;
  tipoTarifaEditado: boolean = false;
  mostrarInactivos: boolean = false;

  // Opciones de dropdown
  tiposTarifa = Object.values(TipoTarifa);
  rolesFamiliares = Object.values(RolFamiliar);
  tiposGrado = Object.values(TipoGrado);
  deportes = [
    'TAEKWONDO',
    'KICKBOXING',
  ];
  todosLosGrados: any[] = [];
  grados: any[] = [];

  // Productos del alumno
  productosAlumno: ProductoAlumnoDTO[] = [];
  productosPaginados: ProductoAlumnoDTO[] = [];
  paginaActualProductos = 1;
  tamanoPaginaProductos = 4;
  totalPaginasProductos = 0;

  // Convocatorias
  convocatoriasDisponibles: any[] = [];
  convocatoriasDelAlumno: any[] = [];
  convocatoriaActual: any | null = null;
  mostrarModalConvocatorias = false;
  mostrarModalEliminarConvocatorias = false;
  documentosAlumno: any[] = [];

  // Multi-sport properties
  deportesDelAlumno: AlumnoDeporteDTO[] = [];
  deporteActivo: string = Deporte.TAEKWONDO;
  mostrarModalAgregarDeporte = false;
  mostrarModalActualizarGrado = false;
  nuevoDeporte: string = '';
  gradoInicialDeporte: string = '';
  deporteParaActualizarGrado: string = '';
  nuevoGradoActualizar: string = '';
  deportesDisponibles: Deporte[] = [];
  DeporteEnum = Deporte;
  DeporteLabels = DeporteLabels;

  // Para manipular el input file
  @ViewChild('inputFile', { static: false }) inputFile!: ElementRef;

  // Alumno a editar (cuando abrimos el formulario)
  alumnoEditado: any = {
    tipoTarifa: null,
    tipoGrado: null,
  };

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly alumnoService: AlumnoService,
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
        rolFamiliar: [{ value: RolFamiliar.NINGUNO, disabled: true }],
        grupoFamiliar: [{ value: '', disabled: true }],
        deporte: [''], // DEPRECATED: No longer required - managed via tabs
        cuantiaTarifa: ['', Validators.required],
        fechaAlta: ['', Validators.required],
        fechaAltaInicial: [''],
        fechaBaja: [''],
        autorizacionWeb: [true, Validators.required],
        grado: [''], // DEPRECATED: No longer required - managed via tabs
        competidor: [false],
        peso: [''],
        fechaPeso: [''],
        tieneLicencia: [false],
        numeroLicencia: [''],
        fechaLicencia: [''],
        aptoParaExamen: [false], // DEPRECATED: No longer required - managed via tabs
        tieneDiscapacidad: [false],
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
    this.cargarGrados();

    // Load the list of IDs first, then handle route changes
    this.cargarTodosLosAlumnosIds();

    // DEPRECATED: Deporte is now managed via tabs, not through form
    // this.alumnoForm.get('deporte')?.valueChanges.subscribe((valor) => {
    //   this.onDeporteChange(valor);
    // });

    this.alumnoForm.get('fechaNacimiento')?.valueChanges.subscribe((valor) => {
      if (valor) {
        this.obtenerGradosDisponibles(valor);
      }
    });
    this.alumnoForm.get('tipoTarifa')?.valueChanges.subscribe((tipoTarifa) => {
      if (this.tipoTarifaEditado) {
        const nuevaCuantia = this.asignarCuantiaTarifa(tipoTarifa);
        this.alumnoForm.get('cuantiaTarifa')?.setValue(nuevaCuantia);
      }
      this.tipoTarifaEditado = true;

      // Handle family fields
      this.onTipoTarifaChange(tipoTarifa);
    });
    this.alumnoForm.get('competidor')?.valueChanges.subscribe((isCompetidor) => {
      this.handleCompetidorFields(isCompetidor);
    });
    this.alumnoForm.get('tieneLicencia')?.valueChanges.subscribe((valor) => {
      this.handleLicenciaFields(valor);
    });
  }


  /**
   * Loads all alumno IDs to know the total count and which alumno corresponds to each page
   * Page 1 = first alumno, Page 2 = second alumno, etc.
   * @param callback Optional callback to execute after IDs are loaded
   */
  cargarTodosLosAlumnosIds(callback?: () => void): void {
    this.endpointsService.obtenerAlumnosSinPaginar(this.mostrarInactivos).subscribe({
      next: (alumnos: any[]) => {
        // Extract IDs from the alumnos array
        this.alumnosIds = alumnos.map(alumno => alumno.id);
        this.totalPaginas = this.alumnosIds.length;

        // Set up route parameter subscription (only once)
        if (!this.routeSubscription) {
          this.setupRouteSubscription();
        }

        // Execute callback if provided
        if (callback) {
          callback();
        }
      },
      error: () => {
        this.cargando = false;
        Swal.fire({
          title: 'Error',
          text: 'No se pudo obtener la lista de alumnos.',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Sets up subscription to route parameters to handle navigation
   */
  private setupRouteSubscription(): void {
    // Unsubscribe from previous subscription if it exists
    if (this.routeSubscription) {
      this.routeSubscription.unsubscribe();
    }

    // Create new subscription
    this.routeSubscription = this.route.params.subscribe((params) => {
      const idParam = +params['id'];

      if (idParam) {
        this.procesarParametroRuta(idParam);
      } else if (this.alumnosIds.length > 0) {
        // No ID in URL, navigate to first alumno by its ID
        const firstAlumnoId = this.alumnosIds[0];
        this.router.navigate(['/alumnosEditar', firstAlumnoId], { replaceUrl: true });
      }
    });
  }

  ngOnDestroy(): void {
    // Clean up subscription
    if (this.routeSubscription) {
      this.routeSubscription.unsubscribe();
    }
  }

  /**
   * Process the route parameter - always treated as an alumno ID
   */
  private procesarParametroRuta(idParam: number): void {
    // Set loading to true and clear alumno data immediately when route changes
    this.cargando = true;
    this.alumno = null;

    // Check if this alumno ID exists in our current list
    const pageIndex = this.alumnosIds.indexOf(idParam);

    if (pageIndex === -1) {
      // ID not found in current list - might be a new alumno or inactive alumno
      // Try to load it directly to see if it exists
      this.intentarCargarAlumnoNoEncontrado(idParam);
    } else {
      // It's an alumno ID that exists in our list
      // Calculate the page number (1-indexed) for pagination display
      const pageNumber = pageIndex + 1;
      this.paginaActual = pageNumber;

      // Load this alumno
      this.cargarAlumno(idParam);
    }
  }

  /**
   * Attempts to load an alumno that wasn't found in the current alumnosIds array.
   * This handles cases where:
   * - A newly created alumno is being accessed
   * - An inactive alumno is being accessed while mostrarInactivos is false
   */
  private intentarCargarAlumnoNoEncontrado(alumnoId: number): void {
    this.endpointsService.obtenerAlumnoPorId(alumnoId).subscribe({
        next: (alumnoResponse: any) => {
          // Alumno exists! Check if we need to toggle inactive filter
          const esInactivo = alumnoResponse.fechaBaja != null;

          if (esInactivo && !this.mostrarInactivos) {
            // This is an inactive alumno but we're not showing inactive ones
            // Toggle the filter and reload the list
            this.mostrarInactivos = true;
            this.cargarTodosLosAlumnosIds(() => {
              this.navegarAAlumno(alumnoId);
            });
          } else {
            // Alumno exists and matches our filter, but wasn't in the array
            // This means the array needs to be refreshed (e.g., newly created alumno)
            this.cargarTodosLosAlumnosIds(() => {
              this.navegarAAlumno(alumnoId);
            });
          }
        },
        error: () => {
          // Alumno doesn't exist - stop loading
          this.cargando = false;

          // Show error and navigate to first alumno if available
          Swal.fire({
            title: 'Alumno no encontrado',
            text: 'El alumno solicitado no existe.',
            icon: 'error',
            timer: 2000,
          });
          if (this.alumnosIds.length > 0) {
            const firstAlumnoId = this.alumnosIds[0];
            this.router.navigate(['/alumnosEditar', firstAlumnoId], { replaceUrl: true });
          }
        },
      });
  }

  /**
   * Navigates to a specific alumno by ID, updating the page number accordingly
   */
  private navegarAAlumno(alumnoId: number): void {
    const pageIndex = this.alumnosIds.indexOf(alumnoId);
    if (pageIndex === -1) {
      // Still not found after refresh - navigate to first alumno if available
      if (this.alumnosIds.length > 0) {
        const firstAlumnoId = this.alumnosIds[0];
        this.router.navigate(['/alumnosEditar', firstAlumnoId], { replaceUrl: true });
      }
    } else {
      const pageNumber = pageIndex + 1;
      this.paginaActual = pageNumber;
      this.router.navigate(['/alumnosEditar', alumnoId], { replaceUrl: true });
      this.cargarAlumno(alumnoId);
    }
  }

  cargarAlumno(id: number): void {
    this.cargando = true;
    this.endpointsService.obtenerAlumnoPorId(id)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (alumnoResponse: any) => {
          this.alumno = alumnoResponse;
          this.alumnoId = this.alumno.id;

          // Obtener productos del alumno, convocatorias, etc.
          if (this.alumnoId) {
            this.cargarDocumentosAlumno(this.alumnoId);
            this.obtenerProductosAlumno(this.alumnoId);
            this.cargarConvocatoriasDelAlumno(this.alumnoId);
            this.cargarDeportesDelAlumno(this.alumnoId);
          }
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: `No se pudo cargar el alumno con ID ${id}`,
            icon: 'error',
          });
        },
    });
  }

  private cargarDocumentosAlumno(alumnoId: number): void {
    this.endpointsService.obtenerDocumentosDeAlumno(alumnoId)
      .subscribe({
        next: (docs) => {
          this.documentosAlumno = docs;
        },
        error: () => {
          this.documentosAlumno = [];
        },
      });
  }


  /**
   * Changes to a different page (alumno) in the pagination
   * @param pageNumber The page number (1-indexed)
   */
  cambiarPagina(pageNumber: number): void {
    if (pageNumber < 1 || pageNumber > this.totalPaginas) {
      return; // Invalid page number
    }

    // Convert page number to alumno ID
    const alumnoId = this.alumnosIds[pageNumber - 1]; // Arrays are 0-indexed

    // Navigate using the alumno ID, not the page number
    this.router.navigate(['/alumnosEditar', alumnoId]);
  }

  obtenerProductosAlumno(alumnoId: number) {
    this.endpointsService.obtenerProductosDelAlumno(alumnoId).subscribe({
      next: (productos) => {
        this.productosAlumno = productos;
        this.totalPaginasProductos = Math.ceil(this.productosAlumno.length / this.tamanoPaginaProductos);
        this.cambiarPaginaProductos(1);
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron obtener los productos del alumno.',
          icon: 'error',
        });
      },
    });
  }

  cambiarPaginaProductos(pageNumber: number): void {
    if (pageNumber >= 1 && pageNumber <= this.totalPaginasProductos) {
      this.paginaActualProductos = pageNumber;
      const start = (pageNumber - 1) * this.tamanoPaginaProductos;
      const end = start + this.tamanoPaginaProductos;
      this.productosPaginados = this.productosAlumno.slice(start, end);
    }
  }

  /**
   * Carga la lista de convocatorias a las que pertenece el alumno.
   */
  cargarConvocatoriasDelAlumno(alumnoId: number): void {
    this.endpointsService.obtenerConvocatoriasDeAlumno(alumnoId).subscribe({
      next: (convocatorias) => {
        this.convocatoriasDelAlumno = convocatorias;
      },
      error: () => {
        this.convocatoriasDelAlumno = [];
      },
    });
  }

  /**
   * Abre el formulario de edición o lo cierra, en base a `this.mostrarFormulario`.
   */
  alternarFormulario(alumno: any): void {
    this.mostrarFormulario = !this.mostrarFormulario;

    if (this.mostrarFormulario) {
      // Copiamos el objeto actual del alumno en alumnoEditado
      this.alumnoEditado = { ...alumno };
      this.imagenPreview = alumno.fotoAlumno?.url
        ? alumno.fotoAlumno.url
        : '../../../../assets/media/default.webp';
      this.tipoTarifaEditado = false;
      this.configurarFormulario(alumno);

      // No need to navigate - we're already on the correct page
      // The URL already shows the page number (e.g., /alumnosEditar/5)
    } else {
      // No need to navigate - stay on the same page when closing the form
    }
  }

  /**
   * Rellena el formulario reactivo con los datos del alumno a editar.
   */
  configurarFormulario(alumno: any): void {
    const fechaNacimiento = formatDate(alumno.fechaNacimiento);
    const fechaAlta = formatDate(alumno.fechaAlta);
    const fechaAltaInicial = alumno.fechaAltaInicial ? formatDate(alumno.fechaAltaInicial) : '';
    const fechaBaja = alumno.fechaBaja ? formatDate(alumno.fechaBaja) : '';
    const peso = alumno.peso || '';
    const fechaPeso = alumno.fechaPeso ? formatDate(alumno.fechaPeso) : '';
    const numeroLicencia = alumno.numeroLicencia || '';
    const fechaLicencia = alumno.fechaLicencia
      ? formatDate(alumno.fechaLicencia)
      : '';
    const grado = alumno.grado || '';
    const aptoParaExamen = alumno.aptoParaExamen ?? false;

    // Cargar grados disponibles en base a la fecha de nacimiento
    if (fechaNacimiento) {
      this.obtenerGradosDisponibles(fechaNacimiento);
    }

    this.alumnoForm.patchValue({
      ...alumno,
      fechaNacimiento,
      deporte: alumno.deporte,
      fechaAlta,
      fechaAltaInicial,
      fechaBaja,
      autorizacionWeb: alumno.autorizacionWeb,
      cuantiaTarifa: alumno.cuantiaTarifa,
      rolFamiliar: alumno.rolFamiliar || RolFamiliar.NINGUNO,
      grupoFamiliar: alumno.grupoFamiliar || '',
      peso,
      fechaPeso,
      competidor: alumno.competidor,
      numeroLicencia,
      fechaLicencia,
      tieneLicencia: alumno.tieneLicencia,
      grado,
      aptoParaExamen,
      tieneDiscapacidad: alumno.tieneDiscapacidad,
    });
    this.onDeporteChange(alumno.deporte);

    // Enable family fields if needed when loading existing data
    if (alumno.tipoTarifa === TipoTarifa.PADRES_HIJOS) {
      this.alumnoForm.get('rolFamiliar')?.enable();
    } else if (alumno.tipoTarifa === TipoTarifa.HERMANOS) {
      this.alumnoForm.get('grupoFamiliar')?.enable();
    }
  }

  /**
   * Al enviar el formulario, se confirma con SweetAlert y después
   * se llama a actualizarAlumno().
   */
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

  /**
   * Llamada final al servicio para actualizar en el backend.
   */
  actualizarAlumno(id: number) {
    const formData = new FormData();
    formData.append('alumnoEditado', JSON.stringify(this.alumnoForm.value));

    // Si el usuario ha eliminado la imagen (o nunca la tuvo), gestionamos el file 'null'
    if (this.alumnoEditado.fotoAlumno === null) {
      formData.append('file', 'null');
    } else if (this.alumnoEditado.fotoAlumno) {
      // Guardar el nuevo fichero
      formData.append('file', this.alumnoEditado.fotoAlumno);
    }

    this.endpointsService.actualizarAlumno(id, formData).subscribe({
      next: () => {
        Swal.fire({
          title: '¡Bien!',
          text: '¡Alumno actualizado correctamente!',
          icon: 'success',
          timer: 2000,
        });
        this.mostrarFormulario = false;
        // Volvemos a recargar este mismo alumno
        this.cargarAlumno(id);
      },
      error: () => {
        Swal.fire({
          title: 'Error al actualizar',
          text: 'Error al actualizar al alumno',
          icon: 'error',
        });
      },
    });
  }

  onDocumentoSelected(event: any) {
    const file: File = event.target.files[0];
    if (!file) return;

    // Llamamos al servicio
    this.endpointsService.subirDocumentoAlumno(this.alumnoId!, file).subscribe({
      next: (doc) => {
        Swal.fire({
          title: 'Documento subido',
          text: 'El documento se ha subido correctamente.',
          icon: 'success',
          timer: 2000,
        });
        this.cargarDocumentosAlumno(this.alumnoId!);
      },
      error: (err) => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo subir el documento',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Elimina la foto del alumno actual.
   */
  eliminarFoto(id: number) {
    this.endpointsService.eliminarImagenAlumno(id).subscribe({
      next: () => {
        this.inputFile.nativeElement.value = '';
        this.alumnoEditado.fotoAlumno = null;
        this.imagenPreview = '../../../../assets/media/default.webp';
        this.cargarAlumno(id);
      },
      error: () => {
        Swal.fire({
          title: 'Error al actualizar',
          text: 'Error al actualizar al alumno',
          icon: 'error',
        });
      },
    });
  }

  eliminarDocumento(documentoId: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'Eliminarás este documento permanentemente',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, eliminar',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService
          .eliminarDocumentoAlumno(this.alumnoId!, documentoId)
          .subscribe({
            next: () => {
              Swal.fire({
                title: 'Eliminado',
                text: 'Documento eliminado correctamente.',
                icon: 'success',
                timer: 2000,
              });
              // Quitar el doc de la lista actual
              this.documentosAlumno = this.documentosAlumno.filter(
                (d) => d.id !== documentoId
              );
            },
            error: () => {
              Swal.fire({
                title: 'Error',
                text: 'No se pudo eliminar el documento.',
                icon: 'error',
              });
            },
          });
      }
    });
  }

  abrirDocumento(doc: any) {
    if (!doc.url) {
      Swal.fire({
        icon: 'error',
        title: 'Documento no disponible',
        text: 'No se encontró una URL para el documento.',
      });
      return;
    }
    // Abrir en una nueva pestaña
    window.open(doc.url, '_blank');
  }

  descargarDocumento(doc: any) {
    const alumnoId = this.alumno?.id ?? this.alumnoId;

    if (!doc?.id || !alumnoId) {
      Swal.fire({
        icon: 'error',
        title: 'No se pudo descargar',
        text: 'Falta la información necesaria del documento o del alumno.',
      });
      return;
    }

    this.endpointsService.descargarDocumentoAlumno(alumnoId, doc.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = doc.nombre || 'documento';
        link.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        Swal.fire({
          icon: 'error',
          title: 'Descarga fallida',
          text: 'No se pudo descargar el documento.',
        });
      },
    });
  }

  /**
   * Para cargar los diferentes grados disponibles (según tu lógica de backend).
   */
  cargarGrados(): void {
    this.endpointsService.obtenerGrados().subscribe({
      next: (grados) => {
        this.todosLosGrados = grados;
        this.grados = grados;
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los grados',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Obtiene desde el backend los grados disponibles para la fecha de nacimiento dada.
   */
  obtenerGradosDisponibles(fechaNacimiento: string) {
    this.endpointsService
      .obtenerGradosPorFechaNacimiento(fechaNacimiento)
      .subscribe({
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

  /**
   * Aplica filtros de deporte y edad para determinar los grados disponibles.
   */
  aplicarFiltrosGrado(): void {
    const deporteSeleccionado = this.alumnoForm.get('deporte')?.value;
    let gradosFiltrados = this.todosLosGrados;

    // First, filter by sport
    if (deporteSeleccionado === 'KICKBOXING') {
      const gradosKickboxing = new Set<string> ([
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

  /**
   * Renueva la licencia del alumno.
   */
  renovarLicencia(alumnoId: number): void {
    this.endpointsService.renovarLicencia(alumnoId).subscribe({
      next: () => {
        Swal.fire({
          title: '¡Licencia renovada!',
          text: 'Se ha añadido la licencia al perfil del alumno.',
          icon: 'success',
        });
        this.cargarAlumno(alumnoId);
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo renovar la licencia.',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Controla el check «competidor». Habilita/deshabilita campos del formulario.
   */
  handleCompetidorFields(isCompetidor: boolean) {
    const pesoControl = this.alumnoForm.get('peso');
    const fechaPesoControl = this.alumnoForm.get('fechaPeso');

    if (isCompetidor) {
      pesoControl?.setValidators([Validators.required]);
      fechaPesoControl?.setValidators([Validators.required]);
      pesoControl?.enable();
      fechaPesoControl?.enable();

      if (!fechaPesoControl?.value) {
        fechaPesoControl?.setValue(this.getFechaActual());
      }
    } else {
      pesoControl?.clearValidators();
      fechaPesoControl?.clearValidators();
      pesoControl?.disable();
      fechaPesoControl?.disable();
      pesoControl?.setValue(null);
      fechaPesoControl?.setValue(null);
    }

    pesoControl?.updateValueAndValidity();
    fechaPesoControl?.updateValueAndValidity();
  }

  /**
   * Controla el check «tieneLicencia». Habilita/deshabilita campos del formulario.
   */
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
      numeroLicenciaControl?.setValue(null);
      fechaLicenciaControl?.setValue(null);
    }
    numeroLicenciaControl?.updateValueAndValidity();
    fechaLicenciaControl?.updateValueAndValidity();
  }

  /**
   * Al elegir un deporte, se adaptan validaciones y campos (licencia, competidor, etc.)
   */
  onDeporteChange(deporteSeleccionado: string): void {
    this.resetFormControls();

    if (deporteSeleccionado === 'TAEKWONDO') {
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
    } else if (deporteSeleccionado === 'KICKBOXING') {
      this.showAllFields();
      // Tarifas específicas para Kickboxing
      this.tiposTarifa = [
        TipoTarifa.KICKBOXING,
      ];
      this.aplicarFiltrosGrado();
    }
  }

  /**
   * Cuando el usuario selecciona un nuevo archivo de imagen.
   */
  onFileSelected(event: any) {
    const file: File = event.target.files[0];
    this.alumnoEditado.fotoAlumno = file;

    const reader = new FileReader();
    reader.onload = (e: any) => {
      this.imagenPreview = e.target.result;
    };
    reader.readAsDataURL(file);
  }

  /**
   * Abre el modal con la imagen ampliada (si hay URL disponible).
   */
  abrirModal(imagenUrl: string | null | undefined) {
    const modal = document.getElementById('imageModal');
    const modalImg = document.getElementById('imgAmpliada') as HTMLImageElement;

    // Use default image if no URL is provided
    const urlToShow = imagenUrl || '../../../../assets/media/default.webp';

    if (modal && modalImg) {
      modal.style.display = 'block';
      modalImg.src = urlToShow;
    }
  }

  cerrarModal() {
    const modal = document.getElementById('imageModal');
    if (modal) {
      modal.style.display = 'none';
    }
  }

  /**
   * Resetea validaciones y habilita campos.
   */
  resetFormControls(): void {
    this.alumnoForm.get('tipoTarifa')?.clearValidators();
    this.alumnoForm.get('grado')?.clearValidators();
    this.alumnoForm.get('competidor')?.clearValidators();
    this.alumnoForm.get('tieneLicencia')?.clearValidators();
    this.alumnoForm.get('numeroLicencia')?.clearValidators();
    this.alumnoForm.get('fechaLicencia')?.clearValidators();

    this.alumnoForm.get('tipoTarifa')?.updateValueAndValidity();
    this.alumnoForm.get('grado')?.updateValueAndValidity();
    this.alumnoForm.get('competidor')?.updateValueAndValidity();
    this.alumnoForm.get('tieneLicencia')?.updateValueAndValidity();
    this.alumnoForm.get('numeroLicencia')?.updateValueAndValidity();
    this.alumnoForm.get('fechaLicencia')?.updateValueAndValidity();

    // Habilita todos
    this.alumnoForm.get('tipoTarifa')?.enable();
    this.alumnoForm.get('grado')?.enable();
    this.alumnoForm.get('competidor')?.enable();
    this.alumnoForm.get('tieneLicencia')?.enable();
    this.alumnoForm.get('numeroLicencia')?.enable();
    this.alumnoForm.get('fechaLicencia')?.enable();
  }

  showAllFields(): void {
    this.alumnoForm.get('tipoTarifa')?.setValidators(Validators.required);
    this.alumnoForm.get('grado')?.setValidators(Validators.required);

    this.alumnoForm.get('tipoTarifa')?.updateValueAndValidity();
    this.alumnoForm.get('grado')?.updateValueAndValidity();
  }

  getGradoNombre(grado: any): string {
    const deporteSeleccionado = this.alumnoForm.get('deporte')?.value;
    if (deporteSeleccionado === 'KICKBOXING' && grado.tipoGrado === 'ROJO') {
      return 'MARRON';
    }
    return grado.tipoGrado;
  }

  getGradoStyle(tipoGrado: string): string {
    return getGradoTextStyle(tipoGrado);
  }

  /**
   * Utilidad: calcular el total = precio * cantidad
   */
  calcularTotal(precio: number, cantidad: number): number {
    return precio * cantidad;
  }

  /**
   * Lógica de reserva de plaza, igual que antes.
   */
  reservarPlaza(alumnoId: number) {
    const anoActual = new Date().getFullYear();
    const proximoAno = anoActual + 1;
    Swal.fire({
      title: '¿Quiere añadir una reserva de plaza?',
      text: `Temporada: ${anoActual}/${proximoAno}`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Sí',
      cancelButtonText: 'No',
    }).then((resultado) => {
      if (resultado.isConfirmed) {
        this.confirmarPagoReserva(alumnoId);
      }
    });
  }

  /**
   * Confirma si la reserva ha sido pagada y procede con la creación
   */
  private confirmarPagoReserva(alumnoId: number): void {
    Swal.fire({
      title: '¿Ha sido abonada la reserva?',
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Sí, pagada',
      cancelButtonText: 'No',
    }).then((resultadoPago) => {
      if (resultadoPago.isConfirmed || resultadoPago.isDismissed) {
        const pagado = resultadoPago.isConfirmed;
        this.intentarCrearReserva(alumnoId, pagado);
      }
    });
  }

  /**
   * Intenta crear la reserva y maneja el error de conflicto
   */
  private intentarCrearReserva(alumnoId: number, pagado: boolean): void {
    this.endpointsService.reservarPlaza(alumnoId, pagado).subscribe({
      next: () => {
        this.mostrarReservaExitosa();
        this.obtenerProductosAlumno(alumnoId);
      },
      error: (err) => {
        this.manejarErrorReserva(err, alumnoId, pagado);
      },
    });
  }

  /**
   * Maneja errores al crear la reserva
   */
  private manejarErrorReserva(err: any, alumnoId: number, pagado: boolean): void {
    if (err.status === 409) {
      this.confirmarReservaForzada(alumnoId, pagado);
    } else {
      this.mostrarErrorReserva();
    }
  }

  /**
   * Confirma si desea forzar la creación de la reserva cuando ya existe una
   */
  private confirmarReservaForzada(alumnoId: number, pagado: boolean): void {
    Swal.fire({
      title: 'Reserva existente',
      text: 'Ya existe una reserva de plaza para esta temporada. ¿Quieres proceder de todas formas?',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, proceder',
      cancelButtonText: 'No',
    }).then((respuesta) => {
      if (respuesta.isConfirmed) {
        this.crearReservaForzada(alumnoId, pagado);
      }
    });
  }

  /**
   * Crea la reserva forzada ignorando el conflicto
   */
  private crearReservaForzada(alumnoId: number, pagado: boolean): void {
    this.endpointsService.reservarPlaza(alumnoId, pagado, true).subscribe({
      next: () => {
        this.mostrarReservaExitosa();
        this.obtenerProductosAlumno(alumnoId);
      },
      error: () => {
        this.mostrarErrorReserva();
      },
    });
  }

  /**
   * Muestra mensaje de éxito al crear la reserva
   */
  private mostrarReservaExitosa(): void {
    Swal.fire({
      title: 'Reserva creada',
      text: 'La reserva de plaza ha sido añadida correctamente.',
      icon: 'success',
      timer: 2000,
    });
  }

  /**
   * Muestra mensaje de error al crear la reserva
   */
  private mostrarErrorReserva(): void {
    Swal.fire({
      title: 'Error',
      text: 'No se pudo crear la reserva.',
      icon: 'error',
    });
  }

  /**
   * Valida si la licencia está en vigor comparando años.
   */
  licenciaEnVigor(fechaLicencia: Date): boolean {
    const fechaActual = new Date();
    const fechaLicenciaDate = new Date(fechaLicencia);
    return fechaLicenciaDate.getFullYear() >= fechaActual.getFullYear();
  }

  /**
   * Abre el modal para seleccionar el tipo de convocatoria (examen normal o por recompensa).
   */
  seleccionarTipoConvocatoria(alumno: any): void {
    Swal.fire({
      title: 'Selecciona el tipo de convocatoria',
      text: '¿Asignar el precio por antigüedad o por recompensa?',
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Por Recompensa',
      cancelButtonText: 'Por Antigüedad',
      confirmButtonColor: '#28a745',
      cancelButtonColor: '#6c757d',
    }).then((result) => {
      const porRecompensa = result.isConfirmed;
      this.abrirModalConvocatoriasConTipo(alumno, porRecompensa);
    });
  }

  abrirModalConvocatoriasConTipo(alumno: any, porRecompensa: boolean): void {
    this.alumnoId = alumno.id;
    this.cargarConvocatoriasDisponibles(alumno);
    this.mostrarModalConvocatorias = true;
    this.alumnoEditado.porRecompensa = porRecompensa;
  }

  cargarConvocatoriasDisponibles(alumno: any): void {
    const deporte = alumno.deporte;
    this.endpointsService.obtenerConvocatorias(deporte).subscribe({
      next: (convocatorias) => {
        const hoy = new Date();
        this.convocatoriaActual = convocatorias.find((conv: any) => {
          const fechaConvocatoria = new Date(conv.fechaConvocatoria);
          return (
            fechaConvocatoria.getFullYear() === hoy.getFullYear() &&
            fechaConvocatoria.getMonth() === hoy.getMonth() &&
            fechaConvocatoria.getDate() === hoy.getDate()
          );
        });
        this.convocatoriasDisponibles = convocatorias.filter(
          (convocatoria: any) => convocatoria.id !== this.convocatoriaActual?.id
        );
      },
      error: () => {
        console.error('Error al obtener convocatorias disponibles');
      },
    });
  }

  agregarAConvocatoriaEspecifica(convocatoria: any): void {
    if (!this.alumnoId) return;

    const porRecompensa = this.alumnoEditado.porRecompensa;
    this.endpointsService
      .agregarAlumnoAConvocatoria(this.alumnoId, convocatoria.id, porRecompensa)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Alumno agregado',
            text: 'El alumno ha sido agregado correctamente a la convocatoria.',
            icon: 'success',
            timer: 2000,
          });
          this.cerrarModalConvocatorias();
          if (this.alumnoId) {
            this.cargarConvocatoriasDelAlumno(this.alumnoId);
            this.obtenerProductosAlumno(this.alumnoId);
          }
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

  eliminarDeConvocatoriaSeleccionada(convocatoria: any): void {
    if (!this.alumnoId) return;

    this.cerrarModalEliminarConvocatorias();
    Swal.fire({
      title: '¿Estás seguro?',
      text: `Eliminarás al alumno de la convocatoria de ${convocatoria.deporte}
             del ${formatDate(convocatoria.fechaConvocatoria)}`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, eliminar',
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService
          .eliminarAlumnoDeConvocatoria(this.alumnoId!, convocatoria.id)
          .subscribe({
            next: () => {
              Swal.fire({
                title: 'Eliminado',
                text: 'El alumno ha sido eliminado de la convocatoria.',
                icon: 'success',
                timer: 2000,
              });
              this.cargarConvocatoriasDelAlumno(this.alumnoId!);
              this.obtenerProductosAlumno(this.alumnoId!);
            },
            error: () => {
              Swal.fire({
                title: 'Error',
                text: 'No se pudo eliminar al alumno de la convocatoria.',
                icon: 'error',
              });
            },
          });
      }
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

  /**
   * Asigna el valor de la cuantía de la tarifa según el tipoTarifa.
   */
  asignarCuantiaTarifa(tipoTarifa: TipoTarifa): number {
    switch (tipoTarifa) {
      case TipoTarifa.ADULTO:
        return 30;
      case TipoTarifa.ADULTO_GRUPO:
        return 20;
      case TipoTarifa.FAMILIAR:
        return 0;
      case TipoTarifa.INFANTIL:
        return 28;
      case TipoTarifa.INFANTIL_GRUPO:
        return 20;
      case TipoTarifa.HERMANOS:
        return 26;
      case TipoTarifa.PADRES_HIJOS:
        // For PADRES_HIJOS, the price depends on the rol
        { const rolFamiliar = this.alumnoForm.get('rolFamiliar')?.value;
        if (rolFamiliar === RolFamiliar.PADRE) {
          return 28;
        } else if (rolFamiliar === RolFamiliar.HIJO) {
          return 26;
        }
        return 0; }
      case TipoTarifa.KICKBOXING:
        return 30;
      default:
        throw new Error('Tipo de descuento no válido');
    }
  }

  /**
   * Handles changes to tipo tarifa to show/hide family fields
   */
  onTipoTarifaChange(tipoTarifa: string): void {
    // Reset family fields
    this.alumnoForm.get('rolFamiliar')?.setValue(RolFamiliar.NINGUNO);
    this.alumnoForm.get('grupoFamiliar')?.setValue('');
    this.alumnoForm.get('rolFamiliar')?.disable();
    this.alumnoForm.get('grupoFamiliar')?.disable();
    this.alumnoForm.get('rolFamiliar')?.clearValidators();
    this.alumnoForm.get('grupoFamiliar')?.clearValidators();

    // Enable fields based on tarifa type
    if (tipoTarifa === TipoTarifa.PADRES_HIJOS) {
      this.alumnoForm.get('rolFamiliar')?.enable();
      this.alumnoForm.get('rolFamiliar')?.setValidators(Validators.required);
    } else if (tipoTarifa === TipoTarifa.HERMANOS) {
      this.alumnoForm.get('grupoFamiliar')?.enable();
      this.alumnoForm.get('grupoFamiliar')?.setValidators(Validators.required);
    }

    this.alumnoForm.get('rolFamiliar')?.updateValueAndValidity();
    this.alumnoForm.get('grupoFamiliar')?.updateValueAndValidity();
  }

  /**
   * Valida que la fecha de baja sea posterior a la fecha de alta.
   */
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

  /**
   * Valida que la fecha de nacimiento sea anterior a la fecha de alta.
   */
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

  private getFechaActual(): string {
    const today = new Date();
    return today.toISOString().split('T')[0];
  }

  navegarAGestionProductos(alumnoId: number) {
    this.router.navigate(['/alumnos', alumnoId, 'productos']);
  }

  /**
   * Botón «Volver» que simplemente navega hacia atrás en el historial.
   */
  volver() {
    this.location.back();
  }

  /**
   * Toggle showing inactive alumnos and reload the list
   */
  alternarInactivos(): void {
    const alumnoIdActual = this.alumno?.id;
    this.mostrarInactivos = !this.mostrarInactivos;

    // Reload the IDs list with new filter
    this.cargarTodosLosAlumnosIds(() => {
      // If we have a current alumno, try to stay on it
      if (alumnoIdActual) {
        const pageIndex = this.alumnosIds.indexOf(alumnoIdActual);
        if (pageIndex === -1) {
          // Alumno not in filtered list, navigate to first alumno
          if (this.alumnosIds.length > 0) {
            const firstAlumnoId = this.alumnosIds[0];
            this.router.navigate(['/alumnosEditar', firstAlumnoId], { replaceUrl: true });
          }
        } else {
          // Alumno still in list, update page number and stay on this alumno
          const pageNumber = pageIndex + 1;
          this.paginaActual = pageNumber;
          this.router.navigate(['/alumnosEditar', alumnoIdActual], { replaceUrl: true });
        }
      }
    });
  }

  // ==================== MULTI-SPORT METHODS ====================

  /**
   * Load all sports for the current alumno
   */
  cargarDeportesDelAlumno(alumnoId: number): void {
    this.alumnoService.obtenerDeportesDelAlumno(alumnoId).subscribe({
      next: (deportes: AlumnoDeporteDTO[]) => {
        this.deportesDelAlumno = deportes;

        // Set active sport to the first one, or the alumno's primary sport
        if (deportes.length > 0) {
          if (this.alumno.deporte) {
            // Try to set to alumno's primary sport
            const primaryDeporte = deportes.find(d => d.deporte === this.alumno.deporte);
            this.deporteActivo = primaryDeporte ? primaryDeporte.deporte : deportes[0].deporte;
          } else {
            this.deporteActivo = deportes[0].deporte;
          }
        }

        // Calculate available sports to add
        this.deportesDisponibles = this.getDeportesDisponiblesParaAgregar();
      },
      error: () => {
        this.deportesDelAlumno = [];
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los deportes del alumno',
          icon: 'error',
        });
      },
    });
  }

  /**
   * Get list of sports available to add (not already assigned)
   */
  getDeportesDisponiblesParaAgregar(): Deporte[] {
    const deportesAsignados = this.deportesDelAlumno.map(d => d.deporte);
    return Object.values(Deporte).filter(d => !deportesAsignados.includes(d));
  }

  /**
   * Change the active sport tab
   */
  cambiarTab(deporte: string): void {
    this.deporteActivo = deporte;
  }

  /**
   * Open modal to add a new sport
   */
  abrirModalAgregarDeporte(): void {
    this.deportesDisponibles = this.getDeportesDisponiblesParaAgregar();
    if (this.deportesDisponibles.length === 0) {
      Swal.fire({
        title: 'No hay deportes disponibles',
        text: 'El alumno ya está inscrito en todos los deportes disponibles',
        icon: 'info',
      });
      return;
    }
    this.nuevoDeporte = this.deportesDisponibles[0];
    this.gradoInicialDeporte = 'BLANCO';
    this.mostrarModalAgregarDeporte = true;
  }

  /**
   * Close modal to add sport
   */
  cerrarModalAgregarDeporte(): void {
    this.mostrarModalAgregarDeporte = false;
    this.nuevoDeporte = '';
    this.gradoInicialDeporte = '';
  }

  /**
   * Add a new sport to the alumno
   */
  agregarDeporte(): void {
    if (!this.nuevoDeporte || !this.gradoInicialDeporte || !this.alumnoId) {
      return;
    }

    this.alumnoService
      .agregarDeporteAAlumno(this.alumnoId, this.nuevoDeporte, this.gradoInicialDeporte)
      .subscribe({
        next: () => {
          Swal.fire({
            title: '¡Deporte agregado!',
            text: `El deporte ${getDeporteLabel(this.nuevoDeporte)} ha sido agregado exitosamente`,
            icon: 'success',
            timer: 2000,
          });
          this.cerrarModalAgregarDeporte();
          this.cargarDeportesDelAlumno(this.alumnoId!);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo agregar el deporte',
            icon: 'error',
          });
        },
      });
  }

  /**
   * Remove a sport from the alumno
   */
  removerDeporte(deporte: string): void {
    if (this.deportesDelAlumno.length === 1) {
      Swal.fire({
        title: 'No se puede eliminar',
        text: 'El alumno debe tener al menos un deporte asignado',
        icon: 'warning',
      });
      return;
    }

    Swal.fire({
      title: '¿Estás seguro?',
      text: `Eliminarás el deporte ${getDeporteLabel(deporte)} del alumno. Esta acción no se puede deshacer.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#d33',
      cancelButtonColor: '#3085d6',
      confirmButtonText: 'Sí, eliminar',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed && this.alumnoId) {
        this.alumnoService.removerDeporteDeAlumno(this.alumnoId, deporte).subscribe({
          next: () => {
            Swal.fire({
              title: '¡Eliminado!',
              text: 'El deporte ha sido eliminado correctamente',
              icon: 'success',
              timer: 2000,
            });

            // If we removed the active sport, switch to the first remaining one
            if (this.deporteActivo === deporte && this.deportesDelAlumno.length > 1) {
              const otherDeporte = this.deportesDelAlumno.find(d => d.deporte !== deporte);
              if (otherDeporte) {
                this.deporteActivo = otherDeporte.deporte;
              }
            }

            this.cargarDeportesDelAlumno(this.alumnoId!);
          },
          error: (error) => {
            Swal.fire({
              title: 'Error',
              text: error.error || 'No se pudo eliminar el deporte',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  /**
   * Get the AlumnoDeporteDTO for the active sport
   */
  getDeporteActivo(): AlumnoDeporteDTO | undefined {
    return this.deportesDelAlumno.find(d => d.deporte === this.deporteActivo);
  }

  /**
   * Get the grade name for a specific sport
   */
  getGradoDelDeporte(deporte: string): string {
    const alumnoDeporte = this.deportesDelAlumno.find(d => d.deporte === deporte);
    return alumnoDeporte?.grado || 'Sin grado';
  }

  /**
   * Get deporte label (for display)
   */
  getDeporteLabel(deporte: string): string {
    return getDeporteLabel(deporte);
  }

  /**
   * Open modal to update grade for a specific sport
   */
  abrirModalActualizarGrado(deporte: string): void {
    this.deporteParaActualizarGrado = deporte;
    const deporteActual = this.deportesDelAlumno.find(d => d.deporte === deporte);
    this.nuevoGradoActualizar = deporteActual?.grado || 'BLANCO';
    this.mostrarModalActualizarGrado = true;
  }

  /**
   * Close grade update modal
   */
  cerrarModalActualizarGrado(): void {
    this.mostrarModalActualizarGrado = false;
    this.deporteParaActualizarGrado = '';
    this.nuevoGradoActualizar = '';
  }

  /**
   * Update grade for a specific sport
   */
  actualizarGradoDeporte(): void {
    if (!this.deporteParaActualizarGrado || !this.nuevoGradoActualizar || !this.alumnoId) {
      return;
    }

    this.alumnoService
      .actualizarGradoPorDeporte(this.alumnoId, this.deporteParaActualizarGrado, this.nuevoGradoActualizar)
      .subscribe({
        next: () => {
          Swal.fire({
            title: '¡Grado actualizado!',
            text: `El grado para ${getDeporteLabel(this.deporteParaActualizarGrado)} ha sido actualizado a ${this.nuevoGradoActualizar}`,
            icon: 'success',
            timer: 2000,
          });
          this.cerrarModalActualizarGrado();
          this.cargarDeportesDelAlumno(this.alumnoId!);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar el grado',
            icon: 'error',
          });
        },
      });
  }

  /**
   * Update exam eligibility for a specific sport
   */
  actualizarAptoParaExamen(deporte: string, event: any): void {
    if (!this.alumnoId) {
      return;
    }

    const aptoParaExamen = event.target.checked;

    this.alumnoService
      .actualizarAptoParaExamen(this.alumnoId, deporte, aptoParaExamen)
      .subscribe({
        next: () => {
          Swal.fire({
            title: '¡Actualizado!',
            text: `Estado de aptitud para examen actualizado en ${getDeporteLabel(deporte)}`,
            icon: 'success',
            timer: 1500,
            showConfirmButton: false,
          });
          this.cargarDeportesDelAlumno(this.alumnoId!);
        },
        error: (error) => {
          // Revert the checkbox
          event.target.checked = !aptoParaExamen;
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar el estado',
            icon: 'error',
          });
        },
      });
  }

  /**
   * Update grade date for a specific sport
   */
  actualizarFechaGrado(deporte: string, event: any): void {
    if (!this.alumnoId) {
      return;
    }

    const fechaGrado = event.target.value;
    if (!fechaGrado) {
      return;
    }

    this.alumnoService
      .actualizarFechaGrado(this.alumnoId, deporte, fechaGrado)
      .subscribe({
        next: () => {
          Swal.fire({
            title: '¡Fecha actualizada!',
            text: `Fecha de grado actualizada en ${getDeporteLabel(deporte)}`,
            icon: 'success',
            timer: 1500,
            showConfirmButton: false,
          });
          this.cargarDeportesDelAlumno(this.alumnoId!);
        },
        error: (error) => {
          Swal.fire({
            title: 'Error',
            text: error.error || 'No se pudo actualizar la fecha',
            icon: 'error',
          });
          // Reload to revert the date input
          this.cargarDeportesDelAlumno(this.alumnoId!);
        },
      });
  }
}
