import { Component, OnInit, OnDestroy } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { FormsModule } from '@angular/forms';
import { calcularEdad } from '../../../utilities/calcular-edad';
import { RouterLink } from '@angular/router';
import { InformeModalComponent } from '../../generales/informe-modal/informe-modal.component';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

@Component({
  selector: 'app-listado-alumnos',
  standalone: true,
  imports: [
    CommonModule,
    PaginacionComponent,
    FormsModule,
    RouterLink,
    InformeModalComponent,
  ],
  templateUrl: './listado-alumnos.component.html',
  styleUrl: './listado-alumnos.component.scss',
})
export class ListadoAlumnosComponent implements OnInit, OnDestroy {
  alumnos: any[] = [];
  alumnosSeleccionables: any[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 9;
  totalPaginas: number = 0;
  nombreFiltro: string = '';
  mostrarInactivos: boolean = false;
  private searchSubject = new Subject<string>();
  mesAnoSeleccionado: string = '';
  alumnoSeleccionado: number | null = null;
  mesAnoSeleccionadoIndividual: string = '';
  mostrarModalInforme: boolean = false;
  modalTitle: string = '';
  opcionesInforme: Array<{ value: string; label: string }> = [];
  mesAnoAsistencia!: string;
  grupos = ['lunes', 'martes', 'miércoles', 'jueves'];
  grupoSeleccionado!: string;

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerAlumnos();
    this.cargarTodosLosAlumnos();

    // Setup debounced search
    this.searchSubject.pipe(
      debounceTime(500), // Wait 500ms after user stops typing
      distinctUntilChanged() // Only trigger if value actually changed
    ).subscribe(() => {
      this.paginaActual = 1;
      this.obtenerAlumnos();
    });
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }


  abrirModalInforme(): void {
    this.modalTitle = 'Generar Informe';
    this.opcionesInforme = [
      { value: 'general', label: 'Informe de Alumnos por Grado General' },
      {
        value: 'taekwondo',
        label: 'Informe de Alumnos de Taekwondo por Grado',
      },
      {
        value: 'kickboxing',
        label: 'Informe de Alumnos de Kickboxing por Grado',
      },
      { value: 'licencias', label: 'Informe de Estado de Licencias' },
      {
        value: 'infantiles',
        label: 'Informe de Alumnos Infantiles a Promocionar',
      },
      {
        value: 'adultos',
        label: 'Informe de Alumnos Adultos a Promocionar',
      },
    ];
    this.mostrarModalInforme = true;
  }

  cerrarModalInforme(): void {
    this.mostrarModalInforme = false;
  }

  obtenerAlumnos(): void {
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
          this.totalPaginas = response.totalPages;
        },
        error: () => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
  }

  cargarTodosLosAlumnos(): void {
    this.endpointsService
      .obtenerAlumnosSinPaginar(this.mostrarInactivos)
      .subscribe({
        next: (response) => {
          this.alumnosSeleccionables = response;
        },
        error: () => {
          Swal.fire(
            'Error',
            'No se pudo cargar la lista completa de alumnos.',
            'error'
          );
        },
      });
  }

  generarInformeSeleccionado(tipo: string): void {
    if (tipo === 'general') {
      this.endpointsService.generarInformeAlumnosPorGrado().subscribe({
        next: (pdfBlob: Blob) => {
          const fileURL = URL.createObjectURL(pdfBlob);
          window.open(fileURL, '_blank');
        },
        error: () => {
          Swal.fire('Error', 'No se pudo generar el informe general', 'error');
        },
      });
    } else if (tipo === 'taekwondo') {
      this.endpointsService.generarInformeTaekwondoPorGrado().subscribe({
        next: (pdfBlob: Blob) => {
          const fileURL = URL.createObjectURL(pdfBlob);
          window.open(fileURL, '_blank');
        },
        error: () => {
          Swal.fire(
            'Error',
            'No se pudo generar el informe de Taekwondo',
            'error'
          );
        },
      });
    } else if (tipo === 'kickboxing') {
      this.endpointsService.generarInformeKickboxingPorGrado().subscribe({
        next: (pdfBlob: Blob) => {
          const fileURL = URL.createObjectURL(pdfBlob);
          window.open(fileURL, '_blank');
        },
        error: () => {
          Swal.fire(
            'Error',
            'No se pudo generar el informe de Kickboxing',
            'error'
          );
        },
      });
    } else if (tipo === 'licencias') {
      this.endpointsService.generarInformeLicencias().subscribe((blob) => {
        const url = window.URL.createObjectURL(blob);
        window.open(url);
      });
    } else if (tipo === 'infantiles') {
      this.endpointsService
        .generarInformeInfantilesAPromocionar()
        .subscribe((blob) => {
          const url = window.URL.createObjectURL(blob);
          window.open(url);
        });
    } else if (tipo === 'adultos') {
      this.endpointsService
        .generarInformeAdultosAPromocionar()
        .subscribe((blob) => {
          const url = window.URL.createObjectURL(blob);
          window.open(url);
        });
    }
  }

  calcularEdad(fechaNacimiento: string): number {
    return calcularEdad(fechaNacimiento);
  }

  cambiarPagina(pageNumber: number): void {
    this.paginaActual = pageNumber;
    this.obtenerAlumnos();
  }

  filtrarPorNombre(): void {
    this.searchSubject.next(this.nombreFiltro);
  }

  alternarInactivos(): void {
    this.mostrarInactivos = !this.mostrarInactivos;
    this.obtenerAlumnos();
    this.cargarTodosLosAlumnos();
  }

  darDeAlta(alumnoId: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El alumno será dado de alta.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, dar de alta',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.darDeAltaAlumno(alumnoId).subscribe({
          next: () => {
            Swal.fire({
              title: 'Alumno dado de alta',
              text: 'El alumno ha sido dado de alta correctamente.',
              icon: 'success',
              timer: 2000,
            });
            this.obtenerAlumnos();
          },
          error: () => {
            Swal.fire({
              title: 'Error al dar de alta',
              text: 'Ha ocurrido un error al intentar dar de alta al alumno.',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  darDeBaja(alumnoId: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El alumno será dado de baja.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, dar de baja',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.darDeBajaAlumno(alumnoId).subscribe({
          next: () => {
            Swal.fire({
              title: 'Alumno dado de baja',
              text: 'El alumno ha sido dado de baja correctamente.',
              icon: 'success',
              timer: 2000,
            });
            this.obtenerAlumnos();
          },
          error: () => {
            Swal.fire({
              title: 'Error al dar de baja',
              text: 'Ha ocurrido un error al intentar dar de baja al alumno.',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  cargarMensualidadesGenerales(): void {
    if (!this.mesAnoSeleccionado) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un mes y año.',
        icon: 'error',
      });
      return;
    }

    this.endpointsService
      .cargarMensualidadesGenerales(this.mesAnoSeleccionado)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Éxito',
            text: 'Las mensualidades se han asignado correctamente.',
            icon: 'success',
            timer: 2000,
          });
          this.obtenerAlumnos();
        },
        error: () => {
          Swal.fire({
            title: 'Error',
            text: 'Ocurrió un error al asignar las mensualidades.',
            icon: 'error',
          });
        },
      });
  }

  cargarMensualidadIndividual(): void {
    if (!this.alumnoSeleccionado || !this.mesAnoSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un mes/año.',
        icon: 'error',
      });
      return;
    }

    this.endpointsService
      .cargarMensualidadIndividual(
        this.alumnoSeleccionado,
        this.mesAnoSeleccionadoIndividual
      )
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Éxito',
            text: 'Mensualidad cargada correctamente.',
            icon: 'success',
            timer: 2000,
          });
        },
        error: (error) => {
          if (error.status === 409 && error.error.accion === 'confirmar') {
            Swal.fire({
              title: 'Atención',
              text: error.error.mensaje,
              icon: 'warning',
              showCancelButton: true,
              confirmButtonText: 'Sí, cargar',
              cancelButtonText: 'No, cancelar',
            }).then((result) => {
              if (result.isConfirmed) {
                this.forzarCargarMensualidad();
              }
            });
          } else {
            Swal.fire('Error', 'No se pudo cargar la mensualidad.', 'error');
          }
        },
      });
  }

  forzarCargarMensualidad(): void {
    if (!this.alumnoSeleccionado || !this.mesAnoSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un mes/año.',
        icon: 'error',
      });
      return;
    }

    this.endpointsService
      .cargarMensualidadIndividual(
        this.alumnoSeleccionado,
        this.mesAnoSeleccionadoIndividual,
        true
      )
      .subscribe({
        next: () => {
          Swal.fire('Éxito', 'Mensualidad cargada correctamente.', 'success');
        },
        error: () => {
          Swal.fire('Error', 'No se pudo cargar la mensualidad.', 'error');
        },
      });
  }

  generarListadoAsistencia() {
    const [year, month] = this.mesAnoAsistencia.split('-').map((v) => +v);
    this.endpointsService
      .descargarAsistencia(
        year,
        month,
        this.grupoSeleccionado
      )
      .subscribe((blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Asistencia-${this.grupoSeleccionado}-${this.mesAnoAsistencia}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      });
  }

  private formatearNombreMensualidad(mesAno: string): string {
    const [anio, mes] = mesAno.split('-');
    const meses = [
      'ENERO',
      'FEBRERO',
      'MARZO',
      'ABRIL',
      'MAYO',
      'JUNIO',
      'JULIO',
      'AGOSTO',
      'SEPTIEMBRE',
      'OCTUBRE',
      'NOVIEMBRE',
      'DICIEMBRE',
    ];
    return `${meses[parseInt(mes, 10) - 1]} ${anio}`;
  }
}
