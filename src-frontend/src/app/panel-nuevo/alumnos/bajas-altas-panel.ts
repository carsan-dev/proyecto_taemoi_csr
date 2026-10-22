import { Injectable, inject } from '@angular/core';
import { concat } from 'rxjs';

import { AlumnoService } from '../../features/alumno/services/alumno.service';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AlumnoDeporteDTO } from '../../interfaces/alumno-deporte-dto';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';

/**
 * «Dar de baja» y «Dar de alta» del listado nuevo (8b-D1, decisión 269): las mismas llamadas, en el mismo orden y con
 * los mismos textos de aviso que `BajasAltasAlumno` del listado de hoy (que sigue con SweetAlert2), pero con el diálogo
 * y los avisos del panel nuevo. La baja es un diálogo destructivo (foco en «Cancelar»); el alta, con varios deportes de
 * baja, pide cuál con pastillas («Todos los deportes» primero, como hoy).
 */
@Injectable({ providedIn: 'root' })
export class BajasAltasPanel {
  private readonly endpoints = inject(EndpointsService);
  private readonly alumnoService = inject(AlumnoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly catalogo = inject(CatalogoService);
  /** El nombre de la disciplina, como el resto del listado nuevo (`getDeporteLabel` de hoy lee el mismo catálogo). */
  private readonly nombre = (codigo: string) => this.catalogo.etiquetaDisciplina(codigo);

  /** El mismo `PUT /alumnos/{id}/baja` de hoy, tras confirmarlo. */
  async darDeBaja(alumnoId: number, nombre: string, recargar: () => void): Promise<void> {
    const { confirmado } = await this.confirmacion.confirmar({
      titulo: nombre ? `¿Dar de baja a ${nombre}?` : '¿Dar de baja al alumno?',
      mensaje: 'Se desactivan todas sus disciplinas desde hoy y deja de recibir mensualidades; su ficha y sus pagos se conservan.',
      accion: 'Dar de baja',
      destructivo: true,
    });
    if (!confirmado) {
      return;
    }
    this.endpoints.darDeBajaAlumno(alumnoId).subscribe({
      next: () => {
        this.avisos.exito('Alumno dado de baja correctamente');
        recargar();
      },
      error: () => this.avisos.error('Error al dar de baja al alumno'),
    });
  }

  /** Como hoy: sin deportes de baja, lo dice; con uno, lo activa; con varios, pregunta cuál (o todos). */
  darDeAlta(alumnoId: number, recargar: () => void): void {
    this.alumnoService.obtenerDeportesDelAlumno(alumnoId).subscribe({
      next: (deportes: AlumnoDeporteDTO[]) => void this.elegirYActivar(alumnoId, deportes, recargar),
      error: () => this.avisos.error('Error al obtener los deportes del alumno'),
    });
  }

  private async elegirYActivar(alumnoId: number, deportes: AlumnoDeporteDTO[], recargar: () => void): Promise<void> {
    const inactivos = deportes.filter((d) => d.activo === false);
    if (inactivos.length === 0) {
      this.avisos.info('El alumno ya tiene todos sus deportes activos.');
      return;
    }
    if (inactivos.length === 1) {
      this.activar(alumnoId, inactivos[0].deporte, recargar);
      return;
    }
    const { confirmado, valor } = await this.confirmacion.confirmar({
      titulo: 'Selecciona el deporte a activar',
      campo: {
        etiqueta: 'Deporte',
        opciones: [
          { valor: 'TODOS', texto: 'Todos los deportes' },
          ...inactivos.map((d) => ({ valor: d.deporte, texto: this.nombre(d.deporte) })),
        ],
        requerido: true,
      },
      accion: 'Dar de alta',
    });
    if (!confirmado || !valor) {
      return;
    }
    if (valor === 'TODOS') {
      this.activarTodos(alumnoId, inactivos, recargar);
    } else {
      this.activar(alumnoId, valor, recargar);
    }
  }

  private activar(alumnoId: number, deporte: string, recargar: () => void): void {
    this.alumnoService.activarDeporteDeAlumno(alumnoId, deporte).subscribe({
      next: () => {
        this.avisos.exito(`Alumno dado de alta en ${this.nombre(deporte)}`);
        recargar();
      },
      error: (error) => this.avisos.error(textoDeError(error, 'Error al dar de alta al alumno')),
    });
  }

  private activarTodos(alumnoId: number, inactivos: AlumnoDeporteDTO[], recargar: () => void): void {
    concat(...inactivos.map((d) => this.alumnoService.activarDeporteDeAlumno(alumnoId, d.deporte))).subscribe({
      complete: () => {
        this.avisos.exito(`Alumno dado de alta en todos sus deportes: ${inactivos.map((d) => this.nombre(d.deporte)).join(', ')}`);
        recargar();
      },
      error: (error) => this.avisos.error(textoDeError(error, 'Error al activar algunos deportes')),
    });
  }
}

/** Hoy se muestra `error.error` si viene (el texto del servidor); si no, el genérico. */
function textoDeError(error: any, porDefecto: string): string {
  return typeof error?.error === 'string' && error.error ? error.error : porDefecto;
}
