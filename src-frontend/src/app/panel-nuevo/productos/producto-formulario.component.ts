import { Location } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, Subject, Subscription } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { Producto } from '../../interfaces/producto';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { numero } from '../formato/formato-es';
import {
  CampoProducto,
  ProductoPanel,
  cuerpoProducto,
  importeParaCampo,
  leerImporte,
  mensajesError,
  validarPrecio,
} from './productos-vista';

type Estado = 'cargando' | 'listo' | 'error' | 'invalido';

interface ErrorResumen {
  campo: CampoProducto;
  id: string;
  mensaje: string;
}

const IDS: Record<CampoProducto, string> = { concepto: 'producto-concepto', precio: 'producto-precio' };

/**
 * Crear y editar producto del panel nuevo (8b-3g): concepto, precio en es-ES («1.010,50», también con el punto decimal
 * de hoy) y «Lo puede vender el profesor», con etiquetas «obligatorio», resumen de errores arriba con enlaces a cada
 * campo y barra fija abajo con «Cancelar» y la acción (con errores, `aria-disabled`; «Guardando…» sin doble envío). Las
 * MISMAS peticiones que `CrearProductoComponent` y `EditarProductoComponent`: `POST /productos` y `PUT /productos/{id}`
 * con `{concepto, precio, vendibleProfesor}`, el precio como número (el mismo de hoy). Sin SweetAlert2 ni cargador
 * global. Va en su propio chunk detrás de `disenoNuevoGuard` (`productosCrear` y `productosEditar/:id`).
 */
@Component({
  selector: 'app-producto-formulario',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AcentoEscuelaDirective, EsqueletoComponent, BloqueErrorComponent],
  templateUrl: './producto-formulario.component.html',
  styleUrls: ['./producto-formulario.component.scss'],
})
export class ProductoFormularioComponent implements OnInit, OnDestroy {
  private readonly endpoints = inject(EndpointsService);
  private readonly lecturas = inject(LecturasPanelService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Los validadores de hoy (concepto obligatorio; precio obligatorio y mayor o igual a 0) más el formato del precio. */
  readonly form = inject(FormBuilder).nonNullable.group({
    concepto: ['', Validators.required],
    precio: ['', validarPrecio],
    vendibleProfesor: [false],
  });

  readonly ids = IDS;

  modo: 'crear' | 'editar' = 'crear';
  productoId: number | null = null;
  estado: Estado = 'listo';
  guardando = false;
  /** Se pulsó la acción con errores: el resumen se ve y cada error, bajo su campo. */
  intentado = false;

  private lectura?: Subscription;
  private readonly destroy$ = new Subject<void>();
  /** Guardar no se cancela al salir; la navegación de después, sí. */
  private destruido = false;

  get titulo(): string {
    return this.modo === 'crear' ? 'Crear producto' : 'Editar producto';
  }

  get subtitulo(): string {
    return this.modo === 'crear'
      ? 'Registra un producto o servicio para asignar a los alumnos'
      : 'Modifica la información del producto o servicio';
  }

  get accion(): string {
    return this.modo === 'crear' ? 'Crear producto' : 'Guardar cambios';
  }

  ngOnInit(): void {
    this.ruta.params.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const bruto = params['id'];
      const id = Number(bruto);
      if (bruto !== undefined && (!Number.isInteger(id) || id <= 0)) {
        // `productosEditar/abc`: editar un producto que no existe, no crear uno.
        this.modo = 'editar';
        this.productoId = null;
        this.estado = 'invalido';
      } else if (bruto !== undefined) {
        this.modo = 'editar';
        this.productoId = id;
        this.cargar();
      } else {
        this.modo = 'crear';
        this.productoId = null;
        this.estado = 'listo';
      }
    });
  }

  ngOnDestroy(): void {
    this.lectura?.unsubscribe();
    this.destruido = true;
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ------------------------------------------------------------------ lectura (editar)

  /** `GET /productos/{id}` de hoy. */
  cargar(): void {
    if (this.productoId === null) {
      return;
    }
    this.lectura?.unsubscribe();
    this.estado = 'cargando';
    this.lectura = this.lecturas.producto(this.productoId).subscribe({
      next: (producto: ProductoPanel) => {
        this.form.reset({
          concepto: producto?.concepto ?? '',
          precio: importeParaCampo(producto?.precio),
          vendibleProfesor: !!producto?.vendibleProfesor,
        });
        this.estado = 'listo';
        this.cdr.markForCheck();
      },
      error: () => {
        this.estado = 'error';
        this.cdr.markForCheck();
      },
    });
  }

  // ------------------------------------------------------------------ errores

  private visibleError(campo: CampoProducto): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.touched || this.intentado);
  }

  private mensajesDe(campo: CampoProducto): string[] {
    return mensajesError(campo, this.form.controls[campo].errors, this.form.controls[campo].value);
  }

  erroresDe(campo: CampoProducto): string[] {
    return this.visibleError(campo) ? this.mensajesDe(campo) : [];
  }

  get errores(): ErrorResumen[] {
    const lista: ErrorResumen[] = [];
    for (const campo of ['concepto', 'precio'] as CampoProducto[]) {
      for (const mensaje of this.mensajesDe(campo)) {
        lista.push({ campo, id: IDS[campo], mensaje });
      }
    }
    return lista;
  }

  get tituloResumen(): string {
    const n = this.errores.length;
    return n === 1 ? 'Revisa 1 campo antes de guardar' : `Revisa ${numero(n)} campos antes de guardar`;
  }

  describirCampo(campo: CampoProducto): string {
    const ids = [`${IDS[campo]}-ayuda`];
    if (this.erroresDe(campo).length) {
      ids.push(`${IDS[campo]}-error`);
    }
    return ids.join(' ');
  }

  irACampo(evento: Event, id: string): void {
    evento.preventDefault();
    const campo = this.host.nativeElement.querySelector<HTMLElement>(`#${id}`);
    campo?.focus();
    campo?.scrollIntoView?.({ block: 'center' });
  }

  /** Al salir del precio, si se lee bien, se escribe en es-ES («1010.5» pasa a «1.010,50»): se ve qué se ha entendido. */
  alSalirDelPrecio(): void {
    const control = this.form.controls.precio;
    const lectura = leerImporte(control.value);
    if (control.valid && lectura.estado === 'valido') {
      const canonico = importeParaCampo(lectura.valor);
      if (canonico !== control.value) {
        control.setValue(canonico);
      }
    }
  }

  // ------------------------------------------------------------------ guardar y cancelar

  guardar(): void {
    if (this.guardando || this.estado !== 'listo') {
      return;
    }
    if (this.form.invalid) {
      this.intentado = true;
      this.form.markAllAsTouched();
      this.cdr.detectChanges();
      this.host.nativeElement.querySelector<HTMLElement>('.producto-form__resumen')?.focus();
      return;
    }
    const cuerpo = cuerpoProducto(this.form.getRawValue());
    if (this.modo === 'crear') {
      this.enviar(this.endpoints.crearProducto(cuerpo as unknown as Producto), 'Producto creado correctamente.', 'No se pudo crear el producto.');
    } else if (this.productoId !== null) {
      // El método de hoy declara `FormData`, pero hoy recibe el `productoForm.value` (JSON): el mismo cuerpo.
      this.enviar(
        this.endpoints.actualizarProducto(this.productoId, cuerpo as unknown as FormData),
        'Producto actualizado correctamente.',
        'No se pudo actualizar el producto.'
      );
    }
  }

  /** `POST /productos` o `PUT /productos/{id}` de hoy; al terminar, como hoy, a Productos. */
  private enviar(peticion: Observable<unknown>, exito: string, fallo: string): void {
    this.guardando = true;
    peticion.subscribe({
      next: () => {
        this.guardando = false;
        this.avisos.exito(exito);
        if (!this.destruido) {
          void this.router.navigate(['/productosListar']);
        }
      },
      error: (error) => {
        this.guardando = false;
        this.avisos.error(mensajeError(error, fallo));
        this.cdr.markForCheck();
      },
    });
  }

  /** «Cancelar»: no envía nada y vuelve atrás, como hoy. */
  cancelar(): void {
    if (this.guardando) {
      return;
    }
    this.location.back();
  }
}

/** El `mensaje` del servidor si lo trae (el 400 de renombrar con cargos); si no, el de hoy. */
function mensajeError(error: unknown, porDefecto: string): string {
  const mensaje = (error as { error?: { mensaje?: unknown } } | null)?.error?.mensaje;
  return typeof mensaje === 'string' && mensaje.trim() ? mensaje : porDefecto;
}
