import { HttpEvent, HttpEventType } from '@angular/common/http';
import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';

import { MOTIVO_LECTURA } from '../../utils/subida-archivos.util';
import { MaterialExamenApiService } from './material-examen-api.service';
import { DetalleBloque, SubidaMaterial } from './material-examen.models';
import { motivoFalloSubida, motivoVideo } from './material-examen-vista';

export type EstadoSubida = 'espera' | 'subiendo' | 'fallo';

export interface SubidaVideo {
  clave: number;
  nombre: string;
  /** El vídeo guardado al que sustituye; null en uno nuevo. */
  sustituye: string | null;
  archivo: File;
  estado: EstadoSubida;
  cargados: number;
  total: number;
  motivo: string | null;
  reintentable: boolean;
}

export interface SubidaTerminada {
  nombre: string;
  detalle: DetalleBloque;
}

/** Comprueba que el navegador puede leer el fichero con solo un byte: el vídeo no se copia a memoria. */
async function legible(archivo: File): Promise<boolean> {
  try {
    await archivo.slice(0, 1).arrayBuffer();
    return true;
  } catch {
    return false;
  }
}

/**
 * Cola de subida de vídeos de un bloque: de una en una, y un fallo no para las siguientes. Lo que no pasaría la validación
 * de la API se queda en la lista sin llegar a enviarse. Vive con la pantalla: al salir de ella, la subida se corta.
 */
@Injectable()
export class ColaSubidas implements OnDestroy {
  private readonly api = inject(MaterialExamenApiService);

  readonly items = signal<SubidaVideo[]>([]);
  /** Hay algo subiendo o esperando turno; los fallos no cuentan. */
  readonly enCurso = computed(() => this.items().some((item) => item.estado !== 'fallo'));
  readonly terminada = new Subject<SubidaTerminada>();

  private disciplina = '';
  private bloque = '';
  private contador = 0;
  private activa: { clave: number; suscripcion: Subscription } | null = null;
  private destruida = false;

  iniciar(disciplina: string, bloque: string): void {
    this.disciplina = disciplina;
    this.bloque = bloque;
  }

  ngOnDestroy(): void {
    this.destruida = true;
    this.activa?.suscripcion.unsubscribe();
    this.activa = null;
    this.items.set([]);
  }

  /** Añade los ficheros en el orden recibido. Con `sustituye`, cada uno reemplaza a ese vídeo. */
  async agregar(archivos: readonly File[], sustituye: string | null = null): Promise<void> {
    for (const archivo of archivos) {
      let motivo = motivoVideo(archivo, sustituye);
      if (!motivo && !(await legible(archivo))) {
        motivo = MOTIVO_LECTURA;
      }
      if (this.destruida) {
        return;
      }
      this.items.update((lista) => [
        ...lista,
        {
          clave: ++this.contador,
          nombre: archivo.name,
          sustituye,
          archivo,
          estado: motivo ? 'fallo' : 'espera',
          cargados: 0,
          total: archivo.size,
          motivo,
          reintentable: false,
        },
      ]);
      this.siguiente();
    }
  }

  /** Quita la fila: en una subida en curso, corta el envío; en un fallo, solo la borra. */
  cancelar(clave: number): void {
    if (this.activa?.clave === clave) {
      this.activa.suscripcion.unsubscribe();
      this.activa = null;
    }
    this.items.update((lista) => lista.filter((item) => item.clave !== clave));
    this.siguiente();
  }

  reintentar(clave: number): void {
    this.actualizar(clave, { estado: 'espera', cargados: 0, motivo: null, reintentable: false });
    this.siguiente();
  }

  private actualizar(clave: number, cambios: Partial<SubidaVideo>): void {
    this.items.update((lista) => lista.map((item) => (item.clave === clave ? { ...item, ...cambios } : item)));
  }

  private siguiente(): void {
    if (this.activa) {
      return;
    }
    const item = this.items().find((i) => i.estado === 'espera');
    if (!item) {
      return;
    }
    this.actualizar(item.clave, { estado: 'subiendo' });
    let cargados = 0;
    // `activa` se fija antes de suscribirse: una respuesta síncrona no debe dejarla apuntando a una subida ya acabada.
    const activa = { clave: item.clave, suscripcion: new Subscription() };
    this.activa = activa;
    activa.suscripcion.add(
      this.enviar(item).subscribe({
        next: (evento) => {
          if (evento.type === HttpEventType.UploadProgress) {
            cargados = evento.loaded;
            this.actualizar(item.clave, { cargados, total: evento.total ?? item.total });
          } else if (evento.type === HttpEventType.Response && evento.body) {
            this.acabar(item, evento.body);
          }
        },
        error: (error) => {
          this.activa = null;
          this.actualizar(item.clave, { estado: 'fallo', ...motivoFalloSubida(error, cargados) });
          this.siguiente();
        },
      })
    );
  }

  private enviar(item: SubidaVideo): Observable<HttpEvent<SubidaMaterial | DetalleBloque>> {
    return item.sustituye
      ? this.api.sustituirVideo(this.disciplina, this.bloque, item.sustituye, item.archivo)
      : this.api.subirVideo(this.disciplina, this.bloque, item.archivo);
  }

  private acabar(item: SubidaVideo, cuerpo: SubidaMaterial | DetalleBloque): void {
    this.activa = null;
    this.items.update((lista) => lista.filter((i) => i.clave !== item.clave));
    this.terminada.next({ nombre: item.nombre, detalle: 'bloque' in cuerpo ? cuerpo.bloque : cuerpo });
    this.siguiente();
  }
}
