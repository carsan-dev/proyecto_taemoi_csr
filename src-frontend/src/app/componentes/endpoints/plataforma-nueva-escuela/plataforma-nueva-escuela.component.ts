import { HttpErrorResponse } from '@angular/common/http';
import { Location } from '@angular/common';
import { Component, ElementRef, Injector, OnInit, afterNextRender, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import Swal from 'sweetalert2';

import { AltaEscuelaService, DisciplinaPlantilla, EscuelaCreada } from '../../../servicios/plataforma/alta-escuela.service';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

export const NOMBRE_ESCUELA_MAX = 150;
export const NOMBRE_SEDE_MAX = 100;

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export type CampoAlta = 'nombre' | 'emailContacto' | 'sede' | 'propietarioEmail' | 'disciplinas';

/** Orden en que el foco busca el primer campo con error. */
const ORDEN_CAMPOS: CampoAlta[] = ['nombre', 'emailContacto', 'sede', 'propietarioEmail', 'disciplinas'];

const plural = (n: number, uno: string, varios: string): string => `${n} ${n === 1 ? uno : varios}`;

@Component({
  selector: 'app-plataforma-nueva-escuela',
  standalone: true,
  imports: [FormsModule, SkeletonCardComponent],
  templateUrl: './plataforma-nueva-escuela.component.html',
  styleUrl: './plataforma-nueva-escuela.component.scss',
})
export class PlataformaNuevaEscuelaComponent implements OnInit {
  private readonly servicio = inject(AltaEscuelaService);
  private readonly location = inject(Location);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly nombreMax = NOMBRE_ESCUELA_MAX;
  readonly sedeMax = NOMBRE_SEDE_MAX;

  nombre = '';
  emailContacto = '';
  sedeNombre = '';
  propietarioNombre = '';
  propietarioApellidos = '';
  propietarioEmail = '';
  seleccion = new Set<string>();

  disciplinas: DisciplinaPlantilla[] = [];
  cargando = true;
  errorCarga = false;

  errores: Partial<Record<CampoAlta, string>> = {};
  errorGeneral: string | null = null;
  enviando = false;
  creada: EscuelaCreada | null = null;

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.errorCarga = false;
    this.servicio.disciplinas().subscribe({
      next: (lista) => {
        this.disciplinas = lista ?? [];
        // Una disciplina que ya no está en la plantilla no puede seguir marcada.
        this.seleccion = new Set([...this.seleccion].filter((c) => this.disciplinas.some((d) => d.codigo === c)));
        this.cargando = false;
      },
      error: () => {
        this.cargando = false;
        this.errorCarga = true;
      },
    });
  }

  // ------------------------------------------------------------------ textos

  detalle(d: DisciplinaPlantilla): string {
    return [
      d.conGrados ? 'Con grados' : 'Sin grados',
      plural(d.tarifas, 'tarifa base', 'tarifas base'),
      plural(d.productos, 'producto', 'productos'),
    ].join(' · ');
  }

  /** «N disciplina(s) · se invitará a <email>», solo con las partes ya conocidas. */
  get resumen(): string {
    const partes: string[] = [];
    if (this.seleccion.size > 0) {
      partes.push(plural(this.seleccion.size, 'disciplina', 'disciplinas'));
    }
    const email = this.propietarioEmail.trim();
    if (email) {
      partes.push(`se invitará a ${email}`);
    }
    return partes.join(' · ');
  }

  get contadorNombre(): string {
    return `${this.nombre.length} / ${NOMBRE_ESCUELA_MAX}`;
  }

  get contadorSede(): string {
    return `${this.sedeNombre.length} / ${NOMBRE_SEDE_MAX}`;
  }

  // ------------------------------------------------------------------ formulario

  marcada(codigo: string): boolean {
    return this.seleccion.has(codigo);
  }

  alternar(codigo: string, marcada: boolean): void {
    if (this.enviando) {
      return;
    }
    if (marcada) {
      this.seleccion.add(codigo);
    } else {
      this.seleccion.delete(codigo);
    }
    delete this.errores.disciplinas;
  }

  /** Quita el error del campo en cuanto se vuelve a escribir en él. */
  limpiar(campo: CampoAlta): void {
    delete this.errores[campo];
  }

  get sucio(): boolean {
    return (
      !!(this.nombre || this.emailContacto || this.sedeNombre || this.propietarioNombre || this.propietarioApellidos || this.propietarioEmail) ||
      this.seleccion.size > 0
    );
  }

  private validar(): boolean {
    const e: Partial<Record<CampoAlta, string>> = {};
    const nombre = this.nombre.trim();
    if (!nombre) {
      e.nombre = 'Escribe el nombre de la escuela.';
    } else if (nombre.length > NOMBRE_ESCUELA_MAX) {
      e.nombre = `El nombre admite hasta ${NOMBRE_ESCUELA_MAX} caracteres.`;
    }
    const contacto = this.emailContacto.trim();
    if (!contacto) {
      e.emailContacto = 'Escribe el correo de contacto.';
    } else if (!EMAIL.test(contacto)) {
      e.emailContacto = 'Escribe un correo válido, por ejemplo escuela@correo.es.';
    }
    const sede = this.sedeNombre.trim();
    if (!sede) {
      e.sede = 'Escribe el nombre de la primera sede.';
    } else if (sede.length > NOMBRE_SEDE_MAX) {
      e.sede = `El nombre admite hasta ${NOMBRE_SEDE_MAX} caracteres.`;
    }
    const propietario = this.propietarioEmail.trim();
    if (!propietario) {
      e.propietarioEmail = 'Escribe el email del propietario.';
    } else if (!EMAIL.test(propietario)) {
      e.propietarioEmail = 'Escribe un email válido, por ejemplo nombre@correo.es.';
    }
    if (this.seleccion.size === 0) {
      e.disciplinas = 'Marca al menos una disciplina.';
    }
    this.errores = e;
    return Object.keys(e).length === 0;
  }

  enviar(): void {
    if (this.enviando) {
      return;
    }
    this.errorGeneral = null;
    if (!this.validar()) {
      this.enfocar(() => this.primerCampoConError());
      return;
    }
    this.enviando = true;
    this.servicio
      .crear({
        nombre: this.nombre.trim(),
        emailContacto: this.emailContacto.trim(),
        sede: { nombre: this.sedeNombre.trim() },
        propietario: {
          nombre: this.propietarioNombre.trim(),
          apellidos: this.propietarioApellidos.trim(),
          email: this.propietarioEmail.trim(),
        },
        disciplinas: this.disciplinas.map((d) => d.codigo).filter((c) => this.seleccion.has(c)),
      })
      .subscribe({
        next: (escuela) => {
          this.enviando = false;
          this.creada = escuela;
          this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>('[data-foco="confirmacion"]'));
        },
        error: (error: HttpErrorResponse) => {
          this.enviando = false;
          const mensaje = this.mensaje(error);
          if (error?.status === 409) {
            this.errores = { nombre: mensaje ?? 'Ya existe una escuela con ese nombre.' };
            this.enfocar(() => this.campo('nombre'));
          } else {
            this.errorGeneral = mensaje ?? 'No se pudo crear la escuela. Inténtalo de nuevo.';
          }
        },
      });
  }

  async cancelar(): Promise<void> {
    if (this.enviando) {
      return;
    }
    if (this.sucio) {
      const respuesta = await Swal.fire({
        title: '¿Descartar la escuela nueva?',
        text: 'Lo que has escrito se perderá.',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonText: 'Descartar',
        cancelButtonText: 'Seguir editando',
        confirmButtonColor: '#c53030',
      });
      if (!respuesta.isConfirmed) {
        return;
      }
    }
    this.location.back();
  }

  crearOtra(): void {
    this.nombre = '';
    this.emailContacto = '';
    this.sedeNombre = '';
    this.propietarioNombre = '';
    this.propietarioApellidos = '';
    this.propietarioEmail = '';
    this.seleccion = new Set();
    this.errores = {};
    this.errorGeneral = null;
    this.creada = null;
    this.enfocar(() => this.campo('nombre'));
  }

  irALogo(): void {
    void this.router.navigate(['/plataformaLogos']);
  }

  // ------------------------------------------------------------------ foco

  private campo(campo: CampoAlta): HTMLElement | null {
    const selector = campo === 'disciplinas' ? '#ne-disciplinas input[type=checkbox]' : `#ne-${campo}`;
    return this.host.nativeElement.querySelector<HTMLElement>(selector);
  }

  private primerCampoConError(): HTMLElement | null {
    const primero = ORDEN_CAMPOS.find((c) => this.errores[c]);
    return primero ? this.campo(primero) : null;
  }

  private enfocar(buscar: () => HTMLElement | null): void {
    afterNextRender(() => buscar()?.focus(), { injector: this.injector });
  }

  private mensaje(error: HttpErrorResponse): string | null {
    const mensaje = error?.error?.mensaje;
    return typeof mensaje === 'string' && mensaje.trim() ? mensaje : null;
  }
}
