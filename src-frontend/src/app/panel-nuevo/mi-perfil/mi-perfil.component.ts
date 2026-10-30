import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';

import { mensajeDeError } from '../../servicios/endpoints/caja.service';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { textoSalirSinGuardar } from '../productos-alumno/productos-alumno-vista';
import { ConCambiosSinGuardar } from '../productos-alumno/salir-sin-guardar.guard';
import { MiPerfil, MiPerfilService } from './mi-perfil.service';

export const MAX_NOMBRE = 40;
export const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
] as const;
/** Días de cada mes; febrero llega a 29 porque el cumpleaños no lleva año. */
const DIAS_POR_MES = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

export const TEXTOS_MI_PERFIL = {
  errorCarga: 'No se pudo cargar tu perfil.',
  errorGuardar: 'No se pudo guardar. Inténtalo de nuevo.',
  soloUno: 'Elige día y mes, o deja los dos vacíos.',
  guardado: 'Perfil guardado',
} as const;

/** Vacío (o solo espacios) equivale a «sin valor». */
function normalizar(texto: string | null | undefined): string | null {
  const limpio = (texto ?? '').trim();
  return limpio === '' ? null : limpio;
}

/**
 * «Mi perfil» (decisión 322) en `/panel/mi-perfil`: el nombre con el que el panel llama a la cuenta y su cumpleaños. Solo
 * cambia lo que ve la propia cuenta en su panel.
 */
@Component({
  selector: 'pn-mi-perfil',
  standalone: true,
  imports: [BloqueErrorComponent, EsqueletoComponent, BotonOcupadoComponent, AcentoEscuelaDirective],
  templateUrl: './mi-perfil.component.html',
  styleUrl: './mi-perfil.component.scss',
})
export class MiPerfilComponent implements OnInit, ConCambiosSinGuardar {
  private readonly servicio = inject(MiPerfilService);
  private readonly avisos = inject(AvisosService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly confirmacion = inject(ConfirmacionService);

  readonly textos = TEXTOS_MI_PERFIL;
  readonly maxNombre = MAX_NOMBRE;
  readonly meses = MESES;

  readonly estado = signal<'cargando' | 'error' | 'listo'>('cargando');
  readonly perfil = signal<MiPerfil | null>(null);
  readonly nombre = signal('');
  readonly dia = signal<number | null>(null);
  readonly mes = signal<number | null>(null);
  readonly guardando = signal(false);
  readonly errorCumple = signal(false);
  readonly errorGuardado = signal('');

  /** El cumpleaños sale de la ficha de alumno: no hay campos. */
  readonly cumpleFicha = computed(() => this.perfil()?.cumpleFicha ?? null);
  readonly nombreBase = computed(() => this.perfil()?.nombreBase ?? null);
  readonly textoFicha = computed(() => {
    const ficha = this.cumpleFicha();
    return ficha ? `${ficha.dia} de ${MESES[ficha.mes - 1]}` : '';
  });
  /** Solo los días que existen en el mes elegido; sin mes, los 31. */
  readonly dias = computed(() => {
    const mes = this.mes();
    const total = mes === null ? 31 : DIAS_POR_MES[mes - 1];
    return Array.from({ length: total }, (_, i) => i + 1);
  });

  readonly nombreCambia = computed(() => {
    const perfil = this.perfil();
    return perfil !== null && normalizar(this.nombre()) !== normalizar(perfil.nombrePreferido);
  });
  readonly cumpleCambia = computed(() => {
    const perfil = this.perfil();
    return (
      perfil !== null && this.cumpleFicha() === null && (this.dia() !== perfil.cumpleDia || this.mes() !== perfil.cumpleMes)
    );
  });
  readonly pendientes = computed(() => (this.nombreCambia() ? 1 : 0) + (this.cumpleCambia() ? 1 : 0));
  readonly hayCambios = computed(() => this.pendientes() > 0);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.estado.set('cargando');
    this.servicio.obtener().subscribe({
      next: (perfil) => {
        this.aplicar(perfil);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  private aplicar(perfil: MiPerfil): void {
    this.perfil.set(perfil);
    this.nombre.set(perfil.nombrePreferido ?? '');
    this.dia.set(perfil.cumpleDia);
    this.mes.set(perfil.cumpleMes);
    this.errorCumple.set(false);
    this.errorGuardado.set('');
  }

  escribirNombre(evento: Event): void {
    this.nombre.set(this.valor(evento));
    this.errorGuardado.set('');
  }

  elegirDia(evento: Event): void {
    this.dia.set(this.numero(this.valor(evento)));
    this.cumpleModificado();
  }

  elegirMes(evento: Event): void {
    const mes = this.numero(this.valor(evento));
    this.mes.set(mes);
    // Si el día elegido ya no existe en el mes nuevo, se vacía; el error sale al guardar.
    const dia = this.dia();
    if (mes !== null && dia !== null && dia > DIAS_POR_MES[mes - 1]) {
      this.dia.set(null);
    }
    this.cumpleModificado();
  }

  private cumpleModificado(): void {
    this.errorCumple.set(false);
    this.errorGuardado.set('');
  }

  descartar(): void {
    const perfil = this.perfil();
    if (perfil && !this.guardando()) {
      this.aplicar(perfil);
    }
  }

  guardar(): void {
    const perfil = this.perfil();
    if (!perfil || this.guardando() || !this.hayCambios()) {
      return;
    }
    const conFicha = this.cumpleFicha() !== null;
    // Con ficha la API ignora el cumpleaños del cuerpo; se manda lo que llegó del GET, nunca null/null, para que si la
    // ficha deja de valer entre el GET y el PUT no se borre el cumpleaños guardado en la cuenta.
    const dia = conFicha ? perfil.cumpleDia : this.dia();
    const mes = conFicha ? perfil.cumpleMes : this.mes();
    if ((dia === null) !== (mes === null)) {
      this.errorCumple.set(true);
      return;
    }
    this.errorCumple.set(false);
    this.errorGuardado.set('');
    this.guardando.set(true);
    this.servicio.guardar({ nombrePreferido: normalizar(this.nombre()), cumpleDia: dia, cumpleMes: mes }).subscribe({
      next: (guardado) => {
        this.guardando.set(false);
        this.aplicar(guardado);
        this.avisos.exito(this.textos.guardado);
        // El botón de cuenta y el saludo de Inicio leen el nombre de la sesión: se vuelve a pedir sin recargar.
        this.permisos.refrescar();
      },
      error: (error) => {
        this.guardando.set(false);
        this.errorGuardado.set(mensajeDeError(error) ?? this.textos.errorGuardar);
      },
    });
  }

  /** `canDeactivate` (`salirSinGuardarGuard`): con cambios, «¿Salir sin guardar?». */
  async puedeSalir(): Promise<boolean> {
    if (!this.hayCambios() && !this.guardando()) {
      return true;
    }
    const pendientes = this.pendientes();
    const r = await this.confirmacion.confirmar({
      titulo: '¿Salir sin guardar?',
      ...(pendientes > 0 ? { mensaje: textoSalirSinGuardar(pendientes) } : {}),
      accion: 'Salir sin guardar',
      cancelar: 'Seguir editando',
      destructivo: true,
      nombre: 'salir-sin-guardar',
    });
    return r.confirmado;
  }

  /** Recargar o cerrar la pestaña con cambios: el aviso del navegador. */
  @HostListener('window:beforeunload', ['$event'])
  antesDeDescargar(evento: BeforeUnloadEvent): void {
    if (this.hayCambios() || this.guardando()) {
      evento.preventDefault();
      evento.returnValue = '';
    }
  }

  private valor(evento: Event): string {
    return (evento.target as HTMLInputElement | HTMLSelectElement | null)?.value ?? '';
  }

  private numero(texto: string): number | null {
    return texto === '' ? null : Number(texto);
  }
}
