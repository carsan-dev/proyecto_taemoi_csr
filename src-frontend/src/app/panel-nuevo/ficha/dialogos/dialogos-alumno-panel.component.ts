import { DatePipe } from '@angular/common';
import { Component, DoCheck, Input, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

import type { EditarAlumnoComponent } from '../../../componentes/endpoints/editar-alumno/editar-alumno.component';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { CampoDialogo } from '../../dialogo/dialogo.component';
import { PanelLateralComponent } from '../../panel-lateral/panel-lateral.component';
import { MAX_PASTILLAS } from './dialogos-ficha-panel';

/**
 * Los diálogos propios de la ficha (los de `DialogosAlumnoComponent`: convocatorias, quitar de una convocatoria,
 * WhatsApp, agregar deporte y actualizar grado) con las piezas del panel nuevo (8b-D2b): los cuatro con lista o con
 * varios campos, en el panel lateral (hoja inferior en el móvil); «Actualizar grado», que es corto, en el diálogo. El
 * estado y los métodos son los de la ficha (`[ficha]="this"`), los mismos que usa la de hoy: lo que se envía no cambia.
 * La ficha de hoy sigue con `DialogosAlumnoComponent`.
 */
@Component({
  selector: 'pn-dialogos-alumno',
  standalone: true,
  imports: [DatePipe, FormsModule, PanelLateralComponent],
  templateUrl: './dialogos-alumno-panel.component.html',
  styleUrls: ['./dialogos-alumno-panel.component.scss'],
})
export class DialogosAlumnoPanelComponent implements DoCheck {
  private readonly confirmacion = inject(ConfirmacionService);

  @Input({ required: true }) ficha!: EditarAlumnoComponent;

  /** El diálogo de «Actualizar grado» está abierto (se abre cuando la ficha pone su bandera). */
  private gradoAbierto = false;

  ngDoCheck(): void {
    if (this.ficha.mostrarModalActualizarGrado && !this.gradoAbierto) {
      void this.abrirActualizarGrado();
    }
  }

  /** «Agregar Deporte» deshabilitado con lo mismo que hoy. */
  get agregarIncompleto(): boolean {
    const f = this.ficha;
    return !f.nuevoDeporte || !f.fechaAltaDeporte || (f.requiereGradoNuevoDeporte() && (!f.gradoInicialDeporte || !f.fechaGradoDeporte));
  }

  /**
   * «Actualizar Grado - …»: el grado de hoy, el nuevo (pastillas o desplegable) y la fecha. Al aceptar pone los dos
   * valores en la ficha y llama a su `actualizarGradoDeporte` (el mismo envío que hoy); el diálogo ya se ha cerrado, así
   * que su bandera se baja (si la actualización falla, el aviso de error lo dice y se vuelve a abrir con el botón).
   */
  private async abrirActualizarGrado(): Promise<void> {
    this.gradoAbierto = true;
    const f = this.ficha;
    const deporte = f.deporteParaActualizarGrado;
    const grados = f.gradosDeLaDisciplina(deporte, f.gradoGuardado(deporte)).map((g) => ({ valor: g.codigo, texto: g.nombre }));
    const campos: CampoDialogo[] = [
      {
        id: 'nuevoGradoActualizar',
        tipo: grados.length <= MAX_PASTILLAS ? 'pastillas' : 'select',
        etiqueta: 'Nuevo Grado',
        opciones: grados,
        valor: f.nuevoGradoActualizar,
      },
      { id: 'fechaGradoActualizar', tipo: 'date', etiqueta: 'Fecha de Grado', valor: f.fechaGradoActualizar },
    ];
    const r = await this.confirmacion.confirmar({
      titulo: `Actualizar Grado - ${f.getDeporteLabel(deporte)}`,
      // El nombre del grado (el de hoy enseña el código, «NARANJA»).
      mensaje: ['Grado actual: ', { fuerte: grados.find((g) => g.valor === f.gradoGuardado(deporte))?.texto ?? f.getGradoDelDeporte(deporte) }],
      campos,
      accion: 'Actualizar Grado',
      nombre: 'actualizar-grado',
      comprobar: (v) =>
        !v['nuevoGradoActualizar']
          ? { campo: 'nuevoGradoActualizar', mensaje: 'Selecciona el nuevo grado' }
          : !v['fechaGradoActualizar']
            ? { campo: 'fechaGradoActualizar', mensaje: 'Selecciona la fecha del grado' }
            : null,
    });
    if (r.confirmado && r.valores) {
      f.nuevoGradoActualizar = r.valores['nuevoGradoActualizar'];
      f.fechaGradoActualizar = r.valores['fechaGradoActualizar'];
      f.actualizarGradoDeporte();
      f.mostrarModalActualizarGrado = false;
    } else {
      f.cerrarModalActualizarGrado();
    }
    this.gradoAbierto = false;
  }
}
