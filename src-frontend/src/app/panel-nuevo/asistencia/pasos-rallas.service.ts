import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import {
  ConvocatoriaAlQuitar, PasePendiente, PasoRallas, PrefijoAsistencia, RallasAlQuitar, RallasDelAlumno,
} from '../../interfaces/asistencia';
import { RallasService } from '../../servicios/endpoints/rallas.service';
import { AvisosService } from '../avisos/avisos.service';
import { MEDIAS_COMPLETAS, PASOS_RALLAS, pasoBloqueado, textoApto, textoCuentaRallas } from '../cinturon/rallas-textos';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { ParteMensaje } from '../dialogo/dialogo.component';
import { fechaCorta, formatearEuros, mensajeDelServidor, nombreCompleto } from '../mis-grupos/profesor-textos';

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

/** Un paso desde la hoja: quién, dónde, cómo estaba y cómo se pinta. */
export interface DarPaso {
  prefijo: PrefijoAsistencia;
  grupoId: number;
  alumno: AlumnoDeRallas;
  /** La disciplina del grupo («Taekwondo»), para los textos. */
  disciplina: string;
  antes: number;
  /** Apto en la disciplina del grupo: si quitar desde las 5 falla, se queda como estaba. */
  apto: boolean;
  paso: PasoRallas;
  poner: PonerRallas;
  enCurso: (valor: boolean) => void;
  destruir: DestroyRef;
}

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
   *
   * Quitar desde las 5 rallas no es optimista: primero se pregunta al servidor qué pasaría (`alQuitar`). Si es apto o
   * tiene un pase pendiente o una convocatoria, se confirma con lo que se borra y el POST lleva esos ids; si no, es un
   * paso normal.
   */
  darPaso(o: DarPaso): void {
    if (pasoBloqueado(o.antes, o.paso)) return;
    if (o.antes === MEDIAS_COMPLETAS && o.paso < 0) {
      this.quitarDesdeCompletas(o);
      return;
    }
    this.pasoOptimista(o);
  }

  private pasoOptimista(o: DarPaso): void {
    o.enCurso(true);
    o.poner(o.antes + o.paso);
    this.rallas.paso(o.prefijo, o.grupoId, o.alumno.id, o.paso).pipe(
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

  /** Desde las 5 rallas: se pregunta qué pasaría y, si hace falta, se confirma antes de guardar. */
  private quitarDesdeCompletas(o: DarPaso): void {
    o.enCurso(true);
    this.rallas.alQuitar(o.prefijo, o.grupoId, o.alumno.id).pipe(takeUntilDestroyed(o.destruir)).subscribe({
      next: (estado) => {
        if (!estado.aptoParaExamen && !estado.pases.length && !estado.convocatorias.length) {
          // Nada que avisar: el paso de siempre (sigue en curso).
          this.pasoOptimista(o);
          return;
        }
        o.enCurso(false);
        void this.confirmarQuitar(o, estado);
      },
      error: (e) => {
        o.enCurso(false);
        this.avisos.error(mensajeDelServidor(e, TEXTOS_RALLAS.errorRallas));
      },
    });
  }

  private async confirmarQuitar(o: DarPaso, estado: RallasAlQuitar): Promise<void> {
    const { alumno, paso } = o;
    const apto = textoApto(alumno.genero);
    const enDisciplina = o.disciplina ? ` en ${o.disciplina}` : '';
    const etiqueta = PASOS_RALLAS.find((p) => p.paso === paso)?.etiqueta ?? '';
    const quedara = textoCuentaRallas(MEDIAS_COMPLETAS + paso).toLowerCase();
    const dejaDeEstar = estado.aptoParaExamen ? ` y dejará de estar ${apto} para examen${enDisciplina}` : '';
    const { pases, convocatorias } = estado;
    const conBorrado = pases.length > 0 || convocatorias.length > 0;
    const accion = pases.length && convocatorias.length ? 'Quitar y borrar' : pases.length ? 'Quitar y borrar el pase' : convocatorias.length ? 'Quitar y sacar de la convocatoria' : etiqueta;
    const r = await this.confirmacion.confirmar<RallasDelAlumno>({
      titulo: `¿${etiqueta} a ${alumno.nombre}?`,
      mensaje: [{ fuerte: nombreCompleto(alumno.nombre, alumno.apellidos) }, ` se quedará con ${quedara}${dejaDeEstar}.`],
      aviso: conBorrado
        ? {
            titulo: pases.length && !convocatorias.length ? 'También se borrará su pase pendiente de cobro:' : undefined,
            lineas: [...pases.map((p) => lineaPase(p, convocatorias.length > 0)), ...convocatorias.map(lineaConvocatoria)],
          }
        : undefined,
      accion,
      accionOcupada: 'Quitando…',
      destructivo: conBorrado,
      nombre: 'rallas-quitar-apto',
      ejecutar: () =>
        this.rallas.paso(o.prefijo, o.grupoId, alumno.id, paso, {
          confirmarQuitarApto: true,
          pasesABorrar: pases.map((p) => p.id),
          convocatoriasASalir: convocatorias.map((c) => c.convocatoriaId),
        }),
    });
    if (!r.confirmado) return;
    if (r.error || !r.respuesta) {
      // Un 409 (algo cambió mientras tanto) o la red: queda como estaba.
      o.poner(o.antes, o.apto);
      this.avisos.error(mensajeDelServidor(r.error, TEXTOS_RALLAS.errorRallas));
      return;
    }
    const respuesta = r.respuesta;
    o.poner(respuesta.mediasRallas, respuesta.aptoParaExamen);
    const frases = [
      estado.aptoParaExamen ? `${alumno.nombre} ya no está ${apto} para examen.` : '',
      respuesta.pasesBorrados?.length ? 'Se ha borrado su pase pendiente.' : '',
      ...(respuesta.convocatoriasSalidas ?? []).map((c) => `Ya no está en la convocatoria del ${fechaCorta(c.fecha)}.`),
    ].filter(Boolean);
    if (frases.length) this.avisos.exito(frases.join(' '));
  }

  /**
   * Con las 5 rallas: se confirma y, si sale bien, queda apto; las rallas se quedan hasta que cambie de grado. Quien llama cierra la hoja antes, para
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
      mensaje: [
        { fuerte: nombreCompleto(alumno.nombre, alumno.apellidos) },
        ` quedará ${apto} para examen${enDisciplina}. Las rallas se quedan hasta que cambie de grado.`,
      ],
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
    o.poner(r.respuesta?.mediasRallas ?? MEDIAS_COMPLETAS, r.respuesta?.aptoParaExamen ?? true);
    this.avisos.exito(`${alumno.nombre} queda ${apto} para examen${enDisciplina}.`);
  }
}

/** «Pase con derecho a examen - TAEKWONDO — 30,00 €». */
function lineaPase(p: PasePendiente, conConvocatoria = false): ParteMensaje[] {
  const prefijo = conConvocatoria ? 'Se borrará su pase pendiente de cobro: ' : '';
  return [`${prefijo}${p.concepto} — ${formatearEuros(Number(p.importe))}`];
}

/** «Se le sacará de la convocatoria del 12/03/2026 y se borrará su cargo: Examen — 25,00 €». */
function lineaConvocatoria(c: ConvocatoriaAlQuitar): ParteMensaje[] {
  let cargo = '';
  if (c.cargoId !== null && c.cargoId !== undefined) {
    const concepto = c.concepto ? `: ${c.concepto}` : '';
    const importe = c.importe !== null && c.importe !== undefined ? ` — ${formatearEuros(Number(c.importe))}` : '';
    cargo = ` y se borrará su cargo${concepto}${importe}`;
  }
  return [`Se le sacará de la convocatoria del ${fechaCorta(c.fecha)}${cargo}`];
}
