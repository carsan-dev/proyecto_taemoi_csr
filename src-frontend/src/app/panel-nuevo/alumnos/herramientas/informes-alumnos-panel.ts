import { InformesAlumnos } from '../../../componentes/endpoints/listado-alumnos/informes-alumnos';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { AccionInforme, InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { AvisosService } from '../../avisos/avisos.service';

/**
 * La lógica de «Generar informe» de siempre (`InformesAlumnos`) para el panel lateral del listado nuevo (8b-D2a): mismas
 * peticiones, títulos y nombres de archivo. Lo único que cambia son las dos preguntas que hoy hace un SweetAlert2 (que
 * quedaría detrás del panel): la temporada de las reservas de plaza se elige en su fila y el formato de las deudas, con
 * su botón («Descargar PDF» o «Descargar CSV»). Los fallos van en los avisos del panel nuevo.
 */
export class InformesAlumnosPanel extends InformesAlumnos {
  constructor(
    endpoints: EndpointsService,
    informes: InformePdfService,
    private readonly avisos: AvisosService
  ) {
    super(endpoints, informes, (texto) => avisos.error(texto));
  }

  /** La temporada ya viene elegida en la fila; sin temporadas, los textos de hoy en un aviso. */
  protected override solicitarTemporadaReservasPlaza(soloActivos: boolean, temporada?: string, accion: AccionInforme = 'descargar'): void {
    if (this.cargandoTemporadas) {
      this.avisos.info('Espera unos segundos y vuelve a generar el informe.');
      return;
    }
    if (!temporada || !this.temporadas.includes(temporada)) {
      this.avisos.info('No hay temporadas con reservas de plaza disponibles.');
      return;
    }
    this.generarReservasPlaza(temporada, soloActivos, accion);
  }

  /** «Ver PDF» como hoy; descargar, en PDF (el CSV tiene su propio botón, que llama a `descargarDeudas`). */
  protected override generarInformeDeudas(soloActivos: boolean, accion: AccionInforme): void {
    if (accion === 'ver') {
      super.generarInformeDeudas(soloActivos, accion);
    } else {
      this.descargarDeudas(soloActivos, 'pdf');
    }
  }
}
