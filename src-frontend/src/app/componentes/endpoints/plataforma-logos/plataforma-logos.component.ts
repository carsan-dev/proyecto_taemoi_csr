import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Observable, Subscription, throwError, timer } from 'rxjs';
import { retry } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { EscuelaLogo } from '../../../interfaces/escuela-logo';
import { LogoEscuelaService } from '../../../servicios/plataforma/logo-escuela.service';
import { showErrorToast } from '../../../utils/toast.util';
import { SUBIDA_LOGO, prepararFicheroParaSubida, reiniciarSelectorArchivo } from '../../../utils/subida-archivos.util';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { BLANCO, POR_DEFECTO, TemaColor, contraste, esColorValido, formatoRazon, temaDe } from './regla-contraste';

export const LOGO_MAX_BYTES = 1024 * 1024;
/** Espera antes de repetir la miniatura: en una red móvil cortada, repetir al instante suele fallar otra vez. */
export const ESPERA_REINTENTO_MS = 1500;

export interface FilaLogo {
  escuela: EscuelaLogo;
  /** URL de objeto de la miniatura; null si no hay logo o aún no ha llegado. */
  miniatura: string | null;
  ocupada: boolean;
  /** Miniatura pidiéndose al servidor. */
  cargandoMiniatura: boolean;
  /** Contador de cargas de la miniatura: una respuesta con otro número es obsoleta y se descarta. */
  peticion: number;
  error: string | null;
  /** Descarga de la miniatura en curso (incluida la espera del reintento); se cancela al repetir, quitar o destruir. */
  descarga: Subscription | null;
  /** El logo se guardó pero su miniatura no llegó tras subirlo: se avisa en la fila, con «Reintentar». */
  sinVistaPrevia: boolean;
  /** 8b-T: el color que se está escribiendo (`#RRGGBB`); vacío, sin color. */
  color: string;
  guardandoColor: boolean;
  errorColor: string | null;
}

/** Lo que enseña la vista previa de la cabecera de una fila. */
export interface VistaColor {
  tema: TemaColor;
  /** Razón del texto sobre el color de la cabecera. */
  razonTexto: string;
  textoBlanco: boolean;
  /** Razón del color como tinta (botones y enlaces) sobre blanco. */
  razonTinta: string;
  /** Sin color: el de por defecto. */
  porDefecto: boolean;
}

export const MENSAJE_FORMATO_COLOR = 'Escribe el color como #RRGGBB, por ejemplo #1B2B2E.';

@Component({
  selector: 'app-plataforma-logos',
  standalone: true,
  imports: [SkeletonCardComponent],
  templateUrl: './plataforma-logos.component.html',
  styleUrl: './plataforma-logos.component.scss',
})
export class PlataformaLogosComponent implements OnInit, OnDestroy {
  private readonly logos = inject(LogoEscuelaService);

  readonly formatos = '.png,.jpg,.jpeg,.svg,image/png,image/jpeg,image/svg+xml';
  /** Color del panel de una escuela sin color guardado (decisión 257). */
  readonly porDefecto = POR_DEFECTO;

  filas: FilaLogo[] = [];
  cargando = true;
  errorCarga = false;
  private destruido = false;

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.filas.forEach((fila) => {
      this.cancelarDescarga(fila);
      this.soltarMiniatura(fila);
    });
  }

  cargar(): void {
    this.cargando = true;
    this.errorCarga = false;
    this.logos.escuelas().subscribe({
      next: (escuelas) => {
        this.filas.forEach((fila) => {
          this.cancelarDescarga(fila);
          this.soltarMiniatura(fila);
        });
        this.filas = (escuelas ?? []).map((escuela) => ({ escuela, miniatura: null, ocupada: false, cargandoMiniatura: false, peticion: 0, error: null, descarga: null, sinVistaPrevia: false, color: escuela.colorPrimario ?? '', guardandoColor: false, errorColor: null }));
        this.cargando = false;
        this.filas.forEach((fila) => this.cargarMiniatura(fila));
      },
      error: () => {
        this.cargando = false;
        this.errorCarga = true;
      },
    });
  }

  trackById(_: number, fila: FilaLogo): number {
    return fila.escuela.id;
  }

  async elegido(fila: FilaLogo, evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0] ?? null;
    if (!archivo || fila.ocupada) {
      reiniciarSelectorArchivo(entrada);
      return;
    }
    // Copia en memoria al elegirlo: en Android leer el content:// al enviar falla.
    const preparado = await prepararFicheroParaSubida(archivo, SUBIDA_LOGO);
    reiniciarSelectorArchivo(entrada);
    if (!preparado.ok) {
      fila.error = preparado.motivo;
      return;
    }
    if (fila.ocupada) {
      return;
    }
    fila.error = null;
    fila.ocupada = true;
    this.logos.subir(fila.escuela.id, preparado.file).subscribe({
      next: (escuela) => {
        fila.escuela = escuela;
        fila.ocupada = false;
        this.cargarMiniatura(fila, true);
      },
      error: (error: HttpErrorResponse) => {
        fila.ocupada = false;
        fila.error = this.mensaje(error, 'No se pudo subir el logo. Inténtalo de nuevo.');
      },
    });
  }

  async quitar(fila: FilaLogo): Promise<void> {
    if (fila.ocupada) {
      return;
    }
    const heredada = fila.escuela.marcaHeredada;
    const respuesta = await Swal.fire({
      title: heredada ? `¿Volver al logo de siempre de ${fila.escuela.nombre}?` : `¿Quitar el logo de ${fila.escuela.nombre}?`,
      text: heredada
        ? 'Sus PDF y correos llevarán el logo anterior.'
        : 'Sus PDF y correos saldrán solo con el nombre.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: heredada ? 'Volver al de siempre' : 'Quitar logo',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#c53030',
    });
    if (!respuesta.isConfirmed || this.destruido) {
      return;
    }
    fila.error = null;
    fila.ocupada = true;
    this.logos.quitar(fila.escuela.id).subscribe({
      next: (escuela) => {
        fila.escuela = escuela;
        fila.ocupada = false;
        fila.peticion++;
        this.cancelarDescarga(fila);
        fila.cargandoMiniatura = false;
        fila.sinVistaPrevia = false;
        this.soltarMiniatura(fila);
        // La escuela heredada, sin logo subido, vuelve a mostrar el de siempre.
        if (escuela.marcaHeredada) {
          this.cargarMiniatura(fila);
        }
      },
      error: (error: HttpErrorResponse) => {
        fila.ocupada = false;
        fila.error = this.mensaje(error, 'No se pudo quitar el logo. Inténtalo de nuevo.');
      },
    });
  }

  // ------------------------------------------------------------------ color (8b-T)

  /** El color de la vista previa: el escrito si vale; si no, el guardado; sin ninguno, el de por defecto. */
  vista(fila: FilaLogo): VistaColor {
    const escrito = fila.color.trim();
    const guardado = fila.escuela.colorPrimario;
    const color = esColorValido(escrito) ? escrito : esColorValido(guardado) ? guardado : null;
    const tema = temaDe(color);
    return {
      tema,
      razonTexto: formatoRazon(contraste(tema.sobrePrimario, tema.primario)),
      textoBlanco: tema.sobrePrimario === BLANCO,
      razonTinta: formatoRazon(contraste(tema.primarioTinta, BLANCO)),
      porDefecto: color === null,
    };
  }

  /** Valor del selector nativo (`#rrggbb` en minúsculas): el escrito si vale; si no, el guardado o el de por defecto. */
  valorSelector(fila: FilaLogo): string {
    const escrito = fila.color.trim();
    const guardado = fila.escuela.colorPrimario;
    return (esColorValido(escrito) ? escrito : esColorValido(guardado) ? guardado : POR_DEFECTO).toLowerCase();
  }

  colorElegido(fila: FilaLogo, evento: Event): void {
    fila.color = (evento.target as HTMLInputElement).value.toUpperCase();
    fila.errorColor = null;
  }

  colorEscrito(fila: FilaLogo, evento: Event): void {
    fila.color = (evento.target as HTMLInputElement).value;
    fila.errorColor = null;
  }

  /** Hay algo distinto de lo guardado que guardar. */
  hayCambioDeColor(fila: FilaLogo): boolean {
    return fila.color.trim().toUpperCase() !== (fila.escuela.colorPrimario ?? '').toUpperCase();
  }

  guardarColor(fila: FilaLogo): void {
    if (fila.guardandoColor) {
      return;
    }
    const escrito = fila.color.trim();
    if (!esColorValido(escrito)) {
      fila.errorColor = MENSAJE_FORMATO_COLOR;
      return;
    }
    this.enviarColor(fila, escrito.toUpperCase(), 'No se pudo guardar el color. Inténtalo de nuevo.');
  }

  quitarColor(fila: FilaLogo): void {
    if (fila.guardandoColor) {
      return;
    }
    this.enviarColor(fila, null, 'No se pudo quitar el color. Inténtalo de nuevo.');
  }

  private enviarColor(fila: FilaLogo, color: string | null, porDefecto: string): void {
    fila.errorColor = null;
    fila.guardandoColor = true;
    this.logos.guardarColor(fila.escuela.id, color).subscribe({
      next: (escuela) => {
        // El servidor añade el tema calculado; la fila guarda solo los datos de la escuela.
        fila.escuela = { id: escuela.id, nombre: escuela.nombre, tieneLogo: escuela.tieneLogo, marcaHeredada: escuela.marcaHeredada, colorPrimario: escuela.colorPrimario ?? null };
        fila.color = fila.escuela.colorPrimario ?? '';
        fila.guardandoColor = false;
      },
      error: (error: HttpErrorResponse) => {
        fila.guardandoColor = false;
        fila.errorColor = this.mensaje(error, porDefecto);
      },
    });
  }

  /** Reintenta la miniatura desde la fila cuando el logo ya estaba guardado. */
  reintentarMiniatura(fila: FilaLogo): void {
    this.cargarMiniatura(fila, true);
  }

  private cargarMiniatura(fila: FilaLogo, trasSubir = false): void {
    // La heredada siempre tiene miniatura: sin logo subido, el backend sirve el de siempre.
    const peticion = ++fila.peticion;
    this.cancelarDescarga(fila);
    if (!fila.escuela.tieneLogo && !fila.escuela.marcaHeredada) {
      fila.cargandoMiniatura = false;
      this.soltarMiniatura(fila);
      return;
    }
    fila.cargandoMiniatura = true;
    fila.sinVistaPrevia = false;
    fila.descarga = this.conReintento(this.logos.logo(fila.escuela.id)).subscribe({
      next: (blob) => {
        // Una respuesta de una carga anterior (p. ej. con el logo ya quitado) no pinta nada.
        if (this.destruido || peticion !== fila.peticion) {
          return;
        }
        fila.cargandoMiniatura = false;
        this.soltarMiniatura(fila);
        fila.miniatura = URL.createObjectURL(blob);
      },
      error: (error: HttpErrorResponse) => {
        if (this.destruido || peticion !== fila.peticion) {
          return;
        }
        fila.cargandoMiniatura = false;
        // Recién subido, el logo ya está guardado: el fallo es solo de la vista previa y se dice en la fila.
        if (trasSubir) {
          this.soltarMiniatura(fila); // la anterior ya no es la del logo guardado
          fila.sinVistaPrevia = true;
          return;
        }
        // 404 en la heredada: no hay logo que servir, queda el marcador «Logo de siempre».
        if (fila.escuela.marcaHeredada && error?.status === 404) {
          return;
        }
        showErrorToast(`No se pudo cargar el logo de ${fila.escuela.nombre}`);
      },
    });
  }

  /** Un reintento automático ante corte de red (estado 0) o error del servidor (5xx); el resto falla de inmediato. */
  private conReintento(descarga: Observable<Blob>): Observable<Blob> {
    return descarga.pipe(
      retry({
        count: 1,
        delay: (error: HttpErrorResponse) => (error?.status === 0 || error?.status >= 500 ? timer(ESPERA_REINTENTO_MS) : throwError(() => error)),
      })
    );
  }

  private cancelarDescarga(fila: FilaLogo): void {
    fila.descarga?.unsubscribe();
    fila.descarga = null;
  }

  private soltarMiniatura(fila: FilaLogo): void {
    if (fila.miniatura) {
      URL.revokeObjectURL(fila.miniatura);
      fila.miniatura = null;
    }
  }

  private mensaje(error: HttpErrorResponse, porDefecto: string): string {
    const mensaje = error?.error?.mensaje;
    return typeof mensaje === 'string' && mensaje.trim() ? mensaje : porDefecto;
  }
}
