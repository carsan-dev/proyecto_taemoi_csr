import { Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { eur } from '../formato/formato-es';
import { ProductosAlumnoEstado } from './productos-alumno.estado';
import { ConCambiosSinGuardar } from './salir-sin-guardar.guard';
import { TEXTOS_PRODUCTOS_ALUMNO, etiquetaProducto } from './productos-alumno-vista';

/**
 * «Productos y cargos» de un alumno (8b-3n) en `/alumnos/:id/productos`, con el interruptor del diseño nuevo. Toda la
 * lógica está en `ProductosAlumnoEstado` (`estado`), uno por pantalla. La plantilla de esta pieza es provisional y SIN
 * estilos (funcional y accesible): la del boceto P1 se escribe encima con la misma API.
 */
@Component({
  selector: 'pn-productos-alumno',
  standalone: true,
  imports: [RouterLink, BloqueErrorComponent, EsqueletoComponent],
  providers: [ProductosAlumnoEstado],
  templateUrl: './productos-alumno-panel.component.html',
})
export class ProductosAlumnoPanelComponent implements OnInit, ConCambiosSinGuardar {
  readonly estado = inject(ProductosAlumnoEstado);
  private readonly ruta = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly textos = TEXTOS_PRODUCTOS_ALUMNO;
  readonly eur = eur;
  readonly etiquetaProducto = etiquetaProducto;

  ngOnInit(): void {
    this.ruta.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((parametros) => {
      const id = Number(parametros.get('id'));
      if (this.estado.estado() !== 'cargando' && id === this.estado.alumnoId()) {
        return;
      }
      this.estado.iniciar(id);
    });
  }

  /** `canDeactivate` (`salirSinGuardarGuard`): con cambios, «¿Salir sin guardar?». */
  puedeSalir(): boolean | Promise<boolean> {
    return this.estado.confirmarSalida();
  }

  /** Recargar o cerrar la pestaña con cambios: el aviso del navegador. */
  @HostListener('window:beforeunload', ['$event'])
  antesDeDescargar(evento: BeforeUnloadEvent): void {
    if (this.estado.hayCambios() || this.estado.guardando()) {
      evento.preventDefault();
      evento.returnValue = '';
    }
  }

  valor(evento: Event): string {
    const destino = evento.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement | null;
    return destino?.value ?? '';
  }

  elegirProducto(evento: Event): void {
    const valor = this.valor(evento);
    this.estado.elegirProducto(valor ? Number(valor) : null);
  }
}
