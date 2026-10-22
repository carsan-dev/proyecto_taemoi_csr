import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';

import { OPCIONES_INFORME_ALUMNOS } from '../../../componentes/endpoints/listado-alumnos/informes-alumnos';
import { categoriasDeInforme } from '../../../componentes/generales/informe-modal/categorias-informe';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { AvisosService } from '../../avisos/avisos.service';
import { BotonOcupadoComponent } from '../../carga/boton-ocupado.component';
import { PanelLateralComponent } from '../../panel-lateral/panel-lateral.component';
import { InformesAlumnosPanel } from './informes-alumnos-panel';

/** Qué se pidió en una fila: verlo, descargarlo en PDF o (solo las deudas) en CSV. */
type AccionFila = 'ver' | 'descargar' | 'csv';

const RESERVAS = 'reservas-plaza';

/**
 * «Generar informe» del listado nuevo en el panel lateral (8b-D2a, decisión 270), como las herramientas de «Informes»:
 * los mismos tipos, grupos y orden que el diálogo de hoy (`InformeModalComponent`) y la misma generación
 * (`InformesAlumnos`, mismas peticiones y nombres de archivo). Cada fila lleva sus botones; el pulsado se pone ocupado,
 * los demás esperan (una petición a la vez) y el panel no se cierra hasta que termina. Errores en los avisos del panel
 * nuevo (`InformePdfPanelService`), sin SweetAlert2 ni cargador global.
 */
@Component({
  selector: 'pn-generar-informe',
  standalone: true,
  imports: [PanelLateralComponent, BotonOcupadoComponent],
  templateUrl: './generar-informe-panel.component.html',
  styleUrls: ['./generar-informe-panel.component.scss', './campos-panel.scss'],
})
export class GenerarInformePanelComponent implements OnInit {
  readonly informes = inject(InformePdfService);
  private readonly logica = new InformesAlumnosPanel(inject(EndpointsService), this.informes, inject(AvisosService));

  @Output() readonly cerrar = new EventEmitter<void>();

  readonly categorias = categoriasDeInforme(OPCIONES_INFORME_ALUMNOS);
  readonly reservas = RESERVAS;
  soloActivos = true;
  temporada = '';
  /** La fila y el botón que se pulsaron: es el que se pone ocupado. */
  private pulsado: { tipo: string; accion: AccionFila } | null = null;
  private temporadasLeidas: string[] | null = null;
  private opciones: { value: string; label: string }[] = [];

  ngOnInit(): void {
    // Como al abrir el diálogo de hoy: se vuelven a pedir las temporadas de reservas de plaza.
    this.logica.abrir();
  }

  get cargandoTemporadas(): boolean {
    return this.logica.cargandoTemporadas;
  }

  /** Las temporadas del diálogo de hoy: sin repetir, la más reciente primero y la actual como «Actual (…)», elegida. */
  get opcionesTemporada(): { value: string; label: string }[] {
    const leidas = this.logica.temporadas;
    if (leidas !== this.temporadasLeidas) {
      this.temporadasLeidas = leidas;
      const temporadas = [...new Set(leidas)].sort((a, b) => b.localeCompare(a));
      const ano = new Date().getFullYear();
      const actual = `${ano}/${ano + 1}`;
      this.opciones = temporadas.map((t) => ({ value: t, label: t === actual ? `Actual (${t})` : t }));
      if (!temporadas.includes(this.temporada)) {
        this.temporada = temporadas.includes(actual) ? actual : (temporadas[0] ?? '');
      }
    }
    return this.opciones;
  }

  /** Reservas de plaza sin temporada que elegir (leyéndolas o sin ninguna): sus botones esperan. */
  sinTemporada(tipo: string): boolean {
    return tipo === RESERVAS && (this.cargandoTemporadas || this.opcionesTemporada.length === 0);
  }

  ocupado(tipo: string, accion: AccionFila): boolean {
    return this.informes.ocupado && this.pulsado?.tipo === tipo && this.pulsado.accion === accion;
  }

  deshabilitado(tipo: string, accion: AccionFila): boolean {
    return (this.informes.ocupado && !this.ocupado(tipo, accion)) || this.sinTemporada(tipo);
  }

  alternarSoloActivos(): void {
    this.soloActivos = !this.soloActivos;
  }

  elegirTemporada(valor: string): void {
    this.temporada = valor;
  }

  generar(tipo: string, accion: AccionFila): void {
    if (this.informes.ocupado || this.sinTemporada(tipo)) {
      return;
    }
    this.pulsado = { tipo, accion };
    if (accion === 'csv') {
      this.logica.descargarDeudas(this.soloActivos, 'csv');
      return;
    }
    this.logica.generarInformeSeleccionado({
      tipo,
      accion,
      soloActivos: this.soloActivos,
      temporada: tipo === RESERVAS ? this.temporada : undefined,
    });
  }
}
