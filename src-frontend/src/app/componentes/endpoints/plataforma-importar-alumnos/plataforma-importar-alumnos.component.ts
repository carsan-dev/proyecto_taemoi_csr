import { HttpErrorResponse } from '@angular/common/http';
import { Location } from '@angular/common';
import { Component, ElementRef, Injector, OnInit, afterNextRender, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { EscuelaLogo } from '../../../interfaces/escuela-logo';
import { ConfirmacionService } from '../../../panel-nuevo/dialogo/confirmacion.service';
import { AcentoEscuelaDirective } from '../../../panel-nuevo/acento-escuela.directive';
import { ImportacionAlumnosService, Incidencia, InformeImportacion } from '../../../servicios/plataforma/importacion-alumnos.service';
import { LogoEscuelaService } from '../../../servicios/plataforma/logo-escuela.service';

export const TAMANO_MAX_BYTES = 5 * 1024 * 1024;
/** Filas que enseña cada lista antes de «Ver los N». */
export const FILAS_VISIBLES = 3;

export const AYUDA_FICHERO = 'Un fichero .xlsx, hasta 5 MB.';
export const AYUDA_FICHERO_NO_VALIDO = 'Usa la hoja maestra de la escuela, sin cambiar los títulos de las columnas.';

/** inicio: elegir escuela y hoja · ensayando/cargando: en vuelo · informe: resultado del ensayo · hecha · fallo (500 o red al cargar). */
export type FaseImportacion = 'inicio' | 'ensayando' | 'informe' | 'cargando' | 'hecha' | 'fallo';

export type ListaIncidencias = 'errores' | 'avisos';

const plural = (n: number, uno: string, varios: string): string => `${n} ${n === 1 ? uno : varios}`;

/** Filas distintas con alguna incidencia: una fila con dos problemas cuenta una vez. */
export function filasDistintas(incidencias: readonly Incidencia[]): number {
  return new Set(incidencias.map((i) => i.fila)).size;
}

/** «35 KB» o «1,2 MB». */
export function formatoTamano(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${(bytes / (1024 * 1024)).toLocaleString('es-ES', { maximumFractionDigits: 1 })} MB`;
}

@Component({
  selector: 'app-plataforma-importar-alumnos',
  standalone: true,
  imports: [FormsModule, AcentoEscuelaDirective],
  templateUrl: './plataforma-importar-alumnos.component.html',
  styleUrl: './plataforma-importar-alumnos.component.scss',
})
export class PlataformaImportarAlumnosComponent implements OnInit {
  private readonly importacion = inject(ImportacionAlumnosService);
  private readonly escuelasApi = inject(LogoEscuelaService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly location = inject(Location);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly visibles = FILAS_VISIBLES;

  escuelas: EscuelaLogo[] = [];
  cargandoEscuelas = true;
  errorEscuelas = false;

  fase: FaseImportacion = 'inicio';
  escuelaId: number | null = null;
  archivo: File | null = null;

  errorEscuela: string | null = null;
  errorArchivo: string | null = null;
  /** La ayuda bajo la zona de subida: la de siempre, o la del fichero que no es la hoja (400). */
  ayudaArchivo = AYUDA_FICHERO;

  informe: InformeImportacion | null = null;
  /** El ensayo limpio vale solo para este fichero y esta escuela: si cambia cualquiera, deja de valer. */
  private ensayado: { archivo: File; escuelaId: number } | null = null;
  private desplegadas: Record<ListaIncidencias, boolean> = { errores: false, avisos: false };

  resultado: InformeImportacion | null = null;
  /** Nombre de la escuela del ensayo y del resultado, fijado al ensayar. */
  nombreEscuelaEnVuelo = '';
  descargando = false;
  errorDescarga: string | null = null;
  private confirmando = false;

  ngOnInit(): void {
    this.cargarEscuelas();
  }

  cargarEscuelas(): void {
    this.cargandoEscuelas = true;
    this.errorEscuelas = false;
    this.escuelasApi.escuelas().subscribe({
      next: (lista) => {
        this.escuelas = lista ?? [];
        this.cargandoEscuelas = false;
      },
      error: () => {
        this.cargandoEscuelas = false;
        this.errorEscuelas = true;
      },
    });
  }

  // ------------------------------------------------------------------ estado derivado

  get ocupado(): boolean {
    return this.fase === 'ensayando' || this.fase === 'cargando';
  }

  get nombreEscuela(): string {
    return this.escuelas.find((e) => e.id === this.escuelaId)?.nombre ?? '';
  }

  get errores(): Incidencia[] {
    return this.informe?.errores ?? [];
  }

  get avisos(): Incidencia[] {
    return this.informe?.avisos ?? [];
  }

  get filasConErrores(): number {
    return filasDistintas(this.errores);
  }

  get filasConAvisos(): number {
    return filasDistintas(this.avisos);
  }

  /** Cargar solo existe tras un ensayo limpio de este mismo fichero y esta misma escuela, con algo que cargar. */
  get puedeCargar(): boolean {
    const e = this.ensayado;
    return (
      this.fase === 'informe' &&
      !!this.informe &&
      this.informe.errores.length === 0 &&
      this.informe.listas > 0 &&
      !!e &&
      e.archivo === this.archivo &&
      e.escuelaId === this.escuelaId
    );
  }

  /** Ensayo sin errores pero sin nada que cargar (todo estaba ya cargado). */
  get nadaQueCargar(): boolean {
    return this.fase === 'informe' && !!this.informe && this.informe.errores.length === 0 && this.informe.listas === 0;
  }

  get detalleFichero(): string {
    const partes: string[] = [];
    if (this.archivo) {
      partes.push(formatoTamano(this.archivo.size));
    }
    if (this.informe && this.fase !== 'inicio') {
      partes.push(plural(this.informe.filas, 'fila', 'filas'));
    }
    const escuela = this.fase === 'inicio' ? '' : this.nombreEscuelaEnVuelo;
    if (escuela) {
      partes.push(escuela);
    }
    return partes.join(' · ');
  }

  get resumen(): string {
    if (this.fase === 'ensayando') {
      return 'Ensayando la hoja…';
    }
    if (this.fase === 'cargando') {
      return `Cargando ${plural(this.informe?.listas ?? 0, 'alumno', 'alumnos')}…`;
    }
    if (this.fase === 'informe' && this.informe) {
      return this.informe.errores.length === 0
        ? `${plural(this.informe.listas, 'alumno listo', 'alumnos listos')} para ${this.nombreEscuelaEnVuelo}.`
        : '';
    }
    return !this.escuelaId || !this.archivo ? 'Elige la escuela y la hoja.' : '';
  }

  get tituloResultado(): string {
    return plural(this.resultado?.creados ?? 0, 'alumno cargado', 'alumnos cargados');
  }

  get detalleResultado(): string {
    const ya = this.resultado?.yaCargados ?? 0;
    const base = `En ${this.nombreEscuelaEnVuelo}.`;
    if (ya === 0) {
      return `${base} Ninguno estaba ya cargado.`;
    }
    return `${base} ${ya === 1 ? '1 ya estaba cargado y no se ha tocado.' : `${ya} ya estaban cargados y no se han tocado.`}`;
  }

  get textoCargar(): string {
    return `Cargar ${plural(this.informe?.listas ?? 0, 'alumno', 'alumnos')}`;
  }

  get textoFilasConErrores(): string {
    return `${plural(this.filasConErrores, 'fila con errores', 'filas con errores')}.`;
  }

  // ------------------------------------------------------------------ listas

  filasVisibles(lista: ListaIncidencias): Incidencia[] {
    const todas = lista === 'errores' ? this.errores : this.avisos;
    return this.desplegadas[lista] ? todas : todas.slice(0, FILAS_VISIBLES);
  }

  puedeDesplegar(lista: ListaIncidencias): boolean {
    return !this.desplegadas[lista] && this.totalIncidencias(lista) > FILAS_VISIBLES;
  }

  totalIncidencias(lista: ListaIncidencias): number {
    return (lista === 'errores' ? this.errores : this.avisos).length;
  }

  desplegar(lista: ListaIncidencias): void {
    this.desplegadas[lista] = true;
    this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>(`[data-lista="${lista}"] li:nth-child(${FILAS_VISIBLES + 1})`));
  }

  referencia(i: Incidencia): string {
    return i.numeroFederacion === null || i.numeroFederacion === undefined ? `Fila ${i.fila}` : `Fila ${i.fila} · n.º ${i.numeroFederacion}`;
  }

  trackIncidencia = (indice: number, i: Incidencia): string => `${i.fila}-${i.columna ?? ''}-${indice}`;

  // ------------------------------------------------------------------ escuela y fichero

  /** Elegir otra escuela descarta el ensayo anterior. */
  alElegirEscuela(valor: number | string | null): void {
    this.escuelaId = valor === null || valor === '' ? null : Number(valor);
    this.errorEscuela = null;
    this.descartarEnsayo();
  }

  alElegirFichero(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const fichero = entrada.files?.[0] ?? null;
    // Se puede volver a elegir el mismo fichero.
    entrada.value = '';
    if (!fichero || this.ocupado) {
      return;
    }
    const problema = this.validarFichero(fichero);
    this.descartarEnsayo();
    this.fase = 'inicio';
    this.ayudaArchivo = AYUDA_FICHERO;
    if (problema) {
      this.archivo = null;
      this.errorArchivo = problema;
      return;
    }
    this.archivo = fichero;
    this.errorArchivo = null;
  }

  private validarFichero(fichero: File): string | null {
    if (!/\.xlsx$/i.test(fichero.name)) {
      return 'Elige un fichero .xlsx.';
    }
    if (fichero.size > TAMANO_MAX_BYTES) {
      return 'El fichero pesa más de 5 MB.';
    }
    return null;
  }

  private descartarEnsayo(): void {
    this.informe = null;
    this.ensayado = null;
    this.errorDescarga = null;
    this.desplegadas = { errores: false, avisos: false };
  }

  elegirExcel(entrada: HTMLInputElement): void {
    if (!this.ocupado) {
      entrada.click();
    }
  }

  // ------------------------------------------------------------------ ensayar

  ensayar(): void {
    if (this.ocupado) {
      return;
    }
    this.errorEscuela = this.escuelaId ? null : 'Elige la escuela.';
    this.errorArchivo = this.archivo ? null : 'Elige la hoja de Excel.';
    if (this.errorEscuela || this.errorArchivo) {
      this.fase = 'inicio';
      this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>(this.errorEscuela ? '#ia-escuela' : '#ia-elegir'));
      return;
    }
    const archivo = this.archivo!;
    const escuelaId = this.escuelaId!;
    this.nombreEscuelaEnVuelo = this.nombreEscuela;
    this.descartarEnsayo();
    this.fase = 'ensayando';
    this.importacion.ensayar(escuelaId, archivo).subscribe({
      next: (informe) => {
        this.informe = informe;
        this.ensayado = { archivo, escuelaId };
        this.fase = 'informe';
        this.enfocar(() => this.titulo('resultado'));
      },
      error: (error: HttpErrorResponse) => this.falloDelEnsayo(error),
    });
  }

  /** «Ensayar otra vez» (tras corregir la hoja o tras el 500): el mismo fichero y la misma escuela. */
  ensayarOtraVez(): void {
    this.ensayar();
  }

  private falloDelEnsayo(error: HttpErrorResponse): void {
    this.fase = 'inicio';
    this.archivo = null;
    this.informe = null;
    this.ayudaArchivo = AYUDA_FICHERO;
    if (error?.status === 400) {
      this.errorArchivo = this.mensaje(error) ?? 'Este fichero no es la hoja de alumnos.';
      this.ayudaArchivo = AYUDA_FICHERO_NO_VALIDO;
    } else if (error?.status === 0) {
      // El navegador no pudo leer el fichero (cambió en disco desde que se eligió).
      this.errorArchivo = 'No se ha podido leer el fichero. Vuelve a elegirlo.';
    } else {
      this.errorArchivo = 'No se ha podido ensayar la hoja. Inténtalo de nuevo.';
    }
  }

  // ------------------------------------------------------------------ cargar

  async cargar(): Promise<void> {
    if (this.ocupado || this.confirmando || !this.puedeCargar) {
      return;
    }
    const listas = this.informe!.listas;
    const alumnos = plural(listas, 'alumno', 'alumnos');
    this.confirmando = true;
    let confirmado = false;
    try {
      const respuesta = await this.confirmacion.confirmar({
        titulo: `¿Cargar ${alumnos} en ${this.nombreEscuelaEnVuelo}?`,
        mensaje: `${listas === 1 ? 'Se creará' : 'Se crearán'} ${alumnos} con su grado, su sede y su grupo. No se puede deshacer desde aquí.`,
        accion: `Cargar ${alumnos}`,
        cancelar: 'Cancelar',
        alerta: true,
        nombre: 'importar-alumnos',
      });
      confirmado = respuesta.confirmado;
    } finally {
      this.confirmando = false;
    }
    // La regla es dura: se vuelve a comprobar tras el diálogo.
    if (confirmado && this.puedeCargar) {
      this.enviarCarga();
    }
  }

  private enviarCarga(): void {
    const archivo = this.archivo!;
    const escuelaId = this.escuelaId!;
    this.fase = 'cargando';
    this.importacion.cargar(escuelaId, archivo).subscribe({
      next: (informe) => {
        this.resultado = informe;
        this.fase = 'hecha';
        // No se conserva la hoja ni su informe tras la carga.
        this.archivo = null;
        this.descartarEnsayo();
        this.enfocar(() => this.titulo('hecha'));
      },
      error: (error: HttpErrorResponse) => this.falloDeLaCarga(error),
    });
  }

  private falloDeLaCarga(error: HttpErrorResponse): void {
    this.ensayado = null;
    const cuerpo = error?.error as Partial<InformeImportacion> | null;
    if (error?.status === 422 && cuerpo && Array.isArray(cuerpo.errores)) {
      // La escuela cambió entre el ensayo y la carga: no se escribió nada y el informe manda.
      this.informe = {
        filas: cuerpo.filas ?? 0,
        listas: cuerpo.listas ?? 0,
        creados: 0,
        yaCargados: cuerpo.yaCargados ?? 0,
        errores: cuerpo.errores,
        avisos: cuerpo.avisos ?? [],
        yaCargadas: cuerpo.yaCargadas ?? [],
      };
      this.desplegadas = { errores: false, avisos: false };
      this.fase = 'informe';
      this.enfocar(() => this.titulo('resultado'));
      return;
    }
    if (error?.status === 400) {
      this.falloDelEnsayo(error);
      return;
    }
    this.fase = 'fallo';
    this.enfocar(() => this.titulo('fallo'));
  }

  // ------------------------------------------------------------------ otras acciones

  /** «Sustituir»: se elige otro fichero y el informe anterior deja de valer. */
  sustituir(entrada: HTMLInputElement): void {
    if (this.ocupado) {
      return;
    }
    this.descartarEnsayo();
    this.fase = 'inicio';
    entrada.click();
  }

  importarOtra(): void {
    this.fase = 'inicio';
    this.escuelaId = null;
    this.archivo = null;
    this.resultado = null;
    this.errorEscuela = null;
    this.errorArchivo = null;
    this.ayudaArchivo = AYUDA_FICHERO;
    this.nombreEscuelaEnVuelo = '';
    this.descartarEnsayo();
    this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>('#ia-escuela'));
  }

  descargarHoja(): void {
    if (this.descargando || !this.archivo || !this.escuelaId) {
      return;
    }
    this.descargando = true;
    this.errorDescarga = null;
    this.importacion.hojaConErrores(this.escuelaId, this.archivo).subscribe({
      next: ({ blob, nombre }) => {
        this.descargando = false;
        this.guardar(blob, nombre);
      },
      error: () => {
        this.descargando = false;
        this.errorDescarga = 'No se ha podido descargar la hoja. Inténtalo de nuevo.';
      },
    });
  }

  /** Descarga en el navegador, sin guardar la hoja en ningún otro sitio. */
  private guardar(blob: Blob, nombre: string): void {
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    enlace.rel = 'noopener';
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    URL.revokeObjectURL(url);
  }

  cancelar(): void {
    if (this.ocupado) {
      return;
    }
    if (this.hayHistorial()) {
      this.location.back();
    } else {
      // Pantalla abierta directamente: no hay pantalla anterior a la que volver.
      void this.router.navigate(['/plataformaLogos']);
    }
  }

  /** Hay una pantalla anterior en el historial del navegador. */
  hayHistorial(): boolean {
    return window.history.length > 1;
  }

  // ------------------------------------------------------------------ foco y textos

  private titulo(nombre: 'resultado' | 'hecha' | 'fallo'): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>(`[data-foco="${nombre}"]`);
  }

  private enfocar(buscar: () => HTMLElement | null): void {
    afterNextRender(() => buscar()?.focus(), { injector: this.injector });
  }

  private mensaje(error: HttpErrorResponse): string | null {
    const mensaje = error?.error?.mensaje;
    return typeof mensaje === 'string' && mensaje.trim() ? mensaje : null;
  }
}
