import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import Swal from 'sweetalert2';

import { EscuelaLogo } from '../../../interfaces/escuela-logo';
import { LogoEscuelaService } from '../../../servicios/plataforma/logo-escuela.service';
import { showErrorToast } from '../../../utils/toast.util';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

export const LOGO_MAX_BYTES = 1024 * 1024;

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
}

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

  filas: FilaLogo[] = [];
  cargando = true;
  errorCarga = false;
  private destruido = false;

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.filas.forEach((fila) => this.soltarMiniatura(fila));
  }

  cargar(): void {
    this.cargando = true;
    this.errorCarga = false;
    this.logos.escuelas().subscribe({
      next: (escuelas) => {
        this.filas.forEach((fila) => this.soltarMiniatura(fila));
        this.filas = (escuelas ?? []).map((escuela) => ({ escuela, miniatura: null, ocupada: false, cargandoMiniatura: false, peticion: 0, error: null }));
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

  elegido(fila: FilaLogo, evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0] ?? null;
    entrada.value = '';
    if (!archivo || fila.ocupada) {
      return;
    }
    if (archivo.size > LOGO_MAX_BYTES) {
      fila.error = 'El archivo pesa más de 1 MB. Elige una imagen más ligera.';
      return;
    }
    fila.error = null;
    fila.ocupada = true;
    this.logos.subir(fila.escuela.id, archivo).subscribe({
      next: (escuela) => {
        fila.escuela = escuela;
        fila.ocupada = false;
        this.cargarMiniatura(fila);
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
        fila.cargandoMiniatura = false;
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

  private cargarMiniatura(fila: FilaLogo): void {
    // La heredada siempre tiene miniatura: sin logo subido, el backend sirve el de siempre.
    const peticion = ++fila.peticion;
    if (!fila.escuela.tieneLogo && !fila.escuela.marcaHeredada) {
      fila.cargandoMiniatura = false;
      this.soltarMiniatura(fila);
      return;
    }
    fila.cargandoMiniatura = true;
    this.logos.logo(fila.escuela.id).subscribe({
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
        // 404 en la heredada: no hay logo que servir, queda el marcador «Logo de siempre».
        if (fila.escuela.marcaHeredada && error?.status === 404) {
          return;
        }
        showErrorToast(`No se pudo cargar el logo de ${fila.escuela.nombre}`);
      },
    });
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
