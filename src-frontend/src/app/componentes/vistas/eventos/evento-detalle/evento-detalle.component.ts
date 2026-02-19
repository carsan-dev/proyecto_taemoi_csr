import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { Documento, Evento } from '../../../../interfaces/evento';
import { CommonModule } from '@angular/common';
import { NgxSpinnerModule, NgxSpinnerService } from 'ngx-spinner';
import { SeoService } from '../../../../servicios/generales/seo.service';

@Component({
  selector: 'app-evento-detalle',
  standalone: true,
  imports: [CommonModule, NgxSpinnerModule, RouterLink],
  templateUrl: './evento-detalle.component.html',
  styleUrl: './evento-detalle.component.scss',
})
export class EventoDetalleComponent implements OnInit, OnDestroy {
  evento: Evento | null = null;
  eventoId!: number;
  modalImagenAbierto: boolean = false;
  private readonly formateadorFechaCorta = new Intl.DateTimeFormat('es-ES', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
  private readonly formateadorFechaLarga = new Intl.DateTimeFormat('es-ES', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });

  constructor(
    private readonly route: ActivatedRoute,
    private readonly endpointsService: EndpointsService,
    private readonly spinner: NgxSpinnerService,
    private readonly seoService: SeoService
  ) {}

  ngOnInit(): void {
    this.eventoId = +this.route.snapshot.paramMap.get('eventoId')!;
    this.obtenerEvento(this.eventoId);
  }

  obtenerEvento(id: number): void {
    this.spinner.show();

    this.endpointsService.obtenerEventoPorId(id).subscribe({
      next: (response: Evento) => {
        this.evento = response;
        this.actualizarSeo(response);
        this.spinner.hide();
      },
      error: () => {
        this.spinner.hide();
        Swal.fire({
          title: 'Error',
          text: 'No hemos podido obtener los detalles del evento.',
          icon: 'error',
        });
      },
    });
  }

  private actualizarSeo(evento: Evento): void {
    const descripcionCorta =
      evento.descripcion.length > 155
        ? evento.descripcion.substring(0, 152) + '...'
        : evento.descripcion;
    const imageUrl = this.construirUrlImagenEvento(
      evento.fotoEvento?.url,
      undefined,
      this.obtenerVersionImagenEvento(evento)
    );

    this.seoService.updateDynamicSeo({
      title: `${evento.titulo} | Eventos Moiskimdo - Taekwondo en Umbrete`,
      description: descripcionCorta,
      keywords: `${evento.titulo}, evento taekwondo, competicion artes marciales, moiskimdo, umbrete, sevilla`,
      ogImage: imageUrl,
      canonical: `https://moiskimdo.es/eventos/${evento.id}`,
      breadcrumbs: [
        { name: 'Inicio', url: '/' },
        { name: 'Eventos', url: '/eventos' },
        { name: evento.titulo, url: `/eventos/${evento.id}` },
      ],
    });

    // Agregar Event Schema para SEO
    this.seoService.setEventSchema({
      name: evento.titulo,
      description: evento.descripcion,
      image: imageUrl,
      url: `/eventos/${evento.id}`,
      startDate: this.obtenerFechaIsoEvento(evento),
    });
  }

  getEventoImageUrl(width?: number): string {
    const fallback = '../../../../assets/media/default.webp';
    if (!this.evento?.fotoEvento?.url) {
      return fallback;
    }

    const imageUrl = this.construirUrlImagenEvento(
      this.evento.fotoEvento.url,
      width,
      this.obtenerVersionImagenEvento(this.evento)
    );
    return imageUrl ?? fallback;
  }

  abrirModalImagen(): void {
    this.modalImagenAbierto = true;
    document.body.style.overflow = 'hidden'; // Deshabilitar scroll del body
  }

  cerrarModalImagen(): void {
    this.modalImagenAbierto = false;
    document.body.style.overflow = '';
  }

  ngOnDestroy(): void {
    // Ensure scroll is restored if the modal was left open on navigation.
    document.body.style.overflow = '';
    // Limpiar Event Schema al salir de la página
    this.seoService.removeEventSchema();
  }

  getFileIcon(tipo: string): string {
    if (tipo.includes('pdf')) return 'bi-file-earmark-pdf';
    if (tipo.includes('word') || tipo.includes('document')) return 'bi-file-earmark-word';
    if (tipo.includes('excel') || tipo.includes('spreadsheet')) return 'bi-file-earmark-excel';
    if (tipo.includes('image')) return 'bi-file-earmark-image';
    return 'bi-file-earmark';
  }

  private esDispositivoIOS(): boolean {
    const navigatorRef = globalThis.navigator;
    if (!navigatorRef) {
      return false;
    }

    const userAgent = navigatorRef.userAgent ?? '';
    const esIOSClasico = /iPad|iPhone|iPod/.test(userAgent);
    const esIPadOS = navigatorRef.platform === 'MacIntel' && navigatorRef.maxTouchPoints > 1;
    return esIOSClasico || esIPadOS;
  }

  descargarDocumento(documento: Documento): void {
    if (!documento?.id || !this.eventoId) {
      Swal.fire({
        title: 'Error',
        text: 'No se pudo descargar el documento del evento.',
        icon: 'error',
      });
      return;
    }

    if (this.esDispositivoIOS()) {
      const downloadUrl = this.endpointsService.obtenerUrlDescargaDocumentoEvento(
        this.eventoId,
        documento.id,
        true
      );
      globalThis.window?.location.assign(downloadUrl);
      return;
    }

    this.endpointsService.descargarDocumentoEvento(this.eventoId, documento.id, true).subscribe({
      next: (blob) => {
        const url = globalThis.URL.createObjectURL(blob);
        const link = globalThis.document?.createElement('a');
        if (!link) {
          globalThis.URL.revokeObjectURL(url);
          return;
        }
        link.href = url;
        link.download = this.obtenerNombreDescarga(documento.nombre, documento.tipo);
        link.click();
        globalThis.URL.revokeObjectURL(url);
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo descargar el documento del evento.',
          icon: 'error',
        });
      },
    });
  }

  private obtenerNombreDescarga(
    nombre: string | null | undefined,
    mimeType: string | null | undefined
  ): string {
    const base = (nombre ?? '').trim() || 'documento';
    if (base.includes('.')) {
      return base;
    }
    const extension = this.obtenerExtensionDesdeMime(mimeType);
    return extension ? `${base}.${extension}` : base;
  }

  private obtenerExtensionDesdeMime(mimeType: string | null | undefined): string | null {
    const mime = (mimeType ?? '').split(';')[0].trim().toLowerCase();
    switch (mime) {
      case 'application/pdf':
        return 'pdf';
      case 'text/csv':
        return 'csv';
      case 'text/plain':
        return 'txt';
      case 'image/jpeg':
        return 'jpg';
      case 'image/png':
        return 'png';
      case 'image/webp':
        return 'webp';
      case 'image/gif':
        return 'gif';
      case 'application/msword':
        return 'doc';
      case 'application/vnd.openxmlformats-officedocument.wordprocessingml.document':
        return 'docx';
      case 'application/vnd.ms-excel':
        return 'xls';
      case 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':
        return 'xlsx';
      default:
        return null;
    }
  }

  private construirUrlImagenEvento(
    url: string | null | undefined,
    width: number | undefined,
    version: string
  ): string | undefined {
    if (!url) {
      return undefined;
    }

    let resultado = url;
    if (width && width > 0) {
      resultado = this.actualizarParametroUrl(resultado, 'w', String(Math.floor(width)));
    }
    return this.actualizarParametroUrl(resultado, 'v', version);
  }

  private obtenerVersionImagenEvento(evento: Evento): string {
    const version = evento?.fotoEvento?.id ?? evento?.fotoEvento?.nombre ?? '0';
    return String(version);
  }

  private actualizarParametroUrl(url: string, key: string, value: string): string {
    const valueSeguro = encodeURIComponent(value);
    const regex = new RegExp(`([?&])${key}=[^&]*`);
    if (regex.test(url)) {
      return url.replace(regex, `$1${key}=${valueSeguro}`);
    }
    const separador = url.includes('?') ? '&' : '?';
    return `${url}${separador}${key}=${valueSeguro}`;
  }

  getFechaEventoTextoCorto(): string | null {
    const fecha = this.parsearFechaEvento(this.evento?.fechaEvento);
    if (!fecha) {
      return null;
    }
    return this.formateadorFechaCorta.format(fecha);
  }

  getFechaEventoTextoLargo(): string | null {
    const fecha = this.parsearFechaEvento(this.evento?.fechaEvento);
    if (!fecha) {
      return null;
    }
    const texto = this.formateadorFechaLarga.format(fecha);
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  getFechaEventoEstado(): 'proximo' | 'hoy' | 'realizado' | 'sin-fecha' {
    const fecha = this.parsearFechaEvento(this.evento?.fechaEvento);
    if (!fecha) {
      return 'sin-fecha';
    }

    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    const fechaMillis = fecha.getTime();
    const hoyMillis = hoy.getTime();
    if (fechaMillis === hoyMillis) {
      return 'hoy';
    }
    return fechaMillis > hoyMillis ? 'proximo' : 'realizado';
  }

  getEtiquetaFechaEvento(): string | null {
    const estado = this.getFechaEventoEstado();
    if (estado === 'sin-fecha') {
      return null;
    }
    if (estado === 'hoy') {
      return 'Hoy';
    }
    return estado === 'proximo' ? 'Próximo' : 'Realizado';
  }

  private obtenerFechaIsoEvento(evento: Evento): string | undefined {
    const fecha = this.parsearFechaEvento(evento?.fechaEvento);
    if (!fecha) {
      return undefined;
    }
    const year = fecha.getFullYear();
    const month = String(fecha.getMonth() + 1).padStart(2, '0');
    const day = String(fecha.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private parsearFechaEvento(fechaEvento: unknown): Date | null {
    let year: number;
    let month: number;
    let day: number;

    if (typeof fechaEvento === 'string' && fechaEvento.trim()) {
      const match = fechaEvento.match(/^(\d{4})-(\d{2})-(\d{2})$/);
      if (!match) {
        return null;
      }
      year = Number(match[1]);
      month = Number(match[2]);
      day = Number(match[3]);
    } else if (Array.isArray(fechaEvento) && fechaEvento.length >= 3) {
      year = Number(fechaEvento[0]);
      month = Number(fechaEvento[1]);
      day = Number(fechaEvento[2]);
    } else {
      return null;
    }

    if (Number.isNaN(year) || Number.isNaN(month) || Number.isNaN(day)) {
      return null;
    }

    const fecha = new Date(year, month - 1, day);
    if (
      fecha.getFullYear() !== year ||
      fecha.getMonth() !== month - 1 ||
      fecha.getDate() !== day
    ) {
      return null;
    }
    fecha.setHours(0, 0, 0, 0);
    return fecha;
  }
}
