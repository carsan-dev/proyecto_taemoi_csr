import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { AccionInforme, InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { showErrorToast } from '../../../utils/toast.util';

/** Tipos del diálogo «Generar informe» del listado de alumnos (compartidos con el listado nuevo, 8b-3a). */
export const OPCIONES_INFORME_ALUMNOS: ReadonlyArray<{ value: string; label: string }> = [
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
    label:
      'Informe de Alumnos Infantiles a Promocionar (Todos los deportes)',
  },
  {
    value: 'adultos',
    label: 'Informe de Alumnos Adultos a Promocionar (Todos los deportes)',
  },
  {
    value: 'infantiles-taekwondo',
    label: 'Informe de Alumnos Infantiles a Promocionar (Taekwondo)',
  },
  {
    value: 'adultos-taekwondo',
    label: 'Informe de Alumnos Adultos a Promocionar (Taekwondo)',
  },
  {
    value: 'infantiles-kickboxing',
    label: 'Informe de Alumnos Infantiles a Promocionar (Kickboxing)',
  },
  {
    value: 'adultos-kickboxing',
    label: 'Informe de Alumnos Adultos a Promocionar (Kickboxing)',
  },
  { value: 'deudas', label: 'Informe de Deudas de Alumnos' },
  {
    value: 'mensualidades',
    label: 'Informe de Mensualidades de Todos los Alumnos',
  },
  {
    value: 'mensualidades-taekwondo',
    label: 'Informe de Mensualidades de Taekwondo',
  },
  {
    value: 'mensualidades-kickboxing',
    label: 'Informe de Mensualidades de Kickboxing',
  },
  {
    value: 'reservas-plaza',
    label: 'Informe de Reservas de Plaza',
  },
  {
    value: 'productos',
    label: 'Informe de Productos',
  },
  {
    value: 'competidores',
    label: 'Informe de Competidores',
  },
  {
    value: 'dpf',
    label: 'Informe de Alumnas de D.P.F',
  },
];

/** Genera un PDF (o CSV) con el visor y la descarga de siempre (InformePdfService). */
export function generarPdf(
  informes: InformePdfService,
  observable$: Observable<Blob>,
  errorMessage: string,
  filename: string = 'informe.pdf',
  accion: AccionInforme = 'descargar',
  titulo: string = 'Informe'
): void {
  informes.generar(observable$.pipe(map(blob => ({
    documentos: [{ blob, nombreArchivo: filename, titulo }]
  }))), accion, errorMessage);
}

/**
 * Lógica del diálogo «Generar informe» de alumnos: temporadas de reservas de plaza y la generación de cada tipo. Sale
 * del listado de hoy sin cambios (8b-3a) para que el listado nuevo la use tal cual.
 */
export class InformesAlumnos {
  temporadas: string[] = [];
  cargandoTemporadas = false;

  constructor(
    private readonly endpoints: EndpointsService,
    private readonly informes: InformePdfService
  ) {}

  /** Al abrir el diálogo: vacía y vuelve a pedir las temporadas. */
  abrir(): void {
    this.temporadas = [];
    this.cargandoTemporadas = true;
    this.cargarTemporadasReservasPlaza();
  }

  cargarTemporadasReservasPlaza(): void {
    this.endpoints.obtenerTemporadasReservasPlaza().subscribe({
      next: temporadas => {
        this.temporadas = temporadas;
        this.cargandoTemporadas = false;
      },
      error: () => {
        this.temporadas = [];
        this.cargandoTemporadas = false;
        showErrorToast('No se pudieron cargar las temporadas de reservas');
      },
    });
  }

  generarInformeSeleccionado(event: {
    tipo: string;
    soloActivos: boolean;
    temporada?: string;
    accion: AccionInforme;
  }): void {
    if (this.informes.ocupado || this.informes.destruido) { return; }
    const { tipo, soloActivos, temporada, accion } = event;
    const titulo = OPCIONES_INFORME_ALUMNOS.find(opcion => opcion.value === tipo)?.label || 'Informe';

    switch (tipo) {
      case 'general':
        generarPdf(this.informes, 
          this.endpoints.generarInformeAlumnosPorGrado(soloActivos),
          'No se pudo generar el informe general', 'informe.pdf', accion, titulo
        );
        break;
      case 'taekwondo':
        generarPdf(this.informes, 
          this.endpoints.generarInformeTaekwondoPorGrado(soloActivos),
          'No se pudo generar el informe de Taekwondo', 'informe.pdf', accion, titulo
        );
        break;
      case 'kickboxing':
        generarPdf(this.informes, 
          this.endpoints.generarInformeKickboxingPorGrado(soloActivos),
          'No se pudo generar el informe de Kickboxing', 'informe.pdf', accion, titulo
        );
        break;
      case 'licencias':
        generarPdf(this.informes, 
          this.endpoints.generarInformeLicencias(soloActivos),
          'No se pudo generar el informe de licencias', 'informe.pdf', accion, titulo
        );
        break;
      case 'infantiles':
        generarPdf(this.informes, 
          this.endpoints.generarInformeInfantilesAPromocionar(soloActivos),
          'No se pudo generar el informe de infantiles', 'informe.pdf', accion, titulo
        );
        break;
      case 'adultos':
        generarPdf(this.informes, 
          this.endpoints.generarInformeAdultosAPromocionar(soloActivos),
          'No se pudo generar el informe de adultos', 'informe.pdf', accion, titulo
        );
        break;
      case 'infantiles-taekwondo':
        generarPdf(this.informes, 
          this.endpoints.generarInformeInfantilesAPromocionarTaekwondo(soloActivos),
          'No se pudo generar el informe de infantiles de Taekwondo', 'informe.pdf', accion, titulo
        );
        break;
      case 'infantiles-kickboxing':
        generarPdf(this.informes, 
          this.endpoints.generarInformeInfantilesAPromocionarKickboxing(soloActivos),
          'No se pudo generar el informe de infantiles de Kickboxing', 'informe.pdf', accion, titulo
        );
        break;
      case 'adultos-taekwondo':
        generarPdf(this.informes, 
          this.endpoints.generarInformeAdultosAPromocionarTaekwondo(soloActivos),
          'No se pudo generar el informe de adultos de Taekwondo', 'informe.pdf', accion, titulo
        );
        break;
      case 'adultos-kickboxing':
        generarPdf(this.informes, 
          this.endpoints.generarInformeAdultosAPromocionarKickboxing(soloActivos),
          'No se pudo generar el informe de adultos de Kickboxing', 'informe.pdf', accion, titulo
        );
        break;
      case 'deudas':
        this.generarInformeDeudas(soloActivos, accion);
        break;
      case 'mensualidades':
        generarPdf(this.informes, 
          this.endpoints.generarInformeMensualidades(soloActivos),
          'No se pudo generar el informe de mensualidades', 'informe.pdf', accion, titulo
        );
        break;
      case 'mensualidades-taekwondo':
        generarPdf(this.informes, 
          this.endpoints.generarInformeMensualidadesTaekwondo(soloActivos),
          'No se pudo generar el informe de mensualidades de Taekwondo', 'informe.pdf', accion, titulo
        );
        break;
      case 'mensualidades-kickboxing':
        generarPdf(this.informes, 
          this.endpoints.generarInformeMensualidadesKickboxing(soloActivos),
          'No se pudo generar el informe de mensualidades de Kickboxing', 'informe.pdf', accion, titulo
        );
        break;
      case 'reservas-plaza':
        this.solicitarTemporadaReservasPlaza(soloActivos, temporada, accion);
        break;
      case 'productos':
        generarPdf(this.informes, 
          this.endpoints.generarInformeProductos(),
          'No se pudo generar el informe de productos', 'informe.pdf', accion, titulo
        );
        break;
      case 'competidores':
        generarPdf(this.informes, 
          this.endpoints.generarInformeCompetidores(),
          'No se pudo generar el informe de competidores', 'informe.pdf', accion, titulo
        );
        break;
      case 'dpf':
        generarPdf(this.informes, 
          this.endpoints.generarInformeAlumnasDpf(soloActivos),
          'No se pudo generar el informe de alumnas de D.P.F', 'informe_alumnas_dpf.pdf', accion, titulo
        );
        break;
    }
  }

  private solicitarTemporadaReservasPlaza(soloActivos: boolean, temporadaPreseleccionada?: string, accion: AccionInforme = 'descargar'): void {
    if (this.cargandoTemporadas) {
      Swal.fire('Cargando temporadas', 'Espera unos segundos y vuelve a generar el informe.', 'info');
      return;
    }

    if (this.temporadas.length === 0) {
      Swal.fire('Sin temporadas', 'No hay temporadas con reservas de plaza disponibles.', 'warning');
      return;
    }

    const anoActual = new Date().getFullYear();
    const temporadaActual = `${anoActual}/${anoActual + 1}`;
    const temporadaInicial = temporadaPreseleccionada
      || (this.temporadas.includes(temporadaActual) ? temporadaActual : this.temporadas[0]);
    const inputOptions = this.temporadas.reduce((options, temporada) => {
      options[temporada] = temporada === temporadaActual ? `Actual (${temporada})` : temporada;
      return options;
    }, {} as Record<string, string>);

    this.informes.seleccionar({
      title: 'Seleccionar temporada',
      input: 'select',
      inputOptions,
      inputValue: temporadaInicial,
      showCancelButton: true,
      confirmButtonText: 'Generar informe',
      cancelButtonText: 'Cancelar',
      inputValidator: value => value ? null : 'Debes seleccionar una temporada',
    }).then(result => {
      if (!result?.isConfirmed || !result.value) {
        return;
      }
      const temporada = result.value;
      generarPdf(this.informes, 
        this.endpoints.generarInformeReservasPlaza(temporada, soloActivos),
        'No se pudo generar el informe de reservas de plaza',
        `informe_reservas_plaza_${temporada.replace('/', '_')}.pdf`, accion, `Reservas de plaza ${temporada}`
      );
    });
  }

  /**
   * Handles deudas report generation with format selection dialog
   */
  private generarInformeDeudas(soloActivos: boolean, accion: AccionInforme): void {
    if (accion === 'ver') {
      generarPdf(this.informes, this.endpoints.generarInformeDeudas(soloActivos),
        'No se pudo generar el informe de deudas en PDF', 'informe.pdf', accion, 'Deudas');
      return;
    }
    void this.informes.seleccionar({
      title: 'Seleccionar Formato', text: '¿En qué formato deseas generar el informe de deudas?',
      icon: 'question', showCancelButton: true, showDenyButton: true,
      confirmButtonText: 'PDF', denyButtonText: 'CSV', cancelButtonText: 'Cancelar',
    }).then(result => {
      if (result?.isConfirmed) {
        generarPdf(this.informes, this.endpoints.generarInformeDeudas(soloActivos),
          'No se pudo generar el informe de deudas en PDF');
      } else if (result?.isDenied) {
        generarPdf(this.informes, this.endpoints.generarInformeDeudasCSV(soloActivos),
          'No se pudo generar el informe CSV', 'informe_deudas_alumnos.csv');
      }
    });
  }
}
