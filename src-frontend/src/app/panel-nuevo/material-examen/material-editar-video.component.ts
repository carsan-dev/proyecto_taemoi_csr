import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';

import { reiniciarSelectorArchivo } from '../../utils/subida-archivos.util';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { fecha } from '../formato/formato-es';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import { SubidaVideo } from './cola-subidas';
import { ACEPTA_VIDEOS, peso, tipoFichero } from './material-examen-vista';
import { VideoDetalle } from './material-examen.models';
import { MaterialSubidaFilaComponent } from './material-subida-fila.component';

/** El tope de la API (`MAX_TITULO`). */
export const MAX_TITULO = 120;

/**
 * Panel «Editar vídeo» (boceto `Editar.dc.html`): título con contador y fichero con «Sustituir». Sustituir no espera a
 * «Guardar»: entra en la cola del bloque y su progreso se ve aquí, porque el panel tapa la lista.
 */
@Component({
  selector: 'pn-material-editar-video',
  standalone: true,
  imports: [BotonOcupadoComponent, PanelLateralComponent, MaterialSubidaFilaComponent],
  templateUrl: './material-editar-video.component.html',
  styleUrls: ['./material-editar-video.component.scss'],
})
export class MaterialEditarVideoComponent implements OnChanges {
  @Input({ required: true }) video!: VideoDetalle;
  /** «Inicio a amarillo · Taekwondo». */
  @Input({ required: true }) contexto = '';
  /** La regla de «Taeguk» y «Pumse» solo existe en Taekwondo. */
  @Input() taekwondo = false;
  @Input() guardando = false;
  @Input() subidas: readonly SubidaVideo[] = [];
  @Input() movil = false;
  @Output() readonly cerrar = new EventEmitter<void>();
  @Output() readonly guardar = new EventEmitter<string>();
  @Output() readonly sustituir = new EventEmitter<File>();
  @Output() readonly cancelarSubida = new EventEmitter<number>();
  @Output() readonly reintentarSubida = new EventEmitter<number>();

  readonly max = MAX_TITULO;
  readonly aceptaVideos = ACEPTA_VIDEOS;
  titulo = '';
  error = '';

  get datosFichero(): string {
    return `${tipoFichero(this.video.fichero)} · ${peso(this.video.bytes)} · ${fecha(this.video.fecha)}`;
  }

  ngOnChanges(cambios: SimpleChanges): void {
    // Sustituir refresca el vídeo con el panel abierto: solo se rellena al abrirlo, para no pisar lo que se esté escribiendo.
    if (cambios['video']?.firstChange) {
      this.titulo = this.video.titulo;
    }
  }

  escribir(valor: string): void {
    this.titulo = valor;
    if (this.error && valor.trim()) {
      this.error = '';
    }
  }

  confirmar(): void {
    const limpio = this.titulo.trim();
    if (!limpio) {
      this.error = 'Escribe un título.';
      return;
    }
    this.guardar.emit(limpio);
  }

  elegir(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    if (archivo) {
      this.sustituir.emit(archivo);
    }
    reiniciarSelectorArchivo(entrada);
  }
}
