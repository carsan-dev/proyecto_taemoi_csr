import { NgTemplateOutlet } from '@angular/common';
import { Component, ElementRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { HttpErrorResponse } from '@angular/common/http';

import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { AcentoEscuelaDirective } from '../../acento-escuela.directive';
import { AvisosService } from '../../avisos/avisos.service';
import { BloqueErrorComponent } from '../../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../../carga/esqueleto.component';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { MarcaEscuelaPanelService } from '../../marca-escuela-panel.service';
import { textoSalirSinGuardar } from '../../productos-alumno/productos-alumno-vista';
import { ConCambiosSinGuardar } from '../../productos-alumno/salir-sin-guardar.guard';
import {
  CAMPOS_DATOS,
  CampoDato,
  ClaveDato,
  ORDEN_DATOS,
  ValoresDatos,
  clavesCambiadas,
  erroresDeApi,
  errorDeDato,
  escuelaSinRellenar,
  solicitudDe,
  valoresDe,
  valoresVacios,
} from './datos-escuela-vista';
import { DatosEscuelaService } from './datos-escuela.service';

export const TEXTOS_DATOS_ESCUELA = {
  errorCarga: 'No se han podido leer los datos de la escuela.',
  guardado: 'Datos de la escuela guardados.',
  errorGuardar: 'No se han podido guardar los cambios.',
  errorGuardarDetalle: 'Siguen aquí. Comprueba la conexión y vuelve a pulsar «Guardar cambios».',
} as const;

/**
 * «Escuela · Datos de la escuela» (pieza 394; boceto `docs/multi-escuela/boceto-escuela-datos.html`): datos fiscales,
 * contacto, firmante de los documentos y día límite de la mensualidad de la escuela activa. Solo el propietario
 * (`adminOnlyGuard`; la API da 403 al resto). El logo y el color se ven pero no se cambian aquí. El precio de la licencia
 * de competidor es un producto: se cambia en Tesorería · Productos.
 *
 * Se guarda con la barra inferior de cambios (como «Mi perfil»). Se exige todo lo obligatorio al guardar, no al cargar:
 * una escuela con el firmante vacío (Moiskimdo hoy) abre sin errores.
 */
@Component({
  selector: 'pn-datos-escuela',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    RouterLink,
    AcentoEscuelaDirective,
    BloqueErrorComponent,
    BotonOcupadoComponent,
    EsqueletoComponent,
  ],
  templateUrl: './datos-escuela.component.html',
  styleUrl: './datos-escuela.component.scss',
})
export class DatosEscuelaComponent implements OnInit, ConCambiosSinGuardar {
  private readonly servicio = inject(DatosEscuelaService);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly marcaServicio = inject(MarcaEscuelaPanelService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly textos = TEXTOS_DATOS_ESCUELA;

  readonly estado = signal<'cargando' | 'error' | 'listo'>('cargando');
  readonly valores = signal<ValoresDatos>(valoresVacios());
  readonly guardados = signal<ValoresDatos>(valoresVacios());
  readonly tocados = signal<ReadonlySet<ClaveDato>>(new Set());
  readonly intentado = signal(false);
  readonly guardando = signal(false);
  /** Errores de un 400 de la API, por campo; se quitan al editar el campo. */
  readonly errorApi = signal<Partial<Record<ClaveDato, string>>>({});
  /** «Rellena estos datos antes de cobrar…»: la escuela se abre sin ningún dato. */
  readonly primera = signal(false);
  /** Logo y color de la escuela, solo lectura; null si la marca no se pudo leer. */
  readonly logo = signal<string | null>(null);
  readonly color = signal<string | null>(null);
  readonly nombreEscuela = signal('');

  readonly pendientes = computed(() => clavesCambiadas(this.valores(), this.guardados()).length);
  readonly hayCambios = computed(() => this.pendientes() > 0);
  /** Los campos con error a la vista (tocados, o todos tras intentar guardar). */
  readonly malos = computed(() => (this.intentado() ? ORDEN_DATOS.filter((k) => this.error(k)) : []));
  readonly barraVisible = computed(() => this.hayCambios() || this.guardando());
  readonly dia = computed(() => {
    const valor = this.valores().diaLimiteMensualidad.trim();
    return this.error('diaLimiteMensualidad', true) === '' && valor !== '' ? Number(valor) : null;
  });

  ngOnInit(): void {
    this.cargar();
    this.marcaServicio.marca().subscribe({
      next: (marca) => {
        this.nombreEscuela.set(marca.nombre);
        this.logo.set(this.marcaServicio.urlLogo(marca));
        this.color.set(marca.tema.primario);
      },
      error: () => undefined,
    });
  }

  cargar(): void {
    this.estado.set('cargando');
    this.servicio.obtener().subscribe({
      next: (datos) => {
        const valores = valoresDe(datos);
        this.aplicar(valores);
        this.primera.set(escuelaSinRellenar(valores));
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  private aplicar(valores: ValoresDatos): void {
    this.guardados.set(valores);
    this.valores.set({ ...valores });
    this.tocados.set(new Set());
    this.intentado.set(false);
    this.errorApi.set({});
  }

  /** El error a mostrar del campo: el de la API o el del cliente. Con `siempre`, sin esperar a que se toque. */
  error(clave: ClaveDato, siempre = false): string {
    if (!siempre && !this.intentado() && !this.tocados().has(clave)) {
      return '';
    }
    return this.errorApi()[clave] ?? errorDeDato(clave, this.valores()[clave]);
  }

  def(clave: ClaveDato): CampoDato {
    return CAMPOS_DATOS[clave];
  }

  valor(clave: ClaveDato): string {
    return this.valores()[clave];
  }

  longitud(clave: ClaveDato): number {
    return this.valores()[clave].length;
  }

  escribir(clave: ClaveDato, evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    let valor = entrada.value;
    if (CAMPOS_DATOS[clave].mayusculas) {
      valor = valor.toUpperCase();
      if (valor !== entrada.value) {
        entrada.value = valor;
      }
    }
    this.valores.update((v) => ({ ...v, [clave]: valor }));
    if (this.errorApi()[clave]) {
      this.errorApi.update(({ [clave]: _, ...resto }) => resto);
    }
  }

  tocar(clave: ClaveDato): void {
    this.tocados.update((t) => new Set(t).add(clave));
  }

  descartar(): void {
    if (this.guardando()) {
      return;
    }
    this.aplicar(this.guardados());
    this.enfocar(ORDEN_DATOS[0]);
  }

  guardar(): void {
    if (this.guardando() || !this.hayCambios()) {
      return;
    }
    this.intentado.set(true);
    const malos = this.malos();
    if (malos.length > 0) {
      this.enfocar(malos[0]);
      return;
    }
    this.guardando.set(true);
    this.servicio.guardar(solicitudDe(this.valores())).subscribe({
      next: (datos) => {
        this.guardando.set(false);
        this.aplicar(valoresDe(datos));
        this.primera.set(false);
        this.avisos.exito(this.textos.guardado);
      },
      error: (error: unknown) => {
        this.guardando.set(false);
        const porCampo = error instanceof HttpErrorResponse && error.status === 400 ? erroresDeApi(error.error) : {};
        const claves = ORDEN_DATOS.filter((k) => porCampo[k]);
        if (claves.length > 0) {
          this.errorApi.set(porCampo);
          setTimeout(() => this.enfocar(claves[0]));
          return;
        }
        this.avisos.error(`${this.textos.errorGuardar} ${mensajeDeError(error) ?? this.textos.errorGuardarDetalle}`);
      },
    });
  }

  private enfocar(clave: ClaveDato): void {
    this.host.nativeElement.querySelector<HTMLElement>(`#dato-${clave}`)?.focus();
  }

  /** `canDeactivate` (`salirSinGuardarGuard`): con cambios, «¿Salir sin guardar?». */
  async puedeSalir(): Promise<boolean> {
    if (!this.hayCambios() && !this.guardando()) {
      return true;
    }
    const r = await this.confirmacion.confirmar({
      titulo: '¿Salir sin guardar?',
      mensaje: textoSalirSinGuardar(this.pendientes()),
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
}
