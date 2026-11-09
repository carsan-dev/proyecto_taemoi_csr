import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import { PasoRallas, PrefijoAsistencia } from '../../interfaces/asistencia';
import { RallasService } from '../../servicios/endpoints/rallas.service';
import { AvisosService } from '../avisos/avisos.service';
import { pasoBloqueado, textoApto } from '../cinturon/rallas-textos';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { mensajeDelServidor, nombreCompleto } from '../mis-grupos/profesor-textos';

export const TEXTOS_RALLAS = {
  errorRallas: 'No se ha podido guardar la ralla. Inténtalo de nuevo.',
  errorApto: 'No se ha podido marcar para examen. Inténtalo de nuevo.',
} as const;

/** El alumno de la hoja, lo justo para los textos. */
export interface AlumnoDeRallas {
  id: number;
  nombre: string;
  apellidos: string;
  genero?: string | null;
}

/** Pone en pantalla las medias (y, si llega, el apto) de la disciplina del grupo. */
export type PonerRallas = (medias: number, apto?: boolean) => void;

/**
 * Lo que hacen los pasos y «Marcar apto» de la hoja de rallas (decisiones 513 y 514), igual desde Pasar lista que desde
 * la ficha de Mis grupos. Cada pantalla guarda su estado y lo pinta con `poner`; aquí van la llamada, la vuelta atrás
 * si falla, la confirmación del apto y los avisos.
 */
@Injectable({ providedIn: 'root' })
export class PasosRallasService {
  private readonly rallas = inject(RallasService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);

  /**
   * Un paso, guardado al momento (decisión 513 a). Optimista: se pinta ya y, si la llamada falla (red o 409), se
   * deshace y se avisa. Un paso que no cabe no hace nada. `enCurso` marca la llamada en vuelo.
   */
  darPaso(o: {
    prefijo: PrefijoAsistencia;
    grupoId: number;
    alumnoId: number;
    antes: number;
    paso: PasoRallas;
    poner: PonerRallas;
    enCurso: (valor: boolean) => void;
    destruir: DestroyRef;
  }): void {
    if (pasoBloqueado(o.antes, o.paso)) return;
    o.enCurso(true);
    o.poner(o.antes + o.paso);
    this.rallas.paso(o.prefijo, o.grupoId, o.alumnoId, o.paso).pipe(
      takeUntilDestroyed(o.destruir),
      finalize(() => o.enCurso(false)),
    ).subscribe({
      next: (r) => o.poner(r.mediasRallas, r.aptoParaExamen),
      error: (e) => {
        o.poner(o.antes);
        this.avisos.error(mensajeDelServidor(e, TEXTOS_RALLAS.errorRallas));
      },
    });
  }

  /**
   * Con las 5 rallas: se confirma y, si sale bien, queda apto y sus rallas a 0. Quien llama cierra la hoja antes, para
   * que el foco vuelva a la punta y la confirmación lo devuelva ahí al cerrarse.
   */
  async confirmarApto(o: {
    prefijo: PrefijoAsistencia;
    grupoId: number;
    alumno: AlumnoDeRallas;
    disciplina: string;
    poner: PonerRallas;
  }): Promise<void> {
    const { alumno } = o;
    const apto = textoApto(alumno.genero);
    const enDisciplina = o.disciplina ? ` en ${o.disciplina}` : '';
    const r = await this.confirmacion.confirmar({
      titulo: `¿Marcar a ${alumno.nombre} ${apto} para examen?`,
      mensaje: [{ fuerte: nombreCompleto(alumno.nombre, alumno.apellidos) }, ` quedará ${apto} para examen${enDisciplina} y sus rallas volverán a 0.`],
      accion: `Marcar ${apto}`,
      accionOcupada: 'Marcando…',
      nombre: 'rallas-apto',
      ejecutar: () => this.rallas.apto(o.prefijo, o.grupoId, alumno.id),
    });
    if (!r.confirmado) return;
    if (r.error) {
      this.avisos.error(mensajeDelServidor(r.error, TEXTOS_RALLAS.errorApto));
      return;
    }
    o.poner(r.respuesta?.mediasRallas ?? 0, r.respuesta?.aptoParaExamen ?? true);
    this.avisos.exito(`${alumno.nombre} queda ${apto} para examen${enDisciplina}. Sus rallas vuelven a 0.`);
  }
}
