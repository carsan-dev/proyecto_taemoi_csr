import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { DomSanitizer, SafeResourceUrl, SafeUrl } from '@angular/platform-browser';
import { Subscription } from 'rxjs';

import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { AlumnoDeporteDTO } from '../../../../interfaces/alumno-deporte-dto';
import { MaterialExamenDTO, MaterialExamenVideoDTO } from '../../../../interfaces/material-examen';
import { getDeporteLabel } from '../../../../enums/deporte';

@Component({
  selector: 'app-materiales-examen-user',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './materiales-examen-user.component.html',
  styleUrl: './materiales-examen-user.component.scss',
})
export class MaterialesExamenUserComponent implements OnChanges, OnDestroy {
  @Input() alumnoId: number | null = null;
  @Input() deportes: AlumnoDeporteDTO[] = [];

  deportesConMaterial: AlumnoDeporteDTO[] = [];
  deporteSeleccionado: string | null = null;
  material: MaterialExamenDTO | null = null;
  videoSeleccionado: MaterialExamenVideoDTO | null = null;
  videoSeleccionadoUrl: SafeUrl | null = null;
  temarioUrl: SafeResourceUrl | null = null;
  cargando: boolean = false;
  errorCarga: string | null = null;
  mostrarTemario: boolean = false;
  descripcionBloqueActual: string | null = null;

  private materialSubscription: Subscription | null = null;
  private lastFetchKey: string | null = null;
  private readonly descripcionPreparacionPorGrado: Record<string, string> = {
    BLANCO: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N BLANCO/AMARILLO',
    BLANCO_AMARILLO: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N AMARILLO',
    AMARILLO: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N AMARILLO/NARANJA',
    AMARILLO_NARANJA: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NARANJA',
    NARANJA: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NARANJA/VERDE',
    NARANJA_VERDE: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N VERDE',
    VERDE: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N VERDE/AZUL',
    VERDE_AZUL: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N AZUL',
    AZUL: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N AZUL/ROJO',
    AZUL_ROJO: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ROJO',
    ROJO: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ROJO NEGRO 1\u00BA PUM / NEGRO 1\u00BA DAN',
    ROJO_NEGRO_1_PUM: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ROJO NEGRO 2\u00BA PUM',
    ROJO_NEGRO_2_PUM: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ROJO NEGRO 3\u00BA PUM',
    ROJO_NEGRO_3_PUM: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N ROJO NEGRO 3\u00BA PUM',
    NEGRO_1_DAN: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NEGRO 2\u00BA DAN',
    NEGRO_2_DAN: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NEGRO 3\u00BA DAN',
    NEGRO_3_DAN: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NEGRO 4\u00BA DAN',
    NEGRO_4_DAN: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NEGRO 5\u00BA DAN',
    NEGRO_5_DAN: 'PREPARACI\u00D3N DE EXAMEN PARA CINTUR\u00D3N NEGRO 6\u00BA DAN',
  };

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly sanitizer: DomSanitizer
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['alumnoId'] && !changes['deportes']) {
      return;
    }

    this.deportesConMaterial = this.obtenerDeportesConMaterial();
    if (!this.alumnoId || this.alumnoId <= 0 || this.deportesConMaterial.length === 0) {
      this.resetearVista();
      return;
    }

    const deporteActualDisponible = this.deportesConMaterial.some(
      (item) => item.deporte === this.deporteSeleccionado
    );
    if (!deporteActualDisponible) {
      this.deporteSeleccionado = this.deportesConMaterial[0].deporte;
    }

    this.cargarMaterialSeleccionado();
  }

  ngOnDestroy(): void {
    this.materialSubscription?.unsubscribe();
  }

  onSeleccionarDeporte(deporte: string): void {
    if (!deporte || deporte === this.deporteSeleccionado) {
      return;
    }

    this.deporteSeleccionado = deporte;
    this.cargarMaterialSeleccionado(true);
  }

  onSeleccionarVideo(video: MaterialExamenVideoDTO): void {
    this.videoSeleccionado = video;
    this.videoSeleccionadoUrl = this.sanitizer.bypassSecurityTrustUrl(video.streamUrl);
  }

  toggleTemario(): void {
    this.mostrarTemario = !this.mostrarTemario;
  }

  getDeporteLabel(deporte: string): string {
    return getDeporteLabel(deporte);
  }

  getTemarioUrl(): string | null {
    return this.material?.temario?.downloadUrl ?? null;
  }

  private cargarMaterialSeleccionado(force: boolean = false): void {
    if (!this.alumnoId || !this.deporteSeleccionado) {
      this.resetearVista();
      return;
    }

    const fetchKey = `${this.alumnoId}-${this.deporteSeleccionado}`;
    if (!force && fetchKey === this.lastFetchKey) {
      return;
    }
    this.lastFetchKey = fetchKey;

    this.materialSubscription?.unsubscribe();
    this.cargando = true;
    this.errorCarga = null;
    this.material = null;
    this.videoSeleccionado = null;
    this.videoSeleccionadoUrl = null;
    this.temarioUrl = null;
    this.mostrarTemario = false;

    this.materialSubscription = this.endpointsService
      .obtenerMaterialExamenAlumno(this.alumnoId, this.deporteSeleccionado)
      .subscribe({
        next: (material) => {
          this.material = this.normalizarMaterial(material);
          this.descripcionBloqueActual = this.obtenerDescripcionBloque(this.material);
          this.temarioUrl = this.material.temario?.downloadUrl
            ? this.sanitizer.bypassSecurityTrustResourceUrl(this.material.temario.downloadUrl)
            : null;

          const primerVideo = this.material.videos[0] ?? null;
          if (primerVideo) {
            this.onSeleccionarVideo(primerVideo);
          }
          this.cargando = false;
        },
        error: () => {
          this.errorCarga = 'No se pudo cargar el material de examen.';
          this.cargando = false;
        },
      });
  }

  private normalizarMaterial(material: MaterialExamenDTO | null | undefined): MaterialExamenDTO {
    return {
      deporte: material?.deporte ?? this.deporteSeleccionado ?? '',
      gradoActual: material?.gradoActual ?? null,
      bloqueId: material?.bloqueId ?? null,
      temario: material?.temario ?? null,
      videos: Array.isArray(material?.videos) ? material!.videos : [],
    };
  }

  private obtenerDescripcionBloque(material: MaterialExamenDTO): string | null {
    const gradoNormalizado = (material.gradoActual || '').toUpperCase().trim();
    if (!gradoNormalizado) {
      return null;
    }

    return this.descripcionPreparacionPorGrado[gradoNormalizado] ?? null;
  }

  private obtenerDeportesConMaterial(): AlumnoDeporteDTO[] {
    if (!Array.isArray(this.deportes)) {
      return [];
    }

    return this.deportes.filter((item) =>
      !!item?.deporte &&
      !!item?.grado &&
      item.activo !== false &&
      (item.deporte === 'TAEKWONDO' || item.deporte === 'KICKBOXING')
    );
  }

  private resetearVista(): void {
    this.materialSubscription?.unsubscribe();
    this.deporteSeleccionado = null;
    this.material = null;
    this.videoSeleccionado = null;
    this.videoSeleccionadoUrl = null;
    this.temarioUrl = null;
    this.errorCarga = null;
    this.cargando = false;
    this.mostrarTemario = false;
    this.descripcionBloqueActual = null;
    this.lastFetchKey = null;
  }
}
